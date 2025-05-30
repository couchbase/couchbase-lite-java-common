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
package com.couchbase.lite.internal.core;

import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.LoadTest;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;
import com.couchbase.lite.internal.utils.StopWatch;


/**
 * Ported from c4AllDocsPerformanceTest.cc
 */
public class C4AllDocsPerformanceTest extends C4BaseTest {
    private static final int DOC_SIZE = 1000;
    private static final int DOC_NUM = 1000; // 100000

    @Before
    public final void setUpC4AllDocsPerformanceTest() throws CouchbaseLiteException {
        char[] chars = new char[DOC_SIZE];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        boolean commit = false;
        try {
            c4Database.beginTransaction();
            try {
                Random random = new Random();
                for (int i = 0; i < DOC_NUM; i++) {
                    String docID = String.format(
                        "doc-%08x-%08x-%08x-%04x",
                        random.nextLong(),
                        random.nextLong(),
                        random.nextLong(),
                        i);
                    String json = String.format("{\"content\":\"%s\"}", content);
                    String[] history = new String[] {getTestRevId("bbbb", 1)};
                    // don't try to free the C4 Document
                    C4Document doc = C4TestUtils.create(
                        c4Collection,
                        json2fleece(json),
                        docID,
                        0,
                        true,
                        false,
                        history,
                        true,
                        0,
                        0);
                    Assert.assertNotNull(doc);
                }
                commit = true;
            }
            finally {
                c4Database.endTransaction(commit);
            }
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }

        Assert.assertEquals(DOC_NUM, c4Collection.getDocumentCount());
    }

    // - AllDocsPerformance
    @LoadTest
    @SlowTest
    @Test
    public void testAllDocsPerformance() throws LiteCoreException {
        StopWatch timer = new StopWatch();

        // No start or end ID:
        C4TestUtils.C4DocEnumerator e = C4TestUtils.enumerateDocsForCollection(
            c4Collection,
            C4Constants.EnumeratorFlags.DEFAULT & ~C4Constants.EnumeratorFlags.INCLUDE_BODIES);
        int i = 0;
        while (nextDocument(e) != null) { i++; }
        Assert.assertEquals(DOC_NUM, i);

        double elapsed = timer.getElapsedTimeMillis();
        Report.log("Enumerating %d docs took %.3f ms (%.3f ms/doc)", i, elapsed, elapsed / i);
    }

    private C4Document nextDocument(C4TestUtils.C4DocEnumerator e) throws LiteCoreException {
        return e.next() ? e.getDocument() : null;
    }
}
