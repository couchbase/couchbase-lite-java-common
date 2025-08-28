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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.internal.core.C4DocumentChange;
import com.couchbase.lite.internal.listener.ChangeNotifier;
import com.couchbase.lite.internal.utils.Fn;


@SuppressWarnings("ConstantConditions")
public class NotificationTest extends BaseDbTest {
    @Test
    public void testCollectionChange() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger n = new AtomicInteger(0);
        try (ListenerToken ignore = getTestCollection().addChangeListener(
            getTestSerialExecutor(),
            change -> {
                Assert.assertNotNull(change);
                Assert.assertEquals(getTestCollection(), change.getCollection());
                List<String> ids = change.getDocumentIDs();
                Assert.assertNotNull(ids);
                if (n.addAndGet(ids.size()) >= 10) { latch.countDown(); }
            })) {
            for (int i = 0; i < 10; i++) {
                MutableDocument doc = new MutableDocument();
                doc.setValue("type", "demo");
                saveDocInTestCollection(doc);
            }

            Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
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
        try (ListenerToken ignore = getTestCollection().addDocumentChangeListener(
            mDocA.getId(),
            change -> {
                Assert.assertNotNull(change);
                Assert.assertEquals("A", change.getDocumentID());
                Assert.assertEquals(1, latch.getCount());
                latch.countDown();
            })) {
            saveDocInTestCollection(mDocB);
            saveDocInTestCollection(mDocA);
            Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
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
        try (ListenerToken ignore = getTestCollection().addDocumentChangeListener(
            docA.getId(),
            change -> {
                Assert.assertNotNull(change);
                Assert.assertEquals("A", change.getDocumentID());
                Assert.assertEquals(1, latch.getCount());
                latch.countDown();
            })) {
            mDocB = docB.toMutable();
            mDocB.setValue("thewronganswer", 42);
            saveDocInTestCollection(mDocB);

            mDocA = docA.toMutable();
            mDocA.setValue("thewronganswer", 18);
            saveDocInTestCollection(mDocA);
            Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
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
        try (ListenerToken ignore = getTestCollection().addDocumentChangeListener(
            docA.getId(),
            change -> {
                Assert.assertNotNull(change);
                Assert.assertEquals("A", change.getDocumentID());
                Assert.assertEquals(1, latch.getCount());
                latch.countDown();
            })) {
            getTestCollection().delete(docB);
            getTestCollection().delete(docA);
            Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testExternalChanges() throws InterruptedException, CouchbaseLiteException {
        final Database db2 = getTestDatabase().copy();
        final Collection coll2 = BaseDbTestKt.getSimilarCollection(db2, getTestCollection());
        Assert.assertNotNull(coll2);

        final AtomicInteger counter = new AtomicInteger(0);

        ListenerToken token = null;
        try {
            final CountDownLatch latchDB = new CountDownLatch(1);
            coll2.addChangeListener(
                getTestSerialExecutor(),
                change -> {
                    Assert.assertNotNull(change);
                    if (counter.addAndGet(change.getDocumentIDs().size()) >= 10) {
                        Assert.assertEquals(1, latchDB.getCount());
                        latchDB.countDown();
                    }
                });

            final CountDownLatch latchDoc = new CountDownLatch(1);
            token = coll2.addDocumentChangeListener(
                "doc-6", getTestSerialExecutor(), change -> {
                    Assert.assertNotNull(change);
                    Assert.assertEquals("doc-6", change.getDocumentID());
                    Document doc = BaseDbTestKt.getNonNullDoc(coll2, change.getDocumentID());
                    Assert.assertEquals("demo", doc.getString("type"));
                    Assert.assertEquals(1, latchDoc.getCount());
                    latchDoc.countDown();
                });

            getTestDatabase().inBatch(() -> {
                for (int i = 0; i < 10; i++) {
                    MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%d", i));
                    doc.setValue("type", "demo");
                    saveDocInTestCollection(doc);
                }
            });

            Assert.assertTrue(latchDB.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
            Assert.assertTrue(latchDoc.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
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
        try (ListenerToken ignore1 = getTestCollection().addDocumentChangeListener(id, listener);
             ListenerToken ignore2 = getTestCollection().addDocumentChangeListener(id, listener);
             ListenerToken ignore3 = getTestCollection().addDocumentChangeListener(id, listener);
             ListenerToken ignore4 = getTestCollection().addDocumentChangeListener(id, listener);
             ListenerToken ignore5 = getTestCollection().addDocumentChangeListener(id, listener)
        ) {
            doc1.setValue("name", "Scott Tiger");
            saveDocInTestCollection(doc1);
            Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
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

        try (ListenerToken token = getTestCollection().addDocumentChangeListener(id, listener)) {
            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scott Tiger");
            savedDoc1 = saveDocInTestCollection(doc1);

            Assert.assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

            // Remove change listener:
            token.remove();

            // Update doc1:
            doc1 = savedDoc1.toMutable();
            doc1.setValue("name", "Scotty");
            saveDocInTestCollection(doc1);

            Assert.assertFalse(latch2.await(500, TimeUnit.MILLISECONDS));
            Assert.assertEquals(1, latch2.getCount());
        }
    }

    @Test
    public void testCollectionChangeNotifier() {
        CollectionChangeNotifier changeNotifier = new CollectionChangeNotifier(getTestCollection());
        Assert.assertEquals(0, changeNotifier.getListenerCount());

        Fn.Consumer<ListenerToken> onRemove = token -> {
            int count = changeNotifier.getListenerCount();
            boolean empty = changeNotifier.removeChangeListener(token);
            Assert.assertTrue((count > 1) != empty);
        };

        ListenerToken t1 = changeNotifier.addChangeListener(null, c -> { }, onRemove);
        Assert.assertEquals(1, changeNotifier.getListenerCount());

        ListenerToken t2 = changeNotifier.addChangeListener(null, c -> { }, onRemove);
        Assert.assertEquals(2, changeNotifier.getListenerCount());

        t2.remove();
        Assert.assertEquals(1, changeNotifier.getListenerCount());

        t1.remove();
        Assert.assertEquals(0, changeNotifier.getListenerCount());

        t1.remove();
        Assert.assertEquals(0, changeNotifier.getListenerCount());

        t2.remove();
        Assert.assertEquals(0, changeNotifier.getListenerCount());
    }

    // CBL-4989 and CBL-4991: Check a few DocumentChange corner cases:
    // - null is a legal rev id
    // - null is not a legal doc id
    // - a list of changes that contains only nulls does not prevent further processing
    // - an empty change list does stop further processing
    @Test
    public void testCollectionChanged() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger changeCount = new AtomicInteger(0);
        AtomicInteger callCount = new AtomicInteger(0);

        ChangeNotifier.C4ChangeProducer<C4DocumentChange> mockProducer
            = new ChangeNotifier.C4ChangeProducer<C4DocumentChange>() {
            @Override
            public List<C4DocumentChange> getChanges(int maxChanges) {
                int n = callCount.incrementAndGet();
                switch (n) {
                    case 1:
                        return Arrays.asList(C4DocumentChange.createC4DocumentChange("A", "r1", 0L, true));
                    case 2:
                        return Arrays.asList(C4DocumentChange.createC4DocumentChange("B", null, 0L, true));
                    case 3:
                        return Arrays.asList(C4DocumentChange.createC4DocumentChange(null, null, 0L, true));
                    case 4:
                        return Arrays.asList();
                    case 5:
                        return Arrays.asList(C4DocumentChange.createC4DocumentChange("C", "r1", 0L, true));
                    case 6:
                        return null;
                    default:
                        return Arrays.asList(C4DocumentChange.createC4DocumentChange("D", "r1", 0L, true));
                }
            }

            @Override
            public void close() { }
        };

        CollectionChangeNotifier notifier = new CollectionChangeNotifier(getTestCollection());
        notifier.addChangeListener(
            null,
            ch -> {
                changeCount.addAndGet(ch.getDocumentIDs().size());
                latch.countDown();
            },
            ign -> { });
        notifier.run(mockProducer);

        Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        Assert.assertEquals(3, changeCount.get());
        Assert.assertEquals(6, callCount.get());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testLegacyChangeAPI() throws CouchbaseLiteException, InterruptedException {
        Collection defaultCollection = getTestDatabase().getDefaultCollection();

        CountDownLatch latch1 = new CountDownLatch(1);
        DatabaseChangeListener dbListener = change -> latch1.countDown();
        dbListener.changed(new DatabaseChange(getTestDatabase(), Collections.emptyList()));
        Assert.assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

        CountDownLatch latch2 = new CountDownLatch(1);
        CollectionChangeListener colListener = change -> latch2.countDown();
        colListener.changed(new CollectionChange(getTestCollection(), Collections.emptyList()));
        Assert.assertTrue(latch2.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
    }

    // Kotlin shims

    private Document saveDocInTestCollection(MutableDocument mDoc) {
        return saveDocInCollection(mDoc, getTestCollection());
    }
}
