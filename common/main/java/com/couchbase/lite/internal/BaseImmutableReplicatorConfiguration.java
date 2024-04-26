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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.Authenticator;
import com.couchbase.lite.Collection;
import com.couchbase.lite.CollectionConfiguration;
import com.couchbase.lite.Database;
import com.couchbase.lite.Defaults;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.ProxyAuthenticator;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.ReplicatorType;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The spec:
 * https://docs.google.com/document/d/16XmIOw7aZ_NcFc6Dy6fc1jV7sc994r6iv5qm9_J7qKo/edit#heading=h.kt1n12mtpzx4
 * mandates that these cannot be simple properties on ReplicatorConfiguration.
 */
@SuppressWarnings("PMD.TooManyFields")
public class BaseImmutableReplicatorConfiguration {

    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    @NonNull
    private final Map<Collection, CollectionConfiguration> configs;
    @NonNull
    private final Endpoint target;
    @NonNull
    private final ReplicatorType type;
    private final boolean continuous;
    @Nullable
    private final Authenticator authenticator;
    @Nullable
    private final ProxyAuthenticator proxyAuthenticator;
    @Nullable
    private final Map<String, String> headers;

    private final boolean acceptParentCookies;
    @Nullable
    private final X509Certificate pinnedServerCertificate;
    private final int maxAttempts;
    private final int maxAttemptWaitTime;
    private final int heartbeat;
    private final boolean enableAutoPurge;

    @Nullable
    private final Database database;

    // PMD doesn't get weak references
    @SuppressWarnings({"PMD.SingularField", "FieldCanBeLocal"})
    private Map<String, Object> options;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    protected BaseImmutableReplicatorConfiguration(@NonNull ReplicatorConfiguration config) {
        final Map<Collection, CollectionConfiguration> collectionConfigs = config.getCollectionConfigurations();
        if (collectionConfigs.isEmpty()) {
            throw new IllegalArgumentException("Attempt to configure a replicator with no source collections");
        }
        this.configs = Collections.unmodifiableMap(new HashMap<>(collectionConfigs));
        this.target = Preconditions.assertNotNull(config.getTarget(), "replication target");
        this.type = Preconditions.assertNotNull(config.getType(), "replicator type");
        this.continuous = config.isContinuous();
        this.authenticator = config.getAuthenticator();
        this.proxyAuthenticator = config.getProxyAuthenticator();
        this.headers = config.getHeaders();
        this.acceptParentCookies = config.isAcceptParentDomainCookies();
        this.pinnedServerCertificate = config.getPinnedServerX509Certificate();
        this.maxAttempts = config.getMaxAttempts();
        this.maxAttemptWaitTime = config.getMaxAttemptWaitTime();
        this.heartbeat = config.getHeartbeat();
        this.enableAutoPurge = config.isAutoPurgeEnabled();
        this.database = Preconditions.assertNotNull(config.getDatabase(), "replications source database");
    }

    //-------------------------------------------------------------------------
    // Properties
    //-------------------------------------------------------------------------

    @NonNull
    public final Map<Collection, CollectionConfiguration> getCollectionConfigs() { return configs; }

    @Nullable
    public final Database getDatabase() { return database; }

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
    public final ProxyAuthenticator getProxyAuthenticator() { return proxyAuthenticator; }

    @Nullable
    public final Map<String, String> getHeaders() { return headers; }

    public boolean isAcceptParentCookies() { return acceptParentCookies; }

    @Nullable
    public final X509Certificate getPinnedServerCertificate() { return pinnedServerCertificate; }

    public final int getMaxRetryAttempts() { return maxAttempts; }

    public final int getMaxRetryAttemptWaitTime() { return maxAttemptWaitTime; }

    public final int getHeartbeat() { return heartbeat; }

    public final boolean isAutoPurgeEnabled() { return enableAutoPurge; }

    @NonNull
    public final Endpoint getTarget() { return target; }

    @SuppressWarnings("PMD.NPathComplexity")
    @NonNull
    public Map<String, Object> getConnectionOptions() {
        final Map<String, Object> options = new HashMap<>();

        if (authenticator != null) { ((BaseAuthenticator) authenticator).authenticate(options); }

        // Add the proxy authenticator, if any:
        if (proxyAuthenticator != null) { ((BaseAuthenticator) proxyAuthenticator).authenticate(options); }

        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null) {
            try {
                options.put(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT, pinnedServerCertificate.getEncoded());
            }
            catch (CertificateEncodingException e) {
                Log.w(LogDomain.NETWORK, "Unable to encode pinned certificate.  Ignoring", e);
            }
        }

        // These three properties still support 0 -> default.
        // The default, however, is set here.

        options.put(
            C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL,
            (heartbeat > 0) ? heartbeat : Defaults.Replicator.HEARTBEAT);

        options.put(
            C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL,
            (maxAttemptWaitTime > 0) ? maxAttemptWaitTime : Defaults.Replicator.MAX_ATTEMPTS_WAIT_TIME);

        options.put(
            C4Replicator.REPLICATOR_OPTION_MAX_RETRIES,
            ((maxAttempts > 0)
                ? maxAttempts
                : ((continuous)
                    ? Defaults.Replicator.MAX_ATTEMPTS_CONTINUOUS
                    : (Defaults.Replicator.MAX_ATTEMPTS_SINGLE_SHOT)))
                - 1); // subtract 1 from max attempts to get what LiteCore wants: number of retries.


        options.put(C4Replicator.REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES, acceptParentCookies);

        options.put(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE, enableAutoPurge);

        final Map<String, Object> httpHeaders = new HashMap<>();
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

        httpHeaders.put(AbstractCBLWebSocket.HEADER_USER_AGENT, CBLVersion.getUserAgent());

        options.put(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS, httpHeaders);

        // need to hang on to the options, so that the GC doesn't get 'em.
        this.options = options;

        return this.options;
    }
}
