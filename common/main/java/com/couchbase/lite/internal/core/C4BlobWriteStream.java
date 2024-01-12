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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * An open stream for writing data to a blob.
 */
public final class C4BlobWriteStream extends C4NativePeer {
    @NonNull
    private final C4BlobStore.NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobWriteStream(@NonNull C4BlobStore.NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Writes an entire byte array to the stream.
     *
     * @param bytes array of bytes to be written in its entirety
     * @throws LiteCoreException on write failure
     */
    public void write(@NonNull byte[] bytes) throws LiteCoreException {
        Preconditions.assertNotNull(bytes, "bytes");
        write(bytes, bytes.length);
    }

    /**
     * Writes the len bytes from the passed array, to the stream.
     *
     * @param bytes array of bytes to be written in its entirety.
     * @param len   the number of bytes to write
     * @throws LiteCoreException on write failure
     */
    public void write(@NonNull byte[] bytes, int len) throws LiteCoreException {
        Preconditions.assertNotNull(bytes, "bytes");
        if (len <= 0) { return; }
        withPeer(peer -> impl.nWrite(peer, bytes, len));
    }

    /**
     * Computes the blob-key (digest) of the data written to the stream. This should only be
     * called after writing the entire data. No more data can be written after this call.
     */
    @NonNull
    public C4BlobKey computeBlobKey() throws LiteCoreException {
        return C4BlobKey.create(withPeerOrThrow(impl::nComputeBlobKey));
    }

    /**
     * Adds the data written to the stream as a finished blob to the store.
     * If you skip this call, the blob will not be added to the store. (You might do this if you
     * were unable to receive all of the data from the network, or if you've called
     * c4stream_computeBlobKey and found that the data does not match the expected digest/key.)
     */
    public void install() throws LiteCoreException { withPeer(impl::nInstall); }

    /**
     * Closes a blob write-stream. If c4stream_install was not already called, the temporary file
     * will be deleted without adding the blob to the store.
     */
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
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                final C4BlobStore.NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nCloseWriteStream(peer); }
            });
    }
}
