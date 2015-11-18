/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.utilint.net;

import com.sleepycat.je.rep.net.DataChannel;
import java.nio.channels.SocketChannel;

/**
 * An abstract class that utilizes a delegate socketChannel for network
 * I/O, but which provides an abstract ByteChannel interface for callers.
 * This allows more interesting communication mechanisms to be introduced.
 */
abstract public class AbstractDataChannel implements DataChannel {

    /**
     * The underlying socket channel
     */
    protected final SocketChannel socketChannel;

    /**
     * Constructor for sub-classes.
     * @param socketChannel The underlying SocketChannel over which data will
     *        be sent.  This should be the lowest-level socket so that select
     *        operations can be performed on it.
     */
    protected AbstractDataChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    /**
     * Accessor for the underlying SocketChannel
     * Callers may used the returned SocketChannel in order to query/modify
     * connections attributes, but may not directly close, read from or write
     * to the SocketChannel.
     */
    @Override
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }
}

