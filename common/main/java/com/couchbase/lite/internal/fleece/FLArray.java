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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


public class FLArray {
    private final long peer; // pointer to FLArray

    //-------------------------------------------------------------------------
    // constructor
    //-------------------------------------------------------------------------

    public FLArray(long peer) {
        Preconditions.assertNotZero(peer, "peer");
        this.peer = peer;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Returns the number of items in an array; 0 if peer is null.
     *
     * @return the number of items in an array; 0 if peer is null.
     */
    public long count() { return count(peer); }

    /**
     * Returns an value at an array index, or null if the index is out of range.
     *
     * @param index index for value
     * @return the FLValue at index
     */
    @NonNull
    public FLValue get(long index) { return new FLValue(get(peer, index)); }

    @NonNull
    public List<Object> asArray() { return asTypedArray(); }

    @NonNull
    @SuppressWarnings("unchecked")
    public <T> List<T> asTypedArray() {
        final List<T> results = new ArrayList<>();
        final FLArrayIterator itr = FLArrayIterator.getManagedArrayIterator();

        itr.begin(this);
        FLValue value;
        while ((value = itr.getValue()) != null) {
            results.add((T) value.asObject());
            if (!itr.next()) { break; }
        }

        return results;
    }

    //-------------------------------------------------------------------------
    // package level access
    //-------------------------------------------------------------------------

    @Nullable
    <T> T withContent(@NonNull Fn.Function<Long, T> fn) { return fn.apply(peer); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long count(long array);

    private static native long get(long array, long index);
}
