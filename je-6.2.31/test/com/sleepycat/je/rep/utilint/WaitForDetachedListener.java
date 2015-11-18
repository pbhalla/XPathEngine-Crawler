/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.utilint;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class WaitForDetachedListener implements StateChangeListener {

    private static final long DEFAULT_TIMEOUT_MS = 30000;
    private final CountDownLatch waitForDetached = new CountDownLatch(1);

    public void stateChange(StateChangeEvent stateChangeEvent) {
        if (stateChangeEvent.getState().isDetached()) {
            waitForDetached.countDown();
        }
    }

    public boolean awaitDetached()
        throws InterruptedException {

        return waitForDetached.await(
            DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
