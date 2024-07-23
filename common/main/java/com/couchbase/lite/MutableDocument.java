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

import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.couchbase.lite.internal.core.C4Document;


/**
 * A Couchbase Lite Document. A document has key/value properties like a Map.
 */
public final class MutableDocument extends Document implements MutableDictionaryInterface {

    @NonNull
    private static String createUUID() { return UUID.randomUUID().toString().toLowerCase(Locale.ENGLISH); }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Creates a new Document object with a new random UUID. The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     */
    public MutableDocument() { this((String) null); }

    /**
     * Creates a new Document with the given ID.  If the id is null, the document
     * will be created with a new random UUID. The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     *
     * @param id the document ID or null.
     */
    public MutableDocument(@Nullable String id) { this(null, id, null); }

    /**
     * Creates a new Document with a new random UUID and the map as the content.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     * The created document will be saved into a database when you call Database.save(Document)
     * with this document object.
     *
     * @param data the Map object
     */
    public MutableDocument(@NonNull Map<String, ?> data) { this(null, data); }

    /**
     * Creates a new Document with a given ID and content from the passed Map.
     * If the id is null, the document will be created with a new random UUID.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * The List and Map must contain only the above types.
     * The created document will be saved into a database when you call
     * the Database's save(Document) method with the document object given.
     *
     * @param id   the document ID.
     * @param data the Map object
     */
    public MutableDocument(@Nullable String id, @NonNull Map<String, ?> data) {
        this(null, id, null);
        if (data != null) { setData(data); }
    }

    /**
     * Creates a new Document with the given ID and content from the passed JSON string.
     * If the id is null, the document will be created with a new random UUID.
     * The created document will be saved into a database when you call the Database's
     * save(Document) method with the document object given.
     *
     * @param id   the document ID or null.
     * @param json the document content as a JSON string.
     */
    public MutableDocument(@Nullable String id, @NonNull String json) {
        this(null, id, null);
        setJSON(json);
    }

    MutableDocument(@NonNull Document doc) {
        this(doc.getCollection(), doc.getId(), doc.getC4doc());
        if (doc.isMutable()) { setContent(doc.getContent().toMutable()); }
    }

    // Expressing this constructor in terms of the previous one
    // fails because the previous constructor does not copy
    // the source document's mutated state.  While it *does* copy the
    // mutable state, if the source has been changed since it was created
    // the previous constructor will lose those changes when it is encoded.
    MutableDocument(@NonNull String id, @NonNull Document doc) {
        this(doc.getCollection(), id, null);
        setData(doc.getContent().toMap());
    }

    private MutableDocument(@Nullable Collection collection, @Nullable String id, @Nullable C4Document c4doc) {
        super(collection, (id != null) ? id : createUUID(), c4doc, true);
    }

    //---------------------------------------------
    // public API methods
    //---------------------------------------------

    /**
     * Returns the copy of this MutableDocument object.
     *
     * @return The MutableDocument object
     */
    @NonNull
    @Override
    public MutableDocument toMutable() { return new MutableDocument(this); }

    //---------------------------------------------
    // DictionaryInterface implementation
    //---------------------------------------------

    /**
     * Populate a document with content from a Map.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.  Setting the
     * document content will replace the current data including the existing Array and Dictionary
     * objects.
     *
     * @param data the dictionary object.
     * @return this Document instance
     */
    @NonNull
    @Override
    public MutableDocument setData(@NonNull Map<String, ?> data) {
        getMutableContent().setData(data);
        return this;
    }

    /**
     * Populate a document with content from a JSON string.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.  Setting the
     * document content will replace the current data including the existing Array and Dictionary
     * objects.
     *
     * @param json the dictionary object.
     * @return this Document instance
     */

    @NonNull
    @Override
    public MutableDocument setJSON(@NonNull String json) {
        getMutableContent().setJSON(json);
        return this;
    }

    /**
     * Set an object value by key. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. If present, Lists, Maps and Dictionaries may contain only
     * the above types. An Date object will be converted to an ISO-8601 format string.
     *
     * @param key   the key.
     * @param value the Object value.
     * @return this Document instance
     */
    @NonNull
    @Override
    public MutableDocument setValue(@NonNull String key, @Nullable Object value) {
        getMutableContent().setValue(key, value);
        return this;
    }

    /**
     * Set a String value for the given key
     *
     * @param key   the key.
     * @param value the String value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setString(@NonNull String key, @Nullable String value) { return setValue(key, value); }

    /**
     * Set a Number value for the given key
     *
     * @param key   the key.
     * @param value the Number value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setNumber(@NonNull String key, @Nullable Number value) { return setValue(key, value); }

    /**
     * Set a integer value for the given key
     *
     * @param key   the key.
     * @param value the integer value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setInt(@NonNull String key, int value) { return setValue(key, value); }

    /**
     * Set a long value for the given key
     *
     * @param key   the key.
     * @param value the long value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setLong(@NonNull String key, long value) { return setValue(key, value); }

    /**
     * Set a float value for the given key
     *
     * @param key   the key.
     * @param value the float value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setFloat(@NonNull String key, float value) { return setValue(key, value); }

    /**
     * Set a double value for the given key
     *
     * @param key   the key.
     * @param value the double value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setDouble(@NonNull String key, double value) { return setValue(key, value); }

    /**
     * Set a boolean value for the given key
     *
     * @param key   the key.
     * @param value the boolean value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setBoolean(@NonNull String key, boolean value) { return setValue(key, value); }

    /**
     * Set a Blob value for the given key
     *
     * @param key   the key.
     * @param value the Blob value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setBlob(@NonNull String key, @Nullable Blob value) { return setValue(key, value); }

    /**
     * Set a Date value for the given key
     *
     * @param key   the key.
     * @param value the Date value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setDate(@NonNull String key, @Nullable Date value) { return setValue(key, value); }

    /**
     * Set an Array value for the given key
     *
     * @param key   the key.
     * @param value the Array value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setArray(@NonNull String key, @Nullable Array value) { return setValue(key, value); }

    /**
     * Set a Dictionary value for the given key
     *
     * @param key   the key.
     * @param value the Dictionary value.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument setDictionary(@NonNull String key, @Nullable Dictionary value) {
        return setValue(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this MutableDocument instance
     */
    @NonNull
    @Override
    public MutableDocument remove(@NonNull String key) {
        getMutableContent().remove(key);
        return this;
    }

    /**
     * Get a property's value as a Array.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Nullable
    @Override
    public MutableArray getArray(@NonNull String key) { return getMutableContent().getArray(key); }

    /**
     * Get a property's value as a Dictionary.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Nullable
    @Override
    public MutableDictionary getDictionary(@NonNull String key) { return getMutableContent().getDictionary(key); }

    /**
     * Unimplemented: Mutable objects may not be encoded as JSON
     *
     * @return never
     * @throws CouchbaseLiteError always
     */
    @NonNull
    @Override
    public String toJSON() { throw new CouchbaseLiteError("Mutable objects may not be encoded as JSON"); }

    //---------------------------------------------
    // Private access
    //---------------------------------------------

    @NonNull
    private MutableDictionary getMutableContent() { return (MutableDictionary) getContent(); }
}
