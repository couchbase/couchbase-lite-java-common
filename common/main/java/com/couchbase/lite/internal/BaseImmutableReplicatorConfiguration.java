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
package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.Authenticator;
import com.couchbase.lite.Collection;
import com.couchbase.lite.CollectionConfiguration;
import com.couchbase.lite.ConflictResolver;
import com.couchbase.lite.Database;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.ReplicationFilter;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.ReplicatorType;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * These properties are not simply properties on the AbstractReplicator object as is mandated by a spec:
 * https://docs.google.com/document/d/16XmIOw7aZ_NcFc6Dy6fc1jV7sc994r6iv5qm9_J7qKo/edit#heading=h.kt1n12mtpzx4
 */
@SuppressWarnings("PMD.TooManyFields")
public class BaseImmutableReplicatorConfiguration {

    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    @NonNull
    private final Map<Collection, CollectionConfiguration> collections;
    @NonNull
    private final ReplicatorType type;
    private final boolean continuous;
    @Nullable
    private final Authenticator authenticator;
    @Nullable
    private final Map<String, String> headers;
    @Nullable
    private final X509Certificate pinnedServerCertificate;
    @Nullable
    private final List<String> channels;
    @Nullable
    private final List<String> documentIDs;
    @Nullable
    private final ReplicationFilter pushFilter;
    @Nullable
    private final ReplicationFilter pullFilter;
    @Nullable
    private final ConflictResolver conflictResolver;
    private final int maxRetryAttempts;
    private final int maxRetryAttemptWaitTime;
    private final int heartbeat;
    private final boolean enableAutoPurge;
    @NonNull
    private final Endpoint target;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------
    protected BaseImmutableReplicatorConfiguration(@NonNull ReplicatorConfiguration config) {
        this.collections = Preconditions.assertNotNull(config.getCollections(), "collections");
        this.type = config.getType();
        this.continuous = config.isContinuous();
        this.authenticator = config.getAuthenticator();
        this.headers = config.getHeaders();
        this.pinnedServerCertificate = config.getPinnedServerX509Certificate();
        this.channels = config.getChannels();
        this.documentIDs = config.getDocumentIDs();
        this.pushFilter = config.getPushFilter();
        this.pullFilter = config.getPullFilter();
        this.conflictResolver = config.getConflictResolver();
        this.maxRetryAttempts = config.getMaxAttempts();
        this.maxRetryAttemptWaitTime = config.getMaxAttemptWaitTime();
        this.heartbeat = config.getHeartbeat();
        this.enableAutoPurge = config.isAutoPurgeEnabled();
        this.target = config.getTarget();
    }

    //-------------------------------------------------------------------------
    // Properties
    //-------------------------------------------------------------------------

    @NonNull
    public final Map<Collection, CollectionConfiguration> getCollections() { return collections; }

    @Nullable
    public final Database getDatabase() {
        return (collections.isEmpty()) ? null : collections.keySet().iterator().next().getDatabase();
    }

    @NonNull
    public final ReplicatorType getType() { return type; }

    public final boolean isPush() {
        return type == ReplicatorType.PUSH_AND_PULL
            || type == ReplicatorType.PUSH;
    }

    public final boolean isPull() {
        return type == ReplicatorType.PUSH_AND_PULL
            || type == ReplicatorType.PULL;
    }

    public final boolean isContinuous() { return continuous; }

    @Nullable
    public final Authenticator getAuthenticator() { return authenticator; }

    @Nullable
    public final Map<String, String> getHeaders() { return headers; }

    @Nullable
    public final X509Certificate getPinnedServerCertificate() { return pinnedServerCertificate; }

    @Nullable
    public final List<String> getChannels() { return channels; }

    @Nullable
    public final List<String> getDocumentIDs() { return documentIDs; }

    @Nullable
    public final ReplicationFilter getPushFilter() { return pushFilter; }

    @Nullable
    public final ReplicationFilter getPullFilter() { return pullFilter; }

    @Nullable
    public final ConflictResolver getConflictResolver() { return conflictResolver; }

    public final int getMaxRetryAttempts() { return maxRetryAttempts; }

    public final int getMaxRetryAttemptWaitTime() { return maxRetryAttemptWaitTime; }

    public final int getHeartbeat() { return heartbeat; }

    public final boolean isAutoPurgeEnabled() { return enableAutoPurge; }

    @NonNull
    public final Endpoint getTarget() { return target; }

    @SuppressWarnings("PMD.NPathComplexity")
    @NonNull
    public Map<String, Object> getConnectionOptions() {
        final Map<String, Object> options = new HashMap<>();

        if (authenticator != null) { ((BaseAuthenticator) authenticator).authenticate(options); }

        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null) {
            try {
                options.put(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT, pinnedServerCertificate.getEncoded());
            }
            catch (CertificateEncodingException e) {
                Log.w(LogDomain.NETWORK, "Unable to encode pinned certificate.  Ignoring", e);
            }
        }

        if ((documentIDs != null) && (!documentIDs.isEmpty())) {
            options.put(C4Replicator.REPLICATOR_OPTION_DOC_IDS, documentIDs);
        }

        if ((channels != null) && (!channels.isEmpty())) {
            options.put(C4Replicator.REPLICATOR_OPTION_CHANNELS, channels);
        }

        if (heartbeat > 0) { options.put(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL, heartbeat); }
        if (maxRetryAttempts > 0) { options.put(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES, maxRetryAttempts - 1); }
        if (maxRetryAttemptWaitTime > 0) {
            options.put(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL, maxRetryAttemptWaitTime);
        }

        if (!enableAutoPurge) { options.put(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE, Boolean.FALSE); }

        final Map<String, Object> httpHeaders = new HashMap<>();
        httpHeaders.put("User-Agent", CBLVersion.getUserAgent());

        if (headers != null) {
            // If client code specified a cookies header, remove it and add it to the separate cookies option
            String cookies = headers.remove(AbstractCBLWebSocket.HEADER_COOKIES);
            if (cookies != null) {
                final Object curCookies = options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
                if (curCookies instanceof String) { cookies += "; " + curCookies; }
                options.put(C4Replicator.REPLICATOR_OPTION_COOKIES, cookies);
            }

            httpHeaders.putAll(headers);
        }

        options.put(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS, httpHeaders);

        return options;
    }
}
