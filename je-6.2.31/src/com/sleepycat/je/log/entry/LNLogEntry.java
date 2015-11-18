/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.log.entry;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DupKeyData;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.VersionedLN;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.VLSN;

/**
 * LNLogEntry embodies all LN log entries.
 * On disk, an LN log entry contains (pre version 6)
 * <pre>
 *   LN
 *   databaseid
 *   key
 *   abortLsn          -- if transactional
 *   abortKnownDeleted -- if transactional
 *   txn               -- if transactional
 *
 * (version 6)
 *   databaseid
 *   abortLsn          -- if transactional
 *   abortKnownDeleted -- if transactional
 *   txn               -- if transactional
 *   LN
 *   key
 * </pre>
 * Before version 6, a non-full-item read of a log entry only retrieved
 * the node ID. After version 6, the database id, transaction id and node ID
 * are all available.
 */
public class LNLogEntry<T extends LN> extends BaseReplicableEntry<T> {
    private static final byte ABORT_KNOWN_DELETED_MASK = (byte) 1;

    /**
     * Used for computing the minimum log space used by an LNLogEntry.
     */
    public static final int MIN_LOG_SIZE = 1 + // DatabaseId
                                           1 + // LN with zero-length data
                                           LogEntryHeader.MIN_HEADER_SIZE;

    /**
     * The log version of the most recent format change for this entry,
     * including any changes to the format of the underlying LN and other
     * loggables.
     *
     * @see #getLastFormatChange
     */
    public static final int LAST_FORMAT_CHANGE = 8;

    /*
     * Persistent fields in an LN entry.
     */
    private LN ln;
    private DatabaseId dbId;
    private byte[] key;
    private long abortLsn = DbLsn.NULL_LSN;
    private boolean abortKnownDeleted;
    private Txn txn;     // conditional

    /* Transient field for duplicates conversion and user key/data methods. */
    enum DupStatus { UNKNOWN, NEED_CONVERSION, DUP_DB, NOT_DUP_DB }
    private DupStatus dupStatus;

    /* For construction of VersionedLN, when VLSN is preserved. */
    private final Constructor<VersionedLN> versionedLNConstructor;

    /**
     * Creates an instance to read an entry.
     *
     * @param <T> the type of the contained LN
     * @param cls the class of the contained LN
     * @return the log entry
     */
    public static <T extends LN> LNLogEntry<T> create(final Class<T> cls) {
        return new LNLogEntry<T>(cls);
    }

    /* Constructor to read an entry. */
    LNLogEntry(final Class<T> cls) {
        super(cls);
        if (cls == LN.class) {
            versionedLNConstructor = getNoArgsConstructor(VersionedLN.class);
        } else {
            versionedLNConstructor = null;
        }
    }

    /* Constructor to write an entry. */
    public LNLogEntry(LogEntryType entryType,
                      T ln,
                      DatabaseId dbId,
                      byte[] key,
                      long abortLsn,
                      boolean abortKnownDeleted,
                      Txn txn) {
        setLogType(entryType);
        this.ln = ln;
        this.dbId = dbId;
        this.key = key;
        this.abortLsn = abortLsn;
        this.abortKnownDeleted = abortKnownDeleted;
        this.txn = txn;
        versionedLNConstructor = null;

        /* A txn should only be provided for transactional entry types. */
        assert(entryType.isTransactional() == (txn != null));
    }

    @Override
    public void readEntry(EnvironmentImpl envImpl,
                          LogEntryHeader header,
                          ByteBuffer entryBuffer) {

        /* Subclasses must call readBaseLNEntry. */
        assert getClass() == LNLogEntry.class;

        /*
         * Prior to version 8, the optimization to omit the key size was
         * mistakenly not applied to internal LN types such as FileSummaryLN
         * and MapLN, and was only applied to user LN types.  The optimization
         * should be applicable whenever LNLogEntry is not subclassed to add
         * additional fields. [#18055]
         */
        final boolean keyIsLastSerializedField =
            header.getVersion() >= 8 || entryType.isUserLNType();

        readBaseLNEntry(envImpl, header, entryBuffer,
                        keyIsLastSerializedField);
    }

    /**
     * Method shared by LNLogEntry subclasses.
     *
     * @param keyIsLastSerializedField specifies whether the key length can be
     * omitted because the key is the last field.  This should be false when
     * an LNLogEntry subclass adds fields to the serialized format.
     */
    final void readBaseLNEntry(EnvironmentImpl envImpl,
                               LogEntryHeader header,
                               ByteBuffer entryBuffer,
                               boolean keyIsLastSerializedField) {

        int logVersion = header.getVersion();
        boolean unpacked = (logVersion < 6);
        int recStartPosition = entryBuffer.position();

        /*
         * For log version 6 and above we store the key last so that we can
         * avoid storing the key size. Instead, we derive it from the LN size
         * and the total entry size. The DatabaseId is also packed.
         */
        if (unpacked) {
            /* LN is first for log versions prior to 6. */
            ln = newLNInstance(envImpl);
            ln.readFromLog(entryBuffer, logVersion);
        }

        /* DatabaseImpl Id. */
        dbId = new DatabaseId();
        dbId.readFromLog(entryBuffer, logVersion);

        /* Key. */
        if (unpacked) {
            key = LogUtils.readByteArray(entryBuffer, true/*unpacked*/);
        } else {
            /* Read later. */
        }

        if (entryType.isTransactional()) {

            /*
             * AbortLsn. If it was a marker LSN that was used to fill in a
             * create, mark it null.
             */
            abortLsn = LogUtils.readLong(entryBuffer, unpacked);
            if (DbLsn.getFileNumber(abortLsn) ==
                DbLsn.getFileNumber(DbLsn.NULL_LSN)) {
                abortLsn = DbLsn.NULL_LSN;
            }

            abortKnownDeleted =
                ((entryBuffer.get() & ABORT_KNOWN_DELETED_MASK) != 0) ?
                true : false;

            /* Locker. */
            txn = new Txn();
            txn.readFromLog(entryBuffer, logVersion);
        }

        if (!unpacked) {
            /* LN is next for log version 6 and above. */
            ln = newLNInstance(envImpl);
            ln.readFromLog(entryBuffer, logVersion);
            final int keySize;
            if (keyIsLastSerializedField) {
                final int bytesWritten =
                    entryBuffer.position() - recStartPosition;
                keySize = header.getItemSize() - bytesWritten;
            } else {
                keySize = LogUtils.readPackedInt(entryBuffer);
            }
            key = LogUtils.readBytesNoLength(entryBuffer, keySize);
        }

        /* Save transient fields after read. */
        setLNTransientFields(ln, header.getVLSN());

        /* Dup conversion will be done by postFetchInit. */
        dupStatus =
            (logVersion < 8) ? DupStatus.NEED_CONVERSION : DupStatus.UNKNOWN;
    }

    /**
     * newLNInstance usually returns exactly the type of LN of the type that
     * was contained in in the log. For example, if a LNLogEntry holds a MapLN,
     * newLNInstance will return that MapLN. There is one extra possibility for
     * vanilla (data record) LNs. In that case, this method may either return a
     * LN or a generated type, the VersionedLN, which adds the vlsn information
     * from the log header to the LN object.
     */
    LN newLNInstance(EnvironmentImpl envImpl) {
        if (versionedLNConstructor != null && envImpl.getPreserveVLSN()) {
            return newInstanceOfType(versionedLNConstructor);
        }
        return newInstanceOfType();
    }

    @Override
    public StringBuilder dumpEntry(StringBuilder sb, boolean verbose) {
        ln.dumpLog(sb, verbose);
        dbId.dumpLog(sb, verbose);
        ln.dumpKey(sb, key);
        if (entryType.isTransactional()) {
            if (abortLsn != DbLsn.NULL_LSN) {
                sb.append(DbLsn.toString(abortLsn));
            }
            sb.append("<knownDeleted val=\"");
            sb.append(abortKnownDeleted ? "true" : "false");
            sb.append("\"/>");
            txn.dumpLog(sb, verbose);
        }
        return sb;
    }

    @Override
    public void dumpRep(StringBuilder sb) {
        if (entryType.isTransactional()) {
            sb.append(" txn=").append(txn.getId());
        }
    }

    @Override
    public LN getMainItem() {
        return ln;
    }

    @Override
    public long getTransactionId() {
        if (entryType.isTransactional()) {
            return txn.getId();
        }
        return 0;
    }

    /*
     * Writing support.
     */

    @Override
    public int getLastFormatChange() {
        return LAST_FORMAT_CHANGE;
    }

    @Override
    public int getSize() {

        /* Subclasses must call getBaseLNEntrySize. */
        assert getClass() == LNLogEntry.class;

        return getBaseLNEntrySize(true /*keyIsLastSerializedField*/);
    }

    /**
     * Method shared by LNLogEntry subclasses.
     *
     * @param keyIsLastSerializedField specifies whether the key length can be
     * omitted because the key is the last field.  This should be false when
     * an LNLogEntry subclass adds fields to the serialized format.
     */
    final int getBaseLNEntrySize(boolean keyIsLastSerializedField) {
        int len = key.length;
        int size = ln.getLogSize() +
            dbId.getLogSize() +
            len;
        if (!keyIsLastSerializedField) {
            size += LogUtils.getPackedIntLogSize(len);
        }
        if (entryType.isTransactional()) {
            size += LogUtils.getPackedLongLogSize(abortLsn);
            size++;   // abortKnownDeleted
            size += txn.getLogSize();
        }
        return size;
    }

    @Override
    public void writeEntry(final ByteBuffer destBuffer, final int logVersion) {

        /* Subclasses must call writeBaseLNEntry. */
        assert getClass() == LNLogEntry.class;

        writeBaseLNEntry(destBuffer,
                         true /*keyIsLastSerializedField*/,
                         logVersion);
    }

    /**
     * Method shared by LNLogEntry subclasses.
     *
     * @param keyIsLastSerializedField specifies whether the key length can be
     * omitted because the key is the last field.  This should be false when
     * an LNLogEntry subclass adds fields to the serialized format.
     */
    final void writeBaseLNEntry(final ByteBuffer destBuffer,
                                final boolean keyIsLastSerializedField,
                                final int logVersion) {
        checkCurrentVersion(this, logVersion);
        assert ln.getLastFormatChange() <= LAST_FORMAT_CHANGE &&
            dbId.getLastFormatChange() <= LAST_FORMAT_CHANGE
            : "Format of loggable newer than format of entry";

        dbId.writeToLog(destBuffer, logVersion);

        if (entryType.isTransactional()) {
            LogUtils.writePackedLong(destBuffer, abortLsn);
            byte aKD = 0;
            if (abortKnownDeleted) {
                aKD |= ABORT_KNOWN_DELETED_MASK;
            }
            destBuffer.put(aKD);
            assert txn.getLastFormatChange() <= LAST_FORMAT_CHANGE
                : "Format of loggable newer than format of entry";
            txn.writeToLog(destBuffer, logVersion);
        }

        ln.writeToLog(destBuffer, logVersion);
        if (!keyIsLastSerializedField) {
            LogUtils.writePackedInt(destBuffer, key.length);
        }
        LogUtils.writeBytesNoLength(destBuffer, key);
    }

    /**
     * An LN has two transient fields that are derived from its parent log
     * entry: last logged size and VLSN sequence.
     */
    private void setLNTransientFields(LN ln, VLSN vlsn) {
        if (vlsn != null) {
            ln.setVLSNSequence(vlsn.getSequence());
        }
    }

    @Override
    public boolean isImmediatelyObsolete(DatabaseImpl dbImpl) {
        return ln.isDeleted() || dbImpl.isLNImmediatelyObsolete();
    }

    @Override
    public boolean isDeleted() {
        return ln.isDeleted();
    }

    /**
     * For LN entries, we need to record the latest LSN for that node with the
     * owning transaction, within the protection of the log latch. This is a
     * callback for the log manager to do that recording.
     */
    @Override
    public void postLogWork(LogEntryHeader header,
                            long justLoggedLsn,
                            VLSN vlsn) {
        if (entryType.isTransactional()) {
            txn.addLogInfo(justLoggedLsn);
        }
        /* Save transient fields after write. */
        setLNTransientFields(ln, vlsn);
    }

    @Override
    public void postFetchInit(DatabaseImpl dbImpl) {
        postFetchInit(dbImpl.getSortedDuplicates());
    }

    /**
     * Converts the key/data for old format LNs in a duplicates DB.
     *
     * This method MUST be called before calling any of the following methods:
     *  getLN
     *  getKey
     *  getUserKeyData
     */
    public void postFetchInit(boolean isDupDb) {

        final boolean needConversion =
            (dupStatus == DupStatus.NEED_CONVERSION);

        dupStatus = isDupDb ? DupStatus.DUP_DB : DupStatus.NOT_DUP_DB;

        /* Do not convert more than once. */
        if (!needConversion) {
            return;
        }

        /* Nothing to convert for non-duplicates DB. */
        if (dupStatus == DupStatus.NOT_DUP_DB) {
            return;
        }

        key = combineDupKeyData();
    }

    /**
     * Combine old key and old LN's data into a new key, and set the LN's data
     * to empty.
     */
    byte[] combineDupKeyData() {
        assert !ln.isDeleted(); // DeletedLNLogEntry overrides this method.
        return DupKeyData.combine(key, ln.setEmpty());
    }

    /**
     * Translates two-part keys in duplicate DBs back to the original user
     * operation params.  postFetchInit must be called before calling this
     * method.
     */
    public void getUserKeyData(DatabaseEntry keyParam,
                               DatabaseEntry dataParam) {

        requireKnownDupStatus();

        if (dupStatus == DupStatus.DUP_DB) {
            DupKeyData.split(new DatabaseEntry(key), keyParam, dataParam);
        } else {
            if (keyParam != null) {
                keyParam.setData(key);
            }
            if (dataParam != null) {
                dataParam.setData(ln.getData());
            }
        }
    }

    /*
     * Accessors.
     */
    public LN getLN() {
        requireKnownDupStatus();
        return ln;
    }

    public byte[] getKey() {
        requireKnownDupStatus();
        return key;
    }

    private void requireKnownDupStatus() {
        if (dupStatus != DupStatus.DUP_DB &&
            dupStatus != DupStatus.NOT_DUP_DB) {
            throw EnvironmentFailureException.unexpectedState
                ("postFetchInit was not called");
        }
    }

    /**
     * This method is only used when the converted length is not needed, for
     * example by StatsFileReader.
     */
    public int getUnconvertedDataLength() {
        return ln.getData().length;
    }

    /**
     * This method is only used when the converted length is not needed, for
     * example by StatsFileReader.
     */
    public int getUnconvertedKeyLength() {
        return key.length;
    }

    @Override
    public DatabaseId getDbId() {
        return dbId;
    }

    public long getAbortLsn() {
        return abortLsn;
    }

    public boolean getAbortKnownDeleted() {
        return abortKnownDeleted;
    }

    public Long getTxnId() {
        if (entryType.isTransactional()) {
            return Long.valueOf(txn.getId());
        }
        return null;
    }

    public Txn getUserTxn() {
        if (entryType.isTransactional()) {
            return txn;
        }
        return null;
    }

    @Override
    public boolean logicalEquals(LogEntry other) {
        if (!(other instanceof LNLogEntry)) {
            return false;
        }

        LNLogEntry<?> otherEntry = (LNLogEntry<?>) other;

        if (!dbId.logicalEquals(otherEntry.dbId)) {
            return false;
        }

        if (txn != null) {
            if (!txn.logicalEquals(otherEntry.txn)) {
                return false;
            }
        } else {
            if (otherEntry.txn != null) {
                return false;
            }
        }

        if (!Arrays.equals(key, otherEntry.key)) {
            return false;
        }

        if (!ln.logicalEquals(otherEntry.ln)) {
            return false;
        }

        return true;
    }
}
