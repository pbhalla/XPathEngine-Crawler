/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.log.entry;

import java.nio.ByteBuffer;

import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Holds a partial BIN that serves as a live BIN delta.
 *
 * A live delta (unlike a the obsolete OldBINDelta, which is contained in an
 * OldBINDeltaLogEntry) may appear in the Btree to serve as an incomplete BIN.
 */
public class BINDeltaLogEntry extends INLogEntry<BIN> {

    public BINDeltaLogEntry(Class<BIN> logClass) {
        super(logClass);
    }

    /**
     * When constructing an entry for writing to the log, use LOG_BIN_DELTA.
     */
    public BINDeltaLogEntry(BIN bin) {
        super(bin, true /*isBINDelta*/);
    }

    /*
     * Whether this LogEntry reads/writes a BIN-Delta logrec.
     */
    @Override
    public boolean isBINDelta() {
        return true;
    }
}
