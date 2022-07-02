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

package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.Collection;
import com.couchbase.lite.CollectionConfiguration;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.ReplicatorType;
import com.couchbase.lite.internal.BaseSocketFactory;
import com.couchbase.lite.internal.ReplicationCollection;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.impl.NativeC4Replicator;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.replicator.ReplicatorListener;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * There are two things that need protection in the class:
 * <ol>
 * <li/> Messages sent to it from native code:  This object proxies those messages out to
 * various listeners.  Until a replicator object is removed from BOUND_REPLICATORS
 * forwarding such a message must work.
 * <li/> Calls to the native object:  These should work as long as the peer handle is non-zero.
 * This object must be careful never to forward a call to a native object once that object has been freed.
 * </ol>
 * <p>
 * This class and its members are referenced by name, from native code.
 */
public abstract class C4Replicator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    public static final LogDomain LOG_DOMAIN = LogDomain.REPLICATOR;


    //////// Most of these are defined in c4Replicator.h and must agree with those definitions.

    public static final String WEBSOCKET_SCHEME = "ws";
    public static final String WEBSOCKET_SECURE_CONNECTION_SCHEME = "wss";
    public static final String MESSAGE_SCHEME = "x-msg-endpt";

    public static final String C4_REPLICATOR_SCHEME_2 = "blip";
    public static final String C4_REPLICATOR_TLS_SCHEME_2 = "blips";

    ////// values for enum C4ReplicatorProgressLevel

    public static final int PROGRESS_OVERALL = 0;
    public static final int PROGRESS_PER_DOC = 1;
    public static final int PROGRESS_PER_ATTACHMENT = 2;

    ////// Replicator option dictionary keys:

    // begin: collection specific properties.
    // Docs to replicate: string[]
    public static final String REPLICATOR_OPTION_DOC_IDS = "docIDs";
    // SG channel names: string[]
    public static final String REPLICATOR_OPTION_CHANNELS = "channels";
    // Filter name: string
    public static final String REPLICATOR_OPTION_FILTER = "filter";
    // Filter params: Dict[string]
    public static final String REPLICATOR_OPTION_FILTER_PARAMS = "filterParams";
    // Don't push/pull tombstones: bool
    public static final String REPLICATOR_OPTION_SKIP_DELETED = "skipDeleted";
    // Reject incoming conflicts: bool
    public static final String REPLICATOR_OPTION_NO_INCOMING_CONFLICTS = "noIncomingConflicts";
    // Allow creating conflicts on remote: bool
    public static final String REPLICATOR_OPTION_OUTGOING_CONFLICTS = "outgoingConflicts";
    // How often to checkpoint, in seconds: number
    public static final String REPLICATOR_CHECKPOINT_INTERVAL = "checkpointInterval";
    // end: collection specific properties

    // Stable ID for remote db with unstable URL: string
    public static final String REPLICATOR_OPTION_REMOTE_DB_UNIQUE_ID = "remoteDBUniqueID";
    // Disables delta sync: bool
    public static final String REPLICATOR_OPTION_DISABLE_DELTAS = "noDeltas";
    // Disables property decryption
    public static final String REPLICATOR_OPTION_DISABLE_PROPERTY_DECRYPTION = "noDecryption";
    // Max number of retry attempts (int)
    public static final String REPLICATOR_OPTION_MAX_RETRIES = "maxRetries";
    // Max delay between retries (secs)
    public static final String REPLICATOR_OPTION_MAX_RETRY_INTERVAL = "maxRetryInterval";
    // true, Enable auto-purge
    public static final String REPLICATOR_OPTION_ENABLE_AUTO_PURGE = "autoPurge";

    //// TLS options
    // Trusted root certs (data)
    public static final String REPLICATOR_OPTION_ROOT_CERTS = "rootCerts";
    // Cert or public key: [data]
    public static final String REPLICATOR_OPTION_PINNED_SERVER_CERT = "pinnedCert";
    // Only accept self signed server certs (for P2P, bool)
    public static final String REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT = "onlySelfSignedServer";

    //// HTTP options
    // Extra HTTP headers: string[]
    public static final String REPLICATOR_OPTION_EXTRA_HEADERS = "headers";
    // HTTP Cookie header value: string
    public static final String REPLICATOR_OPTION_COOKIES = "cookies";
    // Auth settings: Dict
    public static final String REPLICATOR_OPTION_AUTHENTICATION = "auth";
    // Proxy settings (Dict); see [3]]
    public static final String REPLICATOR_OPTION_PROXY_SERVER = "proxy";

    //// WebSocket options
    // Interval in secs to send a keep-alive: ping
    public static final String REPLICATOR_HEARTBEAT_INTERVAL = "heartbeat";
    // Sec-WebSocket-Protocol header value
    public static final String SOCKET_OPTION_WS_PROTOCOLS = "WS-Protocols";
    // Specific network interface (name or IP address) used for connecting to the remote server.
    public static final String SOCKET_OPTIONS_NETWORK_INTERFACE = "networkInterface";

    //// BLIP options
    // Data compression level, 0..9
    public static final String REPLICATOR_COMPRESSION_LEVEL = "BLIPCompressionLevel";

    ////// Auth dictionary keys:

    // Auth type; see [2] (string)
    public static final String REPLICATOR_AUTH_TYPE = "type";
    // User name for basic auth (string)
    public static final String REPLICATOR_AUTH_USER_NAME = "username";
    // Password for basic auth (string)
    public static final String REPLICATOR_AUTH_PASSWORD = "password";
    // TLS client certificate (value platform-dependent)
    public static final String REPLICATOR_AUTH_CLIENT_CERT = "clientCert";
    // Client cert's private key (data)
    public static final String REPLICATOR_AUTH_CLIENT_CERT_KEY = "clientCertKey";
    // Session cookie or auth token (string)
    public static final String REPLICATOR_AUTH_TOKEN = "token";

    ////// auth.type values:
    // HTTP Basic (the default)
    public static final String AUTH_TYPE_BASIC = "Basic";
    // SG session cookie
    public static final String AUTH_TYPE_SESSION = "Session";
    public static final String AUTH_TYPE_OPEN_ID_CONNECT = "OpenID Connect";
    public static final String AUTH_TYPE_FACEBOOK = "Facebook";
    public static final String AUTH_TYPE_CLIENT_CERT = "Client Cert";

    //-------------------------------------------------------------------------
    // Types
    //-------------------------------------------------------------------------

    ////// Native API

    public interface NativeImpl {
        @SuppressWarnings("PMD.ExcessiveParameterList")
        long nCreate(
            ReplicationCollection[] collections,
            long db,
            String scheme,
            String host,
            int port,
            String path,
            String remoteDbName,
            int framing,
            boolean push,
            boolean pull,
            boolean continuous,
            long replicatorToken,
            long socketFactoryToken)
            throws LiteCoreException;

        long nCreateLocal(
            ReplicationCollection[] collections,
            long db,
            long targetDb,
            boolean push,
            boolean pull,
            boolean continuous,
            long replicatorToken)
            throws LiteCoreException;

        long nCreateWithSocket(
            ReplicationCollection[] collections,
            long db,
            long openSocket,
            long replicatorToken)
            throws LiteCoreException;

        @NonNull
        C4ReplicatorStatus nGetStatus(long peer);
        void nStart(long peer, boolean restart);
        void nStop(long peer);
        void nSetOptions(long peer, byte[] options);
        long nGetPendingDocIds(long peer) throws LiteCoreException;
        boolean nIsDocumentPending(long peer, String id) throws LiteCoreException;
        void nSetProgressLevel(long peer, int progressLevel) throws LiteCoreException;
        void nSetHostReachable(long peer, boolean reachable);
        void nFree(long peer);
    }

    ////// Standard Replicator

    static final class C4CommonReplicator extends C4Replicator {
        @NonNull
        private final AbstractReplicator replicator;

        @Nullable
        private final SocketFactory socketFactory;
        private final long socketFactoryToken;

        C4CommonReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull ReplicationCollection[] colls,
            @NonNull ReplicatorListener listener,
            @NonNull AbstractReplicator replicator) {
            this(impl, peer, token, colls, listener, replicator, null, 0L);
        }

        C4CommonReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull ReplicationCollection[] colls,
            @NonNull ReplicatorListener listener,
            @NonNull AbstractReplicator replicator,
            @Nullable SocketFactory socketFactory,
            long socketFactoryToken) {
            super(impl, peer, token, colls, listener);

            this.replicator = Preconditions.assertNotNull(replicator, "replicator");

            this.socketFactory = socketFactory;
            this.socketFactoryToken = socketFactoryToken;
        }

        @Override
        @NonNull
        public AbstractReplicator getReplicator() { return replicator; }

        @Override
        protected void releaseResources() { BaseSocketFactory.unbindSocketFactory(socketFactoryToken); }

        @NonNull
        @Override
        public String toString() {
            return "C4Repl{" + ClassUtils.objId(this) + "/" + super.toString() + ": " + listener
                // don't try to stringify the replicator: it stringifies this
                + ", " + ClassUtils.objId(replicator) + ", "
                + socketFactory + "'}";
        }
    }

    ////// Message Endpoint Replicator

    static final class C4MessageEndpointReplicator extends C4Replicator {
        C4MessageEndpointReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull ReplicationCollection[] colls,
            @NonNull ReplicatorListener listener) {
            super(impl, peer, token, colls, listener);
        }

        @Override
        @Nullable
        public AbstractReplicator getReplicator() { return null; }

        @Override
        protected void releaseResources() { }

        @NonNull
        @Override
        public String toString() {
            return "C4MessageEndpointReplicator{" + ClassUtils.objId(this) + "/" + super.toString() + listener + "'}";
        }
    }

    //-------------------------------------------------------------------------
    // Static fields
    //-------------------------------------------------------------------------
    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Replicator();

    // Lookup table: maps a random token (context) to its companion Java C4Replicator
    @NonNull
    @VisibleForTesting
    static final TaggedWeakPeerBinding<C4Replicator> BOUND_REPLICATORS = new TaggedWeakPeerBinding<>();

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void statusChangedCallback(long token, @Nullable C4ReplicatorStatus status) {
        final C4Replicator c4Repl = BOUND_REPLICATORS.getBinding(token);
        Log.d(LOG_DOMAIN, "C4Replicator.statusChangedCallback(0x%x) %s: %s", token, c4Repl, status);
        if (c4Repl == null) { return; }
        c4Repl.listener.statusChanged(c4Repl, status);
    }

    // This method is called by reflection.  Don't change its signature.
    static void documentEndedCallback(long token, boolean pushing, @Nullable C4DocumentEnded... documentsEnded) {
        final C4Replicator c4Repl = BOUND_REPLICATORS.getBinding(token);
        Log.d(LOG_DOMAIN, "C4Replicator.documentEndedCallback %s@0x%x: %s", c4Repl, token, pushing);
        if (c4Repl == null) { return; }
        c4Repl.listener.documentEnded(c4Repl, pushing, documentsEnded);
    }

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createRemoteReplicator(
        @NonNull Map<Collection, CollectionConfiguration> collections,
        long db,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDbName,
        @NonNull MessageFraming framing,
        @NonNull ReplicatorType type,
        boolean continuous,
        @Nullable Map<String, Object> options,
        @NonNull ReplicatorListener listener,
        @NonNull AbstractReplicator replicator,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        final long replToken = BOUND_REPLICATORS.reserveKey();
        final long sfToken = (socketFactory == null) ? 0L : BaseSocketFactory.bindSocketFactory(socketFactory);

        final ReplicationCollection[] colls = ReplicationCollection.createAll(collections, options);

        final long peer = NATIVE_IMPL.nCreate(
            colls,
            db,
            scheme,
            host,
            port,
            path,
            remoteDbName,
            MessageFraming.getC4Framing(framing),
            (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PUSH),
            (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PULL),
            continuous,
            replToken,
            sfToken);

        final C4Replicator c4Replicator
            = new C4CommonReplicator(NATIVE_IMPL, peer, replToken, colls, listener, replicator, socketFactory, sfToken);

        BOUND_REPLICATORS.bind(replToken, c4Replicator);

        return c4Replicator;
    }

    @NonNull
    static C4Replicator createLocalReplicator(
        @NonNull Map<Collection, CollectionConfiguration> collections,
        long db,
        @NonNull C4Database targetDb,
        @NonNull ReplicatorType type,
        boolean continuous,
        @Nullable Map<String, Object> options,
        @NonNull ReplicatorListener listener,
        @NonNull AbstractReplicator replicator)
        throws LiteCoreException {
        final long replToken = BOUND_REPLICATORS.reserveKey();

        final ReplicationCollection[] colls = ReplicationCollection.createAll(collections, options);

        final long peer = NATIVE_IMPL.nCreateLocal(
            colls,
            db,
            targetDb.getHandle(),
            (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PUSH),
            (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PULL),
            continuous,
            replToken);

        final C4Replicator c4Replicator
            = new C4CommonReplicator(NATIVE_IMPL, peer, replToken, colls, listener, replicator);

        BOUND_REPLICATORS.bind(replToken, c4Replicator);

        return c4Replicator;
    }

    @NonNull
    static C4Replicator createMessageEndpointReplicator(
        @NonNull Set<Collection> collections,
        long db,
        @NonNull C4Socket c4Socket,
        @Nullable Map<String, Object> options,
        @NonNull ReplicatorListener listener)
        throws LiteCoreException {
        final long replToken = BOUND_REPLICATORS.reserveKey();

        final ReplicationCollection[] colls = ReplicationCollection.createAll(collections, options);

        final long peer = NATIVE_IMPL.nCreateWithSocket(
            colls,
            db,
            c4Socket.getPeerHandle(),
            replToken);

        final C4Replicator c4Replicator
            = new C4MessageEndpointReplicator(NATIVE_IMPL, peer, replToken, colls, listener);

        BOUND_REPLICATORS.bind(replToken, c4Replicator);

        return c4Replicator;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;

    private final long token;

    @NonNull
    private final List<ReplicationCollection> colls;

    @NonNull
    protected final ReplicatorListener listener;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4Replicator(
        @NonNull NativeImpl impl,
        long peer,
        long token,
        @NonNull ReplicationCollection[] colls,
        @NonNull ReplicatorListener listener) {
        super(peer);

        this.impl = impl;

        this.token = Preconditions.assertNotZero(token, "token");

        this.colls = Arrays.asList(colls);

        this.listener = Preconditions.assertNotNull(listener, "listener");
    }

    //-------------------------------------------------------------------------
    // Instance Methods
    //-------------------------------------------------------------------------

    public void start(boolean restart) { impl.nStart(getPeer(), restart); }

    public void stop() { impl.nStop(getPeer()); }

    @CallSuper
    @Override
    public void close() {
        for (ReplicationCollection coll: colls) { coll.close(); }
        closePeer(null);
    }

    @Nullable
    public abstract AbstractReplicator getReplicator();

    public void setOptions(@Nullable byte[] options) { impl.nSetOptions(getPeer(), options); }

    @Nullable
    public C4ReplicatorStatus getStatus() { return impl.nGetStatus(getPeer()); }

    public boolean isDocumentPending(String docId) throws LiteCoreException {
        return impl.nIsDocumentPending(getPeer(), docId);
    }

    @NonNull
    public Set<String> getPendingDocIDs() throws LiteCoreException {
        try (FLSliceResult result = FLSliceResult.getManagedSliceResult(impl.nGetPendingDocIds(getPeer()))) {
            final FLValue slice = FLValue.fromData(result);
            return (slice == null) ? Collections.emptySet() : new HashSet<>(slice.asTypedArray());
        }
    }

    public void setProgressLevel(int level) throws LiteCoreException { impl.nSetProgressLevel(getPeer(), level); }

    public void setHostReachable(boolean reachable) { impl.nSetHostReachable(getPeer(), reachable); }

    protected abstract void releaseResources();

    // Note: the reference in the BOUND_REPLICATOR must already be gone, or we wouldn't be here...
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LOG_DOMAIN); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            peer -> {
                releaseResources();
                BOUND_REPLICATORS.unbind(token);
                impl.nStop(peer);
                impl.nFree(peer);
            });
    }
}
