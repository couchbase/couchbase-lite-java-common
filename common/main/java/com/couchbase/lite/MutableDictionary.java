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
import androidx.annotation.VisibleForTesting;

import java.util.Date;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MDict;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Mutable access to dictionary data.
 */
public final class MutableDictionary extends Dictionary implements MutableDictionaryInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a new empty MutableDictionary.
     */
    public MutableDictionary() { }

    /**
     * Creates a new MutableDictionary with content from the passed Map.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     *
     * @param data the dictionary content map.
     */
    public MutableDictionary(@NonNull Map<String, ?> data) { setData(data); }

    /**
     * Creates a new MutableDictionary with content from the passed JSON string.
     *
     * @param json the dictionary content as a JSON string.
     */
    public MutableDictionary(@NonNull String json) { setJSON(json); }

    // Create a MutableDictionary that is a copy of the passed Dictionary
    MutableDictionary(@NonNull Dictionary dict) { super(new MDict(dict.internalDict, true)); }

    // Called from the MValueConverter.
    MutableDictionary(@NonNull MValue val, @Nullable MCollection parent) { super(val, parent); }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Populate a dictionary with content from a Map.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     * Setting the dictionary content will replace the current data including
     * any existing Array and Dictionary objects.
     *
     * @param data the dictionary object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setData(@NonNull Map<String, ?> data) {
        synchronized (lock) {
            internalDict.clear();
            for (Map.Entry<String, ?> entry: data.entrySet()) {
                internalDict.set(
                    Preconditions.assertNotNull(entry.getKey(), "data key"),
                    new MValue(cblOCopy(entry.getValue())));
            }
        }
        return this;
    }

    /**
     * Populate a dictionary with content from a JSON string.
     * Setting the dictionary content will replace the current data including
     * any existing Array and Dictionary objects.
     *
     * @param json the dictionary object.
     * @return this Document instance
     */
    // ??? This is an expensive way to do this...
    @NonNull
    @Override
    public MutableDictionary setJSON(@NonNull String json) {
        synchronized (lock) {
            try { setData(JSONUtils.fromJSON(new JSONObject(json))); }
            catch (JSONException e) { throw new IllegalArgumentException("Failed parsing JSON", e); }
        }
        return this;
    }

    /**
     * Set an object value by key.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     *
     * @param key   the key.
     * @param value the object value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setValue(@NonNull String key, @Nullable Object value) {
        Preconditions.assertNotNull(key, "key");
        final Object val = cblOCopy(value);
        synchronized (lock) {
            if (MutableContainer.willMutate(val, internalDict.get(key), internalDict)) {
                internalDict.set(key, new MValue(val));
            }
        }
        return this;
    }

    /**
     * Set a String value for the given key.
     *
     * @param key   The key
     * @param value The String value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setString(@NonNull String key, @Nullable String value) { return setValue(key, value); }

    /**
     * Set a Number value for the given key.
     *
     * @param key   The key
     * @param value The number value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setNumber(@NonNull String key, @Nullable Number value) { return setValue(key, value); }

    /**
     * Set an int value for the given key.
     *
     * @param key   The key
     * @param value The int value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setInt(@NonNull String key, int value) { return setValue(key, value); }

    /**
     * Set a long value for the given key.
     *
     * @param key   The key
     * @param value The long value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setLong(@NonNull String key, long value) { return setValue(key, value); }

    /**
     * Set a float value for the given key.
     *
     * @param key   The key
     * @param value The float value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setFloat(@NonNull String key, float value) { return setValue(key, value); }

    /**
     * Set a double value for the given key.
     *
     * @param key   The key
     * @param value The double value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDouble(@NonNull String key, double value) { return setValue(key, value); }

    /**
     * Set a boolean value for the given key.
     *
     * @param key   The key
     * @param value The boolean value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setBoolean(@NonNull String key, boolean value) { return setValue(key, value); }

    /**
     * Set a Blob object for the given key.
     *
     * @param key   The key
     * @param value The Blob object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setBlob(@NonNull String key, @Nullable Blob value) { return setValue(key, value); }

    /**
     * Set a Date object for the given key.
     *
     * @param key   The key
     * @param value The Date object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDate(@NonNull String key, @Nullable Date value) { return setValue(key, value); }

    /**
     * Set an Array object for the given key.
     *
     * @param key   The key
     * @param value The Array object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setArray(@NonNull String key, @Nullable Array value) { return setValue(key, value); }

    /**
     * Set a Dictionary object for the given key.
     *
     * @param key   The key
     * @param value The Dictionary object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDictionary(@NonNull String key, @Nullable Dictionary value) {
        return setValue(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary remove(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { internalDict.remove(key); }
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
    public MutableArray getArray(@NonNull String key) { return (MutableArray) super.getArray(key); }

    /**
     * Get a property's value as a Dictionary.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Nullable
    @Override
    public MutableDictionary getDictionary(@NonNull String key) { return (MutableDictionary) super.getDictionary(key); }

    /**
     * Mutable objects may not be encoded as JSON
     *
     * @return never
     * @throws CouchbaseLiteError always
     */
    @NonNull
    @Override
    public String toJSON() { throw new CouchbaseLiteError("Mutable objects may not be encoded as JSON"); }

    @VisibleForTesting
    boolean isChanged() {
        synchronized (lock) { return internalDict.isMutated(); }
    }

    @Nullable
    private Object cblOCopy(@Nullable Object value) {
        return (value == this)
            ? ((Dictionary) value).toMutable()
            : MutableContainer.toCBL(value);
    }
}
