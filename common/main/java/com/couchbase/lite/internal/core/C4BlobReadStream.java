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


/**
 * An open stream for reading data from a blob.
 */
public final class C4BlobReadStream extends C4Peer {
    private static void release(@NonNull C4BlobStore.NativeImpl impl, long peer) {
        synchronized (LockManager.INSTANCE.getLock(peer)) { impl.nCloseReadStream(peer); }
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

    C4BlobReadStream(@NonNull C4BlobStore.NativeImpl impl, long peer) {
        super(peer, releasePeer -> release(impl, releasePeer));
        this.impl = impl;
        lock = LockManager.INSTANCE.getLock(peer);
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Reads from an open stream.
     *
     * @param maxBytesToRead The maximum number of bytes to read to the buffer
     */
    public int read(byte[] b, int offset, long maxBytesToRead) throws LiteCoreException {
        return withPeerOrDefault(0, peer -> {
            synchronized (lock) { return impl.nRead(peer, b, offset, maxBytesToRead); }
        });
    }

    /**
     * Moves to a random location in the stream.
     * The next c4stream_read call will read starting at that location.
     */
    public void seek(long position) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nSeek(peer, position); }
        });
    }
}
