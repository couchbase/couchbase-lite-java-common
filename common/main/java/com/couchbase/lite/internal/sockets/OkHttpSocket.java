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
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Cookie;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;


public final class OkHttpSocket extends WebSocketListener implements SocketToRemote {
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    private interface SocketFactory {
        @NonNull
        WebSocket create(@NonNull OkHttpClient client, @NonNull Request req, @NonNull WebSocketListener listener);
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

    // A singleton WebSocket
    @NonNull
    private static final WebSocket NULL_WS = new WebSocket() {
        @NonNull
        @Override
        public Request request() { throw new UnsupportedOperationException(); }

        @Override
        public long queueSize() { throw new UnsupportedOperationException(); }

        @Override
        public boolean send(@NonNull String text) { throw new UnsupportedOperationException(); }

        @Override
        public boolean send(@NonNull ByteString bytes) { throw new UnsupportedOperationException(); }

        @Override
        public boolean close(int code, @Nullable String reason) { throw new UnsupportedOperationException(); }

        @Override
        public void cancel() { throw new UnsupportedOperationException(); }
    };

    /**
     * Parse request header cookie in the format of "name=value;name=value..." as an OkHttp Cookie.
     */
    @NonNull
    public static List<Cookie> parseCookies(@NonNull HttpUrl url, @NonNull String cookies) {
        final List<Cookie> cookieList = new ArrayList<>();
        final StringTokenizer st = new StringTokenizer(cookies, ";");
        while (st.hasMoreTokens()) {
            final Cookie cookie = Cookie.parse(url, st.nextToken().trim());
            if (cookie != null) { cookieList.add(cookie); }
        }
        return cookieList;
    }


    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    private final SocketFactory socketFactory;

    // This is the OkHttp connection outbound to the remote.
    // Its value has lifecycle null -> NULL_WS -> valid -> null.
    // It could be a simple final var, if the initialization process happened in the other order...
    // Whenever this value is non-null, we assume that referenced OkHttp WebSocket
    // will do something reasonable with calls.
    private final AtomicReference<WebSocket> toRemote = new AtomicReference<>();

    // From CBLWebSocket's point of view, this is the inbound pipe, from the remote
    // From our point of view, it is the outbound connection to core.
    // Its value has lifecycle SocketFromRemote.NULL -> valid -> null.  After null, it should never change again.
    private final AtomicReference<SocketFromRemote> toCore = new AtomicReference<>(SocketFromRemote.Constants.NULL);

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public OkHttpSocket() { this(OkHttpClient::newWebSocket); }

    @VisibleForTesting
    OkHttpSocket(@NonNull SocketFactory socketFactory) { this.socketFactory = socketFactory; }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "OkHttpSocket" + ClassUtils.objId(this); }

    //-------------------------------------------------------------------------
    // Implementation of AutoCloseable
    //-------------------------------------------------------------------------

    @Override
    public void close() {
        final CloseStatus status = new CloseStatus(
            C4Constants.ErrorDomain.WEB_SOCKET,
            C4Constants.WebSocketError.GOING_AWAY,
            "Closed by client");
        final SocketFromRemote core = toCore.getAndSet(null);
        final WebSocket remote = toRemote.getAndSet(null);
        if (remote != null) { remote.close(status.code, status.message); }
        if ((core != null) && (!SocketFromRemote.Constants.NULL.equals(core))) { core.remoteClosed(status); }
    }

    //-------------------------------------------------------------------------
    // Implementation of SocketToRemote (Outbound: Core to Remote)
    //-------------------------------------------------------------------------

    // Initialize this object: it needs a connection to core
    @Override
    public void init(@NonNull SocketFromRemote core) {
        Log.d(LOG_DOMAIN, "%s.init: %s", this, core);
        if (toCore.compareAndSet(SocketFromRemote.Constants.NULL, core)) { return; }

        final SocketFromRemote prevCore = toCore.get();
        if (prevCore == null) {
            Log.w(LOG_DOMAIN, "Ignoring attempt to initialize a closed socket socket: %s", this);
            return;
        }

        if (core.equals(prevCore)) {
            Log.w(LOG_DOMAIN, "Ignoring socket re-initialization: %s", this);
            return;
        }

        throw new CBLSocketException(
            C4Constants.ErrorDomain.NETWORK,
            C4Constants.NetworkError.NETWORK_RESET,
            "Attempt to re-initialize socket(" + prevCore + "): " + core);
    }

    // Request a remote connections
    @Override
    public boolean openRemote(@NonNull URI uri, @Nullable Map<String, Object> options) {
        Log.d(LOG_DOMAIN, "%s.open: %s, %s", this, uri, options);
        final SocketFromRemote core = getOpenCore();
        if (core == null) { return false; }

        if (!toRemote.compareAndSet(null, NULL_WS)) {
            Log.d(LOG_DOMAIN, "Attempt to re-open open socket: %s", this);
            return false;
        }

        // This bleeds a bit of the the OkHttp API into the CBLWebsocket.
        // It's just a builder, though: probably ok.
        final OkHttpClient.Builder builder = BASE_HTTP_CLIENT.newBuilder();
        core.setupRemoteSocketFactory(builder);

        if (!toRemote.compareAndSet(NULL_WS, socketFactory.create(builder.build(), newRequest(uri, options), this))) {
            throw new CBLSocketException(
                C4Constants.ErrorDomain.NETWORK,
                C4Constants.NetworkError.UNKNOWN,
                "Failed setting remote web socket");
        }

        return true;
    }

    // Send data to remote
    @Override
    public boolean writeToRemote(@NonNull byte[] data) {
        final int nBytes = (data == null) ? -1 : data.length;
        Log.d(LOG_DOMAIN, "%s.write(%d)", this, nBytes);
        if (nBytes <= 0) { return true; }
        getOpenCore();
        return withRemote(remote -> remote.send(ByteString.of(data, 0, data.length)));
    }

    // Close the remote connection
    @Override
    public boolean closeRemote(@NonNull CloseStatus status) {
        Log.d(LOG_DOMAIN, "%s.close: %s", this, status);
        getOpenCore();
        return withRemote(remote -> remote.close(status.code, status.message));
    }

    // Cancel the remote connection with prejudice
    @Override
    public void cancelRemote() {
        Log.d(LOG_DOMAIN, "%s.cancel", this);
        final WebSocket remote = toRemote.get();
        closeSocket(
            remote,
            core -> { if (remote != null) { remote.cancel(); } });
    }

    //-------------------------------------------------------------------------
    // Implementation of WebSocketListener (Inbound: Remote to Core)
    //-------------------------------------------------------------------------

    // We have an open connection to the remote
    @SuppressWarnings("PMD.PrematureDeclaration")
    @Override
    public void onOpen(@NonNull WebSocket ws, @NonNull Response resp) {
        Log.d(LOG_DOMAIN, "%s.onOpen: %s", this, resp);
        final SocketFromRemote core = getOpenCore();
        if ((core == null) || (!verifyRemote(ws))) { return; }

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
        final int len = (bytes == null) ? -1 : bytes.size();
        Log.d(LOG_DOMAIN, "%s.onBytes(%d)", this, len);
        if (len <= 0) { return; }
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
        getOpenCore();
        closeSocket(
            ws,
            core -> core.remoteClosed(new CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, code, message)));
    }

    // Remote connection has failed
    // Invoked when a web socket has been closed due to an error reading from or writing to the network.
    // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
    @Override
    public void onFailure(@NonNull WebSocket ws, @NonNull Throwable err, @Nullable Response resp) {
        Log.d(LOG_DOMAIN, "%s.onFailure: %s", err, this, resp);
        getOpenCore();

        if (resp == null) {
            closeSocket(ws, core -> core.remoteFailed(err));
            return;
        }

        closeSocket(
            ws,
            core -> core.remoteClosed(new CloseStatus(
                C4Constants.ErrorDomain.WEB_SOCKET,
                resp.code(),
                resp.message())));
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
        final SocketFromRemote core = getOpenCore();
        if ((core != null) && (verifyRemote(ws))) { op.accept(core); }
    }

    private void closeSocket(@Nullable WebSocket ws, @NonNull Fn.Consumer<SocketFromRemote> delegate) {
        if (!toRemote.compareAndSet(ws, null)) {
            final WebSocket remote = toRemote.get();
            if (remote != null) {
                Log.w(LOG_DOMAIN, "Ignoring attempt to close the wrong socket: %s, %s", ws, remote);
                return;
            }
        }
        final SocketFromRemote core = toCore.getAndSet(null);
        if ((core != null) && (!SocketFromRemote.Constants.NULL.equals(core))) { delegate.accept(core); }
    }

    @Nullable
    private SocketFromRemote getOpenCore() {
        final SocketFromRemote core = toCore.get();
        if (SocketFromRemote.Constants.NULL.equals(core)) {
            throw new CouchbaseLiteError("Attempt to use socket before initialization");
        }
        return core;
    }

    private boolean verifyRemote(@Nullable WebSocket ws) {
        final WebSocket remote = toRemote.get();
        if (Objects.equals(ws, remote)) { return true; }

        if (remote == null) {
            Log.d(LOG_DOMAIN, "Ignoring operation on closed socket: %s", ws);
            return false;
        }

        Log.w(LOG_DOMAIN, "Ignoring operation on the wrong socket(%s): %s", remote, ws);
        return false;
    }
}
