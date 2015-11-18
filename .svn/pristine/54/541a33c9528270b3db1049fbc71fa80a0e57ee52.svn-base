/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.net;

/**
 * The LoggerFactory interface provides a mechanism for obtaining logging
 * objects for use with dynamically constructed network objects.  Instances
 * of this interface are provided during object instantiation.
 */
public interface LoggerFactory {

    /**
     * Obtains an InstanceLogger for the specified class.
     *
     * @param clazz the class for which a logger instance is to be obtained.
     * @return a logging object
     */
    public InstanceLogger getLogger(Class<?> clazz);
}
