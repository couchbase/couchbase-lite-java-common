//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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


import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class LiveQueryTest extends BaseDbTest {
    private static final long EXPECTED_DELAY_MS = 200;
    private static final long SLOP_MS = EXPECTED_DELAY_MS / 10;
    private static final long TOLERABLE_DELAY_MS = EXPECTED_DELAY_MS + SLOP_MS;

    private static final String KEY = "number";

    private volatile Query globalQuery;
    private volatile CountDownLatch globalLatch;
    private volatile ListenerToken globalToken;

    @Ignore("Test flaky with 220ms")
    @Test
    public void testCreateBasicListener() throws InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch.countDown());

        try { assertTrue(latch.await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    // Test create a second query, first query shouldn't get call back
    // Test flaky with 220ms
    // !!! This test does not test what it claims to test
    @Ignore("Test flaky with 220ms")
    @Test
    public void testCreateSecondListener() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch1 = new CountDownLatch(2);
        final CountDownLatch[] latch2 = new CountDownLatch[2];
        for (int i = 0; i < latch2.length; i++) { latch2[i] = new CountDownLatch(1);}
        final int[] count = new int[] {0};

        // latch 0 count down once when adding listener
        ListenerToken token1 = query.addChangeListener(testSerialExecutor, change -> latch1.countDown());
        ListenerToken token2 = query.addChangeListener(testSerialExecutor, change -> {
            int n = count[0]++;
            latch2[n].countDown();
        });
        try {
            assertTrue(latch2[0].await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));

            // !!! THIS IS NOT TESTING WHAT IT CLAIMS TO TEST
            // creation of token2 should not trigger first listener callback
            assertFalse(latch1.await(2 * TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));

            createDocNumbered(11);

            // introducing change in database should trigger both listener callbacks
            assertTrue(latch1.await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));
            assertTrue(latch2[1].await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));
        }
        finally {
            query.removeChangeListener(token1);
            query.removeChangeListener(token2);
        }
    }

    // Creating a document that a query can see should cause an update
    @Ignore("Test is flaky with 220ms delay")
    @Test
    public void testAddChangeToBasicLiveQuery() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch.countDown());

        createDocNumbered(10);

        try { assertTrue(latch.await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    // !!! What the ever-luvvin heck is this trying to test???
    @Ignore("Test fail with 220ms delay")
    @Test
    public void testCloseResultsInLiveQueryListener() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        final int[] count = new int[] {0};
        final CountDownLatch[] latches = new CountDownLatch[2];
        for (int i = 0; i < latches.length; i++) { latches[i] = new CountDownLatch(1); }
        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                // this will close the resultset
                try (ResultSet rs = change.getResults()) {
                    int n = count[0]++;
                    latches[n].countDown();
                }
            });

        createDocNumbered(10);
        assertTrue(latches[0].await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));

        createDocNumbered(11);
        try { assertTrue(latches[1].await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    // !!! This test does not test what it claims to test
    @Ignore("Flaky test with 220ms")
    @Test
    public void testCloseRSWith2Listeners() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                // close the result set
                try (ResultSet rs = change.getResults()) {
                    if (rs != null) { rs.close(); }
                    latch1.countDown();
                }
            });

        // !!! There is no enforcement of order, here.
        //     The close might be happening after the next
        ListenerToken token1 = query.addChangeListener(
            testSerialExecutor, change -> {
                try (ResultSet rs = change.getResults()) {
                    assertNotNull(rs.next()); // second listener can still iterate over the result
                    latch2.countDown();
                }
            });

        createDocNumbered(11);

        // both listener get notified after create doc in database.
        assertTrue(latch2.await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));
        assertTrue(latch1.await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));

        query.removeChangeListener(token);
        query.removeChangeListener(token1);
    }

    // All listeners should hear an update within tolerable time
    @Ignore("Need LiteCore specs for this test")
    @Test
    public void testLiveQueryWith2Listeners() throws CouchbaseLiteException, InterruptedException {
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(2);

        ListenerToken token1 = query.addChangeListener(testSerialExecutor, change -> latch.countDown());
        ListenerToken token2 = query.addChangeListener(testSerialExecutor, change -> latch.countDown());

        createDocNumbered(11);

        try { assertTrue(latch.await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS)); }
        finally {
            query.removeChangeListener(token1);
            query.removeChangeListener(token2);
        }
    }

    // Test call-back delay
    @Ignore("Need LiteCore specs for this test")
    @Test
    public void testLiveQueryDelay() throws CouchbaseLiteException, InterruptedException {
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        //- There should be a 200ms delay upon registration
        //- There should be only one callback
        //- Delay in registration should take the query at least 200ms to post change

        final long[] times = new long[] {1, System.currentTimeMillis(), 0};
        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                int n = (int) ++times[0];
                if (n >= times.length) { return; }
                times[n] = System.currentTimeMillis();
            });

        try {

            Thread.sleep(TOLERABLE_DELAY_MS);
            createDocNumbered(12);
            createDocNumbered(13);
            createDocNumbered(14);
            createDocNumbered(15);
            createDocNumbered(16);

            assertEquals(2, times[0]); //there should only be one callback
            assertTrue(times[2] - times[1] > EXPECTED_DELAY_MS);
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // Changing query parameters should cause an update.
    @Ignore("Need to wait for core update on the implementation of setParameters")
    @Test
    public void testChangeParameters() throws CouchbaseLiteException, InterruptedException {
        createDocNumbered(1);
        createDocNumbered(2);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.parameter("VALUE")))
            .orderBy(Ordering.property(KEY).ascending());

        globalLatch = new CountDownLatch(1);

        Parameters params = new Parameters();
        params.setInt("VALUE", 2);
        query.setParameters(params);

        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> globalLatch.countDown());
        try {
            assertTrue(globalLatch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            globalLatch = new CountDownLatch(1);

            params = new Parameters();
            params.setInt("VALUE", 1);
            query.setParameters(params);

            assertTrue(globalLatch.await(SLOP_MS, TimeUnit.MILLISECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1606
    @Test
    public void testRemovingLiveQuery() throws CouchbaseLiteException, InterruptedException {
        int n = 1;
        newQuery(n);
        try {
            // creates doc1 -> first query match
            createDocNumbered(n++);
            assertTrue(globalLatch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            // create doc2 -> update query match
            createDocNumbered(n++);
            assertTrue(globalLatch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            // create doc3 -> update query match
            createDocNumbered(n);
            assertTrue(globalLatch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            globalQuery.removeChangeListener(globalToken);
        }
    }

    // CBL-2344: Live query may stop refreshing
    @Ignore("Fails during re-implementation of LiveQuery")
    @Test
    public void testLiveQueryRefresh() throws CouchbaseLiteException, InterruptedException {
        final AtomicReference<CountDownLatch> latchHolder = new AtomicReference<>();
        final AtomicReference<List<Result>> resultsHolder = new AtomicReference<>();

        createDocNumbered(10);

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThan(Expression.intValue(0)));

        latchHolder.set(new CountDownLatch(1));
        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                resultsHolder.set(change.getResults().allResults());
                latchHolder.get().countDown();
            }
        );

        try {
            // this update should happen nearly instantaneously
            assertTrue(latchHolder.get().await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));
            assertEquals(1, resultsHolder.get().size());

            // adding this document will trigger the query but since it does not meet the query
            // criteria, it will not produce a new result. The listener should not be called.
            // Wait for 2 full update intervals and a little bit more.
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(0);
            assertFalse(latchHolder.get().await((2 * EXPECTED_DELAY_MS) + SLOP_MS, TimeUnit.MILLISECONDS));

            // adding this document should cause a call to the listener in not much more than an update interval
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(11);
            assertTrue(latchHolder.get().await(TOLERABLE_DELAY_MS, TimeUnit.MILLISECONDS));
            assertEquals(2, resultsHolder.get().size());
        }
        finally {
            query.removeChangeListener(token);
        }
    }


    // create test docs
    private void createDocNumbered(int i) throws CouchbaseLiteException {
        String docID = "doc-" + i;
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue(KEY, i);
        saveDocInBaseTestDb(doc);
    }

    private void nextQuery(int n, QueryChange change) {
        List<Result> results = change.getResults().allResults();
        if (results.size() <= 0) { return; }

        globalQuery.removeChangeListener(globalToken);

        CountDownLatch latch = globalLatch;

        if (n < 3) { newQuery(n); }

        latch.countDown();
    }

    private void newQuery(int n) {
        globalQuery = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(n)))
            .orderBy(Ordering.property(KEY).ascending());

        globalLatch = new CountDownLatch(1);

        globalToken = globalQuery.addChangeListener(testSerialExecutor, ch -> nextQuery(n + 1, ch));
    }
}
