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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LogDomain;
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
@SuppressWarnings({"LineLength", "PMD.TooManyMethods", "PMD.GodClass"})
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
        long nFromNative(long token, String schema, String host, int port, String path, int framing);
        void nOpened(long peer);
        void nGotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece);
        void nCompletedWrite(long peer, long byteCount);
        void nReceived(long peer, byte[] data);
        void nCloseRequested(long peer, int status, @Nullable String message);
        void nClosed(long peer, int errorDomain, int errorCode, String message);
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
    // !!! What happens when an exception bubbles up to a Core thread?
    static void open(
        long peer,
        @Nullable Object factory,
        @Nullable String scheme,
        @Nullable String hostname,
        int port,
        @Nullable String path,
        @Nullable byte[] options) {
        C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.open @%x: %s, %s", peer, socket, factory);

        if (socket == null) {
            if (scheme == null) {
                Log.d(LOG_DOMAIN, "C4Socket.open: scheme is null");
                return;
            }
            if (hostname == null) {
                Log.d(LOG_DOMAIN, "C4Socket.open: hostname is null");
                return;
            }
            if (path == null) {
                Log.d(LOG_DOMAIN, "C4Socket.open: path is null");
                return;
            }
            if (options == null) {
                Log.d(LOG_DOMAIN, "C4Socket.open: options are null");
                return;
            }

            if (!(factory instanceof SocketFactory)) {
                throw new IllegalArgumentException("Context is not a socket factory: " + factory);
            }

            socket = createSocket(peer);

            final CoreSocketListener listener
                = ((SocketFactory) factory).createSocket(socket, scheme, hostname, port, path, options);

            socket.init(listener);
        }

        socket.openSocket();
    }

    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, @Nullable byte[] allocatedData) {
        if (allocatedData == null) {
            Log.d(LOG_DOMAIN, "C4Socket.write: allocatedData is null");
            return;
        }

        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.write(%d) @%x: %s", allocatedData.length, peer, socket);

        if (socket != null) {
            socket.send(allocatedData);
            return;
        }

        Log.w(LogDomain.NETWORK, "No socket for peer @%x! Packet(%d) dropped!", peer, allocatedData.length);
    }

    // This method is called by reflection.  Don't change its signature.
    static void completedReceive(long peer, long byteCount) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.completedReceive(%d) @%x: %s", byteCount, peer, socket);

        if (socket != null) {
            socket.completedReceive(byteCount);
            return;
        }

        Log.w(LogDomain.NETWORK, "No socket for peer @%x! Receipt dropped!", peer);
    }

    // This method is called by reflection.  Don't change its signature.
    static void requestClose(long peer, int status, @Nullable String message) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.requestClose(%d) @%x: %s, '%s'", status, peer, socket, message);

        if (socket != null) {
            socket.requestClose(status, message);
            return;
        }

        Log.w(LogDomain.NETWORK, "No socket for peer @%x! Close request dropped!", peer);
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    static void close(long peer) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.close @%x: %s (%s)", peer, socket);

        if (socket != null) {
            socket.closeSocket();
            return;
        }

        Log.w(LogDomain.NETWORK, "No socket for peer @%x! Close dropped!", peer);
    }

    // This method is called by reflection.  Don't change its signature.
    //
    // It is the second half of the asynchronous close process.
    // We are guaranteed this callback once we call `C4Socket.closed`
    // This is where we actually free the Java object.
    static void dispose(long peer) { BOUND_SOCKETS.unbind(peer); }

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

    @GuardedBy("getPeerLock()")
    private boolean closing;

    @GuardedBy("getPeerLock()")
    private volatile CoreSocketListener listener;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Socket(long peer, @NonNull NativeImpl impl) {
        super(peer);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    @Override
    public void close() { closeInternal(C4Constants.ErrorDomain.LITE_CORE, C4Constants.LiteCoreError.SUCCESS, null); }

    //-------------------------------------------------------------------------
    // Implementation of CoreSocketDelegate
    //-------------------------------------------------------------------------

    @Override
    public void init(@NonNull CoreSocketListener listener) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            this.listener = listener;
            BOUND_SOCKETS.bind(peer, this);
        }
        Log.d(LOG_DOMAIN, "C4Socket.init @%x: %s, %s", peer, this, listener);
    }


    @Override
    public void opened() {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { impl.nOpened(peer); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.opened @%x: %s", peer, this);
    }

    @Override
    public void gotHTTPResponse(int httpStatus, @Nullable byte[] responseHeadersFleece) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { impl.nGotHTTPResponse(peer, httpStatus, responseHeadersFleece); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.gotHTTPResponse(%d) @%x: %s", httpStatus, peer, this);
    }

    @Override
    public void completedWrite(long byteCount) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { impl.nCompletedWrite(peer, byteCount); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.completedWrite(%d) @%x: %s", byteCount, peer, this);
    }

    @Override
    public void received(@NonNull byte[] data) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { impl.nReceived(peer, data); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.received(%d) @%x: %s", data.length, peer, this);
    }

    @Override
    public void closeRequested(int status, @Nullable String message) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { impl.nCloseRequested(peer, status, message); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.closeRequested(%d) @%x: %s, '%s'", status, peer, this, message);
    }

    @Override
    public void closed(int errorDomain, int errorCode, @Nullable String message) {
        closeInternal(errorDomain, errorCode, message);
    }

    @NonNull
    @Override
    public Object getLock() { return getPeerLock(); }

    @GuardedBy("getPeerLock()")
    public boolean isClosing() { return closing || (getPeerUnchecked() == 0L); }

    // there's really no point in having a finalizer...
    // there's a hard reference to this object in HANDLES_TO_SOCKETS

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    // !!! Wildly unsafe...
    long getPeerHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // Implementation of CoreSocketDelegate (Core to Remote)
    //-------------------------------------------------------------------------

    // Apparently PMD isn't smart enough to figure out that this *is* being called.
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void openSocket() { listener.onCoreRequestOpen(); }

    private void send(@NonNull byte[] data) { listener.onCoreSend(data); }

    private void completedReceive(long nBytes) { listener.onCoreCompletedReceive(nBytes); }

    private void requestClose(int status, @Nullable String message) { listener.onCoreRequestClose(status, message); }

    private void closeSocket() { listener.onCoreClosed(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closeInternal(int domain, int code, String msg) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (!closing && (peer != 0)) { impl.nClosed(peer, domain, code, msg); }
            closing = true;
        }
        Log.d(LOG_DOMAIN, "C4Socket.closed(%d,%d) @%x: %s, '%s'", domain, code, peer, this, msg);
    }
}
