/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.net;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 * @hidden
 * An interface that associates a delegate socketChannel for network
 * I/O, but which provides a ByteChannel interface for callers.
 */
public interface DataChannel extends ByteChannel {

    /**
     * Accessor for the underlying SocketChannel.
     * Callers may used the returned SocketChannel in order to query/modify
     * connections attributes, but may not directly close, read from or write
     * to the SocketChannel.
     *
     * @return the socket channel underlying this data channel instance
     */
    public SocketChannel getSocketChannel();

    /**
     * Checks whether the channel encrypted.
     *
     * @return true if the data channel provides network privacy
     */
    public boolean isSecure();

    /**
     * Checks whether  the channel capable of determining peer trust.
     *
     * @return true if the data channel implementation has the capability
     * to determine trust.
     */
    public boolean isTrustCapable();

    /**
     * Checks whether the channel peer is trusted.
     *
     * @return true if the channel has determined that the peer is trusted.
     */
    public boolean isTrusted();

    /**
     * Attempt to flush any pending writes to the underlying socket buffer.
     * The caller should ensure that it is the only thread accessing the
     * DataChannel in order that the return value be meaningful.
     *
     * @return true if all pending writes have been flushed, or false if
     * there are writes remainining.
     */
    public boolean flush() throws IOException;
}

