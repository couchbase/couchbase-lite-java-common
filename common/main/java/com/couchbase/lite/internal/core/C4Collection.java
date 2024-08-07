//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//

package com.couchbase.lite.internal.core;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.Collection;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.Scope;
import com.couchbase.lite.internal.core.impl.NativeC4Collection;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.Preconditions;


public final class C4Collection extends C4NativePeer {
    public interface NativeImpl {
        // Factory methods
        long nCreateCollection(long c4Db, @NonNull String scope, @NonNull String collection)
            throws LiteCoreException;
        long nGetCollection(long c4Db, @NonNull String scope, @NonNull String collection) throws LiteCoreException;
        long nGetDefaultCollection(long c4Db) throws LiteCoreException;

        // Collections
        boolean nCollectionIsValid(long peer);
        long nGetDocumentCount(long peer);
        void nFree(long peer);

        // Documents
        long nGetDocExpiration(long peer, @NonNull String docID) throws LiteCoreException;
        void nSetDocExpiration(long peer, @NonNull String docID, long timestamp) throws LiteCoreException;
        void nPurgeDoc(long peer, @NonNull String docID) throws LiteCoreException;

        // Indexes
        long nGetIndexesInfo(long peer) throws LiteCoreException;

        void nCreateValueIndex(long peer, String name, int queryLanguage, String indexSpec) throws LiteCoreException;

        void nCreateFullTextIndex(
            long peer,
            String name,
            int queryLanguage,
            String indexSpec,
            String language,
            boolean ignoreDiacritics)
            throws LiteCoreException;

        void nCreatePredictiveIndex(long peer, String name, String indexSpec) throws LiteCoreException;

        @SuppressWarnings("PMD.ExcessiveParameterList")
        void nCreateVectorIndex(
            long peer,
            String name,
            String queryExpressions,
            long dimensions,
            int metric,
            long centroids,
            int encoding,
            long subquantizers,
            long bits,
            long minTrainingSize,
            long maxTrainingSize,
            long numProbes,
            boolean isLazy)
            throws LiteCoreException;

        long nGetIndex(long peer, @NonNull String name) throws LiteCoreException;

        void nDeleteIndex(long peer, @NonNull String name) throws LiteCoreException;
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Collection();

    @NonNull
    public static C4Collection create(@NonNull C4Database c4db, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        return create(NATIVE_IMPL, c4db, scope, collection);
    }

    @Nullable
    public static C4Collection get(@NonNull C4Database c4db, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        return get(NATIVE_IMPL, c4db, scope, collection);
    }

    @NonNull
    public static C4Collection getDefault(@NonNull C4Database c4db) throws LiteCoreException {
        return getDefault(NATIVE_IMPL, c4db);
    }

    @VisibleForTesting
    @NonNull
    static C4Collection create(
        @NonNull NativeImpl impl,
        @NonNull C4Database c4db,
        @NonNull String scope,
        @NonNull String collection)
        throws LiteCoreException {
        return new C4Collection(
            impl,
            impl.nCreateCollection(c4db.getPeer(), scope, collection),
            c4db,
            scope,
            collection);
    }

    @VisibleForTesting
    @Nullable
    static C4Collection get(
        @NonNull NativeImpl impl,
        @NonNull C4Database c4db,
        @NonNull String scope,
        @NonNull String collection)
        throws LiteCoreException {
        final long c4collection = impl.nGetCollection(c4db.getPeer(), scope, collection);
        return (c4collection == 0L) ? null : new C4Collection(impl, c4collection, c4db, scope, collection);
    }

    @VisibleForTesting
    @NonNull
    static C4Collection getDefault(@NonNull NativeImpl impl, @NonNull C4Database c4db) throws LiteCoreException {
        return new C4Collection(
            impl,
            impl.nGetDefaultCollection(c4db.getPeer()),
            c4db, Scope.DEFAULT_NAME,
            Collection.DEFAULT_NAME);
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;
    @NonNull
    private final C4Database db;

    @NonNull
    private final String scope;
    @NonNull
    private final String name;


    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4Collection(
        @NonNull NativeImpl impl,
        long peer,
        @NonNull C4Database db,
        @NonNull String scope,
        @NonNull String name) {
        super(peer);
        this.impl = impl;
        this.db = db;
        this.scope = scope;
        this.name = name;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    public void close() { closePeer(null); }

    @NonNull
    @Override
    public String toString() { return "C4Collection" + super.toString(); }

    public boolean isValid() { return withPeerOrDefault(false, impl::nCollectionIsValid); }

    // - Documents

    public long getDocumentCount() { return withPeerOrDefault(0L, impl::nGetDocumentCount); }

    @Nullable
    public C4Document getDocument(@NonNull String docId) throws LiteCoreException {
        return C4Document.get(this, Preconditions.assertNotNull(docId, "doc ID"));
    }

    @Nullable
    public C4Document getDocumentWithRevs(@NonNull String docId) throws LiteCoreException {
        return C4Document.getWithRevs(this, Preconditions.assertNotNull(docId, "doc ID"));
    }

    @NonNull
    public C4Document createDocument(@NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return C4Document.create(this, docID, body, flags);
    }

    public void setDocumentExpiration(String docID, long timeStamp) throws LiteCoreException {
        withPeer(peer -> impl.nSetDocExpiration(peer, docID, timeStamp));
    }

    public long getDocumentExpiration(String docID) throws LiteCoreException {
        return withPeerOrDefault(0L, peer -> impl.nGetDocExpiration(peer, docID));
    }

    public void purgeDocument(String docID) throws LiteCoreException {
        withPeer(peer -> impl.nPurgeDoc(peer, docID));
    }

    // - Observers

    @NonNull
    public C4CollectionObserver createCollectionObserver(@NonNull Runnable listener) throws LiteCoreException {
        return withPeerOrThrow(peer -> C4CollectionObserver.newObserver(peer, listener));
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(@NonNull String docID, @NonNull Runnable listener)
        throws LiteCoreException {
        return withPeerOrThrow(peer -> C4CollectionDocObserver.newObserver(peer, docID, listener));
    }

    // - Indexes

    // These all call the same underlying LiteCore method but the call interface gets
    // completely polluted if we try to combine them into a single call.

    public void createValueIndex(String name, int queryLanguage, String indexSpec) throws LiteCoreException {
        withPeer(peer -> impl.nCreateValueIndex(peer, name, queryLanguage, indexSpec));
    }

    public void createFullTextIndex(
        String name,
        int queryLanguage,
        String indexSpec,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        withPeer(peer -> impl.nCreateFullTextIndex(
            peer,
            name,
            queryLanguage,
            indexSpec,
            language,
            ignoreDiacritics));
    }

    public void createPredictiveIndex(String name, String indexSpec) throws LiteCoreException {
        withPeer(peer -> impl.nCreatePredictiveIndex(peer, name, indexSpec));
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public void createVectorIndex(
        String name,
        String queryExpressions,
        long dimensions,
        int metric,
        long centroids,
        int encoding,
        long subquantizers,
        long bits,
        long minTrainingSize,
        long maxTrainingSize,
        long numProbes,
        boolean isLazy)
        throws LiteCoreException {
        withPeer(peer -> impl.nCreateVectorIndex(
            peer,
            name,
            queryExpressions,
            dimensions,
            metric,
            centroids,
            encoding,
            subquantizers,
            bits,
            minTrainingSize,
            maxTrainingSize,
            numProbes,
            isLazy));
    }

    @NonNull
    public FLValue getIndexesInfo() throws LiteCoreException {
        return withPeerOrThrow(peer -> FLValue.getFLValue(impl.nGetIndexesInfo(peer)));
    }

    @GuardedBy("Database.getDbLock()")
    @Nullable
    public C4Index getIndex(@NonNull String name) throws LiteCoreException {
        return nullableWithPeerOrThrow(peer -> {
            final long idx = impl.nGetIndex(peer, name);
            return (idx == 0L) ? null : C4Index.create(idx);
        });
    }

    public void deleteIndex(String name) throws LiteCoreException {
        withPeer(peer -> impl.nDeleteIndex(peer, name));
    }

    @NonNull
    public C4Database getDb() { return db; }

    @NonNull
    public String getScope() { return scope; }

    @NonNull
    public String getName() { return name; }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    // ??? Exposes the peer handle
    long getHandle() { return getPeer(); }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    // Dumb check for null is necessary because Android has a nasty habit
    // of releasing fields befor calling finalize
    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
