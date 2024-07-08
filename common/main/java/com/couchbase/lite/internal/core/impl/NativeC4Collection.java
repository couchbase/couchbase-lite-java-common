//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
import com.couchbase.lite.internal.core.C4Collection;


@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.TooManyMethods"})
public final class NativeC4Collection implements C4Collection.NativeImpl {

    // Collections

    @Override
    public long nCreateCollection(long c4Db, @Nullable String scope, @NonNull String collection)
        throws LiteCoreException {
        return createCollection(c4Db, scope, collection);
    }

    @Override
    public long nGetCollection(long c4Db, @NonNull String scope, @NonNull String collection) throws LiteCoreException {
        return getCollection(c4Db, scope, collection);
    }

    @Override
    public long nGetDefaultCollection(long c4Db) throws LiteCoreException { return getDefaultCollection(c4Db); }

    @Override
    public boolean nCollectionIsValid(long peer) { return isValid(peer); }

    @Override
    public void nFree(long peer) { free(peer); }

    @Override
    public long nGetDocumentCount(long peer) { return getDocumentCount(peer); }

    // Documents

    @Override
    public long nGetDocExpiration(long peer, @NonNull String docID) throws LiteCoreException {
        return getDocExpiration(peer, docID);
    }

    @Override
    public void nSetDocExpiration(long peer, @NonNull String docID, long timestamp) throws LiteCoreException {
        setDocExpiration(peer, docID, timestamp);
    }

    @Override
    public void nPurgeDoc(long peer, @NonNull String docID) throws LiteCoreException { purgeDoc(peer, docID); }

    // Indexes

    @Override
    public long nGetIndexesInfo(long peer) throws LiteCoreException { return getIndexesInfo(peer); }

    @Override
    public void nCreateValueIndex(long peer, String name, int qLanguage, String indexSpec) throws LiteCoreException {
        createValueIndex(peer, name, qLanguage, indexSpec);
    }

    @Override
    public void nCreateFullTextIndex(
        long peer,
        String name,
        int qLanguage,
        String indexSpec,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        createFullTextIndex(peer, name, qLanguage, indexSpec, language, ignoreDiacritics);
    }

    @Override
    public void nCreatePredictiveIndex(long peer, String name, String indexSpec) throws LiteCoreException {
        createPredictiveIndex(peer, name, indexSpec);
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Override
    public void nCreateVectorIndex(
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
        throws LiteCoreException {
        createVectorIndex(
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

    @Override
    public long nGetIndex(long peer, @NonNull String name) throws LiteCoreException {
        return getIndex(peer, name);
    }

    @Override
    public void nDeleteIndex(long peer, @NonNull String name) throws LiteCoreException { deleteIndex(peer, name); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long getDefaultCollection(long c4Db) throws LiteCoreException;

    private static native long getCollection(long c4Db, @Nullable String scope, @NonNull String collection)
        throws LiteCoreException;

    private static native long createCollection(long c4Db, @Nullable String scope, @NonNull String collection)
        throws LiteCoreException;

    private static native boolean isValid(long peer);

    private static native void free(long peer);

    private static native long getDocumentCount(long peer);

    private static native void setDocExpiration(long peer, @NonNull String docID, long timestamp)
        throws LiteCoreException;

    private static native long getDocExpiration(long peer, @NonNull String docID)
        throws LiteCoreException;

    private static native void purgeDoc(long peer, @NonNull String docID)
        throws LiteCoreException;

    private static native long getIndexesInfo(long peer) throws LiteCoreException;

    private static native void createValueIndex(long peer, String name, int qLanguage, String indexSpec)
        throws LiteCoreException;

    private static native void createFullTextIndex(
        long peer,
        String name,
        int qLanguage,
        String indexSpec,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException;

    private static native void createPredictiveIndex(long peer, String name, String indexSpec)
        throws LiteCoreException;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native void createVectorIndex(
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

    private static native long getIndex(long peer, @NonNull String name) throws LiteCoreException;

    private static native void deleteIndex(long peer, @NonNull String name) throws LiteCoreException;
}
