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

package com.couchbase.lite.internal.core

import com.couchbase.lite.Collection
import com.couchbase.lite.LiteCoreException
import com.couchbase.lite.MaintenanceType
import com.couchbase.lite.Scope
import com.couchbase.lite.internal.utils.FileUtils
import com.couchbase.lite.internal.utils.VerySlowTest
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.experimental.and


class C4DatabaseTest : C4BaseTest() {
    private val NOT_CREATE_FLAG = C4Constants.DatabaseFlags.CREATE.inv()
    private val POSIX_EEXIST = 17

    private val mockNativeImpl = object : C4Database.NativeImpl {
        override fun nOpen(
            parentDir: String,
            name: String,
            flags: Long,
            algorithm: Int,
            encryptionKey: ByteArray?,
        ): Long = 0xeb6eb15aL

        override fun nClose(db: Long) = Unit
        override fun nFree(db: Long) = Unit
        override fun nGetPath(db: Long): String = "test/path"
        override fun nCopy(
            sourcePath: String?,
            parentDir: String?,
            name: String?,
            flags: Long,
            algorithm: Int,
            encryptionKey: ByteArray?,
        ) = Unit

        override fun nDelete(db: Long) = Unit
        override fun nDeleteNamed(name: String, dir: String) = Unit
        override fun nGetPublicUUID(db: Long): ByteArray = byteArrayOf()
        override fun nBeginTransaction(db: Long) = Unit
        override fun nEndTransaction(db: Long, commit: Boolean) = Unit
        override fun nMaintenance(db: Long, type: Int): Boolean = true
        override fun nRekey(db: Long, keyType: Int, newKey: ByteArray?) = Unit
        override fun nSetCookie(db: Long, url: String?, setCookieHeader: String?, acceptParents: Boolean) = Unit
        override fun nGetCookies(db: Long, url: String): String = "test_cookies"
        override fun nGetSharedFleeceEncoder(db: Long): Long = 1L
        override fun nGetFLSharedKeys(db: Long): Long = 1L
        override fun nDocContainsBlobs(dictPtr: Long, dictSize: Long, sharedKeys: Long) = false
        override fun nGetScopeNames(peer: Long): MutableSet<String> = mutableSetOf()
        override fun nHasScope(peer: Long, scope: String): Boolean = true
        override fun nGetCollectionNames(peer: Long, scope: String): MutableSet<String> = mutableSetOf()
        override fun nDeleteCollection(peer: Long, scope: String, collection: String) = Unit
    }

    // - Test with C4Database mock

    // Test if java platform receives a peer value for c4Database, a java c4Database should exist
    @Test
    fun testOpenDb() {
        C4Database.getDatabase(
            mockNativeImpl,
            dbParentDirPath,
            "test_DB",
            0L,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        ).use { c4Database -> Assert.assertNotNull(c4Database) }
    }

    @Test
    fun testGetSharedFleeceEncoder() {
        C4Database.getDatabase(
            mockNativeImpl,
            dbParentDirPath,
            "test_DB",
            0L,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        ).use { c4Database -> Assert.assertNotNull(c4Database.sharedFleeceEncoder) }
    }

    @Test
    fun testGetFLSharedKeys() {
        C4Database.getDatabase(
            mockNativeImpl,
            dbParentDirPath,
            "test_DB",
            0L,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        ).use { c4Database -> Assert.assertNotNull(c4Database.flSharedKeys) }
    }

    /**
     * Functional Tests
     */

    // - "Database ErrorMessages"
    @Test
    fun testDatabaseErrorMessages() {
        try {
            C4Database.getDatabase("", "", 0L)
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            Assert.assertEquals(C4Constants.LiteCoreError.WRONG_FORMAT, e.code)
            Assert.assertTrue(e.message.contains("Parent directory does not exist"))
        }
    }

    // - "Database Info"
    @Test
    fun testDatabaseInfo() {
        val publicUUID = c4Database.publicUUID
        Assert.assertNotNull(publicUUID)
        Assert.assertTrue(publicUUID.isNotEmpty())

        //Weird requirements of UUIDs according to the spec
        Assert.assertEquals(0x40.toByte(), (publicUUID[6] and 0xF0.toByte()))
        Assert.assertEquals(0x80.toUByte(), (publicUUID[8].toUByte() and 0xC0.toUByte()))
        val privateUUID = C4TestUtils.privateUUIDForDb(c4Database)
        Assert.assertNotNull(privateUUID)
        Assert.assertTrue(privateUUID.isNotEmpty())
        Assert.assertEquals(0x40.toByte(), (privateUUID[6] and 0xF0.toByte()))
        Assert.assertEquals(0x80.toUByte(), (privateUUID[8].toUByte() and 0xC0.toUByte()))
        Assert.assertFalse(publicUUID.contentEquals(privateUUID))

        reopenDB()

        //Make sure UUIDs are persistent
        val publicUUID2 = c4Database.publicUUID
        val privateUUID2 = C4TestUtils.privateUUIDForDb(c4Database)
        Assert.assertArrayEquals(publicUUID, publicUUID2)
        Assert.assertArrayEquals(privateUUID, privateUUID2)
    }

    // - Database deletion lock
    @VerySlowTest
    @Test
    fun testDatabaseDeletionLock() {
        // Try it using the C4Db's idea of the location of the db
        try {
            val name = c4Database.dbName
            Assert.assertNotNull(name)
            val dir = c4Database.dbDirectory
            Assert.assertNotNull(dir)
            if (dir != null && name != null) {
                C4Database.deleteNamedDb(dir, name)
                Assert.fail()
            }

        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            Assert.assertEquals(C4Constants.LiteCoreError.BUSY, e.code)
        }

        // Try it using our idea of the location of the db
        try {
            C4Database.deleteNamedDb(dbParentDirPath, dbName)
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            Assert.assertEquals(C4Constants.LiteCoreError.BUSY, e.code)
        }
    }

    // - Database Read-Only UUIDs
    @Test
    fun testDatabaseReadOnlyUUIDs() {
        closeC4Database()
        c4Database = C4Database.getDatabase(
            dbParentDirPath,
            dbName,
            (testDbFlags and NOT_CREATE_FLAG) or C4Constants.DatabaseFlags.READ_ONLY
        )
        Assert.assertNotNull(c4Database)
        Assert.assertNotNull(c4Database.publicUUID)
        Assert.assertNotNull(C4TestUtils.privateUUIDForDb(c4Database))
    }

    // - "Database OpenBundle"
    @Test
    fun testDatabaseOpenBundle() {
        val bundleDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"))
        val bundleName = getUniqueName("bundle")
        try {
            var bundle: C4Database? = null

            // Open nonexistent bundle with just the create flag.
            try {
                bundle = C4Database.getDatabase(bundleDirPath, bundleName, C4Constants.DatabaseFlags.CREATE)
                Assert.assertNotNull(bundle)
            } finally {
                if (bundle != null) {
                    bundle.closeDb()
                    bundle = null
                }
            }

            // Reopen without 'create' flag:
            try {
                bundle = C4Database.getDatabase(bundleDirPath, bundleName, testDbFlags and NOT_CREATE_FLAG)
                Assert.assertNotNull(bundle)
            } finally {
                bundle?.closeDb()
            }
        } finally {
            FileUtils.eraseFileOrDir(bundleDirPath)
        }
    }

    // Open nonexistent bundle without the create flag.
    @Test
    fun testDatabaseOpenNonExistentBundleWithoutCreateFlagFails() {
        try {
            C4Database.getDatabase(
                getScratchDirectoryPath(getUniqueName("c4_test_2")),
                getUniqueName("bundle"),
                testDbFlags and NOT_CREATE_FLAG
            )
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code)
        }
    }

    // - "Database Compact"
    @Test
    fun testDatabaseCompact() {
        val doc1ID = "doc001"
        val doc2ID = "doc002"
        val doc3ID = "doc003"
        val doc4ID = "doc004"
        val content1 = "This is the first attachment"
        val content2 = "This is the second attachment"
        val content3 = "This is the third attachment"

        val allKeys: MutableSet<C4BlobKey> = HashSet()
        val key1: C4BlobKey
        val key2: C4BlobKey
        val key3: C4BlobKey
        try {
            val atts: MutableList<String> = ArrayList()
            var keys: List<C4BlobKey>
            c4Database.beginTransaction()
            try {
                atts.add(content1)
                keys = addDocWithAttachments(doc1ID, atts)
                allKeys.addAll(keys)
                key1 = keys[0]
                atts.clear()
                atts.add(content2)
                keys = addDocWithAttachments(doc2ID, atts)
                allKeys.addAll(keys)
                key2 = keys[0]
                keys = addDocWithAttachments(doc4ID, atts)
                allKeys.addAll(keys)
                atts.clear()
                atts.add(content3)
                keys = addDocWithAttachments(doc3ID, atts)
                allKeys.addAll(keys)
                key3 = keys[0] // legacy: TODO need to implement legacy support
            } finally {
                c4Database.endTransaction(true)
            }

            val store = c4Database.blobStore
            Assert.assertNotNull(store)
            c4Database.performMaintenance(MaintenanceType.COMPACT)
            Assert.assertTrue(store.getSize(key1) > 0)
            Assert.assertTrue(store.getSize(key2) > 0)
            Assert.assertTrue(store.getSize(key3) > 0)

            // Only reference to first blob is gone
            createRev(doc1ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            c4Database.performMaintenance(MaintenanceType.COMPACT)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertTrue(store.getSize(key2) > 0)
            Assert.assertTrue(store.getSize(key3) > 0)

            // Two references exist to the second blob, so it should still
            // exist after deleting doc002
            createRev(doc2ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            c4Database.performMaintenance(MaintenanceType.COMPACT)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertTrue(store.getSize(key2) > 0)
            Assert.assertTrue(store.getSize(key3) > 0)

            // After deleting doc4 both blobs should be gone
            createRev(doc4ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            c4Database.performMaintenance(MaintenanceType.COMPACT)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertEquals(store.getSize(key2), -1)
            Assert.assertTrue(store.getSize(key3) > 0)

            // Delete doc with legacy attachment, and it too will be gone
            createRev(doc3ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            c4Database.performMaintenance(MaintenanceType.COMPACT)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertEquals(store.getSize(key2), -1)
            Assert.assertEquals(store.getSize(key3), -1)
        } finally {
            closeKeys(allKeys)
        }
    }

    @Test
    fun testDatabaseCopyToNonexistentDirectoryFails() {
        try {
            C4Database.copyDb(
                c4Database.dbPath!!,
                File(File(getScratchDirectoryPath(getUniqueName("a")), "aa"), "aaa").path,
                getUniqueName("c4_copy_test_db"),
                testDbFlags
            )
            Assert.fail("Copy to non-existent directory should fail")
        } catch (ex: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), ex.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), ex.code.toLong())
        }
    }

    @Test
    fun testDatabaseCopyFromNonexistentDbFails() {
        try {
            C4Database.copyDb(
                File(c4Database.dbPath?.let { File(it).parentFile }, "x" + C4Database.DB_EXTENSION).path,
                getScratchDirectoryPath(getUniqueName("c4_test_2")),
                getUniqueName("c4_copy_test_db"),
                testDbFlags
            )
            Assert.fail("Copy from non-existent database should fail")
        } catch (ex: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain)
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code)
        }
    }


    // - Scopes and Collections

    //  Test create 1 scope and 1 collection
    @Test
    fun testCreateScopeAndCollection() {
        val dbName = getUniqueName("c4_test_db")
        val c4Db = C4Database.getDatabase(dbParentDirPath, dbName, testDbFlags)

        Assert.assertEquals(1, c4Db.scopeNames.size)
        Assert.assertEquals(setOf(Scope.DEFAULT_NAME), c4Db.scopeNames)

        c4Db.addCollection("test_scope", "test_coll")
        Assert.assertEquals(2, c4Db.scopeNames.size)

        Assert.assertTrue(c4Db.hasScope("test_scope"))
        Assert.assertEquals(setOf(Scope.DEFAULT_NAME, "test_scope"), c4Db.scopeNames)

        Assert.assertEquals(1, c4Db.getCollectionNames("test_scope").size)
        Assert.assertEquals(setOf("test_coll"), c4Db.getCollectionNames("test_scope"))
    }

    // Test create multiple collections in a scope
    @Test
    fun testCreateMultipleCollections() {
        val scope = c4Collection.scope
        c4Database.addCollection(scope, "test_coll_1")
        c4Database.addCollection(scope, "test_coll_2")
        c4Database.addCollection(scope, "test_coll_3")

        Assert.assertEquals(2, c4Database.scopeNames.size) // +1 for the default
        Assert.assertEquals(4, c4Database.getCollectionNames(scope).size) // +1 for the test collection (c4Collection)
        Assert.assertEquals(
            setOf("test_coll_1", "test_coll_2", "test_coll_3", c4Collection.name),
            c4Database.getCollectionNames(scope)
        )
    }

    // Test DeleteDefaultCollection are in
    // cbl-java-common @ a2de0d43d09ce64fd3a1301dc35

    // After deleting a collection, we can no longer get that collection (make sure test doesn't crash)
    @Test
    fun testDeleteCollection() {
        val coll = c4Database.addCollection("test_scope", "test_coll")
        Assert.assertNotNull(c4Database.getCollection("test_scope", "test_coll"))
        c4Database.deleteCollection("test_scope", "test_coll")

        //getting a non-existent collection should return null, not an exception
        Assert.assertNull(c4Database.getCollection("test_scope", "test_coll"))
        Assert.assertEquals(0, coll.documentCount)

        assertThrowsLiteCoreException(
            C4Constants.ErrorDomain.LITE_CORE,
            C4Constants.LiteCoreError.NOT_OPEN
        ) {
            coll.createDocument("1", null, 0)
        }
    }

    // Close Database and collection operations will throw error
    @Test
    fun testCollectionOnClosedDB() {
        val col = c4Database.getCollection(Scope.DEFAULT_NAME, Collection.DEFAULT_NAME)
        c4Database.closeDb()
        c4Database = null // prevent @after from closing the database again
        assertThrowsLiteCoreException(C4Constants.ErrorDomain.LITE_CORE, C4Constants.LiteCoreError.NOT_OPEN) {
            col?.createDocument("1", null, 0)
        }
    }


    /**
     * Deprecated tests that should be in C4Collection instead of C4Database
     */

    @Test
    fun testDatabaseCopySucceeds() {
        createRev("doc001", REV_ID_1, fleeceBody)
        createRev("doc002", REV_ID_1, fleeceBody)
        Assert.assertEquals(2L, c4Collection.documentCount)


        val dbName = getUniqueName("c4_copy_test_db")
        val dstParentDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"))
        C4Database.copyDb(c4Database.dbPath!!, dstParentDirPath, dbName, testDbFlags)
        val copyDb = C4Database.getDatabase(dstParentDirPath, dbName, testDbFlags)
        Assert.assertNotNull(copyDb)
        Assert.assertEquals(2L, c4Collection.documentCount)
    }

    @Test
    fun testDatabaseCopyToExistingDBFails() {
        createRev("doc001", REV_ID_1, fleeceBody)
        createRev("doc002", REV_ID_1, fleeceBody)
        val srcDbPath = c4Database.dbPath
        val dstParentDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"))
        var targetDb = C4Database.getDatabase(dstParentDirPath, dbName, testDbFlags)
        createRev(targetDb.defaultCollection, "doc001", REV_ID_1, fleeceBody)
        Assert.assertEquals(1L, targetDb.defaultCollection.documentCount)
        targetDb.close()
        try {
            C4Database.copyDb(
                srcDbPath!!,
                dstParentDirPath,
                dbName,
                testDbFlags
            )
        } catch (ex: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.POSIX, ex.domain)
            Assert.assertEquals(POSIX_EEXIST, ex.code)
        }
        targetDb = C4Database.getDatabase(dstParentDirPath, dbName, testDbFlags)
        Assert.assertEquals(1L, targetDb.defaultCollection.documentCount)
        targetDb.close()
    }

    // - "Database AllDocsInfo"
    // This test depends on the fact that the enumerator will return
    // the documents in dictionary order, doc-01 before doc-10, etc
    @Test
    fun testAllDocsInfo() {
        setupAllDocs()
        var i = 1

        // No start or end ID:
        val iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT
        val allDocs = C4TestUtils.enumerateDocsForCollection(c4Collection, iteratorFlags)
        Assert.assertNotNull(allDocs)
        while (allDocs.next()) {
            val doc = allDocs.document
            Assert.assertEquals(docId(i), C4TestUtils.idForDoc(doc))
            Assert.assertEquals(REV_ID_1, doc.revID)
            Assert.assertEquals(REV_ID_1, doc.selectedRevID)
            Assert.assertEquals(i.toLong(), doc.sequence)
            Assert.assertEquals(i.toLong(), doc.selectedSequence)
            Assert.assertTrue(doc.docExists())
            i++
        }
        Assert.assertEquals(100, i)
    }


    @Test
    fun testPurgeExpiredDocs() {
        val now = System.currentTimeMillis()
        val shortExpire = now + 1000
        val longExpire = now + STD_TIMEOUT_MS

        var docID = "expire_me"
        createRev(docID, REV_ID_1, fleeceBody)
        Assert.assertEquals(0L, c4Collection.getDocumentExpiration(docID))

        c4Collection.setDocumentExpiration(docID, longExpire)
        Assert.assertEquals(longExpire, c4Collection.getDocumentExpiration(docID))

        c4Collection.setDocumentExpiration(docID, shortExpire)
        Assert.assertEquals(shortExpire, c4Collection.getDocumentExpiration(docID))

        docID = "expire_me_too"
        createRev(docID, REV_ID_1, fleeceBody)
        c4Collection.setDocumentExpiration(docID, shortExpire)
        Assert.assertEquals(shortExpire, c4Collection.getDocumentExpiration(docID))

        docID = "expire_me_later"
        createRev(docID, REV_ID_1, fleeceBody)
        c4Collection.setDocumentExpiration(docID, longExpire)
        Assert.assertEquals(longExpire, c4Collection.getDocumentExpiration(docID))

        docID = "dont_expire_me_at_all"
        createRev(docID, REV_ID_1, fleeceBody)

        Assert.assertEquals(0L, c4Collection.getDocumentExpiration(docID))
        Assert.assertEquals(4L, c4Collection.documentCount)

        // There should be a time at which exactly two of the docs have expired (the other two have not).
        // That time should be less than the long-expire timeout
        waitUntil(STD_TIMEOUT_MS) { 2L == c4Collection.documentCount }
    }

    @Test
    fun testDatabaseCancelExpire() {
        val docID1 = "expire_me"
        createRev(docID1, REV_ID_1, fleeceBody)

        val docID2 = "dont_expire_me"
        createRev(docID2, REV_ID_1, fleeceBody)

        Assert.assertEquals(2L, c4Collection.documentCount)

        val expire = System.currentTimeMillis() + 100
        c4Collection.setDocumentExpiration(docID1, expire)
        c4Collection.setDocumentExpiration(docID2, expire)
        c4Collection.setDocumentExpiration(docID2, 0)

        waitUntil(STD_TIMEOUT_MS) { 1L == c4Collection.documentCount }

        Assert.assertNotNull(c4Collection.getDocument(docID2))
    }

    @Test
    fun testPurgeDoc() {
        val docID = "purge_me"
        createRev(docID, REV_ID_1, fleeceBody)

        c4Collection?.purgeDocument(docID)

        Assert.assertNull(c4Database.defaultCollection.getDocument(docID))
    }

    @Test
    fun testDatabaseBlobStore() {
        val blobs = c4Database.blobStore
        Assert.assertNotNull(blobs)
        // NOTE: BlobStore is from the database. Not necessary to call free()?
    }

    // - Utility methods

    fun docId(i: Int) = i.toString().padStart(3, '0')

    private fun setupAllDocs() {
        for (i in 1..99) {
            createRev(docId(i), REV_ID_1, fleeceBody)
        }

        // Add a deleted doc to make sure it's skipped by default:
        createRev("doc-005DEL", REV_ID_1, null, C4Constants.DocumentFlags.DELETED)
    }

    private fun closeKeys(keys: kotlin.collections.Collection<C4BlobKey>) {
        for (key in keys) {
            key.close()
        }
    }

    private fun addDocWithAttachments(docID: String, attachments: List<String>): List<C4BlobKey> {
        val keys: MutableList<C4BlobKey> = ArrayList()
        val json = StringBuilder()
        json.append("{attached: [")
        for (attachment in attachments) {
            val store = c4Database.blobStore
            val key = store.create(attachment.toByteArray(StandardCharsets.UTF_8))
            keys.add(key)
            val keyStr = key.toString()
            json.append("{'")
            json.append("@type")
            json.append("': '")
            json.append("blob")
            json.append("', ")
            json.append("digest: '")
            json.append(keyStr)
            json.append("', length: ")
            json.append(attachment.length)
            json.append(", content_type: '")
            json.append("text/plain")
            json.append("'},")
        }
        json.append("]}")

        C4TestUtils.encodeJSONInDb(c4Database, json5(json.toString())).use { body ->
            // Don't try to autoclose this: See C4Document.close()
            Assert.assertNotNull(
                C4TestUtils.create(
                    c4Collection,
                    body,
                    docID,
                    C4Constants.RevisionFlags.HAS_ATTACHMENTS,
                    false,
                    false,
                    arrayOfNulls(0),
                    true,
                    0,
                    0
                )
            )
        }

        return keys
    }
}
