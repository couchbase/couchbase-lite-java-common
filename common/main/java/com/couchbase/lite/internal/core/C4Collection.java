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
import com.couchbase.lite.internal.core.impl.NativeC4Collection;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public class C4Collection extends C4NativePeer {
    public interface NativeImpl {
        long nCreateCollection(long c4Db, @Nullable String scope, @NonNull String collection);
        boolean nIsValid(long c4collection);
        long nGetDatabase(long c4collection);
        long nGetDocumentCount(long c4collection);
        long nGetLastSequence(long c4collection);
        long nGetDoc(long c4Collection, @NonNull String docID, boolean mustExist, long contentLevel);
        long nGetDocBySequence(long c4Collection, long seq);
        long nPutDoc(long c4Collection, long request);
        long nCreateDoc(
            long c4Collection,
            @NonNull String docID,
            @NonNull byte[] body,
            int revisionFlags);
        boolean nMoveDoc(
            long c4Collection,
            @NonNull String docID,
            long toCollection,
            @NonNull String newDocID);
        boolean nPurgeDoc(long c4Collection, @NonNull String docID);
        boolean nSetDocExpiration(long c4Collection, @NonNull String docID, long timestamp);
        long nGetDocExpiration(long c4Collection, @NonNull String docID);
        long nNextDocExpiration(long c4Db);
        long nPurgeExpiredDocs(long c4Db);
        boolean nCreateIndex(
            long c4Collection,
            String name,
            String indexSpec,
            int queryLanguage,
            int indexType,
            String language,
            boolean ignoreDiacritics);
        boolean nDeleteIndex(long c4Collection, @NonNull String name);
        long nGetIndexesInfo(long c4Collection);
    }

    @NonNull
    @VisibleForTesting
    static volatile NativeImpl nativeImpl = new NativeC4Collection();

    @NonNull
    @VisibleForTesting
    static final NativeRefPeerBinding<C4Collection> BOUND_COLLECTIONS = new NativeRefPeerBinding<>();

    /**
     * this method will change once createCollection is moved to c4db
     **/
    @NonNull
    public static C4Collection create(@NonNull C4Database c4Database, @NonNull C4CollectionSpec spec) {
        final long peer = nativeImpl.nCreateCollection(
            c4Database.getPeer(),
            spec.getScopeName(),
            spec.getCollectionName());
        final C4Collection c4Collection = new C4Collection(nativeImpl, c4Database, peer);
        BOUND_COLLECTIONS.bind(peer, c4Collection);
        return c4Collection;
    }

    private final NativeImpl impl;
    private final C4Database c4Database;

    @VisibleForTesting
    C4Collection(@NonNull NativeImpl impl, @NonNull C4Database c4Database, long peer) {
        super(peer);
        this.impl = impl;
        this.c4Database = c4Database;
    }

    /**
     * Accessors
     */

    public boolean isValid() { return withPeerOrDefault(false, impl::nIsValid); }

    @NonNull
    public C4Database getDatabase() { return c4Database; }

    public long getDocumentCount() { return withPeerOrDefault(0L, impl::nGetDocumentCount); }

    public long getLastSequence() { return withPeerOrDefault(0L, impl::nGetLastSequence); }


    /**
     * Document
     */

    @NonNull
    public C4Document getDocument(@NonNull String docID, boolean mustExist, long contentLevel) {
        final long docPeer = impl.nGetDoc(getPeer(), docID, mustExist, contentLevel);
        return new C4Document(docPeer);
    }

    @NonNull
    public C4Document getDocBySequence(long sequence) {
        final long docPeer = impl.nGetDocBySequence(getPeer(), sequence);
        return new C4Document(docPeer);
    }

    @NonNull
    public C4Document putDoc(long request) {
        final long docPeer = impl.nPutDoc(getPeer(), request);
        return new C4Document(docPeer);
    }

    @NonNull
    public C4Document createDocument(@NonNull String docID, @NonNull byte[] body, int revisionFlags) {
        final long docPeer = impl.nCreateDoc(getPeer(), docID, body, revisionFlags);
        return new C4Document(docPeer);
    }

    public boolean moveDocument(String docID, C4Collection toCollection, String newDocID) {
        return impl.nMoveDoc(getPeer(), docID, toCollection.getPeer(), newDocID);
    }

    public boolean purgeDocument(String docID) { return impl.nPurgeDoc(getPeer(), docID); }

    public boolean setDocumentExpiration(String docID, long timeStamp) {
        return impl.nSetDocExpiration(
            getPeer(),
            docID,
            timeStamp);
    }

    public long getDocumentExpiration(String docID) { return impl.nGetDocExpiration(getPeer(), docID); }

    public long nextDocumentExpiration() {return withPeerOrDefault(0L, impl::nNextDocExpiration); }

    public long purgeExpiredDocuments() {return withPeerOrDefault(0L, impl::nPurgeExpiredDocs); }

    /**
     * Indexes
     */

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

    public boolean deleteIndex(String name) { return impl.nDeleteIndex(getPeer(), name); }

    @NonNull
    public FLSliceResult getIndexesInfo() {
        final long peer = withPeerOrDefault(0L, impl::nGetIndexesInfo);
        return FLSliceResult.getManagedSliceResult(peer);
    }

    @Override
    public void close() throws Exception { releasePeer(); }
}
