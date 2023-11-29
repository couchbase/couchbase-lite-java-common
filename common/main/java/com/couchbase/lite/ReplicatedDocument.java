//
// Copyright (c) 2020 Couchbase, Inc.
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

package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;

import com.couchbase.lite.internal.core.C4Constants;


/**
 * Information about a Document updated by replication.
 */
public final class ReplicatedDocument {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final String scope;
    @NonNull
    private final String name;
    @NonNull
    private final String docId;
    @NonNull
    private final EnumSet<DocumentFlag> flags;
    @Nullable
    private volatile CouchbaseLiteException error;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    ReplicatedDocument(
        @NonNull String scope,
        @NonNull String name,
        @NonNull String docId,
        int flags,
        @Nullable CouchbaseLiteException error) {
        this.scope = scope;
        this.name = name;
        this.docId = docId;

        this.error = error;

        this.flags = EnumSet.noneOf(DocumentFlag.class);
        if (C4Constants.hasFlags(flags, C4Constants.RevisionFlags.DELETED)) { this.flags.add(DocumentFlag.DELETED); }
        if (C4Constants.hasFlags(flags, C4Constants.RevisionFlags.PURGED)) {
            this.flags.add(DocumentFlag.ACCESS_REMOVED);
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * The scope of the collection to which the changed document belongs.
     *
     * @deprecated Use getScope()
     */
    @Deprecated
    @NonNull
    public String getCollectionScope() { return scope; }

    /**
     * The scope of the collection to which the changed document belongs.
     */
    @NonNull
    public String getScope() { return scope; }

    /**
     * The name of the collection to which the changed document belongs.
     *
     * @deprecated Use getName()
     */
    @Deprecated
    @NonNull
    public String getCollectionName() { return name; }

    /**
     * The name of the collection to which the changed document belongs.
     */
    @NonNull
    public String getCollection() { return name; }

    /**
     * The id document of the changed document.
     */
    @NonNull
    public String getID() { return docId; }

    /**
     * The current status flag of the document. eg. deleted, access removed
     */
    @NonNull
    public EnumSet<DocumentFlag> getFlags() { return flags; }

    /**
     * The current document replication error.
     */
    @Nullable
    public CouchbaseLiteException getError() { return error; }

    /**
     * Set the current document replication error.
     */
    public void setError(@Nullable CouchbaseLiteException error) { this.error = error; }

    @NonNull
    @Override
    public String toString() { return "ReplicatedDocument{@" + docId + ", " + error + "}"; }
}

