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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.peers.LockManager;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * An open stream for writing data to a blob.
 */
public final class C4BlobWriteStream extends C4Peer {
    private static void release(@NonNull C4BlobStore.NativeImpl impl, long peer) {
        synchronized (LockManager.INSTANCE.getLock(peer)) { impl.nCloseWriteStream(peer); }
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final C4BlobStore.NativeImpl impl;

    // Seize this lock *after* the peer lock
    @NonNull
    private final Object lock;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobWriteStream(@NonNull C4BlobStore.NativeImpl impl, long peer) {
        super(peer, releasePeer -> release(impl, releasePeer));
        this.impl = impl;
        lock = LockManager.INSTANCE.getLock(peer);
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
    public void write(@NonNull byte[] bytes) throws LiteCoreException { write(bytes, bytes.length); }

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
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nWrite(peer, bytes, len); }
        });
    }

    /**
     * Computes the blob-key (digest) of the data written to the stream. This should only be
     * called after writing the entire data. No more data can be written after this call.
     */
    @NonNull
    public C4BlobKey computeBlobKey() throws LiteCoreException {
        final long key = withPeerOrThrow(impl::nComputeBlobKey);
        synchronized (lock) { return C4BlobKey.create(key); }
    }

    /**
     * Adds the data written to the stream as a finished blob to the store.
     * If you skip this call, the blob will not be added to the store. (You might do this if you
     * were unable to receive all of the data from the network, or if you've called
     * c4stream_computeBlobKey and found that the data does not match the expected digest/key.)
     */
    public void install() throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nInstall(peer); }
        });
    }
}
