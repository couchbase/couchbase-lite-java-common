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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;


public final class OkHttpSocket extends WebSocketListener implements SocketToRemote {
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

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
    private volatile SocketFromRemote fromRemote;
    @Nullable
    private volatile OkHttpClient socketFactory;

    @GuardedBy("fromRemote.getLock()")
    @Nullable
    private WebSocket webSocket;

    @NonNull
    @Override
    public String toString() { return "OkHttpSocket" + ClassUtils.objId(this); }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    @Override
    public void close() { closeRemote(C4Constants.WebSocketError.GOING_AWAY, "Closed by client"); }

    //-------------------------------------------------------------------------
    // Implementation of ToRemote (Outbound: Core to Remote)
    //-------------------------------------------------------------------------

    // Initialize this object: it needs a proxy to handle activity
    @Override
    public void init(@NonNull SocketFromRemote fromRemote) throws Exception {
        Log.d(LOG_DOMAIN, "%s.init: %s", this, fromRemote);

        synchronized (this) {
            final SocketFromRemote oldFromRemote = this.fromRemote;
            if (oldFromRemote != null) {
                if (!oldFromRemote.equals(fromRemote)) {
                    Log.w(LOG_DOMAIN, "Attempt to re-initialize OkHttpSocket");
                }
                return;
            }
            this.fromRemote = fromRemote;
        }

        final OkHttpClient.Builder builder = BASE_HTTP_CLIENT.newBuilder();
        fromRemote.setupRemoteSocketFactory(builder);
        this.socketFactory = builder.build();
    }

    // Request a remote connections
    @Override
    public void openRemote(@NonNull Request request) {
        Log.d(LOG_DOMAIN, "%s.open: %s", this, request);
        final OkHttpClient remoteSocketFactory = socketFactory;
        if (remoteSocketFactory == null) {
            throw new IllegalStateException("Attempt to open a connection with null socket factory");
        }
        remoteSocketFactory.newWebSocket(request, this);
    }

    // Send data to remote
    @Override
    public boolean sendToRemote(@NonNull byte[] data) {
        final int nBytes = (data == null) ? -1 : data.length;
        Log.d(LOG_DOMAIN, "%s.send(%d)", this, nBytes);

        if (nBytes <= 0) { return true; }

        return withWebSocket(socket -> socket.send(ByteString.of(data, 0, data.length)));
    }

    // Check to see if there is a remote connection
    // The state returned by this function is only accurate at the moment the call is made
    @Override
    public boolean isRemoteOpen() { return withWebSocket(socket -> true); }

    // Close the remote connection
    @Override
    public boolean closeRemote(int code, @Nullable String message) {
        Log.d(LOG_DOMAIN, "%s.close(%d): '%s'", this, code, message);
        return withWebSocket(socket -> socket.close(code, message));
    }

    // Cancel the remote connection with prejudice
    @Override
    public void cancelRemote() {
        Log.d(LOG_DOMAIN, "%s.cancel", this);
        withWebSocket(socket -> {
            socket.cancel();
            return true;
        });
    }

    //-------------------------------------------------------------------------
    // Implementation of WebSocketListener (Inbound: Remote to Core)
    //-------------------------------------------------------------------------

    // We have an open connection to the remote
    @Override
    public void onOpen(@NonNull WebSocket socket, @NonNull Response resp) {
        Log.d(LOG_DOMAIN, "%s.onOpen: %s", this, resp);
        setWebSocket(null, socket, l -> l.remoteOpened(resp));
    }

    // Receive data from the remote
    @Override
    public void onMessage(@NonNull WebSocket socket, @NonNull String text) {
        final int nBytes = (text == null) ? -1 : text.length();
        Log.d(LOG_DOMAIN, "%s.onText(%d)", this, nBytes);
        if (nBytes <= 0) { return; }
        delegateSafely(socket, l -> l.remoteWrites(text.getBytes(StandardCharsets.UTF_8)));
    }

    // Receive data from the remote
    @Override
    public void onMessage(@NonNull WebSocket socket, @NonNull ByteString bytes) {
        final int nBytes = (bytes == null) ? -1 : bytes.size();
        Log.d(LOG_DOMAIN, "%s.onBytes(%d)", this, nBytes);
        if (nBytes <= 0) { return; }
        delegateSafely(socket, l -> l.remoteWrites(bytes.toByteArray()));
    }

    // Remote wants to close the connection
    @Override
    public void onClosing(@NonNull WebSocket socket, int code, @NonNull String message) {
        Log.d(LOG_DOMAIN, "%s.onClosing(%d): '%s'", this, code, message);
        delegateSafely(socket, l -> l.remoteRequestedClose(code, message));
    }

    // Remote connection has been closed
    @Override
    public void onClosed(@NonNull WebSocket socket, int code, @NonNull String message) {
        Log.d(LOG_DOMAIN, "%s.onClosing(%d): '%s'", this, code, message);
        setWebSocket(socket, null, l -> l.remoteClosed(code, message));
    }

    // Remote connection has failed
    // Invoked when a web socket has been closed due to an error reading from or writing to the network.
    // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
    @Override
    public void onFailure(@NonNull WebSocket socket, @NonNull Throwable err, @Nullable Response resp) {
        Log.d(LOG_DOMAIN, "%s.onFailure: %s", err, this, resp);
        setWebSocket(socket, null, l -> l.remoteFailed(err, resp));
    }

    private void setWebSocket(
        @Nullable WebSocket okSocket,
        @Nullable WebSocket newSocket,
        @NonNull Fn.Consumer<SocketFromRemote> delegate) {
        final SocketFromRemote fromRemote = getFromRemote();
        synchronized (fromRemote.getLock()) {
            if ((webSocket != null) && !Objects.equals(webSocket, okSocket)) {
                Log.w(
                    LOG_DOMAIN,
                    "Attempt to set the wrong socket for OkHttpRemoteDelegate: %s, %s, %s",
                    webSocket,
                    okSocket,
                    newSocket
                );
                return;
            }
            webSocket = newSocket;
            delegate.accept(fromRemote);
        }
    }

    private boolean withWebSocket(@NonNull Fn.Function<WebSocket, Boolean> op) {
        final SocketFromRemote remoteListener = getFromRemote();
        synchronized (remoteListener.getLock()) {
            if (webSocket == null) { return false; }
            final Boolean val = op.apply(webSocket);
            return (val != null) && val;
        }
    }

    private void delegateSafely(@Nullable WebSocket socket, @NonNull Fn.Consumer<SocketFromRemote> delegate) {
        final WebSocket remoteSocket;
        final SocketFromRemote fromRemote = getFromRemote();
        synchronized (fromRemote.getLock()) {
            if (Objects.equals(webSocket, socket)) {
                delegate.accept(fromRemote);
                return;
            }
            remoteSocket = webSocket;
        }
        Log.w(LOG_DOMAIN, "Attempt to execute request on wrong socket: %s, %s", remoteSocket, socket);
    }

    @NonNull
    private SocketFromRemote getFromRemote() {
        final SocketFromRemote fromRemote = this.fromRemote;
        if (fromRemote == null) { throw new IllegalStateException("Attempt to delegate to null fromRemote"); }
        return fromRemote;
    }
}
