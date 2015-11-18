/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.dbi;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class CompressedOopsDetectorTest {

    /**
     * There is no easy way to test that the detector has determined correctly
     * if compact OOPs are in use -- that requires out-of-band confirmation.
     * Just test that the detector reports that it knows the answer.  A test
     * failure suggests that the CompressedOopsDetector class needs to be
     * updated for the current platform.
     */
    @Test
    public void testDetector() {
        assertNotNull("CompressedOopsDetector result is unknown",
                      CompressedOopsDetector.isEnabled());
    }
}
