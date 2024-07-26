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

import java.util.List;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4Document;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.logging.Log;


@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public final class C4Document extends C4NativePeer {
    public interface NativeImpl {
        //// Creating and Updating Documents
        @GuardedBy("dbLock")
        long nGetFromCollection(long coll, String docID, boolean mustExist, boolean getAllRevs)
            throws LiteCoreException;
        @GuardedBy("dbLock")
        long nCreateFromSlice(long coll, String docID, long bodyPtr, long bodySize, int flags)
            throws LiteCoreException;
        //// Properties
        int nGetFlags(long doc);
        @NonNull
        String nGetRevID(long doc);
        long nGetSequence(long doc);
        //// Revisions
        int nGetSelectedFlags(long doc);
        @NonNull
        String nGetSelectedRevID(long doc);
        @Nullable
        String nGetRevisionHistory(long coll, long doc, long maxRevs, @Nullable String[] backToRevs)
            throws LiteCoreException;
        long nGetTimestamp(long doc);
        long nGetSelectedSequence(long doc);
        // return pointer to FLValue
        @GuardedBy("dbLock")
        long nGetSelectedBody2(long doc);
        //// Conflict Resolution
        @GuardedBy("dbLock")
        void nSelectNextLeafRevision(long doc, boolean includeDeleted, boolean withBody) throws LiteCoreException;
        @GuardedBy("dbLock")
        void nResolveConflict(
            long doc,
            String winningRevID,
            String losingRevID,
            byte[] mergeBody,
            int mergedFlags)
            throws LiteCoreException;
        @GuardedBy("dbLock&docLock")
        long nUpdate(long doc, long bodyPtr, long bodySize, int flags) throws LiteCoreException;
        @GuardedBy("dbLock&docLock")
        void nSave(long doc, int maxRevTreeDepth) throws LiteCoreException;
        //// Fleece-related
        @GuardedBy("dbLock")
        @NonNull
        String nBodyAsJSON(long doc, boolean canonical) throws LiteCoreException;
        //// Lifecycle
        void nFree(long doc);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Document();

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4Document create(@NonNull C4Collection coll, @NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        final Object lock = coll.getDbLock();

        final long peer = coll.withPeerOrThrow(collPeer -> {
            synchronized (lock) {
                return NATIVE_IMPL.nCreateFromSlice(
                    collPeer,
                    docID,
                    (body == null) ? 0 : body.getBase(),
                    (body == null) ? 0 : body.getSize(),
                    flags);
            }
        });

        return new C4Document(NATIVE_IMPL, peer, lock);
    }

    @Nullable
    static C4Document get(@NonNull C4Collection coll, @NonNull String docID)
        throws LiteCoreException {
        final Object lock = coll.getDbLock();
        final long peer = coll.withPeerOrThrow(collPeer -> {
            synchronized (lock) { return NATIVE_IMPL.nGetFromCollection(collPeer, docID, true, false); }
        });
        return (peer == 0) ? null : new C4Document(NATIVE_IMPL, peer, lock);
    }

    @Nullable
    static C4Document getWithRevs(@NonNull C4Collection coll, @NonNull String docID)
        throws LiteCoreException {
        final Object lock = coll.getDbLock();
        final long peer = coll.withPeerOrThrow(collPeer -> {
            synchronized (lock) { return NATIVE_IMPL.nGetFromCollection(collPeer, docID, true, true); }
        });
        return (peer == 0) ? null : new C4Document(NATIVE_IMPL, peer, lock);
    }

    @VisibleForTesting
    @NonNull
    static C4Document getOrCreateDocument(@NonNull C4Collection coll, @NonNull String docID) throws LiteCoreException {
        final Object lock = coll.getDbLock();
        final long peer = coll.withPeerOrThrow(collPeer -> {
            synchronized (lock) { return NATIVE_IMPL.nGetFromCollection(collPeer, docID, false, true); }
        });

        // This should never happen.  With "mustExist" set false we should get:
        // - the existing doc, if there is one
        // - a new doc if none exists
        // - an exception other than "not found"
        if (peer == 0) {
            throw new LiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.NOT_FOUND,
                "Could not create document: " + docID);
        }

        return new C4Document(NATIVE_IMPL, peer, lock);
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final NativeImpl impl;

    @NonNull
    private final Object dbLock;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4Document(@NonNull NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
        this.dbLock = lock;
    }

    @VisibleForTesting
    C4Document(long peer, @NonNull Object lock) { this(NATIVE_IMPL, peer, lock); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - Properties
    @Nullable
    public String getRevID() { return withPeerOrNull(impl::nGetRevID); }

    public long getSequence() { return withPeerOrDefault(0L, impl::nGetSequence); }

    public int getSelectedFlags() { return withPeerOrDefault(0, impl::nGetSelectedFlags); }

    // - Revisions

    @Nullable
    public String getSelectedRevID() { return withPeerOrNull(impl::nGetSelectedRevID); }

    @Nullable
    public String getRevisionHistory(@NonNull C4Collection coll, long maxRevs, @Nullable List<String> backToRevs)
        throws LiteCoreException {
        final String[] backToRevsArray = (backToRevs == null) ? null : backToRevs.toArray(new String[0]);
        return coll.withPeerOrNull(collPeer ->
            withPeerOrNull(peer -> impl.nGetRevisionHistory(collPeer, peer, maxRevs, backToRevsArray)));
    }

    public long getSelectedSequence() { return withPeerOrDefault(0L, impl::nGetSelectedSequence); }

    @Nullable
    public FLDict getSelectedBody2() {
        return nullableWithPeerOrThrow(peer -> FLDict.createOrNull(() -> impl.nGetSelectedBody2(peer)));
    }

    // - Conflict resolution

    public long getTimestamp() { return withPeerOrDefault(0L, impl::nGetTimestamp); }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (dbLock) { impl.nSelectNextLeafRevision(peer, includeDeleted, withBody); }
        });
    }

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (dbLock) { impl.nResolveConflict(peer, winningRevID, losingRevID, mergeBody, mergedFlags); }
        });
    }

    @Nullable
    public C4Document update(@Nullable FLSliceResult body, int flags) throws LiteCoreException {
        final long newDoc = withPeerOrDefault(
            0L,
            peer -> {
                synchronized (dbLock) {
                    return impl.nUpdate(
                        peer,
                        (body == null) ? 0 : body.getBase(),
                        (body == null) ? 0 : body.getSize(),
                        flags);
                }
            });
        return (newDoc == 0) ? null : new C4Document(impl, newDoc, dbLock);
    }

    public void save(int maxRevTreeDepth) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (dbLock) { impl.nSave(peer, maxRevTreeDepth); }
        });
    }

    // - Fleece

    @NonNull
    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withPeerOrThrow(peer -> {
            synchronized (dbLock) { return impl.nBodyAsJSON(peer, canonical); }
        });
    }

    // - Helper methods

    public boolean docExists() { return C4Constants.hasFlags(getFlags(), C4Constants.DocumentFlags.EXISTS); }

    public boolean isDocDeleted() { return C4Constants.hasFlags(getFlags(), C4Constants.DocumentFlags.DELETED); }

    public boolean isDocConflicted() { return C4Constants.hasFlags(getFlags(), C4Constants.DocumentFlags.CONFLICTED); }

    public boolean hasDocAttachments() {
        return C4Constants.hasFlags(getFlags(), C4Constants.DocumentFlags.HAS_ATTACHMENTS);
    }

    public boolean isRevDeleted() {
        return C4Constants.hasFlags(getSelectedFlags(), C4Constants.RevisionFlags.DELETED);
    }

    public boolean isRevConflicted() {
        return C4Constants.hasFlags(getSelectedFlags(), C4Constants.RevisionFlags.IS_CONFLICT);
    }

    public boolean hasRevAttachments() {
        return C4Constants.hasFlags(getFlags(), C4Constants.RevisionFlags.HAS_ATTACHMENTS);
    }

    // Although we inherit it from C4NativePeer, actually closing the C4Document
    // will cause crashes. Apparently, there may be multiple active references
    // to a single C4Document, making it very hard to figure out when they can be
    // closed, explicitly.  Just log the call: don't actually close it.
    // See finalize() below.
    @Override
    public void close() {
        Log.w(LogDomain.DATABASE, "Unsafe call to C4Document.close()", new Exception("Unsafe call at:"));
    }

    @NonNull
    @Override
    public String toString() { return "C4Document" + super.toString(); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    // As noted above (close()) and in Document.updateC4DocumentLocked it seems that there
    // may be several live reference to a single C4Document (see MutableDocument.<init>).
    // That means that it is pretty difficult to figure how to release them, explicitly.
    // Attempts to close the C4Document, e.g. in Document.updateC4DocumentLocked resulted
    // in many failed tests and even some native crashes in Database.saveInTransaction.
    // That is just a huge shame, since it means that every single document created by
    // client code, eventually ends up on the finalizer queue. A lot of code that seems
    // to work -- some of it fairly mysterious -- would have to change to fix this.
    // I'm quite reluctant to make such big changes without a clear benefit from doing so.
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        // Since there is no good way to free these suckers explicitly,
        // we leave them to the finalizer and don't squawk about it.
        try { closePeer(null); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private int getFlags() { return withPeerOrDefault(0, impl::nGetFlags); }

    // This idiom, which you will see in many places in this code,
    // may protect against a failure that both customers and I have seen:
    // the ART runtime frees (and nulls) a member reference before freeing the
    // object that refers to it: impl may be null.
    // If that happens, we are going to leak memory.  This idiom, though
    // may prevent an NPE on the finalizer thread.
    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
