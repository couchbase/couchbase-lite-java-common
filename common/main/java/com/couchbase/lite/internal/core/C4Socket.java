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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.impl.NativeC4Socket;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.sockets.CoreSocketDelegate;
import com.couchbase.lite.internal.sockets.CoreSocketListener;
import com.couchbase.lite.internal.support.Log;


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
public final class C4Socket extends C4NativePeer implements CoreSocketDelegate {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    // C4SocketFraming (c4Socket.h)
    public static final int WEB_SOCKET_CLIENT_FRAMING = 0; ///< Frame as WebSocket client messages (masked)
    public static final int NO_FRAMING = 1;                ///< No framing; use messages as-is
    public static final int WEB_SOCKET_SERVER_FRAMING = 2; ///< Frame as WebSocket server messages (not masked)

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
    // Static Variables
    //-------------------------------------------------------------------------

    // Not final for testing
    @NonNull
    @VisibleForTesting
    static NativeImpl nativeImpl = new NativeC4Socket();

    // Lookup table: maps a handle to a peer native socket to its Java companion
    private static final NativeRefPeerBinding<C4Socket> BOUND_SOCKETS = new NativeRefPeerBinding<>();

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
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "@%x#%s.open: %s", peer, socket, factory); }

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

            final CoreSocketListener listener
                = ((SocketFactory) factory).createSocket(socket, scheme, hostname, port, path, options);

            socket.init(listener);
        }

        socket.listener.onCoreRequestOpen();
    }

    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, @Nullable byte[] data) {
        final int nBytes = (data == null) ? 0 : data.length;
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "@%x#%s.write(%d)", peer, socket, nBytes); }

        if (socket == null) {
            Log.w(LOG_DOMAIN, "C4Socket.write(%d) @%x: No socket for peer", nBytes, peer);
            return;
        }

        if (nBytes <= 0) {
            Log.i(LOG_DOMAIN, "C4Socket.write: empty data");
            return;
        }

        socket.listener.onCoreSend(data);
    }

    // This method is called by reflection.  Don't change its signature.
    static void completedReceive(long peer, long nBytes) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(LOG_DOMAIN, "@%x#%s.completedReceive(%d)", peer, socket, nBytes);
        }

        if (socket == null) {
            Log.w(LOG_DOMAIN, "C4Socket.completedReceive(%d) @%x: No socket for peer", nBytes, peer);
            return;
        }

        socket.listener.onCoreCompletedReceive(nBytes);
    }

    // This method is called by reflection.  Don't change its signature.
    static void requestClose(long peer, int status, @Nullable String message) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(LOG_DOMAIN, "@%x#%s.completedReceive(%d): '%s'", peer, socket, status, message);
        }

        if (socket == null) {
            Log.w(LOG_DOMAIN, "C4Socket.requestClose @%x: No socket for peer: (%d) '%s'", peer, status, message);
            return;
        }

        socket.listener.onCoreRequestClose(status, message);
    }

    // This method is called by reflection.  Don't change its signature.
    static void close(long peer) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "@%x#%s.close", peer, socket); }

        if (socket == null) {
            Log.w(LOG_DOMAIN, "C4Socket.close @%x: No socket for peer", peer);
            return;
        }

        socket.listener.onCoreClosed();
    }

    // This method is called by reflection.  Don't change its signature.
    //
    // It is the second half of the asynchronous close process.
    // We are guaranteed this callback once we call `C4Socket.closed`
    // This is where we actually free the Java object.
    static void dispose(long peer) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "@%x.dispose", peer); }
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        if (socket != null) { socket.close(); }
    }

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4Socket createSocketFromURL(@NonNull String path, int framing) {
        return createSocket(nativeImpl.nFromNative(0L, "x-msg-conn", "", 0, path, framing));
    }

    @NonNull
    private static C4Socket createSocket(long peer) { return new C4Socket(peer, nativeImpl); }

    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    private final NativeImpl impl;

    private volatile CoreSocketListener listener;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Socket(long peer, @NonNull NativeImpl impl) {
        super(peer);
        this.impl = impl;
        impl.nRetain(peer);
        BOUND_SOCKETS.bind(peer, this);
    }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    @Override
    public void close() { closePeer(LOG_DOMAIN); }

    @NonNull
    public String toString() { return "C4Socket" + super.toString(); }

    //-------------------------------------------------------------------------
    // Implementation of CoreSocketDelegate (Remote to Core)
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public Object getLock() { return getPeerLock(); }

    @Override
    public void init(@NonNull CoreSocketListener listener) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.init: %s", this, listener); }
        withPeer(peer -> this.listener = listener);
    }

    @Override
    public void opened() {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.opened", this); }
        withPeer(impl::nOpened);
    }

    @Override
    public void gotHTTPResponse(int httpStatus, @Nullable byte[] fleeceResponseHeaders) {
        Log.d(LOG_DOMAIN, "%s.gotHTTPResponse(%d)", this, httpStatus);
        withPeer(peer -> impl.nGotHTTPResponse(peer, httpStatus, fleeceResponseHeaders));
    }

    @Override
    public void completedWrite(long byteCount) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.completedWrite(%d)", this, byteCount); }
        withPeer(peer -> impl.nCompletedWrite(peer, byteCount));
    }

    @Override
    public void received(@NonNull byte[] data) {
        Log.d(LOG_DOMAIN, "%s.received(%d)", this, (data == null) ? -1 : data.length);
        withPeer(peer -> impl.nReceived(peer, data));
    }

    @Override
    public void closeRequested(int status, @Nullable String message) {
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(LOG_DOMAIN, "%s.closeRequested(%d): '%s'", this, status, message);
        }
        withPeer(peer -> impl.nCloseRequested(peer, status, message));
    }

    // Closing the socket is a two step process
    // Calling impl.nClosed actually frees the peer
    // Core calls back to C4Socket.dispose, a goodbye kiss
    // that we use to remove this object from BOUND_SOCKETS
    @Override
    public void closed(int domain, int code, @Nullable String message) {
        if (CouchbaseLiteInternal.debugging()) {
            Log.d(LOG_DOMAIN, "%s.closed(%d, %d): '%s'", this, domain, code, message);
        }
        withPeer(peer -> impl.nClosed(peer, domain, code, message));
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(null); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    // !!! Wildly unsafe...
    long getPeerHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            peer -> {
                BOUND_SOCKETS.unbind(peer);
                impl.nRelease(peer);
            });
    }
}
