//
// Copyright (c) 2020 Couchbase, Inc.
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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.util.List;

import com.couchbase.lite.Endpoint;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.URLEndpoint;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
import com.couchbase.lite.internal.replicator.CBLWebSocket;
import com.couchbase.lite.internal.sockets.CBLSocketException;
import com.couchbase.lite.internal.sockets.OkHttpSocket;
import com.couchbase.lite.internal.sockets.SocketFromCore;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.sockets.SocketToRemote;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Base class for socket factories.
 */
public abstract class AbstractSocketFactory implements BaseSocketFactory {
    @NonNull
    private final CBLCookieStore cookieStore;
    @NonNull
    private final Fn.Consumer<List<Certificate>> serverCertsListener;

    @NonNull
    protected final Endpoint endpoint;

    // Test instrumentation
    @GuardedBy("endpoint")
    @Nullable
    private Fn.Consumer<SocketFromCore> testListener;

    public AbstractSocketFactory(
        @NonNull ReplicatorConfiguration config,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
        this.endpoint = config.getTarget();
        this.cookieStore = cookieStore;
        this.serverCertsListener = serverCertsListener;
    }

    @Override
    @NonNull
    public final SocketFromCore createSocket(
        @NonNull SocketToCore toCore,
        @NonNull String scheme,
        @NonNull String host,
        int port,
        @NonNull String path,
        @NonNull byte[] opts) {
        final SocketFromCore fromCore = (endpoint instanceof URLEndpoint)
            ? createCBLWebSocket(toCore, scheme, host, port, path, opts)
            : createPlatformSocket(toCore);

        if (fromCore == null) { throw new CBLSocketException("Can't create endpoint: " + endpoint); }

        // Test instrumentation
        final Fn.Consumer<SocketFromCore> testListener = getTestListener();
        if (testListener != null) { testListener.accept(fromCore); }

        return fromCore;
    }

    @NonNull
    @Override
    public String toString() { return "SocketFactory{@" + endpoint + '}'; }

    @VisibleForTesting
    public final void setTestListener(@Nullable Fn.Consumer<SocketFromCore> testListener) {
        synchronized (endpoint) { this.testListener = testListener; }
    }

    @Nullable
    protected abstract SocketFromCore createPlatformSocket(@NonNull SocketToCore toCore);

    @NonNull
    private SocketFromCore createCBLWebSocket(
        @NonNull SocketToCore toCore,
        @NonNull String scheme,
        @NonNull String host,
        int port,
        @NonNull String path,
        @NonNull byte[] opts) {
        final URI uri;
        try { uri = new URI(translateScheme(scheme), null, host, port, path, null, null); }
        catch (URISyntaxException e) {
            throw new CBLSocketException(
                String.format("Bad URI for socket: %s//%s:%d/%s", scheme, host, port, path), e);
        }

        final SocketToRemote toRemote = new OkHttpSocket();
        final CBLWebSocket socket = new CBLWebSocket(toRemote, toCore, uri, opts, cookieStore, serverCertsListener);
        toRemote.init(socket);

        return socket;
    }

    // OkHttp doesn't understand blip or blips
    @NonNull
    private String translateScheme(@NonNull String scheme) {
        Preconditions.assertNotNull(scheme, "scheme");

        if (C4Replicator.C4_REPLICATOR_SCHEME_2.equalsIgnoreCase(scheme)) { return C4Replicator.WEBSOCKET_SCHEME; }

        if (C4Replicator.C4_REPLICATOR_TLS_SCHEME_2.equalsIgnoreCase(scheme)) {
            return C4Replicator.WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        return scheme;
    }

    @Nullable
    private Fn.Consumer<SocketFromCore> getTestListener() {
        synchronized (endpoint) { return testListener; }
    }
}

