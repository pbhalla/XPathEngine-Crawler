/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2013 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.net;

import javax.net.ssl.SSLSession;

/**
 * Interface to check the identity of a client based on its certificate.
 * Implementations of this interface can be configured for use by the HA
 * service to determine whether a client connection should be treated as
 * authenticated.
 */
public interface SSLAuthenticator {
    /*
     * Based on the information in the SSLSession object, should the client peer
     * be trusted as an internal entity?  This method is called only in server
     * mode.
     *
     * @param sslSession an SSL session object
     * @return true if the SSL peer should be treated as "trusted"
     */
    public boolean isTrusted(SSLSession sslSession);
}

