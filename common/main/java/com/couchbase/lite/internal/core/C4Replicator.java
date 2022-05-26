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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.BaseSocketFactory;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.impl.NativeC4Replicator;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.exec.ClientTask;
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

    public interface NativeImpl {
        @SuppressWarnings("PMD.ExcessiveParameterList")
        long nCreate(
            long db,
            String scheme,
            String host,
            int port,
            String path,
            String remoteDbName,
            int push,
            int pull,
            int framing,
            byte[] options,
            boolean hasPushFilter,
            boolean hasPullFilter,
            long replicatorToken,
            long socketFactoryToken)
            throws LiteCoreException;

        long nCreateLocal(
            long db,
            long targetDb,
            int push,
            int pull,
            byte[] options,
            boolean hasPushFilter,
            boolean hasPullFilter,
            long replicatorToken)
            throws LiteCoreException;

        long nCreateWithSocket(
            long db,
            long openSocket,
            int push,
            int pull,
            byte[] options,
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

    static final class C4MessageEndpointReplicator extends C4Replicator {
        C4MessageEndpointReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull ReplicatorListener listener) {
            super(impl, peer, token, listener);
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

    static final class C4ReplicatorReplicator extends C4Replicator {
        @NonNull
        private final AbstractReplicator replicator;

        @Nullable
        private final C4ReplicationFilter pushFilter;
        @Nullable
        private final C4ReplicationFilter pullFilter;

        @Nullable
        private final SocketFactory socketFactory;
        private final long socketFactoryToken;

        C4ReplicatorReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull ReplicatorListener listener,
            @NonNull AbstractReplicator replicator,
            @Nullable C4ReplicationFilter pushFilter,
            @Nullable C4ReplicationFilter pullFilter) {
            this(impl, peer, token, listener, replicator, pushFilter, pullFilter, null, 0L);
        }

        C4ReplicatorReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull ReplicatorListener listener,
            @NonNull AbstractReplicator replicator,
            @Nullable C4ReplicationFilter pushFilter,
            @Nullable C4ReplicationFilter pullFilter,
            @Nullable SocketFactory socketFactory,
            long socketFactoryToken) {
            super(impl, peer, token, listener);

            this.replicator = Preconditions.assertNotNull(replicator, "replicator");

            this.pushFilter = pushFilter;
            this.pullFilter = pullFilter;

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
                + ", " + pushFilter + ", " + pullFilter + ", "
                + socketFactory + "'}";
        }
    }

    //-------------------------------------------------------------------------
    // Static fields
    //-------------------------------------------------------------------------
    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Replicator();

    // Lookup table: maps a handle to a peer native socket to its Java companion
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

    // This method is called by reflection.  Don't change its signature.
    // It is called from a native thread that Java has never even heard of...
    static boolean filterCallback(
        long replicatorToken,
        @Nullable String docID,
        @Nullable String revID,
        int flags,
        long dict,
        boolean isPush) {
        final C4Replicator repl = BOUND_REPLICATORS.getBinding(replicatorToken);
        Log.d(
            LOG_DOMAIN,
            "Running %s filter for doc %s@%s, %s@%s",
            (isPush ? "push" : "pull"),
            docID,
            revID,
            replicatorToken,
            repl);
        // supported only for replicators
        if (!(repl instanceof C4ReplicatorReplicator)) { return true; }
        final C4ReplicatorReplicator c4Repl = (C4ReplicatorReplicator) repl;

        final C4ReplicationFilter filter = (isPush) ? c4Repl.pushFilter : c4Repl.pullFilter;
        if (filter == null) { return true; }

        final ClientTask<Boolean> task
            = new ClientTask<>(() -> filter.validationFunction(docID, revID, flags, dict, isPush, c4Repl.replicator));
        task.execute();

        final Exception err = task.getFailure();
        if (err != null) {
            Log.w(LOG_DOMAIN, "Replication filter failed", err);
            return false;
        }

        final Boolean accepted = task.getResult();
        return (accepted != null) && accepted;
    }

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createRemoteReplicator(
        long db,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDbName,
        int push,
        int pull,
        @NonNull MessageFraming framing,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener,
        @NonNull AbstractReplicator replicator,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        final long replicatorToken = BOUND_REPLICATORS.reserveKey();
        final long sfToken = (socketFactory == null) ? 0L : BaseSocketFactory.bindSocketFactory(socketFactory);

        final long peer = NATIVE_IMPL.nCreate(
            db,
            scheme,
            host,
            port,
            path,
            remoteDbName,
            push,
            pull,
            MessageFraming.getC4Framing(framing),
            options,
            pushFilter != null,
            pullFilter != null,
            replicatorToken,
            sfToken);

        final C4Replicator c4Replicator = new C4ReplicatorReplicator(
            NATIVE_IMPL,
            peer,
            replicatorToken,
            listener,
            replicator,
            pushFilter,
            pullFilter,
            socketFactory,
            sfToken
        );

        BOUND_REPLICATORS.bind(replicatorToken, c4Replicator);

        return c4Replicator;
    }

    @NonNull
    static C4Replicator createLocalReplicator(
        long db,
        @NonNull C4Database targetDb,
        int push,
        int pull,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener,
        @NonNull AbstractReplicator replicator,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter)
        throws LiteCoreException {
        final long replicatorToken = BOUND_REPLICATORS.reserveKey();

        final long peer = NATIVE_IMPL.nCreateLocal(
            db,
            targetDb.getHandle(),
            push,
            pull,
            options,
            pushFilter != null,
            pullFilter != null,
            replicatorToken);

        final C4Replicator c4Replicator = new C4ReplicatorReplicator(
            NATIVE_IMPL,
            peer,
            replicatorToken,
            listener,
            replicator,
            pushFilter,
            pullFilter);

        BOUND_REPLICATORS.bind(replicatorToken, c4Replicator);

        return c4Replicator;
    }

    @NonNull
    static C4Replicator createMessageEndpointReplicator(
        long db,
        @NonNull C4Socket c4Socket,
        int push,
        int pull,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener)
        throws LiteCoreException {
        final long replicatorToken = BOUND_REPLICATORS.reserveKey();

        final long peer = NATIVE_IMPL.nCreateWithSocket(
            db,
            c4Socket.getPeerHandle(),
            push,
            pull,
            options,
            replicatorToken);

        final C4Replicator c4Replicator = new C4MessageEndpointReplicator(
            NATIVE_IMPL,
            peer,
            replicatorToken,
            listener);

        BOUND_REPLICATORS.bind(replicatorToken, c4Replicator);

        return c4Replicator;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;

    private final long token;

    @NonNull
    protected final ReplicatorListener listener;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4Replicator(@NonNull NativeImpl impl, long peer, long token, @NonNull ReplicatorListener listener) {
        super(peer);

        this.impl = impl;

        this.token = Preconditions.assertNotZero(token, "token");

        this.listener = Preconditions.assertNotNull(listener, "listener");
    }

    //-------------------------------------------------------------------------
    // Instance Methods
    //-------------------------------------------------------------------------

    public void start(boolean restart) { impl.nStart(getPeer(), restart); }

    public void stop() { impl.nStop(getPeer()); }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

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
