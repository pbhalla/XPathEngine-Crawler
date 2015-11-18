/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.utilint.DbLsn;

/*
 * WriteLockInfos store some extra info associated with the write locks (WRITE
 * and RANGE_WRITE) acquired by a Txn. Since locks are acquired on LSNs, there
 * is one WriteLockInfo per Txn and write-locked LSN. Each Txn has a map
 * mapping the LSNs it has write-locked to their associated WriteLockInfos.
 *
 * Although WriteLockInfos are per LSN, the info they store is per logical
 * record. Because a Txn may update the same logical record multiple times
 * and each such time the record gets a new LSN, there may be multiple
 * WriteLockInfos for the same logical record in the same Txn. Care is taken
 * so that all WriteLockInfos for the same logical record store the same info.
 */
public class WriteLockInfo {

    /*
     * The LSN of the record version to revert to if the txn aborts. This is
     * stored persistently in each LN log entry. May be null if the record
     * was created by this transaction (will definitely be null if the txn
     * did not reuse an existing slot for the new record).
     *
     * All WriteLockInfos for the same Txn and same logical record must have
     * the same abortLsn value. So, this value is set when the Txn 
     * inserts/updates/deletes a record for the 1st time, and it is copied from
     * one WriteLockInfo to the next WriteLockInfo for the same record if/when
     * that record is inserted/updated/deleted again by the same Txn. 
     */
    private long abortLsn = DbLsn.NULL_LSN;

    /*
     * The value of the slot's knownDeleted flag before the 1st update
     * of the associated record. It parallels abortLsn.
     */
    private boolean abortKnownDeleted;

    /*
     * Size of the log entry pointed-to by abortLsn, or zero if abortLsn is
     * NULL_LSN or if the size is not known. Used for obsolete counting during
     * a commit.
     */
    private int abortLogSize;

    /*
     * The database of the node, or null if abortLsn is NULL_LSN.  Used for
     * obsolete counting during a commit.
     */
    private DatabaseImpl abortDb;

    /*
     * True if the LSN has never been locked before by this Txn. Used so we
     * can determine when to set abortLsn.
     */
    private boolean neverLocked;

    static final WriteLockInfo basicWriteLockInfo =
        new WriteLockInfo();

    public // for Sizeof
    WriteLockInfo() {
        abortLsn = DbLsn.NULL_LSN;
        abortKnownDeleted = false;
        neverLocked = true;
    }

    public boolean getAbortKnownDeleted() {
        return abortKnownDeleted;
    }

    public void setAbortKnownDeleted(boolean abortKnownDeleted) {
        this.abortKnownDeleted = abortKnownDeleted;
    }

    public long getAbortLsn() {
        return abortLsn;
    }

    public void setAbortLsn(long abortLsn) {
        this.abortLsn = abortLsn;
    }

    public DatabaseImpl getAbortDb() {
        return abortDb;
    }

    public int getAbortLogSize() {
        return abortLogSize;
    }

    public void setAbortInfo(DatabaseImpl db, int logSize) {
        abortDb = db;
        abortLogSize = logSize;
    }

    public boolean getNeverLocked() {
        return neverLocked;
    }

    public void setNeverLocked(boolean neverLocked) {
        this.neverLocked = neverLocked;
    }

    public void copyAbortInfo(WriteLockInfo fromInfo) {
        abortDb = fromInfo.abortDb;
        abortLogSize = fromInfo.abortLogSize;
    }
    
    /*
     * Copy all the information needed to create a clone of the lock.
     */    
    public void copyAllInfo(WriteLockInfo fromInfo) {
        abortLsn = fromInfo.abortLsn;
        abortKnownDeleted = fromInfo.abortKnownDeleted;
        copyAbortInfo(fromInfo);
        neverLocked = fromInfo.neverLocked;
    }

    @Override
    public String toString() {
        return "abortLsn=" +
            DbLsn.getNoFormatString(abortLsn) +
            " abortKnownDeleted=" + abortKnownDeleted +
            " neverLocked=" + neverLocked +
            " abortLogSize=" + abortLogSize;
    }
}
