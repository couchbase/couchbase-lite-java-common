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

import com.couchbase.lite.internal.replicator.ReplicationStatusChange;


/**
 * ReplicatorChange contains the replicator status information.
 */
public final class ReplicatorChange extends ReplicationStatusChange {
    @NonNull
    private final Replicator replicator;

    ReplicatorChange(@NonNull Replicator replicator, @NonNull ReplicatorStatus status) {
        super(status);
        this.replicator = replicator;
    }

    /**
     * Return the source replicator object.
     */
    @NonNull
    public Replicator getReplicator() { return replicator; }

    @NonNull
    @Override
    public String toString() {return "ReplicatorChange{" + replicator + " => " + status + '}'; }
}
