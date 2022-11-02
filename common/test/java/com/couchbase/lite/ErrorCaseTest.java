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
        Document doc = saveDocInCollection(new MutableDocument(docID));

        // First time deletion:
        testCollection.delete(doc);
        assertEquals(0, testCollection.getCount());
        assertNull(testCollection.getDocument(docID));

        // Second time deletion:
        // NOTE: doc is pointing to old revision. this cause conflict but this generate same revision
        testCollection.delete(doc);

        assertNull(testCollection.getDocument(docID));
    }

    // -- DatabaseTest
    @Test
    public void testDeleteUnsavedDocument() {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        try {
            testCollection.delete(doc);
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
        saveDocInCollection(doc);
        doc.setValue("age", 20);
        Document saved = saveDocInCollection(doc);
        assertEquals(2, saved.generation());
        assertEquals(20, saved.getInt("age"));
        assertEquals("Scott Tiger", saved.getString("name"));
    }

    @Test
    public void testDeleteSavedMutableDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        saveDocInCollection(doc);
        testCollection.delete(doc);
        assertNull(testCollection.getDocument("doc1"));
    }

    @Test
    public void testDeleteDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInCollection(doc);

        // purge doc
        testCollection.purge(saved);

        try {
            testCollection.delete(saved);
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
        Document saved = saveDocInCollection(doc);

        // delete doc
        testCollection.delete(saved);

        // delete doc -> conflict resolver -> no-op
        testCollection.delete(saved);
    }

    @Test
    public void testPurgeDocAfterDeleteDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInCollection(doc);

        // delete doc
        testCollection.delete(saved);

        // purge doc
        testCollection.purge(saved);
    }

    @Test
    public void testPurgeDocAfterPurgeDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Scott Tiger");
        Document saved = saveDocInCollection(doc);

        // purge doc
        testCollection.purge(saved);

        try {
            testCollection.purge(saved);
            fail();
        }
        catch (CouchbaseLiteException e) {
            if (e.getCode() != CBLError.Code.NOT_FOUND) { fail(); }
        }
    }

    // -- ArrayTest

    @Test(expected = IllegalArgumentException.class)
    public void testAddValueUnExpectedObject() {
        new MutableArray().addValue(new CustomClass());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetValueUnExpectedObject() {
        MutableArray mArray = new MutableArray();
        mArray.addValue(0);
        mArray.setValue(0, new CustomClass());
    }

    private Document saveDocInCollection(MutableDocument doc) {
        return saveDocInCollection(doc, testCollection, null);
    }
}
