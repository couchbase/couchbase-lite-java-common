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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;


@SuppressWarnings("ConstantConditions")
public class LiveQueryTest extends BaseDbTest {

    // Maximum delay for db change debounce from core is 500ms, happening when there's a rapid change within 250ms of
    // previous query run. Query run time and other delay factors cannot be accurately approximated. Thus, an acceptable
    // approximate for the time platform should wait if we expect to NOT get a callback is 500 * 2.
    // more details on core delay can be found in c4Observer.h
    private static final long APPROXIMATE_CORE_DELAY_MS = 500 * 2;

    private static final String KEY = "number";

    /**
     * When a query observer is first registered, the query should get notified
     */
    @Test
    public void testCreateBasicListener() throws InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        try (ListenerToken ignore = query.addChangeListener(testSerialExecutor, change -> latch.countDown())) {
            Assert.assertTrue(latch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }

    /**
     * When a second observer is registered, it should get call back after query done running
     * The first observer should NOT get notified when the second observer is created
     * When there's a db change, both observers should get notified in a tolerable amount of time
     */
    @Test
    public void testMultipleListeners() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());
        final CountDownLatch[] latch1 = new CountDownLatch[2];
        final CountDownLatch[] latch2 = new CountDownLatch[2];

        for (int i = 0; i < latch1.length; i++) { latch1[i] = new CountDownLatch(1); }
        for (int i = 0; i < latch2.length; i++) { latch2[i] = new CountDownLatch(1); }

        final AtomicIntegerArray atmCount = new AtomicIntegerArray(2);

        try (ListenerToken ignore1 = query.addChangeListener(
            testSerialExecutor,
            change -> latch1[atmCount.getAndIncrement(0)].countDown())) {

            // listener 1 gets notified after observer subscribed
            Assert.assertTrue(latch1[0].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            try (ListenerToken ignore2 = query.addChangeListener(
                testSerialExecutor,
                change -> latch2[atmCount.getAndIncrement(1)].countDown())) {
                // listener 2 should get notified
                Assert.assertTrue(latch2[0].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

                // creation of the second listener should not trigger first listener callback
                Assert.assertFalse(latch1[1].await(APPROXIMATE_CORE_DELAY_MS, TimeUnit.MILLISECONDS));

                createDocNumbered(11);

                // introducing change in database should trigger both listener callbacks
                Assert.assertTrue(latch1[1].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
                Assert.assertTrue(latch2[1].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            }
        }
    }

    // When a result set is closed, we should still be able to introduce a change
    @Test
    public void testCloseResultsInLiveQueryListener() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()));

        final AtomicIntegerArray atmCount = new AtomicIntegerArray(1);
        final CountDownLatch[] latches = new CountDownLatch[2];
        for (int i = 0; i < latches.length; i++) { latches[i] = new CountDownLatch(1); }

        try (ListenerToken ignore = query.addChangeListener(
            testSerialExecutor,
            change -> {
                change.getResults().close();
                latches[atmCount.getAndIncrement(0)].countDown();
            })) {
            createDocNumbered(10);
            Assert.assertTrue(latches[0].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            createDocNumbered(11);
            Assert.assertTrue(latches[1].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }

    /**
     * Two observers should have two independent result sets.
     * When two observers try to iterate through the result set,
     * values in that rs should not be skipped because of the other observer
     */
    @Test
    public void testIterateRSWith2Listeners() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        try (ListenerToken ignore1 = query.addChangeListener(
            testSerialExecutor,
            change -> {
                // even if the other listener finishes running first and iterates through doc-11,
                // this listener should get an independent rs, thus iterates from the beginning, getting doc-11
                try (ResultSet rs = change.getResults()) {
                    Result r = rs.next();
                    if ((r != null) && Objects.equals(r.getString(0), "doc-11")) { latch1.countDown(); }
                }
            });
             ListenerToken ignore2 = query.addChangeListener(
                 testSerialExecutor, change -> {
                     // even if the other listener finishes running first and iterates through doc-11,
                     // this listener should get an independent rs, thus iterates from the beginning, getting doc-11
                     try (ResultSet rs = change.getResults()) {
                         Result r = rs.next();
                         if ((r != null) && Objects.equals(r.getString(0), "doc-11")) { latch2.countDown(); }
                     }
                 })) {
            createDocNumbered(11);

            // both listeners get notified after doc-11 is created in database
            // rs iterates through the correct value
            Assert.assertTrue(latch1.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            Assert.assertTrue(latch2.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }


    // Changing query parameters should cause an update within tolerable time
    @Test
    public void testChangeParameters() throws CouchbaseLiteException, InterruptedException {
        createDocNumbered(1);
        createDocNumbered(2);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.parameter("VALUE")))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch[] latch = new CountDownLatch[3];
        for (int i = 0; i < latch.length; i++) { latch[i] = new CountDownLatch(1); }

        // count is used to get the next latch and also check size of rs
        final AtomicIntegerArray atmCount = new AtomicIntegerArray(1);

        // VALUE is set to 2, we should expect that query will only get notification for doc 2, rs size is 1
        Parameters params = new Parameters();
        params.setInt("VALUE", 2);
        query.setParameters(params);

        try (ListenerToken ignore = query.addChangeListener(
            testSerialExecutor,
            change -> {
                try (ResultSet rs = change.getResults()) {
                    //  query should only be notified 2 times:
                    //  1. query first gets doc 2, the rs size is 1
                    //  2. after param changes to 1, query gets a new rs that has doc 1 and 2, rs size is now 2
                    //  query should not be notified when doc 0 is added to the db
                    if (rs.allResults().size() == atmCount.get(0) + 1) {
                        latch[atmCount.getAndIncrement(0)].countDown();
                    }
                }
            })) {
            Assert.assertTrue(latch[0].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            params = new Parameters();
            params.setInt("VALUE", 1);
            query.setParameters(params);

            // VALUE changes to 1, query now gets a new rs for doc 1 and 2
            Assert.assertTrue(latch[1].await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            // This doc does not meet the condition of the query, thus query should not get notified
            createDocNumbered(0);
            Assert.assertFalse(latch[2].await(APPROXIMATE_CORE_DELAY_MS, TimeUnit.MILLISECONDS));
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
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(KEY).greaterThan(Expression.intValue(0)));

        latchHolder.set(new CountDownLatch(1));

        try (ListenerToken ignore = query.addChangeListener(
            testSerialExecutor,
            change -> {
                resultsHolder.set(change.getResults().allResults());
                latchHolder.get().countDown();
            }
        )) {
            // this update should happen nearly instantaneously
            Assert.assertTrue(latchHolder.get().await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            Assert.assertEquals(1, resultsHolder.get().size());

            // adding this document will trigger the query but since it does not meet the query
            // criteria, it will not produce a new result. The listener should not be called.
            // Wait for 2 full update intervals and a little bit more.
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(0);
            Assert.assertFalse(latchHolder.get().await((APPROXIMATE_CORE_DELAY_MS), TimeUnit.MILLISECONDS));

            // adding this document should cause a call to the listener in not much more than an update interval
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(11);
            Assert.assertTrue(latchHolder.get().await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            Assert.assertEquals(2, resultsHolder.get().size());
        }
    }

    // CBL-4423: must close all live queries when closing a database
    @Test
    public void testLiveQueryOnDBClose() throws CouchbaseLiteException {
        createDocNumbered(10);

        final N1qlQuery query
            = new N1qlQuery(getTestDatabase(), "SELECT _id FROM " + getTestCollection().getFullName());

        final List<QueryChangeListener> listeners = new ArrayList<>();
        final List<ListenerToken> tokens = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            QueryChangeListener listener = change -> { };
            listeners.add(listener);
            tokens.add(query.addChangeListener(listener));
        }

        Assert.assertEquals(listeners.size(), query.liveCount());
        for (ListenerToken token: tokens) { Assert.assertTrue(query.isLive(token)); }

        getTestDatabase().close();

        Assert.assertEquals(0, query.liveCount());
    }

    // create test docs
    // !!! Replace with standard save routine
    private void createDocNumbered(int i) throws CouchbaseLiteException {
        String docID = "doc-" + i;
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue(KEY, i);
        getTestCollection().save(doc);
    }
}
