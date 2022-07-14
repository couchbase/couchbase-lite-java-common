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
package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


// !!! ADD COLLECTION NOTIFICATION TESTS

public class NotificationTest extends BaseDbTest {
    @Test
    public void testDatabaseChange() throws InterruptedException, CouchbaseLiteException {
        final CountDownLatch latch = new CountDownLatch(1);

        int[] n = {0};
        baseTestDb.addChangeListener(
            testSerialExecutor,
            change -> {
                assertNotNull(change);
                assertEquals(baseTestDb, change.getDatabase());
                List<String> ids = change.getDocumentIDs();
                assertNotNull(ids);
                n[0] += ids.size();
                if (n[0] >= 10) { latch.countDown(); }
            });

        for (int i = 0; i < 10; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
            doc.setValue("type", "demo");
            saveDocInBaseTestDb(doc);
        }

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void testDocumentChangeOnSave() throws InterruptedException, CouchbaseLiteException {
        MutableDocument mDocA = new MutableDocument("A");
        mDocA.setValue("theanswer", 18);
        MutableDocument mDocB = new MutableDocument("B");
        mDocB.setValue("thewronganswer", 18);

        // save doc
        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = baseTestDb.addDocumentChangeListener(
            mDocA.getId(),
            change -> {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch.getCount());
                latch.countDown();
            });
        try {
            saveDocInBaseTestDb(mDocB);
            saveDocInBaseTestDb(mDocA);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testDocumentChangeOnUpdate() throws InterruptedException, CouchbaseLiteException {
        MutableDocument mDocA = new MutableDocument("A");
        mDocA.setValue("theanswer", 18);
        Document docA = saveDocInBaseTestDb(mDocA);
        MutableDocument mDocB = new MutableDocument("B");
        mDocB.setValue("thewronganswer", 18);
        Document docB = saveDocInBaseTestDb(mDocB);

        // update doc
        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = baseTestDb.addDocumentChangeListener(
            docA.getId(),
            change -> {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch.getCount());
                latch.countDown();
            });
        try {
            mDocB = docB.toMutable();
            mDocB.setValue("thewronganswer", 42);
            saveDocInBaseTestDb(mDocB);

            mDocA = docA.toMutable();
            mDocA.setValue("thewronganswer", 18);
            saveDocInBaseTestDb(mDocA);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testDocumentChangeOnDelete() throws InterruptedException, CouchbaseLiteException {
        MutableDocument mDocA = new MutableDocument("A");
        mDocA.setValue("theanswer", 18);
        Document docA = saveDocInBaseTestDb(mDocA);
        MutableDocument mDocB = new MutableDocument("B");
        mDocB.setValue("thewronganswer", 18);
        Document docB = saveDocInBaseTestDb(mDocB);

        // delete doc
        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = baseTestDb.addDocumentChangeListener(
            docA.getId(),
            change -> {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch.getCount());
                latch.countDown();
            });
        try {
            baseTestDb.delete(docB);
            baseTestDb.delete(docA);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testExternalChanges() throws InterruptedException, CouchbaseLiteException {
        final Database db2 = baseTestDb.copy();
        assertNotNull(db2);

        final AtomicInteger counter = new AtomicInteger(0);

        ListenerToken token = null;
        try {
            final CountDownLatch latchDB = new CountDownLatch(1);
            db2.addChangeListener(
                testSerialExecutor,
                change -> {
                    assertNotNull(change);
                    if (counter.addAndGet(change.getDocumentIDs().size()) >= 10) {
                        assertEquals(1, latchDB.getCount());
                        latchDB.countDown();
                    }
                });

            final CountDownLatch latchDoc = new CountDownLatch(1);
            token = db2.addDocumentChangeListener("doc-6", testSerialExecutor, change -> {
                assertNotNull(change);
                assertEquals("doc-6", change.getDocumentID());
                Document doc = db2.getDocument(change.getDocumentID());
                assertEquals("demo", doc.getString("type"));
                assertEquals(1, latchDoc.getCount());
                latchDoc.countDown();
            });

            baseTestDb.inBatch(() -> {
                for (int i = 0; i < 10; i++) {
                    MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                    doc.setValue("type", "demo");
                    saveDocInBaseTestDb(doc);
                }
            });

            assertTrue(latchDB.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
            assertTrue(latchDoc.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            if (token != null) { token.remove(); }
            db2.close();
        }
    }

    @Test
    public void testAddSameChangeListeners()
        throws InterruptedException, CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        Document savedDoc1 = saveDocInBaseTestDb(doc1);

        final CountDownLatch latch = new CountDownLatch(5);
        // Add change listeners:
        DocumentChangeListener listener = change -> {
            if (change.getDocumentID().equals("doc1")) { latch.countDown(); }
        };
        ListenerToken token1 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token2 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token3 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token4 = baseTestDb.addDocumentChangeListener("doc1", listener);
        ListenerToken token5 = baseTestDb.addDocumentChangeListener("doc1", listener);

        try {
            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scott Tiger");
            saveDocInBaseTestDb(doc1);

            // Let's only wait for 0.5 seconds:
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        }
        finally {
            token1.remove();
            token2.remove();
            token3.remove();
            token4.remove();
            token5.remove();
        }
    }

    @Test
    public void testRemoveDocumentChangeListener()
        throws InterruptedException, CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        Document savedDoc1 = saveDocInBaseTestDb(doc1);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);
        // Add change listeners:
        DocumentChangeListener listener = change -> {
            if (change.getDocumentID().equals("doc1")) {
                latch1.countDown();
                latch2.countDown();
            }
        };

        ListenerToken token = baseTestDb.addDocumentChangeListener("doc1", listener);
        try {
            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scott Tiger");
            savedDoc1 = saveDocInBaseTestDb(doc1);

            // Let's only wait for 0.5 seconds:
            assertTrue(latch1.await(500, TimeUnit.MILLISECONDS));

            // Remove change listener:
            token.remove();

            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scotty");
            saveDocInBaseTestDb(doc1);

            assertFalse(latch2.await(500, TimeUnit.MILLISECONDS));
            assertEquals(1, latch2.getCount());
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testDatabaseChangeNotifier() throws CouchbaseLiteException {
        Database db = createDb("default_config_db");
        try {
            CollectionChangeNotifier changeNotifier = new CollectionChangeNotifier(db.getDefaultCollection());
            assertEquals(0, changeNotifier.getListenerCount());
            ListenerToken t1 = changeNotifier.addChangeListener(
                null,
                c -> { },
                t -> assertTrue(changeNotifier.removeChangeListener(t)));
            assertEquals(1, changeNotifier.getListenerCount());
            ListenerToken t2 = changeNotifier.addChangeListener(
                null,
                c -> { },
                t -> assertFalse(changeNotifier.removeChangeListener(t)));
            assertEquals(2, changeNotifier.getListenerCount());
            t2.remove();
            assertEquals(1, changeNotifier.getListenerCount());
            t1.remove();
            assertEquals(0, changeNotifier.getListenerCount());
            t1.remove();
            assertEquals(0, changeNotifier.getListenerCount());
            t2.remove();
            assertEquals(0, changeNotifier.getListenerCount());
        }
        finally {
            deleteDb(db);
        }
    }

    @Test
    public void testDatabaseChangeAPI() throws CouchbaseLiteException, InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        // don't change this to a lambda.
        DatabaseChangeListener dbListener = new DatabaseChangeListener() {
            @Override
            public void changed(@NonNull DatabaseChange change) { latch1.countDown(); }
        };
        dbListener.changed(new DatabaseChange(baseTestDb.getDefaultCollection(), Collections.emptyList()));
        assertTrue(latch1.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        CountDownLatch latch2 = new CountDownLatch(1);
        // don't change this to a lambda.
        CollectionChangeListener colListener = new CollectionChangeListener() {
            @Override
            public void changed(@NonNull CollectionChange change) { latch2.countDown(); }
        };
        colListener.changed(new CollectionChange(baseTestDb.getDefaultCollection(), Collections.emptyList()));
        assertTrue(latch2.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        CountDownLatch latch3 = new CountDownLatch(2);
        ListenerToken t1 = null;
        ListenerToken t2 = null;
        try {
            // don't change this to a lambda.
            t1 = baseTestDb.addChangeListener(
                new DatabaseChangeListener() {
                    @Override
                    public void changed(@NonNull DatabaseChange change) { latch3.countDown(); }
                });
            // don't change this to a lambda.
            t2 = baseTestDb.getDefaultCollection().addChangeListener(
                new CollectionChangeListener() {
                    @Override
                    public void changed(@NonNull CollectionChange change) { latch3.countDown(); }
                });
            assertEquals(2, baseTestDb.getDefaultCollection().getCollectionListenerCount());
            createDocsInDb(1000, 1, baseTestDb);
            assertTrue(latch3.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
        finally {
            if (t1 != null) { t1.remove(); }
            if (t2 != null) { t2.remove(); }
        }
        assertEquals(0, baseTestDb.getDefaultCollection().getCollectionListenerCount());

        CountDownLatch latch4 = new CountDownLatch(2);
        ListenerToken t3 = null;
        ListenerToken t4 = null;
        try {
            t3 = baseTestDb.addChangeListener(change -> latch4.countDown());
            t4 = baseTestDb.getDefaultCollection().addChangeListener(change -> latch4.countDown());
            assertEquals(2, baseTestDb.getDefaultCollection().getCollectionListenerCount());
            createDocsInDb(2000, 1, baseTestDb);
            assertTrue(latch4.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
        finally {
            if (t3 != null) { t3.remove(); }
            if (t4 != null) { t4.remove(); }
        }
        assertEquals(0, baseTestDb.getDefaultCollection().getCollectionListenerCount());
    }
}
