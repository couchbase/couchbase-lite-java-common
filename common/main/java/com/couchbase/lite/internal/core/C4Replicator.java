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
 * various listeners.  Until a replicator object is removed from the REVERSE_LOOKUP_TABLE
 * forwarding such a message must work.
 * <li/> Calls to the native object:  These should work as long as the peer handle is non-zero.
 * This object must be careful never to forward a call to a native object once that object has been freed.
 * </ol>
 * <p>
 * Instances of this class are created using static factory methods
 * <p>
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public final class C4Replicator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constants
    //
    // Most of these are defined in c4Replicator.h and must agree with those definitions.
    //
    //-------------------------------------------------------------------------
    public static final LogDomain LOG_DOMAIN = LogDomain.REPLICATOR;


    public static final String WEBSOCKET_SCHEME = "ws";
    public static final String WEBSOCKET_SECURE_CONNECTION_SCHEME = "wss";
    public static final String MESSAGE_SCHEME = "x-msg-endpt";

    public static final String C4_REPLICATOR_SCHEME_2 = "blip";
    public static final String C4_REPLICATOR_TLS_SCHEME_2 = "blips";

    ////// Replicator option dictionary keys:

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
    // Stable ID for remote db with unstable URL: string
    public static final String REPLICATOR_OPTION_REMOTE_DB_UNIQUE_ID = "remoteDBUniqueID";
    // < Disables delta sync: bool
    public static final String REPLICATOR_OPTION_DISABLE_DELTAS = "noDeltas";
    // < Max number of retry attempts (int)
    public static final String REPLICATOR_OPTION_MAX_RETRIES = "maxRetries";
    // < Max delay between retries (secs)
    public static final String REPLICATOR_OPTION_MAX_RETRY_INTERVAL = "maxRetryInterval";
    // true, Enable auto-purge
    public static final String REPLICATOR_OPTION_ENABLE_AUTO_PURGE = "autoPurge";


    // < Trusted root certs (data)
    public static final String REPLICATOR_OPTION_ROOT_CERTS = "rootCerts";
    // Cert or public key: [data]
    public static final String REPLICATOR_OPTION_PINNED_SERVER_CERT = "pinnedCert";
    // < Only accept self signed server certs (for P2P, bool)
    public static final String REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT = "onlySelfSignedServer";

    // Extra HTTP headers: string[]
    public static final String REPLICATOR_OPTION_EXTRA_HEADERS = "headers";
    // HTTP Cookie header value: string
    public static final String REPLICATOR_OPTION_COOKIES = "cookies";
    // Auth settings: Dict
    public static final String REPLICATOR_OPTION_AUTHENTICATION = "auth";
    // < Proxy settings (Dict); see [3]]
    public static final String REPLICATOR_OPTION_PROXY_SERVER = "proxy";

    ////// WebSocket protocol options (WebSocketInterface.hh)

    // Interval in secs to send a keep-alive: ping
    public static final String REPLICATOR_HEARTBEAT_INTERVAL = "heartbeat";
    // < Sec-WebSocket-Protocol header value
    public static final String SOCKET_OPTION_WS_PROTOCOLS = "WS-Protocols";
    // < Specific network interface (name or IP address) used for connecting to the remote server.
    public static final String SOCKET_OPTIONS_NETWORK_INTERFACE = "networkInterface";
    // Auth settings: Dict
    static final String REPLICATOR_AUTH_OPTION = "auth";

    ////// Auth dictionary keys:

    // < Auth type; see [2] (string)
    public static final String REPLICATOR_AUTH_TYPE = "type";
    // < User name for basic auth (string)
    public static final String REPLICATOR_AUTH_USER_NAME = "username";
    // < Password for basic auth (string)
    public static final String REPLICATOR_AUTH_PASSWORD = "password";
    // < TLS client certificate (value platform-dependent)
    public static final String REPLICATOR_AUTH_CLIENT_CERT = "clientCert";
    // < Client cert's private key (data)
    public static final String REPLICATOR_AUTH_CLIENT_CERT_KEY = "clientCertKey";
    // < Session cookie or auth token (string)
    public static final String REPLICATOR_AUTH_TOKEN = "token";

    ////// auth.type values:

    // HTTP Basic (the default)
    public static final String AUTH_TYPE_BASIC = "Basic";
    // SG session cookie
    public static final String AUTH_TYPE_SESSION = "Session";
    public static final String AUTH_TYPE_OPEN_ID_CONNECT = "OpenID Connect";
    public static final String AUTH_TYPE_FACEBOOK = "Facebook";
    public static final String AUTH_TYPE_CLIENT_CERT = "Client Cert";

    ////// values for enum C4ReplicatorProgressLevel

    public static final int PROGRESS_OVERALL = 0;
    public static final int PROGRESS_PER_DOC = 1;
    public static final int PROGRESS_PER_ATTACHMENT = 2;

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

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

        final ReplicatorListener listener = c4Repl.listener;
        if (listener != null) { listener.statusChanged(c4Repl, status); }
    }

    // This method is called by reflection.  Don't change its signature.
    static void documentEndedCallback(long token, boolean pushing, @Nullable C4DocumentEnded... documentsEnded) {
        final C4Replicator c4Repl = BOUND_REPLICATORS.getBinding(token);
        Log.d(LOG_DOMAIN, "C4Replicator.documentEndedCallback %s@0x%x: %s", c4Repl, token, pushing);
        if (c4Repl == null) { return; }

        final ReplicatorListener listener = c4Repl.listener;
        if (listener != null) { listener.documentEnded(c4Repl, pushing, documentsEnded); }
    }

    // This method is called by reflection.  Don't change its signature.
    // It is called from a native thread that Java has never even heard of...
    static boolean filterCallback(
        long token,
        @Nullable String docID,
        @Nullable String revID,
        int flags,
        long dict,
        boolean isPush) {
        final C4Replicator c4Repl = BOUND_REPLICATORS.getBinding(token);
        Log.d(
            LOG_DOMAIN,
            "Running %s filter for doc %s@%s, %s@%s",
            (isPush ? "push" : "pull"),
            docID,
            revID,
            token,
            c4Repl);
        if (c4Repl == null) { return true; }

        // supported only for replicators
        final AbstractReplicator repl = c4Repl.replicator;
        if (repl == null) { return true; }

        final C4ReplicationFilter filter = (isPush) ? c4Repl.pushFilter : c4Repl.pullFilter;
        if (filter == null) { return true; }

        final ClientTask<Boolean> task
            = new ClientTask<>(() -> filter.validationFunction(docID, revID, flags, dict, isPush, repl));
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
        @Nullable String remoteDatabaseName,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicator,
        @Nullable SocketFactory socketFactory,
        @NonNull MessageFraming framing)
        throws LiteCoreException {
        final long token = BOUND_REPLICATORS.reserveKey();
        final long sfToken = (socketFactory == null) ? 0L : BaseSocketFactory.bindSocketFactory(socketFactory);

        final long peer = create(
            db,
            scheme,
            host,
            port,
            path,
            remoteDatabaseName,
            push,
            pull,
            sfToken,
            MessageFraming.getC4Framing(framing),
            token,
            pushFilter != null,
            pullFilter != null,
            options);

        final C4Replicator c4eplicator
            = new C4Replicator(peer, token, replicator, listener, pushFilter, pullFilter, sfToken, socketFactory);

        BOUND_REPLICATORS.bind(token, c4eplicator);

        return c4eplicator;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createLocalReplicator(
        long db,
        @NonNull C4Database otherLocalDB,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicator)
        throws LiteCoreException {
        final long token = BOUND_REPLICATORS.reserveKey();

        final long peer = createLocal(
            db,
            otherLocalDB.getHandle(),
            push,
            pull,
            token,
            pushFilter != null,
            pullFilter != null,
            options);

        final C4Replicator c4Replicator = new C4Replicator(peer, token, replicator, listener, pushFilter, pullFilter);

        BOUND_REPLICATORS.bind(token, c4Replicator);

        return c4Replicator;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createTargetReplicator(
        long db,
        @NonNull C4Socket c4Socket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable ReplicatorListener listener)
        throws LiteCoreException {
        final long token = BOUND_REPLICATORS.reserveKey();

        final long peer = createWithSocket(db, c4Socket.getPeerHandle(), push, pull, token, options);

        final C4Replicator c4Replicator = new C4Replicator(peer, token, null, listener, null, null);

        BOUND_REPLICATORS.bind(token, c4Replicator);

        return c4Replicator;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final long token;

    @Nullable
    private final AbstractReplicator replicator;

    @Nullable
    private final ReplicatorListener listener;

    @Nullable
    private final C4ReplicationFilter pushFilter;
    @Nullable
    private final C4ReplicationFilter pullFilter;

    private final long sfToken;
    @Nullable
    private final SocketFactory socketFactory;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    C4Replicator(
        long peer,
        long token,
        @Nullable AbstractReplicator replicator,
        @Nullable ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter) {
        this(peer, token, replicator, listener, pushFilter, pullFilter, 0L, null);
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    C4Replicator(
        long peer,
        long token,
        @Nullable AbstractReplicator replicator,
        @Nullable ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        long sfToken,
        @Nullable SocketFactory socketFactory) {
        super(peer);

        this.token = Preconditions.assertNotZero(token, "token");

        this.replicator = replicator;

        this.listener = listener;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;

        this.sfToken = sfToken;
        this.socketFactory = socketFactory;
    }

    //-------------------------------------------------------------------------
    // Instance Methods
    //-------------------------------------------------------------------------

    public void start(boolean restart) { start(getPeer(), restart); }

    public void stop() { stop(getPeer()); }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    @Nullable
    public AbstractReplicator getReplicator() { return replicator; }

    public void setOptions(@Nullable byte[] options) { setOptions(getPeer(), options); }

    @Nullable
    public C4ReplicatorStatus getStatus() { return getStatus(getPeer()); }

    public boolean isDocumentPending(String docId) throws LiteCoreException {
        return isDocumentPending(getPeer(), docId);
    }

    @NonNull
    public Set<String> getPendingDocIDs() throws LiteCoreException {
        try (FLSliceResult result = FLSliceResult.getManagedSliceResult(getPendingDocIds(getPeer()))) {
            final FLValue slice = FLValue.fromData(result);
            return (slice == null) ? Collections.emptySet() : new HashSet<>(slice.asTypedArray());
        }
    }

    public void setProgressLevel(int level) throws LiteCoreException { setProgressLevel(getPeer(), level); }

    public void setHostReachable(boolean reachable) { setHostReachable(getPeer(), reachable); }

    @NonNull
    @Override
    public String toString() {
        return "C4Repl{" + ClassUtils.objId(this) + "/" + super.toString()
            // don't try to stringify the replicator: it stringifies this
            + ": " + ((replicator == null) ? "null" : ClassUtils.objId(replicator)) + ", "
            + listener + ", " + pushFilter + ", " + pullFilter + ", " + socketFactory + "'}";
    }

    // Note: the reference in the REVERSE_LOOKUP_TABLE must already be gone, or we wouldn't be here...
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
                BaseSocketFactory.unbindSocketFactory(sfToken);
                BOUND_REPLICATORS.unbind(token);
                stop(peer);
                free(peer);
            });
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Creates a new replicator.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long create(
        long db,
        String schema,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        int push,
        int pull,
        long sfToken,
        int framing,
        long token,
        boolean pushFilter,
        boolean pullFilter,
        byte[] options)
        throws LiteCoreException;

    /**
     * Creates a new local replicator.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long createLocal(
        long db,
        long targetDb,
        int push,
        int pull,
        long token,
        boolean pushFilter,
        boolean pullFilter,
        byte[] options)
        throws LiteCoreException;

    /**
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections.  Wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     *
     * @param db         The local database.
     * @param openSocket An already-created C4Socket.
     * @param push       boolean: push replication
     * @param pull       boolean: pull replication
     * @param options    flags
     * @return The pointer of the newly created replicator
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long createWithSocket(
        long db,
        long openSocket,
        int push,
        int pull,
        long token,
        byte[] options)
        throws LiteCoreException;

    /**
     * Frees a replicator reference. If the replicator is running it will stop.
     */
    private static native void free(long replicator);

    /**
     * Tells a replicator to start.
     */
    private static native void start(long replicator, boolean restart);

    /**
     * Tells a replicator to stop.
     */
    private static native void stop(long replicator);

    /**
     * Set the replicator options.
     */
    private static native void setOptions(long replicator, byte[] options);

    /**
     * Returns the current state of a replicator.
     */
    @NonNull
    private static native C4ReplicatorStatus getStatus(long replicator);

    /**
     * Returns a list of string ids for pending documents.
     */
    private static native long getPendingDocIds(long peer) throws LiteCoreException;

    /**
     * Returns true if there are documents that have not been resolved.
     */
    private static native boolean isDocumentPending(long peer, String id) throws LiteCoreException;

    /**
     * Set the core progress callback level.
     */
    private static native void setProgressLevel(long peer, int progressLevel) throws LiteCoreException;

    /**
     * Hint to core about the reachability of the target of this replicator.
     */
    private static native void setHostReachable(long peer, boolean reachable);
}
