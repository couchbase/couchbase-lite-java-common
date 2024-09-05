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
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.sockets.MessageFraming;
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
    // Accept parent domain cookies: boolean
    public static final String REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES = "acceptParentDomainCookies";
    // Auth settings: Dict
    public static final String REPLICATOR_OPTION_AUTHENTICATION = "auth";
    // Proxy settings (Dict); see [3]]

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
    // Proxy authentications: user
    public static final String REPLICATOR_OPTION_PROXY_USER = "proxyUser";
    // Proxy authentications: password
    public static final String REPLICATOR_OPTION_PROXY_PASS = "proxyPassword";

    ////// auth.type values:
    // HTTP Basic (the default)
    public static final String AUTH_TYPE_BASIC = "Basic";
    // SG session cookie
    public static final String AUTH_TYPE_SESSION = "Session";
    public static final String AUTH_TYPE_OPEN_ID_CONNECT = "OpenID Connect";
    public static final String AUTH_TYPE_FACEBOOK = "Facebook";
    public static final String AUTH_TYPE_CLIENT_CERT = "Client Cert";

    private static final String ID = "JRepl@";

    //-------------------------------------------------------------------------
    // Types
    //-------------------------------------------------------------------------

    @FunctionalInterface
    public interface StatusListener {
        void statusChanged(@NonNull C4Replicator replicator, @NonNull C4ReplicatorStatus status);
    }

    @FunctionalInterface
    public interface DocEndsListener {
        void documentsEnded(@NonNull List<C4DocumentEnded> docEnds, boolean pushing);
    }

    ////// Native API

    public interface NativeImpl {
        @SuppressWarnings("PMD.ExcessiveParameterList")
        long nCreate(
            @NonNull String id,
            @NonNull ReplicationCollection[] collections,
            long db,
            @Nullable String scheme,
            @Nullable String host,
            int port,
            @Nullable String path,
            @Nullable String remoteDbName,
            int framing,
            boolean push,
            boolean pull,
            boolean continuous,
            @Nullable byte[] options,
            long replicatorToken,
            long socketFactoryToken)
            throws LiteCoreException;

        long nCreateLocal(
            @NonNull String id,
            @NonNull ReplicationCollection[] collections,
            long db,
            long targetDb,
            boolean push,
            boolean pull,
            boolean continuous,
            @Nullable byte[] options,
            long replicatorToken)
            throws LiteCoreException;

        long nCreateWithSocket(
            @NonNull String id,
            @NonNull ReplicationCollection[] collections,
            long db,
            long openSocket,
            @Nullable byte[] options,
            long replicatorToken)
            throws LiteCoreException;

        @NonNull
        C4ReplicatorStatus nGetStatus(long peer);
        void nStart(long peer, boolean restart);
        void nStop(long peer);
        void nSetOptions(long peer, @Nullable byte[] options);
        @NonNull
        FLSliceResult nGetPendingDocIds(long peer, @NonNull String scope, @NonNull String collection)
            throws LiteCoreException;
        boolean nIsDocumentPending(long peer, @NonNull String id, @NonNull String scope, @NonNull String collection)
            throws LiteCoreException;
        void nSetProgressLevel(long peer, int progressLevel) throws LiteCoreException;
        void nSetHostReachable(long peer, boolean reachable);
        void nFree(long peer);
    }

    ////// Message Endpoint Replicator

    static final class C4MessageEndpointReplicator extends C4Replicator {
        // Protect this socket from the GC.
        @SuppressWarnings("FieldCanBeLocal")
        @NonNull
        private final C4Socket c4Socket;

        C4MessageEndpointReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull C4Socket c4Socket,
            @NonNull List<ReplicationCollection> colls,
            @NonNull StatusListener statusListener) {
            super(impl, peer, token, colls, statusListener);
            this.c4Socket = c4Socket;
        }

        @Override
        protected void releaseResources() { }

        @Override
        protected void documentsEnded(@NonNull List<C4DocumentEnded> docEnds, boolean pushing) {
            Log.d(LOG_DOMAIN, "%s: Unsupported call to doc ended for MessageEndpoint", getReplId());
        }

        @NonNull
        @Override
        public String toString() {
            return "C4MessageEndpointRepl{" + getReplId() + "/" + super.toString() + ", " + statusListener + "'}";
        }
    }

    ////// Standard Replicator

    static final class C4CommonReplicator extends C4Replicator {
        @NonNull
        private final DocEndsListener docEndsListener;

        @NonNull
        private final AbstractReplicator replicator;

        @Nullable
        private final SocketFactory socketFactory;
        private final long socketFactoryToken;


        C4CommonReplicator(
            @NonNull NativeImpl impl,
            long peer,
            long token,
            @NonNull List<ReplicationCollection> colls,
            @NonNull StatusListener statusListener,
            @NonNull DocEndsListener docEndsListener,
            @NonNull AbstractReplicator replicator,
            @Nullable SocketFactory socketFactory,
            long socketFactoryToken) {
            super(impl, peer, token, colls, statusListener);

            this.docEndsListener = docEndsListener;

            this.replicator = Preconditions.assertNotNull(replicator, "replicator");

            this.socketFactory = socketFactory;
            this.socketFactoryToken = socketFactoryToken;
        }

        @Override
        protected void documentsEnded(@NonNull List<C4DocumentEnded> docEnds, boolean pushing) {
            docEndsListener.documentsEnded(docEnds, pushing);
        }

        @Override
        protected void releaseResources() { BaseSocketFactory.unbindSocketFactory(socketFactoryToken); }

        @NonNull
        @Override
        public String toString() {
            return "C4CommonRepl{" + ClassUtils.objId(this) + "/" + super.toString() + ", "
                // don't try to stringify the replicator: it stringifies this
                + ClassUtils.objId(replicator) + ", " + socketFactory + "'}";
        }
    }

    //-------------------------------------------------------------------------
    // Static fields
    //-------------------------------------------------------------------------
    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Replicator();

    // Lookup table: maps a random token (context) to its companion Java C4Replicator
    @VisibleForTesting
    @NonNull
    static final TaggedWeakPeerBinding<C4Replicator> BOUND_REPLICATORS = new TaggedWeakPeerBinding<>();

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void statusChangedCallback(long token, @Nullable C4ReplicatorStatus status) {
        final C4Replicator c4Repl = BOUND_REPLICATORS.getBinding(token);
        final String id = (c4Repl == null) ? "???@" + token : c4Repl.getReplId();
        Log.d(LOG_DOMAIN, "C4Replicator(%s).statusChangedCallback: %s", id, status);

        if ((c4Repl == null)  || (status == null)) { return; }

        c4Repl.statusChanged(c4Repl, status);
    }

    // This method is called by reflection.  Don't change its signature.
    static void documentEndedCallback(long token, boolean pushing, @Nullable C4DocumentEnded... docEnds) {
        final C4Replicator c4Repl = BOUND_REPLICATORS.getBinding(token);
        final String id = (c4Repl == null) ? "???@" + token : c4Repl.getReplId();
        final int nDocs = (docEnds == null) ? 0 : docEnds.length;

        Log.d(LOG_DOMAIN, "C4Replicator(%s).documentEndedCallback: %d (%s)", id, nDocs, pushing);

        if ((c4Repl == null)  || (nDocs <= 0)) { return; }

        c4Repl.documentsEnded(Arrays.asList(docEnds), pushing);
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
        @NonNull StatusListener statusListener,
        @NonNull DocEndsListener docEndsListener,
        @NonNull AbstractReplicator replicator,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        return createRemoteReplicator(
            NATIVE_IMPL,
            collections,
            db,
            scheme,
            host,
            port,
            path,
            remoteDbName,
            framing,
            type,
            continuous,
            options,
            statusListener,
            docEndsListener,
            replicator,
            socketFactory);
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createRemoteReplicator(
        @NonNull NativeImpl impl,
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
        @NonNull StatusListener statusListener,
        @NonNull DocEndsListener docEndsListener,
        @NonNull AbstractReplicator replicator,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        final long replToken = BOUND_REPLICATORS.reserveKey();
        final long sfToken = (socketFactory == null) ? 0L : BaseSocketFactory.bindSocketFactory(socketFactory);

        final ReplicationCollection[] colls = ReplicationCollection.createAll(collections);

        final long peer = impl.nCreate(
            ID + replToken,
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
            ((options == null) || (options.isEmpty())) ? null : FLEncoder.encodeMap(options),
            replToken,
            sfToken);

        final C4Replicator c4Replicator = new C4CommonReplicator(
            impl,
            peer,
            replToken,
            Arrays.asList(colls),
            statusListener,
            docEndsListener,
            replicator,
            socketFactory,
            sfToken);

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
        @NonNull StatusListener statusListener,
        @NonNull DocEndsListener docEndsListener,
        @NonNull AbstractReplicator replicator)
        throws LiteCoreException {
        return createLocalReplicator(
            NATIVE_IMPL,
            collections,
            db,
            targetDb,
            type,
            continuous,
            options,
            statusListener,
            docEndsListener,
            replicator);
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createLocalReplicator(
        @NonNull NativeImpl impl,
        @NonNull Map<Collection, CollectionConfiguration> collections,
        long db,
        @NonNull C4Database targetDb,
        @NonNull ReplicatorType type,
        boolean continuous,
        @Nullable Map<String, Object> options,
        @NonNull StatusListener statusListener,
        @NonNull DocEndsListener docEndsListener,
        @NonNull AbstractReplicator replicator)
        throws LiteCoreException {
        final long token = BOUND_REPLICATORS.reserveKey();

        final ReplicationCollection[] colls = ReplicationCollection.createAll(collections);

        final long peer = targetDb.withPeerOrThrow(dbPeer ->
            impl.nCreateLocal(
                ID + token,
                colls,
                db,
                dbPeer,
                (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PUSH),
                (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PULL),
                continuous,
                ((options == null) || (options.isEmpty())) ? null : FLEncoder.encodeMap(options),
                token));

        final C4Replicator c4Replicator = new C4CommonReplicator(
            impl,
            peer,
            token,
            Arrays.asList(colls),
            statusListener,
            docEndsListener,
            replicator,
            null,
            0L);

        BOUND_REPLICATORS.bind(token, c4Replicator);

        return c4Replicator;
    }

    @NonNull
    static C4Replicator createMessageEndpointReplicator(
        @NonNull Set<Collection> collections,
        long db,
        @NonNull C4Socket c4Socket,
        @Nullable Map<String, Object> options,
        @NonNull StatusListener statusListener)
        throws LiteCoreException {
        return createMessageEndpointReplicator(
            NATIVE_IMPL,
            collections,
            db,
            c4Socket,
            options,
            statusListener);
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createMessageEndpointReplicator(
        @NonNull NativeImpl impl,
        @NonNull Set<Collection> collections,
        long db,
        @NonNull C4Socket c4Socket,
        @Nullable Map<String, Object> options,
        @NonNull StatusListener statusListener)
        throws LiteCoreException {
        final long token = BOUND_REPLICATORS.reserveKey();

        final ReplicationCollection[] colls = ReplicationCollection.createAll(collections);

        final long peer = impl.nCreateWithSocket(
            ID + token,
            colls,
            db,
            c4Socket.getPeerHandle(),
            ((options == null) || (options.isEmpty())) ? null : FLEncoder.encodeMap(options),
            token);

        final C4Replicator c4Replicator
            = new C4MessageEndpointReplicator(impl, peer, token, c4Socket, Arrays.asList(colls), statusListener);

        BOUND_REPLICATORS.bind(token, c4Replicator);

        return c4Replicator;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;

    @VisibleForTesting
    final long token;

    @VisibleForTesting
    @NonNull
    final List<ReplicationCollection> colls;

    @NonNull
    protected final StatusListener statusListener;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4Replicator(
        @NonNull NativeImpl impl,
        long peer,
        long token,
        @NonNull List<ReplicationCollection> colls,
        @NonNull StatusListener statusListener) {
        super(peer);

        this.impl = impl;
        this.token = Preconditions.assertNotZero(token, "token");
        this.colls = Preconditions.assertNotNull(colls, "collections");
        this.statusListener = Preconditions.assertNotNull(statusListener, "status listener");
    }

    //-------------------------------------------------------------------------
    // Instance Methods
    //-------------------------------------------------------------------------

    public void start(boolean restart) { withPeer(peer -> impl.nStart(peer, restart)); }

    public void stop() { withPeer(impl::nStop); }

    @Override
    public final void close() {
        for (ReplicationCollection coll: colls) { coll.close(); }
        closePeer(null);
    }

    @NonNull
    public String getReplId() { return ID + token; }

    public void setOptions(@Nullable byte[] options) { withPeer(peer -> impl.nSetOptions(peer, options)); }

    @Nullable
    public C4ReplicatorStatus getStatus() { return withPeerOrNull(impl::nGetStatus); }

    public boolean isDocumentPending(@NonNull String docId, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        return withPeerOrDefault(false, peer -> impl.nIsDocumentPending(peer, docId, scope, collection));
    }

    @NonNull
    public Set<String> getPendingDocIDs(@NonNull String scope, @NonNull String collection) throws LiteCoreException {
        final FLSliceResult result = withPeerOrNull(peer -> impl.nGetPendingDocIds(peer, scope, collection));
        final FLValue slice = FLValue.fromData(result);
        return (slice == null) ? Collections.emptySet() : new HashSet<>(slice.asTypedArray());
    }

    public void setProgressLevel(int level) throws LiteCoreException {
        withPeer(peer -> impl.nSetProgressLevel(peer, level));
    }

    public void setHostReachable(boolean reachable) { withPeer(peer -> impl.nSetHostReachable(peer, reachable)); }

    protected abstract void releaseResources();

    protected abstract void documentsEnded(@NonNull List<C4DocumentEnded> docEnds, boolean pushing);

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LOG_DOMAIN); }
        finally { super.finalize(); }
    }

    void statusChanged(@NonNull C4Replicator replicator, @NonNull C4ReplicatorStatus status) {
        statusListener.statusChanged(replicator, status);
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

                // It might be better to queue this stuff to be done on another thread...
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) {
                    nativeImpl.nStop(peer);
                    nativeImpl.nFree(peer);
                }
            });
    }
}
