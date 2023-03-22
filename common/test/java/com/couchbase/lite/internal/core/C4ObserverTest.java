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

import org.junit.Test;

import com.couchbase.lite.LiteCoreException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class C4ObserverTest extends C4BaseTest {
    private final AtomicInteger callbackCount = new AtomicInteger(0);

    // - Doc Observer
    @Test
    public void testDocObserver() throws LiteCoreException {
        createRev("A", "1-aa", fleeceBody);
        try (C4DocumentObserver obs = c4Collection.createDocumentObserver("A", callbackCount::incrementAndGet)) {
            assertEquals(0, callbackCount.get());

            createRev("A", "2-bb", fleeceBody);
            createRev("B", "1-bb", fleeceBody);
            assertEquals(1, callbackCount.get());
        }
    }

    // - DB Observer
    @Test
    public void testCollectionObserver() throws LiteCoreException {
        C4CollectionObserver collObserver = null;
        try {
            collObserver = c4Collection.createCollectionObserver(callbackCount::incrementAndGet);
            assertEquals(0, callbackCount.get());

            createRev("A", "1-aa", fleeceBody);
            assertEquals(1, callbackCount.get());
            createRev("B", "1-bb", fleeceBody);
            assertEquals(1, callbackCount.get());

            checkChanges(collObserver, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

            createRev("B", "2-bbbb", fleeceBody);
            assertEquals(2, callbackCount.get());
            createRev("C", "1-cc", fleeceBody);
            assertEquals(2, callbackCount.get());

            checkChanges(collObserver, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);

            collObserver.close();
            collObserver = null;

            createRev("A", "2-aaaa", fleeceBody);
            assertEquals(2, callbackCount.get());
        }
        finally {
            if (collObserver != null) { collObserver.close(); }
        }
    }

    // - Multi-DBs Observer
    @Test
    public void testMultiDBsObserver() throws LiteCoreException {
        C4CollectionObserver collObserver = null;
        try {
            collObserver = c4Collection.createCollectionObserver(callbackCount::incrementAndGet);
            assertEquals(0, callbackCount.get());

            createRev("A", "1-aa", fleeceBody);
            assertEquals(1, callbackCount.get());
            createRev("B", "1-bb", fleeceBody);
            assertEquals(1, callbackCount.get());

            checkChanges(collObserver, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

            try (C4Database otherDb = C4Database.getDatabase(
                dbParentDirPath,
                dbName,
                getFlags(),
                C4Constants.EncryptionAlgorithm.NONE,
                null)) {
                assertNotNull(otherDb);

                try (C4Collection otherColl = otherDb.getCollection(c4Collection.getScope(), c4Collection.getName())) {
                    assertNotNull(otherColl);

                    boolean commit = false;
                    otherDb.beginTransaction();
                    try {
                        createRev(otherColl, "c", "1-cc", fleeceBody);
                        createRev(otherColl, "d", "1-dd", fleeceBody);
                        createRev(otherColl, "e", "1-ee", fleeceBody);
                        commit = true;
                    }
                    finally {
                        otherDb.endTransaction(commit);
                    }
                }

                assertEquals(2, callbackCount.get());
                checkChanges(collObserver, Arrays.asList("c", "d", "e"), Arrays.asList("1-cc", "1-dd", "1-ee"), true);

                collObserver.close();
                collObserver = null;

                createRev("A", "2-aaaa", fleeceBody);
                assertEquals(2, callbackCount.get());
            }
        }
        finally {
            if (collObserver != null) { collObserver.close(); }
        }
    }

    // - Multi-DBObservers
    @Test
    public void testMultiDBObservers() throws LiteCoreException {
        C4CollectionObserver collObserver = null;
        try {
            collObserver = c4Collection.createCollectionObserver(callbackCount::incrementAndGet);
            assertEquals(0, callbackCount.get());

            final AtomicInteger otherCount = new AtomicInteger(0);
            try (C4CollectionObserver dbObs = c4Collection.createCollectionObserver(otherCount::incrementAndGet)) {
                assertEquals(0, otherCount.get());

                createRev("A", "1-aa", fleeceBody);
                assertEquals(1, this.callbackCount.get());
                assertEquals(1, otherCount.get());
                createRev("B", "1-bb", fleeceBody);
                assertEquals(1, this.callbackCount.get());
                assertEquals(1, otherCount.get());

                checkChanges(collObserver, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);
                checkChanges(dbObs, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

                createRev("B", "2-bbbb", fleeceBody);
                assertEquals(2, this.callbackCount.get());
                assertEquals(2, otherCount.get());
                createRev("C", "1-cc", fleeceBody);
                assertEquals(2, this.callbackCount.get());
                assertEquals(2, otherCount.get());

                checkChanges(collObserver, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);
                checkChanges(dbObs, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);

                collObserver.close();
                collObserver = null;
            }

            createRev("A", "2-aaaa", fleeceBody);
            assertEquals(2, this.callbackCount.get());
            assertEquals(2, otherCount.get());
        }
        finally {
            if (collObserver != null) { collObserver.close(); }
        }
    }

    private void checkChanges(
        C4CollectionObserver observer,
        List<String> expectedDocIDs,
        List<String> expectedRevIDs,
        boolean expectedExternal) {
        C4DocumentChange[] changes = observer.getChanges(100);
        assertNotNull(changes);
        assertEquals(expectedDocIDs.size(), changes.length);
        for (int i = 0; i < changes.length; i++) {
            assertEquals(expectedDocIDs.get(i), changes[i].getDocID());
            assertEquals(expectedRevIDs.get(i), changes[i].getRevID());
            assertEquals(expectedExternal, changes[i].isExternal());
        }
    }
}
