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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class ConcurrencyTest extends BaseDbTest {

    @Test
    public void testConcurrentCreates() throws InterruptedException, CouchbaseLiteException {
        final int nDocs = 50;

        final int copies = 4;
        runConcurrentCopies(
            copies,
            id -> {
                try {
                    for (MutableDocument mDoc: createDocs(nDocs, "TAG@CREATES-" + id)) { testCollection.save(mDoc); }
                }
                catch (CouchbaseLiteException e) {
                    throw new AssertionError("Failed saving doc", e);
                }
            });

        // validate stored documents
        for (int i = 0; i < copies; i++) { assertEquals(nDocs, countTaggedDocs("TAG@CREATES-" + i)); }
    }

    @Test
    public void testConcurrentCreatesInBatch() throws CouchbaseLiteException, InterruptedException {
        final int nDocs = 50;

        final int copies = 4;
        runConcurrentCopies(
            copies,
            id -> {
                try {
                    for (MutableDocument mDoc: createDocs(nDocs, "TAG@CREATESBATCH-" + id)) {
                        testDatabase.inBatch(() -> testCollection.save(mDoc));
                    }
                }
                catch (CouchbaseLiteException e) {
                    throw new AssertionError("Failed saving doc in batch", e);
                }
            });

        // validate stored documents
        for (int i = 0; i < copies; i++) { assertEquals(nDocs, countTaggedDocs("TAG@CREATESBATCH-" + i)); }
    }

    @Test
    public void testConcurrentReads() throws InterruptedException {
        final List<String> docIDs = saveDocs(createDocs(5, "TAG@READS"));
        runConcurrentCopies(4, id -> readDocs(docIDs, 50));
    }

    @Test
    public void testConcurrentReadsInBatch() throws InterruptedException {
        final List<String> docIDs = saveDocs(createDocs(5, "TAG@READSBATCH"));

        runConcurrentCopies(
            4,
            id -> {
                try { testDatabase.inBatch(() -> readDocs(docIDs, 50)); }
                catch (CouchbaseLiteException e) { throw new AssertionError("Failed reading docs in batch", e); }
            });
    }

    // ??? Increasing the number of threads in this test causes crashes
    @SlowTest
    @Test
    public void testConcurrentUpdates() throws CouchbaseLiteException, InterruptedException {
        final List<String> docIDs = saveDocs(createDocs(5, "TAG@UPDATES"));

        final int copies = 4;
        runConcurrentCopies(copies, threadId -> updateDocs(docIDs, 50, "TAG@UPDATED-" + threadId));

        int count = 0;
        for (int i = 0; i < copies; i++) { count += countTaggedDocs("TAG@UPDATED-" + i); }

        assertEquals(docIDs.size(), count);
    }

    @Test
    public void testConcurrentDeletes() {
        final List<String> docIDs = saveDocs(createDocs(100, "TAG@DELETES"));

        runConcurrently(
            "delete",
            () -> {
                for (String docID: docIDs) {
                    try {
                        Document doc = testCollection.getDocument(docID);
                        if (doc != null) { testCollection.delete(doc); }
                    }
                    catch (CouchbaseLiteException e) { throw new AssertionError("Failed deleting doc: " + docID, e); }
                }
            },
            () -> {
                for (String docID: docIDs) {
                    try {
                        Document doc = testCollection.getDocument(docID);
                        if (doc != null) { testCollection.delete(doc); }
                    }
                    catch (CouchbaseLiteException e) { throw new AssertionError("Failed deleting doc: " + docID, e); }
                }
            });

        assertEquals(0, testCollection.getCount());
    }

    @Test
    public void testConcurrentPurges() {
        final List<String> docIDs = saveDocs(createDocs(100, "TAG@PURGES"));

        runConcurrently(
            "purge",
            () -> {
                for (String docID: docIDs) {
                    try {
                        Document doc = testCollection.getDocument(docID);
                        if (doc != null) { testCollection.purge(doc); }
                    }
                    catch (CouchbaseLiteException e) {
                        if (e.getCode() != 404) { throw new AssertionError("Failed purging doc: " + docID, e); }
                    }
                }
            },
            () -> {
                for (String docID: docIDs) {
                    try {
                        Document doc = testCollection.getDocument(docID);
                        if (doc != null) { testCollection.purge(doc); }
                    }
                    catch (CouchbaseLiteException e) {
                        if (e.getCode() != 404) { throw new AssertionError("Failed purging doc: " + docID, e); }
                    }
                }
            });

        assertEquals(0, testCollection.getCount());
    }

    @Test
    public void testConcurrentReadWhileUpdate() throws CouchbaseLiteException {
        final List<String> docIDs = saveDocs(createDocs(5, "TAG@READ&UPDATE"));
        runConcurrently(
            "readWhileUpdate",
            () -> readDocs(docIDs, 50),
            () -> updateDocs(docIDs, 50, "TAG@READ&UPDATED"));

        assertEquals(docIDs.size(), countTaggedDocs("TAG@READ&UPDATED"));
    }

    @Test
    public void testConcurrentCreateWhileCloseDB() {
        final List<MutableDocument> docs = createDocs(100, "TAG@CLOSEDB");
        runConcurrently(
            "createWhileCloseD",
            () -> {
                delay(); // wait for other task to get busy...
                closeDb(testDatabase);
            },
            () -> {
                for (MutableDocument mDoc: docs) {
                    try { testCollection.save(mDoc); }
                    catch (CouchbaseLiteException e) {
                        if (e.getDomain().equals(CBLError.Domain.CBLITE) && (e.getCode() == CBLError.Code.NOT_OPEN)) {
                            break;
                        }
                        throw new AssertionError("Failed saving document: " + mDoc, e);
                    }
                }
            });
    }

    @Test
    public void testConcurrentCreateWhileDeleteDB() {
        final List<MutableDocument> docs = createDocs(100, "TAG@DELETEDB");

        runConcurrently(
            "createWhileDeleteDb",
            () -> {
                delay(); // wait for other task to get busy...
                deleteDb(testDatabase);
            },
            () -> {
                for (MutableDocument mDoc: docs) {
                    try { testCollection.save(mDoc); }
                    catch (CouchbaseLiteException e) {
                        if (e.getDomain().equals(CBLError.Domain.CBLITE) && (e.getCode() == CBLError.Code.NOT_OPEN)) {
                            break;
                        }
                        throw new AssertionError("Failed saving document: " + mDoc, e);
                    }
                }
            });
    }

    @Test
    public void testConcurrentCreateWhileCompactDB() {
        final List<MutableDocument> docs = createDocs(100, "TAG@COMPACTDB");

        runConcurrently(
            "createAndCompactDb@1",
            () -> {
                try {
                    delay(); // wait for other task to get busy...
                    if (!testDatabase.performMaintenance(MaintenanceType.COMPACT)) {
                        throw new CouchbaseLiteException("Compaction failed");
                    }
                }
                catch (CouchbaseLiteException e) { throw new AssertionError("Failed compacting database", e); }
            },
            () -> {
                for (MutableDocument doc: docs) {
                    try { testCollection.save(doc); }
                    catch (CouchbaseLiteException e) {
                        if (e.getDomain().equals(CBLError.Domain.CBLITE) && (e.getCode() == CBLError.Code.NOT_OPEN)) {
                            break;
                        }
                        throw new AssertionError("Failed saving document: " + doc, e);
                    }
                }
            });
    }

    @Test
    public void testConcurrentCreateWhileIndexDB() {
        loadJSONResourceIntoTestCollection("sentences.json");

        final List<MutableDocument> docs = createDocs(100, "TAG@INDEX");

        runConcurrently(
            "CreateWhileIndex",
            () -> {
                try {
                    testCollection.createIndex(
                        "sentence",
                        IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));
                }
                catch (CouchbaseLiteException e) { throw new AssertionError("Failed creating index", e); }
            },
            () -> saveDocs(docs));
    }

    @Test
    public void testBlockDatabaseChange() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        ListenerToken token = testCollection.addChangeListener(testSerialExecutor, change -> latch.countDown());
        try {
            testSerialExecutor.execute(() -> {
                try { testCollection.save(new MutableDocument()); }
                catch (Exception e) { error.compareAndSet(null, e); }
            });

            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally { token.remove(); }

        Exception e = error.get();
        if (e != null) { throw new AssertionError("Error saving document", e); }
    }

    @Test
    public void testBlockDocumentChange() throws InterruptedException {
        final MutableDocument mDoc = new MutableDocument();

        final CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        ListenerToken token = testCollection.addDocumentChangeListener(mDoc.getId(), change -> latch.countDown());
        try {
            testSerialExecutor.execute(() -> {
                try { testCollection.save(mDoc); }
                catch (Exception e) { error.compareAndSet(null, e); }
            });

            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally { token.remove(); }

        Exception e = error.get();
        if (e != null) { throw new AssertionError("Error saving document", e); }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1407
    @Test
    public void testQueryExecute() throws InterruptedException {
        loadJSONResourceIntoTestCollection("names_100.json");

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.sequence))
            .from(DataSource.collection(testCollection));

        List<Integer> nResults = Collections.synchronizedList(new ArrayList<>());
        runConcurrentCopies(
            10,
            id -> {
                try (ResultSet rs = query.execute()) { nResults.add(rs.allResults().size()); }
                catch (CouchbaseLiteException e) { throw new AssertionError("Failed executing query", e); }
            }
        );

        assertEquals(10, nResults.size());
        for (Integer n: nResults) { assertEquals(testCollection.getCount(), (long) n); }
    }

    private List<MutableDocument> createDocs(int nDocs, String tag) {
        List<MutableDocument> mDocs = createTestDocs(1000, nDocs);
        for (MutableDocument mDoc: mDocs) {
            mDoc.setValue("tag", tag);

            // Dictionary:
            MutableDictionary address = new MutableDictionary();
            address.setValue("street", "1 Main street");
            address.setValue("city", "Mountain View");
            address.setValue("state", "CA");
            mDoc.setValue("address", address);

            // Array:
            MutableArray phones = new MutableArray();
            phones.addValue("650-123-0001");
            phones.addValue("650-123-0002");
            mDoc.setValue("phones", phones);
        }

        return mDocs;
    }

    // Do not remove the extraneous try-catch here!
    // The Windows Java 11 compiler appears to infer that mapToList
    // throws an Exception and gives a compiler error
    private List<String> saveDocs(List<MutableDocument> mDocs) {
        try { return Collections.synchronizedList(Fn.mapToList(saveDocsInTestCollection(mDocs), Document::getId)); }
        catch (Exception e) { throw new AssertionError("Failed saving documents", e); }
    }

    private void updateDocs(List<String> docIds, int rounds, String tag) {
        for (int i = 1; i <= rounds; i++) {
            for (String docId: docIds) {
                MutableDocument mDoc;
                try { mDoc = testCollection.getDocument(docId).toMutable(); }
                catch (CouchbaseLiteException e) {
                    throw new AssertionError("Failed getting document: " + docId, e);
                }

                mDoc.setValue("tag", tag);

                MutableDictionary address = mDoc.getDictionary("address");
                assertNotNull(address);
                String street = String.format(Locale.ENGLISH, "%d street.", i);
                address.setValue("street", street);

                MutableArray phones = mDoc.getArray("phones");
                assertNotNull(phones);
                assertEquals(2, phones.count());
                String phone = String.format(Locale.ENGLISH, "650-000-%04d", i);
                phones.setValue(0, phone);

                mDoc.setValue("updated", new Date());
                try { testCollection.save(mDoc); }
                catch (CouchbaseLiteException e) {
                    throw new AssertionError("Failed saving document: " + docId, e);
                }
            }
        }
    }

    private void readDocs(List<String> docIDs, int rounds) {
        for (int i = 1; i <= rounds; i++) {
            for (String docID: docIDs) {
                Document doc;
                try { doc = testCollection.getDocument(docID); }
                catch (CouchbaseLiteException e) { throw new AssertionError("Failed reading document: " + docID, e); }
                assertNotNull(doc);
                assertEquals(docID, doc.getId());
            }
        }
    }

    private int countTaggedDocs(String tag) throws CouchbaseLiteException {
        final Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .where(Expression.property("tag").equalTo(Expression.string(tag)));
        try (ResultSet rs = query.execute()) { return rs.allResults().size(); }
    }

    private void runConcurrently(String name, Runnable task1, Runnable task2) {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        createTestThreads(name + "@1", 1, barrier, latch, error, id -> task1.run());
        createTestThreads(name + "@2", 1, barrier, latch, error, id -> task2.run());

        boolean ok = false;
        try { ok = latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS); }
        catch (InterruptedException ignore) { }

        checkForFailure(error);

        assertTrue(ok);
    }

    private void runConcurrentCopies(final int nThreads, final Fn.Consumer<Integer> task)
        throws InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(nThreads);
        final CountDownLatch latch = new CountDownLatch(nThreads);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        createTestThreads("Concurrency-test", nThreads, barrier, latch, error, task);

        // wait
        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

        checkForFailure(error);
    }

    private void createTestThreads(
        String name,
        int nThreads,
        CyclicBarrier barrier,
        CountDownLatch latch,
        AtomicReference<Throwable> error,
        Fn.Consumer<Integer> task) {

        for (int i = 0; i < nThreads; i++) {
            final int id = i;
            Thread worker = new Thread(
                () -> {
                    try {
                        barrier.await();
                        task.accept(id);
                    }
                    catch (BrokenBarrierException | InterruptedException e) {
                        throw new AssertionError("Unexpected error in test", e);
                    }
                    finally {
                        latch.countDown();
                    }
                },
                name + "-" + id);

            worker.setUncaughtExceptionHandler((t, e) -> {
                Report.log(e, "Unexpected exception in test %s on thread %s", t.getName());
                error.compareAndSet(null, e);
            });

            worker.start();
        }
    }

    private void checkForFailure(AtomicReference<Throwable> error) {
        Throwable err = error.get();
        if (err instanceof AssertionError) { throw (AssertionError) err; }
        if (err != null) { throw new AssertionError("Exception thrown in test", err); }
    }

    private void delay() {
        try { Thread.sleep(2); }
        catch (InterruptedException ignore) { }
    }

    // Kotlin shim functions

    private List<Document> saveDocsInTestCollection(List<MutableDocument> mDocs) {
        return saveDocsInTestCollection(mDocs, testCollection);
    }

    private List<Document> saveDocsInTestCollection(List<MutableDocument> mDocs, Collection collection) {
        return saveDocsInCollection(mDocs, collection, null);
    }

    private void loadJSONResourceIntoTestCollection(String resName) {
        loadJSONResourceIntoCollection(resName, "doc-%03d", testCollection);
    }
}
