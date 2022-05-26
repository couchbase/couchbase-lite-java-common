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

    // Collections

    @Override
    public long nGetDefaultCollection(long c4Db) { return getDefaultCollection(c4Db); }

    @Override
    public long nGetCollection(long c4Db, @NonNull String scope, @NonNull String collection) {
        return getCollection(c4Db, scope, collection);
    }

    @Override
    public long nCreateCollection(long c4Db, @Nullable String scope, @NonNull String collection) {
        return createCollection(c4Db, scope, collection);
    }

    @Override
    public boolean nCollectionIsValid(long peer) { return isValid(peer); }


    @Override
    public long nGetDocumentCount(long peer) { return getDocumentCount(peer); }

    @Override
    public long nGetLastSequence(long peer) { return getLastSequence(peer); }

    // Documents

    @Override
    public long nGetDoc(long peer, @NonNull String docID, boolean mustExist) { return getDoc(peer, docID, mustExist); }

    @Override
    public long nGetDocExpiration(long peer, @NonNull String docID) { return getDocExpiration(peer, docID); }

    @Override
    public boolean nSetDocExpiration(long peer, @NonNull String docID, long timestamp) {
        return setDocExpiration(peer, docID, timestamp);
    }

    @Override
    public boolean nDeleteDoc(long peer, @NonNull String docID) { return deleteDoc(peer, docID); }

    @Override
    public boolean nPurgeDoc(long peer, @NonNull String docID) { return purgeDoc(peer, docID); }

    // Indexes

    @Override
    public long nGetIndexesInfo(long peer) { return getIndexesInfo(peer); }

    @Override
    public boolean nCreateIndex(
        long peer,
        String name,
        String indexSpec,
        int queryLanguage,
        int indexType,
        String language,
        boolean ignoreDiacritics) {
        return createIndex(peer, name, indexSpec, queryLanguage, indexType, language, ignoreDiacritics);
    }

    @Override
    public boolean nDeleteIndex(long peer, @NonNull String name) { return deleteIndex(peer, name); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long getDefaultCollection(long c4Db);

    private static native long getCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    private static native long createCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    private static native boolean isValid(long peer);

    private static native long getDocumentCount(long peer);

    private static native long getLastSequence(long peer);

    private static native long getDoc(long peer, @NonNull String docID, boolean mustExist);

    private static native boolean setDocExpiration(long poeer, @NonNull String docID, long timestamp);

    private static native long getDocExpiration(long peer, @NonNull String docID);

    private static native boolean deleteDoc(long peer, @NonNull String docID);

    private static native boolean purgeDoc(long peer, @NonNull String docID);


    private static native long getIndexesInfo(long peer);

    private static native boolean createIndex(
        long peer,
        String name,
        String indexSpec,
        int queryLanguage,
        int indexType,
        String language,
        boolean ignoreDiacritics);

    private static native boolean deleteIndex(long peer, @NonNull String name);
}
