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

package com.couchbase.lite.internal.core;

import android.support.annotation.CallSuper;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
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
@SuppressWarnings({"PMD.GodClass", "PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal", "LineLength"})
public class C4Replicator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constants
    //
    // Most of these are defined in c4Replicator.h and must agree with those definitions.
    //
    // @formatter:off
    //-------------------------------------------------------------------------
    public static final String WEBSOCKET_SCHEME = "ws";
    public static final String WEBSOCKET_SECURE_CONNECTION_SCHEME = "wss";
    public static final String MESSAGE_SCHEME = "x-msg-endpt";

    public static final String C4_REPLICATOR_SCHEME_2 = "blip";
    public static final String C4_REPLICATOR_TLS_SCHEME_2 = "blips";

    // Replicator option dictionary keys:
    public static final String REPLICATOR_OPTION_DOC_IDS = "docIDs"; // Docs to replicate: string[]
    public static final String REPLICATOR_OPTION_CHANNELS = "channels"; // SG channel names: string[]
    public static final String REPLICATOR_OPTION_FILTER = "filter"; // Filter name: string
    public static final String REPLICATOR_OPTION_FILTER_PARAMS = "filterParams"; // Filter params: Dict[string]
    public static final String REPLICATOR_OPTION_SKIP_DELETED = "skipDeleted"; // Don't push/pull tombstones: bool
    public static final String REPLICATOR_OPTION_NO_INCOMING_CONFLICTS = "noIncomingConflicts"; // Reject incoming conflicts: bool
    public static final String REPLICATOR_OPTION_OUTGOING_CONFLICTS = "outgoingConflicts"; // Allow creating conflicts on remote: bool
    public static final String REPLICATOR_CHECKPOINT_INTERVAL = "checkpointInterval"; // How often to checkpoint, in seconds: number
    public static final String REPLICATOR_OPTION_REMOTE_DB_UNIQUE_ID = "remoteDBUniqueID"; // Stable ID for remote db with unstable URL: string
    public static final String REPLICATOR_OPTION_PROGRESS_LEVEL = "progress"; // If >=1, notify on every doc; if >=2, on every attachment (int)
    public static final String REPLICATOR_OPTION_DISABLE_DELTAS = "noDeltas";   ///< Disables delta sync: bool
    public static final String REPLICATOR_OPTION_MAX_RETRIES = "maxRetries";   ///< Max number of retry attempts (int)
    public static final String REPLICATOR_OPTION_MAX_RETRY_INTERVAL = "maxRetryInterval";  ///< Max delay between retries (secs)
    public static final String REPLICATOR_OPTION_ENABLE_AUTO_PURGE = "autoPurge";  /// true, Enable auto-purge


    public static final String REPLICATOR_OPTION_ROOT_CERTS = "rootCerts";  ///< Trusted root certs (data)
    public static final String REPLICATOR_OPTION_PINNED_SERVER_CERT = "pinnedCert";  // Cert or public key: [data]
    public static final String REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT = "onlySelfSignedServer";  ///< Only accept self signed server certs (for P2P, bool)

    public static final String REPLICATOR_OPTION_EXTRA_HEADERS = "headers";  // Extra HTTP headers: string[]
    public static final String REPLICATOR_OPTION_COOKIES = "cookies";  // HTTP Cookie header value: string
    public static final String REPLICATOR_OPTION_AUTHENTICATION = "auth";  // Auth settings: Dict
    public static final String REPLICATOR_OPTION_PROXY_SERVER = "proxy";   ///< Proxy settings (Dict); see [3]]

    // WebSocket protocol options (WebSocketInterface.hh)
    public static final String REPLICATOR_HEARTBEAT_INTERVAL = "heartbeat"; // Interval in secs to send a keep-alive: ping
    public static final String SOCKET_OPTION_WS_PROTOCOLS = "WS-Protocols"; ///< Sec-WebSocket-Protocol header value
    static final String REPLICATOR_AUTH_OPTION = "auth";       // Auth settings: Dict
    // Auth dictionary keys:
    public static final String REPLICATOR_AUTH_TYPE = "type"; ///< Auth type; see [2] (string)
    public static final String REPLICATOR_AUTH_USER_NAME = "username"; ///< User name for basic auth (string)
    public static final String REPLICATOR_AUTH_PASSWORD = "password"; ///< Password for basic auth (string)
    public static final String REPLICATOR_AUTH_CLIENT_CERT = "clientCert"; ///< TLS client certificate (value platform-dependent)
    public static final String REPLICATOR_AUTH_CLIENT_CERT_KEY = "clientCertKey"; ///< Client cert's private key (data)
    public static final String REPLICATOR_AUTH_TOKEN = "token"; ///< Session cookie or auth token (string)

    // auth.type values:
    public static final String AUTH_TYPE_BASIC = "Basic"; // HTTP Basic (the default)
    public static final String AUTH_TYPE_SESSION = "Session"; // SG session cookie
    public static final String AUTH_TYPE_OPEN_ID_CONNECT = "OpenID Connect";
    public static final String AUTH_TYPE_FACEBOOK = "Facebook";
    public static final String AUTH_TYPE_CLIENT_CERT = "Client Cert";
    // @formatter:on

    // values for enum C4ReplicatorProgressLevel
    public static final int PROGRESS_OVERALL = 0;
    public static final int PROGRESS_PER_DOC = 1;
    public static final int PROGRESS_PER_ATTACHMENT = 2;

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // This lock protects both of the maps below and the corresponding vector in the JNI code
    private static final Object CLASS_LOCK = new Object();

    // Long: handle to C4Replicator's native peer
    // C4Replicator: The Java peer (the instance holding the handle that is the key)
    @NonNull
    @GuardedBy("CLASS_LOCK")
    private static final Map<Long, C4Replicator> REVERSE_LOOKUP_TABLE = new HashMap<>();

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void statusChangedCallback(long peer, @Nullable C4ReplicatorStatus status) {
        final C4Replicator repl = getReplicatorForHandle(peer);
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(
                LogDomain.REPLICATOR,
                "C4Replicator.statusChangedCallback @0x%s, status: %s", Long.toHexString(peer), status);
        }
        if (repl == null) { return; }

        final C4ReplicatorListener listener = repl.listener;
        if (listener != null) { listener.statusChanged(repl, status, repl.replicatorContext); }
    }

    // This method is called by reflection.  Don't change its signature.
    static void documentEndedCallback(long peer, boolean pushing, @Nullable C4DocumentEnded... documentsEnded) {
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(
                LogDomain.REPLICATOR,
                "C4Replicator.documentEndedCallback @0x%s, pushing: %s", Long.toHexString(peer), pushing);
        }

        final C4Replicator repl = getReplicatorForHandle(peer);
        if (repl == null) { return; }

        final C4ReplicatorListener listener = repl.listener;
        if (listener != null) { listener.documentEnded(repl, pushing, documentsEnded, repl.replicatorContext); }
    }

    // This method is called by reflection.  Don't change its signature.
    // Supported only for Replicators
    // This method is called from a native thread that Java has never even heard of...
    static boolean validationFunction(
        @Nullable String docID,
        @Nullable String revID,
        int flags,
        long dict,
        boolean isPush,
        Object ctxt) {
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(
                LogDomain.REPLICATOR,
                "Running %s filter for doc %s@%s, repl %s",
                (isPush ? "push" : "pull"),
                docID,
                revID,
                ctxt);
        }

        if (!(ctxt instanceof AbstractReplicator)) {
            Log.w(LogDomain.DATABASE, "Validation function called with unrecognized context: " + ctxt);
            return true;
        }
        final AbstractReplicator repl = (AbstractReplicator) ctxt;

        final C4Replicator c4Repl = repl.getC4Replicator(); // Try that, Kotlin!!
        if (c4Repl == null) { return true; }

        final C4ReplicationFilter filter = (isPush) ? c4Repl.pushFilter : c4Repl.pullFilter;
        return (filter == null) || filter.validationFunction(docID, revID, flags, dict, isPush, repl);
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
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext,
        @Nullable SocketFactory socketFactoryContext,
        int framing)
        throws LiteCoreException {
        final C4Replicator replicator;
        synchronized (CLASS_LOCK) {
            replicator = new C4Replicator(
                db,
                scheme,
                host,
                port,
                path,
                remoteDatabaseName,
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicatorContext,
                socketFactoryContext,
                framing);
            bind(replicator);
        }

        return replicator;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createLocalReplicator(
        long db,
        @NonNull C4Database otherLocalDB,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext)
        throws LiteCoreException {
        final C4Replicator replicator;
        synchronized (CLASS_LOCK) {
            replicator = new C4Replicator(
                db,
                otherLocalDB.getHandle(),
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicatorContext);
            bind(replicator);
        }

        return replicator;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createTargetReplicator(
        long db,
        @NonNull C4Socket openSocket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {
        final C4Replicator replicator;
        synchronized (CLASS_LOCK) {
            replicator = new C4Replicator(
                db,
                openSocket.getPeerHandle(),
                push,
                pull,
                options,
                listener,
                replicatorContext);
            bind(replicator);
        }

        return replicator;
    }

    //-------------------------------------------------------------------------
    // Private static methods
    //-------------------------------------------------------------------------

    @Nullable
    private static C4Replicator getReplicatorForHandle(long peer) {
        synchronized (CLASS_LOCK) { return REVERSE_LOOKUP_TABLE.get(peer); }
    }

    @GuardedBy("CLASS_LOCK")
    private static void bind(@NonNull C4Replicator repl) {
        Preconditions.assertNotNull(repl, "repl");
        final long peer = repl.getPeer();
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(
                LogDomain.REPLICATOR,
                "Binding native replicator @0x%s  => %s", Long.toHexString(peer), ClassUtils.objId(repl));
        }
        REVERSE_LOOKUP_TABLE.put(peer, repl);
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final Object replicatorContext;
    @SuppressFBWarnings("SE_BAD_FIELD")
    @Nullable
    private final SocketFactory socketFactoryContext;

    @Nullable
    private final C4ReplicatorListener listener;

    @Nullable
    private final C4ReplicationFilter pushFilter;
    @Nullable
    private final C4ReplicationFilter pullFilter;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    // Remote
    @SuppressWarnings("PMD.ExcessiveParameterList")
    C4Replicator(
        long db,
        @Nullable String schema,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDatabaseName,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext,
        @Nullable SocketFactory socketFactoryContext,
        int framing)
        throws LiteCoreException {
        super(create(
            db,
            schema,
            host,
            port,
            path,
            remoteDatabaseName,
            push,
            pull,
            socketFactoryContext,
            framing,
            replicatorContext,
            pushFilter,
            pullFilter,
            options));

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.socketFactoryContext = socketFactoryContext;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;
    }

    // Local
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private C4Replicator(
        long db,
        long targetDb,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext)
        throws LiteCoreException {
        super(createLocal(
            db,
            targetDb,
            push,
            pull,
            C4Socket.NO_FRAMING,
            replicatorContext,
            pushFilter,
            pullFilter,
            options));

        this.socketFactoryContext = null;

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;
    }

    // Target
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private C4Replicator(
        long db,
        long socket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {
        super(createWithSocket(db, socket, push, pull, replicatorContext, options));

        this.socketFactoryContext = null;

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.pushFilter = null;
        this.pullFilter = null;
    }

    public void start(boolean restart) { start(getPeer(), restart); }

    public void stop() { stop(getPeer()); }

    @CallSuper
    @Override
    public void close() {
        REVERSE_LOOKUP_TABLE.remove(getPeerUnchecked());
        closePeer(null);
    }

    public void setOptions(@NonNull byte[] options) { setOptions(getPeer(), options); }

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
    public String toString() { return "C4Repl{" + ClassUtils.objId(this) + "/" + super.toString() + "'}"; }

    // Note: the reference in the REVERSE_LOOKUP_TABLE must already be gone, or we wouldn't be here...
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.REPLICATOR); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            peer -> {
                stop(peer);
                free(peer, replicatorContext, socketFactoryContext);
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
        Object socketFactoryContext,
        int framing,
        Object replicatorContext,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
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
        int framing,
        Object replicatorContext,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        byte[] options)
        throws LiteCoreException;

    /**
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections.  Wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     *
     * @param db                The local database.
     * @param openSocket        An already-created C4Socket.
     * @param push              boolean: push replication
     * @param pull              boolean: pull replication
     * @param replicatorContext context object
     * @param options           flags
     * @return The pointer of the newly created replicator
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long createWithSocket(
        long db,
        long openSocket,
        int push,
        int pull,
        Object replicatorContext,
        byte[] options)
        throws LiteCoreException;

    /**
     * Frees a replicator reference. If the replicator is running it will stop.
     */
    private static native void free(long replicator, Object replicatorContext, Object socketFactoryContext);

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
