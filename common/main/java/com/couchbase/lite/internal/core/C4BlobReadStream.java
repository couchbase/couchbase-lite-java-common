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


/**
 * An open stream for reading data from a blob.
 */
public final class C4BlobReadStream extends C4NativePeer {
    @NonNull
    private final C4BlobStore.NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobReadStream(@NonNull C4BlobStore.NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
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
        return withPeerOrDefault(0, peer -> impl.nRead(peer, b, offset, maxBytesToRead));
    }

    /**
     * Returns the exact length in bytes of the stream.
     */
    public long getLength() throws LiteCoreException { return withPeerOrDefault(0L, impl::nGetLength); }

    /**
     * Moves to a random location in the stream; the next c4stream_read call will read from that
     * location.
     */
    public void seek(long position) throws LiteCoreException { withPeer(peer -> impl.nSeek(peer, position)); }

    /**
     * Closes a read-stream.
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
                if (nativeImpl != null) { nativeImpl.nCloseReadStream(peer); }
            });
    }
}
