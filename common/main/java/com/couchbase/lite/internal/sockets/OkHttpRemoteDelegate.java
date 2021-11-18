//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


public final class OkHttpRemoteDelegate extends WebSocketListener implements RemoteSocketDelegate {
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    @FunctionalInterface
    interface Delegation {
        void delegate(RemoteSocketListener remoteListener);
    }

    @NonNull
    private static final OkHttpClient BASE_HTTP_CLIENT = new OkHttpClient.Builder()
        // timeouts: Core manages this: set no timeout, here.
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)

        // redirection
        .followRedirects(true)
        .followSslRedirects(true)

        // ??? .retryOnConnectionFailure(false)

        .build();


    @Nullable
    private volatile RemoteSocketListener listener;
    @Nullable
    private volatile OkHttpClient socketFactory;

    private final AtomicReference<WebSocket> socketRef = new AtomicReference<>();

    @NonNull
    @Override
    public String toString() { return "OkHttpRemoteDelegate" + ClassUtils.objId(this) + "{" + socketRef.get() + "}"; }

    //-------------------------------------------------------------------------
    // Implementation of RemoteSocketDelegate (Outbound: Core to Remote)
    //
    // There are races in this code.  It is possible, for instance,
    // that a multi-threaded client could start a send, complete a close
    // and then attempt to finish the send.
    //-------------------------------------------------------------------------

    // Initialize this object: it needs a proxy to handle activity
    @Override
    public void init(@NonNull RemoteSocketListener listener) throws Exception {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.init: %s", this, listener); }

        this.listener = listener;

        final OkHttpClient.Builder builder = BASE_HTTP_CLIENT.newBuilder();
        listener.setupRemoteSocketFactory(builder);
        this.socketFactory = builder.build();
    }

    // Request a remote connections
    @Override
    public void open(@NonNull Request request) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.open: %s", this, request); }
        final OkHttpClient remoteSocketFactory = socketFactory;
        if (remoteSocketFactory == null) {
            throw new IllegalStateException("Attempt to open a connection with null socket factory");
        }
        remoteSocketFactory.newWebSocket(request, this);
    }

    // Send data to remote
    @Override
    public boolean send(@NonNull byte[] data) {
        final int nBytes = (data == null) ? -1 : data.length;
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.send(%d)", this, nBytes); }

        if (nBytes <= 0) { return true; }

        final WebSocket socket = socketRef.get();
        return (socket != null) && socket.send(ByteString.of(data, 0, data.length));
    }

    // Check to see if there is a remote connection
    // The state returned by this function is only accurate at the moment the call is made
    @Override
    public boolean isOpen() { return socketRef.get() != null; }

    // Close the remote connection
    @Override
    public boolean close(int code, @Nullable String message) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.close(%d): '%s'", this, code, message); }
        final WebSocket remoteSocket = socketRef.get();
        return (remoteSocket == null) || remoteSocket.close(code, message);
    }

    // Cancel the remote connection with prejudice
    @Override
    public void cancel() {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.cancel", this); }
        final WebSocket socket = socketRef.get();
        if (socket != null) { socket.cancel(); }
    }

    //-------------------------------------------------------------------------
    // Implementation of WebSocketListener (Inbound: Remote to Core)
    //-------------------------------------------------------------------------

    // We have an open connection to the remote
    @Override
    public void onOpen(@NonNull WebSocket socket, @NonNull Response resp) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.onOpen: %s", this, resp); }
        socketRef.compareAndSet(null, socket);
        delegateSafely(socket, l -> l.onRemoteOpen(resp));
    }

    // Receive data from the remote
    @Override
    public void onMessage(@NonNull WebSocket socket, @NonNull String text) {
        final int nBytes = (text == null) ? -1 : text.length();
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.onText(%d)", this, nBytes); }
        if (nBytes <= 0) { return; }
        delegateSafely(socket, l -> l.onRemoteMessage(text.getBytes(StandardCharsets.UTF_8)));
    }

    // Receive data from the remote
    @Override
    public void onMessage(@NonNull WebSocket socket, @NonNull ByteString bytes) {
        final int nBytes = (bytes == null) ? -1 : bytes.size();
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.onBytes(%d)", this, nBytes); }
        if (nBytes <= 0) { return; }
        delegateSafely(socket, l -> l.onRemoteMessage(bytes.toByteArray()));
    }

    // Remote wants to close the connection
    @Override
    public void onClosing(@NonNull WebSocket socket, int code, @NonNull String message) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.onClosing(%d): '%s'", this, code, message); }
        delegateSafely(socket, l -> l.onRemoteRequestClose(code, message));
    }

    // Remote connection has been closed
    @Override
    public void onClosed(@NonNull WebSocket socket, int code, @NonNull String message) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.onClosing(%d): '%s'", this, code, message); }
        final RemoteSocketListener remoteListener = listener;
        if (remoteListener == null) { throw new IllegalStateException("Attempt to fail socket with null listener"); }
        socketRef.compareAndSet(socket, null);
        delegateSafely(null, l -> l.onRemoteClosed(code, message));
    }

    // Remote connection has failed
    // Invoked when a web socket has been closed due to an error reading from or writing to the network.
    // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
    @Override
    public void onFailure(@NonNull WebSocket socket, @NonNull Throwable err, @Nullable Response resp) {
        if (CouchbaseLiteInternal.debugging()) { Log.d(LOG_DOMAIN, "%s.onFailure: %s", err, this, resp); }
        socketRef.compareAndSet(socket, null);
        delegateSafely(null, l -> l.onRemoteFailure(err, resp));
    }

    private void delegateSafely(@Nullable WebSocket socket, @NonNull Delegation delegate) {
        final WebSocket remoteSocket = socketRef.get();
        if (!(Objects.equals(remoteSocket, socket))) {
            Log.w(LOG_DOMAIN, "Attempt to execute request on wrong socket: %s, %s", remoteSocket, socket);
            return;
        }

        final RemoteSocketListener remoteListener = listener;
        if (remoteListener == null) { throw new IllegalStateException("Attempt to delegate to null listener"); }

        delegate.delegate(remoteListener);
    }
}
