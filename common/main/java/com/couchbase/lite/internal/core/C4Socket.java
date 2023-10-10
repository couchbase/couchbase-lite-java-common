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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.BaseSocketFactory;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.impl.NativeC4Socket;
import com.couchbase.lite.internal.core.peers.WeakPeerBinding;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.sockets.CBLSocketException;
import com.couchbase.lite.internal.sockets.CloseStatus;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.sockets.SocketFromCore;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * In the logs:
 * <ul>
 * <li>^ prefix is a call outbound from core
 * <li>v prefix is a call inbound from the remote
 * </ul>
 * <p>
 * The use of "fromCore" in this method may be confusing.
 * *This* class is the thing that is closely attached to core.
 * An implementation of SocketFromCore is this class' delegate for
 * events outbound, from core to some client.
 * Somewhere out there, an implementation of SocketToCore will hold
 * a reference to this object, and use it for sending events to core.
 */
public final class C4Socket extends C4NativePeer implements SocketToCore {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    //-------------------------------------------------------------------------
    // Types
    //-------------------------------------------------------------------------

    public interface NativeImpl {
        void nRetain(long peer);
        long nFromNative(long token, String schema, String host, int port, String path, int framing);
        void nOpened(long peer);
        void nGotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece);
        void nCompletedWrite(long peer, long byteCount);
        void nReceived(long peer, byte[] data);
        void nCloseRequested(long peer, int status, @Nullable String message);
        void nClosed(long peer, int errorDomain, int errorCode, String message);
    }

    @FunctionalInterface
    interface SocketTask {
        void accept(C4Socket socket, SocketFromCore fromCore);
    }

    /**
     * Email exchange, 2022/5/17
     *
     * Blake:
     * Is the reference that the JNI gets when it creates [such an] object (which the Java code stores as
     * a `long`) *always* the same reference that that object will pass as a parameter when it does a callback
     * to the Java code e.g. kSocketFactory.write or C4ReplicatorParameters.C4ReplicatorDocumentsEndedCallback.
     *
     * Jim:
     * Yes this is a safe assumption
     */
    public static class NativeRefPeerBinding<T> extends WeakPeerBinding<T> { }

    //-------------------------------------------------------------------------
    // Static fields
    //-------------------------------------------------------------------------
    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Socket();

    // Lookup table: maps a handle to a peer native socket to its Java companion
    @NonNull
    @VisibleForTesting
    static final NativeRefPeerBinding<C4Socket> BOUND_SOCKETS = new NativeRefPeerBinding<>();

    //-------------------------------------------------------------------------
    // Public static Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4Socket createPassiveSocket(int id, @NonNull MessageFraming framing) {
        return createSocket(
            NATIVE_IMPL,
            NATIVE_IMPL.nFromNative(
                0L,
                "x-msg-conn",
                "",
                0,
                "/" + Integer.toHexString(id),
                MessageFraming.getC4Framing(framing)));
    }

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void open(
        long peer,
        long token,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable byte[] options) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "^C4Socket.open@%x: %s@%x", peer, socket, token);

        if ((socket == null) && (!openSocket(NATIVE_IMPL, peer, token, scheme, host, port, path, options))) { return; }

        withSocket(
            peer,
            "open",
            (s, r) -> {
                try { r.coreRequestsOpen(); }
                catch (RuntimeException e) { s.openFailed(e); }
            });
    }

    // Apparently SpotBugs can't tel that `data` *is* null-checked
    @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, @Nullable byte[] data) {
        final int nBytes = (data == null) ? 0 : data.length;
        Log.d(LOG_DOMAIN, "^C4Socket.write@%x(%d)", peer, nBytes);
        if (nBytes <= 0) {
            Log.i(LOG_DOMAIN, "C4Socket.write: empty data");
            return;
        }
        withSocket(peer, "write", (s, r) -> r.coreWrites(data));
    }

    // This method is called by reflection.  Don't change its signature.
    static void completedReceive(long peer, long nBytes) {
        Log.d(LOG_DOMAIN, "^C4Socket.completedReceive@%x(%d)", peer, nBytes);
        withSocket(peer, "completedReceive", (s, r) -> r.coreAcksWrite(nBytes));
    }

    // This method is called by reflection.  Don't change its signature.
    // Called only in NO_FRAMING mode
    static void requestClose(long peer, int status, @Nullable String message) {
        Log.d(LOG_DOMAIN, "^C4Socket.requestClose@%x(%d): '%s'", peer, status, message);
        withSocket(
            peer,
            "requestClose",
            (s, r) -> r.coreRequestsClose(new CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, status, message)));
    }

    // This method is called by reflection.  Don't change its signature.
    // Called only when not in NO_FRAMING mode
    static void close(long peer) {
        Log.d(LOG_DOMAIN, "^C4Socket.close@%x", peer);
        withSocket(peer, "close", (s, r) -> r.coreClosed());
    }

    //-------------------------------------------------------------------------
    // Internal static methods
    //-------------------------------------------------------------------------

    @VisibleForTesting
    @NonNull
    static C4Socket createSocket(@NonNull NativeImpl impl, long peer) {
        final C4Socket socket = new C4Socket(impl, peer);
        BOUND_SOCKETS.bind(peer, socket);
        return socket;
    }

    // Methods in the call chain beneath this method should fail fast.
    // They should wrap any error in a CBLSocketException and throw it
    // without trying to return.
    @VisibleForTesting
    static boolean openSocket(
        @NonNull NativeImpl impl,
        long peer,
        long sfToken,
        @Nullable String scheme,
        @Nullable String hostname,
        int port,
        @Nullable String path,
        @Nullable byte[] options) {
        final BaseSocketFactory socketFactory = BaseSocketFactory.getBoundSocketFactory(sfToken);
        if (socketFactory == null) {
            Log.w(LOG_DOMAIN, "C4Socket.open: no such socket factory: " + sfToken);
            return false;
        }
        if (scheme == null) {
            Log.w(LOG_DOMAIN, "C4Socket.open: scheme is null");
            return false;
        }
        if (hostname == null) {
            Log.w(LOG_DOMAIN, "C4Socket.open: hostname is null");
            return false;
        }
        if (path == null) {
            Log.w(LOG_DOMAIN, "C4Socket.open: path is null");
            return false;
        }
        if (options == null) {
            Log.w(LOG_DOMAIN, "C4Socket.open: options are null");
            return false;
        }

        final C4Socket socket = createSocket(impl, peer);
        try { socket.init(socketFactory.createSocket(socket, scheme, hostname, port, path, options)); }
        catch (RuntimeException e) {
            socket.openFailed(e);
            return false;
        }

        return true;
    }

    private static void withSocket(long peer, @Nullable String op, @NonNull SocketTask task) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (socket != null) {
            socket.continueWith(task);
            return;
        }

        Log.w(LOG_DOMAIN, "C4Socket.%s@%x: No socket for peer", op, peer);
    }

    private static void releaseSocket(
        @Nullable NativeImpl impl,
        long peer,
        int domain,
        int code,
        @Nullable String msg) {
        if (impl != null) { impl.nClosed(peer, domain, code, msg); }
    }

    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final Executor queue = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    @NonNull
    private final AtomicReference<SocketFromCore> fromCore = new AtomicReference<>(null);

    @NonNull
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    // Unlike most ref-counted objects, the C C4Socket is not
    // retained when it is created.  We have to retain it immediately
    // whether we created it or were handed it.
    // Don't bind the socket to the peer, in the constructor, because that would
    // publish an incompletely constructed object.
    @VisibleForTesting
    C4Socket(@NonNull NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
        impl.nRetain(peer);
    }

    @Override
    @NonNull
    public String toString() { return "vC4Socket" + super.toString(); }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    @Override
    public void close() {
        release(null, C4Constants.ErrorDomain.NETWORK, C4Constants.NetworkError.NETWORK_RESET, "Closed by client");
    }

    //-------------------------------------------------------------------------
    // Implementation of SocketToCore (Remote to Core)
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public Object getLock() { return getPeerLock(); }

    @Override
    public void init(@NonNull SocketFromCore core) {
        Log.d(LOG_DOMAIN, "%s.init: %s", this, core);
        Preconditions.assertNotNull(core, "fromCore");
        if ((!fromCore.compareAndSet(null, core)) && (!core.equals(fromCore.get()))) {
            Log.w(LOG_DOMAIN, "Attempt to re-initialize C4Socket");
        }
    }

    @Override
    public void ackOpenToCore(int httpStatus, @Nullable byte[] fleeceResponseHeaders) {
        Log.d(LOG_DOMAIN, "%s.ackOpenToCore", this);
        withPeer(peer -> {
            impl.nGotHTTPResponse(peer, httpStatus, fleeceResponseHeaders);
            impl.nOpened(peer);
        });
    }

    @Override
    public void ackWriteToCore(long byteCount) {
        Log.d(LOG_DOMAIN, "%s.ackWriteToCore(%d)", this, byteCount);
        withPeer(peer -> impl.nCompletedWrite(peer, byteCount));
    }

    @Override
    public void writeToCore(@NonNull byte[] data) {
        Log.d(LOG_DOMAIN, "%s.sendToCore(%d)", this, data.length);
        withPeer(peer -> impl.nReceived(peer, data));
    }

    @Override
    public void requestCoreClose(@NonNull CloseStatus status) {
        Log.d(LOG_DOMAIN, "%s.requestCoreClose(%d): '%s'", this, status.code, status.message);
        withPeer(peer -> impl.nCloseRequested(peer, status.code, status.message));
    }

    @Override
    public void closeCore(@NonNull CloseStatus status) {
        Log.d(LOG_DOMAIN, "%s.closeCore(%d, %d): '%s'", this, status.domain, status.code, status.message);
        release(null, status.domain, status.code, status.message);
    }

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try {
            release(
                LOG_DOMAIN,
                C4Constants.ErrorDomain.NETWORK,
                C4Constants.NetworkError.CONNECTION_ABORTED,
                "Finalized");
        }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Package protected methods
    //-------------------------------------------------------------------------

    // ??? Is there any way to eliminate this?
    long getPeerHandle() { return getPeer(); }

    @VisibleForTesting
    @Nullable
    SocketFromCore getFromCore() { return fromCore.get(); }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    // proxy this call to the fromCore delegate.
    private void continueWith(SocketTask task) { queue.execute(() -> task.accept(this, fromCore.get())); }

    // make a guess about what went wrong...
    private void openFailed(@NonNull RuntimeException err) {
        Log.w(LOG_DOMAIN, "Failed opening connection", err);

        final int domain;
        final int code;
        if (err instanceof CBLSocketException) {
            final CBLSocketException cblErr = (CBLSocketException) err;
            domain = cblErr.getDomain();
            code = cblErr.getCode();
        }
        else {
            domain = C4Constants.ErrorDomain.NETWORK;
            code = C4Constants.NetworkError.UNKNOWN;
        }

        release(null, domain, code, err.getMessage());
    }

    private void release(LogDomain logDomain, int domain, int code, @Nullable String msg) {
        releasePeer(
            logDomain,
            peer -> {
                BOUND_SOCKETS.unbind(peer);
                releaseSocket(impl, peer, domain, code, msg);
            });
    }
}
