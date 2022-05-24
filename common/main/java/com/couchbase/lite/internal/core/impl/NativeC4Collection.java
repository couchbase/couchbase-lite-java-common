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

import com.couchbase.lite.internal.core.C4Collection;


@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.TooManyMethods"})
public class NativeC4Collection implements C4Collection.NativeImpl {

    @Override
    public long nCreateCollection(long c4Db, @Nullable String scope, @NonNull String collection) {
        return createCollection(c4Db, scope, collection);
    }

    @Override
    public boolean nIsValid(long c4collection) { return isValid(c4collection); }

    @Override
    public long nGetDatabase(long c4collection) { return getDatabase(c4collection); }

    @Override
    public long nGetDocumentCount(long c4collection) { return getDocumentCount(c4collection); }

    @Override
    public long nGetLastSequence(long c4collection) { return getLastSequence(c4collection); }

    @Override
    public long nGetDoc(long c4Collection, @NonNull String docID, boolean mustExist, long contentLevel) {
        return getDoc(c4Collection, docID, mustExist, contentLevel);
    }

    @Override
    public long nGetDocBySequence(long c4Collection, long seq) { return getDocBySequence(c4Collection, seq); }

    @Override
    public long nPutDoc(long c4Collection, long request) {
        return putDoc(c4Collection, request);
    }

    @Override
    public long nCreateDoc(long c4Collection, @NonNull String docID, @NonNull byte[] body, int revisionFlags) {
        return createDoc(c4Collection, docID, body, revisionFlags);
    }

    @Override
    public boolean nMoveDoc(long c4Collection, @NonNull String docID, long toCollection, @NonNull String newDocID) {
        return moveDoc(c4Collection, docID, toCollection, newDocID);
    }

    @Override
    public boolean nPurgeDoc(long c4Collection, @NonNull String docID) {
        return purgeDoc(c4Collection, docID);
    }

    @Override
    public boolean nSetDocExpiration(long c4Collection, @NonNull String docID, long timestamp) {
        return setDocExpiration(c4Collection, docID, timestamp);
    }

    @Override
    public long nGetDocExpiration(long c4Collection, @NonNull String docID) {
        return getDocExpiration(c4Collection, docID);
    }

    @Override
    public long nNextDocExpiration(long c4collection) { return nextDocExpiration(c4collection); }

    @Override
    public long nPurgeExpiredDocs(long c4collection) { return purgeExpiredDocs(c4collection); }

    @Override
    public boolean nCreateIndex(
        long c4Collection,
        String name,
        String indexSpec,
        int queryLanguage,
        int indexType,
        String language,
        boolean ignoreDiacritics) {
        return createIndex(c4Collection, name, indexSpec, queryLanguage, indexType, language, ignoreDiacritics);
    }

    @Override
    public boolean nDeleteIndex(long c4Collection, @NonNull String name) { return deleteIndex(c4Collection, name); }

    @Override
    public long nGetIndexesInfo(long c4Collection) { return getIndexesInfo(c4Collection); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------


    private static native long createCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    private static native boolean isValid(long c4Collection);

    private static native long getDatabase(long c4Collection);

    private static native long getDocumentCount(long c4Collection);

    private static native long getLastSequence(long c4Collection);

    private static native long getDoc(long c4Collection, @NonNull String docID, boolean mustExist, long contentLevel);

    private static native long getDocBySequence(long c4Collection, long seq);

    private static native long putDoc(long c4Collection, long request);

    private static native long createDoc(
        long c4Collection,
        @NonNull String docID,
        @NonNull byte[] body,
        int revisionFlags);

    private static native boolean moveDoc(
        long c4Collection,
        @NonNull String docID,
        long toCollection,
        @NonNull String newDocID);

    private static native boolean purgeDoc(long c4Collection, @NonNull String docID);

    private static native boolean setDocExpiration(long c4Collection, @NonNull String docID, long timestamp);

    private static native long getDocExpiration(long c4Collection, @NonNull String docID);

    private static native long nextDocExpiration(long c4Collection);

    private static native long purgeExpiredDocs(long c4Collection);

    private static native boolean createIndex(
        long c4Collection,
        String name,
        String indexSpec,
        int queryLanguage,
        int indexType,
        String language,
        boolean ignoreDiacritics);

    private static native boolean deleteIndex(long c4Collection, @NonNull String name);

    private static native long getIndexesInfo(long c4Collection);
}
