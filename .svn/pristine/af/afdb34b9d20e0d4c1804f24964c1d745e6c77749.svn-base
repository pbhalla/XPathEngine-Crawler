/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.dbi;

/**
 * Used to distinguish Cursor put operations.
 */
public enum PutMode {

    /**
     * User operation: Cursor.putCurrent. Replace data at current position.
     * Return KEYEMPTY if record at current position is deleted.
     */
    CURRENT,

    /**
     * User operation: Cursor.putNoDupData. Applies only to databases with
     * duplicates. Insert key/data pair if it does not already exist;
     * otherwise, return KEYEXIST.
     */
    NO_DUP_DATA,

    /**
     * User operation: Cursor.putNoOverwrite. Insert key/data pair if key
     * does not already exist; otherwise, return KEYEXIST.
     */
    NO_OVERWRITE,

    /**
     * User operation: Cursor.put. Insert if key (for non-duplicates DBs) or
     * key/data (for duplicates DBs) does not already exist; otherwise,
     * overwrite key and data.
     */
    OVERWRITE,

    /**
     * Internal operation (replay of an LN insertion). Use this mode when it
     * is known that the key does not exist. This allows insertions to be 
     * performed in BIN-deltas, because we don't have to mutate them to full
     * BINs to check whether the key exists or not.
     */
    BLIND_INSERTION
}
