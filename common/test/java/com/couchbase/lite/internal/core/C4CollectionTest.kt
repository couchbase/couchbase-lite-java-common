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

import com.couchbase.lite.LiteCoreException
import com.couchbase.lite.MaintenanceType
import com.couchbase.lite.internal.utils.FileUtils
import com.couchbase.lite.internal.utils.SlowTest
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

const val POSIX_EEXIST = 17


class C4CollectionTest : C4BaseTest() {
    private fun C4DocEnumerator.nextDoc() = if (this.next()) this.document else null

    // - "Database ErrorMessages"
    @Test
    fun testDatabaseErrorMessages() {
        try {
            C4Database.getDatabase("", "", 0, 0, null)
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), e.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.WRONG_FORMAT.toLong(), e.code.toLong())
            Assert.assertTrue(e.message!!.startsWith("Parent directory does not exist"))
        }
    }

    // - "Database Info"
    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseInfo() {
        Assert.assertEquals(0, c4Database.documentCount)
        Assert.assertEquals(0, c4Database.lastSequence)
        val publicUUID = c4Database.publicUUID
        Assert.assertNotNull(publicUUID)
        Assert.assertTrue(publicUUID.isNotEmpty())
        // Weird requirements of UUIDs according to the spec:
        Assert.assertEquals(0x40, (publicUUID[6].toInt() and 0xF0).toLong())
        Assert.assertEquals(0x80, (publicUUID[8].toInt() and 0xC0).toLong())
        val privateUUID = c4Database.privateUUID
        Assert.assertNotNull(privateUUID)
        Assert.assertTrue(privateUUID.isNotEmpty())
        Assert.assertEquals(0x40, (privateUUID[6].toInt() and 0xF0).toLong())
        Assert.assertEquals(0x80, (privateUUID[8].toInt() and 0xC0).toLong())
        Assert.assertFalse(Arrays.equals(publicUUID, privateUUID))
        reopenDB()

        // Make sure UUIDs are persistent:
        val publicUUID2 = c4Database.publicUUID
        val privateUUID2 = c4Database.privateUUID
        Assert.assertArrayEquals(publicUUID, publicUUID2)
        Assert.assertArrayEquals(privateUUID, privateUUID2)
    }

    // - Database deletion lock
    @SlowTest
    @Test
    fun testDatabaseDeletionLock() {
        // Try it using the C4Db's idea of the location of the db
        try {
            val name = c4Database.dbName
            Assert.assertNotNull(name)
            val dir = c4Database.dbDirectory
            Assert.assertNotNull(dir)
            C4Database.deleteNamedDb(dir!!, name!!)
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), e.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.BUSY.toLong(), e.code.toLong())
        }

        // Try it using our idea of the location of the db
        try {
            C4Database.deleteNamedDb(dbParentDirPath, dbName)
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), e.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.BUSY.toLong(), e.code.toLong())
        }
    }

    // - Database Read-Only UUIDs
    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseReadOnlyUUIDs() {
        // Make sure UUIDs are available even if the db is opened read-only when they're first accessed.
        reopenDBReadOnly()
        Assert.assertNotNull(c4Database.publicUUID)
        Assert.assertNotNull(c4Database.privateUUID)
    }

    // - "Database OpenBundle"
    @Test
    @Throws(LiteCoreException::class)
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
                Assert.assertNotNull(bundle)
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
                Assert.assertNotNull(bundle)
            } finally {
                if (bundle != null) {
                    bundle.closeDb()
                }
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
            Assert.fail()
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), e.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), e.code.toLong())
        }
    }

    // - "Database CreateRawDoc"
    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseCreateRawDoc() {
        val store = "test"
        val key = "key"
        val meta = "meta"
        var commit = false
        c4Database.beginTransaction()
        try {
            c4Database.rawPut(store, key, meta, fleeceBody)
            commit = true
        } finally {
            c4Database.endTransaction(commit)
        }
        val doc = c4Database.rawGet(store, key)
        Assert.assertNotNull(doc)
        Assert.assertEquals(doc.key(), key)
        Assert.assertEquals(doc.meta(), meta)
        Assert.assertArrayEquals(doc.body(), fleeceBody)
        doc.close()

        // Nonexistent:
        try {
            c4Database.rawGet(store, "bogus")
            Assert.fail("Should not come here.")
        } catch (ex: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), ex.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), ex.code.toLong())
        }
    }

    // - "Database AllDocs"
    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseAllDocs() {
        setupAllDocs()
        Assert.assertEquals(99, c4Database.documentCount)
        var i: Int

        // No start or end ID:
        var iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT
        iteratorFlags = iteratorFlags and C4Constants.EnumeratorFlags.INCLUDE_BODIES.inv()
        enumerateAllDocs(c4Database, iteratorFlags).use { e ->
            Assert.assertNotNull(e)
            i = 1
            while (e.next()) {
                e.document.use { doc ->
                    Assert.assertNotNull(doc)
                    val docID = String.format(Locale.ENGLISH, "doc-%03d", i)
                    Assert.assertEquals(docID, doc.docID)
                    Assert.assertEquals(REV_ID_1, doc.revID)
                    Assert.assertEquals(REV_ID_1, doc.selectedRevID)
                    Assert.assertEquals(i.toLong(), doc.selectedSequence)
                    Assert.assertNull(doc.selectedBody)
                    // Doc was loaded without its body, but it should load on demand:
                    doc.loadRevisionBody()
                    Assert.assertArrayEquals(fleeceBody, doc.selectedBody)
                    i++
                }
            }
            Assert.assertEquals(100, i.toLong())
        }
    }

    // - "Database AllDocsInfo"
    @Test
    @Throws(LiteCoreException::class)
    fun testAllDocsInfo() {
        setupAllDocs()

        // No start or end ID:
        val iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT
        enumerateAllDocs(c4Database, iteratorFlags).use { e ->
            Assert.assertNotNull(e)
            var i = 1
            while (true) {
                val doc = e.nextDoc() ?: break
                val docID = String.format(Locale.ENGLISH, "doc-%03d", i)
                Assert.assertEquals(docID, doc.docID)
                Assert.assertEquals(REV_ID_1, doc.revID)
                Assert.assertEquals(REV_ID_1, doc.selectedRevID)
                Assert.assertEquals(i.toLong(), doc.sequence)
                Assert.assertEquals(i.toLong(), doc.selectedSequence)
                Assert.assertEquals(C4Constants.DocumentFlags.EXISTS.toLong(), doc.flags.toLong())
                i++
            }
            Assert.assertEquals(100, i.toLong())
        }
    }

    // - "Database Changes"
    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseChanges() {
        for (i in 1..99) {
            val docID = String.format(Locale.ENGLISH, "doc-%03d", i)
            createRev(docID, REV_ID_1, fleeceBody)
        }
        var seq: Long

        // Since start:
        var iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT
        iteratorFlags = iteratorFlags and C4Constants.EnumeratorFlags.INCLUDE_BODIES.inv()
        enumerateChanges(c4Database, 0, iteratorFlags).use { e ->
            Assert.assertNotNull(e)
            seq = 1
            while (true) {
                val doc = e.nextDoc() ?: break
                val docID = String.format(Locale.ENGLISH, "doc-%03d", seq)
                Assert.assertEquals(docID, doc.docID)
                Assert.assertEquals(seq, doc.selectedSequence)
                seq++
            }
            Assert.assertEquals(100L, seq)
        }
        enumerateChanges(c4Database, 6, iteratorFlags).use { e ->
            Assert.assertNotNull(e)
            seq = 7
            while (true) {
                val doc = e.nextDoc() ?: break
                val docID = String.format(Locale.ENGLISH, "doc-%03d", seq)
                Assert.assertEquals(docID, doc.docID)
                Assert.assertEquals(seq, doc.selectedSequence)
                seq++
            }
            Assert.assertEquals(100L, seq)
        }
    }

    @Test
    @Throws(LiteCoreException::class)
    fun testPurgeExpiredDocs() {
        val now = System.currentTimeMillis()
        val shortExpire = now + 1000
        val longExpire = now + STD_TIMEOUT_MS
        var docID = "expire_me"
        createRev(docID, REV_ID_1, fleeceBody)
        Assert.assertEquals(0, c4Database.getDocumentExpiration(docID))
        c4Database.setDocumentExpiration(docID, longExpire)
        Assert.assertEquals(longExpire, c4Database.getDocumentExpiration(docID))
        c4Database.setDocumentExpiration(docID, shortExpire)
        Assert.assertEquals(shortExpire, c4Database.getDocumentExpiration(docID))
        docID = "expire_me_too"
        createRev(docID, REV_ID_1, fleeceBody)
        c4Database.setDocumentExpiration(docID, shortExpire)
        Assert.assertEquals(shortExpire, c4Database.getDocumentExpiration(docID))
        docID = "expire_me_later"
        createRev(docID, REV_ID_1, fleeceBody)
        c4Database.setDocumentExpiration(docID, longExpire)
        Assert.assertEquals(longExpire, c4Database.getDocumentExpiration(docID))
        docID = "dont_expire_me_at_all"
        createRev(docID, REV_ID_1, fleeceBody)
        Assert.assertEquals(0, c4Database.getDocumentExpiration(docID))
        Assert.assertEquals(4, c4Database.documentCount)

        // There should be a time at which exactly two of the docs have expired (the other two have not).
        // That time should be less than the long-expire timeout
        waitUntil(STD_TIMEOUT_MS) { 2L == c4Database.documentCount }
    }

    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseCancelExpire() {
        val expire = System.currentTimeMillis() + 100
        val docID1 = "expire_me"
        createRev(docID1, REV_ID_1, fleeceBody)
        val docID2 = "dont_expire_me"
        createRev(docID2, REV_ID_1, fleeceBody)
        Assert.assertEquals(2, c4Database.documentCount)
        c4Database.setDocumentExpiration(docID1, expire)
        c4Database.setDocumentExpiration(docID2, expire)
        c4Database.setDocumentExpiration(docID2, 0)
        waitUntil(STD_TIMEOUT_MS) { 1L == c4Database.documentCount }
        Assert.assertNotNull(c4Database.getDocument(docID2, true))
    }

    @Test
    @Throws(LiteCoreException::class)
    fun testPurgeDoc() {
        val docID = "purge_me"
        createRev(docID, REV_ID_1, fleeceBody)
        try {
            c4Database.purgeDoc(docID)
        } catch (ignore: Exception) {
        }
        try {
            c4Database.getDocument(docID, true)
        } catch (e: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), e.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), e.code.toLong())
            Assert.assertEquals("not found", e.message)
        }
    }

    // - "Database CancelExpire"

    // - "Database CancelExpire"
    // - "Database BlobStore"
    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseBlobStore() {
        val blobs = c4Database.blobStore
        Assert.assertNotNull(blobs)
        // NOTE: BlobStore is from the database. Not necessary to call free()?
    }

    // - "Database Compact"
    @Test
    @Throws(LiteCoreException::class)
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
            compact(c4Database)
            Assert.assertTrue(store.getSize(key1) > 0)
            Assert.assertTrue(store.getSize(key2) > 0)
            Assert.assertTrue(store.getSize(key3) > 0)

            // Only reference to first blob is gone
            createRev(doc1ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertTrue(store.getSize(key2) > 0)
            Assert.assertTrue(store.getSize(key3) > 0)

            // Two references exist to the second blob, so it should still
            // exist after deleting doc002
            createRev(doc2ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertTrue(store.getSize(key2) > 0)
            Assert.assertTrue(store.getSize(key3) > 0)

            // After deleting doc4 both blobs should be gone
            createRev(doc4ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertEquals(store.getSize(key2), -1)
            Assert.assertTrue(store.getSize(key3) > 0)

            // Delete doc with legacy attachment, and it too will be gone
            createRev(doc3ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED)
            compact(c4Database)
            Assert.assertEquals(store.getSize(key1), -1)
            Assert.assertEquals(store.getSize(key2), -1)
            Assert.assertEquals(store.getSize(key3), -1)
        } finally {
            closeKeys(allKeys)
        }
    }

    @Test
    @Throws(LiteCoreException::class)
    fun testDatabaseCopySucceeds() {
        val doc1ID = "doc001"
        val doc2ID = "doc002"
        createRev(doc1ID, REV_ID_1, fleeceBody)
        createRev(doc2ID, REV_ID_1, fleeceBody)
        Assert.assertEquals(2, c4Database.documentCount)
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
        Assert.assertNotNull(copyDb)
        Assert.assertEquals(2, copyDb.documentCount)
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
            Assert.fail("Copy to non-existent directory should fail")
        } catch (ex: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), ex.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), ex.code.toLong())
        }
    }

    @Test
    fun testDatabaseCopyFromNonexistentDbFails() {
        try {
            val dbPath = c4Database.dbPath ?: throw IllegalStateException("Database has no name")
            C4Database.copyDb(
                File(File(dbPath).parentFile, "x" + C4Database.DB_EXTENSION).path,
                getScratchDirectoryPath(getUniqueName("c4_test_2")),
                getUniqueName("c4_copy_test_db"),
                flags,
                C4Constants.EncryptionAlgorithm.NONE,
                null
            )
            Assert.fail("Copy from non-existent database should fail")
        } catch (ex: LiteCoreException) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE.toLong(), ex.domain.toLong())
            Assert.assertEquals(C4Constants.LiteCoreError.NOT_FOUND.toLong(), ex.code.toLong())
        }
    }

    @Test
    @Throws(LiteCoreException::class)
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
        Assert.assertEquals(1, targetDb.documentCount)
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
            Assert.assertEquals(C4Constants.ErrorDomain.POSIX.toLong(), ex.domain.toLong())
            Assert.assertEquals(POSIX_EEXIST.toLong(), ex.code.toLong())
        }
        targetDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            flags,
            C4Constants.EncryptionAlgorithm.NONE,
            null
        )
        Assert.assertEquals(1, targetDb.documentCount)
        targetDb.close()
    }

    @Throws(LiteCoreException::class)
    private fun setupAllDocs() {
        for (i in 1..99) {
            val docID = String.format(Locale.ENGLISH, "doc-%03d", i)
            createRev(docID, REV_ID_1, fleeceBody)
        }

        // Add a deleted doc to make sure it's skipped by default:
        createRev("doc-005DEL", REV_ID_1, null, C4Constants.DocumentFlags.DELETED)
    }

    private fun closeKeys(keys: Collection<C4BlobKey>) {
        for (key in keys) {
            key.close()
        }
    }

    @Throws(LiteCoreException::class)
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
        val jsonStr = json5(json.toString())
        val doc: C4Document
        c4Database.encodeJSON(jsonStr).use { body ->
            // Save document:
            doc = c4Database.putDocument(
                body,
                docID,
                C4Constants.RevisionFlags.HAS_ATTACHMENTS,
                false,
                false, arrayOfNulls(0),
                true,
                0,
                0
            )
        }
        Assert.assertNotNull(doc)
        doc.close()
        return keys
    }

    private fun compact(db: C4Database) {
        try {
            db.performMaintenance(MaintenanceType.COMPACT)
        } catch (e: LiteCoreException) {
            throw IllegalStateException("Db compation failed", e)
        }
    }
}

