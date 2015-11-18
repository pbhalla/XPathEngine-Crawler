/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.net;

/**
 * Interface to support supplying of a password.
 */

public interface PasswordSource {
    /**
     * Get the password.  The use of this is context dependent.
     *
     * @return a copy of the password.  It is recommended that the caller
     * overwrite the return value with other characters when the password
     * is no longer required.
     */
    public char[] getPassword();
}
