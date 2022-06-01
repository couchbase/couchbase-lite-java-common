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

import java.util.ArrayList;
import java.util.List;


public class CollectionConfiguration {
    @Nullable
    private List<String> channels;
    @Nullable
    private List<String> documentIDs;
    @Nullable
    private ReplicationFilter pullFilter;
    @Nullable
    private ReplicationFilter pushFilter;
    @Nullable
    private ConflictResolver conflictResolver;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public CollectionConfiguration() { }

    CollectionConfiguration(@NonNull CollectionConfiguration config) {
        this.channels = config.channels;
        this.documentIDs = config.documentIDs;
        this.pullFilter = config.pullFilter;
        this.pushFilter = config.pushFilter;
        this.conflictResolver = config.conflictResolver;
    }

    public CollectionConfiguration(
        @Nullable List<String> channels,
        @Nullable List<String> documentIDs,
        @Nullable ReplicationFilter pullFilter,
        @Nullable ReplicationFilter pushFilter,
        @Nullable ConflictResolver conflictResolver) {
        this.channels = channels;
        this.documentIDs = documentIDs;
        this.pullFilter = pullFilter;
        this.pushFilter = pushFilter;
        this.conflictResolver = conflictResolver;
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets a set of document IDs to filter by: if given, only documents
     * with these IDs will be pushed and/or pulled.
     *
     * @param documentIDs The document IDs.
     * @return this.
     */
    @NonNull
    public final CollectionConfiguration setDocumentIDs(@Nullable List<String> documentIDs) {
        this.documentIDs = (documentIDs == null) ? null : new ArrayList<>(documentIDs);
        return this;
    }

    /**
     * Sets a set of Sync Gateway channel names to pull from. Ignored for
     * push replication. If unset, all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored
     * by Sync Gateway.
     *
     * @param channels The Sync Gateway channel names.
     * @return this.
     */
    @NonNull
    public final CollectionConfiguration setChannels(@Nullable List<String> channels) {
        this.channels = (channels == null) ? null : new ArrayList<>(channels);
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

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * A set of Sync Gateway channel names to pull from. Ignored for push replication.
     * The default value is null, meaning that all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored by Sync Gateway.
     */
    @Nullable
    public final List<String> getChannels() { return (channels == null) ? null : new ArrayList<>(channels); }

    /**
     * A set of document IDs to filter: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     */
    @Nullable
    public final List<String> getDocumentIDs() { return (documentIDs == null) ? null : new ArrayList<>(documentIDs); }

    /**
     * Return the conflict resolver.
     */
    @Nullable
    public ConflictResolver getConflictResolver() { return conflictResolver; }

    /**
     * Gets the filter used to determine whether a document will be pulled
     * from the remote endpoint.
     */
    @Nullable
    public ReplicationFilter getPullFilter() { return pullFilter; }

    /**
     * Gets the filter used to determine whether a document will be pushed
     * to the remote endpoint.
     */
    @Nullable
    public ReplicationFilter getPushFilter() { return pushFilter; }
}
