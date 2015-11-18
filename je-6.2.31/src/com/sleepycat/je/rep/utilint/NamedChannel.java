/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.utilint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import com.sleepycat.je.rep.net.DataChannel;
import com.sleepycat.je.rep.impl.node.NameIdPair;

/**
 * Packages a DataChannel and a NameIdPair together so that logging
 * messages can show the node name instead of the channel toString();
 */
public class NamedChannel implements ByteChannel {

    private NameIdPair nameIdPair;
    protected final DataChannel channel;

    public NamedChannel(DataChannel channel, NameIdPair nameIdPair) {
        this.channel = channel;
        this.nameIdPair = nameIdPair;
    }

    /*
     * NameIdPair unknown at this time.
     */
    public NamedChannel(DataChannel channel) {
        this.channel = channel;
        this.nameIdPair = NameIdPair.NULL;
    }

    public void setNameIdPair(NameIdPair nameIdPair) {
        this.nameIdPair = nameIdPair;
    }

    public NameIdPair getNameIdPair() {
        return nameIdPair;
    }

    public DataChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        if (getNameIdPair() == null) {
            return getChannel().toString();
        }

        return "(" + getNameIdPair() + ")" + getChannel();
    }

    /*
     * The following ByteChannel implementation methods delegate to the wrapped
     * channel object.
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel.write(src);
    }
}

