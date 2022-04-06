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
import java.util.Map;

import static org.junit.Assert.assertEquals;


public abstract class BaseQueryTest extends BaseDbTest {
    @FunctionalInterface
    public interface QueryResult { void check(int n, Result result); }

    protected final String createNumberedDocInBaseTestDb(int i, int num) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc" + i);
        doc.setValue("number1", i);
        doc.setValue("number2", num - i);
        return saveDocInBaseTestDb(doc).getId();
    }

    protected final List<Map<String, Object>> loadNumberedDocs(final int num) throws CouchbaseLiteException {
        return loadNumberedDocs(1, num);
    }

    protected final List<Map<String, Object>> loadNumberedDocs(final int from, final int to)
        throws CouchbaseLiteException {
        final List<Map<String, Object>> numbers = new ArrayList<>();

        baseTestDb.inBatch(() -> {
            for (int i = from; i <= to; i++) {
                numbers.add(baseTestDb.getDocument(createNumberedDocInBaseTestDb(i, to)).toMap());
            }
        });

        return numbers;
    }

    protected final int verifyQuery(Query query, QueryResult result) throws CouchbaseLiteException {
        return verifyQuery(query, true, result);
    }

    protected final int verifyQuery(Query query, boolean runBoth, QueryResult result) throws CouchbaseLiteException {
        int counter1 = verifyQueryWithEnumerator(query, result);
        if (runBoth) {
            int counter2 = verifyQueryWithIterable(query, result);
            assertEquals(counter1, counter2);
        }
        return counter1;
    }

    private int verifyQueryWithEnumerator(Query query, QueryResult queryResult) throws CouchbaseLiteException {
        int n = 0;
        try (ResultSet rs = query.execute()) {
            Result result;
            while ((result = rs.next()) != null) { queryResult.check(++n, result); }
        }
        return n;
    }

    private int verifyQueryWithIterable(Query query, QueryResult queryResult) throws CouchbaseLiteException {
        int n = 0;
        try (ResultSet rs = query.execute()) {
            for (Result result: rs) { queryResult.check(++n, result); }
        }
        return n;
    }
}
