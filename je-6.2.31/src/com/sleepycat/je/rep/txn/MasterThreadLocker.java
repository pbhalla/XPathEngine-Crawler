/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.txn;

import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.txn.ThreadLocker;

/**
 * A MasterThreadLocker is used with a user initiated non-transactional
 * operation on a Master, for a replicated DB.  Currently there is no behavior
 * specific to this class, and it is only a placeholder for future HA
 * functionality.
 */
public class MasterThreadLocker extends ThreadLocker {

    public MasterThreadLocker(final RepImpl repImpl) {
        super(repImpl);
    }

    @Override
    public ThreadLocker newEmptyThreadLockerClone() {
        return new MasterThreadLocker((RepImpl) envImpl);
    }
}
