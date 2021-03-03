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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.SocketFactory;
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
@SuppressWarnings({"LineLength", "PMD.TooManyMethods"})
public abstract class C4Socket extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    // C4SocketFraming (c4Socket.h)
    public static final int WEB_SOCKET_CLIENT_FRAMING = 0; ///< Frame as WebSocket client messages (masked)
    public static final int NO_FRAMING = 1;                ///< No framing; use messages as-is
    public static final int WEB_SOCKET_SERVER_FRAMING = 2; ///< Frame as WebSocket server messages (not masked)

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Lookup table: maps a handle to a peer native socket to its Java companion
    private static final Map<Long, C4Socket> HANDLES_TO_SOCKETS = Collections.synchronizedMap(new HashMap<>());

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
        byte[] options) {
        C4Socket socket = getSocketForPeer(peer);
        Log.d(LOG_DOMAIN, "C4Socket.open @%x: %s, %s", peer, socket, factory);

        // This socket will be bound in C4Socket.<init>
        if (socket == null) {
            if (!(factory instanceof SocketFactory)) {
                throw new IllegalArgumentException("Context is not a socket factory: " + factory);
            }
            socket = ((SocketFactory) factory).createSocket(peer, scheme, hostname, port, path, options);
        }

        socket.openSocket();
    }

    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, byte[] allocatedData) {
        if (allocatedData == null) {
            Log.v(LOG_DOMAIN, "C4Socket.write: allocatedData is null");
            return;
        }

        final C4Socket socket = getSocketForPeer(peer);
        Log.d(LOG_DOMAIN, "C4Socket.write @%x: %s", peer, socket);

        if (socket != null) { socket.send(allocatedData); }
    }

    // This method is called by reflection.  Don't change its signature.
    static void completedReceive(long peer, long byteCount) {
        final C4Socket socket = getSocketForPeer(peer);
        Log.d(LOG_DOMAIN, "C4Socket.receive @%x: %s", peer, socket);
        if (socket != null) { socket.completedReceive(byteCount); }
    }

    // This method is called by reflection.  Don't change its signature.
    static void requestClose(long peer, int status, @Nullable String message) {
        final C4Socket socket = getSocketForPeer(peer);
        Log.d(LOG_DOMAIN, "C4Socket.requestClose @%x: %s, (%d) '%s'", peer, socket, status, message);
        if (socket != null) { socket.requestClose(status, message); }
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    static void close(long peer) {
        final C4Socket socket = getSocketForPeer(peer);
        Log.d(LOG_DOMAIN, "C4Socket.close @%x: %s", peer, socket);
        if (socket != null) { socket.closeSocket(); }
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    //
    // It is the second half of the asynchronous close process.
    // We are guaranteed this callback once we call `C4Socket.closed`
    // This is where we actually free the Java object.
    static void dispose(long peer) { unbindSocket(peer); }

    //-------------------------------------------------------------------------
    // Private static methods
    //-------------------------------------------------------------------------

    private static void bindSocket(@NonNull C4Socket socket) {
        final long peer;
        final int n;
        synchronized (HANDLES_TO_SOCKETS) {
            peer = socket.getPeer();
            HANDLES_TO_SOCKETS.put(peer, socket);
            n = HANDLES_TO_SOCKETS.size();
        }

        Log.d(LOG_DOMAIN, "Bind socket @%x to %s (%d)", peer, socket, n);
    }

    @Nullable
    private static C4Socket getSocketForPeer(long peer) {
        synchronized (HANDLES_TO_SOCKETS) { return HANDLES_TO_SOCKETS.get(peer); }
    }

    private static void unbindSocket(long peer) {
        final C4Socket socket;
        final int n;
        synchronized (HANDLES_TO_SOCKETS) {
            socket = HANDLES_TO_SOCKETS.remove(peer);
            if (socket != null) { socket.releasePeer(); }
            n = HANDLES_TO_SOCKETS.size();
        }
        Log.d(LOG_DOMAIN, "Unbind socket @%x to %s (%d)", peer, socket, n);
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @GuardedBy("getLock()")
    private boolean closing;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    protected C4Socket(long peer) {
        super(peer);
        bindSocket(this);
    }

    // !!! This should be re-written to use C4NativePeer
    //     It should not pass a reference to this, to the native code
    //     and it should eliminate the vector holding GlobalRefs on the JNI side
    protected C4Socket(String schema, String host, int port, String path, int framing) {
        setPeer(fromNative(this, schema, host, port, path, framing));
        bindSocket(this);
    }

    //-------------------------------------------------------------------------
    // Abstract methods (Core to Remote)
    //-------------------------------------------------------------------------

    protected abstract void openSocket();

    protected abstract void send(@NonNull byte[] allocatedData);

    protected abstract void completedReceive(long byteCount);

    protected abstract void requestClose(int status, @Nullable String message);

    // NOTE!! The implementation of this method *MUST* call closed(int, int, String)
    protected abstract void closeSocket();

    //-------------------------------------------------------------------------
    // Protected methods (Remote to Core)
    //-------------------------------------------------------------------------

    protected final void opened() {
        final long peer;
        synchronized (getLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { opened(peer); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.opened @%x", peer);
    }

    protected final void gotHTTPResponse(int httpStatus, @Nullable byte[] responseHeadersFleece) {
        final long peer;
        synchronized (getLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { gotHTTPResponse(peer, httpStatus, responseHeadersFleece); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.gotHTTPResponse @%x: %d", peer, httpStatus);
    }

    protected final void completedWrite(long byteCount) {
        final long peer;
        synchronized (getLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { completedWrite(peer, byteCount); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.completedWrite @%x: %d", peer, byteCount);
    }

    protected final void received(byte[] data) {
        final long peer;
        synchronized (getLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { received(peer, data); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.received @%x: %d", peer, data.length);
    }

    protected final void closeRequested(int status, String message) {
        final long peer;
        synchronized (getLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { closeRequested(peer, status, message); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.closeRequested @%x: (%d) '%s'", peer, status, message);
    }

    protected final void closed(int errorDomain, int errorCode, String message) {
        closeInternal(errorDomain, errorCode, message);
    }

    @GuardedBy("getLock()")
    protected final boolean isC4SocketClosing() { return closing || (getPeerUnchecked() == 0L); }

    // there's really no point in having a finalizer...
    // there's a hard reference to this object in HANDLES_TO_SOCKETS

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    // !!! Wildly unsafe...
    final long getPeerHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closeInternal(int domain, int code, String msg) {
        final long peer;
        synchronized (getLock()) {
            peer = getPeerUnchecked();
            if (!closing && (peer != 0)) { closed(peer, domain, code, msg); }
            closing = true;
        }
        Log.d(LOG_DOMAIN, "C4Socket.closed @%x: (%d,%d) '%s'", peer, domain, code, msg);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // wrap an existing Java C4Socket in a C-native C4Socket
    private static native long fromNative(
        C4Socket socket,
        String schema,
        String host,
        int port,
        String path,
        int framing);

    private static native void opened(long peer);

    private static native void gotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece);

    private static native void completedWrite(long peer, long byteCount);

    private static native void received(long peer, byte[] data);

    private static native void closeRequested(long peer, int status, String message);

    private static native void closed(long peer, int errorDomain, int errorCode, String message);
}
