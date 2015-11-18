/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.monitor;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.sleepycat.je.rep.MemberNotFoundException;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.utilint.TestHookAdapter;

/**
 * Perform the tests from MonitorChangeListenerTest, but with event delivery
 * disabled, to test in the presence of network failures.
 */
public class MonitorChangeListenerNoEventsTest
        extends MonitorChangeListenerTest {

    @BeforeClass
    public static void classSetup() {
        MonitorService.processGroupChangeHook =
            new TestHookAdapter<GroupChangeEvent>() {
            @Override
            public void doHook(GroupChangeEvent event) {
                ReplicationNode node;
                try {
                    node = event.getRepGroup().getMember(event.getNodeName());
                } catch (MemberNotFoundException e) {
                    node = null;
                }
                /*
                 * Deliver monitor events because they are only generated
                 * locally and are not simulated by pings.
                 */
                if ((node != null) && !node.getType().isMonitor()) {
                    throw new IllegalStateException("don't deliver");
                }
            }
        };
        MonitorService.processJoinGroupHook =
            new TestHookAdapter<JoinGroupEvent>() {
            @Override
            public void doHook(JoinGroupEvent event) {
                throw new IllegalStateException("don't deliver");
            }
        };
        MonitorService.processLeaveGroupHook =
            new TestHookAdapter<LeaveGroupEvent>() {
            @Override
            public void doHook(LeaveGroupEvent event) {
                throw new IllegalStateException("don't deliver");
            }
        };
    }

    @AfterClass
    public static void classCleanup() {
        MonitorService.processGroupChangeHook = null;
        MonitorService.processJoinGroupHook = null;
        MonitorService.processLeaveGroupHook = null;
    }

    /**
     * When event delivery is disabled, the ping produces the leave event, but
     * it uses a different LeaveReason, so disable this check.
     */
    void checkShutdownReplicaLeaveReason(final LeaveGroupEvent event) {
    }
}
