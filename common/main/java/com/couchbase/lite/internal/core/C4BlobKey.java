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
package com.couchbase.lite.internal.core;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;


/**
 * Blob Key
 * <p>
 * A raw SHA-1 digest used as the unique identifier of a blob.
 */
public class C4BlobKey extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /**
     * Decodes a string of the form "sha1-"+base64 into a raw key.
     */
    public C4BlobKey(@Nullable String str) throws LiteCoreException { this(fromString(str)); }

    C4BlobKey(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    @NonNull
    @Override
    public String toString() {
        final String str = toString(getPeerUnchecked());
        return (str != null) ? str : "unknown!!";
    }

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

    // !!! Exposes the peer handle
    long getHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        final long peer = getPeerAndClear();
        if (verifyPeerClosed(peer, domain)) { return; }

        free(peer);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Decode a string of the form "sha1-"+base64 into a raw key
     */
    private static native long fromString(@Nullable String str) throws LiteCoreException;

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    @Nullable
    private static native String toString(long peer);

    /**
     * Release C4BlobKey
     */
    private static native void free(long peer);
}
