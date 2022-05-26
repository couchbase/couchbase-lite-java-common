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
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.impl.NativeC4Collection;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public class C4Collection extends C4NativePeer {
    public interface NativeImpl {
        // Factory methods
        long nGetDefaultCollection(long c4Db);
        long nGetCollection(long c4Db, @NonNull String scope, @NonNull String collection);
        long nCreateCollection(long c4Db, @NonNull String scope, @NonNull String collection);

        // Collections
        boolean nCollectionIsValid(long peer);
        long nGetDocumentCount(long peer);
        long nGetLastSequence(long peer);

        // Documents
        long nGetDoc(long peer, @NonNull String docID, boolean mustExist);
        long nGetDocExpiration(long peer, @NonNull String docID);
        boolean nSetDocExpiration(long peer, @NonNull String docID, long timestamp);
        boolean nDeleteDoc(long peer, @NonNull String docID);
        boolean nPurgeDoc(long peer, @NonNull String docID);

        // Indexes
        long nGetIndexesInfo(long peer);
        boolean nCreateIndex(
            long peer,
            String name,
            String indexSpec,
            int queryLanguage,
            int indexType,
            String language,
            boolean ignoreDiacritics);
        boolean nDeleteIndex(long peer, @NonNull String name);
    }

    @NonNull
    @VisibleForTesting
    static volatile NativeImpl nativeImpl = new NativeC4Collection();

    @Nullable
    public static C4Collection getDefault(@NonNull C4Database c4db) { return getDefault(nativeImpl, c4db); }

    @NonNull
    public static C4Collection get(@NonNull C4Database c4db, @NonNull String scope, @NonNull String collection) {
        return get(nativeImpl, c4db, scope, collection);
    }

    @NonNull
    public static C4Collection create(@NonNull C4Database c4db, @NonNull String scope, @NonNull String collection) {
        return create(nativeImpl, c4db, scope, collection);
    }

    @Nullable
    static C4Collection getDefault(@NonNull NativeImpl impl, @NonNull C4Database c4db) {
        final long c4collection = impl.nGetDefaultCollection(c4db.getPeer());
        return c4collection == 0 ? null : new C4Collection(impl, c4collection, c4db);
    }

    @NonNull
    static C4Collection get(
        @NonNull NativeImpl impl,
        @NonNull C4Database c4db,
        @NonNull String scope,
        @NonNull String collection) {
        return new C4Collection(impl, impl.nGetCollection(c4db.getPeer(), scope, collection), c4db);
    }

    @NonNull
    static C4Collection create(
        @NonNull NativeImpl impl,
        @NonNull C4Database c4db,
        @NonNull String scope,
        @NonNull String collection) {
        return new C4Collection(impl, impl.nCreateCollection(c4db.getPeer(), scope, collection), c4db);
    }

    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;
    @NonNull
    private final C4Database db;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    @VisibleForTesting
    C4Collection(@NonNull NativeImpl impl, long peer, @NonNull C4Database db) {
        super(peer);
        this.impl = impl;
        this.db = db;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    public void close() { releasePeer(); }

    @NonNull
    @Override
    public String toString() { return "C4Collecton" + super.toString(); }

    public boolean isValid() { return withPeerOrDefault(false, impl::nCollectionIsValid); }

    public long getDocumentCount() { return withPeerOrDefault(0L, impl::nGetDocumentCount); }

    //// Documents

    @NonNull
    public C4Document getDocument(@NonNull String docId) throws LiteCoreException {
        return C4Document.get(this, docId, true);
    }

    @NonNull
    public C4Document save(@NonNull String docID, @NonNull FLSliceResult body, int flags)
        throws LiteCoreException {
        return C4Document.create(this, docID, body, flags);
    }

    public long getDocumentExpiration(String docID) {
        return withPeerOrDefault(0L, peer -> impl.nGetDocExpiration(peer, docID));
    }

    public boolean setDocumentExpiration(String docID, long timeStamp) {
        return withPeerOrDefault(false, peer -> impl.nSetDocExpiration(peer, docID, timeStamp));
    }

    public boolean deleteDocument(String docID) { return impl.nDeleteDoc(getPeer(), docID); }

    public boolean purgeDocument(String docID) { return impl.nPurgeDoc(getPeer(), docID); }

    //// Observers

    @NonNull
    public C4CollectionObserver createCollectionObserver(@NonNull Runnable listener) {
        return C4CollectionObserver.newObserver(this, listener);
    }

    @NonNull
    public C4CollectionObserver createDocumentObserver(@NonNull String docID, @NonNull Runnable listener) {
        return C4CollectionObserver.newObserver(this, docID, listener);
    }

    //// Queries

    @NonNull
    public C4Query createJsonQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, AbstractIndex.QueryLanguage.JSON, expression);
    }

    @NonNull
    public C4Query createN1qlQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, AbstractIndex.QueryLanguage.N1QL, expression);
    }

    //// Indexes

    public boolean createIndex(
        String name, String indexSpec, AbstractIndex.QueryLanguage queryLanguage,
        AbstractIndex.IndexType indexType, String language, boolean ignoreDiacritics) {
        return impl.nCreateIndex(
            getPeer(),
            name,
            indexSpec,
            queryLanguage.getValue(),
            indexType.getValue(),
            language,
            ignoreDiacritics);
    }

    @NonNull
    public FLSliceResult getIndexesInfo() {
        final Long result = withPeer(impl::nGetIndexesInfo);
        if (Long.valueOf(0L).equals(result)) { throw new IllegalStateException("IndexesInfo returned 0"); }
        return FLSliceResult.getManagedSliceResult(result);
    }

    public void deleteIndex(String name) { withPeer(peer -> impl.nDeleteIndex(peer, name)); }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    @NonNull
    C4Database getDb() { return db; }

    /* ok */
    @VisibleForTesting
    long getLastSequence() { return withPeerOrDefault(0L, impl::nGetLastSequence); }

    @VisibleForTesting
    @NonNull
    C4Document createRawDocument(@NonNull String docID, @NonNull byte[] body, int flags) throws LiteCoreException {
        return C4Document.createRaw(this, docID, body, flags);
    }

    /* ok */
    @VisibleForTesting
    @NonNull
    public C4Document getDocBySequence(long sequence) throws LiteCoreException {
        return C4Document.getBySequence(this, sequence);
    }
}
