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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;


public final class FLDictIterator extends C4NativePeer {
    private final FLDict.NativeImpl impl;

    // Hold a reference to the object over which we iterate.
    @SuppressWarnings({"PMD.SingularField", "PMD.UnusedPrivateField", "FieldCanBeLocal", "unused"})
    private final FLDict dict;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    FLDictIterator(@NonNull FLDict.NativeImpl impl, @NonNull FLDict dict) {
        super(dict.withContent(impl::nInit));
        this.impl = impl;
        this.dict = dict;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public long getCount() { return withPeerOrThrow(impl::nGetCount); }

    /**
     * Advances the iterator to the next key/value.
     * NOTE: It is illegal to call this when the iterator is already at the end.
     * In particular, calling this when the dict is empty is always illegal
     */
    public void next() { withPeerOrThrow(impl::nNext); }

    @Nullable
    public String getKey() { return withPeerOrNull(impl::nGetKey); }

    @NonNull
    public FLValue getValue() {
        return withPeerOrThrow(p -> FLValue.getFLValue(impl.nGetValue(p)));
    }

    @Override
    public void close() { closePeer(null); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                final FLDict.NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
