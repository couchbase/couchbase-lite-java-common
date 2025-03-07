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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.BaseJFleeceCollection;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.fleece.FLArrayIterator;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.JSONEncodable;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Result represents a row of result set returned by a Query.
 * <p>
 * A Result may be referenced <b>only</b> while the ResultSet that contains it is open.
 * An attempt to reference a Result after calling ResultSet.close on the ResultSet that
 * contains it will throw an CouchbaseLiteError
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class Result
    extends BaseJFleeceCollection
    implements ArrayInterface, DictionaryInterface, JSONEncodable, Iterable<String> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final ResultContext context;
    @NonNull
    private final List<FLValue> values;
    private final long missingColumns;

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    Result(@NonNull ResultContext context, @NonNull C4QueryEnumerator c4enum) {
        this.context = context;
        this.values = extractColumns(c4enum.getColumns());
        this.missingColumns = c4enum.getMissingColumns();
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
    public int count() {
        assertOpen();
        return getColumnCount();
    }

    @Override
    public boolean isEmpty() { return count() == 0; }

    //---------------------------------------------
    // implementation of ReadOnlyArrayInterface
    //---------------------------------------------

    /**
     * The result at the given index interpreted as a boolean.
     * Returns false if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a boolean value.
     */
    @Override
    public boolean getBoolean(int index) { return asBoolean(getFleeceAt(index)); }

    /**
     * The result at the given index interpreted as and an int.
     * Returns 0 if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return an int value.
     */
    @Override
    public int getInt(int index) { return toInteger(getFLValueAt(index)); }

    /**
     * The result at the given index interpreted as a long.
     * Returns 0 if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a long value.
     */
    @Override
    public long getLong(int index) { return toLong(getFLValueAt(index)); }

    /**
     * The result at the given index interpreted as a float.
     * Returns 0.0F if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a float value.
     */
    @Override
    public float getFloat(int index) { return toFloat(getFLValueAt(index)); }

    /**
     * The result at the given index interpreted as a double.
     * Returns 0.0 if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a double value.
     */
    @Override
    public double getDouble(int index) { return toDouble(getFLValueAt(index)); }

    /**
     * The result at the given index interpreted as a Number.
     * Returns null if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a Number value.
     */
    @Nullable
    @Override
    public Number getNumber(int index) { return asNumber(getFleeceAt(index)); }

    /**
     * The result at the given index converted to a String
     *
     * @param index the index of the required value.
     * @return a String value.
     */
    @Nullable
    @Override
    public String getString(int index) { return asString(getFleeceAt(index)); }

    /**
     * Gets value at the given index as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parsable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Date value or null.
     */
    @Nullable
    @Override
    public Date getDate(int index) { return JSONUtils.toDate(getString(index)); }

    /**
     * The result at the given index interpreted as a Blob.
     * Returns null if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a Blob.
     */
    @Nullable
    @Override
    public Blob getBlob(int index) { return asBlob(getFleeceAt(index)); }

    /**
     * The result at the given index interpreted as an Array.
     * Returns null if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return an Array.
     */
    @Nullable
    @Override
    public Array getArray(int index) { return asArray(getFleeceAt(index)); }

    /**
     * The result at the given index interpreted as a Dictionary.
     * Returns null if the value cannot be so interpreted.
     *
     * @param index the index of the required value.
     * @return a Dictionary.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(int index) { return asDictionary(getFleeceAt(index)); }

    /**
     * The result value at the given index.
     *
     * @param index the index of the required value.
     * @return the value.
     */
    @Nullable
    @Override
    public Object getValue(int index) { return getFleeceAt(index); }

    /**
     * The result value at the given index.
     *
     * @param index the index. This value must be 0 &lt;= index &lt; count().
     * @param klass the class of the object.
     * @return the array value at the index.
     */
    @Nullable
    public <T> T getValue(@NonNull Class<T> klass, int index) { return asValue(klass, getValue(index)); }

    /**
     * Gets all the values as a List. The types of the values contained in the returned List
     * are Array, Blob, Dictionary, Number types, String, and null.
     *
     * @return a List containing all values.
     */
    @NonNull
    @Override
    public List<Object> toList() {
        assertOpen();
        final int nVals = count();
        final List<Object> list = new ArrayList<>(nVals);
        for (int i = 0; i < nVals; i++) {
            Object obj = getFleeceAt(i);
            if (obj instanceof AbstractJFleeceCollection<?>) {
                obj = toJFleeceCollection((AbstractJFleeceCollection<?>) obj);
            }
            list.add(obj);
        }
        return list;
    }

    //---------------------------------------------
    // implementation of ReadOnlyDictionaryInterface
    //---------------------------------------------

    /**
     * Tests whether key exists or not.
     *
     * @param key The select result key.
     * @return True if exists, otherwise false.
     */
    @Override
    public boolean contains(@NonNull String key) {
        assertOpen();
        return 0 <= getIndexForKey(key);
    }

    /**
     * @return a list of keys
     */
    @NonNull
    @Override
    public List<String> getKeys() {
        assertOpen();
        return getColumnNames();
    }

    /**
     * The result value for the given key as a boolean
     * Returns false if the key doesn't exist or if the value is not a boolean
     *
     * @param key The select result key.
     * @return The boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index >= 0) && getBoolean(index);
    }

    /**
     * The result value for the given key as an int
     * Returns 0 if the key doesn't exist or if the value is not a int
     *
     * @param key The select result key.
     * @return The integer value.
     */
    @Override
    public int getInt(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? 0 : getInt(index);
    }

    /**
     * The result value for the given key as a long
     * Returns 0L if the key doesn't exist or if the value is not a long
     *
     * @param key The select result key.
     * @return The long value.
     */
    @Override
    public long getLong(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? 0L : getLong(index);
    }

    /**
     * The result value for the given key as a float
     * Returns 0.0F if the key doesn't exist or if the value is not a float
     *
     * @param key The select result key.
     * @return The float value.
     */
    @Override
    public float getFloat(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? 0.0F : getFloat(index);
    }

    /**
     * The result value for the given key as a double
     * Returns 0.0 if the key doesn't exist or if the value is not a double
     *
     * @param key The select result key.
     * @return The double value.
     */
    @Override
    public double getDouble(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? 0.0 : getDouble(index);
    }

    /**
     * The result value for the given key as a Number
     * Returns null if the key doesn't exist or if the value is not a Number
     *
     * @param key The select result key.
     * @return The Number object.
     */
    @Nullable
    @Override
    public Number getNumber(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getNumber(index);
    }

    /**
     * The result value for the given key as a String
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The String object.
     */
    @Nullable
    @Override
    public String getString(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getString(index);
    }

    /**
     * The result value for the given key as a Date
     * Returns null if the key doesn't exist or if the value is not a Date
     *
     * @param key The select result key.
     * @return The Date object.
     */
    @Nullable
    @Override
    public Date getDate(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getDate(index);
    }

    /**
     * The result value for the given key as a Blob
     * Returns null if the key doesn't exist or if the value is not a Blob
     *
     * @param key The select result key.
     * @return The Blob object.
     */
    @Nullable
    @Override
    public Blob getBlob(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getBlob(index);
    }

    /**
     * The result value for the given key as a Array
     * Returns null if the key doesn't exist or if the value is not an Array
     *
     * @param key The select result key.
     * @return The Array object.
     */
    @Nullable
    @Override
    public Array getArray(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getArray(index);
    }

    /**
     * The result value for the given key as a Dictionary
     * Returns null if the key doesn't exist or if the value is not a Dictionary
     *
     * @param key The select result key.
     * @return The Dictionary object.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getDictionary(index);
    }

    /**
     * The result value for the given key as an Object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Object.
     */
    @Nullable
    @Override
    public Object getValue(@NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getValue(index);
    }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return The value in the dictionary at the key (or null).
     * @throws ClassCastException if the value is not of the passed class.
     */
    @Nullable
    public <T> T getValue(@NonNull Class<T> klass, @NonNull String key) {
        final int index = getIndexForKey(key);
        return (index < 0) ? null : getValue(klass, index);
    }

    /**
     * Gets all values as a Map. The keys in the returned map are the names of columns that have
     * values.  The types of the values are Array, Blob, Dictionary, Number types, String, and null.
     *
     * @return The Map representing all values.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() {
        assertOpen();
        final int n = values.size();
        final Map<String, Object> map = new HashMap<>(n);
        for (String key: getColumnNames()) {
            final int i = getIndexForKey(key);
            if ((i < 0) || (i >= n)) { continue; }
            Object obj = values.get(i).toJava();
            if (obj instanceof AbstractJFleeceCollection<?>) {
                obj = toJFleeceCollection((AbstractJFleeceCollection<?>) obj);
            }
            map.put(key, obj);
        }
        return map;
    }

    /**
     * Encode a Result as a JSON string
     *
     * @return JSON encoded representation of the Result
     * @throws CouchbaseLiteException on encoder failure.
     */
    @NonNull
    @Override
    public String toJSON() throws CouchbaseLiteException {
        assertOpen();

        final int n = values.size();
        try (FLEncoder.JSONEncoder enc = FLEncoder.getJSONEncoder()) {
            enc.beginDict(n);
            for (String columnName: getColumnNames()) {
                final int i = getIndexForKey(columnName);
                if ((i < 0) || (i >= n)) { continue; }

                enc.writeKey(columnName);
                enc.writeValue(values.get(i));
            }
            enc.endDict();
            return enc.finishJSON();
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Cannot encode result: " + this);
        }
    }

    //---------------------------------------------
    // implementation of Iterable
    //---------------------------------------------

    /**
     * Gets  an iterator over the result's keys.
     *
     * @return The Iterator object of all result keys.
     */
    @NonNull
    @Override
    public Iterator<String> iterator() { return getKeys().iterator(); }

    //---------------------------------------------
    // private access
    //---------------------------------------------

    private int getColumnCount() { return context.getResultSet().getColumnCount(); }

    @NonNull
    private List<String> getColumnNames() { return context.getResultSet().getColumnNames(); }

    @Nullable
    private Object getFleeceAt(int index) {
        final FLValue value = getFLValueAt(index);
        if (value == null) { return null; }
        synchronized (Preconditions.assertNotNull(context.getDatabase(), "db").getDbLock()) {
            return new MRoot(context, value, false).toJFleece();
        }
    }

    @Nullable
    private FLValue getFLValueAt(int index) {
        assertValid(index);
        return values.get(index);
    }

    private int getIndexForKey(@Nullable String key) {
        final int index = context.getResultSet().getColumnIndex(Preconditions.assertNotNull(key, "key"));
        if (index < 0) { return -1; }
        if ((missingColumns & (1L << index)) != 0) { return -1; }
        return (!isInBounds(index)) ? -1 : index;
    }

    @NonNull
    private List<FLValue> extractColumns(@NonNull FLArrayIterator columns) {
        final int n = getColumnCount();
        final List<FLValue> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) { values.add(columns.getValueAt(i)); }
        return values;
    }

    private void assertValid(int index) {
        assertOpen();
        if (!isInBounds(index)) { throw new ArrayIndexOutOfBoundsException(index + " is not 0 <= i < " + count()); }
    }

    private void assertOpen() {
        if (context.isClosed()) {
            throw new CouchbaseLiteError("Attempt to use a result after its containing ResultSet has been closed");
        }
    }

    private boolean isInBounds(int index) { return (0 <= index) && (index < count()); }
}
