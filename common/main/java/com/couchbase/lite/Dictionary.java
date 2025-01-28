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

import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MDict;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Dictionary provides readonly access to dictionary data.
 */
public class Dictionary extends AbstractJFleeceCollection<MDict> implements DictionaryInterface, Iterable<String> {
    //---------------------------------------------
    // Types
    //---------------------------------------------
    private class DictionaryIterator implements Iterator<String> {
        private final long mutations;
        private final Iterator<String> iterator;

        DictionaryIterator() {
            this.mutations = Dictionary.this.contents.getLocalMutationCount();
            this.iterator = Dictionary.this.getKeys().iterator();
        }

        @Override
        public boolean hasNext() { return iterator.hasNext(); }

        @Nullable
        @Override
        public String next() {
            if (Dictionary.this.contents.getLocalMutationCount() != mutations) {
                throw new ConcurrentModificationException("Dictionary modified during iteration");
            }
            return iterator.next();
        }
    }

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    // Construct a new dictionary with the passed content
    protected Dictionary(@NonNull MDict dict) { super(dict); }

    // Construct a new empty Dictionary
    Dictionary() { this(new MDict()); }

    // Slot(??) constructor
    Dictionary(@NonNull MValue val, @Nullable MCollection parent) { this(new MDict(val, parent)); }

    //-------------------------------------------------------------------------
    // API - public methods
    //-------------------------------------------------------------------------

    /**
     * Return a mutable copy of the dictionary
     *
     * @return the MutableDictionary instance
     */
    @Override
    @NonNull
    public MutableDictionary toMutable() {
        synchronized (lock) { return new MutableDictionary(this); }
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getValue(String), because it does not have to allocate an Object for the
     * property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(@NonNull String key) { return !getMValueAt(key).isEmpty(); }


    @NonNull
    @Override
    public List<String> getKeys() {
        synchronized (lock) { return contents.getKeys(); }
    }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) { return asBoolean(getJFleeceAt(key)); }


    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(@NonNull String key) { return toInteger(getMValueAt(key), contents); }

    /**
     * Gets a property's value as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(@NonNull String key) { return toLong(getMValueAt(key), contents); }

    /**
     * Gets a property's value as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(@NonNull String key) { return toFloat(getMValueAt(key), contents); }

    /**
     * Gets a property's value as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(@NonNull String key) { return toDouble(getMValueAt(key), contents); }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Nullable
    @Override
    public Number getNumber(@NonNull String key) { return asNumber(getJFleeceAt(key)); }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Nullable
    @Override
    public String getString(@NonNull String key) { return asString(getJFleeceAt(key)); }

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
    public Date getDate(@NonNull String key) { return JSONUtils.toDate(getString(key)); }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Nullable
    @Override
    public Blob getBlob(@NonNull String key) { return asBlob(getJFleeceAt(key)); }

    /**
     * Get a property's value as an Array.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Nullable
    @Override
    public Array getArray(@NonNull String key) { return asArray(getJFleeceAt(key)); }

    /**
     * Get a property's value as a Dictionary.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(@NonNull String key) { return asDictionary(getJFleeceAt(key)); }

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
    public Object getValue(@NonNull String key) { return getJFleeceAt(key); }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the value at the index, or null if the value doesn't exist or is not of the given class.
     */
    @Nullable
    public <T> T getValue(@NonNull Class<T> klass, @NonNull String key) { return asValue(klass, getValue(key)); }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() {
        final Map<String, Object> map = new HashMap<>();
        synchronized (lock) {
            for (String key: contents.getKeys()) {
                Object obj = contents.get(key).toJFleece(contents);
                if (obj instanceof AbstractJFleeceCollection<?>) {
                    obj = toJFleeceCollection((AbstractJFleeceCollection<?>) obj);
                }
                map.put(key, obj);
            }
        }
        return map;
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    /**
     * An iterator over keys of this Dictionary.
     * A call to the <code>next()</code> method of the returned iterator
     * will throw a ConcurrentModificationException, if the MutableDictionary is
     * modified while it is in use.
     *
     * @return an iterator over the dictionary's keys.
     */
    @NonNull
    @Override
    public Iterator<String> iterator() { return new DictionaryIterator(); }

    //-------------------------------------------------------------------------
    // Object overrides
    //-------------------------------------------------------------------------

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Dictionary)) { return false; }

        final Dictionary m = (Dictionary) o;

        if (m.count() != count()) { return false; }
        for (String key: this) {
            final Object value = getValue(key);
            if (value != null) {
                if (!value.equals(m.getValue(key))) { return false; }
            }
            else {
                if (!(m.getValue(key) == null && m.contains(key))) { return false; }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (String key: this) {
            final Object value = getValue(key);
            h += key.hashCode() ^ ((value == null) ? 0 : value.hashCode());
        }
        return h;
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Dictionary{(").append(contents.getStateString()).append(
            ')');

        boolean first = true;
        for (String key: getKeys()) {
            if (!first) { buf.append(", "); }
            else {
                first = false;
                buf.append(' ');
            }
            buf.append(key).append("=>").append(getValue(key));
        }

        return buf.append('}').toString();
    }

    //-------------------------------------------------------------------------
    // Private
    //-------------------------------------------------------------------------

    @Nullable
    private Object getJFleeceAt(@NonNull String key) { return getMValueAt(key).toJFleece(contents); }

    @NonNull
    private MValue getMValueAt(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) { return contents.get(key); }
    }
}
