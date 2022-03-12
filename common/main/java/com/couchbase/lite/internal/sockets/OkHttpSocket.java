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
import androidx.annotation.VisibleForTesting;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
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


    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    // This is the OkHttp connection outbound to the remote.
    // Its value has lifecycle null -> valid -> null and never changes again.
    // It would be final, if the initialization process happened in the other order...
    // Whenever this value is non-null, we assume that referenced OkHttp WebSocket
    // will do something reasonable with calls.
    private final AtomicReference<WebSocket> toRemote = new AtomicReference<>();

    // From CBLWebSocket's point of view, this is the inbound pipe, from the remote
    // From our point of view, it is the outbound connection to core.
    // Its value has lifecycle null -> valid -> null .
    private final AtomicReference<SocketFromRemote> toCore = new AtomicReference<>();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "OkHttpSocket" + ClassUtils.objId(this); }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    // ??? This needs some testing.  ... or at least some confidence building.
    @Override
    public void close() {
        final CloseStatus status = new CloseStatus(
            C4Constants.ErrorDomain.WEB_SOCKET,
            C4Constants.WebSocketError.GOING_AWAY,
            "Closed by client");
        closeRemote(status);
        closeSocket(toRemote.get(), l -> l.remoteClosed(status));
    }

    //-------------------------------------------------------------------------
    // Implementation of SocketToRemote (Outbound: Core to Remote)
    //-------------------------------------------------------------------------

    // Initialize this object: it needs a connection to core
    @Override
    public void init(@NonNull SocketFromRemote core) {
        Log.d(LOG_DOMAIN, "%s.init: %s", this, core);
        if (closed.get()) { throw new IllegalStateException("Attempt to re-open socket"); }
        if ((!toCore.compareAndSet(null, core)) && (!toCore.get().equals(core))) {
            throw new IllegalStateException("Attempt to re-initialize socket");
        }
    }

    // Request a remote connections
    @Override
    public boolean openRemote(@NonNull URI uri, @Nullable Map<String, Object> options) {
        Log.d(LOG_DOMAIN, "%s.open: %s, %s", this, uri, options);
        if (closed.get()) { throw new IllegalStateException("Attempt to re-open socket"); }

        final SocketFromRemote core = safeGetCore();

        // This bleeds a bit of the the OkHttp API into the CBLWebsocket.
        // It's just a builder, though: probably ok.
        final OkHttpClient.Builder builder = BASE_HTTP_CLIENT.newBuilder();
        try { core.setupRemoteSocketFactory(builder); }
        catch (Exception e) {
            Log.w(LOG_DOMAIN, "Failed constructing socket factory", e);
            return false;
        }

        builder.build().newWebSocket(newRequest(uri, options), this);

        return true;
    }

    // Send data to remote
    @Override
    public boolean writeToRemote(@NonNull byte[] data) {
        final int nBytes = (data == null) ? -1 : data.length;
        Log.d(LOG_DOMAIN, "%s.write(%d)", this, nBytes);
        if (nBytes <= 0) { return true; }
        return withRemote(remote -> remote.send(ByteString.of(data, 0, data.length)));
    }

    // Close the remote connection
    @Override
    public boolean closeRemote(@NonNull CloseStatus status) {
        Log.d(LOG_DOMAIN, "%s.close: %s", this, status);
        return withRemote(remote -> remote.close(status.code, status.message));
    }

    // Cancel the remote connection with prejudice
    @Override
    public void cancelRemote() {
        Log.d(LOG_DOMAIN, "%s.cancel", this);
        withRemote(remote -> {
            remote.cancel();
            return true;
        });
    }

    //-------------------------------------------------------------------------
    // Implementation of WebSocketListener (Inbound: Remote to Core)
    //-------------------------------------------------------------------------

    // We have an open connection to the remote
    @SuppressWarnings("PMD.PrematureDeclaration")
    @Override
    public void onOpen(@NonNull WebSocket ws, @NonNull Response resp) {
        Log.d(LOG_DOMAIN, "%s.onOpen: %s", this, resp);
        if (closed.get()) { throw new IllegalStateException("Attempt to re-open socket"); }
        final SocketFromRemote core = safeGetCore();
        if (!toRemote.compareAndSet(null, ws)) {
            Log.w(LOG_DOMAIN, "Attempt to reopen socket: %s, %s, %s", toRemote.get(), ws);
            return;
        }

        Map<String, Object> headers = null;
        final Headers httpHeaders = resp.headers();
        if ((httpHeaders != null) && (httpHeaders.size() > 0)) {
            headers = new HashMap<>();
            for (int i = 0; i < httpHeaders.size(); i++) { headers.put(httpHeaders.name(i), httpHeaders.value(i)); }
        }
        core.remoteOpened(resp.code(), headers);
    }

    // Receive data from the remote
    @Override
    public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
        final int len = (text == null) ? -1 : text.length();
        Log.d(LOG_DOMAIN, "%s.onText(%d)", this, len);
        if (len <= 0) { return; }
        withCore(ws, core -> core.remoteWrites(text.getBytes(StandardCharsets.UTF_8)));
    }

    // Receive data from the remote
    @Override
    public void onMessage(@NonNull WebSocket ws, @NonNull ByteString bytes) {
        final int nBytes = (bytes == null) ? -1 : bytes.size();
        Log.d(LOG_DOMAIN, "%s.onBytes(%d)", this, nBytes);
        if (nBytes <= 0) { return; }
        withCore(ws, core -> core.remoteWrites(bytes.toByteArray()));
    }

    // Remote wants to close the connection
    @Override
    public void onClosing(@NonNull WebSocket ws, int code, @NonNull String message) {
        Log.d(LOG_DOMAIN, "%s.onClosing(%d): '%s'", this, code, message);
        final CloseStatus status = new CloseStatus(code, message);
        withCore(ws, core -> core.remoteRequestsClose(status));
    }

    // Remote connection has been closed
    @Override
    public void onClosed(@NonNull WebSocket ws, int code, @NonNull String message) {
        Log.d(LOG_DOMAIN, "%s.onClosed(%d): '%s'", this, code, message);
        closeSocket(ws, l -> l.remoteClosed(new CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, code, message)));
    }

    // Remote connection has failed
    // Invoked when a web socket has been closed due to an error reading from or writing to the network.
    // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
    @Override
    public void onFailure(@NonNull WebSocket ws, @NonNull Throwable err, @Nullable Response resp) {
        Log.d(LOG_DOMAIN, "%s.onFailure: %s", err, this, resp);
        if (resp == null) { closeSocket(ws, l -> l.remoteFailed(err)); }
        else {
            closeSocket(
                ws,
                l -> l.remoteClosed(new CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, resp.code(), resp.message())));
        }
    }

    //-------------------------------------------------------------------------
    // Package protected methods
    //-------------------------------------------------------------------------

    @VisibleForTesting
    @Nullable
    SocketFromRemote getCore() { return toCore.get(); }

    @VisibleForTesting
    @Nullable
    WebSocket getRemote() { return toRemote.get(); }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @NonNull
    private Request newRequest(@NonNull URI uri, @Nullable Map<String, Object> options) {
        final Request.Builder builder = new Request.Builder();

        // Sets the URL target of this request.
        builder.url(uri.toString());

        // Set/update the "Host" header:
        String host = uri.getHost();
        if (uri.getPort() >= 0) { host = host + ":" + uri.getPort(); }
        builder.header("Host", host);

        // Add any additional headers
        if (options != null) {
            final Object extraHeaders = options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
            if (extraHeaders instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> header: ((Map<?, ?>) extraHeaders).entrySet()) {
                    builder.header(header.getKey().toString(), header.getValue().toString());
                }
            }

            // Configure WebSocket related headers:
            final Object protocols = options.get(C4Replicator.SOCKET_OPTION_WS_PROTOCOLS);
            if (protocols instanceof String) { builder.header("Sec-WebSocket-Protocol", (String) protocols); }
        }

        // Construct the HTTP request
        return builder.build();
    }

    private boolean withRemote(@NonNull Fn.Function<WebSocket, Boolean> op) {
        final WebSocket remote = toRemote.get();
        if (remote == null) { return false; }
        final Boolean val = op.apply(remote);
        return (val != null) && val;
    }

    private void withCore(@Nullable WebSocket ws, @NonNull Fn.Consumer<SocketFromRemote> op) {
        final SocketFromRemote core = safeGetCore();
        final WebSocket remote = toRemote.get();
        if (Objects.equals(ws, remote)) {
            op.accept(core);
            return;
        }
        Log.w(LOG_DOMAIN, "Attempt to execute request on wrong socket: %s, %s", remote, ws);
    }

    private void closeSocket(@Nullable WebSocket ws, @NonNull Fn.Consumer<SocketFromRemote> delegate) {
        closed.set(true);
        if (!toRemote.compareAndSet(ws, null)) {
            final WebSocket remote = toRemote.get();
            if (remote != null) {
                Log.w(LOG_DOMAIN, "Ignoring attempt to close the wrong socket: %s, %s", ws, remote);
                return;
            }
        }
        final SocketFromRemote core = toCore.getAndSet(null);
        if (core != null) { delegate.accept(core); }
    }

    @NonNull
    private SocketFromRemote safeGetCore() {
        final SocketFromRemote core = toCore.get();
        if (core == null) { throw new IllegalStateException("Attempt to use socket before initialization"); }
        return core;
    }
}
