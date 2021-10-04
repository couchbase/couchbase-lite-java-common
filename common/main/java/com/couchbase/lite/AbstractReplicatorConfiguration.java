//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.internal.Util;

import com.couchbase.lite.internal.BaseImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Replicator configuration.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyFields", "PMD.UnnecessaryFullyQualifiedName"})
public abstract class AbstractReplicatorConfiguration {
    /**
     * This is a long time: just under 25 days.
     * This many seconds, however, is just less than Integer.MAX_INT millis, and will fit in the heartbeat property.
     */
    public static final int DISABLE_HEARTBEAT = 2147483;

    /**
     * Replicator type
     * PUSH_AND_PULL: Bidirectional; both push and pull
     * PUSH: Pushing changes to the target
     * PULL: Pulling changes from the target
     *
     * @deprecated Use AbstractReplicator.ReplicatorType
     */
    @Deprecated
    public enum ReplicatorType {PUSH_AND_PULL, PUSH, PULL}

    protected static int verifyHeartbeat(int heartbeat) {
        Util.checkDuration("heartbeat", Preconditions.assertNotNegative(heartbeat, "heartbeat"), TimeUnit.SECONDS);
        return heartbeat;
    }

    @Nullable
    protected static byte[] copyCert(@Nullable byte[] cert) {
        if (cert == null) { return null; }
        final byte[] newCert = new byte[cert.length];
        System.arraycopy(cert, 0, newCert, 0, newCert.length);
        return newCert;
    }


    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    @NonNull
    private final Database database;
    @NonNull
    private com.couchbase.lite.ReplicatorType type;
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
    @Nullable
    private ConflictResolver conflictResolver;
    private int maxAttempts;
    private int maxAttemptWaitTime;
    private int heartbeat;
    private boolean enableAutoPurge = true;
    @NonNull
    private final Endpoint target;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    protected AbstractReplicatorConfiguration(@NonNull Database database, @NonNull Endpoint target) {
        this.database = database;
        this.type = com.couchbase.lite.ReplicatorType.PUSH_AND_PULL;
        this.target = target;
    }

    protected AbstractReplicatorConfiguration(@NonNull AbstractReplicatorConfiguration config) {
        this(
            Preconditions.assertNotNull(config, "config").database,
            config.type,
            config.continuous,
            config.authenticator,
            config.headers,
            config.pinnedServerCertificate,
            config.channels,
            config.documentIDs,
            config.pullFilter,
            config.pushFilter,
            config.conflictResolver,
            config.maxAttempts,
            config.maxAttemptWaitTime,
            config.heartbeat,
            config.enableAutoPurge,
            config.target);
    }

    protected AbstractReplicatorConfiguration(@NonNull BaseImmutableReplicatorConfiguration config) {
        this(
            Preconditions.assertNotNull(config, "config").getDatabase(),
            config.getType(),
            config.isContinuous(),
            config.getAuthenticator(),
            config.getHeaders(),
            config.getPinnedServerCertificate(),
            config.getChannels(),
            config.getDocumentIDs(),
            config.getPullFilter(),
            config.getPushFilter(),
            config.getConflictResolver(),
            config.getMaxRetryAttempts(),
            config.getMaxRetryAttemptWaitTime(),
            config.getHeartbeat(),
            config.isAutoPurgeEnabled(),
            config.getTarget());
    }

    @SuppressWarnings({"PMD.ExcessiveParameterList", "PMD.ArrayIsStoredDirectly"})
    protected AbstractReplicatorConfiguration(
        @NonNull Database database,
        @NonNull com.couchbase.lite.ReplicatorType type,
        boolean continuous,
        @Nullable Authenticator authenticator,
        @Nullable Map<String, String> headers,
        @Nullable byte[] pinnedServerCertificate,
        @Nullable List<String> channels,
        @Nullable List<String> documentIDs,
        @Nullable ReplicationFilter pushFilter,
        @Nullable ReplicationFilter pullFilter,
        @Nullable ConflictResolver conflictResolver,
        int maxAttempts,
        int maxAttemptWaitTime,
        int heartbeat,
        boolean enableAutoPurge,
        @NonNull Endpoint target) {
        this.database = database;
        this.type = type;
        this.continuous = continuous;
        this.authenticator = authenticator;
        this.headers = headers;
        this.pinnedServerCertificate = pinnedServerCertificate;
        this.channels = channels;
        this.documentIDs = documentIDs;
        this.pullFilter = pullFilter;
        this.pushFilter = pushFilter;
        this.conflictResolver = conflictResolver;
        this.maxAttempts = maxAttempts;
        this.maxAttemptWaitTime = maxAttemptWaitTime;
        this.heartbeat = heartbeat;
        this.enableAutoPurge = enableAutoPurge;
        this.target = target;
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
        this.channels = (channels == null) ? null : new ArrayList<>(channels);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the the conflict resolver.
     *
     * @param conflictResolver A conflict resolver.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setConflictResolver(@Nullable ConflictResolver conflictResolver) {
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
        this.documentIDs = (documentIDs == null) ? null : new ArrayList<>(documentIDs);
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
     * Sets the target server's SSL certificate.
     *
     * @param pinnedCert the SSL certificate.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPinnedServerCertificate(@Nullable byte[] pinnedCert) {
        pinnedServerCertificate = copyCert(pinnedCert);
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
    public final ReplicatorConfiguration setPushFilter(@Nullable ReplicationFilter pushFilter) {
        this.pushFilter = pushFilter;
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
    public final ReplicatorConfiguration setReplicatorType(@NonNull ReplicatorType replicatorType) {
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
     * Enable/disable auto-purge.
     * <p>
     * Auto-purge is enabled, by default.
     * <p>
     * When the autoPurge flag is disabled, the replicator will notify the registered DocumentReplication listeners
     * with an "access removed" event when access to the document is revoked on the Sync Gateway. On receiving the
     * event, the application may decide to manually purge the document. However, for performance reasons, any
     * DocumentReplication listeners added to the replicator after the replicator is started will not receive the
     * access removed events until the replicator is restarted or reconnected with Sync Gateway.
     */
    @NonNull
    public final ReplicatorConfiguration setAutoPurgeEnabled(boolean enabled) {
        this.enableAutoPurge = enabled;
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
    public final List<String> getChannels() { return (channels == null) ? null : new ArrayList<>(channels); }

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
    public final List<String> getDocumentIDs() { return (documentIDs == null) ? null : new ArrayList<>(documentIDs); }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    @Nullable
    public final Map<String, String> getHeaders() { return (headers == null) ? null : new HashMap<>(headers); }

    /**
     * Return the remote target's SSL certificate.
     */
    @Nullable
    public final byte[] getPinnedServerCertificate() { return copyCert(pinnedServerCertificate); }

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
     * Old getter for Replicator type indicating the direction of the replicator.
     *
     * @deprecated Use getType()
     */
    @Deprecated
    @NonNull
    public final ReplicatorType getReplicatorType() {
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
     * Return Replicator type indicating the direction of the replicator.
     */
    @NonNull
    public final com.couchbase.lite.ReplicatorType getType() { return type; }

    /**
     * Return the replication target to replicate with.
     */
    @NonNull
    public final Endpoint getTarget() { return target; }

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
     * Enable/disable auto-purge.
     * Default is enabled.
     */
    public final boolean isAutoPurgeEnabled() { return enableAutoPurge; }

    @SuppressWarnings("PMD.NPathComplexity")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();

        if (pullFilter != null) { buf.append('|'); }
        if ((type == com.couchbase.lite.ReplicatorType.PULL)
            || (type == com.couchbase.lite.ReplicatorType.PUSH_AND_PULL)) {
            buf.append('<');
        }

        buf.append(continuous ? '*' : '=');

        if ((type == com.couchbase.lite.ReplicatorType.PUSH)
            || (type == com.couchbase.lite.ReplicatorType.PUSH_AND_PULL)) {
            buf.append('>');
        }
        if (pushFilter != null) { buf.append('|'); }

        buf.append('(');
        if (authenticator != null) { buf.append('@'); }
        if (pinnedServerCertificate != null) { buf.append('^'); }
        buf.append(')');

        if (conflictResolver != null) { buf.append('!'); }

        return "ReplicatorConfig{" + database + buf.toString() + target + '}';
    }

    //---------------------------------------------
    // Protecte access
    //---------------------------------------------

    @NonNull
    abstract ReplicatorConfiguration getReplicatorConfiguration();
}
