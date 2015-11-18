/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep;

import com.sleepycat.je.OperationFailureException;

/**
 * @hidden internal, for use in disaster recovery [#23447]
 *
 * Thrown when an operation is performed on an active replication group member
 * but it requires that the member not be active.
 */
public class MemberActiveException extends OperationFailureException {
    private static final long serialVersionUID = 1;

    /**
     * For internal use only.
     * @hidden
     */
    public MemberActiveException(String message) {
        super(null /*locker*/, false /*abortOnly*/, message, null /*cause*/);
    }

    /**
     * For internal use only.
     * @hidden
     */
    private MemberActiveException(String message,
                                  MemberActiveException cause) {
        super(message, cause);
    }

    /**
     * For internal use only.
     * @hidden
     */
    @Override
    public OperationFailureException wrapSelf(String msg) {
        return new MemberActiveException(msg, this);
    }
}
