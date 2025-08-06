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

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import okhttp3.internal.Util;

import com.couchbase.lite.internal.BaseReplicatorConfiguration;
import com.couchbase.lite.internal.ImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.CertUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Replicator configuration.
 */
@SuppressWarnings({
    "PMD.ExcessivePublicCount",
    "PMD.TooManyFields",
    "PMD.TooManyMethods",
    "PMD.UnnecessaryFullyQualifiedName",
    "PMD.CyclomaticComplexity"})
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

    // This method uses an internal OkHttp function
    // which is not entirely kosher
    @SuppressWarnings("PMD.PreserveStackTrace")
    private static int verifyHeartbeat(int heartbeat) {
        try { Util.checkDuration("heartbeat", heartbeat, TimeUnit.SECONDS); }
        catch (IllegalStateException e) { throw new IllegalArgumentException(e.getMessage()); }
        return heartbeat;
    }

    @Nullable
    protected static Map<Collection, CollectionConfiguration> configureDefaultCollection(@Nullable Database db) {
        if (db == null) { return null; }

        final Collection defaultCollection;
        try { defaultCollection = db.getDefaultCollection(); }
        catch (CouchbaseLiteException e) {
            throw new CouchbaseLiteError(Log.lookupStandardMessage("NoDefaultCollectionInConfig"), e);
        }

        final Map<Collection, CollectionConfiguration> collections = new HashMap<>();
        collections.put(defaultCollection, new CollectionConfiguration());

        return collections;
    }

    @NonNull
    protected static Map<Collection, CollectionConfiguration> createCollectionConfigMap(
            @NonNull java.util.Collection<CollectionConfiguration> configs) {
        Preconditions.assertNotNull(configs, "collections");
        Preconditions.assertNotEmpty(configs, "collections");

        final Map<Collection, CollectionConfiguration> map = new HashMap<>();
        for (CollectionConfiguration config : configs) {
            final Collection collection = config.getCollection();
            if (collection == null) {
                throw new IllegalArgumentException("Each CollectionConfiguration must have a non-null Collection.");
            }
            map.put(collection, config);
        }
        return map;
    }

    @Nullable
    private static Map<Collection, CollectionConfiguration> copyConfigs(
        @Nullable Map<Collection, CollectionConfiguration> configs) {
        return (configs == null) ? null : new HashMap<>(configs);
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
    private ProxyAuthenticator proxyAuthenticator;
    @Nullable
    private Map<String, String> headers;
    private boolean acceptParentCookies;
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
            Defaults.Replicator.TYPE,
            Defaults.Replicator.CONTINUOUS,
            null,
            null,
            null,
            Defaults.Replicator.ACCEPT_PARENT_COOKIES,
            null,
            Defaults.Replicator.MAX_ATTEMPTS_SINGLE_SHOT,
            Defaults.Replicator.MAX_ATTEMPTS_WAIT_TIME,
            Defaults.Replicator.HEARTBEAT,
            Defaults.Replicator.ENABLE_AUTO_PURGE,
            db);
    }

    protected AbstractReplicatorConfiguration(@NonNull AbstractReplicatorConfiguration config) {
        this(
            config.collectionConfigurations,
            config.target,
            config.type,
            config.continuous,
            config.authenticator,
            config.proxyAuthenticator,
            config.headers,
            config.acceptParentCookies,
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
            config.getProxyAuthenticator(),
            config.getHeaders(),
            config.isAcceptParentCookies(),
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
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private AbstractReplicatorConfiguration(
        @Nullable Map<Collection, CollectionConfiguration> collections,
        @NonNull Endpoint target,
        @NonNull com.couchbase.lite.ReplicatorType type,
        boolean continuous,
        @Nullable Authenticator authenticator,
        @Nullable ProxyAuthenticator proxyAuthenticator,
        @Nullable Map<String, String> headers,
        boolean acceptParentCookies,
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
        this.proxyAuthenticator = proxyAuthenticator;
        this.headers = headers;
        this.acceptParentCookies = acceptParentCookies;
        this.pinnedServerCertificate = pinnedServerCertificate;
        this.maxAttempts = maxAttempts;
        this.maxAttemptWaitTime = maxAttemptWaitTime;
        this.heartbeat = heartbeat;
        this.enableAutoPurge = enableAutoPurge;

        if (database != null) {
            // Using legacy database API or the database has been validated and set
            this.database = database;
        } else if (collections != null) {
            // Get the database from the collection configs if specified
            for (Collection collection : collections.keySet()) {
                if (this.database == null) {
                    this.database = collection.getDatabase();
                } else {
                    if (!this.database.equals(collection.getDatabase())) {
                        throw new IllegalArgumentException(
                                "Use collection " + collection.getFullName() + " from different database");
                    }
                }
            }
        }
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
     *
     * @deprecated Use ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint) instead.
     */
    @Deprecated
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
     *
     * @deprecated Use ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint) instead.
     */
    @Deprecated
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
     *
     * @deprecated Use ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint) instead.
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration removeCollection(@NonNull Collection collection) {
        removeCollectionInternal(collection);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the replicator type indicating the direction of the replicator.
     * The default is ReplicatorType.PUSH_AND_PULL: bi-directional replication.
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
     * Sets whether the replicator stays active indefinitely to replicate changed documents.
     * The default is false: the replicator will stop after it finishes replicating changed documents.
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
     * The default is auto-purge enabled.
     * <p>
     * Note: A document that is blocked by a document Id filter will not be auto-purged
     * regardless of the setting of the auto purge property
     */
    @NonNull
    public final ReplicatorConfiguration setAutoPurgeEnabled(boolean enabled) {
        this.enableAutoPurge = enabled;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the extra HTTP headers to send in all requests to the remote target.
     * The default is no extra headers.
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
     * The option to remove a restriction that does not allow a replicator to accept cookies
     * from a remote host unless the cookie domain exactly matches the the domain of the sender.
     * For instance, when the option is set to false (the default), and the remote host, “bar.foo.com”,
     * sends a cookie for the domain “.foo.com”, the replicator will reject it.  If the option
     * is set true, however, the replicator will accept it.  This is, in general, dangerous:
     * a host might, for instance, set a cookie for the domain ".com".  It is safe only when
     * the replicator is connecting only to known hosts.
     * The default value of this option is false: parent-domain cookies are not accepted
     */
    @NonNull
    public final ReplicatorConfiguration setAcceptParentDomainCookies(boolean acceptParentCookies) {
        this.acceptParentCookies = acceptParentCookies;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the authenticator to authenticate with a remote target server.
     * Currently there are two types of the authenticators,
     * BasicAuthenticator and SessionAuthenticator, supported.
     * The default is no authenticator.
     *
     * @param authenticator The authenticator.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setAuthenticator(@Nullable Authenticator authenticator) {
        this.authenticator = authenticator;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the proxy authenticator to authenticate with the HTTP Proxy.
     * The default is no authenticator.
     *
     * @param authenticator The authenticator.
     * @return this.
     */
    @NonNull
    public ReplicatorConfiguration setProxyAuthenticator(@Nullable ProxyAuthenticator authenticator) {
        proxyAuthenticator = authenticator;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the certificate used to authenticate the target server.
     * A server will be authenticated if it presents a chain of certificates (possibly of length 1)
     * in which any one of the certificates matches the one passed here.
     * The default is no pinned certificate.
     *
     * @param pinnedCert the SSL certificate.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPinnedServerX509Certificate(@Nullable X509Certificate pinnedCert) {
        pinnedServerCertificate = pinnedCert;
        return getReplicatorConfiguration();
    }

    /**
     * Set the max number of retry attempts made after a connection failure.
     * Set to 1 for no retries and to 0 to restore default behavior.
     * The default is 10 total connection attempts (the initial attempt and up to 9 retries) for
     * a one-shot replicator and a very, very large number of retries, for a continuous replicator.
     *
     * @param maxAttempts max retry attempts
     */
    @NonNull
    public final ReplicatorConfiguration setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Preconditions.assertNotNegative(maxAttempts, "max attempts");
        return getReplicatorConfiguration();
    }

    /**
     * Set the max time between retry attempts, in seconds.
     * Time between retries is initially small but backs off exponentially up to this limit.
     * Once the limit is reached the interval between subsequent attempts will be
     * the value set here, until max-attempts attempts have been made.
     * The minimum value legal value is 1 second.
     * The default is 5 minutes (300 seconds).  Setting the parameter to 0 will restore the default
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
     * The default is 5 minutes (300 seconds).  Setting the parameter to 0 will restore the default
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
     * @deprecated Use setType(com.couchbase.lite.ReplicatorType)
     */
    @SuppressWarnings({"DeprecatedIsStillUsed", "deprecation"})
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
                throw new CouchbaseLiteError("Unrecognized replicator type: " + replicatorType);
        }
        return setType(type);
    }

    /**
     * Sets the target server's SSL certificate.
     * The default is no pinned cert.
     *
     * @param pinnedCert the SSL certificate.
     * @return this.
     * @deprecated Use setPinnedServerX509Certificate(Certificate)
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setPinnedServerCertificate(@Nullable byte[] pinnedCert) {
        if (pinnedCert == null) { pinnedServerCertificate = null; }
        else {
            try { pinnedServerCertificate = CertUtils.createCertificate(pinnedCert); }
            catch (CertificateException e) {
                throw new IllegalArgumentException("Argument could not be parsed as an X509 Certificate", e);
            }
        }

        return getReplicatorConfiguration();
    }

    /**
     * A collection of document IDs identifying documents to be replicated.
     * If non-empty, only documents with IDs in this collection will be pushed and/or pulled.
     * Default is empty: do not filter documents.
     *
     * @param documentIDs The document IDs.
     * @return this.
     * @deprecated Use CollectionConfiguration.setDocumentIDs
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setDocumentIDs(@Nullable List<String> documentIDs) {
        updateValidDefaultConfigOrThrow(
            config -> config.setDocumentIDs((documentIDs == null) ? null : new ArrayList<>(documentIDs)));
        return getReplicatorConfiguration();
    }

    /**
     * Sets a collection of Sync Gateway channel names from which to pull Documents.
     * If unset, all accessible channels will be pulled.
     * Default is empty: pull from all accessible channels.
     * <p>
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
     * @deprecated Use CollectionConfiguration.setChannels
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setChannels(@Nullable List<String> channels) {
        updateValidDefaultConfigOrThrow(
            config -> config.setChannels((channels == null) ? null : new ArrayList<>(channels)));
        return getReplicatorConfiguration();
    }

    /**
     * Sets the the conflict resolver.
     * Default is <code>ConflictResolver.DEFAULT</code>
     *
     * @param conflictResolver A conflict resolver.
     * @return this.
     * @deprecated Use CollectionConfiguration.setConflictResolver
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setConflictResolver(@Nullable ConflictResolver conflictResolver) {
        updateValidDefaultConfigOrThrow(config -> config.setConflictResolver(conflictResolver));
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pulled from the
     * remote endpoint. Only documents for which the object returns true are replicated.
     * Default is no filter.
     *
     * @param pullFilter The filter to filter the document to be pulled.
     * @return this.
     * @deprecated Use CollectionConfiguration.setPullFilter
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setPullFilter(@Nullable ReplicationFilter pullFilter) {
        updateValidDefaultConfigOrThrow(config -> config.setPullFilter(pullFilter));
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     * Default is no filter.
     *
     * @param pushFilter The filter to filter the document to be pushed.
     * @return this.
     * @deprecated Use CollectionConfiguration.setPushFilter
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setPushFilter(@Nullable ReplicationFilter pushFilter) {
        updateValidDefaultConfigOrThrow(config -> config.setPushFilter(pushFilter));
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
     *
     * @deprecated Use getCollectionConfigs() instead.
     */
    @Deprecated
    @Nullable
    public final CollectionConfiguration getCollectionConfiguration(@NonNull Collection collection) {
        final CollectionConfiguration config = collectionConfigurations.get(collection);
        return (config == null) ? null : new CollectionConfiguration(config);
    }

    /**
     * Return the list of collections in the replicator configuration
     *
     * @deprecated Use getCollectionConfigs() instead.
     */
    @Deprecated
    @NonNull
    public final Set<Collection> getCollections() { return new HashSet<>(collectionConfigurations.keySet()); }

    /**
     * Returns a copy of the collection configurations associated with this replicator configuration.
     *
     * @return a set of {@link CollectionConfiguration} objects.
     */
    @NonNull
    public final Set<CollectionConfiguration> getCollectionConfigs() {
        return new HashSet<>(collectionConfigurations.values());
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
     * <p>
     * Note: A document that is blocked by a document Id filter will not be auto-purged
     * regardless of the setting of the auto purge property
     */
    public final boolean isAutoPurgeEnabled() { return enableAutoPurge; }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    @Nullable
    public final Map<String, String> getHeaders() { return (headers == null) ? null : new HashMap<>(headers); }

    /**
     * The option to remove a restriction that does not allow a replicator to accept cookies
     * from a remote host unless the cookie domain exactly matches the the domain of the sender.
     * For instance, when the option is set to false (the default), and the remote host, “bar.foo.com”,
     * sends a cookie for the domain “.foo.com”, the replicator will reject it.  If the option
     * is set true, however, the replicator will accept it.  This is, in general, dangerous:
     * a host might, for instance, set a cookie for the domain ".com".  It is safe only when
     * the replicator is connecting only to known hosts.
     * The default value of this option is false: parent-domain cookies are not accepted
     */
    public final boolean isAcceptParentDomainCookies() { return acceptParentCookies; }

    /**
     * Return the Authenticator used to authenticate the remote.
     */
    @Nullable
    public final Authenticator getAuthenticator() { return authenticator; }

    /**
     * Returns the proxy authenticator.
     *
     * @return the proxy authenticator or null.
     */
    @Nullable
    public ProxyAuthenticator getProxyAuthenticator() { return proxyAuthenticator; }

    /**
     * Return the remote target's SSL certificate.
     */
    @Nullable
    public final X509Certificate getPinnedServerX509Certificate() { return pinnedServerCertificate; }

    /**
     * Return the max number of retry attempts made after connection failure.
     * This method will return 0 when implicitly using the default:
     * 10 total connection attempts (the initial attempt and up to 9 retries) for
     * a one-shot replicator and a very, very large number of retries, for a continuous replicator.
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
     * @deprecated Use com.couchbase.lite.ReplicatorType ReplicatorConfiguration.getType()
     */
    @SuppressWarnings({"DeprecatedIsStillUsed", "deprecation"})
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
                throw new CouchbaseLiteError("Unrecognized replicator type: " + type);
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
            throw new CouchbaseLiteError("Unrecognized certificate encoding", e);
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
        // Can't change the nullity of this method: it has to throw.
        throw new CouchbaseLiteError("No database or collections provided for replication configuration");
    }

    /**
     * A collection of document IDs to filter: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     *
     * @deprecated Use CollectionConfiguration.getDocumentIDs
     */
    @Deprecated
    @Nullable
    public final List<String> getDocumentIDs() {
        final List<String> docIds = getValidDefaultConfigOrThrow().getDocumentIDs();
        return (docIds == null) ? null : new ArrayList<>(docIds);
    }

    /**
     * Gets the collection of Sync Gateway channel names from which to pull documents.
     * If unset, all accessible channels will be pulled.
     * Default is empty: pull from all accessible channels.
     * <p>
     * Note:  Channel specifications apply only to replications
     * pulling from a SyncGateway and only the channels visible
     * to the authenticated user.  Channel specs are ignored:
     * <ul>
     *     <li>during a push replication.</li>
     *     <li>during peer-to-peer or database-to-database replication</li>
     *     <li>when the specified channel is not accessible to the user</li>
     * </ul>
     *
     * @deprecated Use CollectionConfiguration.getChannels
     */
    @Deprecated
    @Nullable
    public final List<String> getChannels() {
        final List<String> channels = getValidDefaultConfigOrThrow().getChannels();
        return (channels == null) ? null : new ArrayList<>(channels);
    }

    /**
     * Return the conflict resolver.
     *
     * @deprecated Use CollectionConfiguration.getConflictResolver
     */
    @Deprecated
    @Nullable
    public final ConflictResolver getConflictResolver() { return getValidDefaultConfigOrThrow().getConflictResolver(); }

    /**
     * Gets the filter used to determine whether a document will be pulled
     * from the remote endpoint.
     *
     * @deprecated Use CollectionConfiguration.getPullFilter
     */
    @Deprecated
    @Nullable
    public final ReplicationFilter getPullFilter() { return getValidDefaultConfigOrThrow().getPullFilter(); }

    /**
     * Gets a filter used to determine whether a document will be pushed
     * to the remote endpoint.
     *
     * @deprecated Use CollectionConfiguration.getPushFilter
     */
    @Deprecated
    @Nullable
    public final ReplicationFilter getPushFilter() { return getValidDefaultConfigOrThrow().getPushFilter(); }

    @SuppressWarnings("PMD.NPathComplexity")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("(");

        for (Collection c: collectionConfigurations.keySet()) {
            if (buf.length() > 1) { buf.append(", "); }
            buf.append(c.getScope().getName()).append('.').append(c.getName());
        }
        buf.append(") ");

        if ((type == com.couchbase.lite.ReplicatorType.PULL)
            || (type == com.couchbase.lite.ReplicatorType.PUSH_AND_PULL)) {
            buf.append('<');
        }

        buf.append(continuous ? '*' : 'o');

        if ((type == com.couchbase.lite.ReplicatorType.PUSH)
            || (type == com.couchbase.lite.ReplicatorType.PUSH_AND_PULL)) {
            buf.append('>');
        }

        if (authenticator != null) { buf.append('@'); }
        if (pinnedServerCertificate != null) { buf.append('^'); }
        buf.append(' ');

        return "ReplicatorConfig{" + buf + target + '}';
    }

    //---------------------------------------------
    // Package access
    //---------------------------------------------

    @NonNull
    abstract ReplicatorConfiguration getReplicatorConfiguration();

    //---------------------------------------------
    // Private
    //---------------------------------------------

    // I think this is a Spotbugs bug: it claims something is null on line 800git
    @SuppressFBWarnings(
        {"NP_NULL_ON_SOME_PATH", "NP_LOAD_OF_KNOWN_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE"})
    private void addCollectionConfig(@NonNull Collection collection, @NonNull CollectionConfiguration config) {
        Preconditions.assertThat(
                config.getCollection() == null || config.getCollection() == collection,
                "CollectionConfiguration collection must be null or match the given collection."
        );
        final Database db = Preconditions.assertNotNull(collection, "collection").getDatabase();
        if (database == null) { database = db; }
        else {
            if (!database.equals(db)) {
                throw new IllegalArgumentException(
                    Log.formatStandardMessage("AddCollectionFromAnotherDB", collection.toString(), database.getName()));
            }
        }

        if (!database.isOpen()) {
            throw new IllegalArgumentException(
                Log.formatStandardMessage("AddCollectionFromClosedDB", collection.toString(), database.getName()));
        }

        try (Collection coll = database.getCollection(collection.getName(), collection.getScope().getName())) {
            if (coll == null) {
                throw new IllegalArgumentException(
                    Log.formatStandardMessage("AddDeletedCollection", collection.toString()));
            }
        }
        catch (CouchbaseLiteException e) {
            throw new IllegalArgumentException("Failed getting collection " + collection, e);
        }

        addCollectionInternal(collection, config);
    }

    @NonNull
    private CollectionConfiguration getValidDefaultConfigOrThrow() { return getAndUpdateDefaultConfig(null); }

    private void updateValidDefaultConfigOrThrow(@NonNull Fn.Consumer<CollectionConfiguration> updater) {
        getAndUpdateDefaultConfig(updater);
    }

    @NonNull
    private CollectionConfiguration getAndUpdateDefaultConfig(@Nullable Fn.Consumer<CollectionConfiguration> updater) {
        final Collection defaultCollection = Fn.firstOrNull(collectionConfigurations.keySet(), Collection::isDefault);
        if (defaultCollection == null) {
            throw new IllegalArgumentException("Cannot use legacy parameters when there is no default collection");
        }

        CollectionConfiguration config = collectionConfigurations.get(defaultCollection);
        if (config == null) {
            throw new IllegalArgumentException(
                "Cannot use legacy parameters when the default collection has no configuration");
        }

        // Copy on write...
        if (updater != null) {
            config = new CollectionConfiguration(config);
            updater.accept(config);
            addCollectionInternal(defaultCollection, config);
        }

        return config;
    }
}
