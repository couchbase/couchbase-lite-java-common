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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.Executor;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.impl.NativeC4Socket;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.sockets.SocketFromCore;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The process for closing one of these is complicated.  No matter what happens, though, it always ends like this:
 * Java calls C4Socket.closed (in the JNI, this turns into a call to c4socket_closed, which actually frees
 * the native object). Presuming that the C has a non-null C4SocketFactory reference and that it contains a
 * non-null socket_dispose reference, the C invokes it, producing the call to C4Socket.dispose
 * <p>
 * I think that this entire class should be re-architected to use a single-threaded executor.
 * Incoming messages should be enqueued as tasks on the executor. That would allow the removal
 * of all of the synchronization and assure that tasks were processed in order.
 * <p>
 * Note that state transitions come from 3 places.  Neither of the two subclasses, MessageSocket nor
 * AbstractCBLWebSocket, allow inbound connections.  For both, though shutdown is multiphase.
 * <nl>
 * <li>Core: core request open and can request close</li>
 * <li>Remote: this is a connection to a remote service.  It can request shutdown</li>
 * <li>Client: the client code can close the connection.  It expects never to hear from it again</li>
 * </nl>
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
        void nRelease(long peer);
    }

    //-------------------------------------------------------------------------
    // Static Fields
    //-------------------------------------------------------------------------

    // Lookup table: maps a handle to a peer native socket to its Java companion
    private static final NativeRefPeerBinding<C4Socket> BOUND_SOCKETS = new NativeRefPeerBinding<>();

    // Not final for testing
    @NonNull
    @VisibleForTesting
    static volatile NativeImpl nativeImpl = new NativeC4Socket();

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4Socket createSocket(int id, @NonNull MessageFraming framing) {
        return createSocket(nativeImpl.nFromNative(
            0L,
            "x-msg-conn",
            "",
            0,
            "/" + Integer.toHexString(id),
            MessageFraming.getC4Framing(framing)));
    }

    @NonNull
    private static C4Socket createSocket(long peer) {
        final C4Socket socket = new C4Socket(peer, nativeImpl);
        BOUND_SOCKETS.bind(peer, socket);
        return socket;
    }

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void open(
        long peer,
        @Nullable Object factory,
        @Nullable String scheme,
        @Nullable String hostname,
        int port,
        @Nullable String path,
        @Nullable byte[] options) {
        C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "^C4Socket.open@%x: %s", peer, socket, factory); }

        if (socket == null) {
            if (!(factory instanceof SocketFactory)) {
                Log.w(LOG_DOMAIN, "C4Socket.open: factory is not a SocketFactory: %s", factory);
                return;
            }

            if (scheme == null) {
                Log.w(LOG_DOMAIN, "C4Socket.open: scheme is null");
                return;
            }
            if (hostname == null) {
                Log.w(LOG_DOMAIN, "C4Socket.open: hostname is null");
                return;
            }
            if (path == null) {
                Log.w(LOG_DOMAIN, "C4Socket.open: path is null");
                return;
            }
            if (options == null) {
                Log.w(LOG_DOMAIN, "C4Socket.open: options are null");
                return;
            }

            socket = createSocket(peer);

            final SocketFromCore fromCore
                = ((SocketFactory) factory).createSocket(socket, scheme, hostname, port, path, options);

            socket.init(fromCore);
        }

        withSocket(peer, "open", SocketFromCore::coreRequestedOpen);
    }

    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, @Nullable byte[] data) {
        final int nBytes = (data == null) ? 0 : data.length;
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "^C4Socket.write@%x(%d)", peer, nBytes); }
        if (nBytes <= 0) {
            Log.i(LOG_DOMAIN, "C4Socket.write: empty data");
            return;
        }
        withSocket(peer, "write", l -> l.coreWrites(data));
    }

    // This method is called by reflection.  Don't change its signature.
    static void completedReceive(long peer, long nBytes) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "^C4Socket.completedReceive@%x(%d)", peer, nBytes); }
        withSocket(peer, "completedReceive", l -> l.coreAckReceive(nBytes));
    }

    // This method is called by reflection.  Don't change its signature.
    static void requestClose(long peer, int status, @Nullable String message) {
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(LOG_DOMAIN, "C4Socket.requestClose@%x(%d): '%s'", peer, status, message);
        }
        withSocket(peer, "requestClose", l -> l.coreRequestedClose(status, message));
    }

    // This method is called by reflection.  Don't change its signature.
    static void close(long peer) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "^C4Socket.close@%x", peer); }
        withSocket(peer, "close", SocketFromCore::coreClosed);
    }

    //-------------------------------------------------------------------------
    // Private static methods
    //-------------------------------------------------------------------------

    private static void withSocket(long peer, @Nullable String op, @NonNull Fn.Consumer<SocketFromCore> task) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (socket == null) {
            Log.w(LOG_DOMAIN, "C4Socket.%s@%x: No socket for peer", op, peer);
            return;
        }
        socket.continueWith(task);
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final Executor queue = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    private final NativeImpl impl;
    private volatile SocketFromCore fromCore;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Socket(long peer, @NonNull NativeImpl impl) {
        super(peer);
        this.impl = impl;
        impl.nRetain(peer);
    }

    //-------------------------------------------------------------------------
    // Methods
    //-------------------------------------------------------------------------

    @NonNull
    public String toString() { return "C4Socket" + super.toString(); }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    // Normally, the socket will be closed with a call to closed(domain, code, message).
    // The call to this method from either client code or from the finalizer
    // should not have any affect, because the peer should already have been released.
    // Called from the finalizer: be sure that there is a live ref to impl.
    @Override
    public void close() { release(null); }

    //-------------------------------------------------------------------------
    // Implementation of ToCore (Remote to Core)
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public Object getLock() { return getPeerLock(); }

    @Override
    public void init(@NonNull SocketFromCore fromCore) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.init: %s", this, fromCore); }
        Preconditions.assertNotNull(fromCore, "fromCore");
        synchronized (getPeerLock()) {
            final SocketFromCore oldFromCore = this.fromCore;
            if (oldFromCore != null) {
                if (!oldFromCore.equals(fromCore)) {
                    Log.w(LOG_DOMAIN, "Attempt to re-initialize C4Socket");
                }
                return;
            }
            this.fromCore = fromCore;
        }
    }

    @Override
    public void ackHttpToCore(int httpStatus, @Nullable byte[] fleeceResponseHeaders) {
        Log.d(LOG_DOMAIN, "v%s.ackHttpToCore(%d)", this, httpStatus);
        withPeer(peer -> impl.nGotHTTPResponse(peer, httpStatus, fleeceResponseHeaders));
    }

    @Override
    public void ackOpenToCore() {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "v%s.ackOpenToCore", this); }
        withPeer(impl::nOpened);
    }

    @Override
    public void ackWriteToCore(long byteCount) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "v%s.ackWriteToCore(%d)", this, byteCount); }
        withPeer(peer -> impl.nCompletedWrite(peer, byteCount));
    }

    @Override
    public void sendToCore(@NonNull byte[] data) {
        Log.d(LOG_DOMAIN, "v%s.sendToCore(%d)", this, (data == null) ? -1 : data.length);
        withPeer(peer -> impl.nReceived(peer, data));
    }

    @Override
    public void requestCoreClose(int code, @Nullable String msg) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "v%s.requestCoreClose(%d): '%s'", this, code, msg); }
        withPeer(peer -> impl.nCloseRequested(peer, code, msg));
    }

    @Override
    public void closeCore(int domain, int code, @Nullable String msg) {
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(LOG_DOMAIN, "v%s.closeCore(%d, %d): '%s'", this, domain, code, msg);
        }

        release(null, domain, code, msg);
    }

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { release(LOG_DOMAIN); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Package protected methods
    //-------------------------------------------------------------------------

    // ??? Is there any way to eliminate this?
    long getPeerHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void continueWith(Fn.Consumer<SocketFromCore> task) { queue.execute(() -> task.accept(fromCore)); }

    private void release(@Nullable LogDomain logDomain) {
        release(
            logDomain,
            C4Constants.ErrorDomain.LITE_CORE,
            C4Constants.LiteCoreError.UNEXPECTED_ERROR,
            "Closed by client");
    }

    private void release(@Nullable LogDomain logDomain, int domain, int code, @Nullable String msg) {
        releasePeer(
            logDomain,
            peer -> {
                Log.d(LogDomain.NETWORK, "DEBUG!!! RELEASE C4SOCKET PEER: @0x%x", peer);
                BOUND_SOCKETS.unbind(peer);
                impl.nClosed(peer, domain, code, msg);
                impl.nRelease(peer);
            });
    }
}
