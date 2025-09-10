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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.couchbase.lite.internal.utils.Preconditions;


public class CollectionConfiguration {
    @NonNull
    private Collection collection;
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

    /**
     * Creates a configuration instance.
     *
     * @deprecated This constructor is deprecated. Use {@link #CollectionConfiguration(Collection)}
     * and setter methods to configure channels, filters, and a custom conflict resolver.
     *
     * @param channels           The list of channels to pull from Sync Gateway.
     * @param documentIDs        The list of document IDs to filter replication.
     * @param pullFilter         The filter function for pulling documents.
     * @param pushFilter         The filter function for pushing documents.
     * @param conflictResolver   The custom conflict resolver.
     */
    @Deprecated
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

    /**
     * Creates a new configuration instance for the given collection.
     *
     * <p>Use setter methods to customize the configuration such as channels, filters or
     * a custom conflict resolver.</p>
     *
     * @param collection The {@link Collection} instance to replicate.
     */
    public CollectionConfiguration(@NonNull Collection collection) {
        this.collection = collection;
    }

    /**
     * Internal API.
     * Creates a new {@link CollectionConfiguration} by copying the values from another instance
     * */
    CollectionConfiguration(@NonNull CollectionConfiguration config) {
        this.collection = config.collection;
        this.channels = config.channels != null ? new ArrayList<>(config.channels) : null;
        this.documentIDs = config.documentIDs != null ? new ArrayList<>(config.documentIDs) : null;
        this.pullFilter = config.pullFilter;
        this.pushFilter = config.pushFilter;
        this.conflictResolver = config.conflictResolver;
    }

    //---------------------------------------------
    // Factory
    // ---------------------------------------------

    /**
     * Creates a set of {@link CollectionConfiguration} instances from the given collections.
     *
     * <p>Each specified collection will be wrapped in a {@link CollectionConfiguration}
     * using default settings (no filters and no custom conflict resolvers).</p>
     *
     * <p>This is a convenience method for configuring multiple collections with
     * default replication settings. If custom configurations are needed,
     * construct {@link CollectionConfiguration} instances directly instead.</p>
     *
     * @param collections A collection of {@link Collection} instances to replicate.
     * @return A set of {@link CollectionConfiguration} instances for the provided collections.
     */
    @NonNull
    public static Set<CollectionConfiguration> fromCollections(@NonNull java.util.Collection<Collection> collections) {
        Preconditions.assertNotNull(collections, "collections");
        Preconditions.assertNotEmpty(collections, "collections");
        final Set<CollectionConfiguration> configs = new HashSet<>();
        for (Collection collection : collections) {
            configs.add(new CollectionConfiguration(collection));
        }
        return configs;
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets a collection of document IDs to filter by: if given, only documents
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
     * Sets a collection of Sync Gateway channel names from which to pull Documents.
     * If unset, all accessible channels will be pulled.
     * Default is empty: pull from all accessible channels.
     *
     * Note:  Channel specifications apply only to replications
     * pulling from a SyncGateway and only the channels visible
     * to the authenticated user.  Channel specs are ignored:
     * <ul>
     *     <li>during a push replication.</li>
     *     <li>during peer-to-peer or database-to-database replication</li>
     *     <li>when the specified channel is not accessible to the user</li>
     * </ul>
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
     * Sets the conflict resolver.
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
     * Returns the collection.
     */
    @Nullable
    public final Collection getCollection() {
        return collection;
    }

    /**
     * Sets a collection of Sync Gateway channel names from which to pull Documents.
     * If unset, all accessible channels will be pulled.
     * Default is empty: pull from all accessible channels.
     *
     * Note:  Channel specifications apply only to replications
     * pulling from a SyncGateway and only the channels visible
     * to the authenticated user.  Channel specs are ignored:
     * <ul>
     *     <li>during a push replication.</li>
     *     <li>during peer-to-peer or database-to-database replication</li>
     *     <li>when the specified channel is not accessible to the user</li>
     * </ul>
     */
    @Nullable
    public final List<String> getChannels() { return (channels == null) ? null : new ArrayList<>(channels); }

    /**
     * A collection of document IDs to filter: if not nil, only documents with these IDs will be pushed
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

    @Override
    @NonNull
    public String toString() {
        return "CollectionConfiguration{"
            + "("
            + ((pullFilter != null) ? "<" : "")
            + ((conflictResolver != null) ? "!" : "")
            + ((pushFilter != null) ? ">" : "")
            + "): "
            + channels + ", "
            + documentIDs + "}"
            + (collection != null ? ", collection=" + collection.getFullName() : "");
    }
}
