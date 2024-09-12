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

import java.util.List;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4Document;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.logging.Log;


@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public final class C4Document extends C4Peer {
    public interface NativeImpl {
        //// Creating and Updating Documents
        long nGetFromCollection(long coll, String docID, boolean mustExist, boolean getAllRevs)
            throws LiteCoreException;
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
        String nGetRevisionHistory(long jdoc, long maxRevs, @Nullable String[] backToRevs);
        long nGetTimestamp(long doc);
        long nGetSelectedSequence(long doc);
        // return pointer to FLValue
        long nGetSelectedBody2(long doc);
        //// Conflict Resolution
        void nSelectNextLeafRevision(long doc, boolean includeDeleted, boolean withBody) throws LiteCoreException;
        void nResolveConflict(
            long doc,
            String winningRevID,
            String losingRevID,
            byte[] mergeBody,
            int mergedFlags)
            throws LiteCoreException;
        long nUpdate(long doc, long bodyPtr, long bodySize, int flags) throws LiteCoreException;
        void nSave(long doc, int maxRevTreeDepth) throws LiteCoreException;
        //// Fleece-related
        @NonNull
        String nBodyAsJSON(long doc, boolean canonical) throws LiteCoreException;
        //// Lifecycle
        void nFree(long doc);
        //// Utility
        boolean nDictContainsBlobs(long dictPtr, long dictSize, long sk);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Document();

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4Document create(@NonNull C4Collection coll, @NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return coll.withPeerOrThrow(collPeer -> new C4Document(
            NATIVE_IMPL,
            NATIVE_IMPL.nCreateFromSlice(
                collPeer,
                docID,
                (body == null) ? 0 : body.getBase(),
                (body == null) ? 0 : body.getSize(),
                flags)));
    }

    @Nullable
    static C4Document get(@NonNull C4Collection coll, @NonNull String docID)
        throws LiteCoreException {
        final long doc = coll.withPeerOrThrow(collPeer -> NATIVE_IMPL.nGetFromCollection(collPeer, docID, true, false));
        return (doc == 0) ? null : new C4Document(NATIVE_IMPL, doc);
    }

    @Nullable
    static C4Document getWithRevs(@NonNull C4Collection coll, @NonNull String docID)
        throws LiteCoreException {
        final long doc = coll.withPeerOrThrow(collPeer -> NATIVE_IMPL.nGetFromCollection(collPeer, docID, true, true));
        return (doc == 0) ? null : new C4Document(NATIVE_IMPL, doc);
    }

    @VisibleForTesting
    @NonNull
    static C4Document getOrCreateDocument(@NonNull C4Collection coll, @NonNull String docID) throws LiteCoreException {
        final long doc = coll.withPeerOrThrow(collPeer -> NATIVE_IMPL.nGetFromCollection(collPeer, docID, false, true));

        // This should never happen.  With "mustExist" set false we should get:
        // - the existing doc, if there is one
        // - a new doc if none exists
        // - an exception other than "not found"
        if (doc == 0) {
            throw new LiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.NOT_FOUND,
                "Could not create document: " + docID);
        }

        return new C4Document(NATIVE_IMPL, doc);
    }

    //-------------------------------------------------------------------------
    // Static Utility Methods
    //-------------------------------------------------------------------------

    public static boolean dictContainsBlobs(@NonNull FLSliceResult dict, @NonNull FLSharedKeys sk) {
        return NATIVE_IMPL.nDictContainsBlobs(dict.getBase(), dict.getSize(), sk.getHandle());
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4Document(@NonNull NativeImpl impl, long peer) {
        super(peer, impl::nFree);
        this.impl = impl;
    }

    @VisibleForTesting
    C4Document(long peer) { this(NATIVE_IMPL, peer); }

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
    public String getRevisonIds(long maxRevs, @Nullable List<String> backToRevs) {
        final String[] backToRevsArray = (backToRevs == null) ? null : backToRevs.toArray(new String[0]);
        return withPeerOrNull(peer -> impl.nGetRevisionHistory(peer, maxRevs, backToRevsArray));
    }

    public long getSelectedSequence() { return withPeerOrDefault(0L, impl::nGetSelectedSequence); }

    @Nullable
    public FLDict getSelectedBody2() {
        final long value = withPeerOrThrow(impl::nGetSelectedBody2);
        return value == 0 ? null : FLDict.create(value);
    }

    // - Conflict resolution

    public long getTimestamp() { return withPeerOrDefault(0L, impl::nGetTimestamp); }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> impl.nSelectNextLeafRevision(peer, includeDeleted, withBody));
    }

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        voidWithPeerOrThrow(peer -> impl.nResolveConflict(peer, winningRevID, losingRevID, mergeBody, mergedFlags));
    }

    @Nullable
    public C4Document update(@Nullable FLSliceResult body, int flags) throws LiteCoreException {
        final long newDoc = withPeerOrDefault(
            0L,
            h -> impl.nUpdate(
                h,
                (body == null) ? 0 : body.getBase(),
                (body == null) ? 0 : body.getSize(),
                flags));
        return (newDoc == 0) ? null : new C4Document(impl, newDoc);
    }

    public void save(int maxRevTreeDepth) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> impl.nSave(peer, maxRevTreeDepth));
    }

    // - Fleece

    @NonNull
    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withPeerOrThrow(h -> impl.nBodyAsJSON(h, canonical));
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

    // Although we inherit it from AutoClosable, actually closing the C4Document
    // will cause crashes. Apparently, there may be multiple active references
    // to a single C4Document, making it very hard to figure out when they can be
    // closed, explicitly.  Just log the call but don't actually close it.
    @SuppressWarnings({"MissingSuperCall", "PMD.CallSuper"})
    @Override
    public void close() {
        Log.w(LogDomain.DATABASE, "Unsafe call to C4Document.close()", new Exception("Unsafe call at:"));
    }

    @NonNull
    @Override
    public String toString() { return "C4Document" + super.toString(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private int getFlags() { return withPeerOrDefault(0, impl::nGetFlags); }
}
