/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.latch;

/**
 * Extends Latch to provide a reader-writer/shared-exclusive latch.  This is
 * implemented with Java's ReentrantReadWriteLock, which is extended for a
 * few reasons (see Latch).
 *
 * This interface may be also be implemented using an underlying exclusive
 * latch.  This is done so that a single interface can be used for for all INs,
 * even though BIN latches are exclusive-only.  See method javadoc for their
 * behavior in exclusive-only mode.
 */
public interface SharedLatch extends Latch {

    /** Returns whether this latch is exclusive-only. */
    boolean isExclusiveOnly();

    /**
     * Acquires a latch for shared/read access.
     *
     * In exclusive-only mode, calling this method is equivalent to calling
     * {@link #acquireExclusive()}.
     */
    void acquireShared();
}
