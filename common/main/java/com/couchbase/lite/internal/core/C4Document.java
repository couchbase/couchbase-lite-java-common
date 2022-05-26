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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;


@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public final class C4Document extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4Document create(@NonNull C4Collection coll, @NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return new C4Document(createFromSlice(coll.getPeer(), docID, (body == null) ? 0 : body.getHandle(), flags));
    }

    @NonNull
    static C4Document createRaw(@NonNull C4Collection coll, @NonNull String docID, @NonNull byte[] body, int flags)
        throws LiteCoreException {
        return new C4Document(createRaw(coll.getPeer(), docID, body, flags));
    }

    @NonNull
    static C4Document get(@NonNull C4Collection coll, @NonNull String docID, boolean mustExist)
        throws LiteCoreException {
        return new C4Document(getFromCollection(coll.getPeer(), docID, mustExist));
    }

    @NonNull
    static C4Document getBySequence(@NonNull C4Collection coll, long sequence)
        throws LiteCoreException {
        return new C4Document(getFromCollectionBySequence(coll.getPeer(), sequence));
    }


    @NonNull
    static C4Document create(@NonNull C4Database db, @NonNull String docID, boolean mustExist)
        throws LiteCoreException {
        return new C4Document(get(db.getPeer(), docID, mustExist));
    }

    @NonNull
    static C4Document create(@NonNull C4Database db, long sequence) throws LiteCoreException {
        return new C4Document(getBySequence(db.getPeer(), sequence));
    }

    @VisibleForTesting
    @NonNull
    static C4Document create(@NonNull C4Database db, @NonNull String docID, @NonNull byte[] body, int flags)
        throws LiteCoreException {
        return new C4Document(create(db.getPeer(), docID, body, flags));
    }

    @NonNull
    static C4Document create(@NonNull C4Database db, @NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return new C4Document(create2(db.getPeer(), docID, (body == null) ? 0 : body.getHandle(), flags));
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Document create(
        @NonNull C4Database db,
        @NonNull byte[] body,
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(put(
            db.getPeer(),
            body,
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Document create(
        @NonNull C4Database db,
        @NonNull FLSliceResult body, // C4Slice*
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(put2(
            db.getPeer(),
            body.getHandle(),
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    //-------------------------------------------------------------------------
    // Static Utility Methods
    //-------------------------------------------------------------------------

    public static boolean dictContainsBlobs(@NonNull FLSliceResult dict, @NonNull FLSharedKeys sk) {
        return dictContainsBlobs(dict.getHandle(), sk.getHandle());
    }


    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4Document(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - C4Document

    public int getFlags() { return withPeerOrDefault(0, C4Document::getFlags); }

    @Nullable
    public String getDocID() { return withPeerOrNull(C4Document::getDocID); }

    @Nullable
    public String getRevID() { return withPeerOrNull(C4Document::getRevID); }

    public long getSequence() { return withPeerOrDefault(0L, C4Document::getSequence); }

    // - C4Revision

    @Nullable
    public String getSelectedRevID() { return withPeerOrNull(C4Document::getSelectedRevID); }

    public long getSelectedSequence() { return withPeerOrDefault(0L, C4Document::getSelectedSequence); }

    @Nullable
    public FLDict getSelectedBody2() {
        final long value = withPeerOrDefault(0L, C4Document::getSelectedBody2);
        return value == 0 ? null : FLDict.create(value);
    }

    // - Lifecycle

    public int getSelectedFlags() { return withPeerOrDefault(0, C4Document::getSelectedFlags); }

    public void save(int maxRevTreeDepth) throws LiteCoreException { save(getPeer(), maxRevTreeDepth); }

    // - Revisions

    public boolean selectNextRevision() { return withPeerOrDefault(false, C4Document::selectNextRevision); }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody) throws LiteCoreException {
        selectNextLeafRevision(getPeer(), includeDeleted, withBody);
    }

    // - Purging and Expiration

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        resolveConflict(getPeer(), winningRevID, losingRevID, mergeBody, mergedFlags);
    }

    // - Creating and Updating Documents

    @Nullable
    public C4Document update(@Nullable FLSliceResult body, int flags) throws LiteCoreException {
        final long bodyHandle = (body != null) ? body.getHandle() : 0;
        final long newDoc = withPeerOrDefault(0L, h -> update2(h, bodyHandle, flags));
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    @VisibleForTesting
    @Nullable
    public C4Document update(@NonNull byte[] body, int flags) throws LiteCoreException {
        final long newDoc = withPeerOrDefault(0L, h -> update(h, body, flags));
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    // - Helper methods

    // helper methods for Document
    public boolean deleted() { return isSelectedRevFlags(C4Constants.RevisionFlags.DELETED); }

    public boolean exists() { return isFlags(C4Constants.DocumentFlags.EXISTS); }

    public boolean isSelectedRevFlags(int flag) { return (getSelectedFlags() & flag) == flag; }

    // - Fleece

    @Nullable
    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withPeerOrNull(h -> bodyAsJSON(h, canonical));
    }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    @NonNull
    @Override
    public String toString() { return "C4Document@" + super.toString(); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        // ??? These things are practically never closed.
        // No point is spamming the log
        try { closePeer(null); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    @VisibleForTesting
    @Nullable
    byte[] getSelectedBody() { return withPeerOrNull(C4Document::getSelectedBody); }

    @VisibleForTesting
    int purgeRevision(String revID) throws LiteCoreException {
        return withPeerOrDefault(0, h -> purgeRevision(h, revID));
    }

    @VisibleForTesting
    boolean selectCurrentRevision() { return withPeerOrDefault(false, C4Document::selectCurrentRevision); }

    @VisibleForTesting
    void loadRevisionBody() throws LiteCoreException { loadRevisionBody(getPeer()); }

    @VisibleForTesting
    boolean hasRevisionBody() { return withPeerOrDefault(false, C4Document::hasRevisionBody); }

    @VisibleForTesting
    boolean selectParentRevision() { return withPeerOrDefault(false, C4Document::selectParentRevision); }

    @VisibleForTesting
    boolean selectCommonAncestorRevision(String revID1, String revID2) {
        return withPeerOrDefault(false, h -> selectCommonAncestorRevision(h, revID1, revID2));
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private boolean isFlags(int flag) { return (getFlags() & flag) == flag; }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean conflicted() { return isFlags(C4Constants.DocumentFlags.CONFLICTED); }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean accessRemoved() { return isSelectedRevFlags(C4Constants.RevisionFlags.PURGED); }

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4Document::free); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // - Creating and Updating Documents

    private static native long createFromSlice(long coll, String docID, long body, int flags)
        throws LiteCoreException;

    private static native long createRaw(long coll, String docID, byte[] body, int flags)
        throws LiteCoreException;

    private static native long getFromCollection(long coll, String docID, boolean mustExist)
        throws LiteCoreException;

    private static native long getFromCollectionBySequence(long coll, long sequence) throws LiteCoreException;

    private static native long create(long db, String docID, byte[] body, int flags) throws LiteCoreException;

    private static native long create2(long db, String docID, long body, int flags) throws LiteCoreException;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long put(
        long db,
        byte[] body,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long put2(
        long db,
        long body, // C4Slice*
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    // - C4Document

    private static native int getFlags(long doc);

    @NonNull
    private static native String getDocID(long doc);

    @NonNull
    private static native String getRevID(long doc);

    private static native long getSequence(long doc);

    // - C4Revision

    @NonNull
    private static native String getSelectedRevID(long doc);

    private static native int getSelectedFlags(long doc);

    private static native long getSelectedSequence(long doc);

    @NonNull
    private static native byte[] getSelectedBody(long doc);

    // return pointer to FLValue
    private static native long getSelectedBody2(long doc);

    // - Lifecycle

    private static native long get(long db, String docID, boolean mustExist) throws LiteCoreException;

    private static native long getBySequence(long db, long sequence) throws LiteCoreException;

    private static native void save(long doc, int maxRevTreeDepth) throws LiteCoreException;

    private static native void free(long doc);

    // - Revisions

    private static native boolean selectCurrentRevision(long doc);

    private static native void loadRevisionBody(long doc) throws LiteCoreException;

    private static native boolean hasRevisionBody(long doc);

    private static native boolean selectParentRevision(long doc);

    private static native boolean selectNextRevision(long doc);

    private static native void selectNextLeafRevision(
        long doc,
        boolean includeDeleted,
        boolean withBody)
        throws LiteCoreException;

    private static native boolean selectCommonAncestorRevision(long doc, String revID1, String revID2);

    // - Purging and Expiration

    private static native int purgeRevision(long doc, String revID) throws LiteCoreException;

    private static native void resolveConflict(
        long doc,
        String winningRevID,
        String losingRevID,
        byte[] mergeBody,
        int mergedFlags)
        throws LiteCoreException;

    // - Creating and Updating Documents

    private static native long update(long doc, byte[] body, int flags) throws LiteCoreException;

    private static native long update2(long doc, long body, int flags) throws LiteCoreException;

    // - Fleece-related

    // doc -> pointer to C4Document
    @Nullable
    private static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;

    private static native boolean dictContainsBlobs(long dict, long sk); // dict -> FLSliceResult
}
