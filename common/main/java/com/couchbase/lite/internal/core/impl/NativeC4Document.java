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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Document;


@SuppressWarnings("PMD.TooManyMethods")
public final class NativeC4Document implements C4Document.NativeImpl {
    //// Creating and Updating Documents
    @GuardedBy("dbLock")
    @Override
    public long nGetFromCollection(long coll, String docID, boolean mustExist, boolean getAllRevs)
        throws LiteCoreException {
        return getFromCollection(coll, docID, mustExist, getAllRevs);
    }

    @GuardedBy("dbLock")
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

    @Nullable
    @Override
    public String nGetRevisionHistory(long coll, long doc, long maxRevs, @Nullable String[] backToRevs)
        throws LiteCoreException {
        return getRevisionHistory(coll, doc, maxRevs, backToRevs);
    }

    @Override
    @NonNull
    public String nGetSelectedRevID(long doc) { return getSelectedRevID(doc); }

    @Override
    public long nGetTimestamp(long doc) { return getTimestamp(doc); }

    @Override
    public long nGetSelectedSequence(long doc) { return getSelectedSequence(doc); }

    // return pointer to FLValue
    @GuardedBy("dbLock")
    @Override
    public long nGetSelectedBody2(long doc) { return getSelectedBody2(doc); }

    //// Conflict Resolution
    @GuardedBy("dbLock")
    @Override
    public void nSelectNextLeafRevision(long doc, boolean includeDeleted, boolean withBody) throws LiteCoreException {
        selectNextLeafRevision(doc, includeDeleted, withBody);
    }

    @GuardedBy("dbLock")
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

    @GuardedBy("dbLock&docLock")
    @Override
    public long nUpdate(long doc, long bodyPtr, long bodySize, int flags) throws LiteCoreException {
        return update2(doc, bodyPtr, bodySize, flags);
    }

    @GuardedBy("dbLock&docLock")
    @Override
    public void nSave(long doc, int maxRevTreeDepth) throws LiteCoreException { save(doc, maxRevTreeDepth); }

    //// Fleece-related
    @GuardedBy("dbLock")
    @Override
    @NonNull
    public String nBodyAsJSON(long doc, boolean canonical) throws LiteCoreException {
        return bodyAsJSON(doc, canonical);
    }

    //// Lifecycle
    @Override
    public void nFree(long doc) { free(doc); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    //-------------------------------------------------------------------------

    //// Creating and Updating Documents

    @GuardedBy("dbLock")
    private static native long getFromCollection(long peer, String docID, boolean mustExist, boolean getAllRevs)
        throws LiteCoreException;

    @GuardedBy("dbLock")
    private static native long createFromSlice(long peer, String docID, long bodyPtr, long bodySize, int flags)
        throws LiteCoreException;

    //// Properties
    private static native int getFlags(long peer);

    @NonNull
    private static native String getRevID(long peer);

    private static native long getSequence(long peer);

    private static native int getSelectedFlags(long peer);

    @NonNull
    private static native String getSelectedRevID(long peer);

    @Nullable
    private static native String getRevisionHistory(long coll, long peer, long maxRevs, @Nullable String[] backToRevs)
        throws LiteCoreException;

    private static native long getTimestamp(long peer);

    private static native long getSelectedSequence(long peer);

    @GuardedBy("dbLock")
    private static native long getSelectedBody2(long peer);

    //// Conflict Resolution

    @GuardedBy("dbLock")
    private static native void selectNextLeafRevision(long peer, boolean includeDeleted, boolean withBody)
        throws LiteCoreException;

    @GuardedBy("dbLock")
    private static native void resolveConflict(
        long peer,
        String winningRevID,
        String losingRevID,
        byte[] mergeBody,
        int mergedFlags)
        throws LiteCoreException;

    @GuardedBy("dbLock&docLock")
    private static native long update2(long peer, long body, long bodySize, int flags) throws LiteCoreException;

    @GuardedBy("dbLock&docLock")
    private static native void save(long peer, int maxRevTreeDepth) throws LiteCoreException;

    //// Fleece-related
    @GuardedBy("dbLock")
    @NonNull
    private static native String bodyAsJSON(long peer, boolean canonical) throws LiteCoreException;

    //// Lifecycle
    private static native void free(long peer);
}
