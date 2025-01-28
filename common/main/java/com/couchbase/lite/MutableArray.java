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
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

import com.couchbase.lite.internal.fleece.MArray;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.JSONUtils;


/**
 * Mutable access to array data.
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class MutableArray extends Array implements MutableArrayInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a new empty MutableArray.
     */
    public MutableArray() { }

    /**
     * Creates a new MutableArray with content from the passed List.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     *
     * @param data the array content list
     */
    public MutableArray(@NonNull List<?> data) { setData(data); }

    /**
     * Creates a new MutableArray with content from the passed JSON string.
     *
     * @param json the array content as a JSON string.
     */
    public MutableArray(@NonNull String json) { setJSON(json); }

    // Create a MutableArray that is a copy of the passed Array
    MutableArray(@NonNull Array array) { super(new MArray(array.contents, true)); }

    // Called from the MValueConverter.
    MutableArray(@NonNull MValue val, @Nullable MCollection parent) { super(val, parent); }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Populate an array with content from a Map.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     * Setting the array content will replace the current data including
     * any existing Array and Dictionary objects.
     *
     * @param data the array
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setData(@NonNull List<?> data) {
        synchronized (lock) {
            contents.clear();
            for (Object obj: data) { contents.append(toJFleece(obj)); }
        }
        return this;
    }

    /**
     * Populate an array with content from a JSON string.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     * Setting the array content will replace the current data including
     * any existing Array and Dictionary objects.
     *
     * @param json the dictionary object.
     * @return this Document instance
     */
    // ??? This is a ridiculously expensive way to do this...
    @NonNull
    @Override
    public MutableArray setJSON(@NonNull String json) {
        synchronized (lock) {
            try { setData(JSONUtils.fromJSON(new JSONArray(json))); }
            catch (JSONException e) { throw new IllegalArgumentException("Failed parsing JSON", e); }
        }
        return this;
    }

    /**
     * Set an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setValue(int index, @Nullable Object value) {
        final Object val = toJFleece(value);
        synchronized (lock) {
            if (willMutate(val, contents.get(index), contents)
                && (!contents.set(index, val))) {
                throw new IndexOutOfBoundsException("Array index " + index + " is out of range");
            }
        }
        return this;
    }

    /**
     * Sets an String object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the String object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setString(int index, @Nullable String value) { return setValue(index, value); }

    /**
     * Sets an NSNumber object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Number object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setNumber(int index, @Nullable Number value) { return setValue(index, value); }

    /**
     * Sets an integer value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the int value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setInt(int index, int value) { return setValue(index, value); }

    /**
     * Sets an integer value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the long value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setLong(int index, long value) { return setValue(index, value); }

    /**
     * Sets a float value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the float value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setFloat(int index, float value) { return setValue(index, value); }

    /**
     * Sets a double value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the double value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setDouble(int index, double value) { return setValue(index, value); }

    /**
     * Sets a boolean value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the boolean value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setBoolean(int index, boolean value) { return setValue(index, value); }

    /**
     * Sets a Blob object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Blob object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setBlob(int index, @Nullable Blob value) { return setValue(index, value); }

    /**
     * Sets a Date object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Date object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setDate(int index, @Nullable Date value) { return setValue(index, value); }

    /**
     * Sets a Array object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Array object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setArray(int index, @Nullable Array value) { return setValue(index, value); }

    /**
     * Sets a Dictionary object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Dictionary object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setDictionary(int index, @Nullable Dictionary value) { return setValue(index, value); }

    /**
     * Adds an object to the end of the array.
     *
     * @param value the object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addValue(@Nullable Object value) {
        final Object val = toJFleece(value);
        synchronized (lock) { contents.append(val); }
        return this;
    }

    /**
     * Adds a String object to the end of the array.
     *
     * @param value the String object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addString(@Nullable String value) { return addValue(value); }

    /**
     * Adds a Number object to the end of the array.
     *
     * @param value the Number object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addNumber(@Nullable Number value) { return addValue(value); }

    /**
     * Adds an integer value to the end of the array.
     *
     * @param value the int value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addInt(int value) { return addValue(value); }

    /**
     * Adds a long value to the end of the array.
     *
     * @param value the long value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addLong(long value) { return addValue(value); }

    /**
     * Adds a float value to the end of the array.
     *
     * @param value the float value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addFloat(float value) { return addValue(value); }

    /**
     * Adds a double value to the end of the array.
     *
     * @param value the double value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addDouble(double value) { return addValue(value); }

    /**
     * Adds a boolean value to the end of the array.
     *
     * @param value the boolean value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addBoolean(boolean value) { return addValue(value); }

    /**
     * Adds a Blob object to the end of the array.
     *
     * @param value the Blob object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addBlob(@Nullable Blob value) { return addValue(value); }

    /**
     * Adds a Date object to the end of the array.
     *
     * @param value the Date object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addDate(@Nullable Date value) { return addValue(value); }

    /**
     * Adds an Array object to the end of the array.
     *
     * @param value the Array object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addArray(@Nullable Array value) { return addValue(value); }

    /**
     * Adds a Dictionary object to the end of the array.
     *
     * @param value the Dictionary object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addDictionary(@Nullable Dictionary value) { return addValue(value); }

    /**
     * Inserts an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertValue(int index, @Nullable Object value) {
        final Object val = toJFleece(value);
        synchronized (lock) {
            if (!contents.insert(index, val)) {
                throw new IndexOutOfBoundsException("Array index " + index + " is out of range");
            }
        }
        return this;
    }

    /**
     * Inserts a String object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the String object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertString(int index, @Nullable String value) { return insertValue(index, value); }

    /**
     * Inserts a Number object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Number object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertNumber(int index, @Nullable Number value) { return insertValue(index, value); }

    /**
     * Inserts an integer value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the int value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertInt(int index, int value) { return insertValue(index, value); }

    /**
     * Inserts a long value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the long value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertLong(int index, long value) { return insertValue(index, value); }

    /**
     * Inserts a float value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the float value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertFloat(int index, float value) { return insertValue(index, value); }

    /**
     * Inserts a double value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the double value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertDouble(int index, double value) { return insertValue(index, value); }

    /**
     * Inserts a boolean value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the boolean value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertBoolean(int index, boolean value) { return insertValue(index, value); }

    /**
     * Inserts a Blob object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Blob object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertBlob(int index, @Nullable Blob value) { return insertValue(index, value); }

    /**
     * Inserts a Date object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Date object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertDate(int index, @Nullable Date value) { return insertValue(index, value); }

    /**
     * Inserts an Array object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Array object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertArray(int index, @Nullable Array value) { return insertValue(index, value); }

    /**
     * Inserts a Dictionary object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Dictionary object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertDictionary(int index, @Nullable Dictionary value) { return insertValue(index, value); }

    /**
     * Removes the object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray remove(int index) {
        synchronized (lock) {
            if (!contents.remove(index, 1)) {
                throw new IndexOutOfBoundsException("Array index " + index + " is out of range");
            }
            return this;
        }
    }

    /**
     * Gets a Array at the given index. Return null if the value is not an array.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Array object.
     */
    @Nullable
    @Override
    public MutableArray getArray(int index) { return (MutableArray) super.getArray(index); }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Nullable
    @Override
    public MutableDictionary getDictionary(int index) { return (MutableDictionary) super.getDictionary(index); }

    @NonNull
    @Override
    public String toJSON() { throw new CouchbaseLiteError("Mutable objects may not be encoded as JSON"); }
}

