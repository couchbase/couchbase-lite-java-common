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
        long nGet(long dict, byte[] keyString);
    }

    static volatile NativeImpl nativeImpl = new NativeFLDict();

    @NonNull
    public static FLDict create(long peer) { return new FLDict(nativeImpl, peer); }

    private final long handle; // hold pointer to FLDict
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    FLDict(@NonNull NativeImpl impl, long handle) {
        this.impl = impl;
        this.handle = Preconditions.assertNotZero(handle, "handle");
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    public FLValue toFLValue() { return new FLValue(handle); }

    public long count() { return impl.nCount(handle); }

    @Nullable
    public FLValue get(@Nullable String key) {
        if (key == null) { return null; }

        final long hValue = impl.nGet(handle, key.getBytes(StandardCharsets.UTF_8));

        return hValue != 0L ? new FLValue(hValue) : null;
    }

    @NonNull
    public Map<String, Object> asDict() {
        final Map<String, Object> results = new HashMap<>();
        final FLDictIterator itr = new FLDictIterator();

        itr.begin(this);
        String key;
        while ((key = itr.getKeyString()) != null) {
            final FLValue val = itr.getValue();
            results.put(key, (val == null) ? null : val.asObject());
            itr.next();
        }

        return results;
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @Nullable
    <T> T withContent(@NonNull Fn.Function<Long, T> fn) { return fn.apply(handle); }
}
