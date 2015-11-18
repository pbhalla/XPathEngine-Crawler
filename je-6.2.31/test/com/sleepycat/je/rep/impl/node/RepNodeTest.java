/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */
package com.sleepycat.je.rep.impl.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.sleepycat.je.rep.impl.node.RepNode.SecondaryNodeIds;

public class RepNodeTest {

    /** Test the SecondaryNodeIds class. */
    @Test
    public void testSecondaryNodeIds() {
        final int size = 100;
        final SecondaryNodeIds ids = new SecondaryNodeIds(size);
        final Set<Integer> set = new HashSet<Integer>();
        for (int i = 0; i < size; i++) {
            final int id = ids.allocateId();
            assertTrue("Unexpected ID value: " + id,
                       id >= (Integer.MAX_VALUE - size));
            set.add(id);
        }
        assertEquals("Number of unique IDs", 100, set.size());
        try {
            ids.allocateId();
            fail("Expected IllegalStateException when no more IDs");
        } catch (IllegalStateException e) {
        }
        try {
            ids.deallocateId(-1);
            fail("Expected IllegalArgumentException for negative ID");
        } catch (IllegalArgumentException e) {
        }
        try {
            ids.deallocateId(Integer.MAX_VALUE - size - 1);
            fail("Expected IllegalArgumentException for invalid ID");
        } catch (IllegalArgumentException e) {
        }
        for (final int id : new HashSet<Integer>(set)) {
            ids.deallocateId(id);
            final int id2 = ids.allocateId();
            assertEquals("Reallocate same ID", id, id2);
        }
        for (final int id : set) {
            ids.deallocateId(id);
        }
        try {
            ids.deallocateId(Integer.MAX_VALUE);
            fail("Expected IllegalArgumentException for not present ID");
        } catch (IllegalArgumentException e) {
        }
    }
}
