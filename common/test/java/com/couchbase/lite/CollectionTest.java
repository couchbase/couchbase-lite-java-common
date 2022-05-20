//
// Copyright (c) 2022 Couchbase, Inc.
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

import com.couchbase.lite.internal.utils.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class CollectionTest extends BaseCollectionTest {

    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    public void testGetNonExistingDocWithID() { assertNull(testCollection.getDocument("non-exist")); }

    // get doc in the collection
    @Test
    public void testGetExistingDocInCollection() throws CouchbaseLiteException {
        final String docId = "doc1";
        createSingleDocInCollectionWithId(docId);
        verifyGetDocumentInCollection(docId);
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------

    @Test
    public void saveNewDocInCollectionWithIDTest() throws CouchbaseLiteException {
        saveNewDocInCollectionWithIDTest("doc1");
    }

    @Test
    public void testSaveNewDocInCollectionWithSpecialCharactersDocID() throws CouchbaseLiteException {
        saveNewDocInCollectionWithIDTest("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'");
    }

    @Test
    public void testSaveAndGetMultipleDocsInCollection() throws CouchbaseLiteException {
        final int nDocs = 10; //1000;
        for (int i = 0; i < nDocs; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.US, "doc_%03d", i));
            doc.setValue("key", i);
            saveDocInBaseCollectionTest(doc);
        }
        assertEquals(nDocs, testCollection.getCount());
        verifyDocuments(nDocs);
    }

    @Test
    public void testSaveDocAndUpdateInCollection() throws CouchbaseLiteException {
        // store doc
        String docID = "doc1";
        MutableDocument doc = createSingleDocInCollectionWithId(docID).toMutable();

        // update doc
        doc.setValue("key", 2);
        saveDocInBaseCollectionTest(doc);

        assertEquals(1, testCollection.getCount());

        // validate document by getDocument
        verifyGetDocumentInCollection(docID, 2);
    }

    @Test
    public void testSaveSameDocTwice() throws CouchbaseLiteException {
        String docID = "doc1";
        MutableDocument doc = createSingleDocInCollectionWithId(docID).toMutable();
        assertEquals(docID, saveDocInBaseCollectionTest(doc).getId());
        assertEquals(1, testCollection.getCount());
    }

    @Test
    public void testSaveAndUpdateMutableDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        testCollection.save(doc);

        // Update:
        doc.setString("lastName", "Tiger");
        testCollection.save(doc);

        // Update:
        doc.setLong("age", 20L); // Int vs Long assertEquals can not ignore diff.
        testCollection.save(doc);
        assertEquals(3, doc.getSequence());

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Daniel");
        expected.put("lastName", "Tiger");
        expected.put("age", 20L);
        assertEquals(expected, doc.toMap());

        Document savedDoc = testCollection.getDocument(doc.getId());
        assertEquals(expected, savedDoc.toMap());
        assertEquals(3, savedDoc.getSequence());
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    public void testDeletePreSaveDoc() {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("key", 1);
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND, () -> testCollection.delete(doc));
    }

    @Test
    public void testDeleteDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = createSingleDocInCollectionWithId(docID);
        assertEquals(1, testCollection.getCount());
        testCollection.delete(doc);
        assertEquals(0, testCollection.getCount());
        assertNull(testCollection.getDocument(docID));
    }

    @Test
    public void testDeleteMultipleDocs() throws CouchbaseLiteException {
        final int nDocs = 10;

        // Save 10 docs:
        createDocsInBaseCollectionTest(nDocs);
        for (int i = 0; i < nDocs; i++) {
            String docID = String.format(Locale.US, "doc_%03d", i);
            Document doc = testCollection.getDocument(docID);
            testCollection.delete(doc);
            assertNull(testCollection.getDocument(docID));
            assertEquals((9 - i), testCollection.getCount());
        }
        assertEquals(0, testCollection.getCount());
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    public void testPurgePreSaveDoc() {
        MutableDocument doc = new MutableDocument("doc1");
        assertEquals(0, testCollection.getCount());
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND, () -> testCollection.purge(doc));
        assertEquals(0, testCollection.getCount());
    }

    @Test
    public void testPurgeDoc() throws CouchbaseLiteException {
        String docID = "doc1";
        Document doc = createSingleDocInCollectionWithId(docID);

        // Purge Doc
        purgeDocInCollectionAndVerify(doc);
        assertEquals(0, testCollection.getCount());
    }

    @Test
    public void testPurgeSameDocTwice() throws CouchbaseLiteException {
        // Store doc:
        String docID = "doc1";
        Document doc = createSingleDocInCollectionWithId(docID);

        // Get the document for the second purge:
        Document doc1 = testCollection.getDocument(docID);

        // Purge the document first time:
        purgeDocInCollectionAndVerify(doc);
        assertEquals(0, testCollection.getCount());

        // Purge the document second time:
        purgeDocInCollectionAndVerify(doc1);
        assertEquals(0, testCollection.getCount());
    }

    //---------------------------------------------
    //  Index functionalities
    //---------------------------------------------
    @Test
    public void testCreateIndexInCollection() throws CouchbaseLiteException {
        assertEquals(0, testCollection.getIndexes().size());

        testCollection.createIndex("index1", new ValueIndexConfiguration("firstName", "lastName"));
        assertEquals(1, testCollection.getIndexes().size());

        testCollection.createIndex(
            "index2",
            new FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es"));
        assertEquals(2, testCollection.getIndexes().size());

        assertTrue(testCollection.getIndexes().contains("index1"));
        assertTrue(testCollection.getIndexes().contains("index2"));
    }

    @Test
    public void testDeleteIndex() throws CouchbaseLiteException {
        testCreateIndexInCollection();

        // Delete indexes:

        testCollection.deleteIndex("index2");
        assertEquals(1, testCollection.getIndexes().size());
        assertTrue(testCollection.getIndexes().contains("index1"));

        testCollection.deleteIndex("index1");
        assertEquals(0, testCollection.getIndexes().size());
        assertTrue(testCollection.getIndexes().isEmpty());

        // Delete non existing index:
        testCollection.deleteIndex("dummy");

        // Delete deleted index:
        testCollection.deleteIndex("index1");
    }

    private void saveNewDocInCollectionWithIDTest(String docID) throws CouchbaseLiteException {
        // store doc
        createSingleDocInCollectionWithId(docID);
        assertEquals(1, testCollection.getCount());

        // validate document by getDocument
        verifyGetDocumentInCollection(docID);
    }

    // helper method to verify n number of docs
    private void verifyDocuments(int n) {
        for (int i = 0; i < n; i++) { verifyGetDocumentInCollection(String.format(Locale.US, "doc_%03d", i), i); }
    }

    // helper methods to verify getDoc
    private void verifyGetDocumentInCollection(String docID) { verifyGetDocumentInCollection(docID, 1); }

    // helper methods to verify getDoc
    private void verifyGetDocumentInCollection(String docID, int value) {
        verifyGetDocumentInCollection(
            testCollection,
            docID,
            value);
    }

    // helper methods to verify getDoc
    private void verifyGetDocumentInCollection(Collection collection, String docID, int value) {
        Document doc = collection.getDocument(docID);
        assertNotNull(doc);
        assertEquals(docID, doc.getId());
        assertEquals(value, ((Number) doc.getValue("key")).intValue());
    }

    // helper method to purge doc and verify doc.
    private void purgeDocInCollectionAndVerify(Document doc) throws CouchbaseLiteException {
        String docID = doc.getId();
        testCollection.purge(doc);
        assertNull(testCollection.getDocument(docID));
    }
}

