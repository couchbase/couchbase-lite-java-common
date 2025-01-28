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
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.couchbase.lite.internal.fleece.MArray;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.JSONUtils;


/**
 * Array provides readonly access to array data.
 */
public class Array extends AbstractJFleeceCollection<MArray> implements ArrayInterface, Iterable<Object> {
    //---------------------------------------------
    // Types
    //---------------------------------------------
    private class ArrayIterator implements Iterator<Object> {
        private final long mutations;
        private int index;

        ArrayIterator() { this.mutations = contents.getLocalMutationCount(); }

        @Override
        public boolean hasNext() { return index < count(); }

        @Nullable
        @Override
        public Object next() {
            if (contents.getLocalMutationCount() != mutations) {
                throw new ConcurrentModificationException("Array modified during iteration");
            }
            return getValue(index++);
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // Construct a new array with the passed content
    protected Array(@NonNull MArray array) { super(array); }

    // Construct a new empty Array
    protected Array() { this(new MArray()); }

    // Slot(??) constructor
    Array(@NonNull MValue val, @Nullable MCollection parent) { this(new MArray(val, parent)); }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return a mutable copy of the array
     *
     * @return the MutableArray instance
     */
    @Override
    @NonNull
    public MutableArray toMutable() {
        synchronized (lock) { return new MutableArray(this); }
    }

    /**
     * Gets value at the given index as a boolean.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(int index) { return asBoolean(getJFleeceAt(index)); }

    /**
     * Gets value at the given index as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the int value.
     */
    @Override
    public int getInt(int index) { return toInteger(getMValueAt(index), contents); }

    /**
     * Gets value at the given index as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the long value.
     */
    @Override
    public long getLong(int index) { return toLong(getMValueAt(index), contents); }

    /**
     * Gets value at the given index as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the float value.
     */
    @Override
    public float getFloat(int index) { return toFloat(getMValueAt(index), contents); }

    /**
     * Gets value at the given index as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the double value.
     */
    @Override
    public double getDouble(int index) { return toDouble(getMValueAt(index), contents); }

    /**
     * Gets value at the given index as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Number or nil.
     */
    @Nullable
    @Override
    public Number getNumber(int index) { return asNumber(getJFleeceAt(index)); }

    /**
     * Gets value at the given index as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the String or null.
     */
    @Nullable
    @Override
    public String getString(int index) { return asString(getJFleeceAt(index)); }

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
     * Gets value at the given index as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Blob value or null.
     */
    @Nullable
    @Override
    public Blob getBlob(int index) { return asBlob(getJFleeceAt(index)); }

    /**
     * Gets value at the given index as an Array.
     * Returns null if the value doesn't exist, or its value is not an Array.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Array object.
     */
    @Nullable
    @Override
    public Array getArray(int index) { return asArray(getJFleeceAt(index)); }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(int index) { return asDictionary(getJFleeceAt(index)); }

    /**
     * Gets value at the given index as an object. The object types are Blob,
     * Array, Dictionary, Number, or String based on the underlying
     * data type; or null if the value is nil.
     *
     * @param index the index. This value must be 0 &lt;= index &lt; count().
     * @return the Object or null.
     */
    @Nullable
    @Override
    public Object getValue(int index) { return getJFleeceAt(index); }

    /**
     * Gets value at the given index as an object. The object types are Blob,
     * Array, Dictionary, Number, or String based on the underlying
     * data type; or null if the value is nil.
     *
     * @param index the index. This value must be 0 &lt;= index &lt; count().
     * @param klass the class of the object.
     * @return the value at the index, or null if the value doesn't exist or is not of the given class.
     */
    @Nullable
    public <T> T getValue(@NonNull Class<T> klass, int index) { return asValue(klass, getValue(index)); }

    /**
     * Gets content of the current object as an List. The values contained in the returned
     * List object are all JSON based values.
     *
     * @return the List object representing the content of the current object in the JSON format.
     */
    @NonNull
    @Override
    public List<Object> toList() {
        synchronized (lock) {
            final int n = count();
            final List<Object> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Object obj = getJFleeceAt(i);
                if (obj instanceof AbstractJFleeceCollection<?>) {
                    obj = toJFleeceCollection((AbstractJFleeceCollection<?>) obj);
                }
                list.add(obj);
            }
            return list;
        }
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    /**
     * An iterator over elements of this array.
     * A call to the <code>next()</code> method of the returned iterator
     * will throw a ConcurrentModificationException, if the MutableArray is
     * modified while it is in use.
     *
     * @return an iterator over the array's elements.
     */
    @NonNull
    @Override
    public Iterator<Object> iterator() { return new ArrayIterator(); }

    //-------------------------------------------------------------------------
    // Object overrides
    //-------------------------------------------------------------------------

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Array)) { return false; }

        final Array a = (Array) o;
        final Iterator<Object> itr1 = iterator();
        final Iterator<Object> itr2 = a.iterator();
        while (itr1.hasNext() && itr2.hasNext()) {
            final Object o1 = itr1.next();
            final Object o2 = itr2.next();
            if (!(Objects.equals(o1, o2))) { return false; }
        }
        return !(itr1.hasNext() || itr2.hasNext());
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (Object o: this) { h = 31 * h + (o == null ? 0 : o.hashCode()); }
        return h;
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Array{(").append(contents.getStateString()).append(')');

        final int n = count();
        for (int i = 0; i < n; i++) {
            if (i > 0) { buf.append(','); }
            buf.append(getValue(i));
        }

        return buf.append('}').toString();
    }

    //-------------------------------------------------------------------------
    // Private
    //-------------------------------------------------------------------------

    @Nullable
    private Object getJFleeceAt(int index) { return getMValueAt(index).toJFleece(contents); }

    @NonNull
    private MValue getMValueAt(int index) {
        final MValue value;
        synchronized (lock) { value = contents.get(index); }
        if (value.isEmpty()) {
            throw new ArrayIndexOutOfBoundsException("index " + index + " is not 0 <= index < " + count());
        }
        return value;
    }
}
