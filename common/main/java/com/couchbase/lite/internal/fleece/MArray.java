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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;


/**
 * Please see the comments in MValue
 */
public final class MArray extends MCollection {
    @NonNull
    private final List<MValue> values = new ArrayList<>();
    @Nullable
    private final FLArray baseArray;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // Array constructor
    public MArray() { baseArray = null; }

    // Copy constructor
    public MArray(@NonNull MArray array, boolean isMutable) {
        super(array, isMutable);

        values.addAll(array.values);
        baseArray = array.baseArray;

        resize();
    }

    // Slot(??) constructor
    public MArray(@NonNull MValue val, @Nullable MCollection parent) {
        super(val, parent, (parent != null) && parent.hasMutableChildren());

        final FLValue value = val.getValue();
        if (value == null) {
            baseArray = null;
            return;
        }

        baseArray = value.asFLArray();

        resize();
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    /**
     * The number of items in the array.
     *
     * @return array size
     */
    public long count() { return values.size(); }

    /**
     * Returns a reference to the MValue of the item at the given index.
     * If the index is out of range, returns an empty MValue.
     */
    @NonNull
    public MValue get(long index) {
        assertOpen();

        if ((index < 0) || (index >= values.size())) { return MValue.EMPTY; }

        MValue value = values.get((int) index);
        if (value.isEmpty() && (baseArray != null)) {
            value = new MValue(baseArray.get(index));
            values.set((int) index, value);
        }

        return value;
    }

    public boolean append(Object value) {
        if (!isMutable()) { throw new IllegalStateException("Cannot append items to a non-mutable MArray"); }
        return insert(count(), value);
    }

    public boolean set(long index, Object value) {
        if (!isMutable()) { throw new IllegalStateException("Cannot set items in a non-mutable MArray"); }
        assertOpen();

        if ((index < 0) || (index >= count())) { return false; }

        mutate(true);
        values.set((int) index, new MValue(value));

        return true;
    }

    public boolean insert(long index, Object value) {
        if (!isMutable()) { throw new IllegalStateException("Cannot insert items in a non-mutable MArray"); }
        assertOpen();

        if ((index < 0) || (index > count())) { return false; }

        if (index < count()) { populateValues(); }

        mutate(true);
        values.add((int) index, new MValue(value));

        return true;
    }

    public boolean remove(long start, long num) {
        if (!isMutable()) { throw new IllegalStateException("Cannot remove items in a non-mutable MArray"); }
        assertOpen();

        final long end = start + num;
        if (end <= start) { return end == start; }

        final long count = count();
        if (end > count) { return false; }

        if (end < count) { populateValues(); }

        mutate(true);
        values.subList((int) start, (int) end).clear();

        return true;
    }

    public void clear() {
        if (!isMutable()) { throw new IllegalStateException("Cannot clear items in a non-mutable MArray"); }
        assertOpen();

        if (values.isEmpty()) { return; }

        mutate(true);
        values.clear();
    }

    /* Encodable */

    public void encodeTo(@NonNull FLEncoder enc) {
        assertOpen();
        if (!isMutated()) {
            if (baseArray != null) {
                enc.writeValue(baseArray);
                return;
            }

            enc.beginArray(0);
            enc.endArray();
            return;
        }

        long i = 0;
        enc.beginArray(values.size());
        for (MValue value: values) {
            if (!value.isEmpty()) { value.encodeTo(enc); }
            else if (baseArray != null) { enc.writeValue(baseArray.get(i)); }
            i++;
        }
        enc.endArray();
    }

    private void resize() {
        assertOpen();
        if (baseArray == null) { return; }
        final long newSize = baseArray.count();
        final int count = values.size();
        if (newSize < count) { values.subList((int) newSize, count).clear(); }
        else if (newSize > count) {
            for (int i = 0; i < newSize - count; i++) { values.add(MValue.EMPTY); }
        }
    }

    //---------------------------------------------
    // Private
    //---------------------------------------------

    private void populateValues() {
        if (baseArray == null) { return; }

        final int size = values.size();
        for (int i = 0; i < size; i++) {
            if (values.get(i).isEmpty()) { values.set(i, new MValue(baseArray.get(i))); }
        }
    }
}
