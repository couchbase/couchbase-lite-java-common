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

import com.couchbase.lite.CouchbaseLiteError;


// Honestly, sometime PMD is just dumb as a post.
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
abstract class CacheEntry {
    static class Cached extends CacheEntry {
        @NonNull
        private final MValue mValue;

        Cached(@NonNull MValue mValue) {
            super(true);
            this.mValue = mValue;
        }

        @Override
        @NonNull
        CacheEntry copy() { return new Cached(mValue); }

        @Override
        @NonNull
        MValue getMVal() { return mValue; }

        @Override
        @NonNull
        public String toString() { return "Cached{ " + mValue + "}"; }
    }

    static class Fleece extends CacheEntry {
        @NonNull
        private final FLArray flArray;
        private final long index;

        Fleece(@NonNull FLArray flArray, long index) {
            super(false);
            this.flArray = flArray;
            this.index = index;
        }

        // This is safe because the flArray is immutable.
        @Override
        @NonNull
        CacheEntry copy() { return new Fleece(flArray, index); }

        @Override
        @NonNull
        MValue getMVal() {
            final FLValue val = flArray.get(index);
            if (val == null) { throw new CouchbaseLiteError("fleece val is null (index out of bounds?)"); }
            return new MValue(val);
        }

        @Override
        @NonNull
        public String toString() { return "Fleece{ " + index + "}"; }
    }

    public final boolean isCached;

    private CacheEntry(boolean isCached) { this.isCached = isCached; }

    @NonNull
    abstract CacheEntry copy();
    @NonNull
    abstract MValue getMVal();
}


/**
 * Please see the comments in MValue
 */
public final class MArray extends MCollection {

    @Nullable
    private final FLArray flArray;
    @NonNull
    private final List<CacheEntry> cache = new ArrayList<>();

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // Construct a new empty MArray
    public MArray() {
        super(MContext.NULL, true);
        flArray = null;
    }

    // Copy constructor
    public MArray(@NonNull MArray array) {
        super(array.getContext(), true);

        assertOpen();

        // this is safe because the flArray is immutable
        flArray = array.flArray;
        if (flArray == null) { return; }

        // Copy the other guys cache.
        for (CacheEntry entry: array.cache) { cache.add(entry.copy()); }
    }

    // Slot(??) constructor
    public MArray(@NonNull MValue mVal, @Nullable MCollection parent, boolean isMutable) {
        super(mVal, parent, isMutable);

        assertOpen();

        final FLValue flVal = mVal.getFLValue();
        flArray = (flVal == null) ? null : flVal.asFLArray();
        if (flArray == null) { return; }

        // create a new, empty, cache of the right size
        for (int i = 0; i < (int) flArray.count(); i++) { cache.add(new CacheEntry.Fleece(flArray, i)); }
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    /**
     * The number of items in the array.
     *
     * @return array size
     */
    public long count() { return cache.size(); }

    @NonNull
    public MValue get(long index) {
        final int idx = validateAndGetIndex(index);
        CacheEntry value = cache.get(idx);

        // if the value is not yet cached, get it and cache it.
        if (!value.isCached) {
            value = new CacheEntry.Cached(value.getMVal());
            cache.set(idx, value);
        }

        return value.getMVal();
    }

    public void set(long index, Object value) {
        final int idx = validateAndGetIndex(index);
        mutate();
        cache.set(idx, new CacheEntry.Cached(new MValue(value)));
    }

    public void append(Object value) {
        validateAndGetIndex(0);
        mutate();
        cache.add(new CacheEntry.Cached(new MValue(value)));
    }

    public void insert(long index, Object value) {
        final int idx = validateAndGetIndex(index);
        mutate();
        cache.add(idx, new CacheEntry.Cached(new MValue(value)));
    }

    public void remove(long index) {
        final int idx = validateAndGetIndex(index);
        mutate();
        cache.remove(idx);
    }

    public void clear() {
        validateAndGetIndex(0);

        if (cache.isEmpty()) { return; }

        mutate();
        cache.clear();
    }

    /* Encodable */

    public void encodeTo(@NonNull FLEncoder enc) {
        assertOpen();
        if (!isMutated()) {
            if (flArray != null) {
                enc.writeValue(flArray);
                return;
            }

            enc.beginArray(0);
            enc.endArray();
            return;
        }

        enc.beginArray(cache.size());
        for (CacheEntry entry: cache) {
            if (!entry.isCached) { enc.writeValue(entry.getMVal()); }
            else { entry.getMVal().encodeTo(enc); }
        }
        enc.endArray();
    }

    //---------------------------------------------
    // Private
    //---------------------------------------------

    private int validateAndGetIndex(long index) {
        assertOpen();

        if (!isMutable()) { throw new CouchbaseLiteError("Cannot set items in a non-mutable MArray"); }

        if ((index < 0) && (index >= count())) {
            throw new IndexOutOfBoundsException("Array index " + index + " is out of range");
        }

        return (int) index;
    }
}
