//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.Authenticator;
import com.couchbase.lite.ConflictResolver;
import com.couchbase.lite.Database;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.ReplicationFilter;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.CBLVersion;

/**
 * A bit odd.  Why are these properties not simply properties on the AbstractReplicator object?
 * Because they are mandated by a spec:
 * https://docs.google.com/document/d/16XmIOw7aZ_NcFc6Dy6fc1jV7sc994r6iv5qm9_J7qKo/edit#heading=h.kt1n12mtpzx4
 */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class BaseImmutableReplicatorConfiguration {

    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    @NonNull
    private final Database database;
    @NonNull
    private final Replicator.Type type;
    private final boolean continuous;
    @Nullable
    private final Authenticator authenticator;
    @Nullable
    private final Map<String, String> headers;
    @Nullable
    private final byte[] pinnedServerCertificate;
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
    private final int maxRetries;
    private final long maxRetryWaitTime;
    private final long heartbeat;
    @NonNull
    private final Endpoint target;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------
    protected BaseImmutableReplicatorConfiguration(@NonNull ReplicatorConfiguration config) {
        this.database = config.getDatabase();
        this.type = config.getType();
        this.continuous = config.isContinuous();
        this.authenticator = config.getAuthenticator();
        this.headers = config.getHeaders();
        this.pinnedServerCertificate = config.getPinnedServerCertificate();
        this.channels = config.getChannels();
        this.documentIDs = config.getDocumentIDs();
        this.pushFilter = config.getPushFilter();
        this.pullFilter = config.getPullFilter();
        this.conflictResolver = config.getConflictResolver();
        this.maxRetries = config.getMaxRetries();
        this.maxRetryWaitTime = config.getMaxRetryWaitTime();
        this.heartbeat = config.getHeartbeat();
        this.target = config.getTarget();
    }

    //-------------------------------------------------------------------------
    // Properties
    //-------------------------------------------------------------------------
    @NonNull
    public final Database getDatabase() { return database; }

    @NonNull
    public final Replicator.Type getType() { return type; }

    public final boolean isPush() {
        return type == Replicator.Type.PUSH_AND_PULL
            || type == Replicator.Type.PUSH;
    }

    public final boolean isPull() {
        return type == Replicator.Type.PUSH_AND_PULL
            || type == Replicator.Type.PULL;
    }

    public final boolean isContinuous() { return continuous; }

    @Nullable
    public final Authenticator getAuthenticator() { return authenticator; }

    @Nullable
    public final Map<String, String> getHeaders() { return headers; }

    // DO NOT MESS WITH THIS RETURN VALUE!!!
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    @SuppressFBWarnings("EI_EXPOSE_REP")
    @Nullable
    public final byte[] getPinnedServerCertificate() { return pinnedServerCertificate; }

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

    public final int getMaxRetries() { return maxRetries; }

    public final long getMaxRetryWaitTime() { return maxRetryWaitTime; }

    public final long getHeartbeat() { return heartbeat; }

    @NonNull
    public final Endpoint getTarget() { return target; }

    //-------------------------------------------------------------------------
    // Public Methods
    //-------------------------------------------------------------------------
    public void addEffectiveOptions(@NonNull Map<String, Object> options) {
        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null) {
            options.put(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT, pinnedServerCertificate);
        }

        if ((documentIDs != null) && (!documentIDs.isEmpty())) {
            options.put(C4Replicator.REPLICATOR_OPTION_DOC_IDS, documentIDs);
        }

        if ((channels != null) && (!channels.isEmpty())) {
            options.put(C4Replicator.REPLICATOR_OPTION_CHANNELS, channels);
        }

        options.put(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES, getMaxRetries());
        options.put(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL, maxRetryWaitTime);
        options.put(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL, heartbeat);

        final Map<String, Object> httpHeaders = new HashMap<>();
        // User-Agent:
        httpHeaders.put("User-Agent", CBLVersion.getUserAgent());
        // headers
        if ((headers != null) && (!headers.isEmpty())) {
            for (Map.Entry<String, String> entry: headers.entrySet()) {
                httpHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        options.put(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS, httpHeaders);
    }
}