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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


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
        loadNumberedDocs(10);

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("number1").lessThan(Expression.intValue(5)));

        final ListenerToken[] token = new ListenerToken[1];
        final Object lock = new Object();

        // Removing the listener while inside the listener itself needs be done carefully.
        // The change handler might get called from the executor thread before query.addChangeListener() returns.
        final CountDownLatch latch = new CountDownLatch(1);
        final QueryChangeListener listener = change -> {
            try (ResultSet rs = change.getResults()) {
                if ((rs != null) && (rs.next() != null)) {
                    synchronized (lock) {
                        query.removeChangeListener(token[0]);
                        token[0] = null;
                    }
                }
            }
            latch.countDown();
        };

        synchronized (lock) { token[0] = query.addChangeListener(testSerialExecutor, listener); }

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

        synchronized (lock) { assertNull(token[0]); }
    }
}
