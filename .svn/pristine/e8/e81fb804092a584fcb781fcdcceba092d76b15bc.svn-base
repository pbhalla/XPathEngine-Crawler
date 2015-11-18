/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.net;

/**
 * The InstanceParams class captures configuration information for object
 * instantiation by DataChannelFactory implementations.
 */
public class InstanceParams {
    private final InstanceContext context;
    private final String classParams;

    /**
     * Creates an InstanceParams instance.
     * @param context the configuration context from which an instantiation
     * is being generated.
     * @param classParams a class-specific parameter argument, which may
     * be null
     */
    public InstanceParams(InstanceContext context, String classParams) {
        this.context = context;
        this.classParams = classParams;
    }

    final public InstanceContext getContext() {
        return context;
    }

    final public String getClassParams() {
        return classParams;
    }
}
