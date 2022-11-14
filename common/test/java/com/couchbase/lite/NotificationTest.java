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

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class NotificationTest extends BaseDbTest {
    @Test
    public void testCollectionChange() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger n = new AtomicInteger(0);
        ListenerToken token = testCollection.addChangeListener(
            testSerialExecutor,
            change -> {
                assertNotNull(change);
                assertEquals(testCollection, change.getCollection());
                List<String> ids = change.getDocumentIDs();
                assertNotNull(ids);
                if (n.addAndGet(ids.size()) >= 10) { latch.countDown(); }
            });

        try {
            for (int i = 0; i < 10; i++) {
                MutableDocument doc = new MutableDocument();
                doc.setValue("type", "demo");
                saveDocInTestCollection(doc);
            }

            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testCollectionChangeOnSave() throws InterruptedException {
        MutableDocument mDocA = new MutableDocument("A");
        mDocA.setValue("theanswer", 18);
        MutableDocument mDocB = new MutableDocument("B");
        mDocB.setValue("thewronganswer", 18);

        // save doc
        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = testCollection.addDocumentChangeListener(
            mDocA.getId(),
            change -> {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch.getCount());
                latch.countDown();
            });
        try {
            saveDocInTestCollection(mDocB);
            saveDocInTestCollection(mDocA);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testCollectionChangeOnUpdate() throws InterruptedException {
        MutableDocument mDocA = new MutableDocument("A");
        mDocA.setValue("theanswer", 18);
        Document docA = saveDocInTestCollection(mDocA);
        MutableDocument mDocB = new MutableDocument("B");
        mDocB.setValue("thewronganswer", 18);
        Document docB = saveDocInTestCollection(mDocB);

        // update doc
        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = testCollection.addDocumentChangeListener(
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
            saveDocInTestCollection(mDocB);

            mDocA = docA.toMutable();
            mDocA.setValue("thewronganswer", 18);
            saveDocInTestCollection(mDocA);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testCollectionChangeOnDelete() throws InterruptedException, CouchbaseLiteException {
        MutableDocument mDocA = new MutableDocument("A");
        mDocA.setValue("theanswer", 18);
        Document docA = saveDocInTestCollection(mDocA);
        MutableDocument mDocB = new MutableDocument("B");
        mDocB.setValue("thewronganswer", 18);
        Document docB = saveDocInTestCollection(mDocB);

        // delete doc
        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = testCollection.addDocumentChangeListener(
            docA.getId(),
            change -> {
                assertNotNull(change);
                assertEquals("A", change.getDocumentID());
                assertEquals(1, latch.getCount());
                latch.countDown();
            });
        try {
            testCollection.delete(docB);
            testCollection.delete(docA);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testExternalChanges() throws InterruptedException, CouchbaseLiteException {
        final Database db2 = testDatabase.copy();
        final Collection coll2 = BaseDbTestKt.getSimilarCollection(db2, testCollection);
        assertNotNull(coll2);

        final AtomicInteger counter = new AtomicInteger(0);

        ListenerToken token = null;
        try {
            final CountDownLatch latchDB = new CountDownLatch(1);
            coll2.addChangeListener(
                testSerialExecutor,
                change -> {
                    assertNotNull(change);
                    if (counter.addAndGet(change.getDocumentIDs().size()) >= 10) {
                        assertEquals(1, latchDB.getCount());
                        latchDB.countDown();
                    }
                });

            final CountDownLatch latchDoc = new CountDownLatch(1);
            token = coll2.addDocumentChangeListener("doc-6", testSerialExecutor, change -> {
                assertNotNull(change);
                assertEquals("doc-6", change.getDocumentID());
                Document doc = BaseDbTestKt.getNonNullDoc(coll2, change.getDocumentID());
                assertEquals("demo", doc.getString("type"));
                assertEquals(1, latchDoc.getCount());
                latchDoc.countDown();
            });

            testDatabase.inBatch(() -> {
                for (int i = 0; i < 10; i++) {
                    MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                    doc.setValue("type", "demo");
                    saveDocInTestCollection(doc);
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
    public void testAddSameChangeListeners() throws InterruptedException {
        MutableDocument doc1 = new MutableDocument();
        String id = doc1.getId();
        doc1.setValue("name", "Scott");
        saveDocInTestCollection(doc1);

        final CountDownLatch latch = new CountDownLatch(5);
        // Add change listeners:
        DocumentChangeListener listener = change -> {
            if (change.getDocumentID().equals(id)) { latch.countDown(); }
        };
        ListenerToken token1 = testCollection.addDocumentChangeListener(id, listener);
        ListenerToken token2 = testCollection.addDocumentChangeListener(id, listener);
        ListenerToken token3 = testCollection.addDocumentChangeListener(id, listener);
        ListenerToken token4 = testCollection.addDocumentChangeListener(id, listener);
        ListenerToken token5 = testCollection.addDocumentChangeListener(id, listener);

        try {
            doc1.setValue("name", "Scott Tiger");
            saveDocInTestCollection(doc1);
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
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
        throws InterruptedException {
        MutableDocument doc1 = new MutableDocument();
        String id = doc1.getId();
        doc1.setValue("name", "Scott");
        Document savedDoc1 = saveDocInTestCollection(doc1);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);
        // Add change listeners:
        DocumentChangeListener listener = change -> {
            if (change.getDocumentID().equals(id)) {
                latch1.countDown();
                latch2.countDown();
            }
        };

        ListenerToken token = testCollection.addDocumentChangeListener(id, listener);
        try {
            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scott Tiger");
            savedDoc1 = saveDocInTestCollection(doc1);

            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

            // Remove change listener:
            token.remove();

            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scotty");
            saveDocInTestCollection(doc1);

            assertFalse(latch2.await(500, TimeUnit.MILLISECONDS));
            assertEquals(1, latch2.getCount());
        }
        finally {
            token.remove();
        }
    }

    @Test
    public void testCollectionChangeNotifier() {
        CollectionChangeNotifier changeNotifier = new CollectionChangeNotifier(testCollection);
        assertEquals(0, changeNotifier.getListenerCount());

        Fn.Consumer<ListenerToken> onRemove = token -> {
            int count = changeNotifier.getListenerCount();
            boolean empty = changeNotifier.removeChangeListener(token);
            assertTrue((count > 1) != empty);
        };

        ListenerToken t1 = changeNotifier.addChangeListener(null, c -> { }, onRemove);
        assertEquals(1, changeNotifier.getListenerCount());

        ListenerToken t2 = changeNotifier.addChangeListener(null, c -> { }, onRemove);
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

    @SuppressWarnings("deprecation")
    @Test
    public void testLegacyChangeAPI() throws CouchbaseLiteException, InterruptedException {
        Collection defaultCollection = testDatabase.getDefaultCollection();

        CountDownLatch latch1 = new CountDownLatch(1);
        DatabaseChangeListener dbListener = change -> latch1.countDown();
        dbListener.changed(new DatabaseChange(testDatabase, Collections.emptyList()));
        assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

        CountDownLatch latch2 = new CountDownLatch(1);
        CollectionChangeListener colListener = change -> latch2.countDown();
        colListener.changed(new CollectionChange(testCollection, Collections.emptyList()));
        assertTrue(latch2.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

        CountDownLatch latch3 = new CountDownLatch(2);
        ListenerToken t1 = null;
        ListenerToken t2 = null;
        try {
            t1 = testDatabase.addChangeListener(change -> latch3.countDown());
            t2 = defaultCollection.addChangeListener(change -> latch3.countDown());
            assertEquals(2, defaultCollection.getCollectionListenerCount());
            saveDocsInCollection(createTestDocs(1000, 10), defaultCollection, null);
            assertTrue(latch3.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            if (t1 != null) { t1.remove(); }
            if (t2 != null) { t2.remove(); }
        }
        assertEquals(0, defaultCollection.getCollectionListenerCount());

        CountDownLatch latch4 = new CountDownLatch(2);
        ListenerToken t3 = null;
        ListenerToken t4 = null;
        try {
            t3 = testDatabase.addChangeListener(change -> latch4.countDown());
            t4 = defaultCollection.addChangeListener(change -> latch4.countDown());
            assertEquals(2, defaultCollection.getCollectionListenerCount());
            saveDocsInCollection(createTestDocs(2000, 10), defaultCollection, null);
            assertTrue(latch4.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            if (t3 != null) { t3.remove(); }
            if (t4 != null) { t4.remove(); }
        }
        assertEquals(0, defaultCollection.getCollectionListenerCount());
    }

    // Kotlin shims

    private Document saveDocInTestCollection(MutableDocument mDoc) {
        return saveDocInCollection(mDoc, testCollection, null);
    }
}
