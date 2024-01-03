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
package com.couchbase.lite

import com.couchbase.lite.internal.CouchbaseLiteInternal
import com.couchbase.lite.internal.core.C4Constants
import com.couchbase.lite.internal.core.C4Database
import com.couchbase.lite.internal.utils.FileUtils
import com.couchbase.lite.internal.utils.SlowTest
import com.couchbase.lite.internal.utils.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit


// The suite of tests that verifies behavior
// with a deleted default collection are in
// cbl-java-common @ a2de0d43d09ce64fd3a1301dc35

// The rules in this test are:
// testDatabase is managed by the superclass
// If a test creates a new database it guarantees that it is deleted.
// If a test opens a copy of the testDatabase, it closes (but does NOT delete) it
class DatabaseTest : BaseDbTest() {

    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    fun testGetNonExistingDocWithID() {
        assertNull(testCollection.getDocument("doesnt-exist"))
    }

    @Test
    fun testGetExistingDocWithID() {
        val doc = MutableDocument()
        doc.setValue(TEST_DOC_TAG_KEY, testTag)
        saveDocInCollection(doc)
        verifyDocInCollection(doc.id)
    }

    @SlowTest
    @Test
    fun testGetExistingDocWithIDFromDifferentDBInstance() {
        // store doc
        val doc = createDocInCollection()

        // open db with same db name and default option
        val (otherDb, otherCollection) = duplicateTestDb()
        otherDb.use {
            assertNotSame(testDatabase, otherDb)

            // get doc from other DB.
            assertEquals(1, otherCollection.count)
            verifyDocInCollection(doc.id)
        }
    }

    @Test
    fun testGetExistingDocWithIDInBatch() {
        val docIds = createDocsInCollection(13).map { it.id }
        testDatabase.inBatch<RuntimeException> { verifyDocsInCollection(docIds) }
    }

    @Test
    fun testGetDocFromClosedDB() {
        // Store doc:
        val docId = createDocInCollection().id

        // Close db:
        testDatabase.close()

        // should fail
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testCollection.getDocument(docId) }
    }

    @Test
    fun testGetDocFromDeletedDB() {
        // Store doc:
        val docId = createDocInCollection().id

        // Delete db:
        testDatabase.delete()

        // should fail
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testCollection.getDocument(docId) }
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------

    @Test
    fun testSaveNewDocWithID() {
        val doc = MutableDocument("doc1")
        doc.setValue(TEST_DOC_TAG_KEY, testTag)
        testCollection.save(doc)

        assertEquals(1, testCollection.count)

        // validate document by getDocument
        verifyDocInCollection(doc.id)
    }

    @Test
    fun testSaveNewDocWithSpecialCharactersDocID() {
        val n = testCollection.count

        val doc = MutableDocument("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'")
        doc.setValue(TEST_DOC_TAG_KEY, testTag)
        testCollection.save(doc)


        assertEquals(n + 1, testCollection.count)

        // validate document by getDocument
        verifyDocInCollection(doc.id)
    }

    @Test
    fun testSaveAndGetMultipleDocs() {
        val docIds = createDocsInCollection(11).map { it.id }
        assertEquals(docIds.size.toLong(), testCollection.count)
        verifyDocsInCollection(docIds)
    }

    @Test
    fun testSaveDoc() {
        val n = testCollection.count

        // store doc
        val doc = createDocInCollection()

        // update doc
        doc.setValue(TEST_DOC_TAG_KEY, "bam!!!")
        saveDocInCollection(doc)
        assertEquals(n + 1, testCollection.count)

        verifyDocInCollection(doc.id, "bam!!!")
    }

    @SlowTest
    @Test
    fun testSaveDocInDifferentDBInstance() {
        val n = testCollection.count

        // Store doc
        val doc = createDocInCollection()

        // Create db with default
        val (otherDb, otherCollection) = duplicateTestDb()
        otherDb.use {
            assertNotSame(otherDb, testDatabase)
            assertNotSame(otherCollection, testCollection)
            assertEquals(n + 1, otherCollection.count)

            // Attempt to save the doc in the wrong db
            doc.setValue(TEST_DOC_TAG_KEY, "bam!!!")
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherCollection.save(doc) }
        }
    }

    @Test
    fun testSaveDocInDifferentDB() {
        // Store doc
        val doc = createDocInCollection()

        // Create db with default
        val otherDb = createDb("save_doc_diff_db")
        val otherCollection = otherDb.createSimilarCollection(testCollection)
        try {
            assertNotSame(otherDb, testDatabase)
            assertNotSame(otherCollection, testCollection)
            assertEquals(0, otherCollection.count)

            // Attempt to save the doc in a *very* wrong db
            doc.setValue(TEST_DOC_TAG_KEY, "bam!!!")
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherCollection.save(doc) }
        } finally {
            // delete otherDb
            eraseDb(otherDb)
        }
    }

    @Test
    fun testSaveSameDocTwice() {
        val n = testCollection.count

        val doc = createDocInCollection()
        assertEquals(n + 1, testCollection.count)

        assertEquals(doc.id, saveDocInCollection(doc).id)

        assertEquals(n + 1, testCollection.count)
    }

    @Test
    fun testSaveInBatch() {
        val nDocs = 17
        val n = testCollection.count

        var docIds: kotlin.collections.Collection<String>? = null
        testDatabase.inBatch<CouchbaseLiteException> {
            docIds = createDocsInCollection(nDocs).map { it.id }
        }
        assertEquals(n + nDocs, testCollection.count)
        verifyDocsInCollection(docIds!!)
    }

    @Test
    fun testSaveDocToClosedDB() {
        testDatabase.close()
        val doc = MutableDocument()
        doc.setValue(TEST_DOC_TAG_KEY, testTag)
        assertThrowsCBLException(CBLError.Domain.CBLITE, C4Constants.LiteCoreError.NOT_OPEN) { testCollection.save(doc) }
    }

    @Test
    fun testSaveDocToDeletedDB() {
        // Delete db:
        testDatabase.delete()
        val doc = MutableDocument()
        doc.setValue(TEST_DOC_TAG_KEY, testTag)
        assertThrowsCBLException(CBLError.Domain.CBLITE, C4Constants.LiteCoreError.NOT_OPEN) { testCollection.save(doc) }
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    fun testDeleteNonExistentDoc() {
        val doc = MutableDocument("doesnt_exist")
        doc.setValue(TEST_DOC_TAG_KEY, testTag)
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { testCollection.delete(doc) }
    }

    @Test
    fun testDeleteDoc() {
        val n = testCollection.count
        val doc = createDocInCollection()
        assertEquals(n + 1, testCollection.count)
        testCollection.delete(doc)
        assertEquals(n, testCollection.count)
        assertNull(testCollection.getDocument(doc.id))
    }

    @SlowTest
    @Test
    fun testDeleteDocInDifferentDBInstance() {
        val n = testCollection.count

        // Store doc:
        val doc = createDocInCollection()

        // Create db with same name:
        // Create db with default
        val (otherDb, otherCollection) = duplicateTestDb()
        otherDb.use {
            assertNotSame(otherDb, testDatabase)
            assertNotSame(otherCollection, testCollection)
            assertEquals(n + 1, testCollection.count)

            // Delete from the wrong db
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherCollection.delete(doc) }
        }
    }

    @Test
    fun testDeleteDocInDifferentDB() {
        // Store doc
        val doc = createDocInCollection()

        // Create db with default
        val otherDb = createDb("del_doc_other_db")
        val otherCollection = otherDb.createTestCollection()
        try {
            assertNotSame(otherDb, testDatabase)
            assertNotSame(otherCollection, testCollection)

            // Delete from the different db:
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherCollection.delete(doc) }
        } finally {
            eraseDb(otherDb)
        }
    }

    @Test
    fun testDeleteDocInBatch() {
        val n = testCollection.count

        var nDocs = 23

        val docs = createDocsInCollection(nDocs).map { it.id }
        testDatabase.inBatch<CouchbaseLiteException> {
            docs.forEach { docId ->
                val doc = testCollection.getDocument(docId)
                assertNotNull(doc!!)
                testCollection.delete(doc)
                assertNull(testCollection.getDocument(docId))
                assertEquals(n + --nDocs, testCollection.count)
            }
        }
        assertEquals(n, testCollection.count)
    }

    @Test
    fun testDeleteDocOnClosedDB() {
        // Store doc:
        val doc = createDocInCollection()

        // Close db:
        testDatabase.close()

        // Delete doc from closed db:
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testCollection.delete(doc) }
    }

    @Test
    fun testDeleteDocOnDeletedDB() {
        // Store doc:
        val doc = createDocInCollection()

        testDatabase.delete()

        // Delete doc from deleted db:
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testCollection.delete(doc) }
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    fun testPurgeNonexistentDoc() {
        val n = testCollection.count
        val doc = MutableDocument("doc1")
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { testCollection.purge(doc) }
        assertEquals(n, testCollection.count)
    }

    @Test
    fun testPurgeDoc() {
        val n = testCollection.count

        val doc = createDocInCollection()
        assertEquals(n + 1, testCollection.count)

        // Purge Doc
        purgeDocAndVerify(doc)
        assertEquals(0, testCollection.count)
    }

    @SlowTest
    @Test
    fun testPurgeDocInDifferentDBInstance() {
        val n = testCollection.count

        // Store doc:
        val doc = createDocInCollection()

        // Create db with default:
        val (otherDb, otherCollection) = duplicateTestDb()
        otherDb.use {
            assertNotSame(otherDb, testDatabase)
            assertNotSame(otherCollection, testCollection)
            assertEquals(n + 1, otherCollection.count)

            // purge document against other db instance:
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherCollection.purge(doc) }
        }
    }

    @Test
    fun testPurgeDocInDifferentDB() {
        // Store doc:
        val doc = createDocInCollection()

        // Create db with default:
        val otherDb = createDb("purge_doc_other_db")
        val otherCollection = otherDb.createSimilarCollection(testCollection)
        try {
            assertNotSame(otherDb, testDatabase)
            assertNotSame(otherCollection, testCollection)
            assertEquals(0, otherCollection.count)

            // Purge document against other db:
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherCollection.purge(doc) }
        } finally {
            eraseDb(otherDb)
        }
    }

    @Test
    fun testPurgeSameDocTwice() {
        val n = testCollection.count

        // Store doc:
        val doc = createDocInCollection()
        assertEquals(n + 1, testCollection.count)

        // Get the document for the second purge:
        val doc1 = testCollection.getDocument(doc.id)
        assertNotNull(doc1!!)

        // Purge the document first time:
        purgeDocAndVerify(doc)
        assertEquals(n, testCollection.count)

        // Purge the document second time:
        purgeDocAndVerify(doc1)
        assertEquals(n, testCollection.count)
    }

    @Test
    fun testPurgeDocInBatch() {
        val n = testCollection.count

        var nDocs = 10

        val docIds = createDocsInCollection(nDocs).map { it.id }

        testDatabase.inBatch<CouchbaseLiteException> {
            docIds.forEach { docId ->
                val doc = testCollection.getDocument(docId)
                assertNotNull(doc!!)
                purgeDocAndVerify(doc)
                assertEquals(n + --nDocs, testCollection.count)
            }
        }
        assertEquals(0, testCollection.count)
    }

    @Test
    fun testPurgeDocOnClosedDB() {
        // Store doc:
        val doc = createDocInCollection()

        // Close db:
        testDatabase.close()

        // Purge doc:
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testCollection.purge(doc) }
    }

    @Test
    fun testPurgeDocOnDeletedDB() {
        // Store doc:
        val doc = createDocInCollection()

        // Close db:
        testDatabase.close()

        // Purge doc:
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testCollection.purge(doc) }
    }

    //---------------------------------------------
    //  Close Database
    //---------------------------------------------
    @Test
    fun testClose() {
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
    }

    @Test
    fun testCloseTwice() {
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
    }

    @Test
    fun testCloseThenAccessDoc() {
        // Store doc:
        val mDict = MutableDictionary() // nested dictionary
        mDict.setString("hello", "world")
        val mDoc = MutableDocument()
        mDoc.setString(TEST_DOC_TAG_KEY, testTag)
        mDoc.setDictionary("dict", mDict)
        val doc = saveDocInCollection(mDoc)

        // Close db:
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)

        // Content should be accessible & modifiable without error:
        assertEquals(mDoc.id, doc.id)
        assertEquals(testTag, doc.getValue(TEST_DOC_TAG_KEY))
        val dict = doc.getDictionary("dict")
        assertNotNull(dict!!)
        assertEquals("world", dict.getString("hello"))
        val updateDoc = doc.toMutable()
        updateDoc.setValue(TEST_DOC_TAG_KEY, "bam!!")
        updateDoc.setValue("anotherValue", 55)
        assertEquals("bam!!", updateDoc.getString(TEST_DOC_TAG_KEY))
        assertEquals(55, updateDoc.getInt("anotherValue"))
    }

    @Test
    fun testCloseThenAccessBlob1() {
        // Store doc with blob:
        val mDoc = createDocInCollection()
        mDoc.setValue("blob", Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8)))
        val doc = saveDocInCollection(mDoc)

        // Close db:
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)

        // content should be accessible & modifiable without error
        val blob = doc.getBlob("blob")
        assertNotNull(blob)
        assertEquals(BLOB_CONTENT.length.toLong(), blob!!.length())
    }

    @Test
    fun testCloseThenAccessBlob2() {
        // Store doc with blob:
        val mDoc = createDocInCollection()
        mDoc.setValue("blob", Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8)))
        val doc = saveDocInCollection(mDoc)

        // Close db:
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)

        // content should be accessible & modifiable without error
        val blob = doc.getBlob("blob")
        assertNotNull(blob)

        // trying to get the content, however, should fail
        assertThrows(java.lang.IllegalStateException::class.java) { blob!!.content }
    }

    @Test
    fun testCloseThenGetDatabaseName() {
        val dbName = testDatabase.name
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
        assertEquals(dbName, testDatabase.name)
    }

    @Test
    fun testCloseThenGetDatabasePath() {
        assertNotNull(testDatabase.path)
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
        assertNull(testDatabase.path)
    }

    @Test
    fun testCloseThenCallInBatch() {
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
        assertThrows(java.lang.IllegalStateException::class.java) {
            testDatabase.inBatch<RuntimeException> { }
        }
    }

    @Test
    fun testCloseInInBatch() {
        testDatabase.inBatch<RuntimeException> {
            // can't close a db in a transaction
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.TRANSACTION_NOT_CLOSED) { testDatabase.close() }
        }
    }

    @Test
    fun testCloseThenDeleteDatabase() {
        assertTrue(testDatabase.isOpen)
        testDatabase.close()
        assertFalse(testDatabase.isOpen)
        assertThrows(java.lang.IllegalStateException::class.java) { testDatabase.delete() }
    }

    //---------------------------------------------
    //  Delete Database
    //---------------------------------------------
    @Test
    fun testDelete() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())
        testDatabase.delete()
        assertFalse(path.exists())
    }

    @Test
    fun testDeleteTwice() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())

        // delete db
        testDatabase.delete()
        assertFalse(path.exists())

        // second delete should fail
        assertThrows(java.lang.IllegalStateException::class.java) { testDatabase.delete() }
    }

    @Test
    fun testDeleteThenAccessDoc() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())

        // Store doc:
        val doc = createDocInCollection()

        // Delete db:
        testDatabase.delete()
        assertFalse(path.exists())

        // Content should be accessible & modifiable without error:
        assertEquals(testTag, doc.getValue(TEST_DOC_TAG_KEY))
        doc.setValue(TEST_DOC_TAG_KEY, "yaya")
        doc.setValue("gitchagitcha", "yaya")
    }

    @Test
    fun testDeleteThenAccessBlob() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())

        // Store doc with blob:
        val doc = createDocInCollection()
        doc.setValue("blob", Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8)))
        saveDocInCollection(doc)

        // Delete db:
        testDatabase.delete()
        assertFalse(path.exists())

        // content should be accessible & modifiable without error
        val obj = doc.getValue("blob")!!
        assertNotNull(obj)
        assertTrue(obj is Blob)
        val blob = obj as Blob
        assertEquals(BLOB_CONTENT.length.toLong(), blob.length())

        // content should still be available.
        assertNotNull(blob.content)
    }

    @Test
    fun testDeleteThenGetDatabaseName() {
        val dbName = testDatabase.name

        val path = File(testDatabase.path!!)
        assertTrue(path.exists())

        // delete db
        testDatabase.delete()
        assertFalse(path.exists())

        assertEquals(dbName, testDatabase.name)
    }

    @Test
    fun testDeleteThenGetDatabasePath() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())

        // delete db
        testDatabase.delete()
        assertFalse(path.exists())

        assertNull(testDatabase.path)
    }

    @Test
    fun testDeleteThenCallInBatch() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())
        testDatabase.delete()
        assertFalse(path.exists())
        assertThrows(java.lang.IllegalStateException::class.java) {
            testDatabase.inBatch<RuntimeException> { }
        }
    }

    @Test
    fun testDeleteInInBatch() {
        val path = File(testDatabase.path!!)
        assertTrue(path.exists())
        testDatabase.inBatch<RuntimeException> {
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.TRANSACTION_NOT_CLOSED) { testDatabase.delete() }
        }
    }

    @SlowTest
    @Test
    fun testDeleteDBOpenedByOtherInstance() {
        val n = testCollection.count

        val path = File(testDatabase.path!!)
        assertTrue(path.exists())

        val (otherDb, otherCollection) = duplicateTestDb()
        otherDb.use {
            assertNotSame(testDatabase, otherDb)
            assertNotSame(testCollection, otherCollection)
            assertEquals(n, otherCollection.count)

            // delete db
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.BUSY) { testDatabase.delete() }
        }
    }

    //---------------------------------------------
    //  Delete Database (static)
    //---------------------------------------------
    @Test
    fun testDeleteWithDefaultDirDB() {
        val dbName = testDatabase.name

        val path = File(testDatabase.path!!)
        assertNotNull(path)
        assertTrue(path.exists())

        // close db before delete
        testDatabase.close()
        Database.delete(dbName, null)
        assertFalse(path.exists())
    }

    @SlowTest
    @Test
    fun testDeleteOpenDbWithDefaultDir() {
        val path = File(testDatabase.path!!)
        assertNotNull(path)
        assertTrue(path.exists())

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.BUSY) { Database.delete(testDatabase.name, null) }
    }

    @Test
    fun testStaticDeleteDb() {
        val dbDirPath = getScratchDirectoryPath(getUniqueName("static-delete-dir"))

        // create db in a custom directory
        val db = createDb("static_del_db", DatabaseConfiguration().setDirectory(dbDirPath))
        try {
            val dbPath = File(db.path!!)
            assertTrue(dbPath.exists())

            // close db before delete
            db.close()
            Database.delete(db.name, File(dbDirPath))
            assertFalse(dbPath.exists())
        } finally {
            eraseDb(db)
        }
    }

    @SlowTest
    @Test
    fun testDeleteOpenDBWithStaticMethod() {
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.BUSY) {
            Database.delete(testDatabase.name, testDatabase.filePath!!.parentFile)
        }
    }

    @Test
    fun testDeleteNonExistingDBWithDefaultDir() {
        assertThrowsCBLException(CBLError.Domain.CBLITE, C4Constants.LiteCoreError.NOT_FOUND) {
            Database.delete("doesntexist", testDatabase.filePath)
        }
    }

    @Test
    fun testDeleteNonExistingDB() {
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            Database.delete(testDatabase.name, File(getScratchDirectoryPath("nowhere")))
        }
    }

    //---------------------------------------------
    //  Database Existence
    //---------------------------------------------

    @Test
    fun testDatabaseExistsWithDefaultDir() {
        var db: Database? = null
        try {
            db = Database(getUniqueName("defaultDb"))
            assertTrue(Database.exists(db.name, null))
        } finally {
            db?.let { deleteDb(db) }
        }
    }

    @Test
    fun testDatabaseExistsWithDir() {
        val dirName = getUniqueName("test-exists-dir")
        val dbDirPath = getScratchDirectoryPath(dirName)
        val dbDir = File(dbDirPath)
        assertFalse(Database.exists(dirName, dbDir))

        // create db with custom directory
        val db = Database(dirName, DatabaseConfiguration().setDirectory(dbDirPath))
        try {
            val dbPath = db.path!!
            assertTrue(Database.exists(dirName, dbDir))

            db.close()
            assertTrue(Database.exists(dirName, dbDir))

            Database.delete(dirName, dbDir)
            assertFalse(Database.exists(dirName, dbDir))

            assertFalse(File(dbPath).exists())
        } finally {
            eraseDb(db)
        }
    }

    @Test
    fun testDatabaseExistsAgainstNonExistDBWithDefaultDir() {
        assertFalse(Database.exists("doesntexist", testDatabase.filePath!!))
    }

    @Test
    fun testDatabaseExistsAgainstNonExistDB() {
        assertFalse(Database.exists(testDatabase.name, File(getScratchDirectoryPath("nowhere"))))
    }

    //---------------------------------------------
    //  Collections, 8.5: Use Collection API on Deleted Collection
    //---------------------------------------------

    // 8.5.1: Test that after the database is closed, calling functions on the scope object
    // returns the expected result based on section 6.3
    @Test
    fun testUseCollectionAPIOnDeletedCollection() {
        val collection = testDatabase.createCollection("bobblehead", "horo")
        assertNotNull(collection)

        val doc = MutableDocument()
        collection.save(doc)

        testDatabase.deleteCollection("bobblehead", "horo")
        assertNull(testDatabase.getCollection("bobblehead", "horo"))

        assertEquals("horo", collection.scope.name)
        assertEquals("bobblehead", collection.name)

        // These two calls should generate warnings, but should not fail
        collection.addChangeListener { }
        collection.addDocumentChangeListener(doc.id) { }

        // All of these things should throw
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.getDocument(doc.id) }

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.save(MutableDocument()) }

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.delete(doc) }

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.purge(doc.id) }

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.indexes }

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.deleteIndex("foo") }

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            collection.createIndex("index", IndexBuilder.valueIndex(ValueIndexItem.property("firstName")))
        }
    }

    // 8.5.2: Test that after the database is closed, calling functions on the scope object
    // returns the expected result based on section 6.3
    @SlowTest
    @Test
    fun testUseCollectionAPIOnDeletedCollectionDeletedFromDifferentDBInstance() {
        val collection = testDatabase.createCollection("bobblehead", "horo")
        assertNotNull(collection)

        val doc = MutableDocument()
        collection.save(doc)

        // delete the collection in a different database
        duplicateDb(testDatabase).use { otherDb ->
            otherDb.deleteCollection("bobblehead", "horo")
            assertNull(testDatabase.getCollection("bobblehead", "horo"))

            assertEquals("horo", collection.scope.name)
            assertEquals("bobblehead", collection.name)

            // These two calls should generate warnings, but should not fail
            collection.addChangeListener { }
            collection.addDocumentChangeListener("docId") { }

            // All of these things should throw
            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.getDocument(doc.id) }

            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.save(MutableDocument()) }

            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.delete(doc) }

            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.purge(doc.id) }

            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.indexes }

            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { collection.deleteIndex("foo") }

            assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
                collection.createIndex("index", IndexBuilder.valueIndex(ValueIndexItem.property("firstName")))
            }
        }
    }

    //---------------------------------------------
    //  Collections, 8.6: Use Collection API on Closed or Deleted Database
    //---------------------------------------------

    // 8.6.1: Test that after the database is closed, calling functions on the scope object
    // returns the expected result based on section 6.3
    @Test
    fun testUseScopeWhenDatabaseIsClosed1() {
        testDatabase.createCollection("bobblehead", "horo")

        val scope = testDatabase.getScope("horo")
        assertNotNull(scope!!)
        assertNotNull(scope.collections)

        testDatabase.close()
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope.collections }
    }

    @Test
    fun testUseScopeWhenDatabaseIsClosed2() {
        testDatabase.createCollection("bobblehead", "horo")

        val scope = testDatabase.getScope("horo")
        assertNotNull(scope!!)
        assertNotNull(scope.collections)

        testDatabase.close()
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope.getCollection("bobblehead") }
    }

    //---------------------------------------------
    //  Collections, 8.7: Use Scope API on Closed or Deleted Database
    //---------------------------------------------

    // 8.7.2: Test that after the database is deleted, calling functions on the scope object
    // returns the expected result based on section 6.3
    @Test
    fun testUseScopeWhenDatabaseIsDeleted1() {
        testDatabase.createCollection("bobblehead", "horo")
        val scope = testDatabase.getScope("horo")

        assertNotNull(scope!!)
        assertNotNull(scope.collections)

        testDatabase.delete()
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope.collections }
    }

    @Test
    fun testUseScopeWhenDatabaseIsDeleted2() {
        testDatabase.createCollection("bobblehead", "horo")
        val scope = testDatabase.getScope("horo")

        assertNotNull(scope!!)
        assertNotNull(scope.collections)

        testDatabase.delete()
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope.getCollection("bobblehead") }
    }

    //---------------------------------------------
    //  Collections, 8.8 Get Scopes or Collections on Closed or Deleted Database
    //---------------------------------------------

    // 8.8.1 Test that after the database is closed, calling functions to get scopes or collections
    // returns the result as expected based on section 6.4
    @Test
    fun testGetScopeWhenDatabaseIsClosed() {
        testDatabase.createCollection("bobblehead", "horo")

        assertNotNull(testDatabase.getScope("horo"))

        testDatabase.close()

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testDatabase.getScope("horo") }
    }

    @Test
    fun testGetCollectionWhenDatabaseIsClosed() {
        testDatabase.createCollection("bobblehead", "horo")

        assertNotNull(testDatabase.getCollection("bobblehead", "horo"))

        testDatabase.close()

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            testDatabase.getCollection("bobblehead", "horo")
        }
    }

    // 8.8.2 Test that after the database is deleted, calling functions to get scopes or collections
    // returns the expected result based on section 6.4
    @Test
    fun testGetScopeWhenDatabaseIsDeleted() {
        testDatabase.createCollection("bobblehead", "horo")

        assertNotNull(testDatabase.getScope("horo"))

        testDatabase.delete()

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { testDatabase.getScope("horo") }
    }

    @Test
    fun testGetCollectionWhenDatabaseIsDeleted() {
        testDatabase.createCollection("bobblehead", "horo")

        assertNotNull(testDatabase.getCollection("bobblehead", "horo"))

        testDatabase.delete()

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            testDatabase.getCollection("bobblehead", "horo")
        }
    }

    //---------------------------------------------
    //  Collections, 8.10 Use Scope API when No Collections in the Scope
    //---------------------------------------------

    // 8.10.1: Test that after all collections in the scope are deleted,
    // calling the scope APIS returns the result as expected based
    // on section 6.5.
    @Test
    fun testUseScopeAPIAfterDeletingAllCollections() {
        val collection = testDatabase.createCollection(
            StringUtils.getUniqueName("test-collection", 4),
            StringUtils.getUniqueName("test-scope", 6)
        )


        val scope = testDatabase.getScope(collection.scope.name)
        assertNotNull(scope!!)

        // create more collections
        (0..4).map { StringUtils.getUniqueName("test-collection", 4) }
            .forEach { testDatabase.createCollection(it, scope.name) }

        val collectionNames = scope.collections.map { it.name }
        assertEquals(6, collectionNames.size)

        // verify that the collections exist
        collectionNames.forEach { assertNotNull(testDatabase.getCollection(it, scope.name)) }

        // delete the collections
        collectionNames.forEach { testDatabase.deleteCollection(it, scope.name) }

        // verify that the collections no longer exist
        collectionNames.forEach { assertNull(scope.getCollection(it)) }

        val collections = scope.collections
        assertNotNull(collections)
        assertTrue(collections.isEmpty())
    }

    // 8.10.2: Test that after all collections in the scope are deleted from
    // a different database instance, calling the scope APIS returns
    // the result as expected based on section 6.5. To test this,
    // get and retain the scope object before deleting all collections.
    @SlowTest
    @Test
    fun testUseScopeAPIAfterDeletingAllCollectionsFromDifferentDBInstance() {
        val collection = testDatabase.createCollection(
            StringUtils.getUniqueName("test-collection", 4),
            StringUtils.getUniqueName("test-scope", 6)
        )

        val scope = testDatabase.getScope(collection.scope.name)
        assertNotNull(scope!!)

        // create more collections
        (0..4).map { StringUtils.getUniqueName("test-collection", 4) }
            .forEach { testDatabase.createCollection(it, scope.name) }

        val collectionNames = scope.collections.map { it.name }
        assertEquals(6, collectionNames.size)

        // verify that the collections exist
        collectionNames.forEach { assertNotNull(testDatabase.getCollection(it, scope.name)) }

        // delete the collections from a different database
        Database(testDatabase.name).use { otherDatabase ->
            val otherScope = otherDatabase.getScope(scope.name)
            assertNotNull(otherScope!!)

            // verify that the collections exist in the other view of the db
            collectionNames.forEach { assertNotNull(otherDatabase.getCollection(it, scope.name)) }

            // delete the collections
            collectionNames.forEach { otherDatabase.deleteCollection(it, otherScope.name) }

            // verify that the collections no longer exist in the other db
            collectionNames.forEach { assertNull(otherDatabase.getCollection(it, otherScope.name)) }
            val otherCollections = otherScope.collections
            assertNotNull(otherCollections)
            assertTrue(otherCollections.isEmpty())

            // verify that the collections no longer exist in the original db
            collectionNames.forEach { assertNull(scope.getCollection(it)) }
            val collections = scope.collections
            assertNotNull(collections)
            assertTrue(collections.isEmpty())
        }
    }

    //---------------------------------------------
    //  ...
    //---------------------------------------------

    @Test
    fun testCompact() {
        val nDocs = 20
        val nUpdates = 25
        val docIds = createDocsInCollection(nDocs).map { it.id }

        // Update each doc 25 times:
        testDatabase.inBatch<CouchbaseLiteException> {
            docIds.forEach { docId ->
                var savedDoc = testCollection.getDocument(docId)
                for (i in 0 until nUpdates) {
                    val doc = savedDoc!!.toMutable()
                    doc.setValue("number", i)
                    savedDoc = saveDocInCollection(doc)
                }
            }
        }

        // Add a blog to each doc
        docIds.forEach { docId ->
            val doc = testCollection.getDocument(docId)!!.toMutable()
            doc.setValue("blob", Blob("text/plain", doc.id.toByteArray()))
            saveDocInCollection(doc)
        }
        assertEquals(nDocs.toLong(), testCollection.count)
        val attsDir = File(testDatabase.path, "Attachments")
        assertTrue(attsDir.exists())
        assertTrue(attsDir.isDirectory)
        assertEquals(nDocs, attsDir.listFiles()?.size ?: -1)

        // Compact:
        assertTrue(testDatabase.performMaintenance(MaintenanceType.COMPACT))
        assertEquals(nDocs, attsDir.listFiles()?.size ?: -1)

        // Delete all docs:
        docIds.forEach { docId ->
            testCollection.delete(testCollection.getDocument(docId)!!)
            assertNull(testCollection.getDocument(docId))
        }

        // Compact:
        assertTrue(testDatabase.performMaintenance(MaintenanceType.COMPACT))
        assertEquals(0, attsDir.listFiles()?.size ?: -1)
    }

    // REF: https://github.com/couchbase/couchbase-lite-android/issues/1231
    @Test
    fun testOverwriteDocWithNewDocInstance() {
        val docId = getUniqueName("conflict")

        var mDoc = MutableDocument(docId)
        mDoc.setValue("someKey", "someVal")
        saveDocInCollection(mDoc)

        // This is a conflict: last write should win
        mDoc = MutableDocument(docId)
        mDoc.setValue("someKey", "newVal")
        saveDocInCollection(mDoc)

        assertEquals(1, testCollection.count)

        val doc = testCollection.getDocument(docId)
        assertNotNull(doc)
        doc!!
        assertEquals("newVal", doc.getString("someKey"))
    }

    @Test
    fun testCopy() {
        val docIdField = getUniqueName("name")
        val dataField = getUniqueName("data")

        val n = testCollection.count

        val docs = createDocsInCollection(10)
        docs.forEach { doc ->
            val mDoc = doc.toMutable()
            mDoc.setValue(docIdField, doc.id)
            mDoc.setValue(dataField, Blob("text/plain", doc.id.toByteArray()))
            saveDocInCollection(mDoc)
        }

        val dbName = getUniqueName("test_copy_db")
        val config = testDatabase.getConfig()

        // Copy the db
        Database.copy(File(testDatabase.path!!), dbName, config)

        // Verify that it exists
        assertTrue(Database.exists(dbName, File(config.directory)))

        // Open it
        val otherDb = Database(dbName, config)
        assertNotNull(otherDb)
        try {
            val otherCollection = otherDb.getSimilarCollection(testCollection)
            assertNotNull(otherCollection)

            assertEquals(n + docs.size.toLong(), otherCollection.count)

            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
                .use { rs ->
                    for (r in rs) {
                        r.getString(docIdField) ?: continue // ignore spurious docs...

                        val docId = r.getString(0)  /// should be the same value as the above.
                        assertNotNull(docId!!)

                        val doc = otherCollection.getDocument(docId)
                        assertNotNull(doc!!)

                        assertEquals(docId, doc.getString(docIdField))
                        val blob = doc.getBlob("dataField")
                        assertNotNull(blob!!)

                        assertEquals(docId, String(blob.content!!))
                    }
                }
        } finally {
            eraseDb(otherDb)
        }
    }

    @Test
    fun testCreateIndex() {
        assertEquals(0, testCollection.indexes.size)

        testCollection.createIndex(
            "index1",
            IndexBuilder.valueIndex(
                ValueIndexItem.property("firstName"),
                ValueIndexItem.property("lastName")
            )
        )
        assertEquals(1, testCollection.indexes.size)

        // Create FTS index:
        testCollection.createIndex("index2", IndexBuilder.fullTextIndex(FullTextIndexItem.property("detail")))
        assertEquals(2, testCollection.indexes.size)

        testCollection.createIndex(
            "index3",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("es-detail")).ignoreAccents(true).setLanguage("es")
        )
        assertEquals(3, testCollection.indexes.size)

        // Create value index with expression() instead of property()
        testCollection.createIndex(
            "index4",
            IndexBuilder.valueIndex(
                ValueIndexItem.expression(Expression.property("firstName")),
                ValueIndexItem.expression(Expression.property("lastName"))
            )
        )
        assertEquals(4, testCollection.indexes.size)

        assertContents(testCollection.indexes, "index1", "index2", "index3", "index4")
    }

    @Test
    fun testCreateIndexWithConfig() {
        assertEquals(0, testCollection.indexes.size)

        testCollection.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, testCollection.indexes.size)

        testCollection.createIndex(
            "index2",
            FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es")
        )
        assertEquals(2, testCollection.indexes.size)

        assertContents(testCollection.indexes, "index1", "index2")
    }

    @Test
    fun testIndexBuilderEmptyArg1() {
        assertThrows(IllegalArgumentException::class.java) { IndexBuilder.fullTextIndex() }
    }

    @Test
    fun testIndexBuilderEmptyArg2() {
        assertThrows(IllegalArgumentException::class.java) { IndexBuilder.valueIndex() }
    }

    @Test
    fun testCreateSameIndexTwice() {
        // Create index with first name:
        val index: Index = IndexBuilder.valueIndex(ValueIndexItem.property("firstName"))
        testCollection.createIndex("myindex", index)
        assertEquals(1, testCollection.indexes.size)

        // Call create index again:
        testCollection.createIndex("myindex", index)
        assertEquals(1, testCollection.indexes.size)

        assertContents(testCollection.indexes, "myindex")
    }

    @Test
    fun testCreateSameNameIndexes() {
        // Create value index with first name:
        testCollection.createIndex("myindex", IndexBuilder.valueIndex(ValueIndexItem.property("firstName")))
        assertEquals(1, testCollection.indexes.size)

        // Create value index with last name:
        testCollection.createIndex("myindex", IndexBuilder.valueIndex(ValueIndexItem.property("lastName")))
        assertEquals(1, testCollection.indexes.size)

        assertContents(testCollection.indexes, "myindex")

        // Create FTS index:
        testCollection.createIndex("myindex", IndexBuilder.fullTextIndex(FullTextIndexItem.property("detail")))
        assertEquals(1, testCollection.indexes.size)

        assertContents(testCollection.indexes, "myindex")
    }

    @Test
    fun testDeleteIndex() {
        createTestIndexes()

        // Delete indexes:
        testCollection.deleteIndex("index4")
        assertEquals(3, testCollection.indexes.size)
        assertContents(testCollection.indexes, "index1", "index2", "index3")

        testCollection.deleteIndex("index1")
        assertEquals(2, testCollection.indexes.size)
        assertContents(testCollection.indexes, "index2", "index3")

        testCollection.deleteIndex("index2")
        assertEquals(1, testCollection.indexes.size)
        assertContents(testCollection.indexes, "index3")

        testCollection.deleteIndex("index3")
        assertEquals(0, testCollection.indexes.size)
        assertTrue(testCollection.indexes.isEmpty())
    }

    @Test
    fun testDeleteNonexistentIndex() {
        createTestIndexes()

        // Delete non existing index:
        testCollection.deleteIndex("dummy")
    }

    @Test
    fun testDeleteDeletedIndexes() {
        createTestIndexes()
        assertEquals(4, testCollection.indexes.size)

        testCollection.deleteIndex("index1")
        testCollection.deleteIndex("index2")
        testCollection.deleteIndex("index3")
        testCollection.deleteIndex("index4")
        assertEquals(0, testCollection.indexes.size)

        // Delete deleted indexes:
        testCollection.deleteIndex("index1")
        testCollection.deleteIndex("index2")
        testCollection.deleteIndex("index3")
        testCollection.deleteIndex("index4")
    }

    @Test
    fun testRebuildIndex() {
        createTestIndexes()
        assertTrue(testDatabase.performMaintenance(MaintenanceType.REINDEX))
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1416
    @Test
    fun testDeleteAndOpenDB() {
        var db: Database? = null
        try {
            // open a database
            db = createDb("del_open_db")
            val dbDir = db.path
            val dbName = db.name

            // delete it
            db.delete()
            assertFalse(Database.exists(dbName, File(dbDir!!)))

            // open it again
            db = Database(dbName)

            // insert documents
            val coll = db.createCollection("del_open_db")
            db.inBatch(UnitOfWork<CouchbaseLiteException> { createDocsInCollection(100, collection = coll) })

            // close db again
            db.close()
        } finally {
            eraseDb(db)
        }
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

        val expected = mapOf("firstName" to "Daniel", "lastName" to "Tiger", "age" to 20L)
        assertEquals(expected, doc.toMap())

        val savedDoc = testCollection.getDocument(doc.id)
        assertEquals(expected, savedDoc!!.toMap())
        assertEquals(3, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithConflictLastWrite() {
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
    }

    @Test
    fun testSaveDocWithConflictFail() {
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testDeleteDocWithConflictLastWrite() {
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
    }

    @Test
    fun testDeleteDocWithConflictFail() {
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testSaveDocWithNoParentConflictLastWrite() {
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
    }

    @Test
    fun testSaveDocWithNoParentConflictFail() {
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testSaveDocWithDeletedConflictLastWrite() {
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
    }

    @Test
    fun testSaveDocWithDeletedConflictFail() {
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testDeleteAndUpdateDoc() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        testCollection.save(doc)

        testCollection.delete(doc)
        assertEquals(2, doc.sequence)

        assertNull(testCollection.getDocument(doc.id))

        doc.setString("firstName", "Scott")
        testCollection.save(doc)
        assertEquals(3, doc.sequence)

        val expected = mapOf("firstName" to "Scott", "lastName" to "Tiger")
        assertEquals(expected, doc.toMap())

        val savedDoc = testCollection.getDocument(doc.id)
        assertNotNull(savedDoc)
        assertEquals(expected, savedDoc!!.toMap())
    }

    @Test
    fun testDeleteAlreadyDeletedDoc() {
        val doc = createDocInCollection()

        // Get two doc1 document objects (doc1a and doc1b):
        val docA = testCollection.getDocument(doc.id)
        val docB = testCollection.getDocument(doc.id)!!.toMutable()

        // Delete doc1a:
        testCollection.delete(docA!!)
        assertEquals(2, docA.sequence)
        assertNull(testCollection.getDocument(doc.id))

        // Delete doc1b:
        testCollection.delete(docB)
        assertEquals(2, docB.sequence)
        assertNull(testCollection.getDocument(doc.id))
    }

    @Test
    fun testDeleteNonExistingDoc() {
        val docA = createDocInCollection()
        val docB = testCollection.getDocument(docA.id)

        // purge doc
        testCollection.purge(docA)
        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(docA.id))

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { testCollection.delete(docA) }
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { testCollection.delete(docB!!) }
        assertEquals(0, testCollection.count)

        assertNull(testCollection.getDocument(docB!!.id))
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1652
    @Test
    fun testDeleteWithOldDocInstance() {
        // 1. save
        val docA = MutableDocument().setBoolean("updated", false)
        testCollection.save(docA)

        val docB = testCollection.getDocument(docA.id)
        testCollection.save(docB?.toMutable()?.setBoolean("updated", true)!!)

        // 3. delete by previously retrieved document
        testCollection.delete(docB)
        assertNull(testCollection.getDocument("doc"))
    }

    // The following four tests verify, explicitly, the code that
    // mitigates the 2.8.0 bug (CBL-1408)
    // There is one more test for this in DatabaseEncryptionTest

    // Verify that a db can be reopened
    @Test
    fun testReOpenExistingDb() {
        val dbName = getUniqueName("test_db")
        var db: Database? = null
        try {
            db = Database(dbName)
            val mDoc = MutableDocument()
            mDoc.setString("foo", "bar")

            var coll = db.createTestCollection()
            val colName = coll.name
            val colScope = coll.scope.name

            coll.save(mDoc)

            db.close()

            val config = DatabaseConfiguration()
            config.directory = CouchbaseLiteInternal.getDefaultDbDir().absolutePath
            assertFalse(config.directory.endsWith(".couchbase"))
            db = Database(dbName, config)
            coll = db.getCollection(colName, colScope)!!

            assertEquals(1L, coll.count)
            assertEquals("bar", coll.getDocument(mDoc.id)?.getString("foo"))
        } finally {
            eraseDb(db)
        }
    }

    @Test
    fun testReOpenExisting2Dot8DotOhDb() {
        val twoDot8DotOhDirPath = File(CouchbaseLiteInternal.getDefaultDbDir(), ".couchbase").canonicalPath
        val dbName = getUniqueName("two_dot_oh_test_db")
        var db: Database? = null
        try {
            // Create a database in the misguided 2.8.0 directory
            val config = DatabaseConfiguration()
            config.directory = twoDot8DotOhDirPath
            db = Database(dbName, config)

            var coll = db.createTestCollection()
            val colName = coll.name
            val colScope = coll.scope.name

            val mDoc = MutableDocument()
            mDoc.setString("foo", "bar")
            coll.save(mDoc)

            db.close()

            // This should open the database and collection created above
            // (the db should be copied out of the .couchbase directory)
            db = Database(dbName)
            coll = db.getCollection(colName, colScope)!!
            assertEquals(1L, coll.count)

            assertEquals("bar", coll.getDocument(mDoc.id)?.getString("foo"))
        } finally {
            FileUtils.eraseFileOrDir(twoDot8DotOhDirPath)
            eraseDb(db)
        }
    }

    @Test
    fun testReOpenExisting2Dot8DotOhDbCopyFails() {
        val twoDot8DotOhDirPath = File(CouchbaseLiteInternal.getDefaultDbDir(), ".couchbase").canonicalPath
        val dbName = getUniqueName("two_dot_oh_test_db")
        var db: Database? = null
        try {
            // Create a database in the misguided 2.8.0 directory
            val config = DatabaseConfiguration()
            config.directory = twoDot8DotOhDirPath
            db = Database(dbName, config)
            val coll = db.createTestCollection()

            val mDoc = MutableDocument()
            mDoc.setString("foo", "bar")
            coll.save(mDoc)

            db.close()

            val twoDot8DotOhDir = File(twoDot8DotOhDirPath)
            val dbFile = C4Database.getDatabaseFile(twoDot8DotOhDir, dbName)
            assertTrue(dbFile.exists())
            FileUtils.deleteContents(dbFile)

            // this should try to copy the db created above
            // it should fail because that isn't really a db anymore
            try {
                db = Database(dbName)
                fail("DB open should have thrown an exception")
            } catch (ignore: CouchbaseLiteException) {
            }

            // the (un-copyable) 2.8.0 db should still exist
            assertTrue(dbFile.exists())

            // the copy should not exist
            assertFalse(C4Database.getDatabaseFile(CouchbaseLiteInternal.getDefaultDbDir(), dbName).exists())
        } finally {
            FileUtils.eraseFileOrDir(twoDot8DotOhDirPath)
            eraseDb(db)
        }
    }

    @Test
    fun testReOpenExistingLegacyAnd2Dot8DotOhDb() {
        val twoDot8DotOhDirPath = File(CouchbaseLiteInternal.getDefaultDbDir(), ".couchbase").canonicalPath
        val dbName = getUniqueName("two_dot_oh_test_db")
        var db: Database? = null
        try {
            db = Database(dbName)
            var coll = db.createTestCollection()
            val colName = coll.name
            val colScope = coll.scope.name

            val mDoc1 = MutableDocument()
            mDoc1.setString("foo", "bar")
            coll.save(mDoc1)

            db.close()

            // Create a database in the misguided 2.8.0 directory
            val config = DatabaseConfiguration()
            config.directory = twoDot8DotOhDirPath
            db = Database(dbName, config)
            coll = db.createTestCollection(colName, colScope)

            val mDoc2 = MutableDocument()
            mDoc2.setString("foo", "baz")
            coll.save(mDoc2)

            db.close()

            // This should open the first database created above
            // The 2.8.0 directory should not have been copied over it
            db = Database(dbName)
            coll = db.getCollection(colName, colScope)!!
            assertEquals(1L, coll.count)
            val doc = coll.getDocument(mDoc1.id)
            assertEquals("bar", doc!!.getString("foo"))
        } finally {
            FileUtils.eraseFileOrDir(twoDot8DotOhDirPath)
            eraseDb(db)
        }
    }

    @Test
    fun testSetDocumentExpirationHangInBatch() {
        val docIds = createDocsInCollection(13).map { it.id }

        val now = Date().time
        val tomorrow = Date(now + 1000L * 60 * 60 * 24) // now + 1 day

        val future = FutureTask {
            testDatabase.inBatch<CouchbaseLiteException> {
                docIds.forEach { docId ->
                    val doc = testCollection.getDocument(docId)!!.toMutable()
                    doc.setString("expiration", tomorrow.toString())
                    testCollection.save(doc)
                    testCollection.setDocumentExpiration(doc.id, tomorrow)
                }
            }
        }
        testSerialExecutor.execute(future)
        try {
            future.get(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw AssertionError("Batch execution failed", e)
        }

        docIds.forEach { docId ->
            assertEquals(tomorrow.toString(), testCollection.getDocument(docId)!!.getString("expiration"))
            assertEquals(tomorrow, testCollection.getDocumentExpiration(docId))
        }
    }

    @Test
    fun testDeleteSameDocTwice() {
        // Store doc:
        val docID = "doc1"
        val doc: Document = saveDocInTestCollection(MutableDocument(docID))

        // First time deletion:
        testCollection.delete(doc)
        assertEquals(0, testCollection.count)
        assertNull(testCollection.getDocument(docID))

        // Second time deletion:
        // NOTE: doc is pointing to old revision. This causes a conflict but generates the same revision
        testCollection.delete(doc)
        assertNull(testCollection.getDocument(docID))
    }

    // -- DatabaseTest
    @Test
    fun testDeleteUnsavedDocument() {
        val doc = MutableDocument("doc1")
        doc.setValue("name", "Scott Tiger")
        try {
            testCollection.delete(doc)
            fail()
        } catch (e: CouchbaseLiteException) {
            if (e.code != CBLError.Code.NOT_FOUND) {
                fail()
            }
        }
        assertEquals("Scott Tiger", doc.getValue("name"))
    }

    @Test
    fun testSaveSavedMutableDocument() {
        val mDoc = MutableDocument("doc1")
        mDoc.setValue("name", "Scott Tiger")
        val doc1 = saveDocInTestCollection(mDoc)
        mDoc.setValue("age", 20)
        val doc2 = saveDocInTestCollection(mDoc)
        assertEquals(1, doc2.compareAge(doc1))
        assertEquals(20, doc2.getInt("age"))
        assertEquals("Scott Tiger", doc2.getString("name"))
    }

    @Test
    fun testDeleteSavedMutableDocument() {
        val doc = MutableDocument("doc1")
        doc.setValue("name", "Scott Tiger")
        saveDocInTestCollection(doc)
        testCollection.delete(doc)
        assertNull(testCollection.getDocument("doc1"))
    }

    @Test
    fun testDeleteDocAfterPurgeDoc() {
        val doc = MutableDocument("doc1")
        doc.setValue("name", "Scott Tiger")
        val saved: Document = saveDocInTestCollection(doc)

        // purge doc
        testCollection.purge(saved)
        try {
            testCollection.delete(saved)
            fail()
        } catch (e: CouchbaseLiteException) {
            assertEquals(CBLError.Code.NOT_FOUND, e.code)
        }
    }

    @Test
    fun testDeleteDocAfterDeleteDoc() {
        val doc = MutableDocument("doc1")
        doc.setValue("name", "Scott Tiger")
        val saved: Document = saveDocInTestCollection(doc)

        // delete doc
        testCollection.delete(saved)

        // delete doc -> conflict resolver -> no-op
        testCollection.delete(saved)
    }

    @Test
    fun testPurgeDocAfterDeleteDoc() {
        val doc = MutableDocument("doc1")
        doc.setValue("name", "Scott Tiger")
        val saved: Document = saveDocInTestCollection(doc)

        // delete doc
        testCollection.delete(saved)

        // purge doc
        testCollection.purge(saved)
    }

    @Test
    fun testPurgeDocAfterPurgeDoc() {
        val doc = MutableDocument("doc1")
        doc.setValue("name", "Scott Tiger")
        val saved: Document = saveDocInTestCollection(doc)

        // purge doc
        testCollection.purge(saved)
        try {
            testCollection.purge(saved)
            fail()
        } catch (e: CouchbaseLiteException) {
            if (e.code != CBLError.Code.NOT_FOUND) {
                fail()
            }
        }
    }


    /////////////////////////////////   H E L P E R S   //////////////////////////////////////

    private fun saveDocInTestCollection(doc: MutableDocument): Document {
        return saveDocInCollection(doc, testCollection)
    }

    // helper method to create a bunch of indices.
    private fun createTestIndexes() {
        testCollection.createIndex(
            "index1",
            IndexBuilder.valueIndex(ValueIndexItem.property("firstName"), ValueIndexItem.property("lastName"))
        )

        // Create FTS index:
        testCollection.createIndex("index2", IndexBuilder.fullTextIndex(FullTextIndexItem.property("detail")))

        testCollection.createIndex(
            "index3",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("es-detail")).ignoreAccents(true).setLanguage("es")
        )

        testCollection.createIndex(
            "index4",
            IndexBuilder.valueIndex(
                ValueIndexItem.expression(Expression.property("firstName")),
                ValueIndexItem.expression(Expression.property("lastName"))
            )
        )
    }

    // helper method to purge doc and verify doc.
    private fun purgeDocAndVerify(doc: Document) {
        testCollection.purge(doc)
        assertNull(testCollection.getDocument(doc.id))
    }

    private fun testSaveDocWithConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
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

        val expected = mapOf("firstName" to "Scott", "lastName" to "Tiger", "nickName" to "Scotty")
        assertEquals(expected, doc1a.toMap())
        assertEquals(3, doc1a.sequence)

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        when (cc) {
            ConcurrencyControl.LAST_WRITE_WINS -> {
                assertTrue(testCollection.save(doc1b, cc))
                val savedDoc = testCollection.getDocument(doc.id)
                assertEquals(doc1b.toMap(), savedDoc!!.toMap())
                assertEquals(4, savedDoc.sequence)
            }
            ConcurrencyControl.FAIL_ON_CONFLICT -> {
                assertFalse(testCollection.save(doc1b, cc))
                val savedDoc = testCollection.getDocument(doc.id)
                assertEquals(expected, savedDoc!!.toMap())
                assertEquals(3, savedDoc.sequence)
            }
        }
    }

    private fun testDeleteDocWithConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
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
        val expected = mapOf("firstName" to "Scott", "lastName" to "Tiger")
        assertEquals(expected, doc1a.toMap())
        assertEquals(2, doc1a.sequence)

        // Modify doc1b and delete.  This results in a conflict when deleted:
        doc1b.setString("lastName", "Lion")
        when (cc) {
            ConcurrencyControl.LAST_WRITE_WINS -> {
                assertTrue(testCollection.delete(doc1b, cc))
                assertEquals(3, doc1b.sequence)
                assertNull(testCollection.getDocument(doc1b.id))
            }
            ConcurrencyControl.FAIL_ON_CONFLICT -> {
                assertFalse(testCollection.delete(doc1b, cc))
                val savedDoc = testCollection.getDocument(doc.id)
                assertEquals(expected, savedDoc!!.toMap())
                assertEquals(2, savedDoc.sequence)
            }
        }
    }

    private fun testSaveDocWithNoParentConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
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
        when (cc) {
            ConcurrencyControl.LAST_WRITE_WINS -> {
                assertTrue(testCollection.save(doc1b, cc))
                savedDoc = testCollection.getDocument(doc1b.id)
                assertEquals(doc1b.toMap(), savedDoc!!.toMap())
                assertEquals(2, savedDoc.sequence)
            }
            ConcurrencyControl.FAIL_ON_CONFLICT -> {
                assertFalse(testCollection.save(doc1b, cc))
                savedDoc = testCollection.getDocument(doc1b.id)
                assertEquals(doc1a.toMap(), savedDoc!!.toMap())
                assertEquals(1, savedDoc.sequence)
            }
        }
    }

    private fun testSaveDocWithDeletedConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
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
        when (cc) {
            ConcurrencyControl.LAST_WRITE_WINS -> {
                assertTrue(testCollection.save(doc1b, cc))
                val savedDoc = testCollection.getDocument(doc.id)
                assertEquals(doc1b.toMap(), savedDoc!!.toMap())
                assertEquals(3, savedDoc.sequence)
            }
            ConcurrencyControl.FAIL_ON_CONFLICT -> {
                assertFalse(testCollection.save(doc1b, cc))
                assertNull(testCollection.getDocument(doc.id))
            }
        }
    }
}
