/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */
package com.sleepycat.je.rep;

/**
 * The different types of nodes that can be in a replication group.
 */
public enum NodeType {

    /**
     * A node that passively listens for the results of elections, but does not
     * participate in them. It does not have a replicated environment
     * associated with it.
     * @see com.sleepycat.je.rep.monitor.Monitor
     */
    MONITOR {
        @Override
        public boolean isMonitor() {
            return true;
        }
    },

    /**
     * A full fledged member of the replication group with an associated
     * replicated environment that can serve as both a Master and a Replica.
     */
    ELECTABLE {
        @Override
        public boolean isElectable() {
            return true;
        }
        @Override
        public boolean isDataNode() {
            return true;
        }
    },

    /**
     * A member of the replication group with an associated replicated
     * environment that serves as a Replica but does not participate in
     * elections or durability decisions.  Secondary nodes are only remembered
     * by the group while they maintain contact with the Master.
     *
     * <p>You can use SECONDARY nodes to:
     * <ul>
     * <li>Provide a copy of the data available at a distant location
     * <li>Maintain an extra copy of the data to increase redundancy
     * <li>Change the number of replicas to adjust to dynamically changing read
     *     loads
     * </ul>
     *
     * @since 6.0
     */
    SECONDARY {
        @Override
        public boolean isSecondary() {
            return true;
        }
        @Override
        public boolean isDataNode() {
            return true;
        }
    };

    /**
     * Returns whether this is the {@link #MONITOR} type.
     *
     * @return whether this is {@code MONITOR}
     * @since 6.0
     */
    public boolean isMonitor() {
        return false;
    }

    /**
     * Returns whether this is the {@link #ELECTABLE} type.
     *
     * @return whether this is {@code ELECTABLE}
     * @since 6.0
     */
    public boolean isElectable() {
        return false;
    }

    /**
     * Returns whether this is the {@link #SECONDARY} type.
     *
     * @return whether this is {@code SECONDARY}
     * @since 6.0
     */
    public boolean isSecondary() {
        return false;
    }

    /**
     * Returns whether this type represents a data node, either {@link
     * #ELECTABLE} or {@link #SECONDARY}.
     *
     * @return whether this represents a data node
     * @since 6.0
     */
    public boolean isDataNode() {
        return false;
    }
}
