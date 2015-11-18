/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.txn;

import com.sleepycat.je.utilint.DbLsn;

/**
 * LockResult is the return type of Locker.lock(). It encapsulates a
 * LockGrantType (the return type of LockManager.lock()) and a WriteLockInfo.
 * 
 * The WriteLockInfo field is non-null if (a) the locker is transactional, and
 * (b) the request was for a WRITE or WRITE_RANGE lock, and (c) the request was
 * not a non-blocking request that got denied. If so, the WriteLockInfo is
 * either a newly created one or a pre-existing one if the same locker had
 * write-locked the same LSN before. 
 */
public class LockResult {
    private LockGrantType grant;
    private WriteLockInfo info;

    /* Made public for unittests */
    public LockResult(LockGrantType grant, WriteLockInfo info) {
        this.grant = grant;
        this.info = info;
    }

    public LockGrantType getLockGrant() {
        return grant;
    }

    public WriteLockInfo getWriteLockInfo() {
        return info;
    }

    public void setAbortLsn(long abortLsn, boolean abortKnownDeleted) {
        /* Do not overwrite abort info if this locker previously . */
        if (info != null && info.getNeverLocked()) {
            if (abortLsn != DbLsn.NULL_LSN) {
                info.setAbortLsn(abortLsn);
                info.setAbortKnownDeleted(abortKnownDeleted);
            }
            info.setNeverLocked(false);
        }
    }

    /**
     * Used to copy write lock info when an LSN is changed.
     */
    public void copyWriteLockInfo(WriteLockInfo fromInfo) {
        if (fromInfo != null && info != null) {
            setAbortLsn(fromInfo.getAbortLsn(),
                        fromInfo.getAbortKnownDeleted());
            info.copyAbortInfo(fromInfo);
        }
    }
}
