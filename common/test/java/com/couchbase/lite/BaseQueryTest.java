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

import androidx.annotation.NonNull;

import java.util.List;

import org.junit.Assert;


public abstract class BaseQueryTest extends BaseDbTest {
    @FunctionalInterface
    public interface ResultVerifier {
        void check(int n, Result result) throws CouchbaseLiteException;
    }

    protected final List<MutableDocument> loadDocuments(int n) {
        return loadDocuments(n, getTestCollection());
    }

    protected final List<MutableDocument> loadDocuments(int first, int n) {
        return loadDocuments(first, n, getTestCollection());
    }

    protected final List<MutableDocument> loadDocuments(int n, Collection collection) {
        return loadDocuments(1, n, collection);
    }

    protected final List<MutableDocument> loadDocuments(int first, int n, Collection collection) {
        final List<MutableDocument> docs = createTestDocs(first, n);
        saveDocsInCollection(docs, collection);
        return docs;
    }

    protected final void verifyQuery(@NonNull Query query, int expected, @NonNull ResultVerifier verifier) {
        Assert.assertEquals(expected, verifyQueryWithEnumerator(query, verifier));
        Assert.assertEquals(expected, verifyQueryWithIterable(query, verifier));
    }

    protected final int verifyQueryWithEnumerator(@NonNull Query query, @NonNull ResultVerifier verifier) {
        int n = 0;
        try (ResultSet rs = query.execute()) {
            Result result;
            while ((result = rs.next()) != null) { verifier.check(++n, result); }
        }
        catch (Exception e) { throw new AssertionError("Failed verifying query (enumerator)", e); }
        return n;
    }

    protected final int verifyQueryWithIterable(@NonNull Query query, @NonNull ResultVerifier verifier) {
        int n = 0;
        try (ResultSet rs = query.execute()) {
            for (Result result: rs) { verifier.check(++n, result); }
        }
        catch (Exception e) { throw new AssertionError("Failed verifying query (iterable)", e); }
        return n;
    }

    // Kotlin shim functions

    protected final Document saveDocInTestCollection(MutableDocument doc) {
        return saveDocumentInCollection(doc, getTestCollection());
    }

    protected final Document saveDocumentInCollection(MutableDocument doc, Collection collection) {
        return saveDocInCollection(doc, collection);
    }
}
