//
// Copyright (c) 2020 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.core;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class C4ObserverTest extends C4BaseTest {
    private final AtomicInteger callbackCount = new AtomicInteger(0);
    private C4DatabaseObserver dbObserver;

    @After
    public final void tearDownC4ObserverTest() {
        if (dbObserver != null) { dbObserver.close(); }
    }

    // - Doc Observer
    @Test
    public void testDocObserver() throws LiteCoreException {
        createRev("A", "1-aa", fleeceBody);
        try (C4DocumentObserver obs = this.c4Database.createDocumentObserver("A", callbackCount::incrementAndGet)) {
            assertEquals(0, callbackCount.get());

            createRev("A", "2-bb", fleeceBody);
            createRev("B", "1-bb", fleeceBody);
            assertEquals(1, callbackCount.get());
        }
    }

    // - DB Observer
    @Test
    public void testDBObserver() throws LiteCoreException {
        dbObserver = this.c4Database.createDatabaseObserver(callbackCount::incrementAndGet);
        assertEquals(0, callbackCount.get());

        createRev("A", "1-aa", fleeceBody);
        assertEquals(1, callbackCount.get());
        createRev("B", "1-bb", fleeceBody);
        assertEquals(1, callbackCount.get());

        checkChanges(Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        createRev("B", "2-bbbb", fleeceBody);
        assertEquals(2, callbackCount.get());
        createRev("C", "1-cc", fleeceBody);
        assertEquals(2, callbackCount.get());

        checkChanges(Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);

        dbObserver.close();
        dbObserver = null;

        createRev("A", "2-aaaa", fleeceBody);
        assertEquals(2, callbackCount.get());
    }

    // - Multi-DBs Observer
    @Test
    public void testMultiDBsObserver() throws LiteCoreException {
        dbObserver = this.c4Database.createDatabaseObserver(callbackCount::incrementAndGet);
        assertEquals(0, callbackCount.get());

        createRev("A", "1-aa", fleeceBody);
        assertEquals(1, callbackCount.get());
        createRev("B", "1-bb", fleeceBody);
        assertEquals(1, callbackCount.get());

        checkChanges(Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        C4Database otherdb = C4Database.getDatabase(
            dbParentDirPath,
            dbName,
            getFlags(),
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertNotNull(otherdb);

        boolean commit = false;
        otherdb.beginTransaction();
        try {
            createRev(otherdb, "c", "1-cc", fleeceBody);
            createRev(otherdb, "d", "1-dd", fleeceBody);
            createRev(otherdb, "e", "1-ee", fleeceBody);
            commit = true;
        }
        finally {
            otherdb.endTransaction(commit);
        }

        assertEquals(2, callbackCount.get());
        checkChanges(Arrays.asList("c", "d", "e"), Arrays.asList("1-cc", "1-dd", "1-ee"), true);

        dbObserver.close();
        dbObserver = null;

        createRev("A", "2-aaaa", fleeceBody);
        assertEquals(2, callbackCount.get());

        otherdb.closeDb();
    }

    // - Multi-DBObservers
    @Test
    public void testMultiDBObservers() throws LiteCoreException {
        dbObserver = this.c4Database.createDatabaseObserver(callbackCount::incrementAndGet);
        assertEquals(0, callbackCount.get());

        final AtomicInteger dbCallbackCalls1 = new AtomicInteger(0);
        C4DatabaseObserver dbObserver1 = this.c4Database.createDatabaseObserver(dbCallbackCalls1::incrementAndGet);
        try {
            assertEquals(0, dbCallbackCalls1.get());


            createRev("A", "1-aa", fleeceBody);
            assertEquals(1, callbackCount.get());
            assertEquals(1, dbCallbackCalls1.get());
            createRev("B", "1-bb", fleeceBody);
            assertEquals(1, callbackCount.get());
            assertEquals(1, dbCallbackCalls1.get());

            checkChanges(dbObserver, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);
            checkChanges(dbObserver1, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

            createRev("B", "2-bbbb", fleeceBody);
            assertEquals(2, callbackCount.get());
            assertEquals(2, dbCallbackCalls1.get());
            createRev("C", "1-cc", fleeceBody);
            assertEquals(2, callbackCount.get());
            assertEquals(2, dbCallbackCalls1.get());

            checkChanges(dbObserver, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);
            checkChanges(dbObserver1, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);


            dbObserver.close();
            dbObserver = null;
        }
        finally {
            dbObserver1.close();
        }

        createRev("A", "2-aaaa", fleeceBody);
        assertEquals(2, callbackCount.get());
        assertEquals(2, dbCallbackCalls1.get());
    }

    private void checkChanges(
        List<String> expectedDocIDs,
        List<String> expectedRevIDs,
        boolean expectedExternal) {
        checkChanges(dbObserver, expectedDocIDs, expectedRevIDs, expectedExternal);
    }

    private void checkChanges(
        C4DatabaseObserver observer,
        List<String> expectedDocIDs,
        List<String> expectedRevIDs,
        boolean expectedExternal) {
        C4DatabaseChange[] changes = observer.getChanges(100);
        assertNotNull(changes);
        assertEquals(expectedDocIDs.size(), changes.length);
        for (int i = 0; i < changes.length; i++) {
            assertEquals(expectedDocIDs.get(i), changes[i].getDocID());
            assertEquals(expectedRevIDs.get(i), changes[i].getRevID());
            assertEquals(expectedExternal, changes[i].isExternal());
        }
    }
}
