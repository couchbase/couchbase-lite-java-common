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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.fleece.FLArrayIterator;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.JSONEncoder;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Result represents a row of result set returned by a Query.
 */
@SuppressWarnings("PMD.GodClass")
public final class Result implements ArrayInterface, DictionaryInterface, Iterable<String> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final ResultSet rs;
    private final List<FLValue> values;
    private final long missingColumns;
    private final DbContext context;

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    Result(ResultSet rs, C4QueryEnumerator c4enum, DbContext context) {
        this.rs = rs;
        this.values = extractColumns(c4enum.getColumns());
        this.missingColumns = c4enum.getMissingColumns();
        this.context = context;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    //---------------------------------------------
    // implementation of common between ReadOnlyArrayInterface and ReadOnlyDictionaryInterface
    //--------------------------------------------

    /**
     * @return the number of the values in the result.
     */
    @Override
    public int count() { return rs.getColumnCount(); }

    //---------------------------------------------
    // implementation of ReadOnlyArrayInterface
    //---------------------------------------------

    /**
     * The result value at the given index.
     *
     * @param index the index of the required value.
     * @return the value.
     */
    @Override
    public Object getValue(int index) {
        assertInBounds(index);
        return fleeceValueToObject(index);
    }

    /**
     * The result at the given index as a String
     *
     * @param index the index of the required value.
     * @return a String value.
     */
    @Override
    public String getString(int index) {
        assertInBounds(index);
        final Object obj = fleeceValueToObject(index);
        return !(obj instanceof String) ? null : (String) obj;
    }

    /**
     * The result  at the given index as a Number
     *
     * @param index the index of the required value.
     * @return a Number value.
     */
    @Override
    public Number getNumber(int index) {
        assertInBounds(index);
        return CBLConverter.asNumber(fleeceValueToObject(index));
    }

    /**
     * The result at the given index as an int
     *
     * @param index the index of the required value.
     * @return an int value.
     */
    @Override
    public int getInt(int index) {
        assertInBounds(index);
        final FLValue flValue = values.get(index);
        return (flValue == null) ? 0 : (int) flValue.asInt();
    }

    /**
     * The result at the given index as a long
     *
     * @param index the index of the required value.
     * @return a long value.
     */
    @Override
    public long getLong(int index) {
        assertInBounds(index);
        final FLValue flValue = values.get(index);
        return (flValue == null) ? 0L : flValue.asInt();
    }

    /**
     * The result at the given index as a float
     *
     * @param index the index of the required value.
     * @return a float value.
     */
    @Override
    public float getFloat(int index) {
        assertInBounds(index);
        final FLValue flValue = values.get(index);
        return (flValue == null) ? 0.0F : flValue.asFloat();
    }

    /**
     * The result at the given index as a double
     *
     * @param index the index of the required value.
     * @return a double value.
     */
    @Override
    public double getDouble(int index) {
        assertInBounds(index);
        final FLValue flValue = values.get(index);
        return (flValue == null) ? 0.0 : flValue.asDouble();
    }

    /**
     * The result at the given index as a boolean
     *
     * @param index the index of the required value.
     * @return a boolean value.
     */
    @Override
    public boolean getBoolean(int index) {
        assertInBounds(index);
        final FLValue flValue = values.get(index);
        return (flValue != null) && flValue.asBool();
    }

    /**
     * The result at the given index as a Blob
     *
     * @param index the index of the required value.
     * @return a Blob.
     */
    @Override
    public Blob getBlob(int index) {
        assertInBounds(index);
        final Object obj = fleeceValueToObject(index);
        return !(obj instanceof Blob) ? null : (Blob) obj;
    }

    /**
     * The result at the given index as a Date
     *
     * @param index the index of the required value.
     * @return a Date.
     */
    @Override
    public Date getDate(int index) {
        assertInBounds(index);
        return JSONUtils.toDate(getString(index));
    }

    /**
     * The result at the given index as an Array
     *
     * @param index the index of the required value.
     * @return an Array.
     */
    @Override
    public Array getArray(int index) {
        assertInBounds(index);
        final Object obj = fleeceValueToObject(index);
        return !(obj instanceof Array) ? null : (Array) obj;
    }

    /**
     * The result at the given index as a Dictionary
     *
     * @param index the index of the required value.
     * @return a Dictionary.
     */
    @Override
    public Dictionary getDictionary(int index) {
        assertInBounds(index);
        final Object obj = fleeceValueToObject(index);
        return !(obj instanceof Dictionary) ? null : (Dictionary) obj;
    }

    /**
     * Gets all values as a List. The types of the values contained in the returned List
     * are Array, Blob, Dictionary, Number types, String, and null.
     *
     * @return a List containing all values.
     */
    @NonNull
    @Override
    public List<Object> toList() {
        final int nVals = count();
        final List<Object> array = new ArrayList<>(nVals);
        for (int i = 0; i < nVals; i++) { array.add(values.get(i).asObject()); }
        return array;
    }

    //---------------------------------------------
    // implementation of ReadOnlyDictionaryInterface
    //---------------------------------------------

    /**
     * @return a list of keys
     */
    @NonNull
    @Override
    public List<String> getKeys() { return rs.getColumnNames(); }

    /**
     * The result value for the given key as a Object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Object.
     */
    @Nullable
    @Override
    public Object getValue(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getValue(index);
    }

    /**
     * The result value for the given key as a String object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The String object.
     */
    @Nullable
    @Override
    public String getString(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getString(index);
    }

    /**
     * The projecting result value for the given key  as a Number object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Number object.
     */
    @Nullable
    @Override
    public Number getNumber(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getNumber(index);
    }

    /**
     * The projecting result value for the given key  as a integer value
     * Returns 0 if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The integer value.
     */
    @Override
    public int getInt(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? 0 : getInt(index);
    }

    /**
     * The projecting result value for the given key  as a long value
     * Returns 0L if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The long value.
     */
    @Override
    public long getLong(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? 0L : getLong(index);
    }

    /**
     * The projecting result value for the given key  as a float value.
     * Returns 0.0f if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The float value.
     */
    @Override
    public float getFloat(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? 0.0F : getFloat(index);
    }

    /**
     * The projecting result value for the given key as a double value.
     * Returns 0.0 if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The double value.
     */
    @Override
    public double getDouble(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? 0.0 : getDouble(index);
    }

    /**
     * The projecting result value for the given key  as a boolean value.
     * Returns false if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return isInBounds(index) && getBoolean(index);
    }

    /**
     * The projecting result value for the given key  as a Blob object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Blob object.
     */
    @Nullable
    @Override
    public Blob getBlob(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getBlob(index);
    }

    /**
     * The projecting result value for the given key as a Date object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Date object.
     */
    @Nullable
    @Override
    public Date getDate(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getDate(index);
    }

    /**
     * The projecting result value for the given key as a readonly Array object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Array object.
     */
    @Nullable
    @Override
    public Array getArray(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getArray(index);
    }

    /**
     * The projecting result value for the given key as a readonly Dictionary object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Dictionary object.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(@NonNull String key) {
        final int index = indexForColumnName(Preconditions.assertNotNull(key, "key"));
        return (!isInBounds(index)) ? null : getDictionary(index);
    }

    /**
     * Gets all values as a Dictionary. The value types of the values contained
     * in the returned Dictionary object are Array, Blob, Dictionary,
     * Number types, String, and null.
     *
     * @return The Map representing all values.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() {
        final int nVals = values.size();
        final Map<String, Object> dict = new HashMap<>(nVals);
        for (String name: rs.getColumnNames()) {
            final int i = indexForColumnName(name);
            if ((i < 0) || (i >= nVals)) { continue; }
            dict.put(name, values.get(i).asObject());
        }
        return dict;
    }

    @NonNull
    @Override
    public String toJSON() {
        final int nVals = values.size();

        try (JSONEncoder enc = new JSONEncoder()) {
            enc.beginDict(nVals);
            for (String columnName: rs.getColumnNames()) {
                final int i = indexForColumnName(columnName);
                if ((i < 0) || (i >= nVals)) { continue; }

                enc.writeKey(columnName);
                enc.writeValue(values.get(i));
            }
            enc.endDict();
            return enc.finishJSON();
        }
        catch (LiteCoreException e) {
            throw new IllegalStateException(
                "Failed marshalling Document to JSON",
                CouchbaseLiteException.convertException(e));
        }
    }

    /**
     * Tests whether a projecting result key exists or not.
     *
     * @param key The select result key.
     * @return True if exists, otherwise false.
     */
    @Override
    public boolean contains(@NonNull String key) {
        return isInBounds(indexForColumnName(Preconditions.assertNotNull(key, "key")));
    }

    //---------------------------------------------
    // implementation of Iterable
    //---------------------------------------------

    /**
     * Gets  an iterator over the projecting result keys.
     *
     * @return The Iterator object of all result keys.
     */
    @NonNull
    @Override
    public Iterator<String> iterator() { return getKeys().iterator(); }

    //---------------------------------------------
    // private access
    //---------------------------------------------

    private int indexForColumnName(String name) {
        final int index = rs.getColumnIndex(name);
        if (index < 0) { return -1; }
        return ((missingColumns & (1 << index)) == 0) ? index : -1;
    }

    private Object fleeceValueToObject(int index) {
        final FLValue value = values.get(index);
        if (value == null) { return null; }
        final MRoot root = new MRoot(context, value, false);
        synchronized (rs.getQuery().getDatabase().getLock()) { return root.asNative(); }
    }

    private List<FLValue> extractColumns(FLArrayIterator columns) {
        final List<FLValue> values = new ArrayList<>();
        final int count = rs.getColumnCount();
        for (int i = 0; i < count; i++) { values.add(columns.getValueAt(i)); }
        return values;
    }

    private boolean isInBounds(int index) { return (index >= 0) && (index < count()); }

    private void assertInBounds(int index) {
        if (!isInBounds(index)) {
            throw new ArrayIndexOutOfBoundsException("index " + index + " must be between 0 and " + count());
        }
    }
}
