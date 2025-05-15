//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4BlobKey;
import com.couchbase.lite.internal.core.C4BlobStore;


@SuppressWarnings("PMD.TooManyMethods")
public final class NativeC4Blob implements C4BlobKey.NativeImpl, C4BlobStore.NativeImpl {

    //// BlobKey

    public long nFromString(@Nullable String str) throws LiteCoreException { return fromString(str); }

    @Nullable
    public String nToString(long peer) { return toString(peer); }

    public void nFree(long peer) { free(peer); }

    //// BlobStore

    @GuardedBy("dbLock")
    public long nGetBlobStore(long db) throws LiteCoreException { return getBlobStore(db); }

    public long nGetSize(long peer, long key) { return getSize(peer, key); }

    @Nullable
    public byte[] nGetContents(long peer, long key) throws LiteCoreException { return getContents(peer, key); }

    @Nullable
    public String nGetFilePath(long peer, long key) throws LiteCoreException { return getFilePath(peer, key); }

    public long nCreate(long peer, byte[] data) throws LiteCoreException { return create(peer, data); }

    public void nDelete(long peer, long key) throws LiteCoreException { delete(peer, key); }

    public long nOpenReadStream(long peer, long key) throws LiteCoreException { return openReadStream(peer, key); }

    public long nOpenWriteStream(long peer) throws LiteCoreException { return openWriteStream(peer); }

    //// BlobReadStream

    @GuardedBy("readStreamLock")
    public int nRead(long peer, byte[] data, int offset, long len) throws LiteCoreException {
        return read(peer, data, offset, len);
    }

    @GuardedBy("readStreamLock")
    public void nSeek(long peer, long pos) throws LiteCoreException { seek(peer, pos); }

    public void nCloseReadStream(long peer) { closeReadStream(peer); }

    //// BlobWriteStream

    @GuardedBy("writeStreamLock")
    public void nWrite(long peer, byte[] data, int len) throws LiteCoreException { write(peer, data, len); }

    @GuardedBy("writeStreamLock")
    public long nComputeBlobKey(long peer) throws LiteCoreException { return computeBlobKey(peer); }

    @GuardedBy("writeStreamLock")
    public void nInstall(long peer) throws LiteCoreException { install(peer); }

    public void nCloseWriteStream(long peer) { closeWriteStream(peer); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    // Thread safety verified as of 2025/5/15
    //-------------------------------------------------------------------------

    // BlobKey
    private static native long fromString(@Nullable String str) throws LiteCoreException;

    @Nullable
    private static native String toString(long peer);

    private static native void free(long peer);

    // BlobStore
    @GuardedBy("dbLock")
    private static native long getBlobStore(long db) throws LiteCoreException;

    private static native long getSize(long peer, long blobKey);

    @Nullable
    private static native byte[] getContents(long peer, long blobKey) throws LiteCoreException;

    @Nullable
    private static native String getFilePath(long peer, long blobKey) throws LiteCoreException;

    private static native long create(long peer, byte[] contents) throws LiteCoreException;

    private static native void delete(long peer, long blobKey) throws LiteCoreException;

    private static native long openReadStream(long peer, long blobKey) throws LiteCoreException;

    private static native long openWriteStream(long peer) throws LiteCoreException;

    // BlobReadStream

    @GuardedBy("readStreamLock")
    private static native int read(long peer, byte[] b, int offset, long maxBytesToRead) throws LiteCoreException;

    @GuardedBy("readStreamLock")
    private static native void seek(long peer, long position) throws LiteCoreException;

    // this method frees the stream
    private static native void closeReadStream(long peer);

    // BlobWriteStream

    @GuardedBy("writeStreamLock")
    private static native void write(long peer, byte[] bytes, int len) throws LiteCoreException;

    @GuardedBy("writeStreamLock")
    private static native long computeBlobKey(long peer) throws LiteCoreException;

    @GuardedBy("writeStreamLock")
    private static native void install(long peer) throws LiteCoreException;

    // this method frees the stream
    private static native void closeWriteStream(long peer);
}

