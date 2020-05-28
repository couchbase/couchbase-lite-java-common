//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Replicator configuration.
 */
@SuppressWarnings("LineLength")
abstract class AbstractReplicatorConfiguration {

    // @formatter:off
    // Replicator option dictionary keys:
    static final String REPLICATOR_OPTION_EXTRA_HEADERS = "headers";  // Extra HTTP headers: string[]
    static final String REPLICATOR_OPTION_COOKIES = "cookies";  // HTTP Cookie header value: string
    static final String REPLICATOR_AUTH_OPTION = "auth";       // Auth settings: Dict
    static final String REPLICATOR_OPTION_PINNED_SERVER_CERT = "pinnedCert";  // Cert or public key: data
    static final String REPLICATOR_OPTION_DOC_IDS = "docIDs";   // Docs to replicate: string[]
    static final String REPLICATOR_OPTION_CHANNELS = "channels"; // SG channel names: string[]
    static final String REPLICATOR_OPTION_FILTER = "filter";   // Filter name: string
    static final String REPLICATOR_OPTION_FILTER_PARAMS = "filterParams";  // Filter params: Dict[string]
    static final String REPLICATOR_OPTION_SKIP_DELETED = "skipDeleted"; // Don't push/pull tombstones: bool
    static final String REPLICATOR_OPTION_NO_CONFLICTS = "noConflicts"; // Puller rejects conflicts: bool
    static final String REPLICATOR_OPTION_CHECKPOINT_INTERVAL = "checkpointInterval"; // How often to checkpoint, in seconds: number
    static final String REPLICATOR_OPTION_REMOTE_DB_UNIQUE_ID = "remoteDBUniqueID"; // How often to checkpoint, in seconds: number
    static final String REPLICATOR_RESET_CHECKPOINT = "reset"; // reset remote checkpoint
    static final String REPLICATOR_OPTION_PROGRESS_LEVEL = "progress";  //< If >=1, notify on every doc; if >=2, on every attachment (int)

    // Auth dictionary keys:
    static final String REPLICATOR_AUTH_TYPE = "type"; // Auth property: string
    static final String REPLICATOR_AUTH_USER_NAME = "username"; // Auth property: string
    static final String REPLICATOR_AUTH_PASSWORD = "password"; // Auth property: string
    static final String REPLICATOR_AUTH_CLIENT_CERT = "clientCert"; // Auth property: value platform-dependent

    // auth.type values:
    static final String AUTH_TYPE_BASIC = "Basic"; // HTTP Basic (the default)
    static final String AUTH_TYPE_SESSION = "Session"; // SG session cookie
    static final String AUTH_TYPE_OPEN_ID_CONNECT = "OpenID Connect";
    static final String AUTH_TYPE_FACEBOOK = "Facebook";
    static final String AUTH_TYPE_CLIENT_CERT = "Client Cert";
    // @formatter:on

    /**
     * Replicator type
     * PUSH_AND_PULL: Bidirectional; both push and pull
     * PUSH: Pushing changes to the target
     * PULL: Pulling changes from the target
     */
    public enum ReplicatorType {PUSH_AND_PULL, PUSH, PULL}

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final Database database;
    @NonNull
    private ReplicatorType replicatorType;
    private boolean continuous;
    @Nullable
    private Authenticator authenticator;
    @Nullable
    private Map<String, String> headers;
    @Nullable
    private byte[] pinnedServerCertificate;
    @Nullable
    private List<String> channels;
    @Nullable
    private List<String> documentIDs;
    @Nullable
    private ReplicationFilter pushFilter;
    @Nullable
    private ReplicationFilter pullFilter;
    @NonNull
    private ServerCertificateVerificationMode certificateVerificationMode
        = ServerCertificateVerificationMode.CA_CERT;
    @Nullable
    private ConflictResolver conflictResolver;

    protected boolean readonly;
    protected final Endpoint target;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    AbstractReplicatorConfiguration(@NonNull AbstractReplicatorConfiguration config) {
        Preconditions.assertNotNull(config, "config");

        this.readonly = false;
        this.database = config.database;
        this.target = config.target;
        this.replicatorType = config.replicatorType;
        this.continuous = config.continuous;
        this.authenticator = config.authenticator;
        this.pinnedServerCertificate = config.pinnedServerCertificate;
        this.headers = config.headers;
        this.channels = config.channels;
        this.documentIDs = config.documentIDs;
        this.pullFilter = config.pullFilter;
        this.pushFilter = config.pushFilter;
        this.conflictResolver = config.conflictResolver;
        this.certificateVerificationMode = config.certificateVerificationMode;
    }

    protected AbstractReplicatorConfiguration(@NonNull Database database, @NonNull Endpoint target) {
        this.database = Preconditions.assertNotNull(database, "database");
        this.target = Preconditions.assertNotNull(target, "target");
        this.readonly = false;
        this.replicatorType = ReplicatorType.PUSH_AND_PULL;
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

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
        checkReadOnly();
        this.authenticator = Preconditions.assertNotNull(authenticator, "authenticator");
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
     */
    @NonNull
    public final ReplicatorConfiguration setChannels(@Nullable List<String> channels) {
        checkReadOnly();
        this.channels = channels;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the the conflict resolver.
     *
     * @param conflictResolver The replicator type.
     * @return this.
     */
    @Nullable
    public final ReplicatorConfiguration setConflictResolver(@Nullable ConflictResolver conflictResolver) {
        checkReadOnly();
        this.conflictResolver = conflictResolver;
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
        checkReadOnly();
        this.continuous = continuous;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a set of document IDs to filter by: if given, only documents
     * with these IDs will be pushed and/or pulled.
     *
     * @param documentIDs The document IDs.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setDocumentIDs(@Nullable List<String> documentIDs) {
        checkReadOnly();
        this.documentIDs = documentIDs;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the extra HTTP headers to send in all requests to the remote target.
     *
     * @param headers The HTTP Headers.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setHeaders(@NonNull Map<String, String> headers) {
        checkReadOnly();
        this.headers = new HashMap<>(headers);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the target server's SSL certificate.
     *
     * @param pinnedCert the SSL certificate.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPinnedServerCertificate(@Nullable byte[] pinnedCert) {
        checkReadOnly();

        if (pinnedCert == null) { pinnedServerCertificate = null; }
        else {
            pinnedServerCertificate = new byte[pinnedCert.length];
            System.arraycopy(pinnedCert, 0, pinnedServerCertificate, 0, pinnedServerCertificate.length);
        }

        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pulled from the
     * remote endpoint. Only documents for which the object returns true are replicated.
     *
     * @param pullFilter The filter to filter the document to be pulled.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPullFilter(@Nullable ReplicationFilter pullFilter) {
        checkReadOnly();
        this.pullFilter = pullFilter;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     *
     * @param pushFilter The filter to filter the document to be pushed.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPushFilter(ReplicationFilter pushFilter) {
        checkReadOnly();
        this.pushFilter = pushFilter;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the replicator type indicating the direction of the replicator.
     * The default value is .pushAndPull which is bi-directional.
     *
     * @param replicatorType The replicator type.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setReplicatorType(@NonNull ReplicatorType replicatorType) {
        checkReadOnly();
        this.replicatorType = Preconditions.assertNotNull(replicatorType, "replicatorType");
        return getReplicatorConfiguration();
    }

    /**
     * Sets the replicator verification mode.
     * The default value is ServerCertificateVerificationMode.CA_CERT.
     *
     * @param mode Specifies the way the replicator verifies the server identity when using TLS communication
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setServerCertificateVerificationMode(
        @NonNull ServerCertificateVerificationMode mode) {
        checkReadOnly();
        this.certificateVerificationMode = Preconditions.assertNotNull(mode, "certificate verification mode");
        return getReplicatorConfiguration();
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return the Authenticator to authenticate with a remote target.
     */
    @Nullable
    public final Authenticator getAuthenticator() { return authenticator; }

    /**
     * A set of Sync Gateway channel names to pull from. Ignored for push replication.
     * The default value is null, meaning that all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored by Sync Gateway.
     */
    @Nullable
    public final List<String> getChannels() { return channels; }

    /**
     * Return the conflict resolver.
     */
    @Nullable
    public final ConflictResolver getConflictResolver() { return conflictResolver; }

    /**
     * Return the continuous flag indicating whether the replicator should stay
     * active indefinitely to replicate changed documents.
     */
    public final boolean isContinuous() { return continuous; }

    /**
     * Return the local database to replicate with the replication target.
     */
    @NonNull
    public final Database getDatabase() { return database; }

    /**
     * A set of document IDs to filter by: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     */
    @Nullable
    public final List<String> getDocumentIDs() { return documentIDs; }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    @Nullable
    public final Map<String, String> getHeaders() { return headers; }

    /**
     * Return the remote target's SSL certificate.
     */
    @Nullable
    public final byte[] getPinnedServerCertificate() {
        if (pinnedServerCertificate == null) { return null; }
        final byte[] pinnedCert = new byte[pinnedServerCertificate.length];
        System.arraycopy(pinnedServerCertificate, 0, pinnedCert, 0, pinnedCert.length);
        return pinnedCert;
    }

    /**
     * Gets a filter object for validating whether the documents can be pulled
     * from the remote endpoint.
     */
    @Nullable
    public final ReplicationFilter getPullFilter() { return pullFilter; }

    /**
     * Gets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     */
    @Nullable
    public final ReplicationFilter getPushFilter() { return pushFilter; }

    /**
     * Return Replicator type indicating the direction of the replicator.
     */
    @NonNull
    public final ReplicatorType getReplicatorType() { return replicatorType; }

    /**
     * Return the replication target to replicate with.
     */
    @NonNull
    public final Endpoint getTarget() { return target; }

    /**
     * Return the replicator verification mode.
     */
    @NonNull
    public final ServerCertificateVerificationMode getServerCertificateVerificationMode() {
        return certificateVerificationMode;
    }

    @NonNull
    @Override
    public String toString() { return "ReplicatorConfig{" + database + " => " + target + "}"; }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    abstract ReplicatorConfiguration getReplicatorConfiguration();

    boolean isPush() {
        return replicatorType == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL
            || replicatorType == ReplicatorConfiguration.ReplicatorType.PUSH;
    }

    boolean isPull() {
        return replicatorType == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL
            || replicatorType == ReplicatorConfiguration.ReplicatorType.PULL;
    }

    final ReplicatorConfiguration readonlyCopy() {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(getReplicatorConfiguration());
        config.readonly = true;
        return config;
    }

    final Map<String, Object> effectiveOptions() {
        final Map<String, Object> options = new HashMap<>();

        if (authenticator != null) { authenticator.authenticate(options); }

        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null) {
            options.put(REPLICATOR_OPTION_PINNED_SERVER_CERT, pinnedServerCertificate);
        }

        if ((documentIDs != null) && (!documentIDs.isEmpty())) { options.put(REPLICATOR_OPTION_DOC_IDS, documentIDs); }

        if ((channels != null) && (!channels.isEmpty())) { options.put(REPLICATOR_OPTION_CHANNELS, channels); }

        final Map<String, Object> httpHeaders = new HashMap<>();
        // User-Agent:
        httpHeaders.put("User-Agent", CBLVersion.getUserAgent());
        // headers
        if ((headers != null) && (!headers.isEmpty())) {
            for (Map.Entry<String, String> entry: headers.entrySet()) {
                httpHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        options.put(REPLICATOR_OPTION_EXTRA_HEADERS, httpHeaders);

        return options;
    }

    private void checkReadOnly() {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
    }
}
