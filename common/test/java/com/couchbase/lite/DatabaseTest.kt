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
@file:Suppress("DEPRECATION")

package com.couchbase.lite

import com.couchbase.lite.internal.CouchbaseLiteInternal
import com.couchbase.lite.internal.core.C4Database
import com.couchbase.lite.internal.utils.FileUtils
import com.couchbase.lite.internal.utils.SlowTest
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
// baseTestDb is managed by the superclass
// If a test creates a new database it guarantees that it is deleted.
// If a test opens a copy of the baseTestDb, it close (but does NOT delete) it
class DatabaseTest : LegacyBaseDbTest() {
    //---------------------------------------------
    //  Get Document
    //---------------------------------------------
    @Test
    fun testGetNonExistingDocWithID() {
        assertNull(baseTestDb.getDocument("non-exist"))
    }

    @Test
    fun testGetExistingDocWithID() {
        val docID = "doc1"
        createSingleDocInBaseTestDb(docID)
        verifyGetDocument(docID, 1)
    }


    @SlowTest
    @Test
    fun testGetExistingDocWithIDFromDifferentDBInstance() {
        // store doc
        val docID = "doc1"
        createSingleDocInBaseTestDb(docID)

        // open db with same db name and default option
        val otherDb = duplicateBaseTestDb()
        otherDb.use {
            assertNotSame(baseTestDb, otherDb)

            // get doc from other DB.
            assertEquals(1, otherDb.count)
            verifyGetDocument(otherDb, docID, 1)
        }
    }

    @Test
    fun testGetExistingDocWithIDInBatch() {
        // Save 10 docs:
        val n = 13
        createDocsInBaseTestDb(n)
        baseTestDb.inBatch<RuntimeException> { verifyDocuments(n) }
    }

    @Test(expected = IllegalStateException::class)
    fun testGetDocFromClosedDB() {
        // Store doc:
        createSingleDocInBaseTestDb("doc1")

        // Close db:
        baseTestDb.close()

        // should fail
        baseTestDb.getDocument("doc1")
    }

    @Test(expected = IllegalStateException::class)
    fun testGetDocFromDeletedDB() {
        // Store doc:
        createSingleDocInBaseTestDb("doc1")

        // Delete db:
        baseTestDb.delete()

        // should fail
        baseTestDb.getDocument("doc1")
    }

    //---------------------------------------------
    //  Save Document
    //---------------------------------------------

    @Test
    fun testSaveNewDocWithID() {
        testSaveNewDocWithID("doc1")
    }

    @Test
    fun testSaveNewDocWithSpecialCharactersDocID() {
        testSaveNewDocWithID("`~@#$%^&*()_+{}|\\\\][=-/.,<>?\\\":;'")
    }

    @Test
    fun testSaveAndGetMultipleDocs() {
        val nDocs = 11
        for (i in 0 until nDocs) {
            val doc = MutableDocument("doc_${i}")
            doc.setValue("key", i)
            saveDocInBaseTestDb(doc)
        }
        assertEquals(nDocs.toLong(), baseTestDb.count)
        verifyDocuments(nDocs)
    }

    @Test
    fun testSaveDoc() {
        // store doc
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID).toMutable()

        // update doc
        doc.setValue("key", 2)
        saveDocInBaseTestDb(doc)
        assertEquals(1, baseTestDb.count)

        // validate document by getDocument
        verifyGetDocument(docID, 2)
    }

    @SlowTest
    @Test
    fun testSaveDocInDifferentDBInstance() {
        // Store doc
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID).toMutable()

        // Create db with default
        val otherDb = duplicateBaseTestDb()
        otherDb.use {
            assertNotSame(otherDb, baseTestDb)
            assertEquals(1, otherDb.count)

            // Update doc & store it into different instance
            doc.setValue("key", 2)
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherDb.save(doc) }
        }
    }

    @Test
    fun testSaveDocInDifferentDB() {
        // Store doc
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID).toMutable()

        // Create db with default
        val otherDb = openDatabase()
        try {
            assertNotSame(otherDb, baseTestDb)
            assertEquals(0, otherDb.count)

            // Update doc & store it into different instance
            doc.setValue("key", 2)
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherDb.save(doc) }
        } finally {
            // delete otherDb
            eraseDb(otherDb)
        }
    }

    @Test
    fun testSaveSameDocTwice() {
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID).toMutable()
        assertEquals(docID, saveDocInBaseTestDb(doc).id)
        assertEquals(1, baseTestDb.count)
    }

    @Test
    fun testSaveInBatch() {
        val nDocs = 17
        baseTestDb.inBatch<CouchbaseLiteException> { createDocsInBaseTestDb(nDocs) }
        assertEquals(nDocs.toLong(), baseTestDb.count)
        verifyDocuments(nDocs)
    }

    @Test(expected = IllegalStateException::class)
    fun testSaveDocToClosedDB() {
        baseTestDb.close()
        val doc = MutableDocument("doc1")
        doc.setValue("key", 1)
        saveDocInBaseTestDb(doc)
    }

    @Test(expected = IllegalStateException::class)
    fun testSaveDocToDeletedDB() {
        // Delete db:
        baseTestDb.delete()
        val doc = MutableDocument("doc1")
        doc.setValue("key", 1)
        saveDocInBaseTestDb(doc)
    }

    //---------------------------------------------
    //  Delete Document
    //---------------------------------------------
    @Test
    fun testDeletePreSaveDoc() {
        val doc = MutableDocument("doc1")
        doc.setValue("key", 1)
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { baseTestDb.delete(doc) }
    }

    @Test
    fun testDeleteDoc() {
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)
        assertEquals(1, baseTestDb.count)
        baseTestDb.delete(doc)
        assertEquals(0, baseTestDb.count)
        assertNull(baseTestDb.getDocument(docID))
    }


    @SlowTest
    @Test
    fun testDeleteDocInDifferentDBInstance() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)

        // Create db with same name:
        // Create db with default
        val otherDb = duplicateBaseTestDb()
        otherDb.use {
            assertNotSame(otherDb, baseTestDb)
            assertEquals(1, otherDb.count)

            // Delete from the different db instance:
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherDb.delete(doc) }
        }
    }

    @Test
    fun testDeleteDocInDifferentDB() {
        // Store doc
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)

        // Create db with default
        val otherDb = openDatabase()
        try {
            assertNotSame(otherDb, baseTestDb)

            // Delete from the different db:
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherDb.delete(doc) }
        } finally {
            eraseDb(otherDb)
        }
    }

    @Test
    fun testDeleteDocInBatch() {
        val nDocs = 10

        // Save 10 docs:
        createDocsInBaseTestDb(nDocs)
        baseTestDb.inBatch<CouchbaseLiteException> {
            for (i in 0 until nDocs) {
                val docID = "doc_${i}"
                val doc = baseTestDb.getDocument(docID)
                baseTestDb.delete(doc!!)
                assertNull(baseTestDb.getDocument(docID))
                assertEquals((9 - i).toLong(), baseTestDb.count)
            }
        }
        assertEquals(0, baseTestDb.count)
    }

    @Test(expected = IllegalStateException::class)
    fun testDeleteDocOnClosedDB() {
        // Store doc:
        val doc = createSingleDocInBaseTestDb("doc1")

        // Close db:
        baseTestDb.close()

        // Delete doc from db:
        baseTestDb.delete(doc)
    }

    @Test(expected = IllegalStateException::class)
    fun testDeleteDocOnDeletedDB() {
        // Store doc:
        val doc = createSingleDocInBaseTestDb("doc1")
        baseTestDb.delete()

        // Delete doc from db:
        baseTestDb.delete(doc)
    }

    //---------------------------------------------
    //  Purge Document
    //---------------------------------------------
    @Test
    fun testPurgePreSaveDoc() {
        val doc = MutableDocument("doc1")
        assertEquals(0, baseTestDb.count)
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { baseTestDb.purge(doc) }
        assertEquals(0, baseTestDb.count)
    }

    @Test
    fun testPurgeDoc() {
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)

        // Purge Doc
        purgeDocAndVerify(doc)
        assertEquals(0, baseTestDb.count)
    }

    @SlowTest
    @Test
    fun testPurgeDocInDifferentDBInstance() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)

        // Create db with default:
        val otherDb = duplicateBaseTestDb()
        otherDb.use {
            assertNotSame(otherDb, baseTestDb)
            assertEquals(1, otherDb.count)

            // purge document against other db instance:
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherDb.purge(doc) }
        }
    }

    @Test
    fun testPurgeDocInDifferentDB() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)

        // Create db with default:
        val otherDb = openDatabase()
        try {
            assertNotSame(otherDb, baseTestDb)
            assertEquals(0, otherDb.count)

            // Purge document against other db:
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.INVALID_PARAMETER) { otherDb.purge(doc) }
        } finally {
            eraseDb(otherDb)
        }
    }

    @Test
    fun testPurgeSameDocTwice() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID)

        // Get the document for the second purge:
        val doc1 = baseTestDb.getDocument(docID)

        // Purge the document first time:
        purgeDocAndVerify(doc)
        assertEquals(0, baseTestDb.count)

        // Purge the document second time:
        purgeDocAndVerify(doc1)
        assertEquals(0, baseTestDb.count)
    }

    @Test
    fun testPurgeDocInBatch() {
        val nDocs = 10
        // Save 10 docs:
        createDocsInBaseTestDb(nDocs)
        baseTestDb.inBatch<CouchbaseLiteException> {
            for (i in 0 until nDocs) {
                val docID = "doc_${i}"
                val doc = baseTestDb.getDocument(docID)
                purgeDocAndVerify(doc)
                assertEquals((9 - i).toLong(), baseTestDb.count)
            }
        }
        assertEquals(0, baseTestDb.count)
    }

    @Test(expected = IllegalStateException::class)
    fun testPurgeDocOnClosedDB() {
        // Store doc:
        val doc = createSingleDocInBaseTestDb("doc1")

        // Close db:
        baseTestDb.close()

        // Purge doc:
        baseTestDb.purge(doc)
    }

    @Test(expected = IllegalStateException::class)
    fun testPurgeDocOnDeletedDB() {
        // Store doc:
        val doc = createSingleDocInBaseTestDb("doc1")

        // Close db:
        baseTestDb.close()

        // Purge doc:
        baseTestDb.purge(doc)
    }

    //---------------------------------------------
    //  Close Database
    //---------------------------------------------
    @Test
    fun testClose() {
        baseTestDb.close()
    }

    @Test
    fun testCloseTwice() {
        baseTestDb.close()
        baseTestDb.close()
    }

    @Test
    fun testCloseThenAccessDoc() {
        // Store doc:
        val docID = "doc1"
        val mDoc = MutableDocument(docID)
        mDoc.setInt("key", 1)
        val mDict = MutableDictionary() // nested dictionary
        mDict.setString("hello", "world")
        mDoc.setDictionary("dict", mDict)
        val doc = saveDocInBaseTestDb(mDoc)

        // Close db:
        baseTestDb.close()

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.id)
        assertEquals(1, (doc.getValue("key") as Number?)!!.toInt().toLong())
        val dict = doc.getDictionary("dict")
        assertNotNull(dict)
        assertEquals("world", dict!!.getString("hello"))
        val updateDoc = doc.toMutable()
        updateDoc.setValue("key", 2)
        updateDoc.setValue("key1", "value")
        assertEquals(2, updateDoc.getInt("key").toLong())
        assertEquals("value", updateDoc.getString("key1"))
    }

    @Test
    fun testCloseThenAccessBlob1() {
        // Store doc with blob:
        val mDoc = createSingleDocInBaseTestDb("doc1").toMutable()
        mDoc.setValue("blob", Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8)))
        val doc = saveDocInBaseTestDb(mDoc)

        // Close db:
        baseTestDb.close()

        // content should be accessible & modifiable without error
        val blob = doc.getBlob("blob")
        assertNotNull(blob)
        assertEquals(BLOB_CONTENT.length.toLong(), blob!!.length())
    }

    @Test(expected = IllegalStateException::class)
    fun testCloseThenAccessBlob2() {
        // Store doc with blob:
        val mDoc = createSingleDocInBaseTestDb("doc1").toMutable()
        mDoc.setValue("blob", Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8)))
        val doc = saveDocInBaseTestDb(mDoc)

        // Close db:
        baseTestDb.close()

        // content should be accessible & modifiable without error
        val blob = doc.getBlob("blob")
        assertNotNull(blob)

        // trying to get the content, however, should fail
        blob!!.content
    }

    @Test
    fun testCloseThenGetDatabaseName() {
        val dbName = baseTestDb.name
        baseTestDb.close()
        assertEquals(dbName, baseTestDb.name)
    }

    @Test
    fun testCloseThenGetDatabasePath() {
        baseTestDb.close()
        assertNull(baseTestDb.path)
    }

    @Test(expected = IllegalStateException::class)
    fun testCloseThenCallInBatch() {
        baseTestDb.close()
        baseTestDb.inBatch<RuntimeException> { fail() }
    }

    @Test
    fun testCloseInInBatch() {
        baseTestDb.inBatch<RuntimeException> {
            // delete db
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.TRANSACTION_NOT_CLOSED) {
                baseTestDb.close()
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun testCloseThenDeleteDatabase() {
        baseTestDb.close()
        baseTestDb.delete()
    }

    //---------------------------------------------
    //  Delete Database
    //---------------------------------------------
    @Test
    fun testDelete() {
        baseTestDb.delete()
    }

    @Test(expected = IllegalStateException::class)
    fun testDeleteTwice() {
        val path = File(baseTestDb.path!!)
        assertTrue(path.exists())

        // delete db
        baseTestDb.delete()
        assertFalse(path.exists())

        // second delete should fail
        baseTestDb.delete()
    }

    @Test
    fun testDeleteThenAccessDoc() {
        // Store doc:
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID).toMutable()

        // Delete db:
        baseTestDb.delete()

        // Content should be accessible & modifiable without error:
        assertEquals(docID, doc.id)
        assertEquals(1, (doc.getValue("key") as Number).toInt().toLong())
        doc.setValue("key", 2)
        doc.setValue("key1", "value")
    }

    @Test
    fun testDeleteThenAccessBlob() {
        // Store doc with blob:
        val docID = "doc1"
        val doc = createSingleDocInBaseTestDb(docID).toMutable()
        doc.setValue("blob", Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8)))
        saveDocInBaseTestDb(doc)

        // Delete db:
        baseTestDb.delete()

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
        val dbName = baseTestDb.name

        // delete db
        baseTestDb.delete()
        assertEquals(dbName, baseTestDb.name)
    }

    @Test
    fun testDeleteThenGetDatabasePath() {
        // delete db
        baseTestDb.delete()
        assertNull(baseTestDb.path)
    }

    @Test(expected = IllegalStateException::class)
    fun testDeleteThenCallInBatch() {
        baseTestDb.delete()
        baseTestDb.inBatch<RuntimeException> { fail() }
    }

    @Test
    fun testDeleteInInBatch() {
        baseTestDb.inBatch<RuntimeException> {
            // delete db
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.TRANSACTION_NOT_CLOSED) {
                baseTestDb.close()
            }
        }
    }

    @SlowTest
    @Test
    fun testDeleteDBOpenedByOtherInstance() {
        val otherDb = duplicateBaseTestDb()
        otherDb.use {
            assertEquals(0, otherDb.count)
            assertNotSame(baseTestDb, otherDb)

            // delete db
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.BUSY) { baseTestDb.delete() }
        }
    }

    //---------------------------------------------
    //  Delete Database (static)
    //---------------------------------------------
    @Test
    fun testDeleteWithDefaultDirDB() {
        val dbName = baseTestDb.name

        val path = File(baseTestDb.path!!)
        assertNotNull(path)
        assertTrue(path.exists())

        // close db before delete
        baseTestDb.close()
        Database.delete(dbName, null)
        assertFalse(path.exists())
    }

    @SlowTest
    @Test
    fun testDeleteOpenDbWithDefaultDir() {
        val path = File(baseTestDb.path!!)
        assertNotNull(path)
        assertTrue(path.exists())

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.BUSY) {
            Database.delete(baseTestDb.name, null)
        }
    }

    @Test
    fun testStaticDeleteDb() {
        val dbDirPath = getScratchDirectoryPath(getUniqueName("static-delete-dir"))

        // create db in a custom directory
        val db = createDb("static_del_db", DatabaseConfiguration().setDirectory(dbDirPath))
        try {
            val dbName = db.name
            val dbPath = File(db.path!!)
            assertTrue(dbPath.exists())

            // close db before delete
            db.close()
            Database.delete(dbName, File(dbDirPath))
            assertFalse(dbPath.exists())
        } finally {
            eraseDb(db)
        }
    }

    @SlowTest
    @Test
    fun testDeleteOpeningDBByStaticMethod() {
        val db = duplicateBaseTestDb()
        val dbName = db.name
        val dbDir = db.filePath!!.parentFile
        try {
            assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.BUSY) { Database.delete(dbName, dbDir) }
        } finally {
            discardDb(db)
        }
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteNonExistingDBWithDefaultDir() {
        Database.delete("notexistdb", baseTestDb.filePath)
    }

    @Test
    fun testDeleteNonExistingDB() {
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) {
            Database.delete(baseTestDb.name, File(getScratchDirectoryPath("nowhere")))
        }
    }

    //---------------------------------------------
    //  Database Existing
    //---------------------------------------------
    @Test
    fun testDatabaseExistsWithDir() {
        val dirName = getUniqueName("test-exists-dir")
        val dbDirPath = getScratchDirectoryPath(dirName)
        val dbDir = File(dbDirPath)
        assertFalse(Database.exists(dirName, dbDir))

        // create db with custom directory
        val db = Database(dirName, DatabaseConfiguration().setDirectory(dbDirPath))
        try {
            assertTrue(Database.exists(dirName, dbDir))
            val dbPath = db.path!!
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
        assertFalse(Database.exists("notexistdb", baseTestDb.filePath!!))
    }

    @Test
    fun testDatabaseExistsAgainstNonExistDB() {
        assertFalse(Database.exists(baseTestDb.name, File(getScratchDirectoryPath("nowhere"))))
    }

    //---------------------------------------------
    //  Collections, 8.7: Use Scope API on Closed or Deleted Database
    //---------------------------------------------

    //
    // 8.7.1: Test that after the database is closed, calling functions on the scope object
    // returns the expected result based on section 6.3
    @Test
    fun testUseScopeWhenDatabaseIsClosed1() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope)
        discardDb(baseTestDb)
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope!!.collections }
    }

    @Test
    fun testUseScopeWhenDatabaseIsClosed2() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope)
        discardDb(baseTestDb)
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            scope!!.getCollection("bobblehead")
        }
    }

    // 8.7.2: Test that after the database is deleted, calling functions on the scope object
    // returns the expected result based on section 6.3
    @Test
    fun testUseScopeWhenDatabaseIsDeleted1() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope!!)
        assertNotNull(scope.collections)
        baseTestDb.delete()
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope.collections }
    }

    @Test
    fun testUseScopeWhenDatabaseIsDeleted2() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope!!)
        assertNotNull(scope.collections)
        baseTestDb.delete()
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) { scope.getCollection("bobblehead") }
    }

    //---------------------------------------------
    //  Collections, 8.8 Get Scopes or Collections on Closed or Deleted Database
    //---------------------------------------------

    // 8.8.1 Test that after the database is closed, calling functions to get scopes or collections
    // returns the result as expected based on section 6.4
    @Test
    fun testGetScopeWhenDatabaseIsClosed() {
        baseTestDb.createCollection("bobblehead", "horo")

        assertNotNull(baseTestDb.getScope("horo"))

        discardDb(baseTestDb)

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            baseTestDb.getScope("horo")
        }
    }

    @Test
    fun testGetCollectionWhenDatabaseIsClosed() {
        baseTestDb.createCollection("bobblehead", "horo")

        assertNotNull(baseTestDb.getCollection("bobblehead", "horo"))

        discardDb(baseTestDb)

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            baseTestDb.getCollection("bobblehead", "horo")
        }
    }

    // 8.8.2 Test that after the database is deleted, calling functions to get scopes or collections
    // returns the expected result based on section 6.4
    @Test
    fun testGetScopeWhenDatabaseIsDeleted() {
        baseTestDb.createCollection("bobblehead", "horo")

        assertNotNull(baseTestDb.getScope("horo"))

        eraseDb(baseTestDb)

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            baseTestDb.getScope("horo")
        }
    }

    @Test
    fun testGetCollectionWhenDatabaseIsDeleted() {
        baseTestDb.createCollection("bobblehead", "horo")

        assertNotNull(baseTestDb.getCollection("bobblehead", "horo"))

        eraseDb(baseTestDb)

        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            baseTestDb.getCollection("bobblehead", "horo")
        }
    }

    //---------------------------------------------
    //  Collections, 8.10 Get Scopes or Collections on Closed or Deleted Database
    //---------------------------------------------

    //
    // 8.10.1: Test that after the database is closed, calling functions on the scope object
    // returns the expected result based on section 6.3
    @Test
    fun testUseScopeWithNoCollections1() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope)

        baseTestDb.deleteCollection("bobblehead", "horo")

        val collections = scope!!.collections
        assertNotNull(collections)
        assertEquals(0, collections.size)
    }

    @Test
    fun testUseScopeWithNoCollections2() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope)

        baseTestDb.deleteCollection("bobblehead", "horo")

        assertNull(scope!!.getCollection("bobblehead"))
    }

    // 8.10.2 Test that after all collections in the scope are deleted from a different database instance,
    // calling the scope APIS returns the result as expected based on section 6.5.
    // To test this, get and retain the scope object before deleting all collections.
    @SlowTest
    @Test
    fun testTestUseScopeAPIAfterDeletingAllCollectionsFromDifferentDBInstance1() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope)

        val dbCopy = duplicateBaseTestDb()
        assertNotNull(dbCopy.getScope("horo"))

        dbCopy.deleteCollection("bobblehead", "horo")

        val collections = scope!!.collections
        assertNotNull(collections)
        assertEquals(0, collections.size)
    }

    @SlowTest
    @Test
    fun testTestUseScopeAPIAfterDeletingAllCollectionsFromDifferentDBInstance2() {
        baseTestDb.createCollection("bobblehead", "horo")
        val scope = baseTestDb.getScope("horo")
        assertNotNull(scope)

        val dbCopy = duplicateBaseTestDb()
        assertNotNull(dbCopy.getScope("horo"))

        dbCopy.deleteCollection("bobblehead", "horo")

        assertNull(scope!!.getCollection("bobblehead"))
    }

    //---------------------------------------------
    //  ...
    //---------------------------------------------

    @Test
    fun testCompact() {
        val nDocs = 20
        val nUpdates = 25
        val docIDs = createDocsInBaseTestDb(nDocs)

        // Update each doc 25 times:
        baseTestDb.inBatch<CouchbaseLiteException> {
            for (docID in docIDs) {
                var savedDoc = baseTestDb.getDocument(docID)
                for (i in 0 until nUpdates) {
                    val doc = savedDoc!!.toMutable()
                    doc.setValue("number", i)
                    savedDoc = saveDocInBaseTestDb(doc)
                }
            }
        }

        // Add each doc with a blob object:
        for (docID in docIDs) {
            val doc = baseTestDb.getDocument(docID)!!.toMutable()
            doc.setValue("blob", Blob("text/plain", doc.id.toByteArray()))
            saveDocInBaseTestDb(doc)
        }
        assertEquals(nDocs.toLong(), baseTestDb.count)
        val attsDir = File(baseTestDb.path, "Attachments")
        assertTrue(attsDir.exists())
        assertTrue(attsDir.isDirectory)
        assertEquals(nDocs, attsDir.listFiles()?.size ?: -1)

        // Compact:
        assertTrue(baseTestDb.performMaintenance(MaintenanceType.COMPACT))
        assertEquals(nDocs, attsDir.listFiles()?.size ?: -1)

        // Delete all docs:
        for (docID in docIDs) {
            val savedDoc = baseTestDb.getDocument(docID)
            baseTestDb.delete(savedDoc!!)
            assertNull(baseTestDb.getDocument(docID))
        }

        // Compact:
        assertTrue(baseTestDb.performMaintenance(MaintenanceType.COMPACT))
        assertEquals(0, attsDir.listFiles()?.size ?: -1)
    }

    // REF: https://github.com/couchbase/couchbase-lite-android/issues/1231
    @Test
    fun testOverwriteDocWithNewDocInstance() {
        val mDoc1 = MutableDocument("abc")
        mDoc1.setValue("someKey", "someVar")
        val doc1 = saveDocInBaseTestDb(mDoc1)

        // This cause conflict, DefaultConflictResolver should be applied.
        val mDoc2 = MutableDocument("abc")
        mDoc2.setValue("someKey", "newVar")
        val doc2 = saveDocInBaseTestDb(mDoc2)

        // Both doc1 and doc2 are now generation 1. Higher revision one should win
        assertEquals(1, baseTestDb.count)
        val doc = baseTestDb.getDocument("abc")
        assertNotNull(doc)
        // doc1 -> theirs, doc2 -> mine
        if (doc2.revisionID!! > doc1.revisionID!!) {
            // mine -> doc 2 win
            assertEquals("newVar", doc!!.getString("someKey"))
        } else {
            // their -> doc 1 win
            assertEquals("someVar", doc!!.getString("someKey"))
        }
    }

    @Test
    fun testCopy() {
        val nDocs = 10
        for (i in 0 until nDocs) {
            val docID = "doc_$i"
            val doc = MutableDocument(docID)
            doc.setValue("name", docID)
            doc.setValue("data", Blob("text/plain", docID.toByteArray()))
            saveDocInBaseTestDb(doc)
        }
        val config = baseTestDb.getConfig()
        val dbName = getUniqueName("test_copy_db")

        // Copy:
        Database.copy(File(baseTestDb.path!!), dbName, config)

        // Verify:
        assertTrue(Database.exists(dbName, File(config.directory)))
        val newDb = Database(dbName, config)
        try {
            assertNotNull(newDb)
            assertEquals(nDocs.toLong(), newDb.count)
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(newDb))
                .execute().use { rs ->
                    for (r in rs) {
                        val docID = r.getString(0)
                        assertNotNull(docID)
                        val doc = newDb.getDocument(docID!!)
                        assertNotNull(doc)
                        assertEquals(docID, doc!!.getString("name"))
                        val blob = doc.getBlob("data")
                        assertNotNull(blob)
                        assertEquals(docID, String(blob!!.content!!))
                    }
                }
        } finally {
            eraseDb(newDb)
        }
    }

    @Test
    fun testCreateIndex() {
        assertEquals(0, baseTestDb.indexes.size.toLong())
        baseTestDb.createIndex(
            "index1",
            IndexBuilder.valueIndex(
                ValueIndexItem.property("firstName"),
                ValueIndexItem.property("lastName")
            )
        )
        assertEquals(1, baseTestDb.indexes.size.toLong())

        // Create FTS index:
        baseTestDb.createIndex("index2", IndexBuilder.fullTextIndex(FullTextIndexItem.property("detail")))
        assertEquals(2, baseTestDb.indexes.size.toLong())
        baseTestDb.createIndex(
            "index3",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("es-detail")).ignoreAccents(true).setLanguage("es")
        )
        assertEquals(3, baseTestDb.indexes.size.toLong())

        // Create value index with expression() instead of property()
        baseTestDb.createIndex(
            "index4",
            IndexBuilder.valueIndex(
                ValueIndexItem.expression(Expression.property("firstName")),
                ValueIndexItem.expression(Expression.property("lastName"))
            )
        )
        assertEquals(4, baseTestDb.indexes.size.toLong())
        assertContents(baseTestDb.indexes, "index1", "index2", "index3", "index4")
    }

    @Test
    fun testCreateIndexWithConfig() {
        assertEquals(0, baseTestDb.indexes.size.toLong())
        baseTestDb.createIndex("index1", ValueIndexConfiguration("firstName", "lastName"))
        assertEquals(1, baseTestDb.indexes.size.toLong())
        baseTestDb.createIndex(
            "index2",
            FullTextIndexConfiguration("detail").ignoreAccents(true).setLanguage("es")
        )
        assertEquals(2, baseTestDb.indexes.size.toLong())
        assertContents(baseTestDb.indexes, "index1", "index2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIndexBuilderEmptyArg1() {
        IndexBuilder.fullTextIndex()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIndexBuilderEmptyArg2() {
        IndexBuilder.valueIndex()
    }

    @Test
    fun testCreateSameIndexTwice() {
        // Create index with first name:
        val indexItem = ValueIndexItem.property("firstName")
        val index: Index = IndexBuilder.valueIndex(indexItem)
        baseTestDb.createIndex("myindex", index)

        // Call create index again:
        baseTestDb.createIndex("myindex", index)
        assertEquals(1, baseTestDb.indexes.size.toLong())
        assertContents(baseTestDb.indexes, "myindex")
    }

    @Test
    fun testCreateSameNameIndexes() {
        val fNameItem = ValueIndexItem.property("firstName")
        val lNameItem = ValueIndexItem.property("lastName")
        val detailItem = FullTextIndexItem.property("detail")

        // Create value index with first name:
        val fNameIndex: Index = IndexBuilder.valueIndex(fNameItem)
        baseTestDb.createIndex("myindex", fNameIndex)

        // Create value index with last name:
        val lNameindex = IndexBuilder.valueIndex(lNameItem)
        baseTestDb.createIndex("myindex", lNameindex)

        // Check:
        assertEquals(1, baseTestDb.indexes.size.toLong())
        assertEquals(listOf("myindex"), baseTestDb.indexes)

        // Create FTS index:
        val detailIndex: Index = IndexBuilder.fullTextIndex(detailItem)
        baseTestDb.createIndex("myindex", detailIndex)

        // Check:
        assertEquals(1, baseTestDb.indexes.size)
        assertContents(baseTestDb.indexes, "myindex")
    }

    @Test
    fun testDeleteIndex() {
        testCreateIndex()

        // Delete indexes:
        baseTestDb.deleteIndex("index4")
        assertEquals(3, baseTestDb.indexes.size.toLong())
        assertContents(baseTestDb.indexes, "index1", "index2", "index3")
        baseTestDb.deleteIndex("index1")
        assertEquals(2, baseTestDb.indexes.size.toLong())
        assertContents(baseTestDb.indexes, "index2", "index3")
        baseTestDb.deleteIndex("index2")
        assertEquals(1, baseTestDb.indexes.size.toLong())
        assertContents(baseTestDb.indexes, "index3")
        baseTestDb.deleteIndex("index3")
        assertEquals(0, baseTestDb.indexes.size.toLong())
        assertTrue(baseTestDb.indexes.isEmpty())

        // Delete non existing index:
        baseTestDb.deleteIndex("dummy")

        // Delete deleted indexes:
        baseTestDb.deleteIndex("index1")
        baseTestDb.deleteIndex("index2")
        baseTestDb.deleteIndex("index3")
        baseTestDb.deleteIndex("index4")
    }

    @Test
    fun testRebuildIndex() {
        testCreateIndex()
        assertTrue(baseTestDb.performMaintenance(MaintenanceType.REINDEX))
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1416
    @Test
    fun testDeleteAndOpenDB() {
        val config = DatabaseConfiguration()
        var database1: Database? = null
        var database2: Database? = null
        try {
            // open a database
            database1 = createDb("del_open_db", config)
            val dbName = database1.name

            // delete it
            database1.delete()

            // open it again
            database2 = Database(dbName, config)

            // insert documents
            val db: Database = database2
            database2.inBatch(UnitOfWork<CouchbaseLiteException> {
                // just create 100 documents
                for (i in 0..99) {
                    val doc = MutableDocument()

                    // each doc has 10 items
                    doc.setInt("index", i)
                    for (j in 0..9) {
                        doc.setInt("item_$j", j)
                    }
                    db.save(doc)
                }
            })

            // close db again
            database2.close()
        } finally {
            eraseDb(database1)
            eraseDb(database2)
        }
    }

    @Test
    fun testSaveAndUpdateMutableDoc() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        baseTestDb.save(doc)

        // Update:
        doc.setString("lastName", "Tiger")
        baseTestDb.save(doc)

        // Update:
        doc.setLong("age", 20L) // Int vs Long assertEquals can not ignore diff.
        baseTestDb.save(doc)
        assertEquals(3, doc.sequence)
        val expected = mapOf("firstName" to "Daniel", "lastName" to "Tiger", "age" to 20L)
        assertEquals(expected, doc.toMap())
        val savedDoc = baseTestDb.getDocument(doc.id)
        assertEquals(expected, savedDoc!!.toMap())
        assertEquals(3, savedDoc.sequence)
    }

    @Test
    fun testSaveDocWithConflict() {
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
        testSaveDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testDeleteDocWithConflict() {
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
        testDeleteDocWithConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testSaveDocWithNoParentConflict() {
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
        testSaveDocWithNoParentConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testSaveDocWithDeletedConflict() {
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.LAST_WRITE_WINS)
        testSaveDocWithDeletedConflictUsingConcurrencyControl(ConcurrencyControl.FAIL_ON_CONFLICT)
    }

    @Test
    fun testDeleteAndUpdateDoc() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        baseTestDb.save(doc)
        baseTestDb.delete(doc)
        assertEquals(2, doc.sequence)
        assertNull(baseTestDb.getDocument(doc.id))
        doc.setString("firstName", "Scott")
        baseTestDb.save(doc)
        assertEquals(3, doc.sequence)
        val expected = mapOf("firstName" to "Scott", "lastName" to "Tiger")
        assertEquals(expected, doc.toMap())
        val savedDoc = baseTestDb.getDocument(doc.id)
        assertNotNull(savedDoc)
        assertEquals(expected, savedDoc!!.toMap())
    }

    @Test
    fun testDeleteAlreadyDeletedDoc() {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        baseTestDb.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = baseTestDb.getDocument("doc1")
        val doc1b = baseTestDb.getDocument("doc1")!!.toMutable()

        // Delete doc1a:
        baseTestDb.delete(doc1a!!)
        assertEquals(2, doc1a.sequence)
        assertNull(baseTestDb.getDocument(doc.id))

        // Delete doc1b:
        baseTestDb.delete(doc1b)
        assertEquals(2, doc1b.sequence)
        assertNull(baseTestDb.getDocument(doc.id))
    }

    @Test
    fun testDeleteNonExistingDoc() {
        val doc1a = createSingleDocInBaseTestDb("doc1")
        val doc1b = baseTestDb.getDocument("doc1")

        // purge doc
        baseTestDb.purge(doc1a)
        assertEquals(0, baseTestDb.count)
        assertNull(baseTestDb.getDocument(doc1a.id))
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { baseTestDb.delete(doc1a) }
        assertThrowsCBL(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND) { baseTestDb.delete(doc1b!!) }
        assertEquals(0, baseTestDb.count)
        assertNull(baseTestDb.getDocument(doc1b!!.id))
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1652
    @Test
    fun testDeleteWithOldDocInstance() {
        // 1. save
        var mdoc = MutableDocument("doc")
        mdoc.setBoolean("updated", false)
        baseTestDb.save(mdoc)
        val doc = baseTestDb.getDocument("doc")

        // 2. update
        mdoc = doc!!.toMutable()
        mdoc.setBoolean("updated", true)
        baseTestDb.save(mdoc)

        // 3. delete by previously retrieved document
        baseTestDb.delete(doc)
        assertNull(baseTestDb.getDocument("doc"))
    }

    // The following four tests verify, explicitly, the code that
    // mitigates the 2.8.0 bug (CBL-1408)
    // There is one more test for this in DatabaseEncryptionTest
    @Test
    fun testReOpenExistingDb() {
        val dbName = getUniqueName("test_db")

        // verify that the db directory is no longer in the misguided 2.8.0 subdirectory
        val dbDirectory = CouchbaseLiteInternal.getDefaultDbDir().absolutePath
        assertFalse(dbDirectory.endsWith(".couchbase"))
        var db: Database? = null
        try {
            db = Database(dbName)
            val mDoc = MutableDocument()
            mDoc.setString("foo", "bar")
            db.save(mDoc)
            db.close()
            db = Database(dbName)
            assertEquals(1L, db.count)
            val doc = db.getDocument(mDoc.id)
            assertEquals("bar", doc!!.getString("foo"))
        } finally {
            eraseDb(db)
        }
    }

    @Test
    fun testReOpenExisting2Dot8DotOhDb() {
        val dbName = getUniqueName("test_db")
        val twoDot8DotOhDirPath = File(CouchbaseLiteInternal.getDefaultDbDir(), ".couchbase").canonicalPath
        var db: Database? = null
        try {
            // Create a database in the misguided 2.8.0 directory
            val config = DatabaseConfiguration()
            config.directory = twoDot8DotOhDirPath
            db = Database(dbName, config)
            val mDoc = MutableDocument()
            mDoc.setString("foo", "bar")
            db.save(mDoc)
            db.close()

            // This should open the database created above
            db = Database(dbName)
            assertEquals(1L, db.count)
            val doc = db.getDocument(mDoc.id)
            assertEquals("bar", doc!!.getString("foo"))
        } finally {

            FileUtils.eraseFileOrDir(twoDot8DotOhDirPath)
            eraseDb(db)
        }
    }

    @Test
    fun testReOpenExisting2Dot8DotOhDbCopyFails() {
        val dbName = getUniqueName("test_db")
        val twoDot8DotOhDirPath = File(CouchbaseLiteInternal.getDefaultDbDir(), ".couchbase").canonicalPath
        var db: Database? = null
        try {
            // Create a database in the misguided 2.8.0 directory
            val config = DatabaseConfiguration()
            config.directory = twoDot8DotOhDirPath
            db = Database(dbName, config)
            val mDoc = MutableDocument()
            mDoc.setString("foo", "bar")
            db.save(mDoc)
            db.close()
            db = null
            val twoDot8DotOhDir = File(twoDot8DotOhDirPath)
            FileUtils.deleteContents(C4Database.getDatabaseFile(twoDot8DotOhDir, dbName))

            // this should try to copy the db created above, but fail
            try {
                db = Database(dbName)
                fail("DB open should have thrown an exception")
            } catch (ignore: CouchbaseLiteException) {
            }

            // the (uncopyable) 2.8.0 db should still exist
            assertTrue(C4Database.getDatabaseFile(twoDot8DotOhDir, dbName).exists())
            // the copy should not exist
            assertFalse(C4Database.getDatabaseFile(CouchbaseLiteInternal.getDefaultDbDir(), dbName).exists())
        } finally {
            FileUtils.eraseFileOrDir(twoDot8DotOhDirPath)
            eraseDb(db)
        }
    }

    @Test
    fun testReOpenExistingLegacyAnd2Dot8DotOhDb() {
        val dbName = getUniqueName("test_db")
        val twoDot8DotOhDirPath = File(CouchbaseLiteInternal.getDefaultDbDir(), ".couchbase").canonicalPath
        var db: Database? = null
        try {
            db = Database(dbName)
            val mDoc1 = MutableDocument()
            mDoc1.setString("foo", "bar")
            db.save(mDoc1)
            db.close()

            // Create a database in the misguided 2.8.0 directory
            val config = DatabaseConfiguration()
            config.directory = twoDot8DotOhDirPath
            db = Database(dbName, config)
            val mDoc2 = MutableDocument()
            mDoc2.setString("foo", "baz")
            db.save(mDoc2)
            db.close()

            // This should open the database created above
            db = Database(dbName)
            assertEquals(1L, db.count)
            val doc = db.getDocument(mDoc1.id)
            assertEquals("bar", doc!!.getString("foo"))
        } finally {
            FileUtils.eraseFileOrDir(twoDot8DotOhDirPath)
            eraseDb(db)
        }
    }

    @Test
    fun testSetDocumentExpirationHangInBatch() {
        val nDocs = 10
        createDocsInBaseTestDb(nDocs)

        val now = Date().time
        val tomorrow = Date(now + 1000L * 60 * 60 * 24) // now + 1 day

        val future = FutureTask<Void> {
            baseTestDb.inBatch<CouchbaseLiteException> {
                for (i in 0 until nDocs) {
                    val doc = baseTestDb.getDocument("doc_${i}")!!.toMutable()
                    doc.setString("expiration", tomorrow.toString())
                    baseTestDb.save(doc)
                    baseTestDb.setDocumentExpiration(doc.id, tomorrow)
                }
            }
            null
        }
        testSerialExecutor.execute(future)
        try {
            future.get(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw AssertionError("Batch execution failed", e)
        }

        for (i in 0 until nDocs) {
            val docID = "doc_${i}"
            assertEquals(tomorrow.toString(), baseTestDb.getDocument("doc_${i}")!!.getString("expiration"))
            assertEquals(tomorrow, baseTestDb.getDocumentExpiration(docID))
        }
    }


    /////////////////////////////////   H E L P E R S   //////////////////////////////////////

    // helper method to save n docs
    private fun createDocsInBaseTestDb(n: Int): List<String> {
        val docs: MutableList<String> = ArrayList()
        for (i in 0 until n) {
            val doc = MutableDocument("doc_${i}")
            doc.setValue("key", i)
            docs.add(saveDocInBaseTestDb(doc).id)
        }
        assertEquals(n.toLong(), baseTestDb.count)
        return docs
    }

    // helper method to verify n number of docs
    private fun verifyDocuments(n: Int) {
        for (i in 0 until n) verifyGetDocument("doc_${i}", i.toLong())
    }

    // helper methods to verify getDoc
    private fun verifyGetDocument(docID: String, value: Long) {
        verifyGetDocument(baseTestDb, docID, value)
    }

    // helper methods to verify getDoc
    private fun verifyGetDocument(db: Database, docID: String, value: Long) {
        val doc = db.getDocument(docID)
        assertNotNull(doc)
        assertEquals(docID, doc!!.id)
        assertEquals(value, doc.getValue("key"))
    }

    // helper method to purge doc and verify doc.
    private fun purgeDocAndVerify(doc: Document?) {
        val docID = doc!!.id
        baseTestDb.purge(doc)
        assertNull(baseTestDb.getDocument(docID))
    }

    // base test method
    private fun testSaveNewDocWithID(docID: String) {
        // store doc
        createSingleDocInBaseTestDb(docID)
        assertEquals(1, baseTestDb.count)

        // validate document by getDocument
        verifyGetDocument(docID, 1)
    }

    private fun testSaveDocWithConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        baseTestDb.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = baseTestDb.getDocument("doc1")!!.toMutable()
        val doc1b = baseTestDb.getDocument("doc1")!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        baseTestDb.save(doc1a)
        doc1a.setString("nickName", "Scotty")
        baseTestDb.save(doc1a)
        val expected = mapOf("firstName" to "Scott", "lastName" to "Tiger", "nickName" to "Scotty")
        assertEquals(expected, doc1a.toMap())
        assertEquals(3, doc1a.sequence)

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.save(doc1b, cc))
            val savedDoc = baseTestDb.getDocument(doc.id)
            assertEquals(doc1b.toMap(), savedDoc!!.toMap())
            assertEquals(4, savedDoc.sequence)
        } else {
            assertFalse(baseTestDb.save(doc1b, cc))
            val savedDoc = baseTestDb.getDocument(doc.id)
            assertEquals(expected, savedDoc!!.toMap())
            assertEquals(3, savedDoc.sequence)
        }
        recreateBastTestDb()
    }

    private fun testDeleteDocWithConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        baseTestDb.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = baseTestDb.getDocument("doc1")!!.toMutable()
        val doc1b = baseTestDb.getDocument("doc1")!!.toMutable()

        // Modify doc1a:
        doc1a.setString("firstName", "Scott")
        baseTestDb.save(doc1a)
        val expected = mapOf("firstName" to "Scott", "lastName" to "Tiger")
        assertEquals(expected, doc1a.toMap())
        assertEquals(2, doc1a.sequence)

        // Modify doc1b and delete, result to conflict when delete:
        doc1b.setString("lastName", "Lion")
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.delete(doc1b, cc))
            assertEquals(3, doc1b.sequence)
            assertNull(baseTestDb.getDocument(doc1b.id))
        } else {
            assertFalse(baseTestDb.delete(doc1b, cc))
            val savedDoc = baseTestDb.getDocument(doc.id)
            assertEquals(expected, savedDoc!!.toMap())
            assertEquals(2, savedDoc.sequence)
        }

        recreateBastTestDb()
    }

    private fun testSaveDocWithNoParentConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
        val doc1a = MutableDocument("doc1")
        doc1a.setString("firstName", "Daniel")
        doc1a.setString("lastName", "Tiger")
        baseTestDb.save(doc1a)
        var savedDoc = baseTestDb.getDocument(doc1a.id)
        assertEquals(doc1a.toMap(), savedDoc!!.toMap())
        assertEquals(1, savedDoc.sequence)
        val doc1b = MutableDocument("doc1")
        doc1b.setString("firstName", "Scott")
        doc1b.setString("lastName", "Tiger")
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.save(doc1b, cc))
            savedDoc = baseTestDb.getDocument(doc1b.id)
            assertEquals(doc1b.toMap(), savedDoc!!.toMap())
            assertEquals(2, savedDoc.sequence)
        } else {
            assertFalse(baseTestDb.save(doc1b, cc))
            savedDoc = baseTestDb.getDocument(doc1b.id)
            assertEquals(doc1a.toMap(), savedDoc!!.toMap())
            assertEquals(1, savedDoc.sequence)
        }

        recreateBastTestDb()
    }

    private fun testSaveDocWithDeletedConflictUsingConcurrencyControl(cc: ConcurrencyControl) {
        val doc = MutableDocument("doc1")
        doc.setString("firstName", "Daniel")
        doc.setString("lastName", "Tiger")
        baseTestDb.save(doc)

        // Get two doc1 document objects (doc1a and doc1b):
        val doc1a = baseTestDb.getDocument("doc1")
        val doc1b = baseTestDb.getDocument("doc1")!!.toMutable()

        // Delete doc1a:
        baseTestDb.delete(doc1a!!)
        assertEquals(2, doc1a.sequence)
        assertNull(baseTestDb.getDocument(doc.id))

        // Modify doc1b, result to conflict when save:
        doc1b.setString("lastName", "Lion")
        if (cc == ConcurrencyControl.LAST_WRITE_WINS) {
            assertTrue(baseTestDb.save(doc1b, cc))
            val savedDoc = baseTestDb.getDocument(doc.id)
            assertEquals(doc1b.toMap(), savedDoc!!.toMap())
            assertEquals(3, savedDoc.sequence)
        } else {
            assertFalse(baseTestDb.save(doc1b, cc))
            assertNull(baseTestDb.getDocument(doc.id))
        }

        recreateBastTestDb()
    }
}