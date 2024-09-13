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
package com.couchbase.lite.internal.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class C4BlobStoreTest extends C4BaseTest {
    private File blobDir;
    private C4BlobStore blobStore;
    private C4BlobKey bogusKey;

    @Before
    public final void setUpC4BlobStoreTest() throws CouchbaseLiteException {
        blobDir = new File(getScratchDirectoryPath(getUniqueName("cbl_blobs")));
        try {
            blobStore = new C4TestUtils.ManagedC4BlobStore(
                blobDir.getCanonicalPath() + File.separator,
                C4Database.DB_FLAGS);
            bogusKey = C4BlobKey.create("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVU=");
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        catch (IOException e) { throw new CouchbaseLiteError("IO error setting up directories", e); }
    }

    @After
    public final void tearDownC4BlobStoreTest() {
        bogusKey.close();

        final C4BlobStore store = blobStore;
        blobStore = null;

        if (store != null) { C4TestUtils.delete(store); }

        if (blobDir != null) { FileUtils.eraseFileOrDir(blobDir); }
    }

    // - parse blob keys
    @Test
    public void testParseBlobKeys() throws LiteCoreException {
        try (C4BlobKey key = C4BlobKey.create("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVU=")) {
            assertEquals("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVU=", key.toString());
        }
    }

    // - parse invalid blob keys
    @Test
    public void testParseInvalidBlobKeys() {
        parseInvalidBlobKeys("");
        parseInvalidBlobKeys("rot13-xxxx");
        parseInvalidBlobKeys("sha1-");
        parseInvalidBlobKeys("sha1-VVVVVVVVVVVVVVVVVVVVVV");
        parseInvalidBlobKeys("sha1-VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVU");
    }

    // - missing blobs
    @Test
    public void testMissingContent() {
        assertEquals(-1, blobStore.getSize(bogusKey));
        assertThrowsLiteCoreException(
            C4Constants.ErrorDomain.LITE_CORE,
            C4Constants.LiteCoreError.NOT_FOUND,
            () -> blobStore.getContents(bogusKey));
    }

    // - missing blobs

    @Test
    public void testMissingFilePath() {
        assertEquals(-1, blobStore.getSize(bogusKey));
        assertThrowsLiteCoreException(
            C4Constants.ErrorDomain.LITE_CORE,
            C4Constants.LiteCoreError.NOT_FOUND,
            () -> blobStore.getFilePath(bogusKey));
    }

    // - create blobs
    @Test
    public void testCreateBlobs() throws LiteCoreException {
        String blobToStore = "This is a blob to store in the store!";

        // Add blob to the store:
        try (C4BlobKey key = blobStore.create(blobToStore.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(key);
            assertEquals("sha1-QneWo5IYIQ0ZrbCG0hXPGC6jy7E=", key.toString());

            // Read it back and compare
            long blobSize = blobStore.getSize(key);
            assertTrue(blobSize >= blobToStore.getBytes(StandardCharsets.UTF_8).length);
            // TODO: Encryption
            assertEquals(blobToStore.getBytes(StandardCharsets.UTF_8).length, blobSize);

            byte[] content = blobStore.getContents(key);
            assertNotNull(content);
            assertArrayEquals(blobToStore.getBytes(StandardCharsets.UTF_8), content);
            assertEquals(blobToStore.getBytes(StandardCharsets.UTF_8).length, content.length);

            String p = blobStore.getFilePath(key);
            // TODO: Encryption
            assertNotNull(p);
            String filename = "QneWo5IYIQ0ZrbCG0hXPGC6jy7E=.blob";
            assertEquals(p.length() - filename.length(), p.indexOf(filename));

            // Try storing it again
            try (C4BlobKey key2 = blobStore.create(blobToStore.getBytes(StandardCharsets.UTF_8))) {
                assertNotNull(key2);
                assertEquals(key.toString(), key2.toString());
            }
        }
    }

    // - delete blobs
    @Test
    public void testDeleteBlobs() throws LiteCoreException {
        String blobToStore = "This is a blob to store in the store!";

        // Add blob to the store:
        try (C4BlobKey key = blobStore.create(blobToStore.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(key);

            // Delete it
            blobStore.delete(key);

            // Try to read it (should be gone):
            long blobSize = blobStore.getSize(key);
            assertEquals(-1, blobSize);

            assertThrowsLiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.NOT_FOUND,
                () -> blobStore.getContents(key));

            assertThrowsLiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.NOT_FOUND,
                () -> blobStore.getFilePath(key));
        }
    }

    // - read blob with stream
    @Test
    public void testReadBlobWithStream() throws LiteCoreException {
        String blob = "This is a blob to store in the store!";
        byte[] buf = new byte[6];

        // Add blob to the store:
        try (C4BlobKey key = blobStore.create(blob.getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(key);

            try (C4BlobReadStream stream = blobStore.openReadStream(key)) {
                assertNotNull(stream);

                assertEquals(blob.getBytes(StandardCharsets.UTF_8).length, stream.getLength());

                // Read it back, 6 bytes at a time:
                StringBuilder readBack = new StringBuilder();

                int n;
                while ((n = stream.read(buf, 0, buf.length)) > 0) {
                    readBack.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                assertEquals(blob, readBack.toString());

                // Try seeking:
                stream.seek(10);
                assertEquals(4, stream.read(buf, 0, 4));
                assertEquals("blob", new String(buf, 0, 4, StandardCharsets.UTF_8));
            }
        }
    }

    // - write blob with stream
    @Test
    public void testWriteBlobWithStream() throws LiteCoreException {
        C4BlobKey key = null;
        try {
            try (C4BlobWriteStream stream = blobStore.openWriteStream()) {
                assertNotNull(stream);

                for (int i = 0; i < 1000; i++) {
                    stream.write(String.format(Locale.ENGLISH, "This is line %03d.\n", i)
                        .getBytes(StandardCharsets.UTF_8));
                }

                // Get the blob key, and install it:
                key = stream.computeBlobKey();
                assertNotNull(key);
                stream.install();
            }

            // Read it back using the key:
            byte[] contents = blobStore.getContents(key);
            assertNotNull(contents);
            assertEquals(18000, contents.length);

            // Read it back random-access:
            try (C4BlobReadStream reader = blobStore.openReadStream(key)) {
                assertNotNull(reader);
                final int increment = 3 * 3 * 3 * 3;
                int line = increment;
                for (int i = 0; i < 1000; i++) {
                    line = (line + increment) % 1000;
                    Report.log("Reading line '%s' at offset %d", line, (18 * line));
                    String buf = String.format(Locale.ENGLISH, "This is line %03d.\n", line);
                    reader.seek(18 * line);
                    byte[] readBuf = new byte[18];
                    reader.read(readBuf, 0, 18);
                    assertNotNull(readBuf);
                    assertEquals(18, readBuf.length);
                    assertArrayEquals(readBuf, buf.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        finally {
            if (key != null) {
                try { key.close(); }
                catch (Exception ignore) { }
            }
        }
    }


    // - write blobs of many sizes
    @SlowTest
    @Test
    public void testWriteBlobsOfManySizes() throws LiteCoreException {
        // The interesting sizes for encrypted blobs are right around the file block size (4096)
        // and the cipher block size (16).

        List<Integer> kSizes = Arrays.asList(0, 1, 15, 16, 17, 4095, 4096, 4097,
            4096 + 15, 4096 + 16, 4096 + 17, 8191, 8192, 8193);
        for (int size: kSizes) {
            Report.log("Testing blob: %s bytes", size);
            // Write the blob:
            try (C4BlobWriteStream stream = blobStore.openWriteStream()) {
                assertNotNull(stream);

                String chars = "ABCDEFGHIJKLMNOPQRSTUVWXY";
                for (int i = 0; i < size; i++) {
                    int c = i % chars.length();
                    stream.write(chars.substring(c, c + 1).getBytes(StandardCharsets.UTF_8));
                }

                // Get the blob key, and install it:
                try (C4BlobKey key = stream.computeBlobKey()) {
                    stream.install();

                    // Read it back using the key:
                    byte[] contents = blobStore.getContents(key);
                    assertNotNull(contents);
                    assertEquals(size, contents.length);
                    for (int i = 0; i < size; i++) {
                        assertEquals(chars.substring(i % chars.length(), i % chars.length() + 1)
                            .getBytes(StandardCharsets.UTF_8)[0], contents[i]);
                    }
                }
            }
        }
    }


    // - write blob and cancel
    @Test
    public void testWriteBlobAndCancel() throws LiteCoreException {
        try (C4BlobWriteStream stream = blobStore.openWriteStream()) {
            assertNotNull(stream);

            String buf = "This is line oops\n";
            stream.write(buf.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void parseInvalidBlobKeys(String str) {
        try (C4BlobKey ignore = C4BlobKey.create(str)) { fail(); }
        catch (LiteCoreException ignore) { }
    }
}
