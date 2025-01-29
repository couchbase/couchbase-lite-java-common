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
import com.couchbase.lite.Scope;
import com.couchbase.lite.internal.core.impl.NativeC4Collection;
import com.couchbase.lite.internal.core.peers.LockManager;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.Preconditions;


public final class C4Collection extends C4Peer {
    public interface NativeImpl {
        // Factory methods
        @GuardedBy("dbLock")
        long nCreateCollection(long c4Db, @NonNull String scope, @NonNull String collection)
            throws LiteCoreException;
        @GuardedBy("dbLock")
        long nGetCollection(long c4Db, @NonNull String scope, @NonNull String collection) throws LiteCoreException;
        long nGetDefaultCollection(long c4Db) throws LiteCoreException;

        // Collections
        boolean nCollectionIsValid(long peer);
        void nFree(long peer);
        @GuardedBy("dbLock")
        long nGetDocumentCount(long peer);

        // Documents
        @GuardedBy("dbLock")
        long nGetDocExpiration(long peer, @NonNull String docID) throws LiteCoreException;
        @GuardedBy("dbLock")
        void nSetDocExpiration(long peer, @NonNull String docID, long timestamp) throws LiteCoreException;
        @GuardedBy("dbLock")
        void nPurgeDoc(long peer, @NonNull String docID) throws LiteCoreException;

        // Indexes
        @GuardedBy("dbLock")
        void nCreateValueIndex(
            long peer,
            @NonNull String name,
            int queryLanguage,
            @NonNull String indexSpec,
            @Nullable String where)
            throws LiteCoreException;
        @GuardedBy("dbLock")
        void nCreateArrayIndex(long peer, @NonNull String name, @NonNull String path, @NonNull String indexSpec)
            throws LiteCoreException;
        @GuardedBy("dbLock")
        void nCreatePredictiveIndex(long peer, @NonNull String name, @NonNull String indexSpec)
            throws LiteCoreException;
        @GuardedBy("dbLock")
        void nCreateFullTextIndex(
            long peer,
            @NonNull String name,
            int queryLanguage,
            @NonNull String indexSpec,
            @Nullable String language,
            boolean ignoreDiacritics,
            @Nullable String where)
            throws LiteCoreException;
        @GuardedBy("dbLock")
        @SuppressWarnings("PMD.ExcessiveParameterList")
        void nCreateVectorIndex(
            long peer,
            @NonNull String name,
            @NonNull String queryExpressions,
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
        @GuardedBy("dbLock")
        long nGetIndexesInfo(long peer) throws LiteCoreException;
        @GuardedBy("dbLock")
        long nGetIndex(long peer, @NonNull String name) throws LiteCoreException;
        @GuardedBy("dbLock")
        void nDeleteIndex(long peer, @NonNull String name) throws LiteCoreException;
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Collection();

    @NonNull
    public static C4Collection create(@NonNull C4Database c4db, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        return create(NATIVE_IMPL, c4db, scope, collection);
    }

    @GuardedBy("dbLock")
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

        final long peer = c4db.withPeerOrThrow(dbPeer -> {
            synchronized (LockManager.INSTANCE.getLock(dbPeer)) {
                return impl.nCreateCollection(dbPeer, scope, collection);
            }
        });

        return new C4Collection(impl, peer, c4db, scope, collection);
    }

    @VisibleForTesting
    @Nullable
    static C4Collection get(
        @NonNull NativeImpl impl,
        @NonNull C4Database c4db,
        @NonNull String scope,
        @NonNull String collection)
        throws LiteCoreException {
        final long peer = c4db.withPeerOrThrow(dbPeer -> {
            synchronized (LockManager.INSTANCE.getLock(dbPeer)) {
                return impl.nGetCollection(dbPeer, scope, collection);
            }
        });
        return (peer == 0L) ? null : new C4Collection(impl, peer, c4db, scope, collection);
    }

    @VisibleForTesting
    @NonNull
    static C4Collection getDefault(@NonNull NativeImpl impl, @NonNull C4Database c4db) throws LiteCoreException {
        return new C4Collection(
            impl,
            c4db.withPeerOrThrow(impl::nGetDefaultCollection),
            c4db, Scope.DEFAULT_NAME,
            Collection.DEFAULT_NAME);
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;

    // Seize this lock *after* the peer lock
    @NonNull
    private final Object dbLock;

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
        super(peer, impl::nFree);
        this.impl = impl;
        this.db = db;
        this.scope = scope;
        this.name = name;
        this.dbLock = db.withPeerOrThrow(dbPeer -> LockManager.INSTANCE.getLock(peer));
    }

    @NonNull
    public Object getDbLock() { return db.getLock(); }

    @NonNull
    public C4Database getDb() { return db; }

    @NonNull
    public String getScope() { return scope; }

    @NonNull
    public String getName() { return name; }

    @NonNull
    @Override
    public String toString() { return "C4Collection" + super.toString(); }

    // - Collections

    public boolean isValid() { return withPeerOrDefault(false, impl::nCollectionIsValid); }

    public long getDocumentCount() {
        return withPeerOrDefault(0L, peer -> {
            synchronized (dbLock) { return impl.nGetDocumentCount(peer); }
        });
    }

    // - Documents

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

    public long getDocumentExpiration(@NonNull String docID) throws LiteCoreException {
        return withPeerOrDefault(0L, peer -> {
            synchronized (dbLock) { return impl.nGetDocExpiration(peer, docID); }
        });
    }

    public void setDocumentExpiration(@NonNull String docID, long timeStamp) throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) { impl.nSetDocExpiration(peer, docID, timeStamp); }
        });
    }

    public void purgeDocument(@NonNull String docID) throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) { impl.nPurgeDoc(peer, docID); }
        });
    }

    public boolean docContainsBlobs(FLSliceResult body, FLSharedKeys keys) { return db.docContainsBlobs(body, keys); }

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

    public void createValueIndex(
        @NonNull String name,
        int queryLanguage,
        @NonNull String indexSpec,
        @Nullable String where)
        throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) { impl.nCreateValueIndex(peer, name, queryLanguage, indexSpec, where); }
        });
    }

    public void createArrayIndex(@NonNull String name, @NonNull String path, @NonNull String indexSpec)
        throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) { impl.nCreateArrayIndex(peer, name, path, indexSpec); }
        });
    }

    public void createPredictiveIndex(@NonNull String name, @NonNull String indexSpec) throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) { impl.nCreatePredictiveIndex(peer, name, indexSpec); }
        });
    }

    public void createFullTextIndex(
        @NonNull String name,
        int queryLanguage,
        @NonNull String indexSpec,
        @Nullable String language,
        boolean ignoreDiacritics,
        @Nullable String where)
        throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) {
                impl.nCreateFullTextIndex(
                    peer,
                    name,
                    queryLanguage,
                    indexSpec,
                    language,
                    ignoreDiacritics,
                    where);
            }
        });
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public void createVectorIndex(
        @NonNull String name,
        @NonNull String queryExpressions,
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
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) {
                impl.nCreateVectorIndex(
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
                    isLazy);
            }
        });
    }

    @NonNull
    public FLValue getIndexesInfo() throws LiteCoreException {
        return withPeerOrThrow(peer -> {
            synchronized (dbLock) { return FLValue.getFLValue(impl.nGetIndexesInfo(peer)); }
        });
    }

    @Nullable
    public C4Index getIndex(@NonNull String name) throws LiteCoreException {
        final long idx = withPeerOrThrow(peer -> {
            synchronized (dbLock) { return impl.nGetIndex(peer, name); }
        });
        return (idx == 0L) ? null : C4Index.create(idx);
    }

    public void deleteIndex(@NonNull String name) throws LiteCoreException {
        voidWithPeerOrWarn(peer -> {
            synchronized (dbLock) { impl.nDeleteIndex(peer, name); }
        });
    }
}
