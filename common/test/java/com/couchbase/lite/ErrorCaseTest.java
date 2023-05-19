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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;


public class ErrorCaseTest extends BaseDbTest {
    static class CustomClass {
        public String text = "custom";
    }

    // -- DatabaseTest

    @Test
    public void testDeleteSameDocTwice() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = saveDocInTestCollection(new MutableDocument(docID));

        // First time deletion:
        getTestCollection().delete(doc);
        assertEquals(0, getTestCollection().getCount());
        assertNull(getTestCollection().getDocument(docID));

        // Second time deletion:
        // NOTE: doc is pointing to old revision. this cause conflict but this generate same revision
        getTestCollection().delete(doc);

        assertNull(getTestCollection().getDocument(docID));
    }

    // -- DatabaseTest
    @Test
    public void testDeleteUnsavedDocument() {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        try {
            getTestCollection().delete(doc);
            fail();
        }
        catch (CouchbaseLiteException e) {
            if (e.getCode() != CBLError.Code.NOT_FOUND) { fail(); }
        }
        assertEquals("Scott Tiger", doc.getValue("name"));
    }

    @Test
    public void testSaveSavedMutableDocument() {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        saveDocInTestCollection(doc);
        doc.setValue("age", 20);
        Document saved = saveDocInTestCollection(doc);
        assertEquals(2, saved.generation());
        assertEquals(20, saved.getInt("age"));
        assertEquals("Scott Tiger", saved.getString("name"));
    }

    @Test
    public void testDeleteSavedMutableDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        saveDocInTestCollection(doc);
        getTestCollection().delete(doc);
        assertNull(getTestCollection().getDocument("doc1"));
    }

    @Test
    public void testDeleteDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInTestCollection(doc);

        // purge doc
        getTestCollection().purge(saved);

        try {
            getTestCollection().delete(saved);
            fail();
        }
        catch (CouchbaseLiteException e) {
            if (e.getCode() != CBLError.Code.NOT_FOUND) { fail(); }
        }
    }

    @Test
    public void testDeleteDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInTestCollection(doc);

        // delete doc
        getTestCollection().delete(saved);

        // delete doc -> conflict resolver -> no-op
        getTestCollection().delete(saved);
    }

    @Test
    public void testPurgeDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInTestCollection(doc);

        // delete doc
        getTestCollection().delete(saved);

        // purge doc
        getTestCollection().purge(saved);
    }

    @Test
    public void testPurgeDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInTestCollection(doc);

        // purge doc
        getTestCollection().purge(saved);

        try {
            getTestCollection().purge(saved);
            fail();
        }
        catch (CouchbaseLiteException e) {
            if (e.getCode() != CBLError.Code.NOT_FOUND) { fail(); }
        }
    }

    // -- ArrayTest

    @Test
    public void testAddValueUnExpectedObject() {
        assertThrows(IllegalArgumentException.class, () -> new MutableArray().addValue(new CustomClass()));
    }

    @Test
    public void testSetValueUnExpectedObject() {
        MutableArray mArray = new MutableArray();
        mArray.addValue(0);
        assertThrows(IllegalArgumentException.class, () -> mArray.setValue(0, new CustomClass()));
    }

    private Document saveDocInTestCollection(MutableDocument doc) {
        return saveDocInCollection(doc, getTestCollection());
    }
}
