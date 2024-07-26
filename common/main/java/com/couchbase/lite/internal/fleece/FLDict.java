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


public final class FLDict extends FLSlice<FLDict.NativeImpl> {
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

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static <E extends Exception> FLDict create(@NonNull Fn.LongProviderThrows<E> fn) throws E {
        return create(fn.get());
    }

    @Nullable
    public static <E extends Exception> FLDict createOrNull(@NonNull Fn.LongProviderThrows<E> fn) throws E {
        final long peer = fn.get();
        return (peer == 0) ? null : create(peer);
    }

    // Don't use this outside this class unless you really must (LiteCore passed you the ref or something)
    // Our peer is nobody else's business.
    @NonNull
    public static FLDict create(long peer) { return new FLDict(NATIVE_IMPL, peer); }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private FLDict(@NonNull NativeImpl impl, long peer) { super(impl, peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    int getType() { return FLSlice.ValueType.DICT; }

    @Override
    public long count() { return impl.nCount(peer); }

    @Nullable
    public FLValue get(@Nullable String key) {
        return (key == null) ? null : getFLValue(peer -> impl.nGet(peer, key.getBytes(StandardCharsets.UTF_8)));
    }

    @NonNull
    public Map<String, Object> asDict() {
        final Map<String, Object> results = new HashMap<>();
        try (FLDictIterator itr = iterator()) {
            String key;
            while ((key = itr.getKey()) != null) {
                final FLValue val = itr.getValue();
                results.put(key, val.asJava());
                itr.next();
            }
        }
        return results;
    }

    @NonNull
    public FLDictIterator iterator() { return new FLDictIterator(impl, this); }
}
