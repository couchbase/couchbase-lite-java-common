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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.json.JSONException;

import com.couchbase.lite.internal.core.C4BlobKey;
import com.couchbase.lite.internal.core.C4BlobReadStream;
import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4BlobWriteStream;
import com.couchbase.lite.internal.fleece.FLEncodable;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.Volatile;


/**
 * A Couchbase Lite Blob.
 * A Blob appears as a property of a Document and contains arbitrary binary data, tagged with MIME type.
 * Blobs can be arbitrarily large, although some operations may require that the entire content be loaded into memory.
 * The containing document's JSON contains only the Blob's metadata (type, length and digest).  The data itself
 * is stored in a file whose name is the content digest (like git).
 * <p>
 **/
// This class should be re-implemented as a wrapper that delegates to one of three internal implementations:
// content in memory, content in stream, content in DB.
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class Blob implements FLEncodable {

    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    public static final String ENCODER_ARG_DB = "BLOB.db";
    public static final String ENCODER_ARG_QUERY_PARAM = "BLOB.queryParam";

    /**
     * The sub-document property that identifies it as a special type of object.
     * For example, a blob is represented as `{"@type":"blob", "digest":"xxxx", ...}`
     */
    static final String META_PROP_TYPE = "@type";
    static final String TYPE_BLOB = "blob";

    static final String PROP_DIGEST = "digest";
    static final String PROP_LENGTH = "length";
    static final String PROP_CONTENT_TYPE = "content_type";
    static final String PROP_DATA = "data";
    static final String PROP_STUB = "stub";
    static final String PROP_REVPOS = "revpos";


    // Max size of data that will be cached in memory with the Blob
    private static final int MAX_CACHED_CONTENT_LENGTH = 8 * 1024;
    private static final String MIME_UNKNOWN = "application/octet-stream";


    //---------------------------------------------
    // Types
    //---------------------------------------------

    // This class is nothing like thread safe
    static final class BlobInputStream extends InputStream {
        private final byte[] buf = new byte[1];
        private C4BlobKey key;
        private C4BlobStore store;
        private C4BlobReadStream blobStream;


        BlobInputStream(@NonNull C4BlobKey key, @NonNull C4BlobStore store) throws LiteCoreException {
            this.key = Preconditions.assertNotNull(key, "key");
            this.store = Preconditions.assertNotNull(store, "store");
            this.blobStream = store.openReadStream(key);
        }

        // not supported...
        @SuppressWarnings("PMD.UselessOverridingMethod")
        @Override
        public int available() throws IOException { return super.available(); }

        // I think we could support this.
        // Currently, however, we do not.
        @Override
        public boolean markSupported() { return false; }

        @Override
        public synchronized void mark(int readLimit) {
            throw new UnsupportedOperationException("'mark()' not supported for Blob stream");
        }

        @Override
        public synchronized void reset() {
            throw new UnsupportedOperationException("'reset()' not supported for Blob stream");
        }

        @Override
        public long skip(long n) throws IOException {
            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                blobStream.seek(n);
                return n;
            }
            catch (LiteCoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read() throws IOException {
            if (key == null) { throw new IOException("Stream is closed"); }

            // Jens says:
            // LiteCore’s stream API is blocking.
            // It always returns one or more bytes, until you hit EOF.
            // (It’s always reading from the filesystem, so the latency should be pretty low.)
            if (read(buf, 0, buf.length) <= 0) { return -1; }

            final int b = (((int) buf[0]) & 0xff);
            buf[0] = 0;

            return b;
        }

        @Override
        public int read(@NonNull byte[] buf) throws IOException { return read(buf, 0, buf.length); }

        @Override
        public int read(@NonNull byte[] buf, int off, int len) throws IOException {
            Preconditions.assertNotNull(buf, "buffer");
            if (off < 0) { throw new IndexOutOfBoundsException("Read offset < 0: " + off); }
            if (len < 0) { throw new IndexOutOfBoundsException("Read length < 0: " + len); }
            if (off + len > buf.length) {
                throw new IndexOutOfBoundsException(
                    "off + len > buf.length (" + off + ", " + len + ", " + buf.length + ")");
            }

            if (len == 0) { return 0; }

            if (key == null) { throw new IOException("Stream is closed"); }

            try {
                final int n = blobStream.read(buf, off, len);
                return (n <= 0) ? -1 : n;
            }
            catch (LiteCoreException e) {
                throw new IOException("Failed reading blob", e);
            }
        }

        @Override
        public void close() throws IOException {
            super.close();

            // close internal stream
            if (blobStream != null) {
                blobStream.close();
                blobStream = null;
            }

            // key should be free
            if (key != null) {
                key.close();
                key = null;
            }

            if (store != null) {
                store.close();
                store = null;
            }
        }
    }

    public static boolean isBlob(@Nullable Map<String, ?> props) {
        if ((props == null) || (!(props.get(PROP_DIGEST) instanceof String))) { return false; }

        if (!TYPE_BLOB.equals(props.get(META_PROP_TYPE))) { return false; }
        int nProps = 2;

        if (props.containsKey(PROP_CONTENT_TYPE)) {
            if (!(props.get(PROP_CONTENT_TYPE) instanceof String)) { return false; }
            nProps++;
        }

        final Object len = props.get(PROP_LENGTH);
        if (len != null) {
            if ((!(len instanceof Integer) && (!(len instanceof Long)))) { return false; }
            nProps++;
        }

        return nProps == props.size();
    }


    //---------------------------------------------
    // member variables
    //---------------------------------------------

    // A newly created unsaved blob will have either blobContent or blobContentStream non-null.
    // A new blob saved to the database will have database and digest.
    // A blob loaded from the database will have database, properties, and digest unless invalid

    /**
     * The type of content this Blob represents; by convention this is a MIME type.
     */
    @NonNull
    private final String contentType;

    /**
     * The binary length of this Blob.
     */
    private long blobLength;

    /**
     * The contents of a Blob as a block of memory.
     * Assert((blobContentStream == null) || (blobContent == null))
     */
    @Nullable
    private byte[] blobContent;

    /**
     * The contents of a Blob as a stream.
     * Assert((blobContentStream == null) || (blobContent == null))
     */
    @Nullable
    private InputStream blobContentStream;

    /**
     * Null if blob is new and unsaved
     */
    @Nullable
    private BaseDatabase database;

    /**
     * The cryptographic digest of this Blob's contents, which uniquely identifies it,
     * or null if the blob has not yet been written to a database.
     */
    @Nullable
    private String blobDigest;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a Blob with the given in-memory data.
     *
     * @param contentType The type of content this Blob will represent
     * @param content     The data that this Blob will contain
     */
    public Blob(@NonNull String contentType, @NonNull byte[] content) {
        Preconditions.assertNotNull(contentType, "contentType");
        Preconditions.assertNotNull(content, "content");

        this.contentType = contentType;
        blobLength = content.length;
        blobContent = copyBytes(content);
        blobContentStream = null;
    }

    /**
     * Construct a Blob with the given stream of data.
     * The passed stream will be closed when it is copied either to memory
     * (see <code>getContent</code>) or to the database.
     * If it is closed before that, by client code, the attempt to store the blob will fail.
     * The converse is also true: the stream for a blob that is not saved or copied to memory
     * will not be closed (except during garbage collection).
     *
     * @param contentType The type of content this Blob will represent
     * @param stream      The stream of data that this Blob will consume
     */
    public Blob(@NonNull String contentType, @NonNull InputStream stream) {
        Preconditions.assertNotNull(contentType, "contentType");
        this.contentType = contentType;
        initStream(stream);
    }

    /**
     * Construct a Blob with the content of a file.
     * The blob can then be added as a property of a Document.
     * This constructor creates a stream that is not closed until the blob is stored in the db,
     * or copied to memory (except by garbage collection).
     *
     * @param contentType The type of content this Blob will represent
     * @param fileURL     A URL to a file containing the data that this Blob will represent.
     * @throws IOException on failure to open the file URL
     */
    public Blob(@NonNull String contentType, @NonNull URL fileURL) throws IOException {
        Preconditions.assertNotNull(contentType, "contentType");
        Preconditions.assertNotNull(fileURL, "fileUrl");

        if (!"file".equalsIgnoreCase(fileURL.getProtocol())) {
            throw new IllegalArgumentException(Log.formatStandardMessage("NotFileBasedURL", fileURL));
        }

        this.contentType = contentType;

        initStream(fileURL.openStream());
    }

    // Initializer for an existing blob being read from a document
    Blob(@NonNull BaseDatabase database, @NonNull Map<String, Object> properties) {
        this.database = database;

        blobDigest = (String) properties.get(PROP_DIGEST);

        final Object len = properties.get(PROP_LENGTH);
        if (len instanceof Number) { blobLength = ((Number) len).longValue(); }
        else { Log.w(LogDomain.DATABASE, "Blob length unspecified for blob %s: using 0.", blobDigest); }

        String propType = (String) properties.get(PROP_CONTENT_TYPE);
        if (propType == null) {
            propType = MIME_UNKNOWN;
            Log.w(LogDomain.DATABASE, "Blob type unspecified for blob %s: using '%s'", blobDigest, propType);
        }
        contentType = propType;

        final Object data = properties.get(PROP_DATA);
        if (data instanceof byte[]) { blobContent = (byte[]) data; }

        if ((blobDigest == null) && (blobContent == null)) {
            Log.w(DOMAIN, "Blob read from database has neither digest nor data.");
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets the contents of this blob as in in-memory byte array.
     * <b>Using this method will cause the entire contents of the blob to be read into memory!</b>
     *
     * @return the contents of a Blob as a block of memory
     */
    @Nullable
    public byte[] getContent() {
        // this will load blobContent from the blobContentStream (all of it!), if there is any
        if (blobContentStream != null) { readContentFromInitStream(); }

        if (blobContent != null) { return copyBytes(blobContent); }

        if (database != null) { return getContentFromDatabase(); }

        if (blobDigest == null) { Log.w(LogDomain.DATABASE, "Blob has no digest"); }

        return null;
    }

    /**
     * Get a the contents of this blob as a stream.
     * The caller is responsible for closing the stream returned by this call.
     * Closing or deleting the database before this call completes may cause it to fail.
     * <b>When called on a blob created from a stream (or a file path), this method will return null!</b>
     *
     * @return a stream of of this blobs contents; null if none exists or if this blob was initialized with a stream
     */
    @Nullable
    public InputStream getContentStream() {
        // refuse to provide a content stream, if this Blob was initialized from a content stream
        if (blobContentStream != null) { return null; }

        if (blobContent != null) { return new ByteArrayInputStream(blobContent); }

        if (database != null) { return getStreamFromDatabase(database); }

        if (blobDigest == null) { Log.w(LogDomain.DATABASE, "Blob has no digest"); }

        return null;
    }

    /**
     * Return the type of of the content this blob contains.  By convention this is a MIME type.
     *
     * @return the type of blobContent
     */
    @NonNull
    public String getContentType() { return contentType; }

    @NonNull
    public String toJSON() {
        if (blobDigest == null) {
            throw new CouchbaseLiteError("A Blob may be encoded as JSON only after it has been saved in a database");
        }

        final Map<String, Object> json = new HashMap<>();

        json.put(META_PROP_TYPE, TYPE_BLOB);
        json.put(PROP_DIGEST, blobDigest);
        json.put(PROP_LENGTH, blobLength);
        json.put(PROP_CONTENT_TYPE, contentType);

        try { return JSONUtils.toJSON(json).toString(); }
        catch (JSONException e) { throw new CouchbaseLiteError("Could not parse Blob JSON", e); }
    }

    /**
     * The number of byte of content this blob contains.
     *
     * @return The length of the blob or 0 if initialized with a stream.
     */
    public long length() { return blobLength; }

    /**
     * The cryptographic digest of this Blob's contents, which uniquely identifies it.
     *
     * @return The cryptographic digest of this blob's contents; null if the content has not been saved in a database
     */
    @Nullable
    public String digest() { return blobDigest; }

    /**
     * Get the blob metadata
     *
     * @return metadata for this Blob
     */
    @NonNull
    public Map<String, Object> getProperties() {
        final Map<String, Object> props = new HashMap<>();
        props.put(PROP_DIGEST, blobDigest);
        props.put(PROP_LENGTH, blobLength);
        props.put(PROP_CONTENT_TYPE, contentType);
        return props;
    }

    /**
     * This method is not part of the public API: Do not use it.
     *
     * @param encoder The FLEncoder to which to encode this object.
     */
    @Volatile
    @Override
    public void encodeTo(@NonNull FLEncoder encoder) {
        final boolean isQueryParam = encoder.getArg(ENCODER_ARG_QUERY_PARAM) != null;

        if (!isQueryParam) { installInDatabase(encoder.getArg(ENCODER_ARG_DB)); }

        encoder.beginDict(4);

        encoder.writeKey(META_PROP_TYPE);
        encoder.writeValue(TYPE_BLOB);

        encoder.writeKey(PROP_LENGTH);
        encoder.writeValue(blobLength);

        encoder.writeKey(PROP_CONTENT_TYPE);
        encoder.writeValue(contentType);

        if (blobDigest != null) {
            encoder.writeKey(PROP_DIGEST);
            encoder.writeValue(blobDigest);
        }

        // ??? all of content in memory, again...
        if (isQueryParam) {
            encoder.writeKey(PROP_DATA);
            encoder.writeValue(getContent());
        }

        encoder.endDict();
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    @NonNull
    @Override
    public String toString() {
        return "Blob{" + ClassUtils.objId(this) + ": " + blobDigest + "(" + contentType + ", " + length() + ")}";
    }

    /**
     * Get the blob hash code.
     * <p>
     * <b>This method is quite expensive. Also, when called on a blob created from a stream
     * (or a file path), it will cause the entire contents of that stream to be read into memory!</b>
     *
     * @return hash code for the object
     */
    @Override
    public int hashCode() { return Arrays.hashCode(getContent()); }

    /**
     * Compare for equality.
     * <p>
     * <b>This method is quite expensive. Also, when called on a blob created from a stream
     * (or a file path), it will cause the entire contents of that stream to be read into memory!</b>
     *
     * @return true if this object is the same as that one.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Blob)) { return false; }

        final Blob m = (Blob) o;
        return ((blobDigest != null) && (m.blobDigest != null))
            ? blobDigest.equals(m.blobDigest)
            : Arrays.equals(getContent(), m.getContent());
    }

    @SuppressWarnings({"NoFinalizer", "PMD.CloseResource"})
    @Override
    protected void finalize() throws Throwable {
        try {
            final InputStream stream = blobContentStream;
            if (stream == null) { return; }

            try { stream.close(); }
            catch (IOException ignore) { }
        }
        finally { super.finalize(); }
    }

    //---------------------------------------------
    // Package protected
    //---------------------------------------------

    long updateSize() {
        if (database == null) { return -1; }
        try (C4BlobStore store = database.getBlobStore(); C4BlobKey key = C4BlobKey.create(blobDigest)) {
            final long storedSize = store.getSize(key);
            if (storedSize >= 0) { blobLength = storedSize; }
            return storedSize;
        }
        catch (LiteCoreException ignore) { }
        return -1;
    }

    // ??? should be called holding the dbLock?
    void installInDatabase(@Nullable Database db) {
        if (database != null) {
            // attempt to save the blob in the wrong db;
            if ((db != null) && (!database.equals(db))) {
                throw new CouchbaseLiteError(Log.lookupStandardMessage("BlobDifferentDatabase"));
            }

            // saved but no digest???
            if (blobDigest == null) { throw new CouchbaseLiteError("Blob has no digest"); }

            // blob has already been saved.
            return;
        }

        database = db;

        // blob was saved using Database.saveBlob();
        if (blobDigest != null) { return; }

        try (C4BlobStore store = database.getBlobStore(); C4BlobKey key = getBlobKey(store)) {
            blobDigest = key.toString();
        }
        catch (Exception e) {
            database = null;
            blobDigest = null;
            throw new CouchbaseLiteError("Failed reading blob content from database", e);
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    @Nullable
    private byte[] copyBytes(@Nullable byte[] b) {
        if (b == null) { return null; }
        final int len = b.length;
        final byte[] copy = new byte[len];
        System.arraycopy(b, 0, copy, 0, len);
        return copy;
    }

    private void initStream(@NonNull InputStream stream) {
        Preconditions.assertNotNull(stream, "input stream");
        blobLength = 0;
        blobContent = null;
        blobContentStream = stream;
    }

    private void installInDatabase(@Nullable Object dbArg) {
        // blob has not been saved: dbArg must be a db in which to save it.
        if ((database == null) && (!(dbArg instanceof Database))) {
            throw new CouchbaseLiteError("No database for Blob save");
        }
        installInDatabase((Database) dbArg);
    }

    @Nullable
    private byte[] getContentFromDatabase() {
        final byte[] newContent;
        try (C4BlobStore blobStore = Preconditions.assertNotNull(database, "database").getBlobStore();
             C4BlobKey key = C4BlobKey.create(blobDigest)) {
            newContent = blobStore.getContents(key);
        }
        catch (LiteCoreException e) {
            final String msg = "Failed to read content from database for digest: " + blobDigest;
            Log.e(DOMAIN, msg, e);
            throw new CouchbaseLiteError(msg, e);
        }

        // cache content if less than 8K
        if ((newContent != null) && (newContent.length < MAX_CACHED_CONTENT_LENGTH)) { blobContent = newContent; }

        return newContent;
    }

    @NonNull
    private InputStream getStreamFromDatabase(@NonNull BaseDatabase db) {
        try { return new BlobInputStream(C4BlobKey.create(blobDigest), db.getBlobStore()); }
        catch (IllegalArgumentException | LiteCoreException e) {
            throw new CouchbaseLiteError("Failed opening blobContent stream.", e);
        }
    }

    @NonNull
    private C4BlobKey getBlobKey(@NonNull C4BlobStore store) throws LiteCoreException, IOException {
        if (blobContent != null) { return store.create(blobContent); }
        if (blobContentStream != null) { return writeDatabaseFromInitStream(store); }
        throw new CouchbaseLiteError(Log.lookupStandardMessage("BlobContentNull"));
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private void readContentFromInitStream() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = Preconditions.assertNotNull(blobContentStream, "content stream")) {
            final byte[] buff = new byte[MAX_CACHED_CONTENT_LENGTH];
            int n;
            while ((n = in.read(buff)) >= 0) { out.write(buff, 0, n); }
        }
        catch (IOException e) {
            throw new CouchbaseLiteError("Failed reading blob content stream", e);
        }
        finally {
            blobContentStream = null;
        }

        blobContent = out.toByteArray();
        blobLength = blobContent.length;
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    @SuppressWarnings("PMD.UseTryWithResources")
    @NonNull
    private C4BlobKey writeDatabaseFromInitStream(@NonNull C4BlobStore store) throws LiteCoreException, IOException {
        if (blobContentStream == null) { throw new CouchbaseLiteError("Blob stream is null"); }

        final C4BlobKey key;

        int len = 0;
        final byte[] buffer;
        try (C4BlobWriteStream blobOut = store.openWriteStream()) {
            buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
            int n;
            while ((n = blobContentStream.read(buffer)) >= 0) {
                blobOut.write(buffer, n);
                len += n;
            }

            blobOut.install();

            key = blobOut.computeBlobKey();
        }
        finally {
            try { blobContentStream.close(); }
            catch (IOException ignore) { }
            blobContentStream = null;
        }

        blobLength = len;

        // don't cache more than 8K
        if ((blobContent != null) && (blobContent.length <= MAX_CACHED_CONTENT_LENGTH)) { blobContent = buffer; }

        return key;
    }
}
