/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.impl;

import com.sleepycat.je.JEVersion;

/**
 * Thrown when a conflict is detected between a minimum JE version requirement
 * and the JE version of a particular node.
 */
public class MinJEVersionUnsupportedException extends Exception {
    private static final long serialVersionUID = 1;

    /** The minimum JE version. */
    public final JEVersion minVersion;

    /** The name of the node where the requested version is not supported. */
    public final String nodeName;

    /** The node version, or null if not known. */
    public final JEVersion nodeVersion;

    /**
     * Creates an instance of this class.
     *
     * @param minVersion the minimum JE version
     * @param nodeName the name of the node where the version is not supported
     * @param nodeVersion the node version, or {@code null} if not known
     */
    public MinJEVersionUnsupportedException(final JEVersion minVersion,
                                            final String nodeName,
                                            final JEVersion nodeVersion) {
        if (minVersion == null) {
            throw new NullPointerException("The minVersion must not be null");
        }
        if (nodeName == null) {
            throw new NullPointerException("The nodeName must not be null");
        }
        this.minVersion = minVersion;
        this.nodeName = nodeName;
        this.nodeVersion = nodeVersion;
    }

    @Override
    public String getMessage() {
        return "Version is not supported:" +
            " minVersion: " + minVersion +
            ", nodeName: " + nodeName +
            ", nodeVersion: " + nodeVersion;
    }
}
