/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle and/or its affiliates.  All rights reserved.
 *
 */
package com.sleepycat.je.rep;

import java.net.InetSocketAddress;

/**
 * An administrative view of a node in a replication group.
 */
public interface ReplicationNode {

    /**
     * Returns the unique name associated with the node.
     *
     * @return the name of the node
     */
    String getName();

    /**
     * Returns the type associated with the node.
     *
     * @return the node type
     */
    NodeType getType();

    /**
     * The socket address used by other nodes in the replication group to
     * communicate with this node.
     *
     * @return the socket address
     */
    InetSocketAddress getSocketAddress();

    /**
     * Returns the host name associated with the node.
     *
     * @return the host name of the node
     */
    String getHostName();

    /**
     * Returns the port number associated with the node.
     *
     * @return the port number of the node
     */
    int getPort();
}
