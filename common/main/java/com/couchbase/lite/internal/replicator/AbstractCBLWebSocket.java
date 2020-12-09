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

import android.support.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
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
import okhttp3.Route;
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


@SuppressWarnings({"PMD.GodClass", "PMD.ExcessiveImports"})
public class AbstractCBLWebSocket extends C4Socket {
    private static final LogDomain TAG = LogDomain.NETWORK;

    private static final int MAX_AUTH_RETRIES = 3;

    private static final int HTTP_STATUS_MIN = 100;
    private static final int HTTP_STATUS_MAX = 600;

    // A complimentary status
    private class Status {
        static final short INITIAL = 1;
        static final short OPENED = 2;
        static final short REQUEST_CLOSED = 3;

        private short status = INITIAL;

        synchronized short getAndSet(short set) {
            short old = status;
            status = set;
            return old;
        }

        synchronized short compareAndSet(short expect, short update) {
            short old = status;
            if (status == expect) {
                status = update;
            }
            return old;
        }
    }

    /**
     * Workaround to enable both TLS1.1 and TLS1.2 for Android API 16 - 19.
     * When starting to support from API 20, we could remove the workaround.
     */
    private static class TLSSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        TLSSocketFactory(KeyManager[] keyManagers, TrustManager[] trustManagers, SecureRandom secureRandom)
            throws GeneralSecurityException {
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, trustManagers, secureRandom);
            delegate = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }

        @Override
        public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return setEnabledProtocols(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return setEnabledProtocols(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return setEnabledProtocols(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress address, int port) throws IOException {
            return setEnabledProtocols(delegate.createSocket(address, port));
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int port, InetAddress localAddress, int localPort)
            throws IOException {
            return setEnabledProtocols(delegate.createSocket(inetAddress, port, localAddress, localPort));
        }

        private Socket setEnabledProtocols(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});
            }
            return socket;
        }
    }

    class CBLWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.v(TAG, "WebSocketListener opened with response " + response);
            switch (status.compareAndSet(Status.INITIAL, Status.OPENED)) {
                case Status.INITIAL:
                    receivedHTTPResponse(response);
                    Log.i(TAG, "WebSocket CONNECTED!");
                    opened();
                    break;
                case Status.OPENED:
                    Log.i(TAG, "WebSocket onOpen called when connection is on, which should not happend");
                    break;
                case Status.REQUEST_CLOSED:
                    Log.i(TAG, "WebSocket connection established after request close");
                    break;
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.v(TAG, "WebSocketListener received text string with length of " + text.length());
            received(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.v(TAG, "WebSocketListener received data of " + bytes.size() + " bytes");
            received(bytes.toByteArray());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.v(TAG, "WebSocketListener is closing with code " + code + ", reason " + reason);
            closeRequested(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.v(TAG, "WebSocketListener closed with code " + code + ", reason " + reason);
            didClose(code, reason);
        }

        // NOTE: from CBLStatus.mm
        // {kCFErrorHTTPConnectionLost,                {POSIXDomain, ECONNRESET}},
        // {kCFURLErrorCannotConnectToHost,            {POSIXDomain, ECONNREFUSED}},
        // {kCFURLErrorNetworkConnectionLost,          {POSIXDomain, ECONNRESET}},

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.w(TAG, "WebSocketListener failed with response " + response, t);

            // Invoked when a web socket has been closed due to an error reading from or writing to the network.
            // Both outgoing and incoming messages may have been lost. No further calls to this listener will be made.
            if (response == null) {
                didClose(t);
                return;
            }

            final int httpStatus = response.code();
            if (httpStatus == 101) {
                didClose(C4Socket.WS_STATUS_CLOSE_PROTOCOL_ERROR, response.message());
                return;
            }

            int closeCode = C4Socket.WS_STATUS_CLOSE_POLICY_ERROR;
            if (httpStatus >= 300 && httpStatus < 1000) { closeCode = httpStatus; }
            didClose(closeCode, response.message());
        }
    }

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    @NonNull
    public static final NativeContext<KeyManager> CLIENT_CERT_AUTH_KEY_MANAGER = new NativeContext<>();

    @NonNull
    private static final OkHttpClient BASE_HTTP_CLIENT = new OkHttpClient.Builder()
        // timeouts: Core manages this: set no timeout, here.
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        // redirection
        .followRedirects(true)
        .followSslRedirects(true)
        .build();

    //-------------------------------------------------------------------------
    // Factory method
    //-------------------------------------------------------------------------

    // Creates an instance of the subclass CBLWebSocket
    public static CBLWebSocket createCBLWebSocket(
        long handle,
        String scheme,
        String hostname,
        int port,
        String path,
        byte[] options,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
        Log.v(TAG, "Creating a CBLWebSocket ...");

        Map<String, Object> fleeceOptions = null;
        if (options != null) { fleeceOptions = FLValue.fromData(options).asDict(); }

        // NOTE: OkHttp can not understand blip/blips
        if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_SCHEME_2)) {
            scheme = C4Replicator.WEBSOCKET_SCHEME;
        }
        else if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_TLS_SCHEME_2)) {
            scheme = C4Replicator.WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        try {
            return new CBLWebSocket(handle, scheme, hostname, port, path, fleeceOptions,
                cookieStore, serverCertsListener);
        }
        catch (Exception e) { Log.e(TAG, "Failed to instantiate CBLWebSocket", e); }

        return null;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final Status status = new Status();
    private final OkHttpClient httpClient;
    private final CBLWebSocketListener wsListener;
    private final URI uri;
    private final Map<String, Object> options;
    private final CBLCookieStore cookieStore;
    private final Fn.Consumer<List<Certificate>> serverCertsListener;
    private WebSocket webSocket;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected AbstractCBLWebSocket(
        long handle,
        String scheme,
        String hostname,
        int port,
        String path,
        Map<String, Object> options,
        CBLCookieStore cookieStore,
        Fn.Consumer<List<Certificate>> serverCertsListener)
        throws GeneralSecurityException, URISyntaxException {
        super(handle);
        this.uri = new URI(checkScheme(scheme), null, hostname, port, path, null, null);
        this.options = options;
        this.cookieStore = cookieStore;
        this.serverCertsListener = serverCertsListener;
        this.httpClient = setupOkHttpClient();
        this.wsListener = new CBLWebSocketListener();
    }

    @Override
    @NonNull
    public String toString() { return "AbstractCBLWebSocket{" + uri + "}"; }

    //-------------------------------------------------------------------------
    // Abstract method implementation
    //-------------------------------------------------------------------------

    @Override
    protected void openSocket() {
        Log.v(TAG, String.format(Locale.ENGLISH, "CBLWebSocket is connecting to %s ...", uri));
        webSocket = httpClient.newWebSocket(newRequest(), wsListener);
    }

    @Override
    protected void send(byte[] allocatedData) {
        if (!webSocket.send(ByteString.of(allocatedData, 0, allocatedData.length))) {
            Log.e(TAG, "CBLWebSocket failed to send data of length = " + allocatedData.length);
            return;
        }

        completedWrite(allocatedData.length);
    }

    @Override
    protected void completedReceive(long byteCount) { }

    @Override
    protected void close() { }

    protected boolean handleClose(Throwable error) { return false; }

    @Override
    protected void requestClose(int status, String message) {
        if (webSocket == null) {
            Log.w(TAG, "CBLWebSocket was not initialized before receiving close request.");
            return;
        }

        if (closing.getAndSet(true)) {
            Log.v(TAG, "CBLWebSocket already closing.");
            return;
        }

        if (this.status.getAndSet(Status.REQUEST_CLOSED) == Status.INITIAL) {
            Log.w(TAG, "CBLWebSocket connection was not established before receiving close request.");
            webSocket.cancel();
            return;
        }

        // Core will, apparently, randomly send HTTP statuses in this, purely WS, call.
        // Just recast them as policy errors.
        if ((status > HTTP_STATUS_MIN) && (status < HTTP_STATUS_MAX)) {
            status = C4Socket.WS_STATUS_CLOSE_POLICY_ERROR;
        }

        if (!webSocket.close(status, message)) {
            Log.w(TAG, "CBLWebSocket failed to initiate a graceful shutdown of this web socket.");
        }
    }

    //-------------------------------------------------------------------------
    // package visible methods
    //-------------------------------------------------------------------------

    // http://www.ietf.org/rfc/rfc2617.txt
    @Nullable
    Request authenticate(@Nullable Route ignore, @NonNull Response resp, @NonNull String user, @NonNull String pwd) {
        Log.v(TAG, "CBLWebSocket authenticated for response " + resp);

        // If failed 3 times, give up.
        if (responseCount(resp) >= MAX_AUTH_RETRIES) { return null; }

        final List<Challenge> challenges = resp.challenges();
        Log.v(TAG, "CBLWebSocket received challenges " + challenges);
        if (challenges == null) { return null; }

        for (Challenge challenge: challenges) {
            if ("Basic".equals(challenge.scheme())) {
                return resp.request()
                    .newBuilder()
                    .header("Authorization", Credentials.basic(user, pwd))
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

        // Authenticator
        final Authenticator authenticator = setupBasicAuthenticator();
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
            public void saveFromResponse(HttpUrl httpUrl, List<Cookie> cookies) {
                for (Cookie cookie : cookies) {
                    cookieStore.setCookie(httpUrl.uri(), cookie.toString());
                }
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
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

    private Authenticator setupBasicAuthenticator() {
        if ((options == null) || !options.containsKey(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION)) { return null; }

        @SuppressWarnings("unchecked") final Map<String, Object> auth
            = (Map<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (auth == null) { return null; }

        final String authType = (String) auth.get(C4Replicator.REPLICATOR_AUTH_TYPE);
        if (!C4Replicator.AUTH_TYPE_BASIC.equals(authType)) { return null; }

        final String username = (String) auth.get(C4Replicator.REPLICATOR_AUTH_USER_NAME);
        final String password = (String) auth.get(C4Replicator.REPLICATOR_AUTH_PASSWORD);
        if ((username == null) || (password == null)) { return null; }

        return (route, response) -> authenticate(route, response, username, password);
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

    private void receivedHTTPResponse(Response response) {
        final int httpStatus = response.code();
        Log.v(TAG, "CBLWebSocket received HTTP response with status " + httpStatus);

        // Post the response headers to LiteCore:
        final Headers hs = response.headers();
        if ((hs == null) || (hs.size() <= 0)) { return; }

        byte[] headersFleece = null;
        final Map<String, Object> headers = new HashMap<>();
        for (int i = 0; i < hs.size(); i++) { headers.put(hs.name(i), hs.value(i)); }

        final FLEncoder enc = new FLEncoder();
        enc.write(headers);
        try { headersFleece = enc.finish(); }
        catch (LiteCoreException e) { Log.e(TAG, "CBLWebSocket failed to encode response header", e); }
        finally { enc.free(); }

        gotHTTPResponse(httpStatus, headersFleece);
    }

    private void didClose(int code, String reason) {
        if (code == C4Socket.WS_STATUS_CLOSE_NORMAL) {
            didClose(null);
            return;
        }

        Log.i(TAG, "CBLWebSocket closed: " + code + "(" + reason + ")");
        closed(C4Constants.ErrorDomain.WEB_SOCKET, code, reason);
    }

    private void didClose(Throwable error) {
        if (error == null) {
            closed(C4Constants.ErrorDomain.WEB_SOCKET, 0, null);
            return;
        }

        if (handleClose(error)) { return; }

        // TLS Certificate error
        if (error.getCause() instanceof java.security.cert.CertificateException) {
            closed(C4Constants.ErrorDomain.NETWORK, C4Constants.NetworkError.TLS_CERT_UNTRUSTED, null);
            return;
        }

        // SSLPeerUnverifiedException
        if (error instanceof javax.net.ssl.SSLPeerUnverifiedException) {
            closed(C4Constants.ErrorDomain.NETWORK, C4Constants.NetworkError.TLS_CERT_UNTRUSTED, null);
            return;
        }

        // UnknownHostException - this is thrown if Airplane mode, offline
        if (error instanceof UnknownHostException) {
            closed(C4Constants.ErrorDomain.NETWORK, C4Constants.NetworkError.UNKNOWN_HOST, null);
            return;
        }

        if (error instanceof SSLHandshakeException) {
            closed(C4Constants.ErrorDomain.NETWORK, C4Constants.NetworkError.TLS_HANDSHAKE_FAILED, null);
            return;
        }

        closed(C4Constants.ErrorDomain.WEB_SOCKET, 0, null);
    }

    //-------------------------------------------------------------------------
    // SSL Support
    //-------------------------------------------------------------------------

    private String checkScheme(String scheme) {
        // NOTE: OkHttp can not understand blip/blips
        if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_SCHEME_2)) { return C4Replicator.WEBSOCKET_SCHEME; }

        if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_TLS_SCHEME_2)) {
            return C4Replicator.WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        return scheme;
    }

    private void setupSSLSocketFactory(OkHttpClient.Builder builder) throws GeneralSecurityException {
        byte[] pinnedServerCert = null;
        boolean acceptOnlySelfSignedServerCert = false;
        KeyManager clientCertAuthKeyManager = null;
        if (options != null) {
            // Pinned Certificate:
            if (options.containsKey(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT)) {
                pinnedServerCert = (byte[]) options.get(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT);
            }

            // Accept only self-signed server cert mode:
            if (options.containsKey(C4Replicator.REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT)) {
                acceptOnlySelfSignedServerCert
                    = (boolean) options.get(C4Replicator.REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT);
            }

            clientCertAuthKeyManager = getAuthenticator();
        }

        // KeyManager for client cert authentication:
        KeyManager[] keyManagers = null;
        if (clientCertAuthKeyManager != null) { keyManagers = new KeyManager[] {clientCertAuthKeyManager}; }

        // TrustManager for server cert verification:
        final X509TrustManager trustManager
            = new CBLTrustManager(pinnedServerCert, acceptOnlySelfSignedServerCert, serverCertsListener);

        // SSLSocketFactory:
        final SSLSocketFactory sslSocketFactory
            = new TLSSocketFactory(keyManagers, new TrustManager[] {trustManager}, null);
        builder.sslSocketFactory(sslSocketFactory, trustManager);

        // HostnameVerifier:
        if (pinnedServerCert != null || acceptOnlySelfSignedServerCert) {
            // As the certificate will need to be matched with the pinned certificate,
            // accepts any host name specified in the certificate.
            builder.hostnameVerifier((s, sslSession) -> true);
        }
    }

    @SuppressWarnings("unchecked")
    private KeyManager getAuthenticator() {
        final Map<String, Object> auth
            = (Map<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (auth == null) { return null; }

        if (!C4Replicator.AUTH_TYPE_CLIENT_CERT.equals(auth.get(C4Replicator.REPLICATOR_AUTH_TYPE))) { return null; }

        final KeyManager clientCertAuthKeyManager
            = CLIENT_CERT_AUTH_KEY_MANAGER.getObjFromContext(
            (long) auth.get(C4Replicator.REPLICATOR_AUTH_CLIENT_CERT_KEY));
        if (clientCertAuthKeyManager == null) {
            Log.w(TAG, "No key manager configured for client certificate authentication");
        }

        return clientCertAuthKeyManager;
    }
}

