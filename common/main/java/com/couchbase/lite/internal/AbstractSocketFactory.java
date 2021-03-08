//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.util.List;

import com.couchbase.lite.Endpoint;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.URLEndpoint;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
import com.couchbase.lite.internal.replicator.CBLWebSocket;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;


/**
 * Base class for socket factories.
 */
public abstract class AbstractSocketFactory {
    @NonNull
    private final CBLCookieStore cookieStore;
    @NonNull
    private final Fn.Consumer<List<Certificate>> serverCertsListener;

    @NonNull
    protected final Endpoint endpoint;

    // Test instrumentation
    @GuardedBy("endpoint")
    @Nullable
    private Fn.Consumer<C4Socket> listener;

    public AbstractSocketFactory(
        @NonNull ReplicatorConfiguration config,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
        this.endpoint = config.getTarget();
        this.cookieStore = cookieStore;
        this.serverCertsListener = serverCertsListener;
    }

    public final C4Socket createSocket(long peer, String scheme, String host, int port, String path, byte[] opts) {
        final C4Socket socket = (endpoint instanceof URLEndpoint)
            ? createCBLWebSocket(peer, scheme, host, port, path, opts)
            : createPlatformSocket(peer);

        if (socket == null) { throw new IllegalStateException("Can't create endpoint: " + endpoint); }

        // Test instrumentation
        final Fn.Consumer<C4Socket> listener = getListener();
        if (listener != null) { listener.accept(socket); }

        return socket;
    }

    @NonNull
    @Override
    public String toString() { return "SocketFactory{" + "endpoint=" + endpoint + '}'; }

    @VisibleForTesting
    public final void setListener(@Nullable Fn.Consumer<C4Socket> listener) {
        synchronized (endpoint) { this.listener = listener; }
    }

    @Nullable
    protected abstract C4Socket createPlatformSocket(long peer);

    @Nullable
    private C4Socket createCBLWebSocket(long peer, String scheme, String host, int port, String path, byte[] opts) {
        final URI uri;
        try { uri = new URI(translateScheme(scheme), null, host, port, path, null, null); }
        catch (URISyntaxException e) {
            Log.w(LogDomain.NETWORK, "Bad URI for socket: %s//%s:%d/%s", e, scheme, host, port, path);
            return null;
        }

        try { return new CBLWebSocket(peer, uri, opts, cookieStore, serverCertsListener); }
        catch (Exception e) { Log.w(LogDomain.NETWORK, "Failed to instantiate CBLWebSocket", e); }

        return null;
    }

    // OkHttp doesn't understand blip or blips
    private String translateScheme(String scheme) {
        if (C4Replicator.C4_REPLICATOR_SCHEME_2.equalsIgnoreCase(scheme)) { return C4Replicator.WEBSOCKET_SCHEME; }

        if (C4Replicator.C4_REPLICATOR_TLS_SCHEME_2.equalsIgnoreCase(scheme)) {
            return C4Replicator.WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        return scheme;
    }

    private Fn.Consumer<C4Socket> getListener() {
        synchronized (endpoint) { return listener; }
    }
}

