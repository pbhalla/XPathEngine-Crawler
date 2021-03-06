/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000, 2010 Oracle and/or its affiliates.  All rights reserved.
 *
 */
package com.sleepycat.persist.test;

import org.junit.Before;
import org.junit.Test;

/**
 * Test that the catalog and data records created with a different version of
 * the DPL are compatible with this version. This test is actually called by 
 * TestVersionCompatibilitySuite, since it contains two parts, firstly to run
 * TestVersionCompatibility tests check previously evolved data without 
 * changing it, then run EvolveTest to try evolving it.
 * 
 * @author Mark Hayes
 */
public class TestVersionCompatibility extends EvolveTestBase {

       
    public TestVersionCompatibility(String originalClsName,
            String evolvedClsName) throws Exception {
        super(originalClsName, evolvedClsName);
    }

    @Override
    boolean useEvolvedClass() {
        return true;
    }

    @Before
    public void setUp() {
        envHome = getTestInitHome(true /*evolved*/);
    }

    @Test
    public void testPreviouslyEvolved()
        throws Exception {

        /* If the store cannot be opened, this test is not appropriate. */
        if (caseObj.getStoreOpenException() != null) {
            return;
        }

        /* The update occurred previously. */
        caseObj.updated = true;

        openEnv();

        /* Open read-only and double check that everything is OK. */
        openStoreReadOnly();
        caseObj.checkEvolvedModel
            (store.getModel(), env, true /*oldTypesExist*/);
        caseObj.readObjects(store, false /*doUpdate*/);
        caseObj.checkEvolvedModel
            (store.getModel(), env, true /*oldTypesExist*/);
        closeStore();

        /* Check raw objects. */
        openRawStore();
        caseObj.checkEvolvedModel
            (rawStore.getModel(), env, true /*oldTypesExist*/);
        caseObj.readRawObjects
            (rawStore, true /*expectEvolved*/, true /*expectUpdated*/);
        closeRawStore();

        closeAll();
    }
}
