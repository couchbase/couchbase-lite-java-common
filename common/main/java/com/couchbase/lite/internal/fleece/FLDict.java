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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.fleece.impl.NativeFLDict;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


public class FLDict {
    public interface NativeImpl {
        long nCount(long dict);
        long nGet(long dict, @NonNull byte[] keyString);

        // Iterator
        long nInit(long dict);
        long nGetCount(long itr);
        boolean nNext(long itr);
        @Nullable
        String nGetKey(long itr);
        long nGetValue(long itr);
        void nFree(long itr);
    }

    private static final NativeImpl NATIVE_IMPL = new NativeFLDict();

    @NonNull
    public static FLDict create(long peer) { return new FLDict(NATIVE_IMPL, peer); }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final NativeImpl impl;
    private final long peer; // hold pointer to FLDict

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    FLDict(@NonNull NativeImpl impl, long peer) {
        this.impl = impl;
        this.peer = Preconditions.assertNotZero(peer, "peer");
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    public FLValue toFLValue() { return FLValue.getFLValue(peer); }

    public long count() { return impl.nCount(peer); }

    @Nullable
    public FLValue get(@Nullable String key) {
        if (key == null) { return null; }

        final long hValue = impl.nGet(peer, key.getBytes(StandardCharsets.UTF_8));

        return hValue == 0L ? null : FLValue.getFLValue(hValue);
    }

    @NonNull
    public <K, V> Map<K, V> asMap(@NonNull Class<K> keyClass, @NonNull Class<V> valueClass) {
        final Map<K, V> results = new HashMap<>();
        try (FLDictIterator itr = iterator()) {
            String key;
            while ((key = itr.getKey()) != null) {
                results.put(keyClass.cast(key), valueClass.cast(itr.getValue().toJava()));
                itr.next();
            }
        }
        return results;
    }

    @NonNull
    public FLDictIterator iterator() { return new FLDictIterator(impl, this); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @Nullable
    <T> T withContent(@NonNull Fn.NullableFunction<Long, T> fn) { return fn.apply(peer); }
}
