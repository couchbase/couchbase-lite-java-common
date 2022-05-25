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


@SuppressWarnings("PMD.UnusedPrivateMethod")
public class NativeC4Collection {

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long getDefaultCollection(long c4Db);

    private static native boolean hasCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    private static native long getCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    private static native long createCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    private static native boolean deleteCollection(long c4Db, @Nullable String scope, @NonNull String collection);

    @NonNull
    private static native Object getCollectionNames(long c4Db, @NonNull String inScope);

    @NonNull
    private static native Object getScopeNames(long c4Db);

    private static native boolean isValid(long c4collection);

    private static native long getDatabase(long c4collection);

    private static native long getDocumentCount(long c4collection);

    private static native long getLastSequence(long c4Db);

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

    private static native long nextDocExpiration(long c4Db);

    private static native long purgeExpiredDocs(long c4Db);

    private static native boolean createIndex(
        long c4Collection,
        String name,
        String indexSpec,
        int queryLanguage,
        int indexType,
        byte[] indexOptions);

    private static native boolean deleteIndex(long c4Collection, @NonNull String name);

    private static native long getIndexesInfo(long c4Collection);
}
