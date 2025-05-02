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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;


@SuppressWarnings("ConstantConditions")
public class QueryChangeTest extends BaseQueryTest {

    @Test
    public void testQueryChangeTest() {
        QueryChange change = new QueryChange(null, null, null);
        Assert.assertNull(change.getQuery());
        Assert.assertNull(change.getResults());
        Assert.assertNull(change.getError());
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
            ResultSet rs = change.getResults();
            if ((rs == null) || (rs.next() == null)) { return; }
            synchronized (lock) {
                ListenerToken t = token.getAndSet(null);
                if (t != null) { t.remove(); }
            }
            latch.countDown();
        };

        // Removing the listener while inside the listener itself needs be done carefully.
        // The listener might get called before query.addChangeListener(), below, returns.
        // If that happened "token" would not yet have been set and the test would not work.
        // Seizing a lock here guarantees that that can't happen.
        synchronized (lock) { token.set(query.addChangeListener(testSerialExecutor, listener)); }
        try { Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)); }
        finally {
            ListenerToken t = token.get();
            if (t != null) { t.remove(); }
        }

        Assert.assertNull(token.get());
    }
}
