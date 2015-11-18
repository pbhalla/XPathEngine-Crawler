/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.utilint.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


/**
 * A basic concrete extension of DataChannel.
 * This simply delegates operations directly to the underlying SocketChannel
 */
public class SimpleDataChannel extends AbstractDataChannel {

    /**
     * Constructor for general use.
     *
     * @param socketChannel A SocketChannel, which should be connected.
     */
    public SimpleDataChannel(SocketChannel socketChannel) {
        super(socketChannel);
    }

    /*
     * The following ByteChannel implementation methods delegate to the wrapped
     * channel object.
     */

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return socketChannel.read(dst);
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return socketChannel.write(src);
    }

    /**
     * Is the channel encrypted?
     */
    @Override
    public boolean isSecure() {
        return false;
    }

    /**
     * Is the channel peer trusted?
     */
    @Override
    public boolean isTrusted() {
        return false;
    }

    /**
     * Is the channel peer trust capable?
     */
    @Override
    public boolean isTrustCapable() {
        return false;
    }

    /**
     * Attempt to flush any pending writes to the underlying socket buffer,
     * which we don't utilize here.
     */
    @Override
    public boolean flush() {
        return true;
    }
}

