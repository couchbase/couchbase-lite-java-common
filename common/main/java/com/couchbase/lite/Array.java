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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.fleece.FLEncodable;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.MArray;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MContext;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.DateUtils;


/**
 * Array provides readonly access to array data.
 */
public class Array implements ArrayInterface, FLEncodable, Iterable<Object> {
    //---------------------------------------------
    // Types
    //---------------------------------------------
    private class ArrayIterator implements Iterator<Object> {
        private final int count;
        private int index;

        ArrayIterator(int count) { this.count = count; }

        @Override
        public boolean hasNext() { return index < count; }

        @Override
        public Object next() { return getValue(index++); }
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    static void throwRangeException(int index) {
        throw new IndexOutOfBoundsException("Array index " + index + " is out of range");
    }

    //---------------------------------------------
    // Instance members
    //---------------------------------------------
    @NonNull
    protected final Object lock;
    @NonNull
    protected final MArray internalArray;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Array() {
        internalArray = new MArray();
        lock = getDbLock();
    }

    Array(@NonNull MValue mv, @Nullable MCollection parent) {
        internalArray = new MArray();
        internalArray.initInSlot(mv, parent);
        lock = getDbLock();
    }

    // to crete mutable copy
    Array(@NonNull MArray mArray, boolean isMutable) {
        internalArray = new MArray();
        internalArray.initAsCopyOf(mArray, isMutable);
        lock = getDbLock();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets a number of the items in the array.
     *
     * @return the size of the array
     */
    @Override
    public final int count() {
        synchronized (lock) { return (int) internalArray.count(); }
    }

    /**
     * Gets value at the given index as an object. The object types are Blob,
     * Array, Dictionary, Number, or String based on the underlying
     * data type; or nil if the value is nil.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Object or null.
     */
    @Nullable
    @Override
    public Object getValue(int index) {
        synchronized (lock) { return getMValue(internalArray, index).asNative(internalArray); }
    }

    /**
     * Gets value at the given index as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the String or null.
     */
    @Nullable
    @Override
    public String getString(int index) {
        synchronized (lock) {
            final Object obj = getMValue(internalArray, index).asNative(internalArray);
            return obj instanceof String ? (String) obj : null;
        }
    }

    /**
     * Gets value at the given index as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Number or nil.
     */
    @Nullable
    @Override
    public Number getNumber(int index) {
        synchronized (lock) { return CBLConverter.asNumber(getMValue(internalArray, index).asNative(internalArray)); }
    }

    /**
     * Gets value at the given index as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the int value.
     */
    @Override
    public int getInt(int index) {
        synchronized (lock) { return CBLConverter.asInteger(getMValue(internalArray, index), internalArray); }
    }

    /**
     * Gets value at the given index as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the long value.
     */
    @Override
    public long getLong(int index) {
        synchronized (lock) { return CBLConverter.asLong(getMValue(internalArray, index), internalArray); }
    }

    /**
     * Gets value at the given index as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the float value.
     */
    @Override
    public float getFloat(int index) {
        synchronized (lock) { return CBLConverter.asFloat(getMValue(internalArray, index), internalArray); }
    }

    /**
     * Gets value at the given index as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the double value.
     */
    @Override
    public double getDouble(int index) {
        synchronized (lock) { return CBLConverter.asDouble(getMValue(internalArray, index), internalArray); }
    }

    /**
     * Gets value at the given index as a boolean.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(int index) {
        synchronized (lock) { return CBLConverter.asBoolean(getMValue(internalArray, index).asNative(internalArray)); }
    }

    /**
     * Gets value at the given index as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Blob value or null.
     */
    @Nullable
    @Override
    public Blob getBlob(int index) {
        synchronized (lock) { return (Blob) getMValue(internalArray, index).asNative(internalArray); }
    }

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
    public Date getDate(int index) { return DateUtils.fromJson(getString(index)); }

    /**
     * Gets a Array at the given index. Return null if the value is not an array.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Array object.
     */
    @Nullable
    @Override
    public Array getArray(int index) {
        synchronized (lock) {
            final Object obj = getMValue(internalArray, index).asNative(internalArray);
            return (obj instanceof Array) ? (Array) obj : null;
        }
    }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Nullable
    @Override
    public Dictionary getDictionary(int index) {
        synchronized (lock) {
            final Object obj = getMValue(internalArray, index).asNative(internalArray);
            return (obj instanceof Dictionary) ? (Dictionary) obj : null;
        }
    }

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
            final int count = (int) internalArray.count();
            final List<Object> result = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                result.add(Fleece.toObject(getMValue(internalArray, index).asNative(internalArray)));
            }
            return result;
        }
    }

    @NonNull
    @Override
    public String toJSON() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------
    @NonNull
    @Override
    public Iterator<Object> iterator() { return new ArrayIterator(count()); }

    //-------------------------------------------------------------------------
    // Implementation of FLEncodable
    //-------------------------------------------------------------------------

    /**
     * encodeTo(FlEncoder) is internal method. Please don't use this method.
     */
    @Override
    public void encodeTo(@NonNull FLEncoder enc) { internalArray.encodeTo(enc); }

    //-------------------------------------------------------------------------
    // Object overrides
    //-------------------------------------------------------------------------
    @Override
    public boolean equals(Object o) {
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

    //-------------------------------------------------------------------------
    // Other public
    //-------------------------------------------------------------------------

    /**
     * Return a mutable copy of the array
     *
     * @return the MutableArray instance
     */
    @NonNull
    public MutableArray toMutable() {
        synchronized (lock) { return new MutableArray(internalArray, true); }
    }

    //-------------------------------------------------------------------------
    // Package protected
    //-------------------------------------------------------------------------
    @NonNull
    MCollection toMCollection() { return internalArray; }

    //-------------------------------------------------------------------------
    // Private
    //-------------------------------------------------------------------------

    @NonNull
    private Object getDbLock() {
        final MContext context = internalArray.getContext();
        if (context instanceof DbContext) {
            final Database db = ((DbContext) context).getDatabase();
            if (db != null) { return db.getLock(); }
        }
        return new Object();
    }

    @NonNull
    private MValue getMValue(@NonNull MArray array, int index) {
        final MValue value = array.get(index);
        if (value.isEmpty()) { throwRangeException(index); }
        return value;
    }
}
