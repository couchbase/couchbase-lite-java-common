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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.Collection;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.Scope;
import com.couchbase.lite.internal.core.impl.NativeC4Collection;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.Preconditions;


public class C4Collection extends C4NativePeer {
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
        void nCreateIndex(
            long peer,
            String name,
            String indexSpec,
            int queryLanguage,
            int indexType,
            String language,
            boolean ignoreDiacritics)
            throws LiteCoreException;
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

    @Nullable
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
    @Nullable
    static C4Collection getDefault(@NonNull NativeImpl impl, @NonNull C4Database c4db) throws LiteCoreException {
        final long c4collection = impl.nGetDefaultCollection(c4db.getPeer());
        return (c4collection == 0)
            ? null
            : new C4Collection(impl, c4collection, c4db, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME);
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

    @VisibleForTesting
    C4Collection(
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

    @NonNull
    public C4Document getDocument(@NonNull String docId) throws LiteCoreException { return getDocument(docId, true); }

    @VisibleForTesting
    @NonNull
    public C4Document getDocument(@NonNull String docId, boolean mustExist) throws LiteCoreException {
        return C4Document.get(this, Preconditions.assertNotNull(docId, "doc ID"), mustExist);
    }

    @NonNull
    public C4Document createDocument(@NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return C4Document.create(this, docID, body, flags);
    }

    public void setDocumentExpiration(String docID, long timeStamp) throws LiteCoreException {
        withPeerThrows(peer -> impl.nSetDocExpiration(peer, docID, timeStamp));
    }

    // ??? Is this ok?  Can it actually ever return 0?
    public long getDocumentExpiration(String docID) throws LiteCoreException {
        return withPeerOrDefault(0L, peer -> impl.nGetDocExpiration(peer, docID));
    }

    public void purgeDocument(String docID) throws LiteCoreException { impl.nPurgeDoc(getPeer(), docID); }

    // - Queries

    @NonNull
    public C4Query createJsonQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, AbstractIndex.QueryLanguage.JSON, expression);
    }

    @NonNull
    public C4Query createN1qlQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, AbstractIndex.QueryLanguage.N1QL, expression);
    }

    // - Observers

    @NonNull
    public C4CollectionObserver createCollectionObserver(@NonNull Runnable listener) {
        return C4CollectionObserver.newObserver(getPeer(), listener);
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(@NonNull String docID, @NonNull Runnable listener) {
        return C4DocumentObserver.newObserver(getPeer(), docID, listener);
    }

    // - Indexes

    public void createIndex(
        String name,
        String indexSpec,
        AbstractIndex.QueryLanguage queryLanguage,
        AbstractIndex.IndexType indexType,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        withPeerThrows(peer ->
            impl.nCreateIndex(
                peer,
                name,
                indexSpec,
                queryLanguage.getValue(),
                indexType.getValue(),
                language,
                ignoreDiacritics));
    }


    @NonNull
    public FLValue getIndexesInfo() throws LiteCoreException {
        final FLValue info = withPeerOrNull((peer) -> new FLValue(impl.nGetIndexesInfo(peer)));
        return Preconditions.assertNotNull(info, "index info");
    }

    public void deleteIndex(String name) throws LiteCoreException {
        withPeerThrows(peer -> impl.nDeleteIndex(peer, name));
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

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, impl::nFree); }
}
