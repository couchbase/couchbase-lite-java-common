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

import static org.junit.Assert.assertEquals;


public abstract class BaseQueryTest extends BaseDbTest {
    @FunctionalInterface
    public interface ResultVerifier {
        void check(int n, Result result) throws CouchbaseLiteException;
    }

    protected static String docId(int i) { return BaseDbTestKt.jsonDocId(i); }


    protected final void createNumberedDocInBaseTestCollection(int i, int num) {
        createNumberedDocInBaseTestCollection(i, num, testCollection);
    }

    protected final void createNumberedDocInBaseTestCollection(int i, int num, Collection collection) {
        MutableDocument doc = new MutableDocument(docId(i));
        doc.setValue("number1", i);
        doc.setValue("number2", num - i);
        saveDocInCollection(doc, collection, null);
    }

    protected final void loadNumberedDocs(int num) {
        loadNumberedDocs(num, testCollection);
    }

    protected final void loadNumberedDocs(int num, Collection collection) {
        loadNumberedDocs(1, num, collection);
    }

    protected final void loadNumberedDocs(int from, int to, Collection collection) {
        try {
            testDatabase.inBatch(() -> {
                for (int i = from; i <= to; i++) { createNumberedDocInBaseTestCollection(i, to, collection); }
            });
        }
        catch (Exception e) { throw new AssertionError("Failed loading numbered docs"); }
    }

    protected final int verifyQueryWithEnumerator(Query query, ResultVerifier verifier) {
        int n = 0;
        try (ResultSet rs = query.execute()) {
            Result result;
            while ((result = rs.next()) != null) { verifier.check(++n, result); }
        }
        catch (Exception e) { throw new AssertionError("Failed verifying query (enumerator)"); }
        return n;
    }

    protected final int verifyQueryWithIterable(Query query, ResultVerifier verifier) {
        int n = 0;
        try (ResultSet rs = query.execute()) {
            for (Result result: rs) { verifier.check(++n, result); }
        }
        catch (Exception e) { throw new AssertionError("Failed verifying query (iterable)"); }
        return n;
    }

    protected final int verifyQuery(Query query, ResultVerifier verifier) {
        int n = verifyQueryWithEnumerator(query, verifier);
        assertEquals(n, verifyQueryWithIterable(query, verifier));
        return n;
    }

    // Kotlin shim functions

    protected final Document saveDocInTestCollection(MutableDocument doc) {
        return saveDocInTestCollection(doc, testCollection);
    }

    protected final Document saveDocInTestCollection(MutableDocument doc, Collection collection) {
        return saveDocInCollection(doc, collection, null);
    }

    protected final void loadJSONResourceIntoCollection(String resName) {
        loadJSONResourceIntoCollection(resName, testCollection);
    }
}
