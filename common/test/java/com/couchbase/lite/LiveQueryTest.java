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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class LiveQueryTest extends BaseDbTest {
    private static final long QUERY_RUN_TIME = 20;
    private static final long SLOP_MS = 3;
    private static final long TOLERABLE_SHORT_DELAY = QUERY_RUN_TIME * 2;
    private static final long TOLERABLE_LONG_DELAY = 600; //delay whenever there's a db change, maximum 500ms

    private static final String KEY = "number";
    private volatile Query globalQuery;
    private volatile CountDownLatch globalLatch;
    private volatile ListenerToken globalToken;

    /*
      When a query an observer is first registered,
      the query should get notified after the time it takes for a query to run * 2
     */

    @Test
    public void testCreateBasicListener() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch.countDown());
        try { assertTrue(latch.await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    /*
    - When a second observer is registered, it should get call back after query done running
    - The first observer should NOT get notified when the second observer is created
    - When a there's a db change, both observers should get notified
     */

    @Test
    public void testCreateSecondListener() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch[] latch1 = new CountDownLatch[2];
        final CountDownLatch[] latch2 = new CountDownLatch[2];

        for (int i = 0; i < latch1.length; i++) { latch1[i] = new CountDownLatch(1);}
        for (int i = 0; i < latch2.length; i++) { latch2[i] = new CountDownLatch(1);}
        final int[] count = new int[] {0, 0};

        ListenerToken token1 = query.addChangeListener(testSerialExecutor, change -> {
            int n = count[0]++;
            latch1[n].countDown();
        });
        // listener 1 gets notified after observer subscribed
        assertTrue(latch1[0].await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS));

        ListenerToken token2 = query.addChangeListener(testSerialExecutor, change -> {
            int n = count[1]++;
            latch2[n].countDown();
        });

        try {
            assertTrue(latch2[0].await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS));

            // creation of the second listener should not trigger first listener callback
            assertFalse(latch1[1].await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS));

            createDocNumbered(11);

            // introducing change in database should trigger both listener callbacks
            assertTrue(latch1[1].await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS));
            assertTrue(latch2[1].await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS));
        }
        finally {
            query.removeChangeListener(token1);
            query.removeChangeListener(token2);
        }
    }

    /*
        Creating a document that a query can see should cause an update.
     */

    @Test
    public void testAddChangeToBasicLiveQuery() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                // There could be race condition here that can causes the change to notify 2 times.
                // The first notification before the created doc and second time after the created doc.
                // To avoid this race condition,
                // only count down when the expected number of doc is returned

                ResultSet rs = change.getResults();
                if (rs.allResults().size() == 1) {
                    latch.countDown();
                }
            });

        createDocNumbered(10);

        try { assertTrue(latch.await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS)); }
        finally {
            query.removeChangeListener(token);
        }
    }

    /*
    When a result set is close, we should still be able to introduce a change
     */
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
                try (ResultSet rs = change.getResults()) {
                    rs.close();
                    int n = count[0]++;
                    latches[n].countDown();
                }
            });

        createDocNumbered(10);
        assertTrue(latches[0].await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS));

        createDocNumbered(11);
        try { assertTrue(latches[1].await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    /*
       Two observers should have two independent result sets.
       When two observers try to iterate through the result set,
       values in that rs should not be skipped because of the other observer
     */
    @Test
    public void testIterateRSWith2Listeners() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                try (ResultSet rs = change.getResults()) {
                    Result r = rs.next();
                    if (r != null) {
                        assertEquals("doc-11", r.getString(0));
                        latch1.countDown();
                    }
                }
            });
        ListenerToken token1 = query.addChangeListener(
            testSerialExecutor, change -> {
                try (ResultSet rs = change.getResults()) {
                    Result r = rs.next();
                    if (r != null) {
                        assertEquals("doc-11", r.getString(0));
                        latch2.countDown();
                    }
                }
            });

        createDocNumbered(11);

        // both listener get notified after create doc in database.
        assertTrue(latch1.await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS));

        query.removeChangeListener(token);
        query.removeChangeListener(token1);
    }

    // All listeners should hear an update within tolerable time
    @Test
    public void testLiveQueryWith2Listeners() throws CouchbaseLiteException, InterruptedException {
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(2);

        // There could be race condition here that can causes the change to notify 2 times.
        // The first notification before the created doc and second time after the created doc.
        // To avoid this race condition,
        // only count down when the expected number of doc is returned
        ListenerToken token1 = query.addChangeListener(testSerialExecutor, change -> {
            if (change.getResults().allResults().size() == 1) {
                latch.countDown();
            }
        });
        ListenerToken token2 = query.addChangeListener(testSerialExecutor, change -> {
            if (change.getResults().allResults().size() == 1) {
                latch.countDown();
            }
        });

        createDocNumbered(11);

        try { assertTrue(latch.await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS)); }
        finally {
            query.removeChangeListener(token1);
            query.removeChangeListener(token2);
        }
    }

    // Changing query parameters should cause an update within tolerable time
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
            assertTrue(globalLatch.await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS));

            globalLatch = new CountDownLatch(1);

            params = new Parameters();
            params.setInt("VALUE", 1);
            query.setParameters(params);

            assertTrue(globalLatch.await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS));
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
            assertTrue(latchHolder.get().await(TOLERABLE_SHORT_DELAY, TimeUnit.MILLISECONDS));
            assertEquals(1, resultsHolder.get().size());

            // adding this document will trigger the query but since it does not meet the query
            // criteria, it will not produce a new result. The listener should not be called.
            // Wait for 2 full update intervals and a little bit more.
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(0);
            assertFalse(latchHolder.get().await((2 * TOLERABLE_LONG_DELAY) + SLOP_MS, TimeUnit.MILLISECONDS));

            // adding this document should cause a call to the listener in not much more than an update interval
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(11);
            assertTrue(latchHolder.get().await(TOLERABLE_LONG_DELAY, TimeUnit.MILLISECONDS));
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
