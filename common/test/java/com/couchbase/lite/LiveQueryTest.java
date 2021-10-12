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
    private static final String KEY = "number";
    private static final long SLOP_MS = 20;

    private volatile Query globalQuery;
    private volatile CountDownLatch globalLatch;
    private volatile ListenerToken globalToken;

    // Null query is illegal
    //Need Rewritten
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentException() { }

    // Creating a document that a query can see should cause an update
    @Test
    public void testBasicLiveQuery() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch.countDown());

        createDocNumbered(10);

        try { assertTrue(latch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS)); }
        finally { query.removeChangeListener(token); }
    }

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
        assertTrue(latches[0].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

        createDocNumbered(11);
        try { assertTrue(latches[1].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    // All listeners should hear an update
    // Rewrite this test
    @Test
    public void testLiveQueryWith2Listeners() throws CouchbaseLiteException, InterruptedException {
    }

    // Changing query parameters should cause an update.
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

            assertTrue(globalLatch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
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
    // Need to rewrite
    @Test
    public void testLiveQueryRefresh() throws CouchbaseLiteException, InterruptedException {
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
