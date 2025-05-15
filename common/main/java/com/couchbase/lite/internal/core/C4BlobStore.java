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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.impl.NativeC4Blob;


/**
 * Blob Store API
 */
public abstract class C4BlobStore extends C4Peer {
    public interface NativeImpl {
        @GuardedBy("dbLock")
        long nGetBlobStore(long db) throws LiteCoreException;
        long nGetSize(long peer, long key);
        @Nullable
        byte[] nGetContents(long peer, long key) throws LiteCoreException;
        @Nullable
        String nGetFilePath(long peer, long key) throws LiteCoreException;
        long nCreate(long peer, byte[] data) throws LiteCoreException;
        void nDelete(long peer, long key) throws LiteCoreException;
        long nOpenReadStream(long peer, long key) throws LiteCoreException;
        long nOpenWriteStream(long peer) throws LiteCoreException;

        // BlobReadStream
        @GuardedBy("streamLock")
        int nRead(long peer, byte[] data, int offset, long len) throws LiteCoreException;
        @GuardedBy("streamLock")
        void nSeek(long peer, long pos) throws LiteCoreException;
        @GuardedBy("streamLock")
        void nCloseReadStream(long peer);

        // BlobReadStream
        @GuardedBy("streamLock")
        void nWrite(long peer, byte[] data, int len) throws LiteCoreException;
        @GuardedBy("streamLock")
        long nComputeBlobKey(long peer) throws LiteCoreException;
        @GuardedBy("streamLock")
        void nInstall(long peer) throws LiteCoreException;
        @GuardedBy("streamLock")
        void nCloseWriteStream(long peer);
    }

    // All of the blob stores used in production code are
    // managed by LiteCore: it will free them
    // Tests create blob stores that are managed by Java.
    // See C4TestUtils.ManagedC4BlobStore
    private static final class UnmanagedC4BlobStore extends C4BlobStore {
        UnmanagedC4BlobStore(@NonNull NativeImpl impl, long peer) { super(impl, peer, null); }
    }

    @VisibleForTesting
    @NonNull
    static final NativeImpl NATIVE_IMPL = new NativeC4Blob();

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4BlobStore create(long dbPeer, @NonNull Object dbLock) throws LiteCoreException {
        synchronized (dbLock) { return new UnmanagedC4BlobStore(NATIVE_IMPL, NATIVE_IMPL.nGetBlobStore(dbPeer)); }
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected C4BlobStore(@NonNull NativeImpl impl, long peer, @Nullable PeerCleaner cleaner) {
        super(peer, cleaner);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Gets the content size of a blob given its key. Returns -1 if it doesn't exist.
     * WARNING: If the blob is encrypted, the return value is a conservative estimate that may
     * be up to 16 bytes larger than the actual size.
     */
    public long getSize(@NonNull C4BlobKey blobKey) {
        return withPeerOrDefault(-1L, peer -> blobKey.withPeerOrThrow(blobPeer -> impl.nGetSize(peer, blobPeer)));
    }

    /**
     * Reads the entire contents of a blob into memory.
     * NOTE: the FLSliceResult returned by this method must be released by the caller
     */
    @Nullable
    public byte[] getContents(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return withPeerOrNull(peer -> blobKey.withPeerOrNull(blobPeer -> impl.nGetContents(peer, blobPeer)));
    }

    /**
     * Returns the path of the file that stores the blob, if possible. This call may fail with
     * error kC4ErrorWrongFormat if the blob is encrypted (in which case the file would be
     * unreadable by the caller) or with kC4ErrorUnsupported if for some implementation reason
     * the blob isn't stored as a standalone file.
     * Thus, the caller MUST use this function only as an optimization, and fall back to reading
     * he contents via the API if it fails.
     * Also, it goes without saying that the caller MUST not modify the file!
     */
    @Nullable
    public String getFilePath(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return withPeerOrNull(peer -> blobKey.withPeerOrNull(blobPeer -> impl.nGetFilePath(peer, blobPeer)));
    }

    /**
     * Stores a blob. The associated key will be written to `outKey`.
     */
    @NonNull
    public C4BlobKey create(@NonNull byte[] contents) throws LiteCoreException {
        return withPeerOrThrow(peer -> C4BlobKey.create(impl.nCreate(peer, contents)));
    }

    /**
     * Opens a blob for reading, as a random-access byte stream.
     */
    @NonNull
    public C4BlobReadStream openReadStream(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return withPeerOrThrow(peer ->
            blobKey.withPeerOrThrow(blobPeer ->
                new C4BlobReadStream(impl, impl.nOpenReadStream(peer, blobPeer))));
    }

    /**
     * Opens a write stream for creating a new blob. You should then call c4stream_write to
     * write the data, ending with c4stream_install to compute the blob's key and add it to
     * the store, and then c4stream_closeWriter.
     */
    @NonNull
    public C4BlobWriteStream openWriteStream() throws LiteCoreException {
        return new C4BlobWriteStream(impl, withPeerOrThrow(impl::nOpenWriteStream));
    }

    /**
     * Deletes a blob from the store given its key.
     */
    @VisibleForTesting
    void delete(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> blobKey.voidWithPeerOrThrow(blobPeer -> impl.nDelete(peer, blobPeer)));
    }
}
