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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class ConcurrencyTest extends LegacyBaseDbTest {
    private static final long TIMEOUT = 180L;

    interface Callback {
        void callback(int threadIndex);
    }

    interface VerifyBlock<T> {
        void verify(int n, T result);
    }

    private final AtomicReference<AssertionError> testFailure = new AtomicReference<>();

    @Before
    public final void setUpConcurrencyTest() { testFailure.set(null); }


    @Test
    public void testConcurrentCreate() throws CouchbaseLiteException {
        Database.log.getConsole().setLevel(LogLevel.DEBUG);
        final int kNDocs = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            kWaitInSec,
            threadIndex -> {
                String tag = "tag-" + threadIndex;
                try { createDocs(kNDocs, tag); }
                catch (CouchbaseLiteException e) { fail(); }
            }
        );

        // validate stored documents
        for (int i = 0; i < kNThreads; i++) { verifyByTagName("tag-" + i, kNDocs); }
    }

    @Test
    public void testConcurrentCreateInBatch() throws CouchbaseLiteException {
        final int kNDocs = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            kWaitInSec,
            threadIndex -> {
                final String tag = "tag-" + threadIndex;
                try { baseTestDb.inBatch(() -> createDocs(kNDocs, tag)); }
                catch (CouchbaseLiteException e) { fail(); }
            }
        );

        checkForFailure();

        // validate stored documents
        for (int i = 0; i < kNThreads; i++) { verifyByTagName("tag-" + i, kNDocs); }
    }

    @SlowTest
    @Test
    public void testConcurrentUpdate() throws CouchbaseLiteException {
        // ??? Increasing number of threads causes crashes
        final int nDocs = 5;
        final int nThreads = 4;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(nDocs, "Create");
        assertEquals(nDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(
            nThreads,
            600,
            threadIndex -> {
                String tag = "tag-" + threadIndex;
                assertTrue(updateDocs(docIDs, 50, tag));
            }
        );

        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < nThreads; i++) { verifyByTagName("tag-" + i, (n, result) -> count.incrementAndGet()); }

        assertEquals(nDocs, count.intValue());
    }

    @Test
    public void testConcurrentRead() throws CouchbaseLiteException {
        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(kNThreads, kWaitInSec, threadIndex -> readDocs(docIDs, kNRounds));
    }

    @Test
    public void testConcurrentReadInBatch() throws CouchbaseLiteException {
        final int kNDocs = 5;
        final int kNRounds = 50;
        final int kNThreads = 4;
        final int kWaitInSec = 180;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // concurrently creates documents
        concurrentValidator(
            kNThreads,
            kWaitInSec,
            threadIndex -> {
                try { baseTestDb.inBatch(() -> readDocs(docIDs, kNRounds)); }
                catch (CouchbaseLiteException e) { fail(); }
            }
        );
    }

    @Test
    public void testConcurrentReadAndUpdate() throws InterruptedException, CouchbaseLiteException {
        final int kNDocs = 5;
        final int kNRounds = 50;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        // Read:
        final CountDownLatch latch1 = new CountDownLatch(1);
        testOnNewThread("testConcurrentReadAndUpdate-1", latch1, () -> readDocs(docIDs, kNRounds));

        // Update:
        final CountDownLatch latch2 = new CountDownLatch(1);
        final String tag = "Update";
        testOnNewThread("testConcurrentReadAndUpdate-2", latch2, () -> assertTrue(updateDocs(docIDs, kNRounds, tag)));

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();

        verifyByTagName(tag, kNDocs);
    }

    @Test
    public void testConcurrentDelete() throws InterruptedException, CouchbaseLiteException {
        final int kNDocs = 100;

        // createDocs2 returns synchronized List.
        final List<String> docIDs = createDocs(kNDocs, "Create");
        assertEquals(kNDocs, docIDs.size());

        final CountDownLatch latch1 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentDelete-1",
            latch1,
            () -> {
                for (String docID: docIDs) {
                    try {
                        Document doc = baseTestDb.getDocument(docID);
                        if (doc != null) { baseTestDb.delete(doc); }
                    }
                    catch (CouchbaseLiteException e) { fail(); }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentDelete-2",
            latch2,
            () -> {
                for (String docID: docIDs) {
                    try {
                        Document doc = baseTestDb.getDocument(docID);
                        if (doc != null) { baseTestDb.delete(doc); }
                    }
                    catch (CouchbaseLiteException e) { fail(); }
                }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();

        assertEquals(0, baseTestDb.getCount());
    }

    @Test
    public void testConcurrentPurge() throws InterruptedException, CouchbaseLiteException {
        final int nDocs = 100;

        // createDocs returns synchronized List.
        final List<String> docIDs = createDocs(nDocs, "Create");
        assertEquals(nDocs, docIDs.size());

        final CountDownLatch latch1 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentPurge-1",
            latch1,
            () -> {
                for (String docID: docIDs) {
                    Document doc = baseTestDb.getDocument(docID);
                    if (doc != null) {
                        try { baseTestDb.purge(doc); }
                        catch (CouchbaseLiteException e) { assertEquals(404, e.getCode()); }
                    }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentPurge-2",
            latch2,
            () -> {
                for (String docID: docIDs) {
                    Document doc = baseTestDb.getDocument(docID);
                    if (doc != null) {
                        try { baseTestDb.purge(doc); }
                        catch (CouchbaseLiteException e) { assertEquals(404, e.getCode()); }
                    }
                }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();

        assertEquals(0, baseTestDb.getCount());
    }

    @Test
    public void testConcurrentCreateAndCloseDB() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndCloseDB-1",
            latch1,
            () -> {
                try { createDocs(100, "Create1"); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        throw new AssertionError("Unrecognized exception", e);
                    }
                }
                // db not open
                catch (IllegalStateException ignore) { }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndCloseDB-2",
            latch2,
            () -> {
                try { baseTestDb.close(); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    public void testConcurrentCreateAndDeleteDB() throws InterruptedException {
        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final String tag1 = "Create1";
        testOnNewThread(
            "testConcurrentCreateAndDeleteDB-1",
            latch1,
            () -> {
                try { createDocs(kNDocs, tag1); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
                // db not open
                catch (IllegalStateException ignore) { }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndDeleteDB-2",
            latch2,
            () -> {
                try { baseTestDb.delete(); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    public void testConcurrentCreateAndCompactDB() throws InterruptedException {
        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndCompactDB-1",
            latch1,
            () -> {
                try { createDocs(kNDocs, "Create1"); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndCompactDB-2",
            latch2,
            () -> {
                try { assertTrue(baseTestDb.performMaintenance(MaintenanceType.COMPACT)); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    public void testConcurrentCreateAndCreateIndexDB() throws Exception {
        loadJSONResourceIntoDatabase("sentences.json");

        final int kNDocs = 100;

        final CountDownLatch latch1 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndCreateIndexDB-1",
            latch1,
            () -> {
                try { createDocs(kNDocs, "Create1"); }
                catch (CouchbaseLiteException e) {
                    if (!e.getDomain().equals(CBLError.Domain.CBLITE) || e.getCode() != CBLError.Code.NOT_OPEN) {
                        fail();
                    }
                }
            });

        final CountDownLatch latch2 = new CountDownLatch(1);
        testOnNewThread(
            "testConcurrentCreateAndCreateIndexDB-2",
            latch2,
            () -> {
                try {
                    Index index = IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence"));
                    baseTestDb.createIndex("sentence", index);
                }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    public void testBlockDatabaseChange() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        ListenerToken token = baseTestDb.addChangeListener(testSerialExecutor, change -> latch2.countDown());

        testOnNewThread(
            "testBlockDatabaseChange",
            latch1,
            () -> {
                try { baseTestDb.save(new MutableDocument("doc1")); }
                catch (CouchbaseLiteException e) { fail(); }
            });

        assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
        checkForFailure();
    }

    @Test
    public void testBlockDocumentChange() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        ListenerToken token = baseTestDb.addDocumentChangeListener("doc1", change -> latch2.countDown());
        try {
            testOnNewThread(
                "testBlockDocumentChange",
                latch1,
                () -> {
                    try { baseTestDb.save(new MutableDocument("doc1")); }
                    catch (CouchbaseLiteException e) { fail(); }
                });

            assertTrue(latch1.await(TIMEOUT, TimeUnit.SECONDS));
            assertTrue(latch2.await(TIMEOUT, TimeUnit.SECONDS));
            checkForFailure();
        }
        finally {
            baseTestDb.removeChangeListener(token);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1407
    @Test
    public void testQueryExecute() throws Exception {
        loadJSONResourceIntoDatabase("names_100.json");

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.sequence))
            .from(DataSource.database(baseTestDb));

        concurrentValidator(
            10,
            180,
            threadIndex -> {
                try (ResultSet rs = query.execute()) {
                    List<Result> results = rs.allResults();
                    assertEquals(100, results.size());
                    assertEquals(baseTestDb.getCount(), results.size());
                }
                catch (CouchbaseLiteException e) {
                    Report.log(LogLevel.ERROR, "Query Error", e);
                    fail();
                }
            }
        );
    }

    private MutableDocument createDocumentWithTag(String tag) {
        MutableDocument doc = new MutableDocument();

        // Tag
        doc.setValue("tag", tag);

        // String
        doc.setValue("firstName", "Daniel");
        doc.setValue("lastName", "Tiger");

        // Dictionary:
        MutableDictionary address = new MutableDictionary();
        address.setValue("street", "1 Main street");
        address.setValue("city", "Mountain View");
        address.setValue("state", "CA");
        doc.setValue("address", address);

        // Array:
        MutableArray phones = new MutableArray();
        phones.addValue("650-123-0001");
        phones.addValue("650-123-0002");
        doc.setValue("phones", phones);

        // Date:
        doc.setValue("updated", new Date());

        return doc;
    }

    private List<String> createDocs(int nDocs, String tag) throws CouchbaseLiteException {
        List<String> docs = Collections.synchronizedList(new ArrayList<>(nDocs));
        for (int i = 0; i < nDocs; i++) {
            MutableDocument doc = createDocumentWithTag(tag);
            docs.add(saveDocInBaseTestDb(doc).getId());
        }
        return docs;
    }

    private boolean updateDocs(List<String> docIds, int rounds, String tag) {
        for (int i = 1; i <= rounds; i++) {
            for (String docId: docIds) {
                Document d = baseTestDb.getDocument(docId);
                MutableDocument doc = d.toMutable();
                doc.setValue("tag", tag);

                MutableDictionary address = doc.getDictionary("address");
                assertNotNull(address);
                String street = String.format(Locale.ENGLISH, "%d street.", i);
                address.setValue("street", street);

                MutableArray phones = doc.getArray("phones");
                assertNotNull(phones);
                assertEquals(2, phones.count());
                String phone = String.format(Locale.ENGLISH, "650-000-%04d", i);
                phones.setValue(0, phone);

                doc.setValue("updated", new Date());
                try { baseTestDb.save(doc); }
                catch (CouchbaseLiteException e) { return false; }
            }
        }
        return true;
    }

    private void readDocs(List<String> docIDs, int rounds) {
        for (int i = 1; i <= rounds; i++) {
            for (String docID: docIDs) {
                Document doc = baseTestDb.getDocument(docID);
                assertNotNull(doc);
                assertEquals(docID, doc.getId());
            }
        }
    }

    private void verifyByTagName(String tag, VerifyBlock<Result> block) throws CouchbaseLiteException {
        int n = 0;
        try (ResultSet rs = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("tag").equalTo(Expression.string(tag)))
            .execute()) {
            for (Result result: rs) { block.verify(++n, result); }
        }
    }

    private void verifyByTagName(String tag, int nRows) throws CouchbaseLiteException {
        final AtomicInteger count = new AtomicInteger(0);
        verifyByTagName(tag, (n, result) -> count.incrementAndGet());
        assertEquals(nRows, count.intValue());
    }

    private void concurrentValidator(final int nThreads, final int waitSec, final Callback callback) {
        // setup
        final Thread[] threads = new Thread[nThreads];
        final CountDownLatch[] latches = new CountDownLatch[nThreads];

        for (int i = 0; i < nThreads; i++) {
            final int counter = i;
            latches[i] = new CountDownLatch(1);
            threads[i] = new Thread(
                () -> {
                    try {
                        callback.callback(counter);
                        latches[counter].countDown();
                    }
                    catch (AssertionError failure) {
                        Report.log(LogLevel.DEBUG, "Test failed", failure);
                        testFailure.compareAndSet(null, failure);
                    }
                },
                "Thread-" + i);
        }

        // start
        for (int i = 0; i < nThreads; i++) { threads[i].start(); }

        // wait
        for (int i = 0; i < nThreads; i++) {
            try { assertTrue(latches[i].await(waitSec, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { fail(); }
        }

        checkForFailure();
    }

    private void testOnNewThread(String threadName, CountDownLatch latch, Runnable test) {
        newTestThread(threadName, latch, test).start();
    }

    private Thread newTestThread(String threadName, CountDownLatch latch, Runnable test) {
        return new Thread(() -> {
            try { test.run(); }
            catch (AssertionError failure) {
                Report.log(LogLevel.DEBUG, "Test failed", failure);
                testFailure.compareAndSet(null, failure);
            }
            finally { latch.countDown(); }
        });
    }

    private void checkForFailure() {
        AssertionError failure = testFailure.get();
        if (failure != null) { throw new AssertionError(failure); }
    }
}
