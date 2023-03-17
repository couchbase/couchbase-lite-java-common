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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public class C4TestUtils {
    public static class C4DocEnumerator extends C4NativePeer {
        public C4DocEnumerator(long db, int flags) throws LiteCoreException { this(enumerateAllDocs(db, flags)); }

        private C4DocEnumerator(long peer) { super(peer); }

        @NonNull
        public C4Document getDocument() throws LiteCoreException {
            return new C4Document(withPeerOrThrow(C4TestUtils::getDocument));
        }

        public boolean next() throws LiteCoreException {
            return withPeerOrDefault(false, C4TestUtils::next);
        }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) {
            releasePeer(domain, C4TestUtils::free);
        }
    }

    @NonNull
    public static byte[] privateUUIDForDb(@NonNull C4Database db) throws LiteCoreException {
        return getPrivateUUID(db.getPeer());
    }

    @NonNull
    public static FLSliceResult encodeJSONInDb(@NonNull C4Database db, @NonNull String data) throws LiteCoreException {
        return encodeJSON(db.getPeer(), data.getBytes(StandardCharsets.UTF_8));
    }

    public static String idForDoc(C4Document doc) { return getDocID(doc.getPeer()); }

    public static C4DocEnumerator enumerateDocsForCollection(C4Collection coll, int flags) throws LiteCoreException {
        return new C4DocEnumerator(coll.getPeer(), flags);
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    public static C4Document create(
        @NonNull C4Collection collection,
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
            collection.getPeer(),
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
    public static C4Document create(
        @NonNull C4Collection collection,
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
            collection.getPeer(),
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
    // native methods
    //-------------------------------------------------------------------------

    @NonNull
    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    @NonNull
    private static native FLSliceResult encodeJSON(long db, @NonNull byte[] jsonData) throws LiteCoreException;

    private static native long enumerateAllDocs(long db, int flags) throws LiteCoreException;

    private static native boolean next(long peer) throws LiteCoreException;

    private static native long getDocument(long peer) throws LiteCoreException;

    private static native void free(long peer);

    @NonNull
    private static native String getDocID(long doc);

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long put(
        long collection,
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
        long collection,
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
}
