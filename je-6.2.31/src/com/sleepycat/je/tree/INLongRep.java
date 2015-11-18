/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */
package com.sleepycat.je.tree;

import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.utilint.SizeofMarker;

/**
 * Holds an array of non-negative long values, one for each slot in an IN.
 *
 * Zero is the default value and is returned when no value has been set.
 *
 * The EMPTY_REP is used at first, and is mutated as necessary as values are
 * set.  A minimum number of bytes per value is used, based on the largest
 * value passed to set().
 *
 * This object calls IN.updateMemorySize to track the memory it uses.
 * EMPTY_REP uses no memory because it is a singleton.
 */
public abstract class INLongRep {

    public abstract long get(int idx);
    public abstract INLongRep set(int idx, long val, IN parent, int minLength);
    public abstract INLongRep copy(int from, int to, int n);
    public abstract long getMemorySize();

    /**
     * Initially empty (all values are zero) but will mutate as needed when
     * non-zero values are passed to set().
     */
    public static INLongRep EMPTY_REP = new INLongRep() {

        @Override
        public long get(int idx) {
            return 0;
        }

        /**
         * When adding to the cache the EMPTY_REP is mutated into a
         * DefaultRep.
         */
        @Override
        public INLongRep set(int idx, long val, IN parent, int minLength) {

            if (val == 0) {
                return this;
            }

            final INLongRep newCache =
                new DefaultRep(parent.getMaxEntries(), minLength);

            parent.updateMemorySize(getMemorySize(), newCache.getMemorySize());

            return newCache.set(idx, val, parent, minLength);
        }

        @Override
        public INLongRep copy(int from, int to, int n) {
            return this;
        }

        /**
         * An EMPTY_REP has no JE cache memory overhead because there is only
         * one global instance.
         */
        @Override
        public long getMemorySize() {
            return 0;
        }
    };

    public static class DefaultRep extends INLongRep {

        /** Maximum value indexed by number of bytes. */
        private static long[] MAX_VALUE = {
            0x0L,
            0xFFL,
            0xFFFFL,
            0xFFFFFFL,
            0xFFFFFFFFL,
            0xFFFFFFFFFFL,
            0xFFFFFFFFFFFFL,
            0xFFFFFFFFFFFFFFL,
            0x7FFFFFFFFFFFFFFFL,
        };

        private final byte[] byteArray;
        private final int bytesPerValue;

        public DefaultRep(int capacity, int nBytes) {
            assert capacity >= 1;
            assert nBytes >= 1;
            assert nBytes <= 8;

            bytesPerValue = nBytes;
            byteArray = new byte[capacity * bytesPerValue];
        }

        /* Only for use by the Sizeof utility. */
        public DefaultRep(@SuppressWarnings("unused") SizeofMarker marker) {
            bytesPerValue = 0;
            byteArray = null;
        }

        @Override
        public long get(int idx) {

            int i = idx * bytesPerValue;
            final int end = i + bytesPerValue;

            long val = (byteArray[i] & 0xFF);

            for (i += 1; i < end; i += 1) {
                val <<= 8;
                val |= (byteArray[i] & 0xFF);
            }

            return val;
        }

        /**
         * Mutates to a DefaultRep with a larger number of bytes if necessary
         * to hold the given value.
         */
        @Override
        public DefaultRep set(int idx, long val, IN parent, int minLength) {

            assert idx >= 0;
            assert idx < byteArray.length / bytesPerValue;
            assert val >= 0;

            /*
             * If the value can't be represented using bytesPerValue, mutate
             * to a cache with a larger number of bytes.
             */
            if (val > MAX_VALUE[bytesPerValue]) {

                final int capacity = byteArray.length / bytesPerValue;

                DefaultRep newCache =
                    new DefaultRep(capacity, bytesPerValue + 1);

                parent.updateMemorySize(getMemorySize(),
                                        newCache.getMemorySize());

                /*
                 * Set new value in new cache, and copy other values from old
                 * cache.
                 */
                newCache = newCache.set(idx, val, parent, minLength);

                for (int i = 0; i < capacity; i += 1) {
                    if (i != idx) {
                        newCache = newCache.set(i, get(i), parent, minLength);
                    }
                }

                return newCache;
            }

            /* Set value in this cache. */
            int i = ((idx + 1) * bytesPerValue) - 1;
            final int end = i - bytesPerValue;

            byteArray[i] = (byte) (val & 0xFF);

            for (i -= 1; i > end; i -= 1) {
                val >>= 8;
                byteArray[i] = (byte) (val & 0xFF);
            }

            assert ((val & 0xFFFFFFFFFFFFFF00L) == 0) : val;

            return this;
        }

        @Override
        public DefaultRep copy(int from, int to, int n) {
            System.arraycopy(byteArray,
                             from * bytesPerValue,
                             byteArray,
                             to * bytesPerValue,
                             n * bytesPerValue);
            return this;
        }

        @Override
        public long getMemorySize() {
            return MemoryBudget.DEFAULT_LONG_REP_OVERHEAD +
                   MemoryBudget.byteArraySize(byteArray.length);
        }
    }
}
