/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.log;

import java.nio.ByteBuffer;

import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Item parameters that apply to a single logged item.  Passed to LogManager
 * log methods and to beforeLog and afterLog methods.
 */
public class LogItem {

    /**
     * Object to be marshaled and logged.
     *
     * Set by caller or beforeLog method.
     */
    public LogEntry entry = null;

    /**
     * The previous version of the node to be counted as obsolete, or NULL_LSN
     * if the entry is not a node or has no old LSN.
     *
     * Set by caller or beforeLog method.
     */
    public long oldLsn = DbLsn.NULL_LSN;

    /**
     * For LNs, oldSize should be set along with oldLsn before logging. It
     * should normally be obtained by calling BIN.getLastLoggedSize.
     */
    public int oldSize = 0;

    /**
     * Another LSN to be counted as obsolete in the LogContext.nodeDb database,
     * or NULL_LSN.  Used for obsolete BIN-deltas.
     *
     * Set by caller or beforeLog method.
     */
    public long auxOldLsn = DbLsn.NULL_LSN;

    /**
     * LSN of the new log entry.  Is NULL_LSN if a BIN-delta is logged.  If
     * not NULL_LSN for a tree node, is typically used to update the slot in
     * the parent IN.
     *
     * Set by log or afterLog method.
     */
    public long newLsn = DbLsn.NULL_LSN;

    /**
     * Size of the new log entry.  Is used to update the LN slot in the BIN.
     *
     * Set by log or afterLog method.
     */
    public int newSize = 0;

    /**
     * Whether the logged entry should be processed during recovery.
     *
     * Set by caller or beforeLog method.
     */
    public Provisional provisional = null;

    /**
     * Whether the logged entry should be replicated.
     *
     * Set by caller or beforeLog method.
     */
    public ReplicationContext repContext = null;

    /* Fields used internally by log method. */
    public LogEntryHeader header = null;
    protected ByteBuffer buffer = null;

    final public LogEntryHeader getHeader() {
        return header;
    }

    public final ByteBuffer getBuffer() {
        return buffer;
    }

    public final long getNewLsn() {
        return newLsn;
    }
}
