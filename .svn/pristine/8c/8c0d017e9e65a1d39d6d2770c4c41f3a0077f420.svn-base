/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.latch;

import com.sleepycat.je.utilint.LoggerUtils;

class OwnerInfo {

    private final Thread thread;
    private final long acquireTime;
    private final Throwable acquireStack;

    OwnerInfo(final LatchContext context) {
        thread = Thread.currentThread();
        acquireTime = System.currentTimeMillis();
        acquireStack =
            new Exception("Latch Acquired: " + context.getLatchName());
    }

    void toString(StringBuilder builder) {
        builder.append(" captureThread: ");
        builder.append(thread);
        builder.append(" acquireTime: ");
        builder.append(acquireTime);
        if (acquireStack != null) {
            builder.append("\n");
            builder.append(LoggerUtils.getStackTrace(acquireStack));
        } else {
            builder.append(" -no stack-");
        }
    }
}
