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

package com.couchbase.lite

import com.couchbase.lite.internal.utils.Report
import com.couchbase.lite.internal.utils.TestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class CollectionTest : BaseCollectionTest() {
    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    fun testGetNonExistingDocWithID() {
        assertNull(testCollection.getDocument("non-exist"))
    }

    // get doc in the collection
    @Test
    @Throws(CouchbaseLiteException::class)
    fun testGetExistingDocInCollection() {
        val docId = "doc1"
        createSingleDocInCollectionWithId(docId)
        verifyGetDocumentInCollection(docId)
    }

    // get doc from the same collection from a different database instance
    @Test
    fun testGetExistingDocWithIDFromDifferentDBInstance() {
        val docID = "doc1"
        val doc = MutableDocument(docID)

        val coll1 = baseTestDb.createCollection(getUniqueName("coll_1"))
        coll1.save(doc)

        val otherDatabase = duplicateDb(baseTestDb)
        try {
            val coll2 = otherDatabase.getCollection(coll1.name)
            assertNotNull(coll2)
            if (coll2 != null) {
                assertEquals(1, coll2.count)
            }
        } finally {
            closeDb(otherDatabase)
        }
    }

    // Test getting doc from collection that is deleted in a different database instance causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromCollectionDeletedInDifferentDBInstance() {
        val otherDatabase = duplicateDb(baseTestDb)
        val docID = "doc1"
        val doc = MutableDocument(docID)

        saveDocInTestCollection(doc)
        otherDatabase.deleteCollection(testColName, testScopeName)

        testCollection.getDocument(docID)

    }

    // Test getting doc from deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromDeletedCollection() {
        //store doc
        createSingleDocInCollectionWithId("doc1")

        //delete col
        Report.log("test scope name $testScopeName")
        baseTestDb.deleteCollection(testColName, testScopeName)

        //should fail
        testCollection.getDocument("doc1")
    }

    // Test getting doc from collection in a closed db causes CBL Exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromCollectionInClosedDB() {
        createSingleDocInCollectionWithId("doc_id")
        closeDb(baseTestDb)
        testCollection.getDocument("doc_id")
    }

    // Test getting doc from collection in a deleted db causes CBL Exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromCollectionInDeletedDB() {
        createSingleDocInCollectionWithId("doc_id")
        deleteDb(baseTestDb)
        testCollection.getDocument("doc_id")
    }

    // Test getting doc count from deleted collection returns 0
    @Test
    fun testGetDocCountFromDeletedCollection() {
        // store doc
        createDocsInTestCollection(10)

        // delete col
        baseTestDb.deleteCollection(testColName, testScopeName)

        assertEquals(0, testCollection.count)
    }

    // Test getting doc count from a collection deleted in a different database instance returns 0
    @Test
    fun testGetDocFromCollectionDeletedInADifferentDBInstance() {
        val otherDatabase = duplicateDb(baseTestDb)
        val docID = "doc1"
        val doc = MutableDocument(docID)
        saveDocInTestCollection(doc)

        otherDatabase.deleteCollection(testColName, testScopeName)
        assertEquals(0, testCollection.count)
    }

    // Test getting doc count from a collection in a closed database returns 0
    @Test
    fun testGetDocCountFromCollectionInClosedDatabase() {
        val docID = "doc_id"
        val doc = MutableDocument(docID)
        saveDocInTestCollection(doc)
        assertEquals(1, testCollection.count)
        closeDb(baseTestDb)
        assertEquals(0, testCollection.count)
    }

    // Test getting doc count from a collection in a deleted database returns 0
    @Test
    fun testGetDocCountFromCollectionInDeletedDatabase() {
        val docID = "doc_id"
        val doc = MutableDocument(docID)
        saveDocInTestCollection(doc)
        assertEquals(1, testCollection.count)
        deleteDb(baseTestDb)
        assertEquals(0, testCollection.count)
    }
    //---------------------------------------------
    //  Save Document
    //---------------------------------------------

    @Test
    @Throws(CouchbaseLiteException::class)
    fun saveNewDocInCollectionWithIDTest() {
        saveNewDocInCollectionWithIDTest("doc1")
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testSaveNewDocInCollectionWithSpecialCharactersDocID() {
        saveNewDocInCollectionWithIDTest("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'")
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testSaveAndGetMultipleDocsInCollection() {
        val nDocs = 10 //1000;
        for (i in 0 until nDocs) {
            val doc = MutableDocument(String.format(Locale.US, "doc_%03d", i))
            doc.setValue("key", i)
            saveDocInTestCollection(doc)
        }
        assertEquals(nDocs.toLong(), testCollection.count)
        verifyDocuments(nDocs)
    }

    // Save doc in a collection from a different DB instance
    @Test
    fun testSaveDocWithIDFromDifferentDBInstance() {
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID).toMutable()

        val otherDB = duplicateDb(baseTestDb)
        val collection = otherDB.getCollection(testColName, testScopeName)
        assertNotNull(collection)

        try {
            assertNotSame(collection, testCollection)
            assertEquals(1, collection!!.count)

            // Update doc and store it into different instance
            doc.setValue("key", 2)
            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) {
                collection.save(doc)
            }
        } finally {
            closeDb(otherDB)
        }
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testSaveDocAndUpdateInCollection() {
        // store doc
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID).toMutable()

        // update doc
        doc.setValue("key", 2)
        saveDocInTestCollection(doc)
        assertEquals(1, testCollection.count)

        // validate document by getDocument
        verifyGetDocumentInCollection(docID, 2)
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testSaveSameDocTwice() {
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID).toMutable()
        assertEquals(docID, saveDocInTestCollection(doc).id)
        assertEquals(1, testCollection.count)
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToDeletedCollection() {
        baseTestDb.deleteCollection(testColName, testScopeName)
        val doc = MutableDocument("doc1")
        doc.setValue("key", 1)

        testCollection.save(doc)
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToCollectionDeletedInDifferentDBInstance() {
        val otherDb = duplicateDb(baseTestDb)
        otherDb.deleteCollection(testColName, testScopeName)
        val docID = "doc1"
        val doc = MutableDocument(docID)
        testCollection.save(doc)
    }

    // Test saving document in a collection of a closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToCollectionInClosedDB() {
        closeDb(baseTestDb)
        saveDocInTestCollection(MutableDocument("invalid"))
    }

    // Test saving document in a collection of a deleted database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToCollectionInDeletedDB(){
        deleteDb(baseTestDb)
        saveDocInTestCollection(MutableDocument("invalid"))
    }

    @Test
    fun testSaveAndUpdateMutableDoc() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        testCollection.save(doc)

        // Update:
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Update:
        doc.setLong("age", 20L) // Int vs Long assertEquals can not ignore diff.
        testCollection.save(doc)
        assertEquals(3, doc.sequence)
        val expected: MutableMap<String, Any> = HashMap()
        expected["firstName"] = "Daniel"
        expected["lastName"] = "Tiger"
        expected["age"] = 20L
        assertEquals(expected, doc.toMap())
        val savedDoc = testCollection.getDocument(doc.id)
        assertEquals(expected, savedDoc!!.toMap())
        assertEquals(3, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithIDInBatch() {
        val doc1 = MutableDocument("doc1")
        val doc2 = MutableDocument("doc2")
        val doc3 = MutableDocument("doc3")
        val doc4 = MutableDocument("doc4")

        val col1 = baseTestDb.createCollection(getUniqueName("col1"), getUniqueName("scope1"))
        val col2 = baseTestDb.createCollection(getUniqueName("col2"), getUniqueName("scope2"))
        val col3 = baseTestDb.createCollection(getUniqueName("col3"), getUniqueName("scope3"))
        val col4 = baseTestDb.createCollection(getUniqueName("col4"), getUniqueName("scope4"))

        baseTestDb.inBatch<CouchbaseLiteException> {
            col1.save(doc1)
            col2.save(doc2)
            col3.save(doc3)
            col4.save(doc4)
        }

        assertEquals(1, col1.count)
        assertEquals(1, col2.count)
        assertEquals(1, col3.count)
        assertEquals(1, col4.count)
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------


    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteDocument() {
        val docID = "doc1"
        val mDoc = MutableDocument(docID)
        mDoc.setValue("name", "Scott Tiger")

        // Save:
        testCollection.save(mDoc)

        // Delete:
        testCollection.delete(mDoc)
        assertNull(testCollection.getDocument(docID))

        // NOTE: doc is reserved.
        val v = mDoc.getValue("name")
        assertEquals("Scott Tiger", v)
        val expected: MutableMap<String, Any> = HashMap()
        expected["name"] = "Scott Tiger"
        assertEquals(expected, mDoc.toMap())
    }

    @Test
    fun testDeletePreSaveDoc() {
        val doc = MutableDocument("doc1")
        doc.setValue("key", 1)
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            testCollection.delete(doc)
        }
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteDoc() {
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID)
        assertEquals(1, testCollection.count)
        testCollection.delete(doc)
        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(docID))
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteMultipleDocs() {
        val nDocs = 10

        // Save 10 docs:
        createDocsInTestCollection(nDocs)
        for (i in 0 until nDocs) {
            val docID = String.format(Locale.US, "doc_%03d", i)
            val doc = testCollection.getDocument(docID)
            testCollection.delete(doc!!)
            assertNull(testCollection.getDocument(docID))
            assertEquals(9 - i, testCollection.count.toInt())
        }
        assertEquals(0, testCollection.count)
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteDocInCollectionFromDifferentDBInstance() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID)

        // Create db with same name:
        // Create db with default
        val otherDb = duplicateBaseTestDb()
        try {
            val collection = otherDb.getCollection(testColName, testScopeName)
            assertNotNull(collection)
            assertNotSame(collection, testCollection)
            assertEquals(1, collection!!.count)

            // Delete from the different db instance:
            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) {
                collection.delete(doc)
            }
        } finally {
            closeDb(otherDb)
        }
    }

    @Test
    fun testDeleteDocInBatch() {
        val collection1 = baseTestDb.createCollection(getUniqueName("col1"))
        val collection2 = baseTestDb.createCollection(getUniqueName("col2"))
        val collection3 = baseTestDb.createCollection(getUniqueName("col3"))
        val collection4 = baseTestDb.createCollection(getUniqueName("col4"))

        val doc1 = MutableDocument("doc1")
        val doc2 = MutableDocument("doc2")
        val doc3 = MutableDocument("doc3")
        val doc4 = MutableDocument("doc4")

        collection1.save(doc1)
        collection2.save(doc2)
        collection3.save(doc3)
        collection4.save(doc4)

        baseTestDb.inBatch<CouchbaseLiteException> {
            collection1.delete(doc1)
            collection3.delete(doc3)
            collection4.delete(doc4)
        }

        assertEquals(1, collection2.count)
        assertEquals(0, collection1.count)
        assertEquals(0, collection3.count)
        assertEquals(0, collection4.count)
    }

    // Test deleting doc on a deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocOnDeletedCollection() {
        val doc = createSingleDocInCollectionWithId("doc1")
        baseTestDb.deleteCollection(testColName, testScopeName)
        testCollection.delete(doc)
    }

    // Test deleting doc on a collection that is deleted from a different db instance causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocOnCollectionDeletedInDifferentDBInstance() {
        val otherDb = duplicateDb(baseTestDb)

        val doc = createSingleDocInCollectionWithId("doc")
        otherDb.deleteCollection(testColName, testScopeName)

        testCollection.delete(doc)
    }

    // Test deleting doc on a collection in a closed db causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocOnCollectionInClosedDB() {
        val doc = createSingleDocInCollectionWithId("doc_id")
        closeDb(baseTestDb)
        testCollection.delete(doc)
    }

    // Test deleting doc on a collection in a deleted db causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocOnCollectionInDeletedDB(){
        val doc = createSingleDocInCollectionWithId("doc_id")
        deleteDb(baseTestDb)
        testCollection.delete(doc)
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteAlreadyDeletedDoc() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Delete doc1a:
        testCollection.delete(doc1a!!)
        assertEquals(2, doc1a.sequence)
        assertNull(testCollection.getDocument(doc.id))

        // Delete doc1b:
        testCollection.delete(doc1b)
        assertEquals(2, doc1b.sequence)
        assertNull(testCollection.getDocument(doc.id))
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteNonExistingDoc() {
        val doc1a = createSingleDocInCollectionWithId("doc1")
        val doc1b = testCollection.getDocument("doc1")

        // purge doc
        testCollection.purge(doc1a)
        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(doc1a.id))
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            testCollection.delete(doc1a)
        }
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            testCollection.delete(doc1b!!)
        }
        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(doc1b!!.id))
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    fun testPurgePreSaveDoc() {
        val doc = MutableDocument("doc1")
        assertEquals(0, testCollection.count)
        TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            testCollection.purge(doc)
        }
        assertEquals(0, testCollection.count)
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testPurgeDoc() {
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID)

        // Purge Doc
        purgeDocInCollectionAndVerify(doc)
        assertEquals(0, testCollection.count)
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testPurgeSameDocTwice() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID)

        // Get the document for the second purge:
        val doc1 = testCollection.getDocument(docID)

        // Purge the document first time:
        purgeDocInCollectionAndVerify(doc)
        assertEquals(0, testCollection.count)

        // Purge the document second time:
        purgeDocInCollectionAndVerify(doc1)
        assertEquals(0, testCollection.count)
    }

    // Purge document from a deleted collection
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocInDeletedCollection() {
        // Store doc
        val docID = "doc1"
        createSingleDocInCollectionWithId(docID)

        // delete collection
        baseTestDb.deleteCollection(testColName, testScopeName)

        testCollection.purge(docID)
    }

    // Purge document from a collection deleted in a different DB Instance
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocInCollectionDeletedInADifferentDBInstance() {
        val docID = "doc_id"
        createSingleDocInCollectionWithId(docID)

        val otherDb = duplicateBaseTestDb()
        otherDb.deleteCollection(testColName, testScopeName)
        testCollection.purge(docID)
    }

    @Test
    fun testPurgeDocInDifferentDBCollectionInstance() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID)

        //create db
        val otherDB = duplicateBaseTestDb()
        try {
            val collection = otherDB.getCollection(testColName, testScopeName)
            assertNotNull(collection)
            assertEquals(1, collection!!.count)

            //purge document against collection in the other db:
            TestUtils.assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { collection.purge(doc) }
        } finally {
            closeDb(otherDB)
        }
    }

    @Test
    fun testPurgeDocInBatch() {
        val collection1 = baseTestDb.createCollection(getUniqueName("col1"))
        val collection2 = baseTestDb.createCollection(getUniqueName("col2"))
        val collection3 = baseTestDb.createCollection(getUniqueName("col3"))
        val collection4 = baseTestDb.createCollection(getUniqueName("col4"))

        val doc1 = MutableDocument("doc1")
        val doc2 = MutableDocument("doc2")
        val doc3 = MutableDocument("doc3")
        val doc4 = MutableDocument("doc4")

        collection1.save(doc1)
        collection2.save(doc2)
        collection3.save(doc3)
        collection4.save(doc4)

        baseTestDb.inBatch<CouchbaseLiteException> {
            collection1.purge(doc1)
            collection2.purge(doc2)
            collection3.purge(doc3)
            collection4.purge(doc4)
        }
        assertEquals(0, collection1.count)
        assertEquals(0, collection2.count)
        assertEquals(0, collection3.count)
        assertEquals(0, collection4.count)
    }

    // Test purging doc on a deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocOnDeletedCollection() {
        //store doc
        val doc = createSingleDocInCollectionWithId("doc1")

        //delete doc
        baseTestDb.deleteCollection(testColName, testScopeName)

        //purge doc
        testCollection.purge(doc)
    }

    // Test purging doc from a collection in a closed database causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocFromCollectionInClosedDB() {
        val doc = createSingleDocInCollectionWithId("doc_id")
        closeDb(baseTestDb)
        testCollection.purge(doc)
    }

    // Test purging doc from a collection in a deleted database causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocFromCollectionInDeletedDB() {
        val doc = createSingleDocInCollectionWithId("doc_id")
        deleteDb(baseTestDb)
        testCollection.purge(doc)
    }

    //---------------------------------------------
    //  Index functionalities
    //---------------------------------------------
    @Test
    @Throws(CouchbaseLiteException::class)
    fun testCreateIndexInCollection() {
        assertEquals(0, testCollection.indexes.size)
        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)
        testCollection.createIndex(
            "index2",
            FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es")
        )
        assertEquals(2, testCollection.indexes.size)
        assertTrue(testCollection.indexes.contains("index1"))
        assertTrue(testCollection.indexes.contains("index2"))
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testCreateSameIndexTwice() {
        testCollection.createIndex("myindex", ValueIndexConfiguration("firstName", "lastName"))

        // Call create index again:
        testCollection.createIndex("myindex", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)
        assertContents(testCollection.indexes.toList(), "myindex")
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testCreateSameNameIndexes() {

        // Create value index with first name:
        testCollection.createIndex("myindex", ValueIndexConfiguration("firstName"))

        // Create value index with last name:
        testCollection.createIndex("myindex", ValueIndexConfiguration("lastName"))

        // Check:
        assertEquals(1, testCollection.indexes.size)
        assertEquals(listOf("myindex"), testCollection.indexes.toList())


        testCollection.createIndex("myindex", ValueIndexConfiguration("detail"))

        // Check:
        assertEquals(1, testCollection.indexes.size)
        assertContents(testCollection.indexes.toList(), "myindex")
    }

    @Test
    @Throws(CouchbaseLiteException::class)
    fun testDeleteIndex() {
        testCreateIndexInCollection()

        // Delete indexes:
        testCollection.deleteIndex("index2")
        assertEquals(1, testCollection.indexes.size)
        assertTrue(testCollection.indexes.contains("index1"))
        testCollection.deleteIndex("index1")
        assertEquals(0, testCollection.indexes.size)
        assertTrue(testCollection.indexes.isEmpty())

        // Delete non existing index:
        testCollection.deleteIndex("dummy")

        // Delete deleted index:
        testCollection.deleteIndex("index1")
    }

    // Test getting index from a deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexFromDeletedCollection() {
        testCreateIndexInCollection()

        // Delete collection
        baseTestDb.deleteCollection(testColName, testScopeName)
        testCollection.indexes
    }

    // Test getting index from a collection deleted from another DB instance causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexFromCollectionDeletedFromADifferentDBInstance() {
        testCreateIndexInCollection()
        val otherDB = duplicateBaseTestDb()
        assertNotNull(otherDB.getCollection(testColName, testScopeName))
        // Delete collection
        otherDB.deleteCollection(testColName, testScopeName)
        testCollection.indexes
    }

    // Test create index from a deleted collection
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexFromDeletedCollection() {
        // Delete collection
        baseTestDb.deleteCollection(testColName, testScopeName)
        testCreateIndexInCollection()
    }

    // Test create index from a collection deleted in a different db instance
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexFromCollectionDeletedInDifferentDBInstance() {
        val otherDb = duplicateBaseTestDb()
        otherDb.deleteCollection(testColName, testScopeName)
        testCreateIndexInCollection()
    }

    // Test delete index from a deletedCollection
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexFromDeletedCollection() {
        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)

        // Delete collection
        baseTestDb.deleteCollection(testColName, testScopeName)

        // delete index
        testCollection.deleteIndex("index1")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexFromCollectionDeletedInDifferentDbInstance() {
        val otherDb = duplicateBaseTestDb()

        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)

        // Delete collection
        otherDb.deleteCollection(testColName, testScopeName)

        // delete index
        testCollection.deleteIndex("index1")
    }

    // Test that getIndexes from collection in closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexesFromCollectionInClosedDatabase() {
        testCollection.createIndex("test_index", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)
        closeDb(baseTestDb)
        testCollection.indexes
    }

    // Test that getIndexes from collection in deleted database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexesFromCollectionInDeletedDatabase() {
        testCollection.createIndex("test_index", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)
        deleteDb(baseTestDb)
        testCollection.indexes
    }

    // Test that createIndex in collection in closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexInCollectionInClosedDatabase() {
        closeDb(baseTestDb)
        testCollection.createIndex("test_index", ValueIndexConfiguration("firstName", "lastName"))
    }

    // Test that createIndex in collection in deleted database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexInCollectionInDeletedDatabase() {
        deleteDb(baseTestDb)
        testCollection.createIndex("test_index", ValueIndexConfiguration("firstName", "lastName"))
    }

    // Test that deletedIndex in collection in closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexInCollectionInClosedDatabase(){
        val name = "test_index"
        testCollection.createIndex(name, ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)
        closeDb(baseTestDb)
        testCollection.deleteIndex(name)
    }

    // Test that deleteINdex in collection in deleted causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexInCollectionInDeletedDatabase(){
        val name = "test_index"
        testCollection.createIndex(name, ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)
        deleteDb(baseTestDb)
        testCollection.deleteIndex(name)
    }

    //---------------------------------------------
    //  Operations with Conflict
    //---------------------------------------------
    @Test
    fun testSaveDocWithConflictLastWriteWins() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")!!.toMutable()
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)
        doc1a.setString("nickName", "Scotty")
        testCollection.save(doc1a)

        val expected: MutableMap<String, Any> = HashMap()
        expected["firstName"] = "Scott"
        expected["lastName"] = "Tiger"
        expected["nickName"] = "Scotty"
        assertEquals(expected, doc1a.toMap())
        assertEquals(3, doc1a.sequence)

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        assertTrue(testCollection.save(doc1b, ConcurrencyControl.LAST_WRITE_WINS))
        val savedDoc = testCollection.getDocument(doc.id)
        assertEquals(doc1b.toMap(), savedDoc!!.toMap())
        assertEquals(4, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithConflictFailOnConflict() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")!!.toMutable()
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)
        doc1a.setString("nickName", "Scotty")
        testCollection.save(doc1a)

        val expected: MutableMap<String, Any> = HashMap()
        expected["firstName"] = "Scott"
        expected["lastName"] = "Tiger"
        expected["nickName"] = "Scotty"
        assertEquals(expected, doc1a.toMap())
        assertEquals(3, doc1a.sequence)

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        assertFalse(testCollection.save(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))
        val savedDoc = testCollection.getDocument(doc.id)
        assertEquals(expected, savedDoc!!.toMap())
        assertEquals(3, savedDoc.sequence)
    }

    @Test
    fun testDeleteDocWithConflictLastWriteWins() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")!!.toMutable()
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)

        val expected: MutableMap<String, Any> = HashMap()
        expected["firstName"] = "Scott"
        expected["lastName"] = "Tiger"
        assertEquals(expected, doc1a.toMap())
        assertEquals(2, doc1a.sequence)

        // Modify doc1b and delete, result to conflict when delete:
        doc1b.setString("lastName", "Lion")
        assertTrue(testCollection.delete(doc1b, ConcurrencyControl.LAST_WRITE_WINS))
        assertEquals(3, doc1b.sequence)
        assertNull(testCollection.getDocument(doc1b.id))
    }

    @Test
    fun testDeleteDocWithConflictFailOnConflict() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")!!.toMutable()
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)

        val expected: MutableMap<String, Any> = HashMap()
        expected["firstName"] = "Scott"
        expected["lastName"] = "Tiger"
        assertEquals(expected, doc1a.toMap())
        assertEquals(2, doc1a.sequence)

        // Modify doc1b and delete, result to conflict when delete:
        assertFalse(testCollection.delete(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))
        val savedDoc = testCollection.getDocument(doc.id)
        assertEquals(expected, savedDoc!!.toMap())
        assertEquals(2, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithNoParentConflictLastWriteWins() {
        val doc1a = MutableDocument("doc1")
        doc1a.setString("firstName", "Daniel")
        doc1a.setString("lastName", "Tiger")
        testCollection.save(doc1a)

        var savedDoc = testCollection.getDocument(doc1a.id)
        assertEquals(doc1a.toMap(), savedDoc!!.toMap())
        assertEquals(1, savedDoc.sequence)

        val doc1b = MutableDocument("doc1")
        doc1b.setString("firstName", "Scott")
        doc1b.setString("lastName", "Tiger")

        assertTrue(testCollection.save(doc1b, ConcurrencyControl.LAST_WRITE_WINS))
        savedDoc = testCollection.getDocument(doc1b.id)
        assertEquals(doc1b.toMap(), savedDoc!!.toMap())
        assertEquals(2, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithNoParentConflictFailOnConflict() {
        val doc1a = MutableDocument("doc1")
        doc1a.setString("firstName", "Daniel")
        doc1a.setString("lastName", "Tiger")
        testCollection.save(doc1a)

        var savedDoc = testCollection.getDocument(doc1a.id)
        assertEquals(doc1a.toMap(), savedDoc!!.toMap())
        assertEquals(1, savedDoc.sequence)

        val doc1b = MutableDocument("doc1")
        doc1b.setString("firstName", "Scott")
        doc1b.setString("lastName", "Tiger")

        assertFalse(testCollection.save(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))
        savedDoc = testCollection.getDocument(doc1b.id)
        assertEquals(doc1a.toMap(), savedDoc!!.toMap())
        assertEquals(1, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithDeletedConflictLastWriteWins() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Delete doc1a:
        testCollection.delete(doc1a!!)
        assertEquals(2, doc1a.sequence)
        assertNull(testCollection.getDocument(doc.id))

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        assertTrue(testCollection.save(doc1b, ConcurrencyControl.LAST_WRITE_WINS))
        val savedDoc = testCollection.getDocument(doc.id)
        assertEquals(doc1b.toMap(), savedDoc!!.toMap())
        assertEquals(3, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithDeletedConflictFailOnConflict() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument("doc1")
        val doc1b = testCollection.getDocument("doc1")!!.toMutable()

        // Delete doc1a:
        testCollection.delete(doc1a!!)
        assertEquals(2, doc1a.sequence)
        assertNull(testCollection.getDocument(doc.id))

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        assertFalse(testCollection.save(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))
        assertNull(testCollection.getDocument(doc.id))
    }

    @Throws(CouchbaseLiteException::class)
    private fun saveNewDocInCollectionWithIDTest(docID: String) {
        // store doc
        createSingleDocInCollectionWithId(docID)
        assertEquals(1, testCollection.count)

        // validate document by getDocument
        verifyGetDocumentInCollection(docID)
    }

    // helper method to verify n number of docs
    private fun verifyDocuments(n: Int) {
        for (i in 0 until n) {
            verifyGetDocumentInCollection(String.format(Locale.US, "doc_%03d", i), i)
        }
    }

    //---------------------------------------------
    //  Operations on deleted collections
    //---------------------------------------------
    @Test
    fun testDeleteThenAccessDoc() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInCollectionWithId(docID).toMutable()

        // Delete db
        baseTestDb.deleteCollection(testColName, testScopeName)

        // Content should be accessible and modifiable without error
        assertEquals(docID, doc.id)
        assertEquals(1, (doc.getValue("key") as Number).toInt())
        doc.setValue("key", 2)
        doc.setValue("key1", "value")
    }

    @Test
    fun testDeleteThenGetCollectionName() {
        baseTestDb.deleteCollection(testColName, testScopeName)
        assertEquals(testColName, testCollection.name)
    }


    // helper methods to verify getDoc
    private fun verifyGetDocumentInCollection(docID: String, value: Int = 1) {
        verifyGetDocumentInCollection(
            testCollection,
            docID,
            value
        )
    }

    // helper methods to verify getDoc
    private fun verifyGetDocumentInCollection(collection: Collection, docID: String, value: Int) {
        val doc = collection.getDocument(docID)
        assertNotNull(doc)
        assertEquals(docID, doc!!.id)
        assertEquals(value, (doc.getValue("key") as Number?)!!.toInt())
    }

    // helper method to purge doc and verify doc.
    @Throws(CouchbaseLiteException::class)
    private fun purgeDocInCollectionAndVerify(doc: Document?) {
        val docID = doc!!.id
        testCollection.purge(doc)
        assertNull(testCollection.getDocument(docID))
    }
}