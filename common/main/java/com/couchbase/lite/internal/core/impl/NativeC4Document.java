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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Document;

@SuppressWarnings("PMD.TooManyMethods")
public final class NativeC4Document implements C4Document.NativeImpl {
    //// Creating and Updating Documents
    @Override
    public long nGetFromCollection(long coll, String docID, boolean mustExist, boolean getAllRevs)
        throws LiteCoreException {
        return getFromCollection(coll, docID, mustExist, getAllRevs);
    }

    @Override
    public long nCreateFromSlice(long coll, String docID, long bodyPtr, long bodySize, int flags)
        throws LiteCoreException {
        return createFromSlice(coll, docID, bodyPtr, bodySize, flags);
    }

    //// Properties
    @Override
    public int nGetFlags(long doc) { return getFlags(doc); }

    @Override
    @NonNull
    public String nGetRevID(long doc) { return getRevID(doc); }

    @Override
    public long nGetSequence(long doc) { return getSequence(doc); }

    //// Revisions
    @Override
    public int nGetSelectedFlags(long doc) { return getSelectedFlags(doc); }

    @Override
    @NonNull
    public String nGetSelectedRevID(long doc) { return getSelectedRevID(doc); }

    @Nullable
    @Override
    public String nGetRevisionHistory(long coll, long doc, long maxRevs, @Nullable String[] backToRevs)
        throws LiteCoreException {
        return getRevisionHistory(coll, doc, maxRevs, backToRevs);
    }

    @Override
    public long nGetTimestamp(long doc) { return getTimestamp(doc); }

    @Override
    public long nGetSelectedSequence(long doc) { return getSelectedSequence(doc); }

    // return pointer to FLValue
    @Override
    public long nGetSelectedBody2(long doc) { return getSelectedBody2(doc); }

    //// Conflict Resolution
    @Override
    public void nSelectNextLeafRevision(long doc, boolean includeDeleted, boolean withBody) throws LiteCoreException {
        selectNextLeafRevision(doc, includeDeleted, withBody);
    }

    @Override
    public void nResolveConflict(
        long doc,
        String winningRevID,
        String losingRevID,
        byte[] mergeBody,
        int mergedFlags)
        throws LiteCoreException {
        resolveConflict(doc, winningRevID, losingRevID, mergeBody, mergedFlags);
    }

    @Override
    public long nUpdate(long doc, long bodyPtr, long bodySize, int flags) throws LiteCoreException {
        return update2(doc, bodyPtr, bodySize, flags);
    }

    @Override
    public void nSave(long doc, int maxRevTreeDepth) throws LiteCoreException { save(doc, maxRevTreeDepth); }

    //// Fleece-related
    @Override
    @Nullable
    public String nBodyAsJSON(long doc, boolean canonical) throws LiteCoreException {
        return bodyAsJSON(doc, canonical);
    }

    //// Lifecycle
    @Override
    public void nFree(long doc) { free(doc); }

    //// Utility
    @Override
    public boolean nDictContainsBlobs(long dictPtr, long dictSize, long sk) {
        return dictContainsBlobs(dictPtr, dictSize, sk);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    //// Creating and Updating Documents

    private static native long getFromCollection(long coll, String docID, boolean mustExist, boolean getAllRevs)
        throws LiteCoreException;

    private static native long createFromSlice(long coll, String docID, long bodyPtr, long bodySize, int flags)
        throws LiteCoreException;

    //// Properties
    private static native int getFlags(long doc);

    @NonNull
    private static native String getRevID(long doc);

    private static native long getSequence(long doc);

    //// Revisions
    private static native int getSelectedFlags(long doc);

    @NonNull
    private static native String getSelectedRevID(long doc);

    @Nullable
    private static native String getRevisionHistory(long coll, long peer, long maxRevs, @Nullable String[] backToRevs)
        throws LiteCoreException;

    private static native long getTimestamp(long doc);

    private static native long getSelectedSequence(long doc);

    private static native long getSelectedBody2(long doc);

    //// Conflict Resolution
    private static native void selectNextLeafRevision(long doc, boolean includeDeleted, boolean withBody)
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

    //// Fleece-related
    @Nullable
    private static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;

    //// Lifecycle
    private static native void free(long doc);

    //// Utility
    private static native boolean dictContainsBlobs(long dictPtr, long dictSize, long sk);

    // Remove when Version Vectors are enabled
    @Deprecated
    private static native long getGenerationForId(@NonNull String doc);
}
