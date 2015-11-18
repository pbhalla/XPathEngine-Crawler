/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.logversion;

import com.sleepycat.je.DatabaseEntry;

public class Utils {

    static final String DB1_NAME = "database1";
    static final String DB2_NAME = "database2";
    static final String DB3_NAME = "database3";
    static final String MIN_VERSION_NAME = "minversion.jdb";
    static final String MAX_VERSION_NAME = "maxversion.jdb";

    static DatabaseEntry entry(int val) {

        byte[] data = new byte[] { (byte) val };
        return new DatabaseEntry(data);
    }

    static int value(DatabaseEntry entry) {

        byte[] data = entry.getData();
        if (data.length != 1) {
            throw new IllegalStateException("len=" + data.length);
        }
        return data[0];
    }
}
