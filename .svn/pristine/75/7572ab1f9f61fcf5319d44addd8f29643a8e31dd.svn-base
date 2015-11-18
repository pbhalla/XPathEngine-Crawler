/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.tree;

import static com.sleepycat.je.EnvironmentFailureException.unexpectedState;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.cleaner.LocalUtilizationTracker;
import com.sleepycat.je.cleaner.PackedObsoleteInfo;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentFailureReason;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.evictor.Evictor;
import com.sleepycat.je.latch.LatchContext;
import com.sleepycat.je.latch.LatchFactory;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.latch.LatchTable;
import com.sleepycat.je.latch.SharedLatch;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.Provisional;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.log.WholeEntry;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.tree.dupConvert.DupConvert;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.SizeofMarker;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.TestHookExecute;

/**
 * An IN represents an Internal Node in the JE tree.
 *
 * Explanation of KD (KnownDeleted) and PD (PendingDelete) entry flags
 * ===================================================================
 *
 * PD: set for all LN entries that are deleted, even before the LN is
 * committed.  Is used as an authoritative (transactionally correct) indication
 * that an LN is deleted. PD will be cleared if the txn for the deleted LN is
 * aborted.
 *
 * KD: set under special conditions for entries containing LNs which are known
 * to be obsolete.  Not used for entries in an active/uncommitted transaction.
 *
 * First notice that IN.fetchLN will allow a FileNotFoundException when the
 * PD or KD flag is set on the entry.  And it will allow a NULL_LSN when the KD
 * flag is set.
 *
 * KD was implemented first, and was originally used when the cleaner attempts
 * to migrate an LN and discovers it is deleted (see Cleaner.migrateLN). We
 * need KD because the INCompressor may not have run, and may not have
 * compressed the BIN. There's the danger that we'll try to fetch that entry,
 * and that the file was deleted by the cleaner.
 *
 * KD was used more recently when an unexpected exception occurs while logging
 * an LN, after inserting the entry.  Rather than delete the entry to clean up,
 * we mark the entry KD so it won't cause a fetch error later.  In this case
 * the entry LSN is NULL_LSN. See Tree.insertNewSlot.
 *
 * PD is closely related to the first use of KD above (for cleaned deleted LNs)
 * and came about because of a cleaner optimization we make. The cleaner
 * considers all deleted LN log entries to be obsolete, without doing a tree
 * lookup, and without any record of an obsolete offset.  This makes the cost
 * of cleaning of deleted LNs very low.  For example, if the log looks like
 * this:
 *
 * 100  LNA
 * 200  delete of LNA
 *
 * then LSN 200 will be considered obsolete when this file is processed by the
 * cleaner. After all, only two things can happen: (1) the txn commits, and we
 * don't need LSN 200, because we can wipe this LN out of the tree, or (2) the
 * txn aborts, and we don't need LSN 200, because we are going to revert to LSN
 * 100/LNA.
 *
 * We set PD for the entry of a deleted LN at the time of the operation, and we
 * clear PD if the transaction aborts.  There is no real danger that this log
 * entry will be processed by the cleaner before it's committed, because
 * cleaning can only happen after the first active LSN.
 *
 * Just as in the first use of KD above, setting PD is necessary to avoid a
 * fetch error, when the file is deleted by the cleaner but the entry
 * containing the deleted LN has not been deleted by the INCompressor.
 *
 * PD is also set in replication rollback, when LNs are marked as
 * invisible.
 *
 * When LSN locking was implemented (see CursorImpl.lockLN), the PD flag took
 * on additional meaning.  PD is used to determine whether an LN is deleted
 * without fetching it, and therefore is relied on to be transactionally
 * correct.
 *
 * In addition to the setting and use of the KD/PD flags described above, the
 * situation is complicated by the fact that we must restore the state of these
 * flags during abort, recovery, and set them properly during slot reuse.
 *
 * We have been meaning to consider whether PD and KD can be consolidated into
 * one flag: simply the Deleted flag.  The Deleted flag would be set in the
 * same way as PD is currently set, as well as the second use of KD described
 * above (when the LSN is NULL_LSN after an insertion error).  The use of KD
 * and PD for invisible entries and recovery rollback should also be
 * considered.
 *
 * If we consolidate the two flags and set the Deleted flag during a delete
 * operation (like PD), we'll have to remove optimizations (in CursorImpl for
 * example) that consider a slot deleted when KD is set.  Since KD is rarely
 * set currently, this shouldn't have a noticeable performance impact.
 */
public class IN extends Node implements Comparable<IN>, LatchContext {

    private static final String BEGIN_TAG = "<in>";
    private static final String END_TAG = "</in>";
    private static final String TRACE_SPLIT = "Split:";
    private static final String TRACE_DELETE = "Delete:";

    private static final int BYTES_PER_LSN_ENTRY = 4;
    public static final int MAX_FILE_OFFSET = 0xfffffe;
    private static final int THREE_BYTE_NEGATIVE_ONE = 0xffffff;

    /*
     * Levels:
     * The mapping tree has levels in the 0x20000 -> 0x2ffff number space.
     * The main tree has levels in the 0x10000 -> 0x1ffff number space.
     * The duplicate tree levels are in 0-> 0xffff number space.
     */
    public static final int DBMAP_LEVEL = 0x20000;
    public static final int MAIN_LEVEL = 0x10000;
    public static final int LEVEL_MASK = 0x0ffff;
    public static final int MIN_LEVEL = -1;
    public static final int MAX_LEVEL = Integer.MAX_VALUE;
    public static final int BIN_LEVEL = MAIN_LEVEL | 1;

    /* Used to indicate that an exact match was found in findEntry. */
    public static final int EXACT_MATCH = (1 << 16);

    /* Used to indicate that an insert was successful. */
    public static final int INSERT_SUCCESS = (1 << 17);

    /*
     * A bit flag set in the return value of partialEviction() to indicate
     * whether the IN is evictable or not.
     */
    public static final long NON_EVICTABLE_IN = (1L << 62);

    /*
     * Boolean properties of an IN, encoded as bits inside the flags
     * data member.
     */
    private static final int IN_DIRTY_BIT = 0x1;
    private static final int IN_RECALC_TOGGLE_BIT = 0x2;
    private static final int IN_IS_ROOT_BIT = 0x4;
    private static final int HAS_CACHED_CHILDREN_BIT = 0x8;
    private static final int IN_DIRTY_LRU_BIT = 0x10;
    private static final int IN_DELTA_BIT = 0x20;

    /* Tracing for LRU-related ops */
    private static final boolean traceLRU = false;
    private static final boolean traceDeltas = false;
    private static final Level traceLevel = Level.INFO;

    DatabaseImpl databaseImpl;

    private int level;

    /* The unique id of this node. */
    long nodeId;

    int flags; // not persistent, except from the root bit

    /*
     * The identifier key is a key that can be used used to search for this IN.
     * Initially it is the key of the zeroth slot, but insertions prior to slot
     * zero make this no longer true.  It is always equal to some key in the
     * IN, and therefore it is changed by BIN.compress when removing slots.
     */
    private byte[] identifierKey;

    int nEntries;

    byte[] entryStates;

    /*
     * entryKeyVals contains the keys in their entirety if key prefixing is not
     * being used. If prefixing is enabled, then keyPrefix contains the prefix
     * and entryKeyVals contains the suffixes.
     */
    INKeyRep entryKeyVals;
    byte[] keyPrefix;

    /*
     * The following entryLsnXXX fields are used for storing LSNs.  There are
     * two possible representations: a byte array based rep, and a long array
     * based one.  For compactness, the byte array rep is used initially.  A
     * single byte[] that uses four bytes per LSN is used. The baseFileNumber
     * field contains the lowest file number of any LSN in the array.  Then for
     * each entry (four bytes each), the first byte contains the offset from
     * the baseFileNumber of that LSN's file number.  The remaining three bytes
     * contain the file offset portion of the LSN.  Three bytes will hold a
     * maximum offset of 16,777,214 (0xfffffe), so with the default JE log file
     * size of 10,000,000 bytes this works well.
     *
     * If either (1) the difference in file numbers exceeds 127
     * (Byte.MAX_VALUE) or (2) the file offset is greater than 16,777,214, then
     * the byte[] based rep mutates to a long[] based rep.
     *
     * In the byte[] rep, DbLsn.NULL_LSN is represented by setting the file
     * offset bytes for a given entry to -1 (0xffffff).
     *
     * Note: A compact representation will be changed to the non-compact one,
     * if needed, but in the current implementation, the reverse mutation
     * (from long to compact) never takes place.
     */
    long baseFileNumber;
    byte[] entryLsnByteArray;
    long[] entryLsnLongArray;
    public static boolean disableCompactLsns; // DbCacheSize only

    /*
     * The children of this IN. Only the ones that are actually in the cache
     * have non-null entries. Specialized sparse array represents are used to
     * represent the entries. The representation can mutate as modifications
     * are made to it.
     */
    INTargetRep entryTargets;

    long inMemorySize;

    /*
     * accumluted memory budget delta.  Once this exceeds
     * MemoryBudget.ACCUMULATED_LIMIT we inform the MemoryBudget that a change
     * has occurred.  See SR 12273.
     */
    private int accumulatedDelta = 0;

    /*
     * Max allowable accumulation of memory budget changes before MemoryBudget
     * should be updated. This allows for consolidating multiple calls to
     * updateXXXMemoryBudget() into one call.  Not declared final so that the
     * unit tests can modify it.  See SR 12273.
     */
    public static int ACCUMULATED_LIMIT = 1000;

    private boolean inListResident; // true if this IN is on the IN list

    /**
     * References to the next and previous nodes in an LRU list. If the node
     * is not in any LRUList, both of these will be null. If the node is at
     * the front/back of an LRUList, prevLRUNode/nextLRUNode will point to
     * the node itself.
     */
    private IN nextLRUNode = null;
    private IN prevLRUNode = null;

    /*
     * Let L be the most recently written logrec for this IN instance.
     * (a) If this is a UIN, lastFullVersion is the lsn of L.
     * (b) If this is a BIN instance and L is a full-version logrec,
     *     lastFullVersion is the lsn of L.
     * (c) If this is a BIN instance and L is a delta logrec, lastFullVersion
     *     is the lsn of the most recently written full-version logrec for the
     *     same BIN.
     *
     * Notice that this is a persistent field, but except from case (c), when
     * reading a logrec L, it is set not to the value found in L, but to the
     * lsn of L. This is why its read/write is managed by the INLogEntry class
     * rather than the IN readFromLog/writeFromLog methods.
     */
    long lastFullVersion = DbLsn.NULL_LSN;

    /*
     * A sequence of obsolete info that cannot be counted as obsolete until an
     * ancestor IN is logged non-provisionally.
     */
    private PackedObsoleteInfo provisionalObsolete;

    /* See convertDupKeys. */
    private boolean needDupKeyConversion;

    private int pinCount = 0;

    protected SharedLatch latch;

    private long generation;

    private TestHook fetchINHook;

    /**
     * Create an empty IN, with no node ID, to be filled in from the log.
     */
    public IN() {
        init(null, Key.EMPTY_KEY, 0, 0);
    }

    /**
     * Create a new IN.
     */
    public IN(
        DatabaseImpl dbImpl,
        byte[] identifierKey,
        int capacity,
        int level) {

        nodeId = dbImpl.getEnv().getNodeSequence().getNextLocalNodeId();

        init(dbImpl, identifierKey, capacity,
             generateLevel(dbImpl.getId(), level));

        initMemorySize();
    }

    /**
     * For Sizeof.
     */
    public IN(@SuppressWarnings("unused") SizeofMarker marker) {

        /*
         * Set all variable fields to null, since they are not part of the
         * fixed overhead.
         */
        entryTargets = null;
        entryKeyVals = null;
        keyPrefix = null;
        entryLsnByteArray = null;
        entryLsnLongArray = null;
        entryStates = null;

        latch = LatchFactory.createSharedLatch(
            LatchSupport.DUMMY_LATCH_CONTEXT, isAlwaysLatchedExclusively());

        /*
         * Use the latch to force it to grow to "runtime size".
         */
        latch.acquireExclusive();
        latch.release();
        latch.acquireExclusive();
        latch.release();
    }

    /**
     * Create a new IN.  Need this because we can't call newInstance() without
     * getting a 0 for nodeId.
     */
    protected IN createNewInstance(byte[] identifierKey,
                                   int maxEntries,
                                   int level) {
        return new IN(databaseImpl, identifierKey, maxEntries, level);
    }

    /**
     * Initialize IN object.
     */
    protected void init(DatabaseImpl db,
                        @SuppressWarnings("hiding")
                        byte[] identifierKey,
                        int initialCapacity,
                        @SuppressWarnings("hiding")
                        int level) {
        setDatabase(db);
        latch = LatchFactory.createSharedLatch(
            this, isAlwaysLatchedExclusively());
        generation = 0;
        flags = 0;
        nEntries = 0;
        this.identifierKey = identifierKey;
        entryTargets = INTargetRep.NONE;
        entryKeyVals = new INKeyRep.Default(initialCapacity);
        keyPrefix = null;
        baseFileNumber = -1;

        /*
         * Normally we start out with the compact LSN rep and then mutate to
         * the long rep when needed.  But for some purposes (DbCacheSize) we
         * start out with the long rep and never use the compact rep.
         */
        if (disableCompactLsns) {
            entryLsnByteArray = null;
            entryLsnLongArray = new long[initialCapacity];
        } else {
            entryLsnByteArray = new byte[initialCapacity << 2];
            entryLsnLongArray = null;
        }

        entryStates = new byte[initialCapacity];
        this.level = level;
        inListResident = false;
    }

    @Override
    public final boolean isIN() {
        return true;
    }

    @Override
    public final boolean isUpperIN() {
        return !isBIN();
    }

    @Override
    public final String getLatchName() {
        return shortClassName() + getNodeId();
    }

    public final String getLatchString() {
        return latch.toString();
    }

    @Override
    public final int getLatchTimeoutMs() {
        return databaseImpl.getEnv().getLatchTimeoutMs();
    }

    @Override
    public final LatchTable getLatchTable() {
        return LatchSupport.btreeLatchTable;
    }

    /*
     * Return whether the shared latch for this kind of node should be of the
     * "always exclusive" variety.  Presently, only IN's are actually latched
     * shared.  BINs are latched exclusive only.
     */
    boolean isAlwaysLatchedExclusively() {
        return false;
    }

    /**
     * Latch this node if it is not latched by another thread, optionally
     * setting the generation if the latch succeeds.
     */
    public final boolean latchNoWait(CacheMode cacheMode)
        throws DatabaseException {

        if (latch.acquireExclusiveNoWait()) {
            setGeneration(cacheMode);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Latch this node if it is not latched by another thread, and set the
     * generation if the latch succeeds.
     */
    public final boolean latchNoWait()
        throws DatabaseException {

        return latchNoWait(CacheMode.DEFAULT);
    }

    /**
     * Latch this node exclusive, optionally setting the generation.
     */
    public void latch(CacheMode cacheMode)
        throws DatabaseException {

        latch.acquireExclusive();
        setGeneration(cacheMode);
    }

    /**
     * Latch this node exclusive and set the generation.
     */
    public final void latch()
        throws DatabaseException {

        latch(CacheMode.DEFAULT);
    }

    /**
     * Latch this node shared, optionally setting the generation.
     */
    @Override
    public void latchShared(CacheMode cacheMode)
        throws DatabaseException {

        latch.acquireShared();
        setGeneration(cacheMode);
    }

    /**
     * Latch this node shared and set the generation.
     */
    @Override
    public final void latchShared()
        throws DatabaseException {

        latchShared(CacheMode.DEFAULT);
    }

    /**
     * Release the latch on this node.
     */
    @Override
    public final void releaseLatch() {
        latch.release();
    }

    /**
     * Release the latch on this node if it is owned.
     */
    public final void releaseLatchIfOwner() {
        latch.releaseIfOwner();
    }

    /**
     * @return true if this thread holds the IN's latch
     */
    public final boolean isLatchOwner() {
        return latch.isOwner();
    }

    public final boolean isLatchExclusiveOwner() {
        return latch.isExclusiveOwner();
    }

    /* For unit testing. */
    public final int getLatchNWaiters() {
        return latch.getNWaiters();
    }

    public final long getGeneration() {
        return generation;
    }

    public final void setGeneration(CacheMode cacheMode) {

        final Evictor evictor = getEvictor();

        switch (cacheMode) {
        case DEFAULT:
        case EVICT_LN:
            generation = Generation.getNextGeneration();

            if (isBIN() || !hasCachedChildrenFlag()) {
                assert(isBIN() || !hasResidentChildren());
                if (evictor != null) {
                    evictor.moveBack(this);
                }
            }

            break;
        case UNCHANGED:
            break;
        case KEEP_HOT:
            generation = Long.MAX_VALUE;

            if (isBIN() || !hasCachedChildrenFlag()) {
                assert(isBIN() || !hasResidentChildren());
                if (evictor != null) {
                    evictor.moveBack(this);
                }
            }

            break;
        case MAKE_COLD:
        case EVICT_BIN:
            if (isBIN()) {
                generation = 0L;
                if (evictor != null) {
                    evictor.moveFront(this);
                }
            }
            break;
        default:
            throw unexpectedState(
                "unknown cacheMode: " + cacheMode);
        }
    }

    public final void setGeneration(long newGeneration) {
        generation = newGeneration;
    }

    public final synchronized void pin() {
        assert(isLatchOwner());
        assert(pinCount >= 0);
        ++pinCount;
    }

    public final synchronized void unpin() {
        assert(pinCount > 0);
        --pinCount;
    }

    public final synchronized boolean isPinned() {
        assert(isLatchExclusiveOwner());
        assert(pinCount >= 0);
        return pinCount > 0;
    }

    /**
     * Get the database for this IN.
     */
    public final DatabaseImpl getDatabase() {
        return databaseImpl;
    }

    /**
     * Set the database reference for this node.
     */
    public final void setDatabase(DatabaseImpl db) {
        databaseImpl = db;
    }

    /*
     * Get the database id for this node.
     */
    public final DatabaseId getDatabaseId() {
        return databaseImpl.getId();
    }

    @Override
    public final EnvironmentImpl getEnvImplForFatalException() {
        return databaseImpl.getEnv();
    }

    public final EnvironmentImpl getEnv() {
        return databaseImpl.getEnv();
    }

    protected final Evictor getEvictor() {
        return databaseImpl.getEnv().getEvictor();
    }

    /**
     * Convenience method to return the database key comparator.
     */
    public final Comparator<byte[]> getKeyComparator() {
        return databaseImpl.getKeyComparator();
    }

    @Override
    public final int getLevel() {
        return level;
    }

    public final int getNormalizedLevel() {
        return level & LEVEL_MASK;
    }

    private static int generateLevel(DatabaseId dbId, int newLevel) {
        if (dbId.equals(DbTree.ID_DB_ID)) {
            return newLevel | DBMAP_LEVEL;
        } else {
            return newLevel | MAIN_LEVEL;
        }
    }

    public final long getNodeId() {
        return nodeId;
    }

    /* For unit tests only. */
    final void setNodeId(long nid) {
        nodeId = nid;
    }

    /**
     * We would like as even a hash distribution as possible so that the
     * Evictor's LRU is as accurate as possible.  ConcurrentHashMap takes the
     * value returned by this method and runs its own hash algorithm on it.
     * So a bit complement of the node ID is sufficent as the return value and
     * is a little better than returning just the node ID.  If we use a
     * different container in the future that does not re-hash the return
     * value, we should probably implement the Wang-Jenkins hash function here.
     */
    @Override
    public final int hashCode() {
        return (int) ~getNodeId();
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof IN)) {
            return false;
        }
        IN in = (IN) obj;
        return (this.getNodeId() == in.getNodeId());
    }

    /**
     * Sort based on equality key.
     */
    public final int compareTo(IN argIN) {
        long argNodeId = argIN.getNodeId();
        long myNodeId = getNodeId();

        if (myNodeId < argNodeId) {
            return -1;
        } else if (myNodeId > argNodeId) {
            return 1;
        } else {
            return 0;
        }
    }

    public final boolean getDirty() {
        return (flags & IN_DIRTY_BIT) != 0;
    }

    /* public for unit tests */
    public final void setDirty(boolean dirty) {
        if (dirty) {
            flags |= IN_DIRTY_BIT;
        } else {
            flags &= ~IN_DIRTY_BIT;
        }
    }

    @Override
    public final boolean isBINDelta() {
        assert(isUpperIN() || isLatchOwner());
        return (flags & IN_DELTA_BIT) != 0;
    }

    /*
     * This version of isBINDelta() takes a checkLatched param to allow
     * for cases where it is ok to call the method without holding the
     * BIN latch (e.g. in single-threaded tests, or when the BIN is not
     * attached to the tree (and thus inaccessible from other threads)).
     */
    @Override
    public final boolean isBINDelta(boolean checkLatched) {
        assert(!checkLatched || isUpperIN() || isLatchOwner());
        return (flags & IN_DELTA_BIT) != 0;
    }

    final void setBINDelta(boolean delta) {
        if (delta) {
            flags |= IN_DELTA_BIT;
        } else {
            flags &= ~IN_DELTA_BIT;
        }
    }

    public final boolean getRecalcToggle() {
        return (flags & IN_RECALC_TOGGLE_BIT) != 0;
    }

    public final void setRecalcToggle(boolean toggle) {
        if (toggle) {
            flags |= IN_RECALC_TOGGLE_BIT;
        } else {
            flags &= ~IN_RECALC_TOGGLE_BIT;
        }
    }

    public final boolean isRoot() {
        return (flags & IN_IS_ROOT_BIT) != 0;
    }

    public final boolean isDbRoot() {
        return (flags & IN_IS_ROOT_BIT) != 0;
    }

    final void setIsRoot(boolean isRoot) {
        setIsRootFlag(isRoot);
        setDirty(true);
    }

    private final void setIsRootFlag(boolean isRoot) {
        if (isRoot) {
            flags |= IN_IS_ROOT_BIT;
        } else {
            flags &= ~IN_IS_ROOT_BIT;
        }
    }

    public final boolean hasCachedChildrenFlag() {
        return (flags & HAS_CACHED_CHILDREN_BIT) != 0;
    }

    private final void setHasCachedChildrenFlag(boolean value) {
        if (value) {
            flags |= HAS_CACHED_CHILDREN_BIT;
        } else {
            flags &= ~HAS_CACHED_CHILDREN_BIT;
        }
    }

    public final boolean isInDirtyLRU() {
        return (flags & IN_DIRTY_LRU_BIT) != 0;
    }

    /* public for unit tests */
    public final void setInDirtyLRU(boolean value) {
        if (value) {
            flags |= IN_DIRTY_LRU_BIT;
        } else {
            flags &= ~IN_DIRTY_LRU_BIT;
        }
    }

    /**
     * @return the identifier key for this node.
     */
    public final byte[] getIdentifierKey() {
        return identifierKey;
    }

    /**
     * Set the identifier key for this node.
     *
     * @param key - the new identifier key for this node.
     */
    public final void setIdentifierKey(byte[] key) {

        assert(!isBINDelta());

        /*
         * The identifierKey is "intentionally" not kept track of in the
         * memory budget.  If we did, then it would look like this:

         int oldIDKeySz = (identifierKey == null) ?
                           0 :
                           MemoryBudget.byteArraySize(identifierKey.length);

         int newIDKeySz = (key == null) ?
                           0 :
                           MemoryBudget.byteArraySize(key.length);
         updateMemorySize(newIDKeySz - oldIDKeySz);

         */
        identifierKey = key;
        setDirty(true);
    }

    /**
     * @return the number of entries in this node.
     */
    public final int getNEntries() {
        return nEntries;
    }

    /**
     * @return the maximum number of entries in this node.
     *
     * Overriden by TestIN in INEntryTestBase.java
     */
    public int getMaxEntries() {
        return entryStates.length;
    }

    public final byte getState(int idx) {
        return entryStates[idx];
    }

    /**
     * @return true if the object is dirty.
     */
    final boolean isDirty(int idx) {
        return ((entryStates[idx] & EntryStates.DIRTY_BIT) != 0);
    }

    /**
     * @return true if the idx'th entry has been deleted, although the
     * transaction that performed the deletion may not be committed.
     */
    public final boolean isEntryPendingDeleted(int idx) {
        return ((entryStates[idx] & EntryStates.PENDING_DELETED_BIT) != 0);
    }

    /**
     * Set pendingDeleted to true.
     */
    public final void setPendingDeleted(int idx) {

        entryStates[idx] |= EntryStates.PENDING_DELETED_BIT;
        entryStates[idx] |= EntryStates.DIRTY_BIT;
    }

    /**
     * Set pendingDeleted to false.
     */
    public final void clearPendingDeleted(int idx) {

        entryStates[idx] &= EntryStates.CLEAR_PENDING_DELETED_BIT;
        entryStates[idx] |= EntryStates.DIRTY_BIT;
    }

    /**
     * @return true if the idx'th entry is deleted for sure.  If a transaction
     * performed the deletion, it has been committed.
     */
    public final boolean isEntryKnownDeleted(int idx) {
        return ((entryStates[idx] & EntryStates.KNOWN_DELETED_BIT) != 0);
    }

    /**
     * Set knownDeleted to true.
     */
    void setKnownDeleted(int idx) {

        entryStates[idx] |= EntryStates.KNOWN_DELETED_BIT;
        entryStates[idx] |= EntryStates.DIRTY_BIT;
        setDirty(true);
    }

    /**
     * Set knownDeleted to false.
     */
    public final void clearKnownDeleted(int idx) {

        entryStates[idx] &= EntryStates.CLEAR_KNOWN_DELETED_BIT;
        entryStates[idx] |= EntryStates.DIRTY_BIT;
        setDirty(true);
    }

    /*
     * In the future we may want to move the following static methods to an
     * EntryState utility class and share all state bit twidling among IN,
     * ChildReference, and DeltaInfo.
     */

    /**
     * Returns true if the given state is known deleted.
     */
    static boolean isStateKnownDeleted(byte state) {
        return ((state & EntryStates.KNOWN_DELETED_BIT) != 0);
    }

    /**
     * Returns true if the given state is pending deleted.
     */
    static boolean isStatePendingDeleted(byte state) {
        return ((state & EntryStates.PENDING_DELETED_BIT) != 0);
    }

    /* For unit testing */
    public final INKeyRep getKeyVals() {
        return entryKeyVals;
    }

    /**
     * Return the idx'th key. If prefixing is enabled, construct a new byte[]
     * containing the prefix and suffix. If prefixing is not enabled, just
     * return the current byte[] in entryKeyVals[].
     */
    public final byte[] getKey(int idx) {

        byte[] suffix = entryKeyVals.get(idx);
        if (suffix == null) {
            suffix = Key.EMPTY_KEY;
        }

        if (keyPrefix == null) {
            return suffix;
        }

        final int prefixLen = keyPrefix.length;
        if (prefixLen == 0) {
            return suffix;
        }

        final int suffixLen = suffix.length;
        final byte[] key = new byte[prefixLen + suffixLen];
        System.arraycopy(keyPrefix, 0, key, 0, prefixLen);
        System.arraycopy(suffix, 0, key, prefixLen, suffixLen);
        return key;
    }

    /**
     * Sets the key in the idx-th slot of this node, if this node is a BIN, the
     * idx-th slot "points" to an LN, and the new key is not identical to the
     * current key in the slot [#15704]
     *
     * This method is called when an LN is fetched in order to ensure the key
     * slot is transactionally correct. A key can change in 3 circumstances,
     * when a key comparator is configured that may not compare all bytes of
     * the key:
     *
     * 1) The user calls Cursor.putCurrent to update the data of a duplicate
     * data item.  CursorImpl.putCurrent will call this method indirectly to
     * update the key.
     *
     * 2) A transaction aborts or a BIN becomes out of date due to the
     * non-transactional nature of INs.  The Node is set to null during abort
     * and recovery.  IN.fetchCurrent will call this method indirectly to
     * update the key.
     *
     * 3) A slot for a deleted LN is reused.  The key in the slot is updated
     * by IN.updateEntry along with the node and LSN.
     *
     * Note that transaction abort and recovery of BIN entries may cause
     * incorrect keys to be present in the tree, since these entries are
     * non-transactional.  However, an incorrect key in a BIN slot may only be
     * present when the node in that slot is null.  Undo/redo sets the node to
     * null.  When the LN node is fetched, the key in the slot is set to the
     * LN's key, which is the source of truth and is transactionally correct.
     *
     * @param newKey is the key to set in the slot and is the LN key.  It may
     * be null if it is known that the key cannot be changed (as in putCurrent
     * in a BIN).  It must be null if the node is not an LN.
     *
     * @return true if a multi-slot change was made and the complete IN memory
     * size must be updated.
     */
    private boolean setLNSlotKey(int idx, byte[] newKey) {

        assert(newKey == null || isBIN());

        /*
         * The new key may be null if a dup LN was deleted, in which case there
         * is no need to update it.  There is no need to compare keys if there
         * is no comparator configured, since a key cannot be changed when the
         * default comparator is used.
         */
        if (newKey != null &&
            getKeyComparator() != null &&
            !Arrays.equals(newKey, getKey(idx))) {
            setDirty(true);
            return setKeyAndDirty(idx, newKey);
        } else {
            return false;
        }
    }

    /**
     * Set the idx'th key.
     *
     * @return true if a multi-slot change was made and the complete IN memory
     * size must be updated.
     */
    private boolean setKeyAndDirty(int idx, byte[] keyVal) {
        entryStates[idx] |= EntryStates.DIRTY_BIT;
        return setKeyAndPrefix(idx, keyVal);
    }

    /*
     * Set the key at idx and adjust the key prefix if necessary.
     *
     * @return true if a multi-slot change was made and the complete IN memory
     * size must be updated.
     */
    private boolean setKeyAndPrefix(int idx, byte[] keyVal) {

        /*
         * Only compute key prefix if prefixing is enabled and there's an
         * existing prefix.
         */
        if (databaseImpl.getKeyPrefixing() && keyPrefix != null) {
            if (!compareToKeyPrefix(keyVal)) {

                /*
                 * The new key doesn't share the current prefix, so recompute
                 * the prefix and readjust all the existing suffixes.
                 */
                byte[] newPrefix = computeKeyPrefix(idx);
                if (newPrefix != null) {
                    /* Take the new key into consideration for new prefix. */
                    newPrefix = Key.createKeyPrefix(newPrefix, keyVal);
                }
                recalcSuffixes(newPrefix, keyVal, idx);
                return true;
            }

            final INKeyRep.Type prevRepType = entryKeyVals.getType();

            entryKeyVals = entryKeyVals.set(
                idx, computeKeySuffix(keyPrefix, keyVal), this);

            return prevRepType != entryKeyVals.getType();
        }

        if (keyPrefix != null) {

            /*
             * Key prefixing has been turned off on this database, but there
             * are existing prefixes. Remove prefixes for this IN.
             */
            recalcSuffixes(new byte[0], keyVal, idx);
            return true;
        } else {
            final INKeyRep.Type oldRepType = entryKeyVals.getType();
            entryKeyVals = entryKeyVals.set(idx, keyVal, this);
            return oldRepType != entryKeyVals.getType();
        }
    }

    /*
     * Iterate over all keys in this IN and recalculate their suffixes based on
     * newPrefix.  If keyVal and idx are supplied, it means that entry[idx] is
     * about to be changed to keyVal so use that instead of
     * entryKeyVals.get(idx) when computing the new suffixes. If idx is < 0,
     * and keyVal is null, then recalculate suffixes for all entries in this.
     */
    private void recalcSuffixes(byte[] newPrefix, byte[] keyVal, int idx) {

        for (int i = 0; i < nEntries; i++) {
            byte[] curKey = (i == idx ? keyVal : getKey(i));
            entryKeyVals =
                entryKeyVals.set(i, computeKeySuffix(newPrefix, curKey), this);
        }
        setKeyPrefix(newPrefix);
    }

    /*
     * Given a prefix and a key, return the suffix portion of keyVal.
     */
    private byte[] computeKeySuffix(byte[] newPrefix, byte[] keyVal) {
        int prefixLen = (newPrefix == null ? 0 : newPrefix.length);

        if (prefixLen == 0) {
            return keyVal;
        }

        int suffixLen = keyVal.length - prefixLen;
        byte[] ret = new byte[suffixLen];
        System.arraycopy(keyVal, prefixLen, ret, 0, suffixLen);
        return ret;
    }

    /**
     * Forces computation of the key prefix, without requiring a split.
     * Is public for use by DbCacheSize.
     */
    public final void recalcKeyPrefix() {

        assert(!isBINDelta());

        recalcSuffixes(computeKeyPrefix(-1), null, -1);
    }

    /*
     * Computes a key prefix based on all the keys in 'this'.  Return null if
     * the IN is empty or prefixing is not enabled or there is no common
     * prefix for the keys.
     */
    private byte[] computeKeyPrefix(int excludeIdx) {
        if (!databaseImpl.getKeyPrefixing() ||
            nEntries <= 1) {
            return null;
        }

        int firstIdx = (excludeIdx == 0) ? 1 : 0;
        byte[] curPrefixKey = getKey(firstIdx);
        int prefixLen = curPrefixKey.length;

        /*
         * Only need to look at first and last entries when keys are ordered
         * byte-by-byte.  But when there is a comparator, keys are not
         * necessarily ordered byte-by-byte.  [#21328]
         */
        boolean byteOrdered;
        if (true) {
            /* Disable optimization for now.  Needs testing. */
            byteOrdered = false;
        } else {
            byteOrdered = (databaseImpl.getKeyComparator() == null);
        }
        if (byteOrdered) {
            int lastIdx = nEntries - 1;
            if (lastIdx == excludeIdx) {
                lastIdx -= 1;
            }
            if (lastIdx > firstIdx) {
                byte[] lastKey = getKey(lastIdx);
                int newPrefixLen = Key.getKeyPrefixLength
                    (curPrefixKey, prefixLen, lastKey);
                if (newPrefixLen < prefixLen) {
                    curPrefixKey = lastKey;
                    prefixLen = newPrefixLen;
                }
            }
        } else {
            for (int i = firstIdx + 1; i < nEntries; i++) {
                if (i == excludeIdx) {
                    continue;
                }
                byte[] curKey = getKey(i);
                int newPrefixLen = Key.getKeyPrefixLength
                    (curPrefixKey, prefixLen, curKey);
                if (newPrefixLen < prefixLen) {
                    curPrefixKey = curKey;
                    prefixLen = newPrefixLen;
                }
            }
        }

        byte[] ret = new byte[prefixLen];
        System.arraycopy(curPrefixKey, 0, ret, 0, prefixLen);

        return ret;
    }

    public final byte[] getKeyPrefix() {
        return keyPrefix;
    }

    /*
     * For unit testing only
     */
    public final boolean hasKeyPrefix() {
        return keyPrefix != null;
    }


    /* This has default protection for access by the unit tests. */
    final void setKeyPrefix(byte[] keyPrefix) {

        assert databaseImpl != null;

        int prevLength = (this.keyPrefix == null) ? 0 : this.keyPrefix.length;
        this.keyPrefix = keyPrefix;
        /* Update the memory budgeting to reflect changes in the key prefix. */
        int currLength = (keyPrefix == null) ? 0 : keyPrefix.length;
        updateMemorySize(prevLength, currLength);
    }

    /*
     * Returns whether the given newKey is a prefix of, or equal to, the
     * current keyPrefix.
     *
     * This has default protection for the unit tests.
     */
    final boolean compareToKeyPrefix(byte[] newKey) {

        if (keyPrefix == null || keyPrefix.length == 0) {
            return false;
        }

        int newKeyLen = newKey.length;
        for (int i = 0; i < keyPrefix.length; i++) {
            if (i < newKeyLen &&
                keyPrefix[i] == newKey[i]) {
                continue;
            } else {
                return false;
            }
        }

        return true;
    }

    /*
     * For debugging.
     */
    final boolean verifyKeyPrefix() {

        byte[] computedKeyPrefix = computeKeyPrefix(-1);
        if (keyPrefix == null) {
            return computedKeyPrefix == null;
        }

        if (computedKeyPrefix == null ||
            computedKeyPrefix.length < keyPrefix.length) {
            System.out.println("VerifyKeyPrefix failed");
            System.out.println(dumpString(0, false));
            return false;
        }
        for (int i = 0; i < keyPrefix.length; i++) {
            if (keyPrefix[i] != computedKeyPrefix[i]) {
                System.out.println("VerifyKeyPrefix failed");
                System.out.println(dumpString(0, false));
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether the given key is greater than or equal to the first key
     * in the IN and less than or equal to the last key in the IN.  This method
     * is used to determine whether a key to be inserted belongs in this IN,
     * without doing a tree search.  If false is returned it is still possible
     * that the key belongs in this IN, but a tree search must be performed to
     * find out.
     */
    public final boolean isKeyInBounds(byte[] keyVal) {

        assert(!isBINDelta());

        if (nEntries < 2) {
            return false;
        }

        Comparator<byte[]> userCompareToFcn = getKeyComparator();
        int cmp;
        byte[] myKey;

        /* Compare key given to my first key. */
        myKey = getKey(0);
        cmp = Key.compareKeys(keyVal, myKey, userCompareToFcn);
        if (cmp < 0) {
            return false;
        }

        /* Compare key given to my last key. */
        myKey = getKey(nEntries - 1);
        cmp = Key.compareKeys(keyVal, myKey, userCompareToFcn);
        if (cmp > 0) {
            return false;
        }

        return true;
    }

    /**
     * Return the idx'th LSN for this entry.
     *
     * @return the idx'th LSN for this entry.
     */
    public final long getLsn(int idx) {

        if (entryLsnLongArray == null) {
            int offset = idx << 2;
            int fileOffset = getFileOffset(offset);
            if (fileOffset == -1) {
                return DbLsn.NULL_LSN;
            } else {
                return DbLsn.makeLsn((baseFileNumber +
                                      getFileNumberOffset(offset)),
                                     fileOffset);
            }
        } else {
            return entryLsnLongArray[idx];
        }
    }

    /**
     * Set the LSN of the idx'th slot, mark the slot dirty, and update
     * memory consuption. Throw exception if the update is not legitimate.
     *
     * Use default protection for unit tests.
     */
    final void setLsn(int idx, long lsn) {
        setLsn(idx, lsn, true);
    }

    /**
     * Set the LSN of the idx'th slot, mark the slot dirty, and update
     * memory consuption. If "check" is true, throw exception if the
     * update is not legitimate.
     */
    private final void setLsn(int idx, long lsn, boolean check) {

        if (!check || shouldUpdateLsn(getLsn(idx), lsn)) {

            int oldSize = computeLsnOverhead();

            /* setLsnInternal can mutate to an array of longs. */
            setLsnInternal(idx, lsn);

            updateMemorySize(computeLsnOverhead() - oldSize);
            entryStates[idx] |= EntryStates.DIRTY_BIT;
        }
    }

    /*
     * Set the LSN of the idx'th slot. If the current storage for LSNs is the
     * compact one, mutate it to the the non-compact, if necessary.
     */
    final void setLsnInternal(int idx, long value) {

        /* Will implement this in the future. Note, don't adjust if mutating.*/
        //maybeAdjustCapacity(offset);
        if (entryLsnLongArray != null) {
            entryLsnLongArray[idx] = value;
            return;
        }

        int offset = idx << 2;

        if (value == DbLsn.NULL_LSN) {
            setFileNumberOffset(offset, (byte) 0);
            setFileOffset(offset, -1);
            return;
        }

        long thisFileNumber = DbLsn.getFileNumber(value);

        if (baseFileNumber == -1) {
            /* First entry. */
            baseFileNumber = thisFileNumber;
            setFileNumberOffset(offset, (byte) 0);

        } else {

            if (thisFileNumber < baseFileNumber) {
                if (!adjustFileNumbers(thisFileNumber)) {
                    mutateToLongArray(idx, value);
                    return;
                }
                baseFileNumber = thisFileNumber;
            }

            long fileNumberDifference = thisFileNumber - baseFileNumber;
            if (fileNumberDifference > Byte.MAX_VALUE) {
                mutateToLongArray(idx, value);
                return;
            }

            setFileNumberOffset(
                offset, (byte) (thisFileNumber - baseFileNumber));
            //assert getFileNumberOffset(offset) >= 0;
        }

        int fileOffset = (int) DbLsn.getFileOffset(value);
        if (fileOffset > MAX_FILE_OFFSET) {
            mutateToLongArray(idx, value);
            return;
        }

        setFileOffset(offset, fileOffset);
        //assert getLsn(offset) == value;
    }

    private boolean adjustFileNumbers(long newBaseFileNumber) {

        long oldBaseFileNumber = baseFileNumber;
        for (int i = 0;
             i < entryLsnByteArray.length;
             i += BYTES_PER_LSN_ENTRY) {
            if (getFileOffset(i) == -1) {
                continue;
            }

            long curEntryFileNumber =
                oldBaseFileNumber + getFileNumberOffset(i);
            long newCurEntryFileNumberOffset =
                (curEntryFileNumber - newBaseFileNumber);

            if (newCurEntryFileNumberOffset > Byte.MAX_VALUE) {
                long undoOffset = oldBaseFileNumber - newBaseFileNumber;
                for (int j = i - BYTES_PER_LSN_ENTRY;
                     j >= 0;
                     j -= BYTES_PER_LSN_ENTRY) {
                    if (getFileOffset(j) == -1) {
                        continue;
                    }
                    setFileNumberOffset
                        (j, (byte) (getFileNumberOffset(j) - undoOffset));
                    //assert getFileNumberOffset(j) >= 0;
                }
                return false;
            }
            setFileNumberOffset(i, (byte) newCurEntryFileNumberOffset);

            //assert getFileNumberOffset(i) >= 0;
        }
        return true;
    }

    private void setFileNumberOffset(int offset, byte fileNumberOffset) {
        entryLsnByteArray[offset] = fileNumberOffset;
    }

    private byte getFileNumberOffset(int offset) {
        return entryLsnByteArray[offset];
    }

    private void setFileOffset(int offset, int fileOffset) {
        put3ByteInt(offset + 1, fileOffset);
    }

    private int getFileOffset(int offset) {
        return get3ByteInt(offset + 1);
    }

    private void put3ByteInt(int offset, int value) {
        entryLsnByteArray[offset++] = (byte) (value >>> 0);
        entryLsnByteArray[offset++] = (byte) (value >>> 8);
        entryLsnByteArray[offset]   = (byte) (value >>> 16);
    }

    private int get3ByteInt(int offset) {
        int ret = (entryLsnByteArray[offset++] & 0xFF) << 0;
        ret += (entryLsnByteArray[offset++] & 0xFF) << 8;
        ret += (entryLsnByteArray[offset]   & 0xFF) << 16;
        if (ret == THREE_BYTE_NEGATIVE_ONE) {
            ret = -1;
        }

        return ret;
    }

    private void mutateToLongArray(int idx, long value) {
        int nElts = entryLsnByteArray.length >> 2;
        long[] newArr = new long[nElts];
        for (int i = 0; i < nElts; i++) {
            newArr[i] = getLsn(i);
        }
        newArr[idx] = value;
        entryLsnLongArray = newArr;
        entryLsnByteArray = null;
    }

    /**
     * For a deferred write database, ensure that information is not lost when
     * a new LSN is assigned.  Also ensures that a transient LSN is not
     * accidentally assigned to a persistent entry.
     *
     * Because this method uses strict checking, prepareForSlotReuse must
     * sometimes be called when a new logical entry is being placed in a slot,
     * e.g., during an IN split or an LN slot reuse.
     *
     * The following transition is a NOOP and the LSN is not set:
     *   Any LSN to same value.
     * The following transitions are allowed and cause the LSN to be set:
     *   Null LSN to transient LSN
     *   Null LSN to persistent LSN
     *   Transient LSN to persistent LSN
     *   Persistent LSN to new persistent LSN
     * The following transitions should not occur and throw an exception:
     *   Transient LSN to null LSN
     *   Transient LSN to new transient LSN
     *   Persistent LSN to null LSN
     *   Persistent LSN to transient LSN
     */
    private final boolean shouldUpdateLsn(long oldLsn, long newLsn) {

        /* Save a little computation in packing/updating an unchanged LSN. */
        if (oldLsn == newLsn) {
            return false;
        }
        /* The rules for a new null LSN can be broken in a read-only env. */
        if (newLsn == DbLsn.NULL_LSN && getEnv().isReadOnly()) {
            return true;
        }
        /* Enforce LSN update rules.  Assume newLsn != oldLsn. */
        if (databaseImpl.isDeferredWriteMode()) {
            if (oldLsn != DbLsn.NULL_LSN && DbLsn.isTransientOrNull(newLsn)) {
                throw unexpectedState(
                    "DeferredWrite LSN update not allowed" +
                    " oldLsn = " + DbLsn.getNoFormatString(oldLsn) +
                    " newLsn = " + DbLsn.getNoFormatString(newLsn));
            }
        } else {
            if (DbLsn.isTransientOrNull(newLsn)) {
                throw unexpectedState(
                    "LSN update not allowed" +
                    " oldLsn = " + DbLsn.getNoFormatString(oldLsn) +
                    " newLsn = " + DbLsn.getNoFormatString(newLsn));
            }
        }
        return true;
    }

    /* For unit tests. */
    final long[] getEntryLsnLongArray() {
        return entryLsnLongArray;
    }

    /* For unit tests. */
    final byte[] getEntryLsnByteArray() {
        return entryLsnByteArray;
    }

    /* For unit tests. */
    final void initEntryLsn(int capacity) {
        entryLsnLongArray = null;
        entryLsnByteArray = new byte[capacity << 2];
        baseFileNumber = -1;
    }

    /* Will implement this in the future. Note, don't adjust if mutating.*/
    /***
      private void maybeAdjustCapacity(int offset) {
      if (entryLsnLongArray == null) {
      int bytesNeeded = offset + BYTES_PER_LSN_ENTRY;
      int currentBytes = entryLsnByteArray.length;
      if (currentBytes < bytesNeeded) {
      int newBytes = bytesNeeded +
      (GROWTH_INCREMENT * BYTES_PER_LSN_ENTRY);
      byte[] newArr = new byte[newBytes];
      System.arraycopy(entryLsnByteArray, 0, newArr, 0,
      currentBytes);
      entryLsnByteArray = newArr;
      for (int i = currentBytes;
      i < newBytes;
      i += BYTES_PER_LSN_ENTRY) {
      setFileNumberOffset(i, (byte) 0);
      setFileOffset(i, -1);
      }
      }
      } else {
      int currentEntries = entryLsnLongArray.length;
      int idx = offset >> 2;
      if (currentEntries < idx + 1) {
      int newEntries = idx + GROWTH_INCREMENT;
      long[] newArr = new long[newEntries];
      System.arraycopy(entryLsnLongArray, 0, newArr, 0,
      currentEntries);
      entryLsnLongArray = newArr;
      for (int i = currentEntries; i < newEntries; i++) {
      entryLsnLongArray[i] = DbLsn.NULL_LSN;
      }
      }
      }
      }
     ***/

    /**
     * The last logged size is not stored for UINs.
     */
    boolean isLastLoggedSizeStored() {
        return false;
    }

    /**
     * The last logged size is not stored for UINs.
     */
    public void setLastLoggedSize(int idx, int lastLoggedSize) {
    }

    /**
     * The last logged size is not stored for UINs.
     */
    void setLastLoggedSizeUnconditional(int idx, int lastLoggedSize) {
    }

    /**
     * The last logged size is always zero for UINs.
     */
    public int getLastLoggedSize(int idx) {
        return 0;
    }

    /* For unit testing */
    public final INArrayRep<INTargetRep, INTargetRep.Type, Node> getTargets() {
        return entryTargets;
    }

    public final boolean isResident(int idx) {
        return entryTargets.get(idx) != null;
    }

    /**
     * Sets the idx'th target. No need to make dirty, that state only applies
     * to key and LSN.
     *
     * <p>WARNING: This method does not update the memory budget.  The caller
     * must update the budget.</p>
     */
    void setTarget(int idx, Node target) {

        assert isLatchExclusiveOwner() :
        "Not latched for write " + getClass().getName() + " id=" + getNodeId();

        final Evictor evictor = getEvictor();

        Node curChild = entryTargets.get(idx);

        if (target == null) {

            if (isUpperIN() && hasCachedChildrenFlag()) {

                entryTargets = entryTargets.set(idx, target, this);

                /*
                 * If this UIN has just lost its last cached child, set its
                 * hasCachedChildren flag to false and put it back to the
                 * LRU list.
                 */
                if (curChild != null && !hasResidentChildren()) {
                    setHasCachedChildrenFlag(false);
                    if (evictor != null && !isDIN()) {
                        if (traceLRU) {
                            LoggerUtils.envLogMsg(
                                traceLevel, getEnv(),
                                Thread.currentThread().getId() + "-" +
                                Thread.currentThread().getName() +
                                "-" + getEnv().getName() +
                                " setTarget(): " +
                                " Adding UIN " + getNodeId() +
                                " to LRU after detaching chld " +
                                ((IN)curChild).getNodeId());
                        }
                        evictor.addBack(this);
                    }
                }
            } else {
                entryTargets = entryTargets.set(idx, target, this);
            }
        } else {
            if (isUpperIN() && !hasCachedChildrenFlag()) {

                setHasCachedChildrenFlag(true);

                if (evictor != null) {
                    if (traceLRU) {
                        LoggerUtils.envLogMsg(
                            traceLevel, getEnv(),
                            Thread.currentThread().getId() + "-" +
                            Thread.currentThread().getName() +
                            "-" + getEnv().getName() +
                            " setTarget(): " +
                            " Removing UIN " + getNodeId() +
                            " after attaching child " +
                            ((IN)target).getNodeId());
                    }
                    evictor.remove(this);
                }
            }

            entryTargets = entryTargets.set(idx, target, this);
        }
    }

    /**
     * Return the idx'th target.
     */
    public final Node getTarget(int idx) {
        return entryTargets.get(idx);
    }

    /*
     * Returns the idx-th child of "this" upper IN, fetching the child from
     * the log and attaching it to its parent if it is not already attached.
     * This method is used during tree searches.
     *
     * On entry, the parent must be latched already.
     *
     * If the child must be fetched from the log, the parent is unlatched.
     * After the disk read is done, the parent is relatched. However, due to
     * splits, it may not be the correct parent anymore. If so, the method
     * will return null, and the caller is expected to restart the tree search.
     *
     * On return, the parent will be latched, unless null is returned or an
     * exception is thrown.
     *
     * The "searchKey" param is the key that the caller is looking for. It is
     * used by this method in determining if, after a disk read, "this" is
     * still the correct parent for the child. "searchKey" may be null if the
     * caller is doing a LEFT or RIGHT search.
     */
    final IN fetchINWithNoLatch(int idx, byte [] searchKey) {
        return fetchINWithNoLatch(idx, searchKey, null);
    }

    /*
     * This variant of fetchIN() takes a SearchResult as a param, instead of
     * an idx (it is used by Tree.getParentINForChildIN()). The ordinal number
     * of the child to fetch is specified by result.index. result.index will
     * be adjusted by this method if, after a disk read, the ordinal number
     * of the child changes due to insertions, deletions or splits in the
     * parent.
     */
    final IN fetchINWithNoLatch(SearchResult result, byte [] searchKey) {
        return fetchINWithNoLatch(result.index, searchKey, result);
    }

    /*
     * Provides the implementation of the above two methods.
     */
    private IN fetchINWithNoLatch(
        int idx,
        byte [] searchKey,
        SearchResult result) {

        assert(isUpperIN());
        assert(isLatchOwner());

        final EnvironmentImpl envImpl = getEnv();
        boolean isMiss = false;
        boolean success = false;

        IN child = (IN)entryTargets.get(idx);

        if (child == null) {

            isMiss = true;

            final long lsn = getLsn(idx);

            if (lsn == DbLsn.NULL_LSN) {
                throw unexpectedState(makeFetchErrorMsg(
                    "NULL_LSN in upper IN", this, lsn, entryStates[idx]));
            }

            try {
                pin();
                releaseLatch();

                TestHookExecute.doHookIfSet(fetchINHook);

                final WholeEntry wholeEntry =
                    envImpl.getLogManager().
                    getLogEntryAllowInvisibleAtRecovery(lsn);

                final LogEntry logEntry = wholeEntry.getEntry();

                child = (IN) logEntry.getResolvedItem(databaseImpl);

                latch(CacheMode.UNCHANGED);

                /*
                 * The following if statement relies on splits being logged
                 * immediately, or more precisely, the split node and its
                 * new sibling being logged immediately, while both siblings
                 * and their parent are latched exclusively. The reason for
                 * this is as follows: 
                 * 
                 * Let K be the search key. If we are doing a left-deep or
                 * right-deep search, K is -INF or +INF respectively.
                 *
                 * Let P be the parent IN (i.e., "this") and S be the slot at
                 * the idx position before P was unlatched above. Here, we
                 * view slots as independent objects, not identified by their
                 * position in an IN but by some unique (imaginary) and
                 * immutable id assigned to the slot when it is first inserted
                 * into an IN. 
                 *
                 * Before unlatching P, S was the correct slot to follow down
                 * the tree looking for K. After P is unlatched and then
                 * relatched, let S' be the slot at the idx position, if P
                 * still has an idx position. We consider the following 2 cases:
                 *
                 * 1. S' exists and S'.LSN == S.LSN. Then S and S' are the same
                 * (logical) slot (because two different slots can never cover
                 * overlapping ranges of keys, and as a result, can never point
                 * to the same LSN). Then, S is still the correct slot to take
                 * down the tree, unless the range of keys covered by S has
                 * shrunk while P was unlatched. But the only way for S's key
                 * range to shrink is for its child IN to split, which could
                 * not have happened because if it did, the before and after
                 * LSNs of S would be different, given that splits are logged
                 * immediately. We conclude that the set of keys covered by
                 * S after P is relatched is the same or a superset of the keys
                 * covered by S before P was unlatched, and thus S (at the idx
                 * position) is still the correct slot to follow.
                 *
                 * 2. There is no idx position in P or S'.LSN != S.LSN. In
                 * this case we cannot be sure if S' (if it exists) is the
                 * the correct slot to follow. So, we (re)search for K in P
                 * to check if P is still the correct parent and find the
                 * correct slot to follow. If this search lands on the 1st or
                 * last slot in P, we may return null because using the key
                 * info contained in P only, we do not know the full range of
                 * keys covered by those two slots. If null is returned, the
                 * caller is expected to restart the tree search from the root.
                 *
                 * Notice that the if conditions are necessary before calling
                 * findEntry(). Without them, we could get into an infinite
                 * loop of search re-tries in the scenario where nothing changes
                 * in the tree and findEntry always lands on the 1st or last
                 * slot in P. The conditions guarantee that we may restart the
                 * tree search only if something changes with S while P is
                 * unlatched (S moves to a different position or a different
                 * IN or it points to a different LSN).
                 * 
                 * Notice also that if P does not split while it is unlatched,
                 * the range of keys covered by P does not change either. This
                 * implies that the correct slot to follow *must* be inside P,
                 * and as a result, the 1st and last slots in P can be trusted.
                 * Unfortunately, however, we have no way to detecting reliably
                 * whether P splits or not.
                 * 
                 * Special care for DBs in DW mode:
                 *
                 * For DBs in DW mode, special care must be taken because
                 * splits are not immediately logged. So, for DW DBs, to avoid
                 * a call to findEntry() we require that not only S'.LSN ==
                 * S.LSN, but also the the child is not cached. These 2
                 * conditions together guarantee that the child did not split
                 * while P was unlatched, because if the child did split, it
                 * was fetched and cached first, so after P is relatched,
                 * either the child would be still cached, or if it was evicted
                 * after the split, S.LSN would have changed.
                 */
                if (idx >= nEntries ||
                    getLsn(idx) != lsn ||
                    (databaseImpl.isDeferredWriteMode() &&
                     entryTargets.get(idx) != null)) {

                    if (searchKey == null) {
                        return null;
                    }

                    idx = findEntry(searchKey, false, false);

                    if ((idx == 0 || idx == nEntries - 1) &&
                        !isKeyInBounds(searchKey)) {
                        return null;
                    }
                }

                if (result != null) {
                    result.index = idx;
                }

                /*
                 * "this" is still the correct parent and "idx" points to the
                 * correct slot to follow for the search down the tree. But
                 * what we fetched from the log may be out-of-date by now
                 * (because it was fetched and then updated by other threads)
                 * or it may not be the correct child anymore ("idx" was
                 * changed by the findEntry() call above). We check 3 cases:
                 * (a) There is already a cached child at the "idx" position.
                 * In this case, we return whatever is there because it has to
                 * be the most recent version of the appropriate child node.
                 * This is true even when a split or reverse split occurred.
                 * The check for isKeyInBounds above is critical in that case.
                 * (b) There is no cached child at the "idx" slot, but the slot
                 * LSN is not the same as the LSN we read from the log. This is
                 * the case if "idx" was changed by findEntry() or other
                 * threads fetched the same child as this thread, updated it,
                 * and then then evicted it. The child we fetched is obsolete
                 * and should not be attached. For simplicity, we just return
                 * null in this (quite rare) case.
                 * (c) If neither (a) nor (b), we attached the fetched child to
                 * the parent.
                 */
                if (entryTargets.get(idx) != null) {
                    child = (IN)entryTargets.get(idx);

                } else if (getLsn(idx) != lsn) {
                    return null;

                } else {
                    child.postFetchInit(databaseImpl, lsn);
                    attachNode(idx, child, null);
                }

                success = true;

            } catch (FileNotFoundException e) {
                throw new EnvironmentFailureException(
                    envImpl, EnvironmentFailureReason.LOG_FILE_NOT_FOUND,
                    makeFetchErrorMsg(null, this, lsn, entryStates[idx]),
                    e);
            } catch (EnvironmentFailureException e) {
                e.addErrorMessage(makeFetchErrorMsg(
                    null, this, lsn, entryStates[idx]));
                throw e;
            } catch (RuntimeException e) {
                throw new EnvironmentFailureException(
                    envImpl, EnvironmentFailureReason.LOG_INTEGRITY,
                    makeFetchErrorMsg(e.toString(), this, lsn,
                                      entryStates[idx]),
                    e);
            } finally {
                /*
                 * Release the parent latch if null is being returned. In this
                 * case, the parent was unlatched earlier during the disk read,
                 * and as a result, the caller cannot make any assumptions
                 * about the state of the parent.
                 *
                 * If we are returning or throwing out of this try block, the
                 * parent may or may not be latched. So, only release the latch
                 * if it is currently held.
                 */
                if (!success) {
                    if (child != null) {
                        child.incFetchStats(envImpl, isMiss);
                    }
                    releaseLatchIfOwner();
                }

                unpin();
            }
        }

        assert(hasResidentChildren() == hasCachedChildrenFlag());

        child.incFetchStats(envImpl, isMiss);

        return child;
    }

    /*
     * Returns the idx-th child of "this" upper IN, fetching the child from
     * the log and attaching it to its parent if it is not already attached.
     *
     * On entry, the parent must be EX-latched already and it stays EX-latched
     * for the duration of this method and on return (even in case of
     * exceptions).
     *
     * @param idx The slot of the child to fetch.
     */
    public IN fetchIN(int idx) {

        assert(isUpperIN());
        if (!isLatchExclusiveOwner()) {
            throw unexpectedState("EX-latch not held before fetch");
        }

        final EnvironmentImpl envImpl = getEnv();
        boolean isMiss = false;

        IN child = (IN) entryTargets.get(idx);

        if (child == null) {

            final long lsn = getLsn(idx);

            if (lsn == DbLsn.NULL_LSN) {
                throw unexpectedState(makeFetchErrorMsg(
                    "NULL_LSN in upper IN", this, lsn, entryStates[idx]));
            }

            try {
                final WholeEntry wholeEntry =
                    envImpl.getLogManager().
                    getLogEntryAllowInvisibleAtRecovery(lsn);

                final LogEntry logEntry = wholeEntry.getEntry();

                child = (IN) logEntry.getResolvedItem(databaseImpl);

                child.postFetchInit(databaseImpl, lsn);
                attachNode(idx, child, null);
                isMiss = true;

            } catch (FileNotFoundException e) {
                throw new EnvironmentFailureException(
                    envImpl, EnvironmentFailureReason.LOG_FILE_NOT_FOUND,
                    makeFetchErrorMsg(null, this, lsn, entryStates[idx]),
                    e);
            } catch (EnvironmentFailureException e) {
                e.addErrorMessage(makeFetchErrorMsg(
                    null, this, lsn, entryStates[idx]));
                throw e;
            } catch (RuntimeException e) {
                throw new EnvironmentFailureException(
                    envImpl, EnvironmentFailureReason.LOG_INTEGRITY,
                    makeFetchErrorMsg(e.toString(), this, lsn,
                                      entryStates[idx]),
                    e);
            }
        }

        assert(hasResidentChildren() == hasCachedChildrenFlag());

        child.incFetchStats(envImpl, isMiss);

        return child;
    }

    /**
     * Returns the target of the idx'th entry or null if a pendingDeleted or
     * knownDeleted entry has been cleaned.  Note that null can only be
     * returned for a slot that could contain a deleted LN, not other node
     * types and not a DupCountLN since DupCountLNs are never deleted.  Null is
     * also returned for a KnownDeleted slot with a NULL_LSN.
     *
     * An exclusive latch must be held on this BIN.
     *
     * @return the target node or null.
     */
    public final LN fetchLN(int idx, CacheMode cacheMode) {

        return (LN) fetchLN(idx, cacheMode, false);
    }

    /**
     * Must be called only when the LSN being fetched is known to be active
     * (non-obsolete) i.e., will always be present in the log even when the LN
     * is "immediately obsolete".  For example, this is true when the LN is
     * part of an active txn during partial rollback, and when DupConvert reads
     * a log written prior to the "immediately obsolete" implementation.
     *
     * Although this method is called "fetchLN", it may fetch a DIN child of
     * a BIN when called from DupConvert.
     *
     * An exclusive latch must be held on this IN.
     */
    public final LN fetchLNKnownActive(int idx, CacheMode cacheMode) {

        return (LN) fetchLN(idx, cacheMode, true);
    }

    /*
     * This method is the same as fetchLNKnownActive, but it may return either
     * an LN or a DIN child of a BIN. It is meant to be used from DupConvert
     * only.
     */
    public final Node fetchLNOrDIN(int idx, CacheMode cacheMode) {

        return fetchLN(idx, cacheMode, true);
    }

    /*
     * Underlying implementation of the above fetchLNXXX methods.
     */
    final Node fetchLN(int idx, CacheMode cacheMode, boolean knownActive) {

        assert(isBIN());

        if (!isLatchExclusiveOwner()) {
            throw unexpectedState("EX-latch not held before fetch");
        }

        if (isEntryKnownDeleted(idx)) {
            return null;
        }

        final EnvironmentImpl envImpl = getEnv();
        boolean isMiss = false;

        Node targetNode = entryTargets.get(idx);

        if (targetNode == null) {

            final long lsn = getLsn(idx);

            if (lsn == DbLsn.NULL_LSN) {
                throw unexpectedState(
                    makeFetchErrorMsg("NULL_LSN without KnownDeleted",
                                      this, lsn, entryStates[idx]));
            }

            /*
             * Fetch of immediately obsolete LN not allowed, unless it is part
             * of an active txn.  This guard is
             * only very approximate.  We could instead use the firstActiveLsn
             * but that's expensive and probably has lock ordering issues.
             */
            if (databaseImpl.isLNImmediatelyObsolete()) {
                if (!knownActive) {
                    throw unexpectedState(
                        "May not fetch immediately obsolete LN");
                }
                // TODO assert for more expensive check
            }

            try {
                final WholeEntry wholeEntry =
                    envImpl.getLogManager().
                    getLogEntryAllowInvisibleAtRecovery(lsn);

                final LogEntry logEntry = wholeEntry.getEntry();

                /* Ensure keys are transactionally correct. [#15704] */
                byte[] lnSlotKey = null;
                if (logEntry instanceof LNLogEntry) {
                    final LNLogEntry<?> lnEntry = (LNLogEntry<?>) logEntry;
                    lnEntry.postFetchInit(databaseImpl);
                    lnSlotKey = lnEntry.getKey();

                    final Evictor evictor = getEvictor();
                    if (evictor != null &&
                        cacheMode != CacheMode.EVICT_LN) {
                        evictor.moveToMixedLRU(this);
                    }
                }

                targetNode = (Node) logEntry.getResolvedItem(databaseImpl);
                targetNode.postFetchInit(databaseImpl, lsn);
                attachNode(idx, targetNode, lnSlotKey);
                /* Last logged size is not present before log version 9. */
                setLastLoggedSize(idx, wholeEntry.getHeader().getEntrySize());
                isMiss = true;

            } catch (FileNotFoundException e) {
                if (!isEntryKnownDeleted(idx) &&
                    !isEntryPendingDeleted(idx)) {
                    throw new EnvironmentFailureException(
                         envImpl, EnvironmentFailureReason.LOG_FILE_NOT_FOUND,
                         makeFetchErrorMsg(null, this, lsn, entryStates[idx]),
                         e);
                }

                /*
                 * Cleaner got to the log file, so just return null. It is safe
                 * to ignore a deleted file for a KD or PD entry because files
                 * with active txns will not be cleaned.
                 */
                return null;

            } catch (EnvironmentFailureException e) {
                e.addErrorMessage(makeFetchErrorMsg(
                    null, this, lsn, entryStates[idx]));
                throw e;

            } catch (RuntimeException e) {
                throw new EnvironmentFailureException(
                    envImpl, EnvironmentFailureReason.LOG_INTEGRITY,
                    makeFetchErrorMsg(e.toString(), this, lsn,
                                      entryStates[idx]),
                    e);
            }
        }

        targetNode.incFetchStats(envImpl, isMiss);

        return targetNode;
    }

    /**
     * Initialize a node that has been read in from the log.
     */
    @Override
    public final void postFetchInit(DatabaseImpl db, long lastLoggedLsn) {

        commonPostFetchInit(db, lastLoggedLsn);
        convertDupKeys(); // must be after initMemorySize

        db.getEnv().getInMemoryINs().add(this);

        final Evictor evictor = getEvictor();

        if (evictor != null && isIN() && !isDIN() && !isDBIN()) {
            if (isUpperIN() && traceLRU) {
                LoggerUtils.envLogMsg(
                    traceLevel, getEnv(),
                    Thread.currentThread().getId() + "-" +
                    Thread.currentThread().getName() +
                    "-" + getEnv().getName() +
                    " postFetchInit(): " +
                    " Adding UIN to LRU: " + getNodeId());
            }
            evictor.addBack(this);
        }
    }

    /**
     * Initialize a node read in during recovery.
     */
    public final void postRecoveryInit(DatabaseImpl db, long lastLoggedLsn) {
        commonPostFetchInit(db, lastLoggedLsn);
    }

    /**
     * Common actions of postFetchInit and postRecoveryInit.
     */
    private void commonPostFetchInit(DatabaseImpl db, long lastLoggedLsn) {
        setDatabase(db);
        setLastLoggedLsn(lastLoggedLsn);
        initMemorySize(); // compute before adding to IN list
    }

    /**
     * Needed only during duplicates conversion, not during normal operation.
     * The needDupKeyConversion field will only be true when first upgrading
     * from JE 4.1.  After the first time an IN is converted, it will be
     * written out in a later file format, so the needDupKeyConversion field
     * will be false and this method will do nothing.  See
     * DupConvert.convertInKeys.
     */
    private void convertDupKeys() {
        /* Do not convert more than once. */
        if (!needDupKeyConversion) {
            return;
        }
        needDupKeyConversion = false;
        DupConvert.convertInKeys(databaseImpl, this);
    }

    /**
     * @see Node#incFetchStats
     */
    @Override
    final void incFetchStats(EnvironmentImpl envImpl, boolean isMiss) {
        Evictor e = envImpl.getEvictor();
        if (e == null) {
            return;
        }
        if (isBIN()) {
            e.incBINFetchStats(isMiss, isBINDelta(false/*checLatched*/));
        } else {
            e.incUINFetchStats(isMiss);
        }
    }

    /**
     * @param in parent IN.  Is null when root is fetched.
     */
    static String makeFetchErrorMsg(String msg, IN in, long lsn, byte state) {

        /*
         * Bolster the exception with the LSN, which is critical for
         * debugging. Otherwise, the exception propagates upward and loses the
         * problem LSN.
         */
        StringBuilder sb = new StringBuilder();

        if (in == null) {
            sb.append("fetchRoot of ");
        } else if (in.isBIN()) {
            sb.append("fetchLN of ");
        } else {
            sb.append("fetchIN of ");
        }

        if (lsn == DbLsn.NULL_LSN) {
            sb.append("null lsn");
        } else {
            sb.append(DbLsn.getNoFormatString(lsn));
        }

        if (in != null) {
            sb.append(" parent IN=").append(in.getNodeId());
            sb.append(" IN class=").append(in.getClass().getName());
            sb.append(" lastFullVersion=");
            sb.append(DbLsn.getNoFormatString(in.getLastFullVersion()));
            sb.append(" lastLoggedVersion=");
            sb.append(DbLsn.getNoFormatString(in.getLastLoggedVersion()));
            sb.append(" parent.getDirty()=").append(in.getDirty());
        }

        sb.append(" state=").append(state);

        if (msg != null) {
            sb.append(" ").append(msg);
        }

        return sb.toString();
    }

    public final int findEntry(
        byte[] key,
        boolean indicateIfDuplicate,
        boolean exact) {

        return findEntry(key, indicateIfDuplicate, exact, null /*Comparator*/);
    }

    /**
     * Find the entry in this IN for which key is LTE the key arg.
     *
     * Currently uses a binary search, but eventually, this may use binary or
     * linear search depending on key size, number of entries, etc.
     *
     * This method guarantees that the key parameter, which is the user's key
     * parameter in user-initiated search operations, is always the left hand
     * parameter to the Comparator.compare method.  This allows a comparator
     * to perform specialized searches, when passed down from upper layers.
     *
     * This is public so that DbCursorTest can access it.
     *
     * Note that the 0'th entry's key is treated specially in an IN.  It always
     * compares lower than any other key.
     *
     * @param key - the key to search for.
     * @param indicateIfDuplicate - true if EXACT_MATCH should
     * be or'd onto the return value if key is already present in this node.
     * @param exact - true if an exact match must be found.
     * @return offset for the entry that has a key LTE the arg.  0 if key
     * is less than the 1st entry.  -1 if exact is true and no exact match
     * is found.  If indicateIfDuplicate is true and an exact match was found
     * then EXACT_MATCH is or'd onto the return value.
     */
    public final int findEntry(
        byte[] key,
        boolean indicateIfDuplicate,
        boolean exact,
        Comparator<byte[]> comparator) {

        int high = nEntries - 1;
        int low = 0;
        int middle = 0;

        Comparator<byte[]> userCompareToFcn = (comparator != null) ?
            comparator :
            databaseImpl.getKeyComparator();

        /*
         * Special Treatment of 0th Entry
         * ------------------------------
         * IN's are special in that they have a entry[0] where the key is a
         * virtual key in that it always compares lower than any other key.
         * BIN's don't treat key[0] specially.  But if the caller asked for an
         * exact match or to indicate duplicates, then use the key[0] and
         * forget about the special entry zero comparison.
         *
         * We always use inexact searching to get down to the BIN, and then
         * call findEntry separately on the BIN if necessary.  So the behavior
         * of findEntry is different for BINs and INs, because it's used in
         * different ways.
         *
         * Consider a tree where the lowest key is "b" and we want to insert
         * "a".  If we did the comparison (with exact == false), we wouldn't
         * find the correct (i.e.  the left) path down the tree.  So the
         * virtual key ensures that "a" gets inserted down the left path.
         *
         * The insertion case is a good specific example.  findBinForInsert
         * does inexact searching in the INs only, not the BIN.
         *
         * There's nothing special about the 0th key itself, only the use of
         * the 0th key in the comparison algorithm.
         */
        boolean entryZeroSpecialCompare =
            isUpperIN() && !exact && !indicateIfDuplicate;

        assert nEntries >= 0;

        while (low <= high) {

            middle = (high + low) / 2;
            int s;
            byte[] middleKey = null;

            if (middle == 0 && entryZeroSpecialCompare) {
                s = 1;
            } else {
                middleKey = getKey(middle);
                s = Key.compareKeys(key, middleKey, userCompareToFcn);
            }

            if (s < 0) {
                high = middle - 1;
            } else if (s > 0) {
                low = middle + 1;
            } else {
                int ret;
                if (indicateIfDuplicate) {
                    ret = middle | EXACT_MATCH;
                } else {
                    ret = middle;
                }

                if ((ret >= 0) && exact && isEntryKnownDeleted(ret & 0xffff)) {
                    return -1;
                } else {
                    return ret;
                }
            }
        }

        /*
         * No match found.  Either return -1 if caller wanted exact matches
         * only, or return entry for which arg key is > entry key.
         */
        if (exact) {
            return -1;
        } else {
            return high;
        }
    }

    /**
     * Inserts a slot with the given key, lsn and child node into this IN, if
     * a slot with the same key does not exist already. The state of the new
     * slot is set to DIRTY. Assumes this node is already latched by the
     * caller.
     *
     * @return true if the entry was successfully inserted, false
     * if it was a duplicate.
     *
     * @throws EnvironmentFailureException if the node is full
     * (it should have been split earlier).
     */
    public final boolean insertEntry(
        Node child,
        byte[] key,
        long childLsn)
        throws DatabaseException {

        assert(!isBINDelta());

        return
            (insertEntry1(child, key, childLsn, EntryStates.DIRTY_BIT, false) &
             INSERT_SUCCESS) != 0;
    }

    /**
     * Inserts a slot with the given key, lsn and child node into this IN, if
     * a slot with the same key does not exist already. The state of the new
     * slot is set to DIRTY. Assumes this node is already latched by the
     * caller.
     *
     * @return either (1) the index of location in the IN where the entry was
     * inserted |'d with INSERT_SUCCESS, or (2) the index of the duplicate in
     * the IN if the entry was found to be a duplicate.
     *
     * @throws EnvironmentFailureException if the node is full (it should have
     * been split earlier).
     */
    public final int insertEntry1(
        Node child,
        byte[] key,
        long childLsn,
        boolean blindInsertion) {

        return insertEntry1(
            child, key, childLsn, EntryStates.DIRTY_BIT, blindInsertion);
    }

    /**
     * Inserts a slot with the given key, lsn, state, and child node into this
     * IN, if a slot with the same key does not exist already. Assumes this
     * node is already latched by the caller.
     *
     * This returns a failure if there's a duplicate match. The caller must do
     * the processing to check if the entry is actually deleted and can be
     * overwritten. This is foisted upon the caller rather than handled in this
     * object because there may be some latch releasing/retaking in order to
     * check a child LN.
     *
     * @return either (1) the index of location in the IN where the entry was
     * inserted |'d with INSERT_SUCCESS, or (2) the index of the duplicate in
     * the IN if the entry was found to be a duplicate.
     *
     * @throws EnvironmentFailureException if the node is full (it should have
     * been split earlier).
     */
    public final int insertEntry1(
        Node child,
        byte[] key,
        long childLsn,
        byte state,
        boolean blindInsertion) {

        /*
         * Search without requiring an exact match, but do let us know the
         * index of the match if there is one.
         */
        int index = findEntry(key, true, false);

        if (index >= 0 && (index & EXACT_MATCH) != 0) {

            /*
             * There is an exact match.  Don't insert; let the caller decide
             * what to do with this duplicate.
             */
            return index & ~IN.EXACT_MATCH;
        }

        /*
         * There was no key match, but if this is a bin delta, there may be an
         * exact match in the full bin. Mutate to full bin and search again.
         * However, if we know for sure that the key does not exist in the full
         * BIN, then don't mutate, unless there is no space in the delta to do
         * the insertion.
         */
        if (isBINDelta()) {

            BIN bin = (BIN)this;

            boolean doBlindInsertion = (nEntries < getMaxEntries());

            if (doBlindInsertion &&
                !blindInsertion &&
                bin.mayHaveKeyInFullBin(key)) {

                doBlindInsertion = false;
            }

            if (!doBlindInsertion) {

                mutateToFullBIN();

                index = findEntry(key, true, false);

                if (index >= 0 && (index & EXACT_MATCH) != 0) {
                    return index & ~IN.EXACT_MATCH;
                }
            } else {
                getEvictor().incBinDeltaBlindOps();

                if (traceDeltas) {
                    LoggerUtils.envLogMsg(
                        traceLevel, getEnv(),
                        Thread.currentThread().getId() + "-" +
                        Thread.currentThread().getName() +
                        "-" + getEnv().getName() +
                        (blindInsertion ?
                         " Blind insertion in BIN-delta " :
                         " Blind put in BIN-delta ") +
                        getNodeId() + " nEntries = " +
                        nEntries + " max entries = " +
                        getMaxEntries() +
                        " full BIN entries = " +
                        bin.getFullBinNEntries() +
                        " full BIN max entries = " +
                        bin.getFullBinMaxEntries());
                }
            }
        }

        if (nEntries >= getMaxEntries()) {
            throw unexpectedState(
                getEnv(),
                "Node " + getNodeId() +
                " should have been split before calling insertEntry" +
                " is BIN-delta: " + isBINDelta() +
                " num entries: " + nEntries +
                " max entries: " + getMaxEntries());
        }

        /* There was no key match, so insert to the right of this entry. */
        index++;

        /* We found a spot for insert, shift entries as needed. */
        if (index < nEntries) {
            int oldSize = computeLsnOverhead();

            /* Adding elements to the LSN array can change the space used. */
            shiftEntriesRight(index);

            updateMemorySize(computeLsnOverhead() - oldSize);
        } else {
            nEntries++;
        }

        if (isBINDelta()) {
            ((BIN)this).incFullBinNEntries();
        }

        int oldSize = computeLsnOverhead();

        setTarget(index, child);
        /* setLsnInternal can mutate to an array of longs. */
        setLsnInternal(index, childLsn);
        entryStates[index] = state;
        boolean multiSlotChange = setKeyAndPrefix(index, key);

        adjustCursorsForInsert(index);

        updateMemorySize(oldSize,
                         getEntryInMemorySize(index) +
                         computeLsnOverhead());

        if (multiSlotChange) {
            updateMemorySize(inMemorySize, computeMemorySize());
        }

        setDirty(true);

        assert(isBIN() || hasResidentChildren() == hasCachedChildrenFlag());

        return (index | INSERT_SUCCESS);
    }

    /**
     * Deletes the ChildReference with the key arg from this IN.  Assumes this
     * node is already latched by the caller.
     *
     * This seems to only be used by INTest.
     *
     * @param key The key of the reference to delete from the IN.
     *
     * @param maybeValidate true if assert validation should occur prior to
     * delete.  Set this to false during recovery.
     *
     * @return true if the entry was successfully deleted, false if it was not
     * found.
     */
    final boolean deleteEntry(byte[] key, boolean maybeValidate)
        throws DatabaseException {

        assert(!isBINDelta());

        if (nEntries == 0) {
            return false; // caller should put this node on the IN cleaner list
        }

        int index = findEntry(key, false, true);
        if (index < 0) {
            return false;
        }

        return deleteEntry(index, maybeValidate);
    }

    /**
     * Deletes the ChildReference at index from this IN.  Assumes this node is
     * already latched by the caller.
     *
     * @param index The index of the entry to delete from the IN.
     *
     * @param maybeValidate true if asserts are enabled.
     *
     * @return true if the entry was successfully deleted, false if it was not
     * found.
     */
    public final boolean deleteEntry(int index, boolean maybeValidate)
        throws DatabaseException {

        assert(!isBINDelta());

        if (nEntries == 0) {
            return false;
        }

        /* Check the subtree validation only if maybeValidate is true. */
        assert maybeValidate ?
            validateSubtreeBeforeDelete(index) :
            true;

        if (index < nEntries) {

            Node child = getTarget(index);

            if (child != null && child.isIN()) {
                IN childIN = (IN)child;
                getEnv().getInMemoryINs().remove(childIN);
            }

            updateMemorySize(getEntryInMemorySize(index), 0);
            int oldLSNArraySize = computeLsnOverhead();

            /*
             * Do the actual deletion. Note: setTarget() must be called before
             * copyEntries() so that the hasCachedChildrenFlag will be properly
             * maintained.
             */
            setTarget(index, null);
            copyEntries(index + 1, index, nEntries - index - 1);
            nEntries--;

            setDirty(true);
            setProhibitNextDelta();

            /* cleanup what used to be the last entry */
            clearEntry(nEntries);

            /* setLsnInternal can mutate to an array of longs. */
            updateMemorySize(oldLSNArraySize, computeLsnOverhead());

            assert(isBIN() || hasCachedChildrenFlag() == hasResidentChildren());

            /*
             * Note that we don't have to adjust cursors for delete, since
             * there should be nothing pointing at this record.
             */
            traceDelete(Level.FINEST, index);
            return true;
        } else {
            return false;
        }
    }

    /**
     * WARNING: clearEntry() calls entryTargets.set() directly, instead of
     * setTarget(). As a result, the hasCachedChildren flag of the IN is not
     * updated here. The caller is responsible for updating this flag, if
     * needed.
     */
    void clearEntry(int idx) {

        entryTargets = entryTargets.set(idx, null, this);
        entryKeyVals = entryKeyVals.set(idx, null, this);
        setLsnInternal(idx, DbLsn.NULL_LSN);
        entryStates[idx] = 0;
    }

    /**
     * Update the idx'th entry of this node.
     */
    public final void updateEntry(int idx, long lsn, int lastLoggedSize) {

        setLsn(idx, lsn);
        setLastLoggedSize(idx, lastLoggedSize);
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node.
     *
     * This method is used only by the BIN.applyDelta() method.
     *
     * Unlike other update methods, the LSN may be NULL_LSN if the KD flag is
     * set. This allows applying a BIN-delta with a NULL_LSN and KD, for an
     * invisible log entry for example.
     */
    final void updateEntry(
        int idx,
        Node node,
        long lsn,
        int lastLoggedSize,
        byte state) {

        assert(isBIN());
        assert(!isBINDelta());
        assert (lsn != DbLsn.NULL_LSN) ||
               ((state & EntryStates.KNOWN_DELETED_BIT) != 0);

        setLsn(idx, lsn, false/*check*/);
        setLastLoggedSize(idx, lastLoggedSize);
        entryStates[idx] = state;
        setTarget(idx, node);
        setDirty(true);
    }

    /**
     * Update the LSN, key, lastLoggedSize, and cached-child properties of
     * the idx-th slot.
     *
     * This method is used to insert a new record in an existing slot. It is
     * also used in DupConvert, where it is called to convert the keys of an
     * upper IN that has just been fetched from the log and is not attached to
     * in-memory tree yet.
     */
    public final void updateEntry(
        int idx,
        Node node,
        long lsn,
        int lastLoggedSize,
        byte[] key) {

        Node child = getTarget(idx);
        assert(child == null ||
               child.isLN() ||
               !((IN)child).getInListResident() ||
               child == node);

        long oldSize = getEntryInMemorySize(idx);

        setLsn(idx, lsn);
        setLastLoggedSize(idx, lastLoggedSize);
        setTarget(idx, node);

        boolean multiSlotChange = setKeyAndDirty(idx, key);

        if (multiSlotChange) {
            updateMemorySize(inMemorySize, computeMemorySize());
        } else {
            long newSize = getEntryInMemorySize(idx);
            updateMemorySize(oldSize, newSize);
        }

        setDirty(true);

        assert(isBIN() || hasResidentChildren() == hasCachedChildrenFlag());
    }

    /**
     * Update the idx'th entry of this node. This flavor is used in
     * CursorImpl.updateRecordInternal() and CursorImpl.deleteCurrentRecord().
     * The target is not set by this method, because it has already been set
     * or modified in-place by the caller; instead, the size of the old LN is
     * passed as input in order to do memory counting. This method allows
     * updating the slot without replacing the target to support delete and
     * update without fetching or causing eviction.
     */
    public final void updateEntry(
        int idx,
        long oldNodeSize,
        long lsn,
        int lastLoggedSize,
        byte[] lnSlotKey) {

        assert(isBIN());

        long oldSize = getEntryInMemorySize(idx);

        setLsn(idx, lsn);
        setLastLoggedSize(idx, lastLoggedSize);

        boolean multiSlotChange = setLNSlotKey(idx, lnSlotKey);
        if (multiSlotChange) {
            updateMemorySize(inMemorySize, computeMemorySize());
        } else {
            long newSize = getEntryInMemorySize(idx);
            updateMemorySize(oldSize, newSize);

            /* Update mem size for node change. */
            final Node currentNode = entryTargets.get(idx);
            long newNodeSize = (currentNode != null ?
                                currentNode.getMemorySizeIncludedByParent() :
                                0);
            updateMemorySize(oldNodeSize, newNodeSize);
        }

        setDirty(true);
    }

    /**
     * Update the cached-child and LSN properties of the idx-th slot. This
     * method is used by the RecoveryManager to change the version of a
     * child node, either to a later version (in case of redo), or to an
     * earlier version (in case of undo). The child node may or may not be
     * already attached to the tree.
     */
    public final void updateNode(
        int idx,
        Node node,
        long lsn,
        int lastLoggedSize) {

        long oldSize = getEntryInMemorySize(idx);

        /*
         * If we are about to detach a cached child IN, make sure that it is
         * not in the INList. This should be the case indeed, because if the
         * child node is an IN, this method is called during the recovery
         * phase where the INList is disabled,
         */
        Node child = getTarget(idx);
        assert(child == null ||
               child.isLN() ||
               !((IN)child).getInListResident() ||
               child == node/* this is needed by a unit test*/);

        setLsn(idx, lsn);
        setLastLoggedSize(idx, lastLoggedSize);
        setTarget(idx, node);

        long newSize = getEntryInMemorySize(idx);
        updateMemorySize(oldSize, newSize);

        setDirty(true);

        assert(isBIN() || hasResidentChildren() == hasCachedChildrenFlag());
    }

    /**
     * Attach the given node as the idx-th child of "this" node. If the child
     * node is an LN, set the key of the parent slot to the given key value,
     * if the 2 key values do not compare equal (see setLNSlotKey() for the
     * reason why the key may be updated).
     *
     * This method is called after the child node has been either (a) fetched
     * in from disk and is not dirty, or (b) is a newly created instance that
     * will be written out later by something like a checkpoint. In either
     * case, the slot LSN does not need to be updated.
     *
     * Note: does not dirty the node unless the LN slot key is changed.
     */
    public final void attachNode(int idx, Node node, byte[] lnSlotKey) {

        long oldSize = getEntryInMemorySize(idx);

        /* Make sure we are not using this method to detach a cached child */
        assert(getTarget(idx) == null);

        setTarget(idx, node);

        boolean multiSlotChange = setLNSlotKey(idx, lnSlotKey);
        if (multiSlotChange) {
            updateMemorySize(inMemorySize, computeMemorySize());
        } else {
            long newSize = getEntryInMemorySize(idx);
            updateMemorySize(oldSize, newSize);
        }

        assert(isBIN() || hasResidentChildren() == hasCachedChildrenFlag());
    }

    /*
     * Detach from the tree the child node at the idx-th slot.
     *
     * The most common caller of this method is the evictor. If the child
     * being evicted was dirty, it has just been logged and the lsn of the
     * slot must be updated.
     */
    public final void detachNode(int idx, boolean updateLsn, long newLsn) {

        long oldSize = getEntryInMemorySize(idx);

        Node child = getTarget(idx);

        if (updateLsn) {
            setLsn(idx, newLsn);
            setDirty(true);
        }
        setTarget(idx, null);

        long newSize = getEntryInMemorySize(idx);
        updateMemorySize(oldSize, newSize);

        if (child != null && child.isIN()) {
            IN childIN = (IN)child;
            getEnv().getInMemoryINs().remove(childIN);
        }

        assert(isBIN() || hasResidentChildren() == hasCachedChildrenFlag());
    }

    void copyEntries(final int from, final int to, final int n) {

        entryTargets = entryTargets.copy(from, to, n, this);
        entryKeyVals = entryKeyVals.copy(from, to, n, this);

        System.arraycopy(entryStates, from, entryStates, to, n);

        if (entryLsnLongArray == null) {
            final int fromOff = from << 2;
            final int toOff = to << 2;
            final int nBytes = n << 2;
            System.arraycopy(entryLsnByteArray, fromOff,
                             entryLsnByteArray, toOff, nBytes);
        } else {
            System.arraycopy(entryLsnLongArray, from,
                             entryLsnLongArray, to,
                             n);
        }
    }

    /*
     * All methods that modify the entry array must adjust memory sizing.
     */

    /**
     * Set the idx'th entry of this node using the specified entry of another
     * node.  Do not validate the LSN, just set it unconditionally.  Used for
     * moving entries between BINs during splits.
     *
     * WARNING: This method bumps nEntries if it is less than idx+1,
     * effectively appending the slot.
     */
    void copyEntry(int idx, IN from, int fromIdx) {

        assert(!isBINDelta());

        final Node target = from.entryTargets.get(fromIdx);
        final byte[] keyVal = from.getKey(fromIdx);
        final long lsn = from.getLsn(fromIdx);
        final byte state = from.entryStates[fromIdx];

        long oldSize = computeLsnOverhead();
        int newNEntries = idx + 1;

        if (newNEntries > nEntries) {

            /*
             * If the new entry is going to bump nEntries, then we don't need
             * the entry size accounting included in oldSize.
             */
            nEntries = newNEntries;
        } else {
            oldSize += getEntryInMemorySize(idx);
        }

        setTarget(idx, target);
        boolean multiSlotChange = setKeyAndPrefix(idx, keyVal);

        /* setLsnInternal can mutate to an array of longs. */
        setLsnInternal(idx, lsn);

        entryStates[idx] = state;

        if (multiSlotChange) {
            updateMemorySize(inMemorySize, computeMemorySize());
        } else {
            long newSize = getEntryInMemorySize(idx) + computeLsnOverhead();
            updateMemorySize(oldSize, newSize);
        }

        setDirty(true);
    }

    /**
     * Return true if this node needs splitting.  For the moment, needing to be
     * split is defined by there being no free entries available.
     */
    public final boolean needsSplitting() {

        if (isBINDelta()) {
            BIN bin = (BIN)this;
            int fullBinNEntries = bin.getFullBinNEntries();
            int fullBinMaxEntries = bin.getFullBinMaxEntries();

            if (fullBinNEntries < 0) {
                /* fullBinNEntries is unknown in logVersions < 10 */
                mutateToFullBIN();
            } else {
                assert(fullBinNEntries > 0);
                return ((fullBinMaxEntries - fullBinNEntries) < 1);
            }
        }

        return ((getMaxEntries() - nEntries) < 1);
    }

    /**
     * Split this into two nodes.  Parent IN is passed in parent and should be
     * latched by the caller.
     *
     * childIndex is the index in parent of where "this" can be found.
     */
    final void split(
        IN parent,
        int childIndex,
        IN grandParent,
        int maxEntries,
        CacheMode cacheMode)
        throws DatabaseException {

        splitInternal(
            parent, childIndex, grandParent, maxEntries, -1, cacheMode);
    }

    /**
     * Called when we know we are about to split on behalf of a key that is the
     * minimum (leftSide) or maximum (!leftSide) of this node.  This is
     * achieved by just forcing the split to occur either one element in from
     * the left or the right (i.e. splitIndex is 1 or nEntries - 1).
     */
    void splitSpecial(
        IN parent,
        int parentIndex,
        IN grandParent,
        int maxEntriesPerNode,
        byte[] key,
        boolean leftSide,
        CacheMode cacheMode)
        throws DatabaseException {

        int index = findEntry(key, false, false);

        if (leftSide && index == 0) {
            splitInternal(
                parent, parentIndex, grandParent, maxEntriesPerNode, 1,
                cacheMode);

        } else if (!leftSide && index == (nEntries - 1)) {
            splitInternal(
                parent, parentIndex, grandParent, maxEntriesPerNode,
                nEntries - 1, cacheMode);

        } else {
            split(
                parent, parentIndex, grandParent, maxEntriesPerNode,
                cacheMode);
        }
    }

    final void splitInternal(
        IN parent,
        int childIndex,
        IN grandParent,
        int maxEntries,
        int splitIndex,
        CacheMode cacheMode)
        throws DatabaseException {

        assert(!isBINDelta());

        /*
         * Find the index of the existing identifierKey so we know which IN
         * (new or old) to put it in.
         */
        if (identifierKey == null) {
            throw unexpectedState();
        }

        int idKeyIndex = findEntry(identifierKey, false, false);

        if (splitIndex < 0) {
            splitIndex = nEntries / 2;
        }

        /* Range of entries to copy to new sibling. */
        final int low, high;

        if (idKeyIndex < splitIndex) {

            /*
             * Current node (this) keeps left half entries.  Right half entries
             * will go in the new node.
             */
            low = splitIndex;
            high = nEntries;
        } else {

            /*
             * Current node (this) keeps right half entries.  Left half entries
             * will go in the new node.
             */
            low = 0;
            high = splitIndex;
        }

        byte[] newIdKey = getKey(low);
        long parentLsn = DbLsn.NULL_LSN;

        IN newSibling = createNewInstance(newIdKey, maxEntries, level);

        newSibling.latch(CacheMode.UNCHANGED);

        try {
            int toIdx = 0;
            boolean deletedEntrySeen = false;
            BINReference binRef = null;
            int newSiblingNEntries = (high - low);
            boolean haveCachedChildren = hasCachedChildrenFlag();

            assert(isBIN() || haveCachedChildren == hasResidentChildren());

            /**
             * Distribute entries among the split node and the new sibling.
             */
            for (int i = low; i < high; i++) {

                if (isEntryPendingDeleted(i)) {
                    if (!deletedEntrySeen) {
                        deletedEntrySeen = true;
                        assert newSibling.isBIN();
                        binRef = ((BIN) newSibling).createReference();
                    }
                }

                newSibling.copyEntry(toIdx++, this, i);
                clearEntry(i);
            }

            if (low == 0) {
                shiftEntriesLeft(newSiblingNEntries);
            }

            newSibling.nEntries = toIdx;
            nEntries -= newSiblingNEntries;
            setDirty(true);

            if (isUpperIN() && haveCachedChildren) {
                setHasCachedChildrenFlag(hasResidentChildren());
            }

            assert(isBIN() ||
                   hasCachedChildrenFlag() == hasResidentChildren());
            assert(isBIN() ||
                   newSibling.hasCachedChildrenFlag() ==
                   newSibling.hasResidentChildren());

            /*
             * Always add to compressor queue since a full BIN is logged below,
             * and never a delta.
             */
            if (deletedEntrySeen) {
                getEnv().addToCompressorQueue(binRef, false);
            }

            adjustCursors(newSibling, low, high);

            /*
             * If this node has no key prefix, calculate it now that it has
             * been split.  This must be done before logging, to ensure the
             * prefix information is made persistent [#20799].
             */
            byte[] newKeyPrefix = computeKeyPrefix(-1);
            recalcSuffixes(newKeyPrefix, null, -1);
            /* Apply compaction after prefixing [#20799]. */
            entryKeyVals = entryKeyVals.compact(this);

            /* Only recalc if there are multiple entries in newSibling. */
            if (newSibling.getNEntries() > 1) {
                byte[] newSiblingPrefix = newSibling.getKeyPrefix();
                newSiblingPrefix = newSibling.computeKeyPrefix(-1);
                newSibling.recalcSuffixes(newSiblingPrefix, null, -1);
                /* initMemorySize calls entryKeyVals.compact. */
                newSibling.initMemorySize();
            }

            /*
             * Update size. newSibling and parent are correct, but this IN has
             * had its entries shifted and is not correct.
             *
             * Also, inMemorySize does not reflect changes that may have
             * resulted from key prefixing related changes, it needs to be
             * brought up to date, so update it appropriately for this and the
             * above reason.
             */
            EnvironmentImpl env = getEnv();
            INList inMemoryINs = env.getInMemoryINs();
            long oldMemorySize = inMemorySize;
            long newSize = computeMemorySize();
            updateMemorySize(oldMemorySize, newSize);
            inMemoryINs.add(newSibling);

            /*
             * Parent refers to child through an element of the entries array.
             * Depending on which half of the BIN we copied keys from, we
             * either have to adjust one pointer and add a new one, or we have
             * to just add a new pointer to the new sibling.
             *
             * We must use the provisional logging for two reasons:
             *
             *   1) All three log entries must be read atomically. The parent
             *   must get logged last, as all referred-to children must precede
             *   it. Provisional entries guarantee that all three are processed
             *   as a unit. Recovery skips provisional entries, so the changed
             *   children are only used if the parent makes it out to the log.
             *
              *   2) We log all they way to the root to avoid the "great aunt"
             *   problem (see LevelRecorder), and provisional logging is
             *   necessary during a checkpoint for levels less than
             *   maxFlushLevel.
             */
            LogManager logManager = env.getLogManager();

            long newSiblingLsn =
                newSibling.optionalLogProvisional(logManager, parent);

            long myNewLsn = optionalLogProvisional(logManager, parent);

            /*
             * When we update the parent entry, we make sure that we don't
             * replace the parent's key that points at 'this' with a key that
             * is > than the existing one.  Replacing the parent's key with
             * something > would effectively render a piece of the subtree
             * inaccessible.  So only replace the parent key with something
             * <= the existing one.  See tree/SplitTest.java for more details
             * on the scenario.
             */
            if (low == 0) {

                /*
                 * Change the original entry to point to the new child and add
                 * an entry to point to the newly logged version of this
                 * existing child.
                 */
                parent.prepareForSlotReuse(childIndex);

                parent.updateSplitSlot(
                    childIndex, newSibling, newSiblingLsn, newIdKey);

                byte[] ourKey = getKey(0);
                boolean inserted = parent.insertEntry(this, ourKey, myNewLsn);
                assert inserted;
            } else {

                /*
                 * Update the existing child's LSN to reflect the newly logged
                 * version and insert new child into parent.
                 */
                parent.updateSplitSlot(childIndex, this, myNewLsn, getKey(0));

                boolean inserted = parent.insertEntry(
                    newSibling, newIdKey, newSiblingLsn);
                assert inserted;
            }

            /**
             * Log the parent. Note that the root slot or grandparent slot is
             * not updated with the parent's LSN here; this is done by
             * Tree.forceSplit.
             */
            if (parent.isDbRoot()) {
                parentLsn = parent.optionalLog(logManager);
            } else {
                parentLsn = parent.optionalLogProvisional(
                    logManager, grandParent);
            }

            /*
             * Check whether either the old or the new sibling must be added
             * to the LRU (mixed LRUSet).
             */
            assert(!isDIN() && !isDBIN());

            final Evictor evictor = getEvictor();

            if (evictor != null) {
                if(isBIN() || !newSibling.hasCachedChildrenFlag()) {
                    switch (cacheMode) {
                    case DEFAULT:
                    case EVICT_LN:
                    case UNCHANGED:
                    case KEEP_HOT:
                        if (isUpperIN() && traceLRU) {
                            LoggerUtils.envLogMsg(
                                traceLevel, getEnv(),
                                "split-newSibling " +
                                Thread.currentThread().getId() + "-" +
                                Thread.currentThread().getName() +
                                "-" + getEnv().getName() +
                                " Adding UIN to LRU: " +
                                newSibling.getNodeId());
                        }
                        evictor.addBack(newSibling);
                        break;
                    case MAKE_COLD:
                    case EVICT_BIN:
                        if (isBIN()) {
                            evictor.addFront(newSibling);
                        } else {
                            if (traceLRU) {
                                LoggerUtils.envLogMsg(
                                    traceLevel, getEnv(),
                                    "split-newSibling-2 " +
                                    Thread.currentThread().getId() + "-" +
                                    Thread.currentThread().getName() +
                                    "-" + getEnv().getName() +
                                    " Adding UIN to LRU: " +
                                    newSibling.getNodeId());
                            }
                            evictor.addBack(newSibling);
                        }
                        break;
                    default:
                        throw unexpectedState
                            ("unknown cacheMode: " + cacheMode);
                    }
                }

                if (isUpperIN() &&
                    haveCachedChildren &&
                    !hasCachedChildrenFlag()) {
                    if (traceLRU) {
                        LoggerUtils.envLogMsg(
                            traceLevel, getEnv(),
                            "split-oldSibling " +
                            Thread.currentThread().getId() + "-" +
                            Thread.currentThread().getName() +
                            "-" + getEnv().getName() +
                            " Adding UIN to LRU: " + getNodeId());
                    }
                    evictor.addBack(this);
                }
            }

            /* Debug log this information. */
            traceSplit(Level.FINE, parent,
                       newSibling, parentLsn, myNewLsn,
                       newSiblingLsn, splitIndex, idKeyIndex, childIndex);
        } finally {
            newSibling.releaseLatch();
        }
    }

    /**
     * Update a slot that is being split. Ther slot to be updated here is the
     * one that existed before the split.
     *
     * @param child The new child to be placed under the slot. May be the
     * newly created sibling or the pre-existing sibling.
     * @param lsn The new lsn of the child (the child was logged just before
     * calling this method, so its slot lsn must be updated)
     * @param key The new key for the slot. We should not actually update the
     * slot key, because its value is the lower bound of the key range covered
     * by the slot, and this lower bound does not change as a result of the
     * split (the new slot created as a result of the split is placed to the
     * right of the pre-existing slot). There is however one exception: the
     * key can be updated if "idx" is the 0-slot. The 0-slot key is not a true
     * lower bound; the actual lower bound for the 0-slot is the key in the
     * parent slot for this IN. So, in this case, if the given key is less
     * than the current one, it is better to update the key in order to better
     * approximate the real lower bound (and thus make the isKeyInBounds()
     * method more effective).
     */
    private void updateSplitSlot(
        int idx,
        IN child,
        long lsn,
        byte[] key) {

        assert(isUpperIN());

        long oldSize = getEntryInMemorySize(idx);

        setLsn(idx, lsn);
        setTarget(idx, child);

        if (idx == 0) {
            byte[] existingKey = getKey(idx);
            int s = Key.compareKeys(key, existingKey, getKeyComparator());

            boolean multiSlotChange = false;
            if (s < 0) {
                multiSlotChange = setKeyAndDirty(idx, key);
            }

            if (multiSlotChange) {
                updateMemorySize(inMemorySize, computeMemorySize());
            } else {
                long newSize = getEntryInMemorySize(idx);
                updateMemorySize(oldSize, newSize);
            }
        } else {
            long newSize = getEntryInMemorySize(idx);
            updateMemorySize(oldSize, newSize);
        }

        setDirty(true);

        assert(hasResidentChildren() == hasCachedChildrenFlag());
    }

    /**
     * Shift entries to the right by one position, starting with (and
     * including) the entry at index. Increment nEntries by 1. Called
     * in insertEntry1()
     *
     * @param index - The position to start shifting from.
     */
    private void shiftEntriesRight(int index) {
        copyEntries(index, index + 1, nEntries - index);
        clearEntry(index);
        nEntries++;
        setDirty(true);
    }

    /**
     * Shift entries starting at the byHowMuch'th element to the left, thus
     * removing the first byHowMuch'th elements of the entries array.  This
     * always starts at the 0th entry. Caller is responsible for decrementing
     * nEntries.
     *
     * @param byHowMuch - The number of entries to remove from the left side
     * of the entries array.
     */
    private void shiftEntriesLeft(int byHowMuch) {
        copyEntries(byHowMuch, 0, nEntries - byHowMuch);
        for (int i = nEntries - byHowMuch; i < nEntries; i++) {
            clearEntry(i);
        }
        setDirty(true);
    }

    void adjustCursors(
        IN newSibling,
        int newSiblingLow,
        int newSiblingHigh) {
        /* Cursors never refer to IN's. */
    }

    void adjustCursorsForInsert(int insertIndex) {
        /* Cursors never refer to IN's. */
    }

    /**
     * Called prior to changing a slot to contain a different logical node.
     * Necessary to support assertions for transient LSNs in shouldUpdateLsn.
     * Examples: LN slot reuse, and splits where a new node is placed in an
     * existing slot.
     */
    public void prepareForSlotReuse(int idx) {

        if (databaseImpl.isDeferredWriteMode()) {
            setLsn(idx, DbLsn.NULL_LSN, false/*check*/);
        }
    }

    /*
     * Get the current memory consumption of this node
     */
    public long getInMemorySize() {
        return inMemorySize;
    }

    /**
     * Compute the current memory consumption of this node, after putting
     * its keys in their compact representation, if possible.
     */
    protected void initMemorySize() {
        entryKeyVals = entryKeyVals.compact(this);
        inMemorySize = computeMemorySize();
    }

    /**
     * Count up the memory usage attributable to this node alone. LNs children
     * are counted by their BIN parents, but INs are not counted by their
     * parents because they are resident on the IN list.  The identifierKey is
     * "intentionally" not kept track of in the memory budget.
     */
    public long computeMemorySize() {

        long calcMemorySize = getFixedMemoryOverhead();

        calcMemorySize += MemoryBudget.byteArraySize(entryStates.length);

        calcMemorySize += computeLsnOverhead();

        for (int i = 0; i < nEntries; i++) {
            calcMemorySize += getEntryInMemorySize(i);
        }

        if (keyPrefix != null) {
            calcMemorySize += MemoryBudget.byteArraySize(keyPrefix.length);
        }

        if (provisionalObsolete != null) {
            calcMemorySize += provisionalObsolete.getMemorySize();
        }

        calcMemorySize += entryTargets.calculateMemorySize();
        calcMemorySize += entryKeyVals.calculateMemorySize();

        return calcMemorySize;
    }

    /*
     * Overridden by subclasses.
     */
    protected long getFixedMemoryOverhead() {
        return MemoryBudget.IN_FIXED_OVERHEAD;
    }

    /*
     * Compute the memory consumption for storing this node's LSNs
     */
    private int computeLsnOverhead() {
        return (entryLsnLongArray == null) ?
            MemoryBudget.byteArraySize(entryLsnByteArray.length) :
            MemoryBudget.ARRAY_OVERHEAD +
                (entryLsnLongArray.length *
                 MemoryBudget.PRIMITIVE_LONG_ARRAY_ITEM_OVERHEAD);
    }

    private long getEntryInMemorySize(int idx) {

        /*
         * Do not count state size here, since it is counted as overhead
         * during initialization.
         */
        long ret = 0;

        /*
         * Don't count the key size if the representation has already
         * accounted for it.
         */
        if (!entryKeyVals.accountsForKeyByteMemUsage()) {

            /*
             * Materialize the key object only if needed, thus avoiding the
             * object allocation cost when possible.
             */
            final byte[] key = entryKeyVals.get(idx);
            if (key != null) {
                ret += MemoryBudget.byteArraySize(key.length);
            }
        }

        final Node target = entryTargets.get(idx);
        if (target != null) {
            ret += target.getMemorySizeIncludedByParent();
        }
        return ret;
    }

    /**
     * Compacts the representation of the BIN, currently just the
     * representation used by entryTargets if possible. Typically invoked after
     * a BIN has been stripped.
     *
     * @return number of bytes reclaimed.
     */
    public long compactMemory() {

        final long oldSize = inMemorySize;
        final INKeyRep oldKeyRep = entryKeyVals;

        entryTargets = entryTargets.compact(this);
        entryKeyVals = entryKeyVals.compact(this);

        /*
         * Note that we only need to account for mem usage changes in the key
         * rep here, not the target rep.  The target rep, unlike the key rep,
         * updates its mem usage internally, and the responsibility for mem
         * usage of contained nodes is fixed -- it is always managed by the IN.
         *
         * When the key rep changes, the accountsForKeyByteMemUsage property
         * also changes. Recalc the size of the entire IN, because
         * responsibility for managing contained key byte mem usage has shifted
         * between the key rep and the IN parent.
         */
        if (entryKeyVals != oldKeyRep) {
            updateMemorySize(inMemorySize, computeMemorySize());
            return oldSize - inMemorySize;
        }

        return oldSize - inMemorySize;
    }

    /**
     * Returns the amount of memory currently budgeted for this IN.
     */
    public long getBudgetedMemorySize() {
        return inMemorySize - accumulatedDelta;
    }

    /**
     * Called as part of a memory budget reset (during a checkpoint) to clear
     * the accumulated delta and return the total memory size.
     */
    public long resetAndGetMemorySize() {
        accumulatedDelta = 0;
        return inMemorySize;
    }

    protected void updateMemorySize(long oldSize, long newSize) {
        long delta = newSize - oldSize;
        updateMemorySize(delta);
    }

    /*
     * Called when a cached child is replaced by another cached child.
     */
    void updateMemorySize(Node oldNode, Node newNode) {
        long delta = 0;
        if (newNode != null) {
            delta = newNode.getMemorySizeIncludedByParent();
        }

        if (oldNode != null) {
            delta -= oldNode.getMemorySizeIncludedByParent();
        }
        updateMemorySize(delta);
    }

    /*
     * Change this.onMemorySize by the given delta and update the memory
     * budget for the cache, but only if the accummulated delta for this
     * node exceeds the ACCUMULATED_LIMIT threshold and this IN is actually
     * on the IN list. (For example, when we create new INs, they are
     * manipulated off the IN list before being added; if we updated the
     * environment wide cache then, we'd end up double counting.)
     */
    void updateMemorySize(long delta) {

        if (delta == 0) {
            return;
        }

        inMemorySize += delta;

        if (inListResident) {

            /*
             * This assertion is disabled if the environment is invalid to
             * avoid spurious assertions during testing of IO errors.  If the
             * environment is invalid, memory budgeting errors are irrelevant.
             * [#21929]
             */
            assert
                inMemorySize >= getFixedMemoryOverhead() ||
                !getEnv().isValid():
                "delta: " + delta + " inMemorySize: " + inMemorySize +
                " overhead: " + getFixedMemoryOverhead() +
                " computed: " + computeMemorySize() +
                " dump: " + toString() + assertPrintMemorySize();

            accumulatedDelta += delta;
            if (accumulatedDelta > ACCUMULATED_LIMIT ||
                accumulatedDelta < -ACCUMULATED_LIMIT) {
                updateMemoryBudget();
            }
        }
    }

    /**
     * Move the accumulated delta to the memory budget.
     */
    public void updateMemoryBudget() {
        final EnvironmentImpl env = getEnv();
        env.getInMemoryINs().memRecalcUpdate(this, accumulatedDelta);
        env.getMemoryBudget().updateTreeMemoryUsage(accumulatedDelta);
        accumulatedDelta = 0;
    }

    /**
     * Returns the treeAdmin memory in objects referenced by this IN.
     * Specifically, this refers to the DbFileSummaryMap held by
     * MapLNs
     */
    public long getTreeAdminMemorySize() {
        return 0;  // by default, no treeAdminMemory
    }

    /*
     *  Utility method used during unit testing.
     */
    protected long printMemorySize() {

        final long inOverhead = getFixedMemoryOverhead();

        final long statesOverhead =
            MemoryBudget.byteArraySize(entryStates.length);

        final int lsnOverhead =  computeLsnOverhead();

        int entryOverhead = 0;
        for (int i = 0; i < nEntries; i++) {
            entryOverhead += getEntryInMemorySize(i);
        }

        final int keyPrefixOverhead =  (keyPrefix != null) ?
            MemoryBudget.byteArraySize(keyPrefix.length) : 0;

        final int provisionalOverhead = (provisionalObsolete != null) ?
            provisionalObsolete.getMemorySize() : 0;

        final long targetRepOverhead = entryTargets.calculateMemorySize();
        final long keyRepOverhead = entryKeyVals.calculateMemorySize();
        final long total = inOverhead + statesOverhead + lsnOverhead +
             entryOverhead + keyPrefixOverhead +  provisionalOverhead +
             targetRepOverhead + keyRepOverhead;

        System.out.println(" nEntries:" + nEntries +
                           "/" + entryStates.length +
                           " in: " + inOverhead +
                           " states: " + statesOverhead +
                           " entry: " + entryOverhead +
                           " lsn: " + lsnOverhead +
                           " keyPrefix: " + keyPrefixOverhead +
                           " provisional: " + provisionalOverhead +
                           " targetRep(" + entryTargets.getType() + "): " +
                           targetRepOverhead +
                           " keyRep(" + entryKeyVals.getType() +"): " +
                           keyRepOverhead +
                           " Total: " + total +
                           " inMemorySize: " + inMemorySize);
        return total;
    }

    /* Utility method used to print memory size in an assertion. */
    private boolean assertPrintMemorySize() {
        printMemorySize();
        return true;
    }

    public boolean verifyMemorySize() {

        long calcMemorySize = computeMemorySize();
        if (calcMemorySize != inMemorySize) {

            String msg = "-Warning: Out of sync. Should be " +
                calcMemorySize + " / actual: " + inMemorySize +
                " node: " + getNodeId();
            LoggerUtils.envLogMsg(Level.INFO, getEnv(), msg);

            System.out.println(msg);
            printMemorySize();

            return false;
        }
        return true;
    }

    /**
     * Adds (increments) or removes (decrements) the cache stats for the key
     * and target representations.  Used when rep objects are being replaced
     * with a new instance, rather than by calling their mutator methods.
     * Specifically, it is called when mutating from full bin to bin delta
     * or vice-versa.
     */
    protected void updateRepCacheStats(boolean increment) {
        assert(isBIN());
        entryKeyVals.updateCacheStats(increment, this);
        entryTargets.updateCacheStats(increment, this);
    }

    protected int getCompactMaxKeyLength() {
        return getEnv().getCompactMaxKeyLength();
    }

    /**
     * Called when adding/removing this IN to/from the INList.
     */
    public void setInListResident(boolean resident) {

        if (!resident) {
            /* Decrement the stats before clearing its residency */
            entryTargets.updateCacheStats(resident, this);
            entryKeyVals.updateCacheStats(resident, this);
        }

        inListResident = resident;

        if (resident) {
            /* Increment the stats after setting its residency. */
            entryTargets.updateCacheStats(resident, this);
            entryKeyVals.updateCacheStats(resident, this);
        }
    }

    /**
     * Returns whether this IN is on the INList.
     */
    public boolean getInListResident() {
        return inListResident;
    }

    public IN getPrevLRUNode() {
    	return prevLRUNode;
    }

    public void setPrevLRUNode(IN node) {
    	prevLRUNode = node;
    }

    public IN getNextLRUNode() {
    	return nextLRUNode;
    }

    public void setNextLRUNode(IN node) {
    	nextLRUNode = node;
    }

    /**
     * Try to compact or otherwise reclaim memory in this IN and return the
     * number of bytes reclaimed. For example, a BIN should evict LNs, if
     * possible.
     *
     * Used by the evictor to reclaim memory by some means short of evicting
     * the entire node.  If a positive value is returned, the evictor will
     * postpone full eviction of this node.
     */
    public long partialEviction() {
        return 0;
    }

    /**
     * Returns whether any child is non-null.  Is final to indicate it is not
     * overridden (unlike hasPinnedChildren, isEvictionProhibited, etc).
     *
     * Note that the IN may or may not be latched when this method is called.
     * Returning the wrong answer is OK in that case (it will be called again
     * later when latched), but an exception should not occur.
     */
    public final boolean hasResidentChildren() {

        for (int i = 0; i < getNEntries(); i++) {
            if (isResident(i)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Do nothing since INs don't support deltas.
     */
    public void setProhibitNextDelta() {
    }

    /*
     * Validate the subtree that we're about to delete.  Make sure there aren't
     * more than one valid entry on each IN and that the last level of the tree
     * is empty. Also check that there are no cursors on any bins in this
     * subtree. Assumes caller is holding the latch on this parent node.
     *
     * While we could latch couple down the tree, rather than hold latches as
     * we descend, we are presumably about to delete this subtree so
     * concurrency shouldn't be an issue.
     *
     * @return true if the subtree rooted at the entry specified by "index" is
     * ok to delete.
     *
     * Overriden by BIN class.
     */
    boolean validateSubtreeBeforeDelete(int index)
        throws DatabaseException {

        if (index >= nEntries) {

            /*
             * There's no entry here, so of course this entry is deletable.
             */
            return true;
        } else {
            IN child = fetchIN(index);

            boolean needToLatch = !child.isLatchExclusiveOwner();

            try {
                if (needToLatch) {
                    child.latch(CacheMode.UNCHANGED);
                }
                return child.isValidForDelete();
            } finally {
                if (needToLatch && isLatchOwner()) {
                    child.releaseLatch();
                }
            }
        }
    }

    /**
     * Check if this node fits the qualifications for being part of a deletable
     * subtree. It can only have one IN child and no LN children.
     *
     * Note: the method is overwritten by BIN and LN.
     * BIN.isValidForDelete() will not fetch any child LNs.
     * LN.isValidForDelete() simply returns false.
     *
     * We assume that this is only called under an assert.
     */
    @Override
    boolean isValidForDelete()
        throws DatabaseException {

        assert(!isBINDelta());

        /*
         * Can only have one valid child, and that child should be
         * deletable.
         */
        if (nEntries > 1) {            // more than 1 entry.
            return false;

        } else if (nEntries == 1) {    // 1 entry, check child
            IN child = fetchIN(0);
            child.latch(CacheMode.UNCHANGED);

            boolean ret = false;
            try {
                if (child.isBINDelta()) {
                    return false;
                }

                ret = child.isValidForDelete();

            } finally {
                child.releaseLatch();
            }
            return ret;
        } else {                       // 0 entries.
            return true;
        }
    }

    /**
     * Check that the IN is in a valid state.  For now, validity means that the
     * keys are in sorted order and that there are more than 0 entries.
     * maxKey, if non-null specifies that all keys in this node must be less
     * than maxKey.
     * @throws EnvironmentFailureException when implemented.
     */
    @Override
    public final void verify(byte[] maxKey)
        throws EnvironmentFailureException {

        /********* never used, but may be used for the basis of a verify()
                   method in the future.
        try {
            Comparator<byte[]> userCompareToFcn =
                (databaseImpl == null ? null : getKeyComparator());

            byte[] key1 = null;
            for (int i = 1; i < nEntries; i++) {
                key1 = entryKeyVals.get(i);
                byte[] key2 = entryKeyVals.get(i - 1);

                int s = Key.compareKeys(key1, key2, userCompareToFcn);
                if (s <= 0) {
                    throw EnvironmentFailureException.unexpectedState
                        ("IN " + getNodeId() + " key " + (i-1) +
                         " (" + Key.dumpString(key2, 0) +
                         ") and " +
                         i + " (" + Key.dumpString(key1, 0) +
                         ") are out of order");
                }
            }

            boolean inconsistent = false;
            if (maxKey != null && key1 != null) {
                if (Key.compareKeys(key1, maxKey, userCompareToFcn) >= 0) {
                    inconsistent = true;
                }
            }

            if (inconsistent) {
                throw EnvironmentFailureException.unexpectedState
                    ("IN " + getNodeId() +
                     " has entry larger than next entry in parent.");
            }
        } catch (DatabaseException DE) {
            DE.printStackTrace(System.out);
        }
        *****************/
    }

    /**
     * Add self and children to this in-memory IN list. Called by recovery, can
     * run with no latching.
     */
    @Override
    final void rebuildINList(INList inList)
        throws DatabaseException {

        /*
         * Recompute your in memory size first and then add yourself to the
         * list.
         */
        initMemorySize();

        inList.add(this);

        boolean hasCachedChildren = false;

        /*
         * Add your children if they're resident. (LNs know how to stop the
         * flow).
         */
        for (int i = 0; i < nEntries; i++) {
            Node n = getTarget(i);
            if (n != null) {
                n.rebuildINList(inList);
                hasCachedChildren = true;
            }
        }

        final Evictor evictor = getEvictor();

        if (isUpperIN()) {
            if (hasCachedChildren) {
                setHasCachedChildrenFlag(true);
            } else {
                setHasCachedChildrenFlag(false);
                if (evictor != null  && !isDIN()) {
                    if (traceLRU) {
                        LoggerUtils.envLogMsg(
                            traceLevel, getEnv(),
                            "rebuildINList " +
                            Thread.currentThread().getId() +
                            "-" +
                            Thread.currentThread().getName() +
                            "-" + getEnv().getName() +
                            " Adding UIN to LRU: " +
                            getNodeId());
                    }
                    evictor.addBack(this);
                }
            }
        } else if (isBIN() && !isDBIN()) {
            if (evictor != null) {
                evictor.addBack(this);
            }
        }
    }

    /**
     * For a regular (not deferred-write) DB, account for a deleted subtree.
     * <p>
     * Count removed nodes as obsolete in the local tracker. The local tracker
     * will be flushed by the compressor.
     * <p>
     * TODO: Note that we neglect to transfer any accumulated provisional
     * obsolete info from the deleted INs to the tracker.  See
     * accountForDeferredWriteSubtreeRemoval for the case where this comes up.
     * Ideally, in a future release we will record all obsolete info for
     * regular as well as deferred-write DBs, including any accumulated
     * provisional info, from the deleted INs to the subtree parent, as we do
     * in accountForDeferredWriteSubtreeRemoval, and then rely on the logging
     * of the subtree parent (for a regular DB) to flush that info.  This was
     * not done as part of SR [#21348] because logging the subtree parent does
     * not flush the utilization info all the way to the log, while the
     * compressor does flush the local tracker info all the way to the log.
     * With further thought we may decide this change in behavior is
     * acceptable, but it was too risky for a patch release.  See the SR
     * [#21348] for more details.
     */
    @Override
    final void accountForSubtreeRemoval(
        INList inList,
        LocalUtilizationTracker localTracker)
        throws DatabaseException {

        assert(!isBINDelta(false/*checkLatched*/));

        if (nEntries > 1) {
            throw unexpectedState(
                "Found non-deletable IN " + getNodeId() +
                " while flushing INList. nEntries = " + nEntries);
        }

        /* Count full and delta versions as obsolete. */
        if (getLastFullVersion() != DbLsn.NULL_LSN) {
            localTracker.countObsoleteNode(
                getLastFullVersion(), getLogType(), 0, databaseImpl);
        }
        if (getLastDeltaVersion() != DbLsn.NULL_LSN) {
            localTracker.countObsoleteNode(
                getLastDeltaVersion(), getLogType(), 0, databaseImpl);
        }

        /* Remove your child, if any. It should normally be resident. */
        if (nEntries > 0) {
            if (isBIN()) {
                assert(isEntryKnownDeleted(0));
            } else {
                Node n = getTarget(0);
                n.accountForSubtreeRemoval(inList, localTracker);
            }
        }
    }

    /**
     * For a deferred-write DB, account for a deleted subtree.  [#21348]
     * <p>
     * Count removed nodes as provisionally obsolete, recording this info in
     * the parent of the subtree. When the root IN is logged non-provisionally
     * by a checkpoint or Database.sync, the provisional obsolete info will be
     * flushed to the log.
     * <p>
     * When removed nodes are counted obsolete, also transfer their accumulated
     * provisional obsolete to the subtree parent.  This accounts for the case
     * where a subtree is removed in the middle of a checkpoint.
     * <p>
     * For example, say a two level subtree is removed, INa-BINb, and the
     * grandparent, INc, is the subtree parent.  Say a checkpoint logged BINb
     * provisionally, but did not log INa (or INc).  The provisional obsolete
     * info for BINb will be present in INa.  In this method, we transfer that
     * info to INc, so it will be flushed when INc is logged non-provisionally.
     */
    @Override
    final void accountForDeferredWriteSubtreeRemoval(
        INList inList,
        IN subtreeParent)
        throws DatabaseException {

        assert(!isBINDelta(false));

        if (nEntries > 1) {
            throw unexpectedState(
                "Found non-deletable IN " + getNodeId() +
                " while flushing INList. nEntries = " + nEntries);
        }

        final Evictor evictor = getEvictor();

        /* Count full and delta versions as obsolete. */
        if (getLastFullVersion() != DbLsn.NULL_LSN) {
            subtreeParent.trackProvisionalObsolete
                (this, getLastFullVersion(), false /*isObsoleteLN*/, 0);
        }
        if (getLastDeltaVersion() != DbLsn.NULL_LSN) {
            subtreeParent.trackProvisionalObsolete
                (this, getLastDeltaVersion(), false /*isObsoleteLN*/, 0);
        }

        /* Remove your child, if any. It should normally be resident. */
        if (nEntries > 0) {
            if (isBIN()) {
                assert(isEntryKnownDeleted(0));
            } else {
                Node n = getTarget(0);
                assert(n != null && !n.isBINDelta(false));
                n.accountForDeferredWriteSubtreeRemoval(inList, subtreeParent);
            }
        }
    }

    /*
     * DbStat support.
     */
    void accumulateStats(TreeWalkerStatsAccumulator acc) {
        acc.processIN(this, Long.valueOf(getNodeId()), getLevel());
    }

    /**
     * Sets the last logged LSN, which for a BIN may be a delta.
     *
     * In this class, the last logged version and last full version are the
     * same.  In the BIN class, this method is overridden since they may be
     * different.
     */
    void setLastLoggedLsn(long lsn) {
        setLastFullLsn(lsn);
    }

    /**
     * Returns the last logged LSN, or NULL_LSN if never logged.
     *
     * In this class, the last logged version and last full version are the
     * same.  In the BIN class, this method is overridden since they may be
     * different.
     */
    public long getLastLoggedVersion() {
        return getLastFullVersion();
    }

    /**
     * Sets the last full version LSN.
     */
    public final void setLastFullLsn(long lsn) {
        lastFullVersion = lsn;
    }

    /**
     * Returns the last full version LSN, or NULL_LSN if never logged.
     */
    public final long getLastFullVersion() {
        return lastFullVersion;
    }

    /**
     * Returns the last delta version LSN, or NULL_LSN if a delta was not last
     * logged.  Public for unit testing.
     */
    public long getLastDeltaVersion() {
        return DbLsn.NULL_LSN;
    }

    /*
     * Logging support
     */

    /**
     * When splits and checkpoints intermingle in a deferred write databases,
     * a checkpoint target may appear which has a valid target but a null LSN.
     * Deferred write dbs are written out in checkpoint style by either
     * Database.sync() or a checkpoint which has cleaned a file containing
     * deferred write entries. For example,
     *   INa
     *    |
     *   BINb
     *
     *  A checkpoint or Database.sync starts
     *  The INList is traversed, dirty nodes are selected
     *  BINb is bypassed on the INList, since it's not dirty
     *  BINb is split, creating a new sibling, BINc, and dirtying INa
     *  INa is selected as a dirty node for the ckpt
     *
     * If this happens, INa is in the selected dirty set, but not its dirty
     * child BINb and new child BINc.
     *
     * In a durable db, the existence of BINb and BINc are logged
     * anyway. But in a deferred write db, there is an entry that points to
     * BINc, but no logged version.
     *
     * This will not cause problems with eviction, because INa can't be
     * evicted until BINb and BINc are logged, are non-dirty, and are detached.
     * But it can cause problems at recovery, because INa will have a null LSN
     * for a valid entry, and the LN children of BINc will not find a home.
     * To prevent this, search for all dirty children that might have been
     * missed during the selection phase, and write them out. It's not
     * sufficient to write only null-LSN children, because the existing sibling
     * must be logged lest LN children recover twice (once in the new sibling,
     * once in the old existing sibling.
     *
     * Overriden by BIN class.
     */
    public void logDirtyChildren()
        throws DatabaseException {

        assert(!isBINDelta());

        EnvironmentImpl envImpl = getDatabase().getEnv();

        /* Look for targets that are dirty. */
        for (int i = 0; i < getNEntries(); i++) {

            IN child = (IN) getTarget(i);
            if (child != null) {
                child.latch(CacheMode.UNCHANGED);
                try {
                    if (child.getDirty()) {
                        /* Ask descendents to log their children. */
                        child.logDirtyChildren();
                        long childLsn =
                            child.log(envImpl.getLogManager(),
                                      false, // allowDeltas
                                      false, // allowCompress
                                      true,  // isProvisional
                                      true,  // backgroundIO
                                      this); // parent
                        updateEntry(i, childLsn, 0 /*lastLoggedSize*/);
                    }
                } finally {
                    child.releaseLatch();
                }
            }
        }
    }

    /**
     * Log this IN and clear the dirty flag.
     */
    public final long log(LogManager logManager)
        throws DatabaseException {

        return logInternal(logManager,
                           false,  // allowDeltas
                           false,  // allowCompress
                           Provisional.NO,
                           false,  // backgroundIO
                           null);  // parent
    }

    /**
     * Log this node with all available options.
     */
    public final long log(
        LogManager logManager,
        boolean allowDeltas,
        boolean allowCompress,
        boolean isProvisional,
        boolean backgroundIO,
        IN parent) // for provisional
        throws DatabaseException {

        return logInternal(logManager,
                           allowDeltas,
                           allowCompress,
                           isProvisional ? Provisional.YES : Provisional.NO,
                           backgroundIO,
                           parent);
    }

    public final long log(
        LogManager logManager,
        boolean allowDeltas,
        boolean allowCompress,
        Provisional provisional,
        boolean backgroundIO,
        IN parent) // for provisional
        throws DatabaseException {

        return logInternal(logManager,
                           allowDeltas,
                           allowCompress,
                           provisional,
                           backgroundIO,
                           parent);
    }

    /**
     * Log this IN and clear the dirty flag.
     */
    public final long optionalLog(LogManager logManager)
        throws DatabaseException {

        if (databaseImpl.isDeferredWriteMode()) {
            return getLastLoggedVersion();
        } else {
            return logInternal(logManager,
                               false,  // allowDeltas
                               false,  // allowCompress
                               Provisional.NO,
                               false,  // backgroundIO
                               null);  // parent
        }
    }

    /**
     * Log this node provisionally and clear the dirty flag.
     * @return LSN of the new log entry
     */
    public final long optionalLogProvisional(LogManager logManager, IN parent)
        throws DatabaseException {

        if (databaseImpl.isDeferredWriteMode()) {
            return getLastLoggedVersion();
        } else {
            return logInternal(logManager,
                               false,  // allowDeltas
                               false,  // allowCompress
                               Provisional.YES,
                               false,  // backgroundIO
                               parent);
        }
    }

    /**
     * Bottleneck method for all single-IN logging.  Multi-IN logging uses
     * beforeLog and afterLog instead.
     */
    private long logInternal(
        LogManager logManager,
        boolean allowDeltas,
        boolean allowCompress,
        Provisional provisional,
        boolean backgroundIO,
        IN parent)
        throws DatabaseException {

        INLogItem item = new INLogItem();
        item.provisional = provisional;
        item.parent = parent;
        item.repContext = ReplicationContext.NO_REPLICATE;

        INLogContext context = new INLogContext();
        context.nodeDb = getDatabase();
        context.backgroundIO = backgroundIO;
        context.allowDeltas = allowDeltas;
        context.allowCompress = allowCompress;

        beforeLog(logManager, item, context);

        logManager.log(item, context);

        afterLog(logManager, item, context);

        return item.newLsn;
    }

    /**
     * Pre-log processing.  Used implicitly for single-item logging and
     * explicitly for multi-item logging.  Overridden by subclasses as needed.
     *
     * Decide how to log this node.  INs are always logged in full.  Cleaner LN
     * migration is never performed since it only applies to BINs.
     *
     * Overriden by BIN class.
     */
    public void beforeLog(
        LogManager logManager,
        INLogItem item,
        INLogContext context) {

        beforeLogCommon(item, context, lastFullVersion, DbLsn.NULL_LSN);
        item.entry = new INLogEntry<IN>(this);
    }

    /**
     * Pre-log processing shared by IN and BIN classes.
     */
    final void beforeLogCommon(
        INLogItem item,
        INLogContext context,
        long oldLsn,
        long auxOldLsn) {

        assert isLatchExclusiveOwner();
        assert item.parent == null || item.parent.isLatchExclusiveOwner();

        /* Count obsolete info when logging non-provisionally. */
        if (countObsoleteDuringLogging(item.provisional)) {
            item.oldLsn = oldLsn;
            item.auxOldLsn = auxOldLsn;
            context.packedObsoleteInfo = provisionalObsolete;
        }
    }

    /**
     * Post-log processing.  Used implicitly for single-item logging and
     * explicitly for multi-item logging.  Overridden by subclasses as needed.
     *
     * The last version of this node must be counted obsolete at the correct
     * time. If logging non-provisionally, the last version of this node and
     * any provisionally logged descendants are immediately obsolete and can be
     * flushed. If logging provisionally, the last version isn't obsolete until
     * an ancestor is logged non-provisionally, so propagate obsolete lsns
     * upwards.
     *
     * Overriden by BIN class.
     */
    public void afterLog(
        LogManager logManager,
        INLogItem item,
        INLogContext context) {

        afterLogCommon(logManager, item, context, lastFullVersion,
                       DbLsn.NULL_LSN);

        setLastFullLsn(item.newLsn);
    }

    /**
     * Post-log processing shared by IN and BIN classes.
     */
    final void afterLogCommon(
        LogManager logManager,
        INLogItem item,
        INLogContext context,
        long oldLsn,
        long auxOldLsn) {

        if (countObsoleteDuringLogging(item.provisional)) {
            discardProvisionalObsolete(logManager);
        } else {
            if (item.parent != null) {
                if (oldLsn != DbLsn.NULL_LSN) {
                    item.parent.trackProvisionalObsolete
                        (this, oldLsn, false /*isObsoleteLN*/, 0);
                }
                if (auxOldLsn != DbLsn.NULL_LSN) {
                    item.parent.trackProvisionalObsolete
                        (this, auxOldLsn, false /*isObsoleteLN*/, 0);
                }
            }
        }

        setDirty(false);

        final Evictor evictor = getEvictor();
        if (evictor != null) {
            /*
             * To capture all cases where a node needs to be moved to the
             * mixed LRUSet after being cleaned, we invoke moveToMixedLRU()
             * from IN.afterLog(). This includes the case where the node is
             * being logged as part of being evicted, in which case we don't
             * really want it to go back to the LRU. However, this is ok
             * because moveToMixedLRU() checks whether the node is actually
             * in the dirty LRUSet before moving it to the mixed LRUSet.
             */

            if (traceLRU && isUpperIN()) {
                LoggerUtils.envLogMsg(
                    traceLevel, getEnv(),
                    Thread.currentThread().getId() + "-" +
                    Thread.currentThread().getName() +
                    "-" + getEnv().getName() +
                    " afterLogCommon(): " +
                    " Moving UIN to mixed LRU: " + getNodeId());
            }
            evictor.moveToMixedLRU(this);
        }

        /*
         * When logging an IN, its parent IN slot should not have KD/PD flags
         * set, since these flags are applicable only to child LNs.  However,
         * in the past these flags were sometimes set incorrectly.  Clear the
         * flags here to guard against possible problems.
         */
        if (item.parent != null && item.parentIndex >= 0) {
            item.parent.clearKnownDeleted(item.parentIndex);
            item.parent.clearPendingDeleted(item.parentIndex);
        }
    }

    /**
     * Returns whether to count the prior version of an IN (as well as
     * accumulated provisionally obsolete LSNs for child nodes) obsolete when
     * logging the new version.
     *
     * True is returned if we are logging the IN non-provisionally, since the
     * non-provisional version durably replaces the prior version and causes
     * all provisional children to also become durable.
     *
     * True is also returned if the database is temporary. Since we never use a
     * temporary DB past recovery, prior versions of an IN are never used.
     * [#16928]
     */
    private boolean countObsoleteDuringLogging(Provisional provisional) {
        return provisional != Provisional.YES ||
               databaseImpl.isTemporary();
    }

    /**
     * Adds the given obsolete LSN and any tracked obsolete LSNs for the given
     * child IN to this IN's tracking list.  This method is called to track
     * obsolete LSNs when a child IN or LN is logged provisionally.  Such LSNs
     * cannot be considered obsolete until an ancestor IN is logged
     * non-provisionally.
     */
    final void trackProvisionalObsolete(
        IN childIN,
        long obsoleteLsn,
        boolean isObsoleteLN,
        int obsoleteSize) {

        int oldMemSize = (provisionalObsolete != null) ?
                         provisionalObsolete.getMemorySize() :
                         0;

        if (childIN != null && childIN.provisionalObsolete != null) {
            if (provisionalObsolete != null) {
                /* Append child info to parent info. */
                provisionalObsolete.copyObsoleteInfo
                    (childIN.provisionalObsolete);
            } else {
                /* Move reference from child to parent. */
                provisionalObsolete = childIN.provisionalObsolete;
            }
            childIN.updateMemorySize(
                0 - childIN.provisionalObsolete.getMemorySize());
            childIN.provisionalObsolete = null;
        }

        if (obsoleteLsn != DbLsn.NULL_LSN) {
            if (provisionalObsolete == null) {
                provisionalObsolete = new PackedObsoleteInfo();
            }
            provisionalObsolete.addObsoleteInfo(obsoleteLsn, isObsoleteLN,
                                                obsoleteSize);
        }

        updateMemorySize(oldMemSize,
                         (provisionalObsolete != null) ?
                         provisionalObsolete.getMemorySize() :
                         0);
    }

    /**
     * Discards the provisional obsolete tracking information in this node
     * after it has been counted in the live tracker.  This method is called
     * after this node is logged non-provisionally.
     */
    private void discardProvisionalObsolete(LogManager logManager)
        throws DatabaseException {

        if (provisionalObsolete != null) {
            updateMemorySize(0 - provisionalObsolete.getMemorySize());
            provisionalObsolete = null;
        }
    }

    /*
     * NOOP for upper INs. Overriden by BIN class.
     */
    public void mutateToFullBIN() {
    }

    private int getNEntriesToWrite(boolean deltasOnly) {
        if (!deltasOnly) {
            return nEntries;
        }
        return getNDeltas();
    }

    public final int getNDeltas() {
        int n = 0;
        for (int i = 0; i < nEntries; i++) {
            if (!isDirty(i)) {
                continue;
            }
            n += 1;
        }
        return n;
    }

    /**
     * @see Node#getGenericLogType
     */
    @Override
    public final LogEntryType getGenericLogType() {
        return getLogType();
    }

    /**
     * Get the log type of this node.
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_IN;
    }

    /**
     * @see Loggable#getLogSize
     *
     * Overrriden by DIN and DBIN classes.
     */
    @Override
    public int getLogSize() {
        return getLogSize(false);
    }

    public final int getLogSize(boolean deltasOnly) {

        int size = super.getLogSize();          // ancestors

        size += LogUtils.getPackedLongLogSize(nodeId);
        size += LogUtils.getByteArrayLogSize(identifierKey); // identifier key

        if (keyPrefix != null) {
            size += LogUtils.getByteArrayLogSize(keyPrefix);
        }

        size += 1; // one byte for boolean flags

        final int nEntriesToWrite = getNEntriesToWrite(deltasOnly);

        final int maxEntriesToWrite =
            (!deltasOnly ?
             getMaxEntries() :
             ((BIN)this).getDeltaCapacity(nEntriesToWrite));

        size += LogUtils.getPackedIntLogSize(nEntriesToWrite);
        size += LogUtils.getPackedIntLogSize(level);
        size += LogUtils.getPackedIntLogSize(maxEntriesToWrite);

        final boolean compactLsnsRep = (entryLsnLongArray == null);
        size += LogUtils.getBooleanLogSize();   // compactLsnsRep
        if (compactLsnsRep) {
            size += LogUtils.INT_BYTES;         // baseFileNumber
        }

        boolean hasLastLoggedSize = isLastLoggedSizeStored();

        for (int i = 0; i < nEntries; i++) {    // entries
            if (deltasOnly && !isDirty(i)) {
                continue;
            }
            size += LogUtils.getByteArrayLogSize(entryKeyVals.get(i)) + // key
                (compactLsnsRep ? LogUtils.INT_BYTES :
                    LogUtils.getLongLogSize()) +                       // LSN
                1;                                                  // state
            if (hasLastLoggedSize) {
                size += LogUtils.getPackedIntLogSize(getLastLoggedSize(i));
            }
        }

        if (deltasOnly) {

            BIN bin = (BIN)this;

            size += LogUtils.getPackedIntLogSize(bin.getFullBinNEntries());
            size += LogUtils.getPackedIntLogSize(bin.getFullBinMaxEntries());

            size += bin.getBloomFilterLogSize();
        }

        return size;
    }

    /*
     * Overrriden by DIN and DBIN classes.
     */
    @Override
    public void writeToLog(ByteBuffer logBuffer) {
        writeToLog(logBuffer, false);
    }

    public final void writeToLog(ByteBuffer logBuffer, boolean deltasOnly) {

        super.writeToLog(logBuffer);

        LogUtils.writePackedLong(logBuffer, nodeId);

        LogUtils.writeByteArray(logBuffer, identifierKey);

        boolean hasKeyPrefix = (keyPrefix != null);
        boolean hasLastLoggedSize = isLastLoggedSizeStored();

        byte booleans = (byte) (isRoot() ? 1 : 0);
        booleans |= (hasKeyPrefix ? 2 : 0);
        booleans |= (hasLastLoggedSize ? 4 : 0);

        byte[] bloomFilter = null;

        if (deltasOnly) {
            BIN bin = (BIN)this;
            bloomFilter = bin.createBloomFilter();

            if (bloomFilter != null) {
                booleans |= 8;
            }
        }

        logBuffer.put(booleans);

        if (hasKeyPrefix) {
            LogUtils.writeByteArray(logBuffer, keyPrefix);
        }

        final int nEntriesToWrite = getNEntriesToWrite(deltasOnly);

        final int maxEntriesToWrite =
            (!deltasOnly ?
             getMaxEntries() :
             ((BIN)this).getDeltaCapacity(nEntriesToWrite));
        /*
        if (deltasOnly) {
            BIN bin = (BIN)this;
            System.out.println(
                "Logging BIN-delta: " + getNodeId() +
                " is delta = " + isBINDelta() +
                " nEntries = " + nEntriesToWrite +
                " max entries = " + maxEntriesToWrite +
                " full BIN entries = " + bin.getFullBinNEntries() +
                " full BIN max entries = " + bin.getFullBinMaxEntries());
        }
        */
        LogUtils.writePackedInt(logBuffer, nEntriesToWrite);
        LogUtils.writePackedInt(logBuffer, level);
        LogUtils.writePackedInt(logBuffer, maxEntriesToWrite);

        /* true if compact representation. */
        boolean compactLsnsRep = (entryLsnLongArray == null);
        LogUtils.writeBoolean(logBuffer, compactLsnsRep);
        if (compactLsnsRep) {
            LogUtils.writeInt(logBuffer, (int) baseFileNumber);
        }

        for (int i = 0; i < nEntries; i++) {

            if (deltasOnly && !isDirty(i)) {
                continue;
            }

            LogUtils.writeByteArray(logBuffer, entryKeyVals.get(i)); // key

            /*
             * A NULL_LSN may be stored when an incomplete insertion occurs,
             * but in that case the KnownDeleted flag must be set. See
             * Tree.insert.  [#13126]
             */
            assert checkForNullLSN(i) :
                "logging IN " + getNodeId() + " with null lsn child " +
                " db=" + databaseImpl.getDebugName() +
                " isDeferredWriteMode=" + databaseImpl.isDeferredWriteMode() +
                " isTemporary=" + databaseImpl.isTemporary();

            if (compactLsnsRep) {                                // LSN
                int offset = i << 2;
                int fileOffset = getFileOffset(offset);
                logBuffer.put(getFileNumberOffset(offset));
                logBuffer.put((byte) ((fileOffset >>> 0) & 0xff));
                logBuffer.put((byte) ((fileOffset >>> 8) & 0xff));
                logBuffer.put((byte) ((fileOffset >>> 16) & 0xff));
            } else {
                LogUtils.writeLong(logBuffer, entryLsnLongArray[i]);
            }

            logBuffer.put(entryStates[i]);                       // state

            if (!deltasOnly) {
                entryStates[i] &= EntryStates.CLEAR_DIRTY_BIT;
            }

            if (hasLastLoggedSize) {
                LogUtils.writePackedInt(logBuffer, getLastLoggedSize(i));
            }
        }

        if (deltasOnly) {

            BIN bin = (BIN)this;

            LogUtils.writePackedInt(logBuffer, bin.getFullBinNEntries());
            LogUtils.writePackedInt(logBuffer, bin.getFullBinMaxEntries());

            if (bloomFilter != null) {
                BINDeltaBloomFilter.writeToLog(bloomFilter, logBuffer);
            }
        }
    }

    /*
     * Used for assertion to prevent writing a null lsn to the log.
     */
    private boolean checkForNullLSN(int index) {
        boolean ok;
        if (isBIN()) {
            ok = !(getLsn(index) == DbLsn.NULL_LSN &&
                   (entryStates[index] & EntryStates.KNOWN_DELETED_BIT) == 0);
        } else {
            ok = (getLsn(index) != DbLsn.NULL_LSN);
        }
        return ok;
    }

    /*
     * Overrriden by DIN and DBIN classes.
     */
    @Override
    public void readFromLog(
        ByteBuffer itemBuffer,
        int entryVersion) {

        readFromLog(itemBuffer, entryVersion, false);
    }

    public final void readFromLog(
        ByteBuffer itemBuffer,
        int entryVersion,
        boolean deltasOnly) {

        super.readFromLog(itemBuffer, entryVersion);

        boolean unpacked = (entryVersion < 6);

        nodeId = LogUtils.readLong(itemBuffer, unpacked);
        identifierKey = LogUtils.readByteArray(itemBuffer, unpacked);

        byte booleans = itemBuffer.get();
        setIsRootFlag((booleans & 1) != 0);
        if ((booleans & 2) != 0) {
            keyPrefix = LogUtils.readByteArray(itemBuffer, unpacked);
        }

        boolean hasLastLoggedSize = ((booleans & 4) != 0);
        assert !(hasLastLoggedSize && (entryVersion < 9));

        boolean hasBloomFilter = ((booleans & 8) != 0);
        assert(!hasBloomFilter || (entryVersion >= 10 && deltasOnly));

        nEntries = LogUtils.readInt(itemBuffer, unpacked);
        level = LogUtils.readInt(itemBuffer, unpacked);
        int length = LogUtils.readInt(itemBuffer, unpacked);

        entryTargets = INTargetRep.NONE;
        entryKeyVals = new INKeyRep.Default(length);
        baseFileNumber = -1;
        long storedBaseFileNumber = -1;
        if (disableCompactLsns) {
            entryLsnByteArray = null;
            entryLsnLongArray = new long[length];
        } else {
            entryLsnByteArray = new byte[length << 2];
            entryLsnLongArray = null;
        }
        entryStates = new byte[length];
        boolean compactLsnsRep = false;

        if (entryVersion > 1) {
            compactLsnsRep = LogUtils.readBoolean(itemBuffer);
            if (compactLsnsRep) {
                baseFileNumber = LogUtils.readInt(itemBuffer) & 0xffffffff;
                storedBaseFileNumber = baseFileNumber;
            }
        }

        for (int i = 0; i < nEntries; i++) {

            entryKeyVals = entryKeyVals.set(
                i, LogUtils.readByteArray(itemBuffer, unpacked), this);

            long lsn;
            if (compactLsnsRep) {
                /* LSNs in compact form. */
                byte fileNumberOffset = itemBuffer.get();
                int fileOffset = (itemBuffer.get() & 0xff);
                fileOffset |= ((itemBuffer.get() & 0xff) << 8);
                fileOffset |= ((itemBuffer.get() & 0xff) << 16);
                if (fileOffset == THREE_BYTE_NEGATIVE_ONE) {
                    lsn = DbLsn.NULL_LSN;
                } else {
                    lsn = DbLsn.makeLsn
                        (storedBaseFileNumber + fileNumberOffset, fileOffset);
                }
            } else {
                /* LSNs in long form. */
                lsn = LogUtils.readLong(itemBuffer);              // LSN
            }

            setLsnInternal(i, lsn);

            byte entryState = itemBuffer.get();                   // state
            if (!deltasOnly) {
                entryState &= EntryStates.CLEAR_DIRTY_BIT;
            }
            entryState &= EntryStates.CLEAR_MIGRATE_BIT;

            /*
             * A NULL_LSN is the remnant of an incomplete insertion and the
             * KnownDeleted flag should be set.  But because of bugs in prior
             * releases, the KnownDeleted flag may not be set.  So set it here.
             * See Tree.insert.  [#13126]
             */
            if (lsn == DbLsn.NULL_LSN) {
                entryState |= EntryStates.KNOWN_DELETED_BIT;
            }

            entryStates[i] = entryState;

            if (hasLastLoggedSize) {
                setLastLoggedSizeUnconditional(
                    i, LogUtils.readPackedInt(itemBuffer));
            }
        }

        if (deltasOnly) {
            setBINDelta(true);

            if (entryVersion >= 10) {
                BIN bin = (BIN)this;
                bin.setFullBinNEntries(LogUtils.readPackedInt(itemBuffer));
                bin.setFullBinMaxEntries(LogUtils.readPackedInt(itemBuffer));

                if (hasBloomFilter) {
                    bin.bloomFilter = BINDeltaBloomFilter.readFromLog(
                        itemBuffer, entryVersion);
                }
            }
        }

        /* Dup conversion will be done by postFetchInit. */
        needDupKeyConversion = (entryVersion < 8);
    }

    /**
     * @see Loggable#logicalEquals
     * Always return false, this item should never be compared.
     */
    @Override
    public final boolean logicalEquals(Loggable other) {
        return false;
    }

    /**
     * @see Loggable#dumpLog
     */
    @Override
    public final void dumpLog(StringBuilder sb, boolean verbose) {
        sb.append(beginTag());

        sb.append("<nodeId val=\"");
        sb.append(nodeId);
        sb.append("\"/>");

        sb.append(Key.dumpString(identifierKey, 0));

        // isRoot
        sb.append("<isRoot val=\"");
        sb.append(isRoot());
        sb.append("\"/>");

        // level
        sb.append("<level val=\"");
        sb.append(Integer.toHexString(level));
        sb.append("\"/>");

        if (keyPrefix != null) {
            sb.append("<keyPrefix>");
            sb.append(Key.dumpString(keyPrefix, 0));
            sb.append("</keyPrefix>");
        }

        // nEntries, length of entries array
        sb.append("<entries numEntries=\"");
        sb.append(nEntries);

        sb.append("\" length=\"");
        sb.append(getMaxEntries());

        if (isBINDelta(false)) {
            BIN bin = (BIN)this;

            sb.append("\" numFullBinEntries=\"");
            sb.append(bin.getFullBinNEntries());
            sb.append("\" maxFullBinEntries=\"");
            sb.append(bin.getFullBinMaxEntries());
        }

        boolean compactLsnsRep = (entryLsnLongArray == null);
        if (compactLsnsRep) {
            sb.append("\" baseFileNumber=\"");
            sb.append(baseFileNumber);
        }
        sb.append("\">");

        if (verbose) {
            for (int i = 0; i < nEntries; i++) {
                sb.append("<ref kd=\"").
                    append(isEntryKnownDeleted(i));
                sb.append("\" pd=\"").
                    append(isEntryPendingDeleted(i));
                sb.append("\" logSize=\"").
                    append(getLastLoggedSize(i));
                sb.append("\">");
                sb.append(Key.dumpString(entryKeyVals.get(i), 0));
                sb.append(DbLsn.toString(getLsn(i)));
                sb.append("</ref>");
            }
        }

        sb.append("</entries>");

        if (isBINDelta(false)) {
            BIN bin = (BIN)this;
            if (bin.bloomFilter != null) {
                BINDeltaBloomFilter.dumpLog(bin.bloomFilter, sb, verbose);
            }
        }

        /* Add on any additional items from subclasses before the end tag. */
        dumpLogAdditional(sb);

        sb.append(endTag());
    }

    /**
     * Allows subclasses to add additional fields before the end tag. If they
     * just overload dumpLog, the xml isn't nested.
     */
    protected void dumpLogAdditional(StringBuilder sb) {
    }

    public String beginTag() {
        return BEGIN_TAG;
    }

    public String endTag() {
        return END_TAG;
    }

    final void dumpKeys() {
        for (int i = 0; i < nEntries; i++) {
            System.out.println(Key.dumpString(entryKeyVals.get(i), 0));
        }
    }

    /**
     * For unit test support:
     * @return a string that dumps information about this IN, without
     */
    @Override
    public String dumpString(int nSpaces, boolean dumpTags) {
        StringBuilder sb = new StringBuilder();
        if (dumpTags) {
            sb.append(TreeUtils.indent(nSpaces));
            sb.append(beginTag());
            sb.append('\n');
        }

        if (dumpTags) {
            sb.append("<nodeId val=\"");
            sb.append(nodeId);
            sb.append("\"/>");
        } else {
            sb.append(nodeId);
        }
        sb.append('\n');

        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<idkey>");
        sb.append(identifierKey == null ?
                  "" :
                  Key.dumpString(identifierKey, 0));
        sb.append("</idkey>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<prefix>");
        sb.append(keyPrefix == null ? "" : Key.dumpString(keyPrefix, 0));
        sb.append("</prefix>\n");
        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<dirty val=\"").append(getDirty()).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<generation val=\"").append(generation).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<level val=\"");
        sb.append(Integer.toHexString(level)).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<isRoot val=\"").append(isRoot()).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<isBINDelta val=\"").append(isBINDelta(false)).append("\"/>");
        sb.append('\n');

        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("<entries nEntries=\"");
        sb.append(nEntries);
        sb.append("\">");
        sb.append('\n');

        for (int i = 0; i < nEntries; i++) {
            sb.append(TreeUtils.indent(nSpaces + 4));
            sb.append("<entry id=\"" + i + "\">");
            sb.append('\n');
            if (getLsn(i) == DbLsn.NULL_LSN) {
                sb.append(TreeUtils.indent(nSpaces + 6));
                sb.append("<lsn/>");
            } else {
                sb.append(DbLsn.dumpString(getLsn(i), nSpaces + 6));
            }
            sb.append('\n');
            if (entryKeyVals.get(i) == null) {
                sb.append(TreeUtils.indent(nSpaces + 6));
                sb.append("<key/>");
            } else {
                sb.append(Key.dumpString(entryKeyVals.get(i), (nSpaces + 6)));
            }
            sb.append('\n');
            if (entryTargets.get(i) == null) {
                sb.append(TreeUtils.indent(nSpaces + 6));
                sb.append("<target/>");
            } else {
                sb.append(entryTargets.get(i).dumpString(nSpaces + 6, true));
            }
            sb.append('\n');
            sb.append(TreeUtils.indent(nSpaces + 6));
            dumpDeletedState(sb, getState(i));
            sb.append("<dirty val=\"").append(isDirty(i)).append("\"/>");
            sb.append('\n');
            sb.append(TreeUtils.indent(nSpaces + 4));
            sb.append("</entry>");
            sb.append('\n');
        }

        sb.append(TreeUtils.indent(nSpaces + 2));
        sb.append("</entries>");
        sb.append('\n');
        if (dumpTags) {
            sb.append(TreeUtils.indent(nSpaces));
            sb.append(endTag());
        }
        return sb.toString();
    }

    /**
     * Utility method for output of knownDeleted and pendingDelete.
     */
    static void dumpDeletedState(StringBuilder sb, byte state) {
        sb.append("<knownDeleted val=\"");
        sb.append(isStateKnownDeleted(state)).append("\"/>");
        sb.append("<pendingDeleted val=\"");
        sb.append(isStatePendingDeleted(state)).append("\"/>");
    }

    @Override
    public String toString() {
        return dumpString(0, true);
    }

    public String shortClassName() {
        return "IN";
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceSplit(Level level,
                            IN parent,
                            IN newSibling,
                            long parentLsn,
                            long myNewLsn,
                            long newSiblingLsn,
                            int splitIndex,
                            int idKeyIndex,
                            int childIndex) {
        Logger logger = getEnv().getLogger();
        if (logger.isLoggable(level)) {
            StringBuilder sb = new StringBuilder();
            sb.append(TRACE_SPLIT);
            sb.append(" parent=");
            sb.append(parent.getNodeId());
            sb.append(" child=");
            sb.append(getNodeId());
            sb.append(" newSibling=");
            sb.append(newSibling.getNodeId());
            sb.append(" parentLsn = ");
            sb.append(DbLsn.getNoFormatString(parentLsn));
            sb.append(" childLsn = ");
            sb.append(DbLsn.getNoFormatString(myNewLsn));
            sb.append(" newSiblingLsn = ");
            sb.append(DbLsn.getNoFormatString(newSiblingLsn));
            sb.append(" splitIdx=");
            sb.append(splitIndex);
            sb.append(" idKeyIdx=");
            sb.append(idKeyIndex);
            sb.append(" childIdx=");
            sb.append(childIndex);
            LoggerUtils.logMsg(logger,
                               databaseImpl.getEnv(),
                               level,
                               sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceDelete(Level level, int index) {
        Logger logger = databaseImpl.getEnv().getLogger();
        if (logger.isLoggable(level)) {
            StringBuilder sb = new StringBuilder();
            sb.append(TRACE_DELETE);
            sb.append(" in=").append(getNodeId());
            sb.append(" index=");
            sb.append(index);
            LoggerUtils.logMsg(logger,
                               databaseImpl.getEnv(),
                               level,
                               sb.toString());
        }
    }

    public final void setFetchINHook(TestHook hook) {
        fetchINHook = hook;
    }
}
