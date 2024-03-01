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
package com.couchbase.lite;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.couchbase.lite.internal.utils.IOUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.StringUtils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


// There are other blob tests in test suites...
@SuppressWarnings("ConstantConditions")
public class BlobTest extends BaseDbTest {
    private String localBlobContent;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public final void setUpBlobTest() { localBlobContent = StringUtils.randomString(100); }

    @Test
    public void testBlobCtorWithNullContentType() {
        assertThrows(IllegalArgumentException.class, () -> new Blob(null, new byte[] {5, 6, 7, 8}));
    }

    @Test
    public void testBlobCtorWithNullContent() {
        assertThrows(IllegalArgumentException.class, () -> new Blob("image/png", (byte[]) null));
    }

    @Test
    public void testBlobCtorWithStreamAndNullContentType() {
        assertThrows(IllegalArgumentException.class, () -> new Blob(null, PlatformUtils.getAsset("attachment.png")));
    }

    @Test
    public void testBlobCtorsWithNullStream() {
        assertThrows(IllegalArgumentException.class, () -> new Blob("image/png", (InputStream) null));
    }

    @Test
    public void testEquals() {
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
        Document doc = saveDocInTestCollection(mDoc);

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
    public void testHashCode() {
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
        Document doc = saveDocInTestCollection(mDoc);

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
    public void testBlobContentBytes() throws IOException {
        byte[] blobContent;
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) { blobContent = IOUtils.toByteArray(is); }

        Blob blob = new Blob("image/png", blobContent);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        Document doc = saveDocInTestCollection(mDoc);

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
            getTestCollection().save(mDoc);
        }

        Document doc = getTestCollection().getDocument("doc1");
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
    public void testGetContent6MBFile() throws IOException {
        byte[] bytes;

        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) {
            bytes = IOUtils.toByteArray(is);
        }

        Blob blob = new Blob("application/json", bytes);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        Document doc = saveDocInTestCollection(mDoc);
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
        Document doc = saveDocInTestCollection(mDoc);

        // Reload the doc from the database to make sure to "bust the cache" for the blob
        // cached in the doc object
        Document reloadedDoc = getTestCollection().getDocument(doc.getId());
        Blob savedBlob = reloadedDoc.getBlob("blob");
        byte[] content = savedBlob.getContent();
        assertArrayEquals(content, bytes);
    }

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

    @Test
    public void testBlobReadByte() throws IOException {
        byte[] data;
        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) { data = IOUtils.toByteArray(is); }
        assertEquals(new Blob("application/json", data).getContentStream().read(), data[0]);
    }

    @Test
    public void testBlobReadByteArray() throws IOException {
        byte[] data;
        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) { data = IOUtils.toByteArray(is); }

        byte[] blobContent = new byte[data.length];
        assertEquals(
            data.length,
            new Blob("application/json", data).getContentStream().read(blobContent, 0, data.length));
        assertArrayEquals(blobContent, data);
    }

    @Test
    public void testBlobReadSkip() throws IOException {
        byte[] data;
        try (InputStream is = PlatformUtils.getAsset("iTunesMusicLibrary.json")) { data = IOUtils.toByteArray(is); }

        InputStream blobStream = new Blob("application/json", data).getContentStream();
        assertEquals(17, blobStream.skip(17));
        assertEquals(blobStream.read(), data[17]);
    }

    @Test
    public void testReadBlobStream() throws IOException {
        byte[] bytes;
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) { bytes = IOUtils.toByteArray(is); }

        Blob blob = new Blob("image/png", bytes);
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setBlob("blob", blob);
        Document doc = saveDocInTestCollection(mDoc);

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


    ///////////////  JSON tests

    // 3.1.a
    @Test
    public void testDbSaveBlob() throws JSONException {
        Blob blob = makeBlob();
        getTestDatabase().saveBlob(blob);
        verifyBlob(new JSONObject(blob.toJSON()));
    }

    // 3.1.b
    @Test
    public void testDbGetBlob() {
        Map<String, Object> props = getPropsForSavedBlob();

        Map<String, Object> fetchProps = new HashMap<>();
        fetchProps.put(Blob.META_PROP_TYPE, Blob.TYPE_BLOB);
        fetchProps.put(Blob.PROP_DIGEST, props.get(Blob.PROP_DIGEST));
        fetchProps.put(Blob.PROP_CONTENT_TYPE, props.get(Blob.PROP_CONTENT_TYPE));
        Blob dbBlob = getTestDatabase().getBlob(fetchProps);

        verifyBlob(dbBlob);
        assertEquals(BLOB_CONTENT, new String(dbBlob.getContent()));
    }

    // 3.1.c
    @Test
    public void testUnsavedBlobToJSON() {
        assertThrows(CouchbaseLiteError.class, () -> makeBlob().toJSON());
    }

    // 3.1.d
    @Test
    public void testDbGetNonexistentBlob() {
        Map<String, Object> props = new HashMap<>();
        props.put(Blob.META_PROP_TYPE, Blob.TYPE_BLOB);
        props.put(Blob.PROP_DIGEST, "sha1-C+ThisIsTheWayWeMakeItFail=");
        assertNull(getTestDatabase().getBlob(props));
    }

    // 3.1.e.0: null param
    @Test
    public void testDbGetNotBlob0() {
        Blob blob = makeBlob();
        getTestDatabase().saveBlob(blob);
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(null));
    }

    // 3.1.e.1: empty param
    @Test
    public void testDbGetNotBlob1() {
        Blob blob = makeBlob();
        getTestDatabase().saveBlob(blob);
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(new HashMap<>()));
    }

    // 3.1.e.2: missing digest
    @Test
    public void testDbGetNotBlob2() {
        Map<String, Object> props = getPropsForSavedBlob();
        props.remove(Blob.PROP_DIGEST);
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(props));
    }

    // 3.1.e.3: missing meta-type
    @Test
    public void testDbGetNotBlob3() {
        Map<String, Object> props = getPropsForSavedBlob();
        props.remove(Blob.META_PROP_TYPE);
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(props));
    }

    // 3.1.e.4: length is not a number
    @Test
    public void testDbGetNotBlob4() {
        Map<String, Object> props = getPropsForSavedBlob();
        props.put(Blob.PROP_LENGTH, "42");
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(props));
    }

    // 3.1.e.5: bad content type
    @Test
    public void testDbGetNotBlob5() {
        Map<String, Object> props = getPropsForSavedBlob();
        props.put(Blob.PROP_CONTENT_TYPE, new Object());
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(props));
    }

    // 3.1.e.6: extra arg
    @Test
    public void testDbGetNotBlob6() {
        Map<String, Object> props = getPropsForSavedBlob();
        props.put("foo", "bar");
        assertThrows(IllegalArgumentException.class, () -> getTestDatabase().getBlob(props));
    }

    // 3.1.f
    @Test
    public void testBlobInDocument() throws JSONException {
        MutableDocument mDoc = new MutableDocument();
        mDoc.setBlob("blob", makeBlob());

        Blob dbBlob = saveDocInTestCollection(mDoc).getBlob("blob");

        verifyBlob(dbBlob);

        verifyBlob(new JSONObject(dbBlob.toJSON()));
    }

    // 3.1.h
    @Test
    public void testBlobGoneAfterCompact() throws CouchbaseLiteException {
        Blob blob = makeBlob();
        getTestDatabase().saveBlob(blob);

        assertTrue(getTestDatabase().performMaintenance(MaintenanceType.COMPACT));

        Map<String, Object> props = new HashMap<>();
        props.put(Blob.META_PROP_TYPE, Blob.TYPE_BLOB);
        props.put(Blob.PROP_DIGEST, blob.digest());

        assertNull(getTestDatabase().getBlob(props));
    }

    @Test
    public void testIsBlob() throws IOException, CouchbaseLiteException {
        try (InputStream is = PlatformUtils.getAsset("attachment.png")) {
            Blob blob = new Blob("image/png", is);
            MutableDocument mDoc = new MutableDocument("doc1");
            mDoc.setBlob("blob", blob);
            getTestCollection().save(mDoc);
        }

        assertTrue(Blob.isBlob(
            new MutableDictionary().setJSON(getTestCollection().getDocument("doc1").getBlob("blob").toJSON()).toMap()));
    }

    // https://issues.couchbase.com/browse/CBL-2320
    @Test
    public void testBlobStreamReadNotNegative() throws CouchbaseLiteException, IOException {
        MutableDocument mDoc = new MutableDocument("blobDoc");
        mDoc.setBlob(
            "blob",
            new Blob("application/octet-stream", new byte[] {-1, (byte) 255, (byte) 0xf0, (byte) 0xa0}));
        saveDocInTestCollection(mDoc);

        InputStream blobStream = getTestCollection().getDocument("blobDoc").getBlob("blob").getContentStream();

        assertEquals(255, blobStream.read());
        assertEquals(255, blobStream.read());
        assertEquals(0xf0, blobStream.read());
        assertEquals(0xa0, blobStream.read());
    }

    private Map<String, Object> getPropsForSavedBlob() {
        Blob blob = makeBlob();
        getTestDatabase().saveBlob(blob);
        return blob.getProperties();
    }

    // Kotlin shim functions

    private Document saveDocInTestCollection(MutableDocument mDoc) {
        return saveDocInTestCollection(mDoc, getTestCollection());
    }

    private Document saveDocInTestCollection(MutableDocument mDoc, Collection collection) {
        return saveDocInCollection(mDoc, collection);
    }
}
