//
// Copyright (c) 2022 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http:// www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.couchbase.lite

import com.couchbase.lite.internal.utils.SlowTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class CollectionTest : BaseDbTest() {
    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    fun testGetNonExistingDocWithID() {
        assertNull(testCollection.getDocument("doesnt-exist"))
    }

    // get doc in the collection
    @Test
    fun testGetExistingDocInCollection() {
        val mDoc = createTestDoc()

        testCollection.save(mDoc)

        assertSameContent(mDoc, testCollection.getDocument(mDoc.id))
    }

    // get doc from the same collection from a different database instance
    @Test
    fun testGetExistingDocWithIdFromDifferentDBInstance() {
        val doc = createDocInCollection()
        duplicateDb(testDatabase).use { assertNotNull(it.getSimilarCollection(testCollection).getDocument(doc.id)) }
    }

    // getting doc from deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromDeletedCollection() {
        // store doc
        val doc = createDocInCollection()

        // delete col
        testCollection.delete()

        // should fail
        testCollection.getDocument(doc.id)
    }

    // getting a doc from collection that is deleted in a different database instance causes CBL exception
    @SlowTest
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromCollectionDeletedInDifferentDBInstance() {
        val doc = createDocInCollection()

        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }

        testCollection.getDocument(doc.id)
    }

    // getting doc from collection in a closed db causes CBL Exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromCollectionInClosedDB() {
        val doc = createDocInCollection()

        closeDb(testDatabase)

        testCollection.getDocument(doc.id)
    }

    // getting doc from collection in a deleted db causes CBL Exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetDocFromCollectionInDeletedDB() {
        val doc = createDocInCollection()

        deleteDb(testDatabase)

        testCollection.getDocument(doc.id)
    }

    // getting doc count from deleted collection returns 0
    @Test
    fun testGetDocCountFromDeletedCollection() {
        // store doc
        createDocsInCollection(10)
        assertEquals(10, testCollection.count)

        // delete col
        testCollection.delete()

        assertEquals(0, testCollection.count)
    }

    // getting doc count from a collection in a deleted database returns 0
    @Test
    fun testGetDocCountFromCollectionInDeletedDatabase() {
        createDocsInCollection(10)
        assertEquals(10, testCollection.count)

        deleteDb(testDatabase)

        assertEquals(0, testCollection.count)
    }

    // getting doc count from a collection deleted in a different database instance returns 0
    @Test
    fun testGetDocCountFromCollectionDeletedInADifferentDBInstance() {
        // store docs
        createDocsInCollection(10)
        assertEquals(10, testCollection.count)

        // delete col in dup db
        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }

        assertEquals(0, testCollection.count)
    }

    // Test getting doc count from a collection in a closed database returns 0
    @Test
    fun testGetDocCountFromCollectionInClosedDatabase() {
        // store doc
        createDocsInCollection(10)
        assertEquals(10, testCollection.count)

        closeDb(testDatabase)

        assertEquals(0, testCollection.count)
    }

//---------------------------------------------
//  Save Document
//---------------------------------------------

    @Test
    fun saveNewDocInCollectionWithIdTest() {
        val id = getUniqueName("test_doc")

        val mDoc = MutableDocument(id)
        testCollection.save(mDoc)

        assertEquals(1, testCollection.count)
        assertSameContent(mDoc, testCollection.getDocument(id))
    }

    @Test
    fun testSaveNewDocInCollectionWithSpecialCharactersDocID() {
        val id = "!`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'"
        val mDoc = MutableDocument(id)
        testCollection.save(mDoc)

        assertEquals(1, testCollection.count)

        assertSameContent(mDoc, testCollection.getDocument(id))
    }

    @Test
    fun testSaveAndGetMultipleDocsInCollection() {
        val docs = createDocsInCollection(10)

        assertEquals(docs.size, testCollection.count.toInt())

        docs.forEach {
            assertSameContent(it, testCollection.getDocument(it.id))
        }
    }

    // saving a doc in a collection from a different DB instance throws
    @Test
    fun testSaveDocWithIdFromDifferentDBInstance() {
        val doc = createDocInCollection()

        duplicateDb(testDatabase).use {
            val dupCollection = it.getSimilarCollection(testCollection)
            assertNotSame(dupCollection, testCollection)
            assertNotNull(dupCollection)
            assertEquals(1, dupCollection.count)

            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) {
                dupCollection.save(doc.toMutable())
            }
        }
    }

    @Test
    fun testSaveDocAndUpdateInCollection() {
        // store doc
        val mDoc = createDocInCollection().toMutable()
        assertEquals(1, testCollection.count)

        // update doc
        mDoc.setValue(TEST_DOC_TAG_KEY, "whuddaboutdit")
        saveDocInCollection(mDoc)
        assertEquals(1, testCollection.count)

        // validate doc
        assertSameContent(mDoc, testCollection.getDocument(mDoc.id))

    }

    @Test
    fun testSaveSameDocTwice() {
        val doc = createDocInCollection()
        assertEquals(1, testCollection.count)

        testCollection.save(doc.toMutable())
        assertEquals(1, testCollection.count)
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToDeletedCollection() {
        testCollection.delete()
        testCollection.save(MutableDocument())
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToCollectionDeletedInDifferentDBInstance() {
        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }
        testCollection.save(MutableDocument())
    }

    // Test saving document in a collection of a closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToCollectionInClosedDB() {
        closeDb(testDatabase)
        testCollection.save(MutableDocument())
    }

    // Test saving document in a collection of a deleted database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testSaveDocToCollectionInDeletedDB() {
        deleteDb(testDatabase)
        testCollection.save(MutableDocument())
    }

    @Test
    fun testSaveAndUpdateMutableDoc() {
        val mDoc = MutableDocument()
        mDoc.setString("firstName", "Robert")
        mDoc.setString("lastName", "Bly")
        testCollection.save(mDoc)
        assertEquals(1, mDoc.sequence)

        mDoc.setString("firstName", "Daniel")
        testCollection.save(mDoc)
        assertEquals(2, mDoc.sequence)

        // Update
        mDoc.setString("lastName", "Tiger")
        testCollection.save(mDoc)
        assertEquals(3, mDoc.sequence)

        // Update again
        mDoc.setLong("age", 20L)
        testCollection.save(mDoc)
        assertEquals(4, mDoc.sequence)

        val expected = mapOf("firstName" to "Daniel", "lastName" to "Tiger", "age" to 20L)
        assertEquals(expected, mDoc.toMap())
        assertEquals(expected, testCollection.getDocument(mDoc.id)!!.content.toMap())
    }

    @Test
    fun testSaveDocsInBatch() {
        val col1 = testDatabase.createTestCollection()
        val col2 = testDatabase.createTestCollection()
        val col3 = testDatabase.createTestCollection()
        val col4 = testDatabase.createTestCollection()

        testDatabase.inBatch<CouchbaseLiteException> {
            col1.save(MutableDocument())
            col3.save(MutableDocument())
            col4.save(MutableDocument())
        }

        assertEquals(1, col1.count)
        assertEquals(0, col2.count)
        assertEquals(1, col3.count)
        assertEquals(1, col4.count)
    }

//---------------------------------------------
//  Delete Document
//---------------------------------------------

    @Test
    fun testDeleteDocument() {
        val doc = createDocInCollection()
        val docContent = doc.content.toMap()

        assertEquals(1, testCollection.count)
        assertNotNull(testCollection.getDocument(doc.id))

        // Delete:
        testCollection.delete(doc)

        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(doc.id))

        // The local copy should be unaffected.
        assertEquals(docContent, doc.content.toMap())
    }

    @Test
    fun testDeleteDocBeforeSave() {
        assertEquals(0, testCollection.count)
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            testCollection.delete(MutableDocument())
        }
    }

    @Test
    fun testDeleteMultipleDocs() {
        // Save 10 docs:
        val docs = createDocsInCollection(10)

        var n = 10L
        for (doc in docs) {
            testCollection.delete(doc)
            assertEquals(--n, testCollection.count)
            assertNull(testCollection.getDocument(doc.id))
        }
    }

    @Test
    fun testDeleteDocInCollectionFromDifferentDBInstance() {
        // Store doc:
        val doc = createDocInCollection()

        // Create db with same name:
        // Create db with default
        duplicateDb(testDatabase).use {
            val dupColl = it.getSimilarCollection(testCollection)
            assertNotNull(dupColl)
            assertNotSame(dupColl, testCollection)
            assertEquals(1, dupColl.count)

            // Try to delete the doc from the duplicate db instance:
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { dupColl.delete(doc) }
        }
    }

    @Test
    fun testDeleteDocInBatch() {
        val collection1 = testDatabase.createTestCollection()
        val collection2 = testDatabase.createTestCollection()
        val collection3 = testDatabase.createTestCollection()
        val collection4 = testDatabase.createTestCollection()

        val doc1 = createDocInCollection(collection = collection1)
        createDocInCollection(collection = collection2)
        val doc3 = createDocInCollection(collection = collection3)
        val doc4 = createDocInCollection(collection = collection4)

        testDatabase.inBatch<CouchbaseLiteException> {
            collection1.delete(doc1)
            collection3.delete(doc3)
            collection4.delete(doc4)
        }

        assertEquals(0, collection1.count)
        assertEquals(1, collection2.count)
        assertEquals(0, collection3.count)
        assertEquals(0, collection4.count)
    }

    // Test deleting doc from a deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocFromDeletedCollection() {
        val doc = createDocInCollection()
        testCollection.delete()
        testCollection.delete(doc)
    }

    // Test deleting doc from a collection that is deleted from a different db instance causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocFromCollectionDeletedInDifferentDBInstance() {
        val doc = createDocInCollection()
        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }
        testCollection.delete(doc)
    }

    // Test deleting doc on a collection in a closed db causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocFromCollectionInClosedDB() {
        val doc = createDocInCollection()
        closeDb(testDatabase)
        testCollection.delete(doc)
    }

    // Test deleting doc on a collection in a deleted db causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDocFromCollectionInDeletedDB() {
        val doc = createDocInCollection()
        deleteDb(testDatabase)
        testCollection.delete(doc)
    }

    @Test
    fun testDeleteAlreadyDeletedDoc() {
        val doc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(doc.id)!!
        val doc1b = testCollection.getDocument(doc.id)!!

        // Delete doc1a:
        testCollection.delete(doc1a)
        assertEquals(2, doc1a.sequence)
        assertNull(testCollection.getDocument(doc.id))

        // Delete doc1b:
        testCollection.delete(doc1b)
        assertEquals(2, doc1b.sequence)
        assertNull(testCollection.getDocument(doc.id))
    }

    @Test
    fun testDeletePurgedDoc() {
        val doc1a = createDocInCollection()
        val doc1b = testCollection.getDocument(doc1a.id)!!
        assertEquals(1, testCollection.count)

        // purge doc
        testCollection.purge(doc1a)
        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(doc1a.id))

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { testCollection.delete(doc1a) }
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { testCollection.delete(doc1b) }
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------

    @Test
    fun testPurgeDocBeforeSaveDoc() {
        assertEquals(0, testCollection.count)

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            testCollection.purge(MutableDocument())
        }
    }

    @Test
    fun testPurgeDoc() {
        val doc = createDocInCollection()

        // Purge Doc
        testCollection.purge(doc)

        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(doc.id))
    }

    @Test
    fun testPurgeSameDocTwice() {
        // Store doc:
        val doc1 = createDocInCollection()

        // Get the document for the second purge:
        val doc2 = testCollection.getDocument(doc1.id)!!

        // Purge the document first time:
        testCollection.purge(doc1)
        assertNull(testCollection.getDocument(doc1.id))
        assertEquals(0, testCollection.count)

        // Purge the document second time:
        testCollection.purge(doc2)
        assertNull(testCollection.getDocument(doc1.id))
        assertEquals(0, testCollection.count)
    }

    // Purge document from a deleted collection
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocFromDeletedCollection() {
        val doc = createDocInCollection()

        // delete collection
        testCollection.delete()

        testCollection.purge(doc.id)
    }

    // Purge document from a collection deleted in a different DB Instance
    @SlowTest
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocFromCollectionDeletedInADifferentDBInstance() {
        val doc = createDocInCollection()

        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }

        testCollection.purge(doc.id)
    }

    @Test
    fun testPurgeDocInDifferentDBCollectionInstance() {
        // Store doc:
        val doc = createDocInCollection()

        // create db
        duplicateDb(testDatabase).use {
            val collection = it.getSimilarCollection(testCollection)

            // purge document against collection in the other db:
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) {
                collection.purge(doc)
            }
        }
    }

    @Test
    fun testPurgeDocInBatch() {
        val collection1 = testDatabase.createTestCollection()
        val collection2 = testDatabase.createTestCollection()
        val collection3 = testDatabase.createTestCollection()
        val collection4 = testDatabase.createTestCollection()

        val doc1 = MutableDocument("doc1")
        val doc2 = MutableDocument("doc2")
        val doc3 = MutableDocument("doc3")
        val doc4 = MutableDocument("doc4")

        collection1.save(doc1)
        collection2.save(doc2)
        collection3.save(doc3)
        collection4.save(doc4)

        testDatabase.inBatch<CouchbaseLiteException> {
            collection1.purge(doc1)
            collection3.purge(doc3)
            collection4.purge(doc4)
        }

        assertEquals(0, collection1.count)
        assertEquals(1, collection2.count)
        assertEquals(0, collection3.count)
        assertEquals(0, collection4.count)
    }

    // Test purging doc on a deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocOnDeletedCollection() {
        val doc = createDocInCollection()

        // delete collection
        testCollection.delete()

        // purge doc
        testCollection.purge(doc)
    }

    // Test purging doc from a collection in a closed database causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocFromCollectionInClosedDB() {
        val doc = createDocInCollection()
        closeDb(testDatabase)
        testCollection.purge(doc)
    }

    // Test purging doc from a collection in a deleted database causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testPurgeDocFromCollectionInDeletedDB() {
        val doc = createDocInCollection()
        deleteDb(testDatabase)
        testCollection.purge(doc)
    }

    //---------------------------------------------
    //  Index functionalities
    //---------------------------------------------

    @Test
    fun testCreateIndexInCollection() {
        assertEquals(0, testCollection.indexes.size)

        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)

        testCollection.createIndex("index2", FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es"))
        assertEquals(2, testCollection.indexes.size)

        assertContents(testCollection.indexes.toList(), "index1", "index2")
        assertTrue(testCollection.indexes.contains("index2"))
    }

    @Test
    fun testCreateSameIndexTwice() {
        testCollection.createIndex("myindex", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)

        // Call create index again:
        testCollection.createIndex("myindex", ValueIndexConfiguration("firstName", "lastName"))

        assertContents(testCollection.indexes.toList(), "myindex")
    }

    @Test
    fun testCreateSameNameIndexes() {
        // Create value index with first name:
        testCollection.createIndex("myindex", ValueIndexConfiguration("firstName"))

        // Replace with value index with last name:
        testCollection.createIndex("myindex", ValueIndexConfiguration("lastName"))

        // Check:
        assertContents(testCollection.indexes.toList(), "myindex")

        // Do it one more time
        testCollection.createIndex("myindex", ValueIndexConfiguration("detail"))

        // Check:
        assertContents(testCollection.indexes.toList(), "myindex")
    }

    // Test create index from a deleted collection
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexFromDeletedCollection() {
        // Delete collection
        testCollection.delete()
        testCollection.createIndex("index2", ValueIndexConfiguration("firstName", "lastName"))
    }

    // Test create index from a collection deleted in a different db instance
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexFromCollectionDeletedInDifferentDBInstance() {
        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }
        testCollection.createIndex("index2", ValueIndexConfiguration("firstName", "lastName"))
    }

    // Test that createIndex in collection in closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexInCollectionInClosedDatabase() {
        closeDb(testDatabase)
        testCollection.createIndex("test_index", ValueIndexConfiguration("firstName", "lastName"))
    }

    // Test that createIndex in collection in deleted database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testCreateIndexInCollectionInDeletedDatabase() {
        deleteDb(testDatabase)
        testCollection.createIndex("test_index", ValueIndexConfiguration("firstName", "lastName"))
    }

    // Test getting index from a deleted collection causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexFromDeletedCollection() {
        // Delete collection
        testCollection.delete()
        testCollection.indexes
    }

    @Ignore("CBL-3824")
    @Test
    fun testCopyFullTextIndex() {
        val db = Database(getUniqueName("test"))
        val coll = db.createCollection("aaa", "bbb")
        coll.createIndex("idx", FullTextIndexConfiguration("detail"))
        Database(db.name)
    }

    // Test getting index from a collection deleted from another DB instance causes CBL exception
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexFromCollectionDeletedFromADifferentDBInstance() {
        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }
        testCollection.indexes
    }

    // Test that getIndexes from collection in closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexesFromCollectionFromClosedDatabase() {
        failOnError("Failed to create index 'index1' for collection ${testCollection}") {
            testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
            assertContents(testCollection.indexes.toList(), "index1")
        }
        closeDb(testDatabase)
        testCollection.indexes
    }

    // Test that getIndexes from collection in deleted database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testGetIndexesFromCollectionFromDeletedDatabase() {
        failOnError("Failed to create index 'index1' for collection ${testCollection}") {
            testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
            assertContents(testCollection.indexes.toList(), "index1")
        }
        deleteDb(testDatabase)
        testCollection.indexes
    }

    @Test
    fun testDeleteIndex() {
        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        testCollection.createIndex("index2", FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es"))
        assertContents(testCollection.indexes.toList(), "index1", "index2")

        // Delete indexes:
        testCollection.deleteIndex("index2")
        assertEquals(1, testCollection.indexes.size)
        assertContents(testCollection.indexes.toList(), "index1")

        testCollection.deleteIndex("index1")
        assertTrue(testCollection.indexes.isEmpty())
    }

    // Test deleting an index twice
    @Test
    fun testDeleteIndexTwice() {
        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        testCollection.createIndex("index2", FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es"))
        assertContents(testCollection.indexes.toList(), "index1", "index2")

        // Delete index2:
        testCollection.deleteIndex("index2")
        assertEquals(1, testCollection.indexes.size)
        assertTrue(testCollection.indexes.contains("index1"))

        // Do it again
        testCollection.deleteIndex("index2")
        assertEquals(1, testCollection.indexes.size)
        assertTrue(testCollection.indexes.contains("index1"))
    }

    // Test getting index from a deleted collection causes CBL exception
    @Test
    fun testDeleteNonExistentIndex() {
        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        testCollection.createIndex("index2", FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es"))
        assertContents(testCollection.indexes.toList(), "index1", "index2")

        testCollection.deleteIndex("dummy")

        assertContents(testCollection.indexes.toList(), "index1", "index2")
    }

    // Test delete index from a deletedCollection
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexFromDeletedCollection() {
        failOnError("Failed to create index 'index1' for collection ${testCollection}") {
            testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
            assertContents(testCollection.indexes.toList(), "index1")
        }

        // Delete collection
        testCollection.delete()

        // delete index
        testCollection.deleteIndex("index1")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexFromCollectionDeletedInDifferentDbInstance() {
        failOnError("Failed to create index 'index1' for collection ${testCollection}") {
            testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
            assertContents(testCollection.indexes.toList(), "index1")
        }

        duplicateDb(testDatabase).use { it.getSimilarCollection(testCollection).delete() }

        // delete index
        testCollection.deleteIndex("index1")
    }

    // Test that deletedIndex in collection in closed database causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexInCollectionInClosedDatabase() {
        failOnError("Failed to create index 'index1' for collection ${testCollection}") {
            testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
            assertContents(testCollection.indexes.toList(), "index1")
        }
        closeDb(testDatabase)
        testCollection.deleteIndex("index1")
    }

    // Test that deleteIndex in collection in deleted causes CBLException
    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteIndexInCollectionInDeletedDatabase() {
        failOnError("Failed to create index 'index1' for collection ${testCollection}") {
            testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
            assertContents(testCollection.indexes.toList(), "index1")
        }
        deleteDb(testDatabase)
        testCollection.deleteIndex("index1")
    }

    //---------------------------------------------
    //  Operations with Conflict
    //---------------------------------------------
    @Test
    fun testSaveDocWithConflictLastWriteWins() {
        val mDoc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(mDoc.id)!!.toMutable()
        val doc1b = testCollection.getDocument(mDoc.id)!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)

        assertEquals(2, doc1a.sequence)

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Ernest")
        assertTrue(testCollection.save(doc1b, ConcurrencyControl.LAST_WRITE_WINS))

        val doc = testCollection.getDocument(mDoc.id)
        assertSameContent(doc1b, doc)
        assertEquals(3, doc!!.sequence)
    }

    @Test
    fun testSaveDocWithConflictFailOnConflict() {
        val mDoc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(mDoc.id)!!.toMutable()
        val doc1b = testCollection.getDocument(mDoc.id)!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)

        assertEquals(2, doc1a.sequence)

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Ernest")
        assertFalse(testCollection.save(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))

        val doc = testCollection.getDocument(mDoc.id)
        assertSameContent(doc1a, doc)
        assertEquals(2, doc!!.sequence)
    }

    @Test
    fun testDeleteDocWithConflictLastWriteWins() {
        val mDoc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(mDoc.id)!!.toMutable()
        val doc1b = testCollection.getDocument(mDoc.id)!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)

        assertEquals(2, doc1a.sequence)

        // Modify doc1b, result to cause conflict when deleted:
        doc1b.setString("lastName", "Ernest")
        assertTrue(testCollection.delete(doc1b, ConcurrencyControl.LAST_WRITE_WINS))

        assertNull(testCollection.getDocument(mDoc.id))
        assertEquals(3, doc1b.sequence)
    }

    @Test
    fun testDeleteDocWithConflictFailOnConflict() {
        val mDoc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(mDoc.id)!!.toMutable()
        val doc1b = testCollection.getDocument(mDoc.id)!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        testCollection.save(doc1a)
        assertEquals(2, doc1a.sequence)

        // Modify doc1b, this will cause a conflict when it is deleted:
        doc1b.setString("lastName", "Ernest")
        assertFalse(testCollection.delete(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))

        val doc = testCollection.getDocument(mDoc.id)
        assertSameContent(doc1a, doc)
        assertEquals(2, doc!!.sequence)
    }

    @Test
    fun testSaveDocWithNoParentConflictLastWriteWins() {
        val mDoc = MutableDocument()
        mDoc.setString("firstName", "Scott")
        assertTrue(testCollection.save(mDoc, ConcurrencyControl.LAST_WRITE_WINS))

        val doc = testCollection.getDocument(mDoc.id)
        assertSameContent(mDoc, doc)
        assertEquals(1, doc!!.sequence)
    }

    @Test
    fun testSaveDocWithNoParentConflictFailOnConflict() {
        val mDoc = MutableDocument()
        mDoc.setString("firstName", "Scott")

        assertTrue(testCollection.save(mDoc, ConcurrencyControl.FAIL_ON_CONFLICT))

        val doc = testCollection.getDocument(mDoc.id)
        assertSameContent(mDoc, doc)
        assertEquals(1, doc!!.sequence)
    }

    @Test
    fun testSaveDocWithDeletedConflictLastWriteWins() {
        val mDoc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(mDoc.id)!!
        val doc1b = testCollection.getDocument(mDoc.id)!!.toMutable()

        // Delete doc1a:
        testCollection.delete(doc1a)
        assertEquals(2, doc1a.sequence)
        assertNull(testCollection.getDocument(mDoc.id))

        // Modify doc1b, result to cause conflict when saved:
        doc1b.setString("lastName", "Ernest")
        assertTrue(testCollection.save(doc1b, ConcurrencyControl.LAST_WRITE_WINS))

        val doc = testCollection.getDocument(mDoc.id)
        assertSameContent(doc1b, doc)
        assertEquals(3, doc!!.sequence)
    }

    @Test
    fun testSaveDocWithDeletedConflictFailOnConflict() {
        val mDoc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = testCollection.getDocument(mDoc.id)!!
        val doc1b = testCollection.getDocument(mDoc.id)!!.toMutable()

        // Delete doc1a:
        testCollection.delete(doc1a)
        assertEquals(2, doc1a.sequence)
        assertNull(testCollection.getDocument(mDoc.id))

        // Modify doc1b, result to cause conflict when saved:
        doc1b.setString("lastName", "Ernest")
        assertFalse(testCollection.save(doc1b, ConcurrencyControl.FAIL_ON_CONFLICT))

        assertNull(testCollection.getDocument(mDoc.id))
        assertEquals(2, doc1a.sequence)
    }

    //---------------------------------------------
    //  Operations on deleted collections
    //---------------------------------------------

    @Test
    fun testDeleteThenAccessDoc() {
        // Store doc:
        val doc = createDocInCollection()
        val docContent = doc.content.toMap()

        // Delete the collection
        testCollection.delete()

        // Content should be accessible and modifiable without error
        assertEquals(docContent, doc.content.toMap())
        doc.toMutable().setValue("lastName", "Ernest")
    }

    @Test
    fun testDeleteThenGetCollectionName() {
        val collectionName = testCollection.name
        testCollection.delete()
        assertEquals(collectionName, testCollection.name)
    }
}