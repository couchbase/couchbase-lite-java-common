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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4Blob;


/**
 * Blob Key
 * <p>
 * A raw SHA-1 digest used as the unique identifier of a blob.
 */
public final class C4BlobKey extends C4NativePeer {
    public interface NativeImpl {
        long nFromString(@Nullable String str) throws LiteCoreException;
        @Nullable
        String nToString(long peer);
        void nFree(long peer);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Blob();

    //-------------------------------------------------------------------------
    // Factory method
    //-------------------------------------------------------------------------

    @NonNull
    public static C4BlobKey create(@Nullable String str) throws LiteCoreException {
        return new C4BlobKey(NATIVE_IMPL, str);
    }

    @NonNull
    public static C4BlobKey create(long peer) { return new C4BlobKey(NATIVE_IMPL, peer); }

    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /**
     * Decodes a string of the form "sha1-"+base64 into a raw key.
     */
    @VisibleForTesting
    public C4BlobKey(@NonNull NativeImpl impl, @Nullable String str) throws LiteCoreException {
        this(impl, impl.nFromString(str));
    }

    private C4BlobKey(@NonNull NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    public void close() { closePeer(null); }

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    @NonNull
    @Override
    public String toString() { return withPeerOrDefault("unknown", impl::nToString); }

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
    // package methods
    //-------------------------------------------------------------------------

    // ??? Exposes the peer handle
    long getHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
