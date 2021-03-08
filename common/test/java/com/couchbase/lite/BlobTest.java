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
package com.couchbase.lite;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.couchbase.lite.internal.utils.FlakyTest;
import com.couchbase.lite.internal.utils.IOUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.StringUtils;

import static com.couchbase.lite.internal.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


// There are other blob tests in test suites...
public class BlobTest extends BaseDbTest {
    private String localBlobContent;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public final void setUpBlobTest() {
        localBlobContent = StringUtils.randomString(100);
        BaseTest.logTestInitializationComplete("Blob");
    }

    @After
    public final void tearDownBlobTest() { BaseTest.logTestTeardownBegun("Blob"); }

    @Test
    public void testEquals() throws CouchbaseLiteException {
        byte[] content1a = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        byte[] content1b = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        byte[] content2a = localBlobContent.getBytes(StandardCharsets.UTF_8);

        // store blob
        Blob data1a = new Blob("text/plain", content1a);
        Blob data1b = new Blob("text/plain", content1b);
        Blob data1c = new Blob("text/plain", content1a); // not store in db
        Blob data2a = new Blob("text/plain", content2a);

        assertEquals(data1a, data1b);
        assertEquals(data1b, data1a);
        assertNotEquals(data1a, data2a);
        assertNotEquals(data1b, data2a);
        assertNotEquals(data2a, data1a);
        assertNotEquals(data2a, data1b);

        MutableDocument mDoc = new MutableDocument();
        mDoc.setBlob("blob1a", data1a);
        mDoc.setBlob("blob1b", data1b);
        mDoc.setBlob("blob2a", data2a);
        Document doc = saveDocInBaseTestDb(mDoc);

        Blob blob1a = doc.getBlob("blob1a");
        Blob blob1b = doc.getBlob("blob1b");
        Blob blob2a = doc.getBlob("blob2a");

        assertEquals(blob1a, blob1b);
        assertEquals(blob1b, blob1a);
        assertNotEquals(blob1a, blob2a);
        assertNotEquals(blob1b, blob2a);
        assertNotEquals(blob2a, blob1a);
        assertNotEquals(blob2a, blob1b);

        assertEquals(blob1a, data1c);
        assertEquals(data1c, blob1a);
    }

    @Test
    public void testHashCode() throws CouchbaseLiteException {
        byte[] content1a = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        byte[] content1b = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        byte[] content2a = localBlobContent.getBytes(StandardCharsets.UTF_8);

        // store blob
        Blob data1a = new Blob("text/plain", content1a);
        Blob data1b = new Blob("text/plain", content1b);
        Blob data1c = new Blob("text/plain", content1a); // not store in db
        Blob data2a = new Blob("text/plain", content2a);

        assertEquals(data1a.hashCode(), data1b.hashCode());
        assertEquals(data1b.hashCode(), data1a.hashCode());
        assertNotEquals(data1a.hashCode(), data2a.hashCode());
        assertNotEquals(data1b.hashCode(), data2a.hashCode());
        assertNotEquals(data2a.hashCode(), data1a.hashCode());
        assertNotEquals(data2a.hashCode(), data1b.hashCode());

        MutableDocument mDoc = new MutableDocument();
        mDoc.setBlob("blob1a", data1a);
        mDoc.setBlob("blob1b", data1b);
        mDoc.setBlob("blob2a", data2a);
        Document doc = saveDocInBaseTestDb(mDoc);

        Blob blob1a = doc.getBlob("blob1a");
        Blob blob1b = doc.getBlob("blob1b");
        Blob blob2a = doc.getBlob("blob2a");

        assertEquals(blob1a.hashCode(), blob1b.hashCode());
        assertEquals(blob1b.hashCode(), blob1a.hashCode());
        assertNotEquals(blob1a.hashCode(), blob2a.hashCode());
        assertNotEquals(blob1b.hashCode(), blob2a.hashCode());
        assertNotEquals(blob2a.hashCode(), blob1a.hashCode());
        assertNotEquals(blob2a.hashCode(), blob1b.hashCode());

        assertEquals(blob1a.hashCode(), data1c.hashCode());
        assertEquals(data1c.hashCode(), blob1a.hashCode());
    }


    @Test
    public void testBlobContentBytes() throws IOException, CouchbaseLiteException {
        byte[] blobContent;
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) { blobContent = IOUtils.toByteArray(is); }

        Blob blob = new Blob("image/png", blobContent);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        saveDocInBaseTestDb(mDoc);

        Document doc = baseTestDb.getDocument("doc1");
        Blob savedBlob = doc.getBlob("blob");
        assertNotNull(savedBlob);

        byte[] buff = blob.getContent();
        assertEquals(blobContent.length, buff.length);
        assertArrayEquals(blobContent, buff);

        assertEquals(blobContent.length, savedBlob.length());

        assertEquals("image/png", savedBlob.getContentType());
    }

    @Test
    public void testBlobContentStream() throws CouchbaseLiteException, IOException {
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) {
            Blob blob = new Blob("image/png", is);
            MutableDocument mDoc = new MutableDocument("doc1");
            mDoc.setBlob("blob", blob);
            baseTestDb.save(mDoc);
        }

        Document doc = baseTestDb.getDocument("doc1");
        Blob savedBlob = doc.getBlob("blob");
        assertNotNull(savedBlob);

        byte[] blobContent;
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) { blobContent = IOUtils.toByteArray(is); }

        byte[] buff = new byte[1024];
        try (InputStream in = savedBlob.getContentStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int n;
            while ((n = in.read(buff)) > 0) { out.write(buff, 0, n); }
            buff = out.toByteArray();
        }

        assertEquals(blobContent.length, buff.length);
        assertArrayEquals(blobContent, buff);

        assertEquals(blobContent.length, savedBlob.length());

        assertEquals("image/png", savedBlob.getContentType());
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1438
    @Test
    public void testGetContent6MBFile() throws IOException, CouchbaseLiteException {
        byte[] bytes;

        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) {
            bytes = IOUtils.toByteArray(is);
        }

        Blob blob = new Blob("application/json", bytes);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        Document doc = saveDocInBaseTestDb(mDoc);
        Blob savedBlob = doc.getBlob("blob");
        assertNotNull(savedBlob);
        assertEquals("application/json", savedBlob.getContentType());
        byte[] content = blob.getContent();
        assertArrayEquals(content, bytes);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1611
    @Test
    public void testGetNonCachedContent6MBFile() throws IOException, CouchbaseLiteException {
        final byte[] bytes;
        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) { bytes = IOUtils.toByteArray(is); }

        Blob blob = new Blob("application/json", bytes);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        Document doc = saveDocInBaseTestDb(mDoc);

        // Reload the doc from the database to make sure to "bust the cache" for the blob
        // cached in the doc object
        Document reloadedDoc = baseTestDb.getDocument(doc.getId());
        Blob savedBlob = reloadedDoc.getBlob("blob");
        byte[] content = savedBlob.getContent();
        assertArrayEquals(content, bytes);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testBlobFromFileURL() throws IOException {
        String contentType = "image/png";
        Blob blob;
        URL url = null;
        File path = tempFolder.newFile("attachment.png");

        try (InputStream is = PlatformUtils.getAsset("attachment.png")) {
            byte[] bytes = IOUtils.toByteArray(is);
            FileOutputStream fos = new FileOutputStream(path);
            fos.write(bytes);
            fos.close();

            blob = new Blob(contentType, path.toURI().toURL());
        }

        byte[] bytes = IOUtils.toByteArray(path);
        byte[] content = blob.getContent();
        assertArrayEquals(content, bytes);

        assertThrows(IllegalArgumentException.class, () -> new Blob(null, url));

        assertThrows(IllegalArgumentException.class, () -> new Blob(contentType, (URL) null));

        assertThrows(IllegalArgumentException.class, () -> new Blob(contentType, new URL("http://java.sun.com")));
    }

    @FlakyTest
    @Test
    public void testBlobReadFunctions() throws IOException {
        byte[] bytes;

        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) {
            bytes = IOUtils.toByteArray(is);
        }

        Blob blob = new Blob("application/json", bytes);
        assertTrue(blob.toString().matches("Blob\\{@0x\\w{6,8},type=application/json,len=67[12]\\d{4}\\}"));
        assertEquals(blob.getContentStream().read(), bytes[0]);

        blob = new Blob("application/json", bytes);
        byte[] bytesReadFromBlob = new byte[bytes.length];
        blob.getContentStream().read(bytesReadFromBlob, 0, bytes.length);
        assertArrayEquals(bytesReadFromBlob, bytes);

        blob = new Blob("application/json", bytes);
        InputStream iStream = blob.getContentStream();
        iStream.skip(2);
        assertEquals(iStream.read(), bytes[2]);
    }

    @Test
    public void testReadBlobStream() throws IOException, CouchbaseLiteException {
        byte[] bytes;
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) { bytes = IOUtils.toByteArray(is); }

        Blob blob = new Blob("image/png", bytes);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        Document doc = saveDocInBaseTestDb(mDoc);

        Blob savedBlob = doc.getBlob("blob");
        assertNotNull(savedBlob);
        assertEquals("image/png", savedBlob.getContentType());

        final byte[] buffer = new byte[1024];

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); InputStream in = savedBlob.getContentStream()) {
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            byte[] readBytes = out.toByteArray();
            assertArrayEquals(bytes, readBytes);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBlobCtorWithNullContentType() { new Blob(null, new byte[] {5, 6, 7, 8}); }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBlobCtorWithNullContent() { new Blob("image/png", (byte[]) null); }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBlobCtorWithStreamAndNullContentType() { new Blob(null, PlatformUtils.getAsset("attachment.png")); }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBlobCtorsWithNullStream() { new Blob("image/png", (InputStream) null); }
}
