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

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Ignore;
import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class QueryChangeTest extends BaseQueryTest {

    @Test
    public void testQueryChangeTest() {
        QueryChange change = new QueryChange(null, null, null);
        assertNull(change.getQuery());
        assertNull(change.getResults());
        assertNull(change.getError());
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1615
    @Test
    public void testRemoveQueryChangeListenerInCallback() throws Exception {
        loadDocuments(10);

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(5)));

        final AtomicReference<ListenerToken> token = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Object lock = new Object();
        final QueryChangeListener listener = change -> {
            try (ResultSet rs = change.getResults()) {
                if ((rs == null) || (rs.next() == null)) { return; }
                synchronized (lock) {
                    ListenerToken t = token.getAndSet(null);
                    if (t != null) { t.remove(); }
                }
                latch.countDown();
            }
        };

        // Removing the listener while inside the listener itself needs be done carefully.
        // The listener might get called before query.addChangeListener(), below, returns.
        // If that happened "token" would not yet have been set and the test would not work.
        // Seizing a lock here guarantees that that can't happen.
        synchronized (lock) { token.set(query.addChangeListener(getTestSerialExecutor(), listener)); }
        try { assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)); }
        finally {
            ListenerToken t = token.get();
            if (t != null) { t.remove(); }
        }

        assertNull(token.get());
    }

    // https://issues.couchbase.com/browse/CBL-5647
    // This test is utterly non-deterministic.  Passing it doesn't prove anything.
    @Ignore("Failing: CBL-5647")
    @Test
    public void testQueryObserverRace() {
        final List<Expression> ids = Fn.mapToList(loadDocuments(10), d -> Expression.string(d.getId())).subList(3, 7);

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.in(ids));

        final ExecutorService exec = Executors.newFixedThreadPool(1000);
        final Deque<ListenerToken> tokens = new LinkedList<>();
        final CyclicBarrier barrier = new CyclicBarrier(1000);
        for (int i = 0; i < 1000; i++) {
            final boolean add = i % 2 == 0;
            exec.submit(() -> {
                try { barrier.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS); }
                catch (BrokenBarrierException | InterruptedException | TimeoutException e) {
                    throw new AssertionError(e);
                }
                if (add) {
                    ListenerToken token = query.addChangeListener(change -> { });
                    synchronized (tokens) { tokens.push(token); }
                }
                else {
                    final ListenerToken token;
                    synchronized (tokens) { token = (tokens.isEmpty()) ? null : tokens.pop(); }
                    if (token != null) { token.close(); }
                }
            });
        }

        exec.shutdown();
        try { assertTrue(exec.awaitTermination(LONG_TIMEOUT_SEC, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { throw new AssertionError(e); }
        finally {
            synchronized (tokens) {
                while (!tokens.isEmpty()) { tokens.pop().close(); }
            }
        }
    }
}
