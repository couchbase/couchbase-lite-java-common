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

import com.couchbase.lite.internal.utils.Preconditions;


// !!! This may need an immutable counterpart.
public class CollectionConfiguration {
    private ReplicatorType type;
    private ReplicationFilter pullFilter;
    private ReplicationFilter pushFilter;
    private ConflictResolver conflictResolver;

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets the replication type type indicating the direction of the replicator for this collection.
     * The default value is .pushAndPull which is bi-directional.
     *
     * @param type The replicator type.
     * @return this.
     */
    @NonNull
    public final CollectionConfiguration setType(@NonNull ReplicatorType type) {
        this.type = Preconditions.assertNotNull(type, "replicator type");
        return this;
    }

    /**
     * Sets a filter object for validating whether the documents can be pulled from the
     * remote endpoint. Only documents for which the object returns true are replicated.
     *
     * @param pullFilter The filter to filter the document to be pulled.
     * @return this.
     */
    @NonNull
    public final CollectionConfiguration setPullFilter(@Nullable ReplicationFilter pullFilter) {
        this.pullFilter = pullFilter;
        return this;
    }

    /**
     * Sets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     *
     * @param pushFilter The filter to filter the document to be pushed.
     * @return this.
     */
    @NonNull
    public final CollectionConfiguration setPushFilter(@Nullable ReplicationFilter pushFilter) {
        this.pushFilter = pushFilter;
        return this;
    }

    /**
     * Sets the the conflict resolver.
     *
     * @param conflictResolver A conflict resolver.
     * @return this.
     */
    @NonNull
    public final CollectionConfiguration setConflictResolver(@Nullable ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
        return this;
    }

    /**
     * Return type type indicating the direction of the replicator for this collection.
     */
    @NonNull
    public final ReplicatorType getType() { return type; }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    @Nullable
    public ReplicationFilter getPullFilter() { return pullFilter; }

    @Nullable
    public ReplicationFilter getPushFilter() { return pushFilter; }

    @Nullable
    public ConflictResolver getConflictResolver() { return conflictResolver; }
}
