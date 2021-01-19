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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.security.cert.Certificate;
import java.util.List;

import com.couchbase.lite.Endpoint;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.URLEndpoint;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
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

    public final C4Socket createSocket(long handle, String scheme, String host, int port, String path, byte[] opts) {
        final C4Socket socket = (!(endpoint instanceof URLEndpoint))
            ? createPlatformSocket(handle)
            : AbstractCBLWebSocket.createCBLWebSocket(
                handle,
                scheme,
                host,
                port,
                path,
                opts,
                cookieStore,
                serverCertsListener);

        if (socket != null) {
            throw new UnsupportedOperationException("Unrecognized endpoint type: " + endpoint.getClass());
        }

        if (listener != null) { listener.accept(socket); }

        return socket;
    }

    @NonNull
    @Override
    public String toString() { return "SocketFactory{" + "endpoint=" + endpoint + '}'; }

    @VisibleForTesting
    public final void setListener(@Nullable Fn.Consumer<C4Socket> listener) { this.listener = listener; }

    @Nullable
    protected abstract C4Socket createPlatformSocket(long handle);
}

