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
package com.couchbase.lite.internal.replicator;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;
import okhttp3.Challenge;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.core.NativeContext;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.StateMachine;


/**
 * This is all a bit odd.  Let me try to explain.
 * <p>
 * First of all, you need to know about ProtocolTypes.
 * Core knows all about WebSockets protocol.  It would be glad to be pretty much completely responsible
 * for a WS connection, if we could just send the bytes across some raw byte stream.  For better or worse, we
 * hired OkHTTP for this job. It is also very smart and *it* wants to handle the WS connection.  The solution,
 * dating back to the dawn of time, is that we *always* use what Core, quite oddly, calls MESSAGE_STREAM protocol.
 * In this mode Core only hands us minimal state transition information, and only the basic information that
 * must be transferred.
 * The comments in c4Socket.h are incredibly valuable.
 * So, some assumptions.  If you are here:
 * <ul>
 * <li> you are talking MESSAGE_STREAM.
 * <li> there are no inbound connections
 * </ul>
 * This class is just a switch that routes things between OkHttp and Core.  Core does its callbacks via the
 * abstract methods defined in C4Socket and implemented here.  OkHttp does its callbacks to the CBLWebSocketListener
 * which proxies them directly to Core, via C4Socket.
 * The peculiar factory method returns an instance of the concrete subclass, CBLWebSocket.  There are different
 * sources for that class, for each of the CE/EE * platform variants of the platform.
 * <p>
 * State transition:
 * Things kick off when Core calls openSocket.  We are now in the state CONNECTING.  In response, we ask OkHttp
 * to open a connection to the remote. In the happy case, OkHttp successfully makes the connection to the remote.
 * That causes a callback to CBLWebSocketListener.onOpen.  We proxy that call to Core.  The connection is now OPEN.
 * After that, the two sides chat.  If Core has something to say, we get a call to send().  We proxy that call to
 * OkHttp, and, when the data has been sent, call back to Core with completedWrite().  If the remote has something
 * to say, we get a call to one of the two CBLWebSocketListener.onMessage() methods and proxy the content to
 * Core by calling received()
 * Eventually, someone decides to close the connection.  If it is the remote, we get a call to
 * CBLWebSocketListener.onClosing().  We proxy that to Core, which, surprisingly, turns right around and proxies
 * it back to us, by calling close().
 * If it is Core that decides to close the connection, we get a call to requestClose().  That should result in a
 * call to CBLWebSocketListener.onClosed().
 */
@SuppressWarnings({"PMD.GodClass", "PMD.ExcessiveImports"})
public abstract class AbstractCBLWebSocket extends C4Socket {
    private static final LogDomain TAG = LogDomain.NETWORK;

    public static final int DEFAULT_ONE_SHOT_MAX_RETRIES = 9;
    public static final int DEFAULT_CONTINUOUS_MAX_RETRIES = Integer.MAX_VALUE;
    public static final long DEFAULT_MAX_RETRY_WAIT_SEC = 300L;
    public static final long DEFAULT_HEARTBEAT_SEC = 300L;

    private static final int MAX_AUTH_RETRIES = 3;

    private static final int HTTP_STATUS_MIN = 100;
    private static final int HTTP_STATUS_MAX = 600;

    private static final String CHALLENGE_BASIC = "Basic";
    private static final String HEADER_AUTH = "Authorization";

    private enum State {INIT, CONNECTING, OPEN, CLOSING, CLOSED, FAILED}

    private static final StateMachine.Builder<State> WS_STATE_BUILDER;
    static {
        final StateMachine.Builder<State> builder = new StateMachine.Builder<>(State.class, State.INIT, State.FAILED);
        builder.addTransition(State.INIT, State.CONNECTING);
        builder.addTransition(State.CONNECTING, State.OPEN, State.CLOSING);
        builder.addTransition(State.OPEN, State.CLOSING, State.CLOSED);
        builder.addTransition(State.CLOSING, State.CLOSED);
        WS_STATE_BUILDER = builder;
    }

    final class Remote extends WebSocketListener {

        // OkHTTP callback, proxied to C4Socket
        // We have an open connection to the remote
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            Log.v(TAG, "%s:OkHTTP open: %s", state, response);

            if (state.setState("Remote.onOpen", State.OPEN) == null) { return; }

            AbstractCBLWebSocket.this.webSocket = webSocket;
            receivedHTTPResponse(response);

            opened();

            Log.i(TAG, "WebSocket OPEN");
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote sent data
        @Override
        public void onMessage(@NonNull WebSocket webSocket, String text) {
            Log.v(TAG, "%s:OkHTTP text data: %d", state, text.length());

            if (!state.checkState("Remote.onMessage", State.OPEN)) { return; }

            received(text.getBytes(StandardCharsets.UTF_8));
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote sent data
        @Override
        public void onMessage(@NonNull WebSocket webSocket, ByteString bytes) {
            Log.v(TAG, "%s:OkHTTP byte data: %d", state, bytes.size());

            if (!state.checkState("Remote.onMessage", State.OPEN)) { return; }

            received(bytes.toByteArray());
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote wants to close the connection
        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.v(TAG, "%s:OkHTTP closing: %s", state, reason);

            if (state.setState("Remote.onClosing", State.CLOSING) == null) { return; }

            closeRequested(code, reason);
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote connection has been closed
        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.v(TAG, "%s:OkHTTP closed: (%d) %s", state, code, reason);

            if (!state.checkState("Remote.onClosed", State.CLOSED)) { return; }

            connectionClosed(code, reason);
        }

        // NOTE: from CBLStatus.mm
        // {kCFErrorHTTPConnectionLost,                {POSIXDomain, ECONNRESET}},
        // {kCFURLErrorCannotConnectToHost,            {POSIXDomain, ECONNREFUSED}},
        // {kCFURLErrorNetworkConnectionLost,          {POSIXDomain, ECONNRESET}},

        // OkHTTP callback, proxied to C4Socket
        // Remote connection failed
        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable err, Response response) {
            Log.v(TAG, "%s:OkHTTP failed: %s", state, response, err);

            state.setState("Remote.onFailure", State.FAILED);

            // Invoked when a web socket has been closed due to an error reading from or writing to the network.
            // Both outgoing and incoming messages may have been lost. No further calls to this listener will be made.
            if (response == null) {
                connectionClosed(err);
                return;
            }

            int httpStatus = response.code();
            if (httpStatus == 101) { httpStatus = C4Socket.WS_STATUS_CLOSE_PROTOCOL_ERROR; }
            else if ((httpStatus < 300) || (httpStatus >= 1000)) { httpStatus = C4Socket.WS_STATUS_CLOSE_POLICY_ERROR; }

            connectionClosed(httpStatus, response.message());
        }
    }

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    @NonNull
    private static final NativeContext<KeyManager> KEY_MANAGERS = new NativeContext<>();

    @NonNull
    private static final OkHttpClient BASE_HTTP_CLIENT = new OkHttpClient.Builder()
        // timeouts: Core manages this: set no timeout, here.
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)

        // redirection
        .followRedirects(true)
        .followSslRedirects(true)

        // heartbeat
        .pingInterval(DEFAULT_HEARTBEAT_SEC, TimeUnit.SECONDS)
// ???        .retryOnConnectionFailure(false)
        .build();

    //-------------------------------------------------------------------------
    // Factory method
    //-------------------------------------------------------------------------

    // Create an instance of the CBLWebSocket subclass
    public static CBLWebSocket createCBLWebSocket(
        long peer,
        String scheme,
        String hostname,
        int port,
        String path,
        byte[] fleeceOptions,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
        Log.v(TAG, "Creating CBLWebSocket@ %x: %s://%s:%d%s", peer, scheme, hostname, port, path);

        try {
            return new CBLWebSocket(
                peer,
                translateScheme(scheme),
                hostname,
                port,
                path,
                (fleeceOptions == null) ? null : FLValue.fromData(fleeceOptions).asDict(),
                cookieStore,
                serverCertsListener);
        }
        catch (Exception e) { Log.w(TAG, "Failed to instantiate CBLWebSocket", e); }

        return null;
    }


    public static int addKeyManager(@NonNull KeyManager keyManager) {
        final int token = AbstractCBLWebSocket.KEY_MANAGERS.reserveKey();
        AbstractCBLWebSocket.KEY_MANAGERS.bind(token, keyManager);
        return token;
    }

    // OkHttp doesn't understand blip or blips
    private static String translateScheme(String scheme) {
        if (C4Replicator.C4_REPLICATOR_SCHEME_2.equalsIgnoreCase(scheme)) { return C4Replicator.WEBSOCKET_SCHEME; }

        if (C4Replicator.C4_REPLICATOR_TLS_SCHEME_2.equalsIgnoreCase(scheme)) {
            return C4Replicator.WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        return scheme;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final OkHttpClient httpClient;
    private final Remote wsListener;
    private final URI uri;
    private final Map<String, Object> options;
    private final CBLCookieStore cookieStore;
    private final Fn.Consumer<List<Certificate>> serverCertsListener;
    private final StateMachine<State> state = WS_STATE_BUILDER.build();

    private WebSocket webSocket;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected AbstractCBLWebSocket(
        long peer,
        String scheme,
        String hostname,
        int port,
        String path,
        Map<String, Object> options,
        CBLCookieStore cookieStore,
        Fn.Consumer<List<Certificate>> serverCertsListener)
        throws GeneralSecurityException, URISyntaxException {
        super(peer);
        this.uri = new URI(translateScheme(scheme), null, hostname, port, path, null, null);
        this.options = options;
        this.cookieStore = cookieStore;
        this.serverCertsListener = serverCertsListener;
        this.httpClient = setupOkHttpClient();
        this.wsListener = new Remote();
    }

    @Override
    @NonNull
    public String toString() { return "AbstractCBLWebSocket{" + uri + "}"; }

    //-------------------------------------------------------------------------
    // Abstract methods
    //-------------------------------------------------------------------------

    // Allow subclass to handle errors.
    protected abstract boolean handleClose(Throwable error);

    //-------------------------------------------------------------------------
    // Abstract method implementation
    //-------------------------------------------------------------------------

    // Core callback
    // Core has closed the connection
    @CallSuper
    @Override
    public final void close() {
        Log.v(TAG, "%s:Core closed", state);
        state.checkState("close", State.CLOSED);
    }

    // Core callback, proxied to OkHTTP
    // Core needs a connection to the remote
    @Override
    protected final void openSocket() {
        Log.v(TAG, "%s:Core connect: %s", state, uri);

        if (state.setState("Core.openSocket", State.CONNECTING) == null) { return; }

        httpClient.newWebSocket(newRequest(), wsListener);
    }

    // Core callback, proxied to OkHTTP
    // Core wants to transfer data to the remote
    @Override
    protected final void send(@NonNull byte[] allocatedData) {
        Log.v(TAG, "%s:Core send: %d", state, allocatedData.length);

        if (!state.checkState("send", State.OPEN)) { return; }

        if (!webSocket.send(ByteString.of(allocatedData, 0, allocatedData.length))) {
            Log.i(TAG, "CBLWebSocket failed to send data of length = " + allocatedData.length);
            return;
        }

        completedWrite(allocatedData.length);
    }

    // Core callback, proxied to OkHTTP
    // Core wants to break the connection
    @Override
    protected final void requestClose(int code, String message) {
        Log.v(TAG, "%s:Core request close: %d", state, code);

        if (state.setState("Core.requestClose", State.CLOSED) == null) { return; }

        // We've told Core to leave the connection to us, so it might pass us the HTTP status
        // If it does, we need to convert it to a WS status for the other side.
        if ((code > HTTP_STATUS_MIN) && (code < HTTP_STATUS_MAX)) { code = C4Socket.WS_STATUS_CLOSE_POLICY_ERROR; }

        if (!webSocket.close(code, message)) {
            Log.i(TAG, "CBLWebSocket failed to initiate a graceful shutdown of this web socket.");
        }
    }

    // Core callback, ignored
    // Core confirms the reception of n bytes
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    @Override
    protected final void completedReceive(long n) { }

    //-------------------------------------------------------------------------
    // package visible methods
    //-------------------------------------------------------------------------

    // http://www.ietf.org/rfc/rfc2617.txt
    @Nullable
    Request authenticate(@NonNull Response resp, @NonNull String user, @NonNull String pwd) {
        Log.v(TAG, "CBLWebSocket authenticated for response " + resp);

        // If failed 3 times, give up.
        if (responseCount(resp) >= MAX_AUTH_RETRIES) { return null; }

        final List<Challenge> challenges = resp.challenges();
        Log.v(TAG, "CBLWebSocket received challenges " + challenges);
        if (challenges == null) { return null; }

        for (Challenge challenge: challenges) {
            if (CHALLENGE_BASIC.equals(challenge.scheme())) {
                return resp.request()
                    .newBuilder()
                    .header(HEADER_AUTH, Credentials.basic(user, pwd))
                    .build();
            }
        }

        return null;
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private OkHttpClient setupOkHttpClient() throws GeneralSecurityException {
        final OkHttpClient.Builder builder = BASE_HTTP_CLIENT.newBuilder();

        // Heartbeat
        final Number heartbeat = (Number) options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL);
        if (heartbeat != null) { builder.pingInterval((long) heartbeat, TimeUnit.SECONDS).build(); }

        // Authenticator
        final Authenticator authenticator = getBasicAuthenticator();
        if (authenticator != null) { builder.authenticator(authenticator); }

        // Cookies
        builder.cookieJar(getCookieJar());

        // Setup SSLFactory and trusted certificate (pinned certificate)
        setupSSLSocketFactory(builder);

        return builder.build();
    }

    private CookieJar getCookieJar() {
        return new CookieJar() {
            @Override
            public void saveFromResponse(@NonNull HttpUrl httpUrl, @NonNull List<Cookie> cookies) {
                for (Cookie cookie: cookies) {
                    cookieStore.setCookie(httpUrl.uri(), cookie.toString());
                }
            }

            @NonNull
            @Override
            public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                final List<Cookie> cookies = new ArrayList<>();

                // Cookies from config
                final String confCookies = (String) options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
                if (confCookies != null) { cookies.addAll(CBLCookieStore.parseCookies(url, confCookies)); }

                // Set cookies in the CookieStore
                final String setCookies = cookieStore.getCookies(url.uri());
                if (setCookies != null) { cookies.addAll(CBLCookieStore.parseCookies(url, setCookies)); }

                return cookies;
            }
        };
    }

    private Authenticator getBasicAuthenticator() {
        if ((options == null) || !options.containsKey(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION)) { return null; }

        @SuppressWarnings("unchecked") final Map<String, Object> auth
            = (Map<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (auth == null) { return null; }

        final String authType = (String) auth.get(C4Replicator.REPLICATOR_AUTH_TYPE);
        if (!C4Replicator.AUTH_TYPE_BASIC.equals(authType)) { return null; }

        final String username = (String) auth.get(C4Replicator.REPLICATOR_AUTH_USER_NAME);
        final String password = (String) auth.get(C4Replicator.REPLICATOR_AUTH_PASSWORD);
        if ((username == null) || (password == null)) { return null; }

        return (route, response) -> authenticate(response, username, password);
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) { result++; }
        return result;
    }

    private Request newRequest() {
        final Request.Builder builder = new Request.Builder();

        // Sets the URL target of this request.
        builder.url(uri.toString());

        // Set/update the "Host" header:
        String host = uri.getHost();
        if (uri.getPort() != -1) { host = String.format(Locale.ENGLISH, "%s:%d", host, uri.getPort()); }
        builder.header("Host", host);

        // Construct the HTTP request:
        if (options == null) { return builder.build(); }

        // Extra Headers
        @SuppressWarnings("unchecked") final Map<String, Object> extraHeaders
            = (Map<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
        if (extraHeaders != null) {
            for (Map.Entry<String, Object> entry: extraHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue().toString());
            }
        }

        // Configure WebSocket related headers:
        final String protocols = (String) options.get(C4Replicator.SOCKET_OPTION_WS_PROTOCOLS);
        if (protocols != null) { builder.header("Sec-WebSocket-Protocol", protocols); }

        return builder.build();
    }

    @SuppressWarnings("PMD.PrematureDeclaration")
    private void receivedHTTPResponse(Response response) {
        final int httpStatus = response.code();
        Log.v(TAG, "CBLWebSocket received HTTP response " + response);

        // Post the response headers to LiteCore:
        final Headers hs = response.headers();
        if ((hs == null) || (hs.size() <= 0)) { return; }

        byte[] headersFleece = null;
        final Map<String, Object> headers = new HashMap<>();
        for (int i = 0; i < hs.size(); i++) { headers.put(hs.name(i), hs.value(i)); }

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(headers);
            headersFleece = enc.finish();
        }
        catch (LiteCoreException e) { Log.w(TAG, "CBLWebSocket failed to encode response header", e); }

        gotHTTPResponse(httpStatus, headersFleece);
    }

    private void connectionClosed(int code, String reason) {
        Log.v(TAG, "CBLWebSocket closed (%d): %s", code, reason);

        if (code == C4Socket.WS_STATUS_CLOSE_NORMAL) {
            connectionClosedNormally();
            return;
        }

        closed(C4Constants.ErrorDomain.WEB_SOCKET, code, reason);
    }

    private void connectionClosed(Throwable error) {
        Log.v(TAG, "CBLWebSocket closed", error);

        if (error == null) {
            connectionClosedNormally();
            return;
        }

        if (handleClose(error)) { return; }

        // TLS Certificate error
        int code = C4Socket.WS_STATUS_CLOSE_PROTOCOL_ERROR;
        if (error.getCause() instanceof CertificateException) { code = C4Constants.NetworkError.TLS_CERT_UNTRUSTED; }

        // SSLPeerUnverifiedException
        else if (error instanceof SSLPeerUnverifiedException) { code = C4Constants.NetworkError.TLS_CERT_UNTRUSTED; }

        // UnknownHostException - this is thrown when in Airplane mode or offline
        else if (error instanceof UnknownHostException) { code = C4Constants.NetworkError.UNKNOWN_HOST; }

        else if (error instanceof SSLHandshakeException) { code = C4Constants.NetworkError.TLS_HANDSHAKE_FAILED; }

        closed(C4Constants.ErrorDomain.NETWORK, code, null);
    }

    private void connectionClosedNormally() { closed(C4Constants.ErrorDomain.WEB_SOCKET, 0, null); }

    //-------------------------------------------------------------------------
    // SSL Support
    //-------------------------------------------------------------------------

    private void setupSSLSocketFactory(OkHttpClient.Builder builder) throws GeneralSecurityException {
        byte[] pinnedServerCert = null;
        boolean acceptOnlySelfSignedServerCert = false;
        KeyManager clientCertAuthKeyManager = null;
        if (options != null) {
            // Pinned Certificate:
            Object opt = options.get(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT);
            if (opt instanceof byte[]) { pinnedServerCert = (byte[]) opt; }

            // Accept only self-signed server cert mode:
            opt = options.get(C4Replicator.REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT);
            if (opt instanceof Boolean) { acceptOnlySelfSignedServerCert = (boolean) opt; }

            clientCertAuthKeyManager = getAuthenticator();
        }

        // KeyManager for client cert authentication:
        KeyManager[] keyManagers = null;
        if (clientCertAuthKeyManager != null) { keyManagers = new KeyManager[] {clientCertAuthKeyManager}; }

        // TrustManager for server cert verification:
        final X509TrustManager trustManager
            = new CBLTrustManager(pinnedServerCert, acceptOnlySelfSignedServerCert, serverCertsListener);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, new TrustManager[] {trustManager}, null);
        builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);

        // HostnameVerifier:
        if (pinnedServerCert != null || acceptOnlySelfSignedServerCert) {
            // As the certificate will need to be matched with the pinned certificate,
            // accepts any host name specified in the certificate.
            builder.hostnameVerifier((s, sslSession) -> true);
        }
    }

    @SuppressWarnings("unchecked")
    private KeyManager getAuthenticator() {
        final Object opt = options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (!(opt instanceof Map)) { return null; }
        final Map<String, Object> auth = (Map<String, Object>) opt;

        if (!C4Replicator.AUTH_TYPE_CLIENT_CERT.equals(auth.get(C4Replicator.REPLICATOR_AUTH_TYPE))) { return null; }

        final Object certKey = auth.get(C4Replicator.REPLICATOR_AUTH_CLIENT_CERT_KEY);

        KeyManager keyManager = null;
        if (certKey instanceof Long) { keyManager = KEY_MANAGERS.getObjFromContext((long) certKey); }
        if (keyManager == null) { Log.i(TAG, "No key manager configured for client certificate authentication"); }

        return keyManager;
    }
}

