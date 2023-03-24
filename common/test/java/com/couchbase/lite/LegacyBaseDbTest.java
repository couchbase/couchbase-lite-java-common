

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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("deprecation")
public abstract class LegacyBaseDbTest extends BaseTest {
    public static final String BLOB_CONTENT = "Knox on fox in socks in box. Socks on Knox and Knox in box.";

    @SafeVarargs
    protected static <T extends Comparable<T>> void assertContents(List<T> l1, T... contents) {
        List<T> l2 = Arrays.asList(contents);
        assertTrue(l1.containsAll(l2) && l2.containsAll(l1));
    }

    // Attempt to standardize doc ids.
    public static String docId(int i) { return "doc" + i; }


    protected Database baseTestDb;

    @Before
    public final void setUpBaseDbTest() {
        baseTestDb = createDb("base_db");
        Report.log("Created base test DB: " + baseTestDb);
        assertNotNull(baseTestDb);
        synchronized (baseTestDb.getDbLock()) { assertTrue(baseTestDb.isOpenLocked()); }
    }

    @After
    public final void tearDownBaseDbTest() {
        eraseDb(baseTestDb);
        Report.log("Deleted baseTestDb: " + baseTestDb);
    }

    protected final Document createSingleDocInBaseTestDb(String docID) throws CouchbaseLiteException {
        final long n = baseTestDb.getCount();

        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        Document savedDoc = saveDocInBaseTestDb(doc);

        assertEquals(n + 1, baseTestDb.getCount());
        assertEquals(1, savedDoc.getSequence());

        return savedDoc;
    }

    protected final void createDocsInDb(int first, int count, Database db) throws CouchbaseLiteException {
        db.inBatch(() -> {
            for (int i = first; i < first + count; i++) {
                final MutableDocument doc = new MutableDocument(docId(i));
                doc.setNumber("count", i);
                doc.setString("inverse", "minus-" + i);
                db.save(doc);
            }
        });
    }

    protected final Document saveDocInBaseTestDb(MutableDocument doc) throws CouchbaseLiteException {
        baseTestDb.save(doc);

        Document savedDoc = baseTestDb.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());

        return savedDoc;
    }
}
