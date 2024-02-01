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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;


@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public final class C4Document extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4Document get(@NonNull C4Collection coll, @NonNull String docID)
        throws LiteCoreException {
        return new C4Document(getFromCollection(coll.getPeer(), docID, true, false));
    }

    @NonNull
    static C4Document getWithRevs(@NonNull C4Collection coll, @NonNull String docID)
        throws LiteCoreException {
        return new C4Document(getFromCollection(coll.getPeer(), docID, true, true));
    }

    @NonNull
    static C4Document create(@NonNull C4Collection coll, @NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return new C4Document(createFromSlice(
            coll.getPeer(),
            docID,
            (body == null) ? 0 : body.getBase(),
            (body == null) ? 0 : body.getSize(),
            flags));
    }

    @VisibleForTesting
    @NonNull
    static C4Document getOrEmpty(@NonNull C4Collection coll, @NonNull String docID) throws LiteCoreException {
        return new C4Document(getFromCollection(coll.getPeer(), docID, false, true));
    }

    @VisibleForTesting
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

    @VisibleForTesting
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
            body.getBase(),
            body.getSize(),
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
        return dictContainsBlobs(dict.getBase(), dict.getSize(), sk.getHandle());
    }


    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4Document(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - Properties

    @Nullable
    public String getRevID() { return withPeerOrNull(C4Document::getRevID); }

    public long getSequence() { return withPeerOrDefault(0L, C4Document::getSequence); }

    public int getSelectedFlags() { return withPeerOrDefault(0, C4Document::getSelectedFlags); }

    // - Revisions

    @Nullable
    public String getSelectedRevID() { return withPeerOrNull(C4Document::getSelectedRevID); }

    public long getSelectedSequence() { return withPeerOrDefault(0L, C4Document::getSelectedSequence); }

    @Nullable
    public FLDict getSelectedBody2() {
        final long value = withPeerOrThrow(C4Document::getSelectedBody2);
        return value == 0 ? null : FLDict.create(value);
    }

    public long getGeneration(String id) { return getGenerationForId(id); }

    // - Conflict resolution

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody) throws LiteCoreException {
        selectNextLeafRevision(getPeer(), includeDeleted, withBody);
    }

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        resolveConflict(getPeer(), winningRevID, losingRevID, mergeBody, mergedFlags);
    }

    @Nullable
    public C4Document update(@Nullable FLSliceResult body, int flags) throws LiteCoreException {
        final long newDoc = withPeerOrDefault(
            0L,
            h -> update2(
                h,
                (body == null) ? 0 : body.getBase(),
                (body == null) ? 0 : body.getSize(),
                flags));
        return (newDoc == 0) ? null : new C4Document(newDoc);
    }

    public void save(int maxRevTreeDepth) throws LiteCoreException { save(getPeer(), maxRevTreeDepth); }

    // - Fleece

    @Nullable
    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return withPeerOrNull(h -> bodyAsJSON(h, canonical));
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
        Log.w(LogDomain.DATABASE, "Unsafe call to C4Database.close()", new Exception("Unsafe call at:"));
    }

    @NonNull
    @Override
    public String toString() { return "C4Document@" + super.toString(); }

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
        try { releasePeer(null, C4Document::free); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    @VisibleForTesting
    @Nullable
    String getDocID() { return withPeerOrNull(C4Document::getDocID); }

    private int getFlags() { return withPeerOrDefault(0, C4Document::getFlags); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // - Creating and Updating Documents

    private static native long getFromCollection(long coll, String docID, boolean mustExist, boolean getAllRevs)
        throws LiteCoreException;

    private static native long createFromSlice(long coll, String docID, long bodyPtr, long bodySize, int flags)
        throws LiteCoreException;

    // - Properties

    private static native int getFlags(long doc);

    @NonNull
    private static native String getRevID(long doc);

    private static native long getSequence(long doc);

    // - Revisions

    private static native int getSelectedFlags(long doc);

    @NonNull
    private static native String getSelectedRevID(long doc);

    private static native long getSelectedSequence(long doc);

    private static native long getGenerationForId(@NonNull String doc);

    // return pointer to FLValue
    private static native long getSelectedBody2(long doc);

    // - Conflict Resolution

    private static native void selectNextLeafRevision(
        long doc,
        boolean includeDeleted,
        boolean withBody)
        throws LiteCoreException;

    private static native void resolveConflict(
        long doc,
        String winningRevID,
        String losingRevID,
        byte[] mergeBody,
        int mergedFlags)
        throws LiteCoreException;

    private static native long update2(long doc, long bodyPtr, long bodySize, int flags) throws LiteCoreException;

    private static native void save(long doc, int maxRevTreeDepth) throws LiteCoreException;

    // - Fleece-related

    // doc -> pointer to C4Document
    @Nullable
    private static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;

    // - Lifecycle

    private static native void free(long doc);

    // - Utility

    private static native boolean dictContainsBlobs(long dictPtr, long dictSize, long sk);

    // - Testing
    // None of these methods may be used in production code.

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
        long bodyPtr,
        long bodySize,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    @NonNull
    private static native String getDocID(long doc);
}
