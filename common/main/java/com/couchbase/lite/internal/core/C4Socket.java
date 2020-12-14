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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.support.Log;


@SuppressWarnings({"LineLength", "PMD.TooManyMethods"})
public abstract class C4Socket extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    // @formatter:off
    public static final int WS_STATUS_CLOSE_NORMAL = 1000;
    public static final int WS_STATUS_GOING_AWAY = 1001;               // Peer has to close, e.g. because host app is quitting
    public static final int WS_STATUS_CLOSE_PROTOCOL_ERROR = 1002;     // Protocol violation: invalid framing data
    public static final int WS_STATUS_CLOSE_DATA_ERROR = 1003;         // Message payload cannot be handled
    public static final int WS_STATUS_CLOSE_NO_CODE = 1005;            // Never sent, only received
    public static final int WS_STATUS_CLOSE_ABNORMAL = 1006;           // Never sent, only received
    public static final int WS_STATUS_CLOSE_BAD_MESSAGE_FORMAT = 1007; // Unparsable message
    public static final int WS_STATUS_CLOSE_POLICY_ERROR = 1008;       // Catch-all failure
    public static final int WS_STATUS_CLOSE_MESSAGE_TO_BIG = 1009;     // Message too big
    public static final int WS_STATUS_CLOSE_MISSING_EXTENSION = 1010;  // Peer doesn't provide a necessary extension
    public static final int WS_STATUS_CLOSE_CANT_FULFILL = 1011;       // Can't fulfill request due to "unexpected condition"
    public static final int WS_STATUS_CLOSE_TLS_FAILURE = 1015;        // Never sent, only received
    public static final int WS_STATUS_CLOSE_USER = 4000;               // First unregistered code for free-form use
    public static final int WS_STATUS_CLOSE_USER_TRANSIENT = WS_STATUS_CLOSE_USER + 1; // User-defined transient error
    public static final int WS_STATUS_CLOSE_USER_PERMANENT = WS_STATUS_CLOSE_USER + 2; // User-defined permanent error
    // @formatter:on

    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    // C4SocketFraming (C4SocketFactory.framing)
    public static final int WEB_SOCKET_CLIENT_FRAMING = 0; ///< Frame as WebSocket client messages (masked)
    public static final int NO_FRAMING = 1;                ///< No framing; use messages as-is
    public static final int WEB_SOCKET_SERVER_FRAMING = 2; ///< Frame as WebSocket server messages (not masked)
    // @formatter:on

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
        Object context,
        @Nullable String scheme,
        @Nullable String hostname,
        int port,
        @Nullable String path,
        byte[] options) {
        C4Socket socket = HANDLES_TO_SOCKETS.get(peer);
        Log.d(LOG_DOMAIN, "C4Socket.open @%x: %s, %s", peer, socket, context);

        if (socket == null) {
            if (!(context instanceof SocketFactory)) {
                throw new IllegalArgumentException("Context is not a socket factory: " + context);
            }
            socket = ((SocketFactory) context).createSocket(peer, scheme, hostname, port, path, options);
        }

        socket.openSocket();
    }

    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, byte[] allocatedData) {
        if (allocatedData == null) {
            Log.v(LOG_DOMAIN, "C4Socket.write: allocatedData is null");
            return;
        }

        final C4Socket socket = HANDLES_TO_SOCKETS.get(peer);
        Log.d(LOG_DOMAIN, "C4Socket.write @%x: %s", peer, socket);
        if (socket == null) { return; }

        socket.send(allocatedData);
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: No further action is required?
    static void completedReceive(long peer, long byteCount) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(peer);
        Log.d(LOG_DOMAIN, "C4Socket.receive @%x: %s", peer, socket);
        if (socket == null) { return; }

        socket.completedReceive(byteCount);
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    static void close(long peer) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(peer);
        Log.d(LOG_DOMAIN, "C4Socket.close @%x: %s", peer, socket);
        if (socket == null) { return; }

        socket.close();
    }

    // This method is called by reflection.  Don't change its signature.
    static void requestClose(long peer, int status, @Nullable String message) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(peer);
        Log.d(
            LOG_DOMAIN,
            "C4Socket.requestClose @%x: %s, (%d) %s", peer, socket, status, message);
        if (socket == null) { return; }

        socket.requestClose(status, message);
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    static void dispose(long peer) {
        final C4Socket socket = HANDLES_TO_SOCKETS.get(peer);
        Log.d(LOG_DOMAIN, "C4Socket.dispose @%x: %s", peer, socket);
        if (socket == null) { return; }

        release(socket);
    }

    //-------------------------------------------------------------------------
    // Private static methods
    //-------------------------------------------------------------------------

    private static void bind(@NonNull C4Socket socket) {
        final long peer = socket.getPeer();
        HANDLES_TO_SOCKETS.put(peer, socket);
        Log.d(LOG_DOMAIN, "C4Socket.bind @%x: %d", peer, HANDLES_TO_SOCKETS.size());
    }

    private static void release(@NonNull C4Socket socket) {
        final long peer = socket.getPeer();
        HANDLES_TO_SOCKETS.remove(peer);
        Log.d(LOG_DOMAIN, "C4Socket.release @%x: %d", peer, HANDLES_TO_SOCKETS.size());
    }


    //-------------------------------------------------------------------------
    // constructors
    //-------------------------------------------------------------------------

    protected C4Socket(long peer) {
        super(peer);
        bind(this);
    }

    // !!! This should be re-written.  There is a vector on the native side
    // that is holding global refs to these objects.
    protected C4Socket(String schema, String host, int port, String path, int framing) {
        setPeer(fromNative(this, schema, host, port, path, framing));
        bind(this);
    }

    @NonNull
    @Override
    public String toString() { return "C4Socket{" + super.toString() + "}"; }

//-------------------------------------------------------------------------
    // Abstract methods
    //-------------------------------------------------------------------------

    public abstract void close();

    protected abstract void openSocket();

    protected abstract void send(byte[] allocatedData);

    protected abstract void completedReceive(long byteCount);

    protected abstract void requestClose(int status, @Nullable String message);

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------

    protected boolean released() { return getPeerUnchecked() == 0L; }

    protected final void opened() {
        final long peer = getPeerUnchecked();
        Log.d(LOG_DOMAIN, "C4Socket.opened @%x", peer);
        if (peer == 0) { return; }
        opened(peer);
    }

    protected final void completedWrite(long byteCount) {
        final long peer = getPeerUnchecked();
        Log.d(LOG_DOMAIN, "C4Socket.completedWrite @%x: %d", peer, byteCount);
        if (peer == 0) { return; }
        completedWrite(peer, byteCount);
    }

    protected final void received(byte[] data) {
        final long peer = getPeerUnchecked();
        Log.d(LOG_DOMAIN, "C4Socket.received @%x: %d", peer, data.length);
        if (peer == 0) { return; }
        received(peer, data);
    }

    protected final void closed(int errorDomain, int errorCode, String message) {
        final long peer = getPeerUnchecked();
        Log.d(LOG_DOMAIN, "C4Socket.closed @%x: %d", peer, errorCode);
        if (peer == 0) { return; }
        closed(peer, errorDomain, errorCode, message);
    }

    protected final void closeRequested(int status, String message) {
        final long peer = getPeerUnchecked();
        Log.d(LOG_DOMAIN, "C4Socket.closeRequested @%x: (%d)%s", peer, status, message);
        if (peer == 0) { return; }
        closeRequested(peer, status, message);
    }

    protected final void gotHTTPResponse(int httpStatus, byte[] responseHeadersFleece) {
        final long handle = getPeerUnchecked();
        Log.d(LOG_DOMAIN, "C4Socket.gotHTTPResponse @%x: %d", handle, httpStatus);
        if (handle == 0) { return; }
        gotHTTPResponse(handle, httpStatus, responseHeadersFleece);
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    final long getHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void opened(long peer);

    private static native void completedWrite(long peer, long byteCount);

    private static native void received(long peer, byte[] data);

    private static native void closed(long peer, int errorDomain, int errorCode, String message);

    private static native void closeRequested(long peer, int status, String message);

    private static native void gotHTTPResponse(long peer, int httpStatus, byte[] responseHeadersFleece);

    // wrap an existing Java C4Socket in a C-native C4Socket
    private static native long fromNative(
        Object nativeHandle,
        String schema,
        String host,
        int port,
        String path,
        int framing);
}
