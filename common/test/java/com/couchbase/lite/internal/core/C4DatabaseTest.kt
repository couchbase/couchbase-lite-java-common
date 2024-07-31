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
import com.couchbase.lite.internal.fleece.FLSliceResult
import com.couchbase.lite.internal.utils.FileUtils
import com.couchbase.lite.internal.utils.VerySlowTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.experimental.and


class C4DatabaseTest : C4BaseTest() {
    private val POSIX_EEXIST = 17

    private val mockDatabase = object : C4Database.NativeImpl {
        override fun nOpen(
            parentDir: String,
            name: String,
            flags: Int,
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
            flags: Int,
            algorithm: Int,
            encryptionKey: ByteArray?,
        ) = Unit

        override fun nDelete(db: Long) = Unit
        override fun nDeleteNamed(name: String, dir: String) = Unit
        override fun nGetPublicUUID(db: Long): ByteArray = byteArrayOf()
        override fun nGetPrivateUUID(db: Long): ByteArray = byteArrayOf()
        override fun nBeginTransaction(db: Long) = Unit
        override fun nEndTransaction(db: Long, commit: Boolean) = Unit
        override fun nMaintenance(db: Long, type: Int): Boolean = true
        override fun nRekey(db: Long, keyType: Int, newKey: ByteArray?) = Unit
        override fun nSetCookie(db: Long, url: String?, setCookieHeader: String?, acceptParents: Boolean) = Unit
        override fun nGetCookies(db: Long, url: String): String = "test_cookies"
        override fun nGetSharedFleeceEncoder(db: Long): Long = 1L
        override fun nEncodeJSON(db: Long, jsonData: ByteArray): FLSliceResult = FLSliceResult.createTestSlice()
        override fun nGetFLSharedKeys(db: Long): Long = 1L
        override fun nGetFlags(peer: Long): Int = 0

        override fun nGetScopeNames(peer: Long): MutableSet<String> = mutableSetOf()
        override fun nHasScope(peer: Long, scope: String): Boolean = true
        override fun nGetCollectionNames(peer: Long, scope: String): MutableSet<String> = mutableSetOf()
        override fun nDeleteCollection(peer: Long, scope: String, collection: String) = Unit
    }

    // - Test with C4Database mock

    // Test if java platform receives a peer value for c4Database, a java c4Database should exist
    @Test
    fun testOpenDb() {
        C4Database.getDatabase(mockDatabase, dbParentDirPath, "test_DB", 0, 0, null).use { c4Database ->
            assertNotNull(c4Database)
        }
    }

    @Test
    fun testGetSharedFleeceEncoder() {
        C4Database.getDatabase(mockDatabase, dbParentDirPath, "test_DB", 0, 0, null).use { c4Database ->
            assertNotNull(c4Database.sharedFleeceEncoder)
        }
    }

    @Test
    fun testGetFLSharedKeys() {
        C4Database.getDatabase(mockDatabase, dbParentDirPath, "test_DB", 0, 0, null).use { c4Database ->
            assertNotNull(c4Database.flSharedKeys)
        }
    }

    /**
     * Functional Tests
     */

    // - "Database ErrorMessages"
    @Test
    fun testDatabaseErrorMessages() {
        try {
            C4Database.getDatabase("", "", 0, 0, null)
            fail()
        } catch (e: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            assertEquals(C4Constants.LiteCoreError.WRONG_FORMAT, e.code)
            e.message?.let { assertTrue(it.startsWith("Parent directory does not exist")) }
        }
    }

    // - "Database Info"
    @Test
    fun testDatabaseInfo() {
        val publicUUID = c4Database.publicUUID
        assertNotNull(publicUUID)
        assertTrue(publicUUID.isNotEmpty())

        //Weird requirements of UUIDs according to the spec
        assertEquals(0x40.toByte(), (publicUUID[6] and 0xF0.toByte()))
        assertEquals(0x80.toUByte(), (publicUUID[8].toUByte() and 0xC0.toUByte()))
        val privateUUID = c4Database.privateUUID
        assertNotNull(privateUUID)
        assertTrue(privateUUID.isNotEmpty())
        assertEquals(0x40.toByte(), (privateUUID[6] and 0xF0.toByte()))
        assertEquals(0x80.toUByte(), (privateUUID[8].toUByte() and 0xC0.toUByte()))
        assertFalse(publicUUID.contentEquals(privateUUID))

        reopenDB()

        //Make sure UUIDs are persistent
        val publicUUID2 = c4Database.publicUUID
        val privateUUID2 = c4Database.privateUUID
        assertArrayEquals(publicUUID, publicUUID2)
        assertArrayEquals(privateUUID, privateUUID2)
    }

    // - Database deletion lock
    @VerySlowTest
    @Test
    fun testDatabaseDeletionLock() {
        // Try it using the C4Db's idea of the location of the db
        try {
            val name = c4Database.dbName
            assertNotNull(name)
            val dir = c4Database.dbDirectory
            assertNotNull(dir)
            if (dir != null && name != null) {
                C4Database.deleteNamedDb(dir, name)
                fail()
            }

        } catch (e: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            assertEquals(C4Constants.LiteCoreError.BUSY, e.code)
        }

        // Try it using our idea of the location of the db
        try {
            C4Database.deleteNamedDb(dbParentDirPath, dbName)
            fail()
        } catch (e: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            assertEquals(C4Constants.LiteCoreError.BUSY, e.code)
        }
    }

    // - Database Read-Only UUIDs
    @Test
    fun testDatabaseReadOnlyUUIDs() {
        reopenDBReadOnly()
        assertNotNull(c4Database.publicUUID)
        assertNotNull(c4Database.privateUUID)
    }

    // - "Database OpenBundle"
    @Test
    fun testDatabaseOpenBundle() {
        val bundleDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"))
        val bundleName = getUniqueName("bundle")
        val flags = C4Constants.DatabaseFlags.SHARED_KEYS
        try {
            var bundle: C4Database? = null

            // Open nonexistent bundle with the create flag.
            try {
                bundle = C4Database.getDatabase(
                    bundleDirPath,
                    bundleName,
                    flags or C4Constants.DatabaseFlags.CREATE,
                    C4Constants.EncryptionAlgorithm.NONE,
                    null
                )
                assertNotNull(bundle)
            } finally {
                if (bundle != null) {
                    bundle.closeDb()
                    bundle = null
                }
            }

            // Reopen without 'create' flag:
            try {
                bundle = C4Database.getDatabase(
                    bundleDirPath,
                    bundleName,
                    flags,
                    C4Constants.EncryptionAlgorithm.NONE,
                    null
                )
                assertNotNull(bundle)
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
                C4Constants.DatabaseFlags.SHARED_KEYS,
                C4Constants.EncryptionAlgorithm.NONE,
                null
            )
            fail()
        } catch (e: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain)
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code)
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
            assertNotNull(store)
            compact(c4Database)
            assertTrue(store.getSize(key1) > 0)
            assertTrue(store.getSize(key2) > 0)
            assertTrue(store.getSize(key3) > 0)

            // Only reference to first blob is gone
            createRev(doc1ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            assertEquals(store.getSize(key1), -1)
            assertTrue(store.getSize(key2) > 0)
            assertTrue(store.getSize(key3) > 0)

            // Two references exist to the second blob, so it should still
            // exist after deleting doc002
            createRev(doc2ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            assertEquals(store.getSize(key1), -1)
            assertTrue(store.getSize(key2) > 0)
            assertTrue(store.getSize(key3) > 0)

            // After deleting doc4 both blobs should be gone
            createRev(doc4ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            assertEquals(store.getSize(key1), -1)
            assertEquals(store.getSize(key2), -1)
            assertTrue(store.getSize(key3) > 0)

            // Delete doc with legacy attachment, and it too will be gone
            createRev(doc3ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            assertEquals(store.getSize(key1), -1)
            assertEquals(store.getSize(key2), -1)
            assertEquals(store.getSize(key3), -1)
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
                flags,
                C4Constants.EncryptionAlgorithm.NONE,
                null
            )
            fail("Copy to non-existent directory should fail")
        } catch (ex: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), ex.domain.toLong())
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), ex.code.toLong())
        }
    }

    @Test
    fun testDatabaseCopyFromNonexistentDbFails() {
        try {
            C4Database.copyDb(
                File(c4Database.dbPath?.let { File(it).parentFile }, "x" + C4Database.DB_EXTENSION).path,
                getScratchDirectoryPath(getUniqueName("c4_test_2")),
                getUniqueName("c4_copy_test_db"),
                flags,
                C4Constants.EncryptionAlgorithm.NONE,
                null
            )
            fail("Copy from non-existent database should fail")
        } catch (ex: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain)
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code)
        }
    }


    // - Scopes and Collections

    //  Test create 1 scope and 1 collection
    @Test
    fun testCreateScopeAndCollection() {
        assertEquals(1, c4Database.scopeNames.size)
        assertEquals(setOf(Scope.DEFAULT_NAME), c4Database.scopeNames)

        c4Database.addCollection("test_scope", "test_coll")
        assertEquals(2, c4Database.scopeNames.size)
        assertTrue(c4Database.hasScope("test_scope"))
        assertEquals(setOf(Scope.DEFAULT_NAME, "test_scope"), c4Database.scopeNames)

        assertEquals(1, c4Database.getCollectionNames("test_scope").size)
        assertEquals(setOf("test_coll"), c4Database.getCollectionNames("test_scope"))
    }

    // Test create multiple collections in a scope
    @Test
    fun testCreateMultipleCollections() {
        c4Database.addCollection("test_scope", "test_coll_1")
        c4Database.addCollection("test_scope", "test_coll_2")
        c4Database.addCollection("test_scope", "test_coll_3")

        assertEquals(2, c4Database.scopeNames.size)
        assertEquals(3, c4Database.getCollectionNames("test_scope").size)
        assertEquals(setOf("test_coll_1", "test_coll_2", "test_coll_3"), c4Database.getCollectionNames("test_scope"))
    }

    // Test DeleteDefaultCollection are in
    // cbl-java-common @ a2de0d43d09ce64fd3a1301dc35

    // After deleting a collection, we can no longer get that collection (make sure test doesn't crash)
    @Test(expected = LiteCoreException::class)
    fun testDeleteCollection() {
        val coll = c4Database.addCollection("test_scope", "test_coll")
        assertNotNull(c4Database.getCollection("test_scope", "test_coll"))
        c4Database.deleteCollection("test_scope", "test_coll")

        //getting a non-existent collection should return null, not an exception
        assertNull(c4Database.getCollection("test_scope", "test_coll"))
        assertEquals(0, coll.documentCount)

        coll.createDocument("1", null, 0)
    }

    // Close Database and collection operations will throw error
    @Test(expected = LiteCoreException::class)
    fun testCollectionOnClosedDB() {
        val col = c4Database.getCollection(Scope.DEFAULT_NAME, Collection.DEFAULT_NAME)
        c4Database.closeDb()
        c4Database = null // prevent @after from closing the database again
        col?.createDocument("1", null, 0)
    }


    /**
     * Deprecated tests that should be in C4Collection instead of C4Database
     */

    @Test
    fun testDatabaseCopySucceeds() {
        val doc1ID = "doc001"
        val doc2ID = "doc002"
        createRev(doc1ID, REV_ID_1, fleeceBody)
        createRev(doc2ID, REV_ID_1, fleeceBody)
        assertEquals(2L, c4Database.defaultCollection?.documentCount)
        val srcDbPath = c4Database.dbPath
        val dbName = getUniqueName("c4_copy_test_db")
        val dstParentDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"))
        C4Database.copyDb(
            srcDbPath!!,
            dstParentDirPath,
            dbName,
            flags,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        )
        val copyDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            flags,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        )
        assertNotNull(copyDb)
        assertEquals(2L, copyDb.defaultCollection?.documentCount)
    }

    @Test
    fun testDatabaseCopyToExistingDBFails() {
        createRev("doc001", REV_ID_1, fleeceBody)
        createRev("doc002", REV_ID_1, fleeceBody)
        val srcDbPath = c4Database.dbPath
        val dstParentDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"))
        var targetDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            flags,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        )
        createRev(targetDb, "doc001", REV_ID_1, fleeceBody)
        assertEquals(1L, targetDb.defaultCollection?.documentCount)
        targetDb.close()
        try {
            C4Database.copyDb(
                srcDbPath!!,
                dstParentDirPath,
                dbName,
                flags,
                C4Constants.EncryptionAlgorithm.NONE,
                null
            )
        } catch (ex: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.POSIX, ex.domain)
            assertEquals(POSIX_EEXIST, ex.code)
        }
        targetDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            flags,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        )
        assertEquals(1L, targetDb.defaultCollection?.documentCount)
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
        val allDocs = enumerateAllDocs(c4Database, iteratorFlags)
        assertNotNull(allDocs)
        while (allDocs.next()) {
            val doc = allDocs.document
            assertEquals(docId(i), doc.docID)
            assertEquals(REV_ID_1, doc.revID)
            assertEquals(REV_ID_1, doc.selectedRevID)
            assertEquals(i.toLong(), doc.sequence)
            assertEquals(i.toLong(), doc.selectedSequence)
            assertTrue(doc.docExists())
            i++
        }
        assertEquals(100, i)
    }


    @Test
    fun testPurgeExpiredDocs() {
        val now = System.currentTimeMillis()
        val shortExpire = now + 1000
        val longExpire = now + STD_TIMEOUT_MS
        var docID = "expire_me"
        createRev(docID, REV_ID_1, fleeceBody)
        assertEquals(0L, c4Database.defaultCollection?.getDocumentExpiration(docID))
        c4Database.defaultCollection?.setDocumentExpiration(docID, longExpire)
        assertEquals(longExpire, c4Database.defaultCollection?.getDocumentExpiration(docID))
        c4Database.defaultCollection?.setDocumentExpiration(docID, shortExpire)
        assertEquals(shortExpire, c4Database.defaultCollection?.getDocumentExpiration(docID))
        docID = "expire_me_too"
        createRev(docID, REV_ID_1, fleeceBody)
        c4Database.defaultCollection?.setDocumentExpiration(docID, shortExpire)
        assertEquals(shortExpire, c4Database.defaultCollection?.getDocumentExpiration(docID))
        docID = "expire_me_later"
        createRev(docID, REV_ID_1, fleeceBody)
        c4Database.defaultCollection?.setDocumentExpiration(docID, longExpire)
        assertEquals(longExpire, c4Database.defaultCollection?.getDocumentExpiration(docID))
        docID = "dont_expire_me_at_all"
        createRev(docID, REV_ID_1, fleeceBody)
        assertEquals(0L, c4Database.defaultCollection?.getDocumentExpiration(docID))
        assertEquals(4L, c4Database.defaultCollection?.documentCount)

        // There should be a time at which exactly two of the docs have expired (the other two have not).
        // That time should be less than the long-expire timeout
        waitUntil(STD_TIMEOUT_MS) { 2L == c4Database.defaultCollection?.documentCount }
    }

    @Test
    fun testDatabaseCancelExpire() {
        val docID1 = "expire_me"
        createRev(docID1, REV_ID_1, fleeceBody)

        val docID2 = "dont_expire_me"
        createRev(docID2, REV_ID_1, fleeceBody)

        assertEquals(2L, c4Database.defaultCollection?.documentCount)

        val expire = System.currentTimeMillis() + 100
        c4Database.defaultCollection?.setDocumentExpiration(docID1, expire)
        c4Database.defaultCollection?.setDocumentExpiration(docID2, expire)
        c4Database.defaultCollection?.setDocumentExpiration(docID2, 0)

        waitUntil(STD_TIMEOUT_MS) { 1L == c4Database.defaultCollection?.documentCount }

        assertNotNull(c4Database.defaultCollection?.getDocument(docID2))
    }

    @Test
    fun testPurgeDoc() {
        val docID = "purge_me"
        createRev(docID, REV_ID_1, fleeceBody)
        try {
            c4Database.defaultCollection?.purgeDocument(docID)
        } catch (ignore: Exception) {
        }
        try {
            c4Database.defaultCollection?.getDocument(docID)
        } catch (e: LiteCoreException) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), e.domain.toLong())
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), e.code.toLong())
            assertEquals("not found", e.message)
        }
    }

    @Test
    fun testDatabaseBlobStore() {
        val blobs = c4Database.blobStore
        assertNotNull(blobs)
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

        // Save document:
        val doc = C4Document.create(
            c4Database,
            c4Database.encodeJSON(json5(json.toString())),
            docID,
            C4Constants.RevisionFlags.HAS_ATTACHMENTS,
            false,
            false, arrayOfNulls(0),
            true,
            0,
            0
        )

        assertNotNull(doc)

        return keys
    }

    private fun compact(db: C4Database) {
        try {
            db.performMaintenance(MaintenanceType.COMPACT)
        } catch (e: LiteCoreException) {
            throw IllegalStateException("Db compaction failed", e)
        }
    }
}
