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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.internal.Util;

import com.couchbase.lite.internal.BaseReplicatorConfiguration;
import com.couchbase.lite.internal.ImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Replicator configuration.
 */
@SuppressWarnings({"PMD.TooManyFields", "PMD.UnnecessaryFullyQualifiedName", "PMD.CyclomaticComplexity"})
public abstract class AbstractReplicatorConfiguration extends BaseReplicatorConfiguration {
    /**
     * This is a long time: just under 25 days.
     * This many seconds, however, is just less than Integer.MAX_INT millis and will fit in the heartbeat property.
     */
    public static final int DISABLE_HEARTBEAT = 2147483;

    /**
     * Replicator type
     * PUSH_AND_PULL: Bidirectional; both push and pull
     * PUSH: Pushing changes to the target
     * PULL: Pulling changes from the target
     *
     * @deprecated Use com.couchbase.lite.ReplicatorType
     */
    @Deprecated
    public enum ReplicatorType {PUSH_AND_PULL, PUSH, PULL}

    protected static int verifyHeartbeat(int heartbeat) {
        Util.checkDuration("heartbeat", Preconditions.assertNotNegative(heartbeat, "heartbeat"), TimeUnit.SECONDS);
        return heartbeat;
    }

    @Nullable
    protected static Map<Collection, CollectionConfiguration> configureDefaultCollection(@Nullable Database db) {
        if (db == null) { return null; }

        final Collection defaultCollection;
        try { defaultCollection = db.getDefaultCollection(); }
        catch (CouchbaseLiteException e) { throw new IllegalStateException("Failed getting default collection", e); }

        if (defaultCollection == null) {
            throw new IllegalStateException("Database " + db + " has no default collection");
        }

        final Map<Collection, CollectionConfiguration> collections = new HashMap<>();
        collections.put(defaultCollection, new CollectionConfiguration());

        return collections;
    }

    @Nullable
    private static Map<Collection, CollectionConfiguration> copyConfigs(
        @Nullable Map<Collection, CollectionConfiguration> configs) {
        if (configs == null) { return null; }
        final Map<Collection, CollectionConfiguration> configsCopy = new HashMap<>();
        for (Map.Entry<Collection, CollectionConfiguration> config: configs.entrySet()) {
            configsCopy.put(config.getKey(), new CollectionConfiguration(config.getValue()));
        }
        return configsCopy;
    }

    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    @NonNull
    private final Endpoint target;

    @NonNull
    private com.couchbase.lite.ReplicatorType type;
    private boolean continuous;
    @Nullable
    private Authenticator authenticator;
    @Nullable
    private Map<String, String> headers;
    @Nullable
    private X509Certificate pinnedServerCertificate;
    private int maxAttempts;
    private int maxAttemptWaitTime;
    private int heartbeat;
    private boolean enableAutoPurge;

    @Nullable
    private Database database;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    @SuppressWarnings({"PMD.ExcessiveParameterList", "PMD.ArrayIsStoredDirectly"})
    protected AbstractReplicatorConfiguration(
        @Nullable Database db,
        @Nullable Map<Collection, CollectionConfiguration> collections,
        @NonNull Endpoint target) {
        this(
            collections,
            target,
            com.couchbase.lite.ReplicatorType.PUSH_AND_PULL,
            true,
            null,
            null,
            null,
            0,
            0,
            0,
            true,
            db);
    }

    protected AbstractReplicatorConfiguration(@NonNull AbstractReplicatorConfiguration config) {
        this(
            config.collectionConfigurations,
            config.target,
            config.type,
            config.continuous,
            config.authenticator,
            config.headers,
            config.pinnedServerCertificate,
            config.maxAttempts,
            config.maxAttemptWaitTime,
            config.heartbeat,
            config.enableAutoPurge,
            config.database);
    }

    AbstractReplicatorConfiguration(@NonNull ImmutableReplicatorConfiguration config) {
        this(
            config.getCollectionConfigs(),
            config.getTarget(),
            config.getType(),
            config.isContinuous(),
            config.getAuthenticator(),
            config.getHeaders(),
            config.getPinnedServerCertificate(),
            config.getMaxRetryAttempts(),
            config.getMaxRetryAttemptWaitTime(),
            config.getHeartbeat(),
            config.isAutoPurgeEnabled(),
            config.getDatabase());
    }

    // The management of CollectionConfigurations is a bit subtle:
    // Although they are mutable, an AbstractReplicatorConfiguration holds
    // the only reference to its copies (they are copied in and copied out)
    // They are, therefore, effectively immutable
    @SuppressWarnings({"PMD.ExcessiveParameterList", "PMD.ArrayIsStoredDirectly"})
    protected AbstractReplicatorConfiguration(
        @Nullable Map<Collection, CollectionConfiguration> collections,
        @NonNull Endpoint target,
        @NonNull com.couchbase.lite.ReplicatorType type,
        boolean continuous,
        @Nullable Authenticator authenticator,
        @Nullable Map<String, String> headers,
        @Nullable X509Certificate pinnedServerCertificate,
        int maxAttempts,
        int maxAttemptWaitTime,
        int heartbeat,
        boolean enableAutoPurge,
        @Nullable Database database) {
        super(copyConfigs(collections));
        this.target = target;
        this.type = type;
        this.continuous = continuous;
        this.authenticator = authenticator;
        this.headers = headers;
        this.pinnedServerCertificate = pinnedServerCertificate;
        this.maxAttempts = maxAttempts;
        this.maxAttemptWaitTime = maxAttemptWaitTime;
        this.heartbeat = heartbeat;
        this.enableAutoPurge = enableAutoPurge;
        this.database = database;
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Add a collection used for the replication with an optional collection configuration.
     * If the collection has been added before, the previously added collection
     * and its configuration if specified will be replaced.
     *
     * @param collection the collection
     * @param config     its configuration
     * @return this
     */
    @NonNull
    public final ReplicatorConfiguration addCollection(
        @NonNull Collection collection,
        @Nullable CollectionConfiguration config) {
        addCollectionConfig(
            collection,
            (config == null) ? new CollectionConfiguration() : new CollectionConfiguration(config));
        return getReplicatorConfiguration();
    }

    /**
     * Add multiple collections used for the replication with an optional shared collection configuration.
     * If any of the collections have been added before, the previously added collections and their
     * configuration if specified will be replaced. Adding an empty collection array is a no-op.
     *
     * @param collections a collection of Collections
     * @param config      the configuration to be applied to all of the collections
     * @return this
     */
    @NonNull
    public final ReplicatorConfiguration addCollections(
        @NonNull java.util.Collection<Collection> collections,
        @Nullable CollectionConfiguration config) {
        // Use a single config instance for all of the collections
        if (config == null) { config = new CollectionConfiguration(); }
        for (Collection collection: collections) { addCollectionConfig(collection, config); }
        return getReplicatorConfiguration();
    }

    /**
     * Remove a collection from the replication.
     *
     * @param collection the collection to be removed
     * @return this
     */
    @NonNull
    public final ReplicatorConfiguration removeCollection(@NonNull Collection collection) {
        removeCollectionInternal(collection);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the replicator type indicating the direction of the replicator.
     * The default value is .pushAndPull which is bi-directional.
     *
     * @param type The replicator type.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setType(@NonNull com.couchbase.lite.ReplicatorType type) {
        this.type = Preconditions.assertNotNull(type, "replicator type");
        return getReplicatorConfiguration();
    }

    /**
     * Sets whether the replicator stays active indefinitely to replicate
     * changed documents. The default value is false, which means that the
     * replicator will stop after it finishes replicating the changed
     * documents.
     *
     * @param continuous The continuous flag.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setContinuous(boolean continuous) {
        this.continuous = continuous;
        return getReplicatorConfiguration();
    }

    /**
     * Enable/disable auto-purge.
     * Default is enabled.
     */
    @NonNull
    public final ReplicatorConfiguration setAutoPurgeEnabled(boolean enabled) {
        this.enableAutoPurge = enabled;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the extra HTTP headers to send in all requests to the remote target.
     *
     * @param headers The HTTP Headers.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setHeaders(@Nullable Map<String, String> headers) {
        this.headers = (headers == null) ? null : new HashMap<>(headers);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the authenticator to authenticate with a remote target server.
     * Currently there are two types of the authenticators,
     * BasicAuthenticator and SessionAuthenticator, supported.
     *
     * @param authenticator The authenticator.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setAuthenticator(@NonNull Authenticator authenticator) {
        this.authenticator = Preconditions.assertNotNull(authenticator, "authenticator");
        return getReplicatorConfiguration();
    }

    @NonNull
    public final ReplicatorConfiguration setPinnedServerX509Certificate(@Nullable X509Certificate pinnedCert) {
        pinnedServerCertificate = pinnedCert;
        return getReplicatorConfiguration();
    }

    /**
     * Set the max number of retry attempts made after a connection failure.
     * Set to 0 for default values.
     * Set to 1 for no retries.
     *
     * @param maxAttempts max retry attempts
     */
    @NonNull
    public final ReplicatorConfiguration setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Preconditions.assertNotNegative(maxAttempts, "max attempts");
        return getReplicatorConfiguration();
    }

    /**
     * Set the max time between retry attempts (exponential backoff).
     * Set to 0 for default values.
     *
     * @param maxAttemptWaitTime max attempt wait time
     */
    @NonNull
    public final ReplicatorConfiguration setMaxAttemptWaitTime(int maxAttemptWaitTime) {
        this.maxAttemptWaitTime = Preconditions.assertNotNegative(maxAttemptWaitTime, "max attempt wait time");
        return getReplicatorConfiguration();
    }

    /**
     * Set the heartbeat interval, in seconds.
     * Set to 0 for default values
     * <p>
     * Must be non-negative and less than Integer.MAX_VALUE milliseconds
     */
    @NonNull
    public final ReplicatorConfiguration setHeartbeat(int heartbeat) {
        this.heartbeat = verifyHeartbeat(heartbeat);
        return getReplicatorConfiguration();
    }

    /**
     * Old setter for replicator type, indicating the direction of the replicator.
     * The default value is PUSH_AND_PULL which is bi-directional.
     *
     * @param replicatorType The replicator type.
     * @return this.
     * @deprecated Use setType(AbstractReplicator.ReplicatorType)
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setReplicatorType(
        @NonNull AbstractReplicatorConfiguration.ReplicatorType replicatorType) {
        final com.couchbase.lite.ReplicatorType type;
        switch (Preconditions.assertNotNull(replicatorType, "replicator type")) {
            case PUSH_AND_PULL:
                type = com.couchbase.lite.ReplicatorType.PUSH_AND_PULL;
                break;
            case PUSH:
                type = com.couchbase.lite.ReplicatorType.PUSH;
                break;
            case PULL:
                type = com.couchbase.lite.ReplicatorType.PULL;
                break;
            default:
                throw new IllegalStateException("Unrecognized replicator type: " + replicatorType);
        }
        return setType(type);
    }

    /**
     * Sets the target server's SSL certificate.
     *
     * @param pinnedCert the SSL certificate.
     * @return this.
     * @deprecated Please use setPinnedServerCertificate(Certificate)
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setPinnedServerCertificate(@Nullable byte[] pinnedCert) {
        if (pinnedCert == null) { pinnedServerCertificate = null; }
        else {
            try (InputStream is = new ByteArrayInputStream(pinnedCert)) {
                pinnedServerCertificate
                    = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
            }
            catch (IOException | CertificateException e) {
                throw new IllegalArgumentException("Argument could not be parsed as an X509 Certificate", e);
            }
        }

        return getReplicatorConfiguration();
    }

    /**
     * Sets a set of document IDs to filter by: if given, only documents
     * with these IDs will be pushed and/or pulled.
     *
     * @param documentIDs The document IDs.
     * @return this.
     * @deprecated Use Collection.setDocumentIDs
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setDocumentIDs(@Nullable List<String> documentIDs) {
        getDefaultConfig().setDocumentIDs((documentIDs == null) ? null : new ArrayList<>(documentIDs));
        return getReplicatorConfiguration();
    }

    /**
     * Sets a set of Sync Gateway channel names to pull from. Ignored for
     * push replication. If unset, all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored
     * by Sync Gateway.
     *
     * @param channels The Sync Gateway channel names.
     * @return this.
     * @deprecated Use Collection.setChannels
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setChannels(@Nullable List<String> channels) {
        getDefaultConfig().setChannels((channels == null) ? null : new ArrayList<>(channels));
        return getReplicatorConfiguration();
    }

    /**
     * Sets the the conflict resolver.
     *
     * @param conflictResolver A conflict resolver.
     * @return this.
     * @deprecated Use Collection.setConflictResolver
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setConflictResolver(@Nullable ConflictResolver conflictResolver) {
        getDefaultConfig().setConflictResolver(conflictResolver);
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pulled from the
     * remote endpoint. Only documents for which the object returns true are replicated.
     *
     * @param pullFilter The filter to filter the document to be pulled.
     * @return this.
     * @deprecated Use Collection.setPullFilter
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setPullFilter(@Nullable ReplicationFilter pullFilter) {
        getDefaultConfig().setPullFilter(pullFilter);
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     *
     * @param pushFilter The filter to filter the document to be pushed.
     * @return this.
     * @deprecated Use Collection.setPushFilter
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setPushFilter(@Nullable ReplicationFilter pushFilter) {
        getDefaultConfig().setPushFilter(pushFilter);
        return getReplicatorConfiguration();
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return the replication target to replicate with.
     */
    @NonNull
    public final Endpoint getTarget() { return target; }

    /**
     * Get the CollectionConfiguration for the passed Collection.
     *
     * @param collection a collection whose configuration is sought.
     * @return the collections configuration
     */
    @Nullable
    public final CollectionConfiguration getCollectionConfiguration(@NonNull Collection collection) {
        final CollectionConfiguration config = collectionConfigurations.get(collection);
        return (config == null) ? null : new CollectionConfiguration(config);
    }

    /**
     * Return the list of collections in the replicator configuration
     */
    @Nullable
    public final Set<Collection> getCollections() {
        final Set collections = collectionConfigurations.keySet();
        return (collections == null) ? null : new HashSet<>(collections);
    }

    /**
     * Return Replicator type indicating the direction of the replicator.
     */
    @NonNull
    public final com.couchbase.lite.ReplicatorType getType() { return type; }

    /**
     * Return the continuous flag indicating whether the replicator should stay
     * active indefinitely to replicate changed documents.
     */
    public final boolean isContinuous() { return continuous; }

    /**
     * Enable/disable auto-purge.
     * Default is enabled.
     */
    public final boolean isAutoPurgeEnabled() { return enableAutoPurge; }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    @Nullable
    public final Map<String, String> getHeaders() { return (headers == null) ? null : new HashMap<>(headers); }

    /**
     * Return the Authenticator used to authenticate the remote.
     */
    @Nullable
    public final Authenticator getAuthenticator() { return authenticator; }

    /**
     * Return the remote target's SSL certificate.
     */
    @Nullable
    public final X509Certificate getPinnedServerX509Certificate() { return pinnedServerCertificate; }

    /**
     * Return the max number of retry attempts made after connection failure.
     */
    public final int getMaxAttempts() { return maxAttempts; }

    /**
     * Return the max time between retry attempts (exponential backoff).
     *
     * @return max retry wait time
     */
    public final int getMaxAttemptWaitTime() { return maxAttemptWaitTime; }

    /**
     * Return the heartbeat interval, in seconds.
     *
     * @return heartbeat interval in seconds
     */
    public final int getHeartbeat() { return heartbeat; }

    /**
     * Old getter for Replicator type indicating the direction of the replicator.
     *
     * @deprecated Use getType()
     */
    @Deprecated
    @NonNull
    public final AbstractReplicatorConfiguration.ReplicatorType getReplicatorType() {
        switch (type) {
            case PUSH_AND_PULL:
                return AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;
            case PUSH:
                return AbstractReplicatorConfiguration.ReplicatorType.PUSH;
            case PULL:
                return AbstractReplicatorConfiguration.ReplicatorType.PULL;
            default:
                throw new IllegalStateException("Unrecognized replicator type: " + type);
        }
    }

    /**
     * Return the remote target's SSL certificate.
     *
     * @deprecated Use getPinnedServerX509Certificate
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    @Deprecated
    @Nullable
    public final byte[] getPinnedServerCertificate() {
        try { return (pinnedServerCertificate == null) ? null : pinnedServerCertificate.getEncoded(); }
        catch (CertificateEncodingException e) {
            throw new IllegalStateException("Unrecognized certificate encoding", e);
        }
    }

    /**
     * Return the local database to replicate with the replication target.
     *
     * @deprecated Use Collection.getDatabase
     */
    @Deprecated
    @NonNull
    public final Database getDatabase() {
        if (database != null) { return database; }
        throw new IllegalStateException("No database or collections provided for replication configuration");
    }

    /**
     * A set of document IDs to filter: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     *
     * @deprecated Use Collection.getDocumentIDs
     */
    @Deprecated
    @Nullable
    public final List<String> getDocumentIDs() {
        final List<String> docIds = getDefaultConfig().getDocumentIDs();
        return (docIds == null) ? null : new ArrayList<>(docIds);
    }

    /**
     * A set of Sync Gateway channel names to pull from. Ignored for push replication.
     * The default value is null, meaning that all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored by Sync Gateway.
     *
     * @deprecated Use Collection.getChannels
     */
    @Deprecated
    @Nullable
    public final List<String> getChannels() {
        final List<String> channels = getDefaultConfig().getChannels();
        return (channels == null) ? null : new ArrayList<>(channels);
    }

    /**
     * Return the conflict resolver.
     *
     * @deprecated Use Collection.getConflictResolver
     */
    @Deprecated
    @Nullable
    public final ConflictResolver getConflictResolver() { return getDefaultConfig().getConflictResolver(); }

    /**
     * Gets the filter used to determine whether a document will be pulled
     * from the remote endpoint.
     *
     * @deprecated Use Collection.getPullFilter
     */
    @Deprecated
    @Nullable
    public final ReplicationFilter getPullFilter() { return getDefaultConfig().getPullFilter(); }

    /**
     * Gets a filter used to determine whether a document will be pushed
     * to the remote endpoint.
     *
     * @deprecated Use Collection.getPushFilter
     */
    @Deprecated
    @Nullable
    public final ReplicationFilter getPushFilter() { return getDefaultConfig().getPushFilter(); }

    @SuppressWarnings("PMD.NPathComplexity")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("(");

        for (Collection c: collectionConfigurations.keySet()) {
            if (buf.length() > 0) { buf.append(", "); }
            buf.append(c.getScope().getName()).append('.').append(c.getName());
        }
        buf.append(") ");

        if ((type == com.couchbase.lite.ReplicatorType.PULL)
            || (type == com.couchbase.lite.ReplicatorType.PUSH_AND_PULL)) {
            buf.append('<');
        }

        buf.append(continuous ? '*' : '=');

        if ((type == com.couchbase.lite.ReplicatorType.PUSH)
            || (type == com.couchbase.lite.ReplicatorType.PUSH_AND_PULL)) {
            buf.append('>');
        }

        buf.append('(');
        if (authenticator != null) { buf.append('@'); }
        if (pinnedServerCertificate != null) { buf.append('^'); }
        buf.append(") ");

        return "ReplicatorConfig{(" + buf + target + '}';
    }

    //---------------------------------------------
    // Package access
    //---------------------------------------------

    @NonNull
    abstract ReplicatorConfiguration getReplicatorConfiguration();

    //---------------------------------------------
    // Private
    //---------------------------------------------

    private void addCollectionConfig(@NonNull Collection collection, @NonNull CollectionConfiguration config) {
        final Database db = Preconditions.assertNotNull(collection, "collection").getDatabase();
        if (database == null) { database = db; }
        else {
            if (!database.equals(db)) {
                throw new IllegalArgumentException(
                    "Attempt to add a collection from the wrong database: " + db + " != " + database);
            }
            if (!database.isOpen()) {
                throw new IllegalArgumentException("Cannot use a collection from a closed database");
            }
        }

        addCollectionInternal(collection, config);
    }

    @NonNull
    private CollectionConfiguration getDefaultConfig() {
        return Preconditions.assertNotNull(
            collectionConfigurations.get(Fn.firstOrNull(collectionConfigurations.keySet(), Collection::isDefault)),
            "The default collection");
    }
}
