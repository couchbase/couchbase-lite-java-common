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

import java.util.List;


/**
 * The representation of the replication of a document.
 */
public final class DocumentReplication {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final Replicator replicator;
    @NonNull
    private final List<ReplicatedDocument> documents;
    private final boolean pushing;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    DocumentReplication(@NonNull Replicator replicator, boolean isPush, @NonNull List<ReplicatedDocument> documents) {
        this.replicator = replicator;
        this.pushing = isPush;
        this.documents = documents;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the source replicator.
     */
    @NonNull
    public Replicator getReplicator() { return replicator; }

    /**
     * The direction of replication for the affected documents.
     */
    public boolean isPush() { return pushing; }

    /**
     * The list of affected documents.
     */
    @NonNull
    public List<ReplicatedDocument> getDocuments() { return documents; }

    @Override
    @NonNull
    public String toString() { return "DocumentReplication{#" + documents.size() + " @" + replicator + "}"; }
}
