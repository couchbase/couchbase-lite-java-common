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

import java.io.File;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLSliceResult;


/**
 * Blob Store API
 */
public abstract class C4BlobStore extends C4NativePeer {

    // unmanaged: the native code will free it
    private static final class UnmanagedC4BlobStore extends C4BlobStore {
        UnmanagedC4BlobStore(long peer) throws LiteCoreException { super(getBlobStore(peer)); }

        @Override
        public void close() { releasePeer(); }
    }

    // managed: Java code is responsible for freeing it
    @VisibleForTesting
    private static final class ManagedC4BlobStore extends C4BlobStore {
        ManagedC4BlobStore(@NonNull String dirPath, long flags) throws LiteCoreException {
            super(openStore(dirPath, flags));
        }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4BlobStore::freeStore); }
    }

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4BlobStore getUnmanagedBlobStore(long peer) throws LiteCoreException {
        return new C4BlobStore.UnmanagedC4BlobStore(peer);
    }

    @VisibleForTesting
    @NonNull
    public static C4BlobStore open(@NonNull String dirPath, long flags) throws LiteCoreException {
        return new ManagedC4BlobStore(
            (dirPath.endsWith(File.separator)) ? dirPath : (dirPath + File.separator),
            flags);
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobStore(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Gets the content size of a blob given its key. Returns -1 if it doesn't exist.
     * WARNING: If the blob is encrypted, the return value is a conservative estimate that may
     * be up to 16 bytes larger than the actual size.
     */
    public long getSize(@NonNull C4BlobKey blobKey) {
        return withPeerOrDefault(-1L, peer -> getSize(peer, getBlobKeyPeer(blobKey)));
    }

    /**
     * Reads the entire contents of a blob into memory. Caller is responsible for freeing it.
     */
    @NonNull
    public FLSliceResult getContents(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return FLSliceResult.getManagedSliceResult(
            withPeerOrThrow(peer -> getContents(peer, getBlobKeyPeer(blobKey))));
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
        return withPeerOrNull(peer -> getFilePath(peer, getBlobKeyPeer(blobKey)));
    }

    /**
     * Stores a blob. The associated key will be written to `outKey`.
     */
    @NonNull
    public C4BlobKey create(@NonNull byte[] contents) throws LiteCoreException {
        return new C4BlobKey(this.<Long, LiteCoreException>withPeerOrThrow(peer -> create(peer, contents)));
    }

    /**
     * Deletes a blob from the store given its key.
     */
    public void delete(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        withPeer(peer -> delete(peer, getBlobKeyPeer(blobKey)));
    }

    /**
     * Opens a blob for reading, as a random-access byte stream.
     */
    @NonNull
    public C4BlobReadStream openReadStream(@NonNull C4BlobKey blobKey) throws LiteCoreException {
        return new C4BlobReadStream(withPeerOrThrow(peer -> openReadStream(peer, getBlobKeyPeer(blobKey))));
    }

    /**
     * Opens a write stream for creating a new blob. You should then call c4stream_write to
     * write the data, ending with c4stream_install to compute the blob's key and add it to
     * the store, and then c4stream_closeWriter.
     */
    @NonNull
    public C4BlobWriteStream openWriteStream() throws LiteCoreException {
        return new C4BlobWriteStream(withPeerOrThrow(C4BlobStore::openWriteStream));
    }

    @Override
    public abstract void close();

    @VisibleForTesting
    public void delete() throws LiteCoreException { releasePeer(null, C4BlobStore::deleteStore); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    // !!! Really shouldn't be grabbing other objects by their handles...
    private long getBlobKeyPeer(@NonNull C4BlobKey blobKey) { return blobKey.getHandle(); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long getBlobStore(long db) throws LiteCoreException;

    private static native long getSize(long peer, long blobKey);

    private static native long getContents(long peer, long blobKey) throws LiteCoreException;

    @NonNull
    private static native String getFilePath(long peer, long blobKey) throws LiteCoreException;

    private static native long create(long peer, byte[] contents) throws LiteCoreException;

    private static native void delete(long peer, long blobKey) throws LiteCoreException;

    private static native long openReadStream(long peer, long blobKey) throws LiteCoreException;

    private static native long openWriteStream(long peer) throws LiteCoreException;

    @VisibleForTesting
    private static native long openStore(String dirPath, long flags) throws LiteCoreException;

    @VisibleForTesting
    private static native void deleteStore(long peer) throws LiteCoreException;

    @VisibleForTesting
    private static native void freeStore(long peer);
}
