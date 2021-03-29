//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.utils.FileUtils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class C4DatabaseTest extends C4BaseTest {

    static C4Document nextDocument(C4DocEnumerator e) throws LiteCoreException {
        return e.next() ? e.getDocument() : null;
    }

    // - "Database ErrorMessages"
    @Test
    public void testDatabaseErrorMessages() {
        try {
            C4Database.getDatabase("", "", 0, 0, null);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.WRONG_FORMAT, e.code);
            assertTrue(e.getMessage().startsWith("Parent directory does not exist"));
        }

        try {
            c4Database.get("a", true);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
            assertEquals("not found", e.getMessage());
        }

        try {
            c4Database.get(null, true);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
            assertEquals("not found", e.getMessage());
        }

        // NOTE: c4error_getMessage() is not supported by Java
    }

    // - "Database Info"
    @Test
    public void testDatabaseInfo() throws LiteCoreException {
        assertEquals(0, c4Database.getDocumentCount());
        assertEquals(0, c4Database.getLastSequence());

        byte[] publicUUID = c4Database.getPublicUUID();
        assertNotNull(publicUUID);
        assertTrue(publicUUID.length > 0);
        // Weird requirements of UUIDs according to the spec:
        assertEquals(0x40, (publicUUID[6] & 0xF0));
        assertEquals(0x80, (publicUUID[8] & 0xC0));
        byte[] privateUUID = c4Database.getPrivateUUID();
        assertNotNull(privateUUID);
        assertTrue(privateUUID.length > 0);
        assertEquals(0x40, (privateUUID[6] & 0xF0));
        assertEquals(0x80, (privateUUID[8] & 0xC0));
        assertFalse(Arrays.equals(publicUUID, privateUUID));

        reopenDB();

        // Make sure UUIDs are persistent:
        byte[] publicUUID2 = c4Database.getPublicUUID();
        byte[] privateUUID2 = c4Database.getPrivateUUID();
        assertArrayEquals(publicUUID, publicUUID2);
        assertArrayEquals(privateUUID, privateUUID2);
    }

    // - Database deletion lock
    @Test
    public void testDatabaseDeletionLock() {
        // Try it using the C4Db's idea of the location of the db
        try {
            final String name = c4Database.getDbName();
            assertNotNull(name);
            final String dir = c4Database.getDbDirectory();
            assertNotNull(dir);
            C4Database.deleteNamedDb(dir, name);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.BUSY, e.code);
        }

        // Try it using our idea of the location of the db
        try {
            C4Database.deleteNamedDb(dbParentDirPath, dbName);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.BUSY, e.code);
        }
    }

    // - Database Read-Only UUIDs
    @Test
    public void testDatabaseReadOnlyUUIDs() throws LiteCoreException {
        // Make sure UUIDs are available even if the db is opened read-only when they're first accessed.
        reopenDBReadOnly();
        assertNotNull(c4Database.getPublicUUID());
        assertNotNull(c4Database.getPrivateUUID());
    }

    // - "Database OpenBundle"
    @Test
    public void testDatabaseOpenBundle() throws LiteCoreException {
        final String bundleDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"));
        final String bundleName = getUniqueName("bundle");

        final int flags = C4Constants.DatabaseFlags.SHARED_KEYS;
        try {
            C4Database bundle = null;

            // Open nonexistent bundle with the create flag.
            try {
                bundle = C4Database.getDatabase(
                    bundleDirPath,
                    bundleName,
                    flags | C4Constants.DatabaseFlags.CREATE,
                    C4Constants.EncryptionAlgorithm.NONE,
                    null);

                assertNotNull(bundle);
            }
            finally {
                if (bundle != null) {
                    bundle.closeDb();
                    bundle = null;
                }
            }

            // Reopen without 'create' flag:
            try {
                bundle = C4Database.getDatabase(
                    bundleDirPath,
                    bundleName,
                    flags,
                    C4Constants.EncryptionAlgorithm.NONE,
                    null);

                assertNotNull(bundle);
            }
            finally {
                if (bundle != null) { bundle.closeDb(); }
            }
        }
        finally {
            FileUtils.eraseFileOrDir(bundleDirPath);
        }
    }

    // Open nonexistent bundle without the create flag.
    @Test
    public void testDatabaseOpenNonExistentBundleWithoutCreateFlagFails() throws LiteCoreException {
        try {
            C4Database.getDatabase(
                getScratchDirectoryPath(getUniqueName("c4_test_2")),
                getUniqueName("bundle"),
                C4Constants.DatabaseFlags.SHARED_KEYS,
                C4Constants.EncryptionAlgorithm.NONE,
                null);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
        }
    }

    // - "Database CreateRawDoc"
    @Test
    public void testDatabaseCreateRawDoc() throws LiteCoreException {
        final String store = "test";
        final String key = "key";
        final String meta = "meta";
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            c4Database.rawPut(store, key, meta, fleeceBody);
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        C4RawDocument doc = c4Database.rawGet(store, key);
        assertNotNull(doc);
        assertEquals(doc.key(), key);
        assertEquals(doc.meta(), meta);
        assertArrayEquals(doc.body(), fleeceBody);
        doc.close();

        // Nonexistent:
        try {
            c4Database.rawGet(store, "bogus");
            fail("Should not come here.");
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }
    }

    // - "Database AllDocs"
    @Test
    public void testDatabaseAllDocs() throws LiteCoreException {
        setupAllDocs();

        assertEquals(99, c4Database.getDocumentCount());

        int i;

        // No start or end ID:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        iteratorFlags &= ~C4Constants.EnumeratorFlags.INCLUDE_BODIES;

        try (C4DocEnumerator e = enumerateAllDocs(c4Database, iteratorFlags)) {
            assertNotNull(e);
            i = 1;
            while (e.next()) {
                try (C4Document doc = e.getDocument()) {
                    assertNotNull(doc);
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(REV_ID_1, doc.getRevID());
                    assertEquals(REV_ID_1, doc.getSelectedRevID());
                    assertEquals(i, doc.getSelectedSequence());
                    assertNull(doc.getSelectedBody());
                    // Doc was loaded without its body, but it should load on demand:
                    doc.loadRevisionBody();
                    assertArrayEquals(fleeceBody, doc.getSelectedBody());
                    i++;
                }
            }
            assertEquals(100, i);
        }
    }

    // - "Database AllDocsInfo"
    @Test
    public void testAllDocsInfo() throws LiteCoreException {
        setupAllDocs();

        // No start or end ID:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        try (C4DocEnumerator e = enumerateAllDocs(c4Database, iteratorFlags)) {
            assertNotNull(e);
            int i = 1;
            while (true) {
                try (C4Document doc = nextDocument(e)) {
                    if (doc == null) { break; }
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(REV_ID_1, doc.getRevID());
                    assertEquals(REV_ID_1, doc.getSelectedRevID());
                    assertEquals(i, doc.getSequence());
                    assertEquals(i, doc.getSelectedSequence());
                    assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
                    i++;
                }
            }
            assertEquals(100, i);
        }
    }

    // - "Database Changes"
    @Test
    public void testDatabaseChanges() throws LiteCoreException {
        for (int i = 1; i < 100; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
            createRev(docID, REV_ID_1, fleeceBody);
        }

        long seq;

        // Since start:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        iteratorFlags &= ~C4Constants.EnumeratorFlags.INCLUDE_BODIES;

        try (C4DocEnumerator e = enumerateChanges(c4Database, 0, iteratorFlags)) {
            assertNotNull(e);
            seq = 1;
            while (true) {
                try (C4Document doc = nextDocument(e)) {
                    if (doc == null) { break; }
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", seq);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(seq, doc.getSelectedSequence());
                    seq++;
                }
            }
            assertEquals(100L, seq);
        }

        // Since 6:
        try (C4DocEnumerator e = enumerateChanges(c4Database, 6, iteratorFlags)) {
            assertNotNull(e);
            seq = 7;
            while (true) {
                try (C4Document doc = nextDocument(e)) {
                    if (doc == null) { break; }
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", seq);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(seq, doc.getSelectedSequence());
                    seq++;
                }
            }
            assertEquals(100L, seq);
        }
    }

    // - "Database Expired"
    @Test
    public void testDatabaseExpired() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, REV_ID_1, fleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 1;
        c4Database.setExpiration(docID, expire);

        expire = System.currentTimeMillis() / 1000 + 2;
        c4Database.setExpiration(docID, expire);
        c4Database.setExpiration(docID, expire);

        String docID2 = "expire_me_too";
        createRev(docID2, REV_ID_1, fleeceBody);
        c4Database.setExpiration(docID2, expire);

        String docID3 = "dont_expire_me";
        createRev(docID3, REV_ID_1, fleeceBody);

        try { Thread.sleep(2 * 1000); }
        catch (InterruptedException ignore) { }

        assertEquals(expire, c4Database.getExpiration(docID));
        assertEquals(expire, c4Database.getExpiration(docID2));
        assertEquals(expire, c4Database.nextDocExpiration());
    }

    @Test
    public void testPurgeExpiredDocs() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, REV_ID_1, fleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 1;
        c4Database.setExpiration(docID, expire);

        expire = System.currentTimeMillis() / 1000 + 2;
        c4Database.setExpiration(docID, expire);

        String docID2 = "expire_me_too";
        createRev(docID2, REV_ID_1, fleeceBody);
        c4Database.setExpiration(docID2, expire);

        try { Thread.sleep(3 * 1000); }
        catch (InterruptedException ignore) { }

        long cnt = c4Database.purgeExpiredDocs();

        assertEquals(cnt, 2);
    }

    @Test
    public void testPurgeDoc() throws LiteCoreException {
        String docID = "purge_me";
        createRev(docID, REV_ID_1, fleeceBody);

        try { c4Database.purgeDoc(docID); }
        catch (Exception ignore) { }

        try { c4Database.get(docID, true); }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, e.code);
            assertEquals("not found", e.getMessage());
        }
    }

    // - "Database CancelExpire"
    @Test
    public void testDatabaseCancelExpire() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, REV_ID_1, fleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 2;
        c4Database.setExpiration(docID, expire);
        c4Database.setExpiration(docID, 0);

        try { Thread.sleep(2 * 1000); }
        catch (InterruptedException ignore) { }

        assertNotNull(c4Database.get(docID, true));
    }

    // - "Database BlobStore"
    @Test
    public void testDatabaseBlobStore() throws LiteCoreException {
        C4BlobStore blobs = c4Database.getBlobStore();
        assertNotNull(blobs);
        // NOTE: BlobStore is from the database. Not necessary to call free()?
    }

    // - "Database Compact"
    @Test
    public void testDatabaseCompact() throws LiteCoreException {
        String doc1ID = "doc001";
        String doc2ID = "doc002";
        String doc3ID = "doc003";
        String doc4ID = "doc004";
        String content1 = "This is the first attachment";
        String content2 = "This is the second attachment";
        String content3 = "This is the third attachment";

        Set<C4BlobKey> allKeys = new HashSet<>();

        final C4BlobKey key1;
        final C4BlobKey key2;
        final C4BlobKey key3;
        try {
            List<String> atts = new ArrayList<>();
            List<C4BlobKey> keys;
            c4Database.beginTransaction();
            try {
                atts.add(content1);
                keys = addDocWithAttachments(doc1ID, atts);
                allKeys.addAll(keys);
                key1 = keys.get(0);

                atts.clear();
                atts.add(content2);
                keys = addDocWithAttachments(doc2ID, atts);
                allKeys.addAll(keys);
                key2 = keys.get(0);

                keys = addDocWithAttachments(doc4ID, atts);
                allKeys.addAll(keys);

                atts.clear();
                atts.add(content3);
                keys = addDocWithAttachments(doc3ID, atts);
                allKeys.addAll(keys);
                key3 = keys.get(0); // legacy: TODO need to implement legacy support
            }
            finally {
                c4Database.endTransaction(true);
            }

            C4BlobStore store = c4Database.getBlobStore();
            assertNotNull(store);
            c4Database.compact();
            assertTrue(store.getSize(key1) > 0);
            assertTrue(store.getSize(key2) > 0);
            assertTrue(store.getSize(key3) > 0);

            // Only reference to first blob is gone
            createRev(doc1ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
            c4Database.compact();
            assertEquals(store.getSize(key1), -1);
            assertTrue(store.getSize(key2) > 0);
            assertTrue(store.getSize(key3) > 0);

            // Two references exist to the second blob, so it should still
            // exist after deleting doc002
            createRev(doc2ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
            c4Database.compact();
            assertEquals(store.getSize(key1), -1);
            assertTrue(store.getSize(key2) > 0);
            assertTrue(store.getSize(key3) > 0);

            // After deleting doc4 both blobs should be gone
            createRev(doc4ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
            c4Database.compact();
            assertEquals(store.getSize(key1), -1);
            assertEquals(store.getSize(key2), -1);
            assertTrue(store.getSize(key3) > 0);

            // Delete doc with legacy attachment, and it too will be gone
            createRev(doc3ID, REV_ID_2, null, C4Constants.DocumentFlags.DELETED);
            c4Database.compact();
            assertEquals(store.getSize(key1), -1);
            assertEquals(store.getSize(key2), -1);
            assertEquals(store.getSize(key3), -1);
        }
        finally {
            closeKeys(allKeys);
        }
    }

    @Test
    public void testDatabaseCopySucceeds() throws LiteCoreException {
        String doc1ID = "doc001";
        String doc2ID = "doc002";

        createRev(doc1ID, REV_ID_1, fleeceBody);
        createRev(doc2ID, REV_ID_1, fleeceBody);
        assertEquals(2, c4Database.getDocumentCount());

        final String srcDbPath = c4Database.getDbPath();

        final String dbName = getUniqueName("c4_copy_test_db");
        final String dstParentDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"));

        C4Database.copyDb(
            srcDbPath,
            dstParentDirPath,
            dbName,
            getFlags(),
            C4Constants.EncryptionAlgorithm.NONE,
            null);

        final C4Database copyDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            getFlags(),
            C4Constants.EncryptionAlgorithm.NONE,
            null);

        assertNotNull(copyDb);
        assertEquals(2, copyDb.getDocumentCount());
    }

    @Test
    public void testDatabaseCopyToNonexistentDirectoryFails() {
        try {
            C4Database.copyDb(
                c4Database.getDbPath(),
                new File(new File(getScratchDirectoryPath(getUniqueName("a")), "aa"), "aaa").getPath(),
                getUniqueName("c4_copy_test_db"),
                getFlags(),
                C4Constants.EncryptionAlgorithm.NONE,
                null);
            fail("Copy to non-existent directory should fail");
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }
    }

    @Test
    public void testDatabaseCopyFromNonexistentDbFails() {
        try {
            C4Database.copyDb(
                new File(new File(c4Database.getDbPath()).getParentFile(), "x" + C4Database.DB_EXTENSION).getPath(),
                getScratchDirectoryPath(getUniqueName("c4_test_2")),
                getUniqueName("c4_copy_test_db"),
                getFlags(),
                C4Constants.EncryptionAlgorithm.NONE,
                null);
            fail("Copy from non-existent database should fail");
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.NOT_FOUND, ex.code);
        }
    }

    @Test
    public void testDatabaseCopyToExistingDBFails() throws LiteCoreException {
        createRev("doc001", REV_ID_1, fleeceBody);
        createRev("doc002", REV_ID_1, fleeceBody);

        final String srcDbPath = c4Database.getDbPath();

        final String dstParentDirPath = getScratchDirectoryPath(getUniqueName("c4_test_2"));

        C4Database targetDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            getFlags(),
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        createRev(targetDb, "doc001", REV_ID_1, fleeceBody);
        assertEquals(1, targetDb.getDocumentCount());
        targetDb.close();

        try {
            C4Database.copyDb(
                srcDbPath,
                dstParentDirPath,
                dbName,
                getFlags(),
                C4Constants.EncryptionAlgorithm.NONE,
                null);
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.POSIX, ex.domain);
            assertEquals(C4Constants.PosixError.EEXIST, ex.code);
        }

        targetDb = C4Database.getDatabase(
            dstParentDirPath,
            dbName,
            getFlags(),
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertEquals(1, targetDb.getDocumentCount());
        targetDb.close();
    }

    private void setupAllDocs() throws LiteCoreException {
        for (int i = 1; i < 100; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
            createRev(docID, REV_ID_1, fleeceBody);
        }

        // Add a deleted doc to make sure it's skipped by default:
        createRev("doc-005DEL", REV_ID_1, null, C4Constants.DocumentFlags.DELETED);
    }

    private void closeKeys(Collection<C4BlobKey> keys) { for (C4BlobKey key: keys) { key.close(); } }

    private List<C4BlobKey> addDocWithAttachments(String docID, List<String> attachments)
        throws LiteCoreException {
        List<C4BlobKey> keys = new ArrayList<>();
        StringBuilder json = new StringBuilder();
        json.append("{attached: [");
        for (String attachment: attachments) {
            C4BlobStore store = c4Database.getBlobStore();
            C4BlobKey key = store.create(attachment.getBytes(StandardCharsets.UTF_8));
            keys.add(key);
            String keyStr = key.toString();
            json.append("{'");
            json.append("@type");
            json.append("': '");
            json.append("blob");
            json.append("', ");
            json.append("digest: '");
            json.append(keyStr);
            json.append("', length: ");
            json.append(attachment.length());
            json.append(", content_type: '");
            json.append("text/plain");
            json.append("'},");
        }
        json.append("]}");
        String jsonStr = json5(json.toString());

        final C4Document doc;
        try (FLSliceResult body = c4Database.encodeJSON(jsonStr)) {
            // Save document:
            doc = c4Database.put(
                body,
                docID,
                C4Constants.RevisionFlags.HAS_ATTACHMENTS,
                false,
                false,
                new String[0],
                true,
                0,
                0);
        }
        assertNotNull(doc);
        doc.close();

        return keys;
    }
}

