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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.impl.NativeFLArray;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


public final class FLArray {
    public interface NativeImpl {
        long nCount(long array);
        long nGet(long array, long index);

        // iterator
        long nInit(long array);
        long nGetValue(long peer);
        long nGetValueAt(long peer, int offset);
        boolean nNext(long peer);
        void nFree(long peer);
    }

    private static final NativeImpl NATIVE_IMPL = new NativeFLArray();

    //-------------------------------------------------------------------------
    // Factory Method
    //-------------------------------------------------------------------------

    @NonNull
    public static FLArray create(long peer) { return new FLArray(NATIVE_IMPL, peer); }

    @NonNull
    public static FLArrayIterator unmanagedIterator(long peer) {
        return new FLArrayIterator.UnmanagedFLArrayIterator(NATIVE_IMPL, peer);
    }

    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final long peer; // pointer to FLArray
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // constructor
    //-------------------------------------------------------------------------

    private FLArray(@NonNull NativeImpl impl, long peer) {
        Preconditions.assertNotZero(peer, "peer");
        this.peer = peer;
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Returns the number of items in an array; 0 if peer is null.
     *
     * @return the number of items in an array; 0 if peer is null.
     */
    public long count() { return impl.nCount(peer); }

    /**
     * Returns an value at an array index, or null if the index is out of range.
     *
     * @param index index for value
     * @return the FLValue at index
     */
    @NonNull
    public FLValue get(long index) { return FLValue.getFLValue(impl.nGet(peer, index)); }

    @NonNull
    public List<Object> asArray() { return asTypedArray(Object.class); }

    @NonNull
    public <T> List<T> asTypedArray(@NonNull Class<T> klass) {
        final List<T> results = new ArrayList<>();
        try (FLArrayIterator itr = iterator()) {
            FLValue value;
            while ((value = itr.getValue()) != null) {
                final Object val = value.asObject();
                try { results.add(klass.cast(val)); }
                catch (ClassCastException e) {
                    Log.w(
                        LogDomain.DATABASE,
                        "Expecting type %s but got %s(%s). Ignored.",
                        klass.getSimpleName(),
                        (val == null) ? "-" : val.getClass().getSimpleName(),
                        val);
                }
                itr.next();
            }
        }

        return results;
    }

    @NonNull
    public FLArrayIterator iterator() { return new FLArrayIterator.ManagedFLArrayIterator(impl, this); }

    //-------------------------------------------------------------------------
    // package level access
    //-------------------------------------------------------------------------

    @Nullable
    <T> T withContent(@NonNull Fn.Function<Long, T> fn) { return fn.apply(peer); }
}
