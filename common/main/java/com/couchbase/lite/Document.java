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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.JSONEncodable;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.Volatile;


/**
 * Readonly version of the Document.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public class Document implements DictionaryInterface, JSONEncodable, Iterable<String> {

    /// Factory methods

    @Nullable
    static Document getDocumentOrNull(@NonNull Collection collection, @NonNull String id)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(collection, "collection");
        Preconditions.assertNotEmpty(id, "id");
        final C4Document c4Doc = collection.getC4Document(id);
        if ((c4Doc == null) || (c4Doc.isDocDeleted())) { return null; }
        return new Document(collection, id, c4Doc, false);
    }

    @NonNull
    static Document getDocumentWithDeleted(@NonNull Collection collection, @NonNull String id)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(collection, "collection");
        Preconditions.assertNotEmpty(id, "id");

        final C4Document c4Doc = collection.getC4Document(id);
        if (c4Doc != null) { return new Document(collection, id, c4Doc, false); }

        throw new CouchbaseLiteException("Document not found: " + id, CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
    }

    @NonNull
    static Document getDocumentWithRevisions(@NonNull Collection collection, @NonNull String id)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(collection, "collection");
        Preconditions.assertNotEmpty(id, "id");

        final C4Document c4Doc = collection.getC4DocumentWithRevs(id);
        if (c4Doc != null) { return new Document(collection, id, c4Doc, false); }

        throw new CouchbaseLiteException("Document not found: " + id, CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
    }


    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final Object lock = new Object();

    @NonNull
    private final String id;
    private final boolean mutable;

    // ctors set this by calling updateDictionaryLocked
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @SuppressWarnings("NotNullFieldNotInitialized")
    // while internalDict is guarded by lock, the content of the Dictionary is not.
    @GuardedBy("lock")
    @NonNull
    private Dictionary internalDict;

    // ??? A single C4Document may be shared by multiple Documents/C4Documents.
    // Don't even try to close it.
    @GuardedBy("lock")
    @Nullable
    private C4Document c4Document;

    @GuardedBy("lock")
    @Nullable
    private Collection collection;

    @SuppressFBWarnings("URF_UNREAD_FIELD")
    @SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField", "FieldCanBeLocal"})
    @GuardedBy("lock")
    @Nullable
    private FLDict data;

    // keep a ref to prevent GC
    @SuppressFBWarnings("URF_UNREAD_FIELD")
    @SuppressWarnings({"PMD.UnusedPrivateField", "FieldCanBeLocal"})
    @GuardedBy("lock")
    @Nullable
    private MRoot root;

    // ??? This nasty little hack is set when a document is created by a replication filter,
    // without a c4doc.  Since that is the only place it is set, it is *also* used
    // in toMutable, as a flag meaning that this document was obtained from a replication filter,
    // to prevent modification of a doc while the replication is running.
    @GuardedBy("lock")
    @Nullable
    private String revId;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // This is the only constructor that child classes should call
    protected Document(
        @Nullable Collection collection,
        @NonNull String id,
        @Nullable C4Document c4doc,
        boolean mutable) {
        this.collection = collection;
        this.id = Preconditions.assertNotNull(id, "id");
        this.mutable = mutable;
        setC4Document(c4doc, mutable);
    }

    // This constructor is used in replicator filters, to hack together a doc from its Fleece representation
    Document(@NonNull Collection collection, @NonNull String id, @Nullable String revId, @NonNull FLDict body) {
        this(collection, id, null, false);
        this.revId = revId;
        setContentLocked(body, false);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * return the collection to which the document belongs.
     *
     * @return the document's collection
     */
    @Nullable
    public Collection getCollection() { return collection; }

    /**
     * return the document's ID.
     *
     * @return the document's ID
     */
    @NonNull
    public String getId() { return id; }

    /**
     * Get the document's revision id.
     * The revision id in the Document class is a constant while the revision id in the MutableDocument
     * class is not. A newly created Document will have a null revision id. The revision id in
     * a MutableDocument will be updated on save. The revision id format is opaque, which means its format
     * has no meaning and shouldn't be parsed to get information.
     *
     * @return the document's revision id
     */
    @Nullable
    public String getRevisionID() {
        synchronized (lock) { return (c4Document == null) ? revId : c4Document.getSelectedRevID(); }
    }

    /**
     * Get the document's timestamp.
     *
     * The values returned by this method are, actually, just the document's generation
     * number.  This is a increasing number but not, until future releases, an actual timestamp.
     *
     * @return the document's timestamp
     */
    @Volatile
    public long getTimestamp() {
        synchronized (lock) { return (c4Document == null) ? -1 : c4Document.getTimestamp(); }
    }

    /**
     * Return the sequence number of the document in the database.
     * The sequence number indicates how recently the document has been changed.  Every time a document
     * is updated, the database assigns it the next sequential sequence number.  Thus, when a document's
     * sequence number changes it means that the document been updated (on-disk).  If one document's sequence
     * is different from another's, the document with the larger sequence number was changed more recently.
     * Sequence numbers are not available for documents obtained from a replication filter.  This method
     * will always return 0 for such documents.
     *
     * @return the sequence number of the document in the database.
     */
    public long getSequence() {
        synchronized (lock) { return (c4Document == null) ? 0 : c4Document.getSelectedSequence(); }
    }

    /**
     * Return a mutable copy of the document
     *
     * @return the MutableDocument instance
     */
    @NonNull
    public MutableDocument toMutable() {
        synchronized (lock) {
            if (revId != null) {
                throw new UnsupportedOperationException("Editing replication filter documents not supported");
            }
        }
        return new MutableDocument(this);
    }

    /**
     * Gets the number of the entries in the document.
     *
     * @return the number of entries in the document.
     */
    @Override
    public final int count() { return getContent().count(); }

    @Override
    public final boolean isEmpty() { return getContent().isEmpty(); }

    //---------------------------------------------
    // API - Implements ReadOnlyDictionaryInterface
    //---------------------------------------------

    /**
     * Get a List containing all keys, or an empty List if the document has no properties.
     *
     * @return all keys
     */
    @NonNull
    @Override
    public List<String> getKeys() { return getContent().getKeys(); }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) { return getContent().getBoolean(key); }

    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(@NonNull String key) { return getContent().getInt(key); }

    /**
     * Gets a property's value as a long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(@NonNull String key) { return getContent().getLong(key); }

    /**
     * Gets a property's value as a float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(@NonNull String key) { return getContent().getFloat(key); }

    /**
     * Gets a property's value as a double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(@NonNull String key) { return getContent().getDouble(key); }

    /**
     * Gets a property's value as a Number.
     * Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Nullable
    @Override
    public Number getNumber(@NonNull String key) { return getContent().getNumber(key); }

    /**
     * Gets a property's value as a String.
     * Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Nullable
    @Override
    public String getString(@NonNull String key) { return getContent().getString(key); }

    /**
     * Gets a property's value as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parsable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key the key
     * @return the Date value or null.
     */
    @Nullable
    @Override
    public Date getDate(@NonNull String key) { return getContent().getDate(key); }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Nullable
    @Override
    public Blob getBlob(@NonNull String key) { return getContent().getBlob(key); }

    /**
     * Get a property's value as an Array.
     * Returns null if the property doesn't exist, or its value is not an Array.
     *
     * @param key the key
     * @return The Array object or null.
     */
    @Nullable
    @Override
    public Array getArray(@NonNull String key) { return getContent().getArray(key); }

    /**
     * Get a property's value as a Dictionary.
     * Returns null if the property doesn't exist, or its value is not a Dictionary.
     *
     * @param key the key
     * @return The Dictionary object or null.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(@NonNull String key) { return getContent().getDictionary(key); }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or null.
     */
    @Nullable
    @Override
    public Object getValue(@NonNull String key) { return getContent().getValue(key); }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or null.
     */
    @Nullable
    public <T> T getValue(@NonNull Class<T> klass, @NonNull String key) { return getContent().getValue(klass, key); }

    /**
     * Gets content of the current object as a Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() { return getContent().toMap(); }

    @NonNull
    @Override
    public String toJSON() throws CouchbaseLiteException {
        try {
            synchronized (lock) {
                if (c4Document == null) { throw new CouchbaseLiteError("Document has not been saved to a database"); }
                return c4Document.bodyAsJSON(true);
            }
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getValue(String),
     * because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(@NonNull String key) { return getContent().contains(key); }

    @Internal("This method is not part of the public API")
    @VisibleForTesting
    @Nullable
    /* <Unsupported API> Internal used for testing purpose. */
    public String getRevisionHistory() throws CouchbaseLiteException {
        synchronized (lock) {
            if (c4Document == null) { return null; }
            if (collection == null) { throw new CouchbaseLiteException("Document has no collection"); }
            final C4Collection c4Coll = collection.getOpenC4Collection();
            try { return c4Document.getRevisionHistory(c4Coll, Integer.MAX_VALUE, null); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    //---------------------------------------------
    // Iterator implementation
    //---------------------------------------------

    /**
     * Gets  an iterator over the keys of the document's properties
     *
     * @return The key iterator
     */
    @NonNull
    @Override
    public Iterator<String> iterator() { return internalDict.iterator(); }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Document)) { return false; }

        final Document doc = (Document) o;

        final Database db = getDatabase();
        final Database otherDb = doc.getDatabase();
        // Step 1: Check Database
        if (!(Objects.equals(db, otherDb))) { return false; }

        // Step 2: Check document ID
        // NOTE id never null?
        if (!id.equals(doc.id)) { return false; }

        // Step 3: Check content
        // NOTE: internalDict never null??
        return getContent().equals(doc.getContent());
    }

    // NOTE id and internalDict never null
    @Override
    public int hashCode() {
        final Database db = getDatabase();
        int result = 0;
        if (db != null) {
            final String path = db.getPath();
            if (path != null) { result = path.hashCode(); }
        }
        result = 31 * result + id.hashCode();
        result = 31 * result + getContent().hashCode();
        return result;
    }

    @SuppressWarnings("PMD.ConsecutiveLiteralAppends")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Document{").append(ClassUtils.objId(this))
            .append(id).append('@').append(getRevisionID())
            .append('(').append(isMutable() ? '+' : '.').append(isDeleted() ? '?' : '.').append("):");

        boolean first = true;
        for (String key: getKeys()) {
            if (first) { first = false; }
            else { buf.append(','); }
            buf.append(key).append("=>").append(getValue(key));
        }

        return buf.append('}').toString();
    }

    @NonNull
    protected final Dictionary getContent() {
        synchronized (lock) { return internalDict; }
    }

    protected final void setContent(@NonNull Dictionary content) {
        Preconditions.assertNotNull(content, "content");
        synchronized (lock) { internalDict = content; }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    final boolean isMutable() { return mutable; }

    int compareAge(@NonNull Document target) { return Long.compare(getTimestamp(), target.getTimestamp()); }

    final boolean isNewDocument() { return getRevisionID() == null; }

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    final boolean exists() {
        synchronized (lock) { return (c4Document != null) && c4Document.docExists(); }
    }

    /**
     * Return whether the document is deleted
     *
     * @return true if deleted, false otherwise
     */
    final boolean isDeleted() {
        synchronized (lock) { return (c4Document != null) && c4Document.isRevDeleted(); }
    }

    @Nullable
    final Database getDatabase() {
        synchronized (lock) { return (collection == null) ? null : collection.getDatabase(); }
    }

    void setCollection(@Nullable Collection collection) {
        synchronized (lock) { this.collection = collection; }
    }

    @Nullable
    final C4Document getC4doc() {
        synchronized (lock) { return c4Document; }
    }

    final void replaceC4Document(@Nullable C4Document c4doc) {
        synchronized (lock) { updateC4DocumentLocked(c4doc); }
    }

    final boolean selectConflictingRevision() throws LiteCoreException {
        synchronized (lock) {
            if (c4Document == null) { return false; }

            boolean foundConflict = false;
            while (!foundConflict) {
                try { c4Document.selectNextLeafRevision(true, true); }
                catch (LiteCoreException e) {
                    // NOTE: other platforms checks if return value from c4doc_selectNextLeafRevision() is false
                    if (e.code == 0) { break; }
                    else { throw e; }
                }
                foundConflict = c4Document.isRevConflicted();
            }

            if (foundConflict) { setC4Document(c4Document, isMutable()); }

            return foundConflict;
        }
    }

    // NOTE: the FLSliceResult returned by this method must be released by the caller
    @NonNull
    final FLSliceResult encode() throws LiteCoreException {
        final Database db = getDatabase();
        if (db == null) { throw new CouchbaseLiteError("Encode called with null database"); }

        try (FLEncoder encoder = db.getSharedFleeceEncoder()) {
            encoder.setArg(Blob.ENCODER_ARG_DB, getDatabase());
            getContent().encodeTo(encoder);
            return encoder.finish2();
        }
    }

    //---------------------------------------------
    // Private access
    //---------------------------------------------

    // Sets c4doc and updates the root dictionary
    private void setC4Document(@Nullable C4Document c4doc, boolean mutable) {
        synchronized (lock) {
            updateC4DocumentLocked(c4doc);
            setContentLocked(((c4doc == null) || c4doc.isRevDeleted()) ? null : c4doc.getSelectedBody2(), mutable);
        }
    }

    @GuardedBy("lock")
    private void updateC4DocumentLocked(@Nullable C4Document c4Doc) {
        if (c4Document == c4Doc) { return; }

        // This seems like a great place to close the old c4Document.
        // It appears, though, that there may be other live references
        // and that closing it here can cause failures.
        // See C4Document.close()
        c4Document = c4Doc;

        if (c4Doc != null) { revId = null; }
    }

    @GuardedBy("lock")
    private void setContentLocked(@Nullable FLDict data, boolean mutable) {
        this.data = data;

        if (data == null) {
            internalDict = mutable ? new MutableDictionary() : new Dictionary();
            root = null;
            return;
        }

        final Database db = getDatabase();
        if (db == null) { throw new CouchbaseLiteError("document has not been saved to a database"); }

        final MRoot newRoot = new MRoot(new DocContext(db, c4Document), data.toFLValue(), mutable);
        internalDict = (Dictionary) Preconditions.assertNotNull(newRoot.toJFleece(), "root dictionary");
        root = newRoot;
    }
}

