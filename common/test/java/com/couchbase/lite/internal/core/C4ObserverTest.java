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
        createRev("A", getTestRevId("aa", 1), fleeceBody);
        try (C4DocumentObserver obs = c4Collection.createDocumentObserver("A", callbackCount::incrementAndGet)) {
            assertEquals(0, callbackCount.get());

            createRev("A", getTestRevId("bb", 2), fleeceBody);
            createRev("B", getTestRevId("bb", 1), fleeceBody);
            assertEquals(1, callbackCount.get());
        }
    }

    @Test
    public void testCollectionObserver() throws LiteCoreException {
        C4CollectionObserver collObserver = null;
        try {
            collObserver = c4Collection.createCollectionObserver(callbackCount::incrementAndGet);
            assertEquals(0, callbackCount.get());

            String revId1 = getTestRevId("aa", 1);
            createRev("A", revId1, fleeceBody);
            assertEquals(1, callbackCount.get());
            String revId2 = getTestRevId("bb", 1);
            createRev("B", revId2, fleeceBody);
            assertEquals(1, callbackCount.get());

            checkChanges(collObserver, Arrays.asList("A", "B"), Arrays.asList(revId1, revId2), false);

            revId1 = getTestRevId("bbbb", 2);
            createRev("B", revId1, fleeceBody);
            assertEquals(2, callbackCount.get());
            revId2 = getTestRevId("cc", 1);
            createRev("C", revId2, fleeceBody);
            assertEquals(2, callbackCount.get());

            checkChanges(collObserver, Arrays.asList("B", "C"), Arrays.asList(revId1, revId2), false);

            collObserver.close();
            collObserver = null;

            createRev("A", getTestRevId("aaaa", 2), fleeceBody);
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

            String revId1 = getTestRevId("aa", 1);
            createRev("A", revId1, fleeceBody);
            assertEquals(1, callbackCount.get());
            String revId2 = getTestRevId("bb", 1);
            createRev("B", revId2, fleeceBody);
            assertEquals(1, callbackCount.get());

            checkChanges(collObserver, Arrays.asList("A", "B"), Arrays.asList(revId1, revId2), false);

            try (C4Database otherDb = C4Database.getDatabase(dbParentDirPath, dbName, C4Database.DB_FLAGS)) {
                assertNotNull(otherDb);

                revId1 = getTestRevId("cc", 1);
                revId2 = getTestRevId("dd", 1);
                String revId3 = getTestRevId("ee", 1);
                try (C4Collection otherColl = otherDb.getCollection(c4Collection.getScope(), c4Collection.getName())) {
                    assertNotNull(otherColl);

                    boolean commit = false;
                    otherDb.beginTransaction();
                    try {
                        createRev(otherColl, "c", revId1, fleeceBody);
                        createRev(otherColl, "d", revId2, fleeceBody);
                        createRev(otherColl, "e", revId3, fleeceBody);
                        commit = true;
                    }
                    finally {
                        otherDb.endTransaction(commit);
                    }
                }

                assertEquals(2, callbackCount.get());
                checkChanges(collObserver, Arrays.asList("c", "d", "e"), Arrays.asList(revId1, revId2, revId3), true);

                collObserver.close();
                collObserver = null;

                createRev("A", getTestRevId("aaaa", 2), fleeceBody);
                assertEquals(2, callbackCount.get());
            }
        }
        finally {
            if (collObserver != null) { collObserver.close(); }
        }
    }

    @Test
    public void testMultiDBObservers() throws LiteCoreException {
        C4CollectionObserver collObserver = null;
        try {
            collObserver = c4Collection.createCollectionObserver(callbackCount::incrementAndGet);
            assertEquals(0, callbackCount.get());

            final AtomicInteger otherCount = new AtomicInteger(0);
            try (C4CollectionObserver dbObs = c4Collection.createCollectionObserver(otherCount::incrementAndGet)) {
                assertEquals(0, otherCount.get());

                String revId1 = getTestRevId("aa", 1);
                createRev("A", revId1, fleeceBody);
                assertEquals(1, this.callbackCount.get());
                assertEquals(1, otherCount.get());
                String revId2 = getTestRevId("bb", 1);
                createRev("B", revId2, fleeceBody);
                assertEquals(1, this.callbackCount.get());
                assertEquals(1, otherCount.get());

                checkChanges(collObserver, Arrays.asList("A", "B"), Arrays.asList(revId1, revId2), false);
                checkChanges(dbObs, Arrays.asList("A", "B"), Arrays.asList(revId1, revId2), false);

                revId1 = getTestRevId("bbbb", 2);
                createRev("B", revId1, fleeceBody);
                assertEquals(2, this.callbackCount.get());
                assertEquals(2, otherCount.get());
                revId2 = getTestRevId("cc", 1);
                createRev("C", revId2, fleeceBody);
                assertEquals(2, this.callbackCount.get());
                assertEquals(2, otherCount.get());

                checkChanges(collObserver, Arrays.asList("B", "C"), Arrays.asList(revId1, revId2), false);
                checkChanges(dbObs, Arrays.asList("B", "C"), Arrays.asList(revId1, revId2), false);

                collObserver.close();
                collObserver = null;
            }

            createRev("A", getTestRevId("aaaa", 2), fleeceBody);
            assertEquals(2, this.callbackCount.get());
            assertEquals(2, otherCount.get());
        }
        finally {
            if (collObserver != null) { collObserver.close(); }
        }
    }
}
