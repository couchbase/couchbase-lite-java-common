//
// Copyright (c) 2020, 2018 Couchbase, Inc All rights reserved.
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


public final class ReplicatedDocument {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final String id;
    @NonNull
    private final EnumSet<DocumentFlag> flags;
    @Nullable
    private final CouchbaseLiteException error;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Document replicated update of a replicator.
     */
    ReplicatedDocument(@NonNull String id, int flags, @Nullable CouchbaseLiteException error, boolean ignore) {
        this.id = id;
        this.error = error;

        this.flags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.RevisionFlags.DELETED) == C4Constants.RevisionFlags.DELETED) {
            this.flags.add(DocumentFlag.DELETED);
        }

        if ((flags & C4Constants.RevisionFlags.PURGED) == C4Constants.RevisionFlags.PURGED) {
            this.flags.add(DocumentFlag.ACCESS_REMOVED);
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * The current document id.
     */
    @NonNull
    public String getID() { return id; }

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

    @NonNull
    @Override
    public String toString() { return "ReplicatedDocument{@" + id + ", " + error + "}"; }
}

