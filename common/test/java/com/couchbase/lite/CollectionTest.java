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

import org.junit.Ignore;
import org.junit.Test;

import com.couchbase.lite.internal.utils.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    // base test method
    private void testSaveNewDocInCollectionWithID(String docID) throws CouchbaseLiteException {
        // store doc
        createSingleDocInCollectionWithId(docID);
        assertEquals(1, testCollection.getCount());

        // validate document by getDocument
        verifyGetDocumentInCollection(docID);
    }

    @Test
    public void testSaveNewDocInCollectionWithID() throws CouchbaseLiteException {
        testSaveNewDocInCollectionWithID("doc1");
    }

    @Test
    public void testSaveNewDocInCollectionWithSpecialCharactersDocID() throws CouchbaseLiteException {
        testSaveNewDocInCollectionWithID("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'");
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

    @Test
    @Ignore
    public void testSaveDocWithConflict() throws CouchbaseLiteException {
        testSaveDocInCollectionWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocInCollectionWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    @Ignore
    public void testDeleteDocWithConflict() throws CouchbaseLiteException {
        testDeleteDocInCollectionWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testDeleteDocInCollectionWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    @Ignore
    public void testSaveDocWithNoParentConflict() throws CouchbaseLiteException {
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
    }

    @Test
    @Ignore
    public void testSaveDocWithDeletedConflict() throws CouchbaseLiteException {
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS);
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT);
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

    // What's the expected outcome if a deleted collection try to purge a document?
    @Test(expected = IllegalStateException.class)
    @Ignore
    public void testPurgeDocOnDeletedCollection() throws CouchbaseLiteException {
        // Store doc:
        Document doc = createSingleDocInCollectionWithId("doc1");

        // Delete Collection
        testScope.deleteCollection(testCollection);

        // Purge doc:
        testCollection.purge(doc);
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

    private void testSaveDocInCollectionWithConflictUsingConcurrencyControl(ConcurrencyControl cc)
        throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        testCollection.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        MutableDocument doc1a = testCollection.getDocument("doc1").toMutable();
        MutableDocument doc1b = testCollection.getDocument("doc1").toMutable();

        // Modify doc1a:
        doc1a.setString("firstName", "Scott");
        testCollection.save(doc1a);
        doc1a.setString("nickName", "Scotty");
        testCollection.save(doc1a);

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        expected.put("nickName", "Scotty");
        assertEquals(expected, doc1a.toMap());
        assertEquals(3, doc1a.getSequence());

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(testCollection.save(doc1b, cc));
            Document savedDoc = testCollection.getDocument(doc.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(4, savedDoc.getSequence());
        }
        else {
            assertFalse(testCollection.save(doc1b, cc));
            Document savedDoc = testCollection.getDocument(doc.getId());
            assertEquals(expected, savedDoc.toMap());
            assertEquals(3, savedDoc.getSequence());
        }
    }

    private void testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl cc)
        throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        testCollection.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        Document doc1a = testCollection.getDocument("doc1");
        MutableDocument doc1b = testCollection.getDocument("doc1").toMutable();

        // Delete doc1a:
        testCollection.delete(doc1a);
        assertEquals(2, doc1a.getSequence());
        assertNull(testCollection.getDocument(doc.getId()));

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(testCollection.save(doc1b, cc));
            Document savedDoc = testCollection.getDocument(doc.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(3, savedDoc.getSequence());
        }
        else {
            assertFalse(testCollection.save(doc1b, cc));
            assertNull(testCollection.getDocument(doc.getId()));
        }
        //recreate a new collection
        testCollection = recreateCollection(testCollection);
    }

    private void testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl cc)
        throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setString("firstName", "Daniel");
        doc1a.setString("lastName", "Tiger");
        testCollection.save(doc1a);

        Document savedDoc = testCollection.getDocument(doc1a.getId());
        assertEquals(doc1a.toMap(), savedDoc.toMap());
        assertEquals(1, savedDoc.getSequence());

        MutableDocument doc1b = new MutableDocument("doc1");
        doc1b.setString("firstName", "Scott");
        doc1b.setString("lastName", "Tiger");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(testCollection.save(doc1b, cc));
            savedDoc = testCollection.getDocument(doc1b.getId());
            assertEquals(doc1b.toMap(), savedDoc.toMap());
            assertEquals(2, savedDoc.getSequence());
        }
        else {
            assertFalse(testCollection.save(doc1b, cc));
            savedDoc = testCollection.getDocument(doc1b.getId());
            assertEquals(doc1a.toMap(), savedDoc.toMap());
            assertEquals(1, savedDoc.getSequence());
        }
    }

    private void testDeleteDocInCollectionWithConflictUsingConcurrencyControl(ConcurrencyControl cc)
        throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setString("firstName", "Daniel");
        doc.setString("lastName", "Tiger");
        testCollection.save(doc);

        // Get two doc1 document objects (doc1a and doc1b):
        MutableDocument doc1a = testCollection.getDocument("doc1").toMutable();
        MutableDocument doc1b = testCollection.getDocument("doc1").toMutable();

        // Modify doc1a:
        doc1a.setString("firstName", "Scott");
        testCollection.save(doc1a);

        Map<String, Object> expected = new HashMap<>();
        expected.put("firstName", "Scott");
        expected.put("lastName", "Tiger");
        assertEquals(expected, doc1a.toMap());
        assertEquals(2, doc1a.getSequence());

        // Modify doc1b and delete, result to conflict when delete:
        doc1b.setString("lastName", "Lion");
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(testCollection.delete(doc1b, cc));
            assertEquals(3, doc1b.getSequence());
            assertNull(testCollection.getDocument(doc1b.getId()));
        }
        else {
            assertFalse(testCollection.delete(doc1b, cc));
            Document savedDoc = testCollection.getDocument(doc.getId());
            assertEquals(expected, savedDoc.toMap());
            assertEquals(2, savedDoc.getSequence());
        }
    }
}

