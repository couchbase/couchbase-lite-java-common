//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.CertificateRevokedException;
import java.util.List;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.sockets.SocketToRemote;
import com.couchbase.lite.internal.utils.Fn;


public final class CBLWebSocket extends AbstractCBLWebSocket {
    public CBLWebSocket(
        @NonNull SocketToRemote toRemote,
        @NonNull SocketToCore toCore,
        @NonNull URI uri,
        @Nullable byte[] opts,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
        super(toRemote, toCore, uri, opts, cookieStore, serverCertsListener);
    }

    @Override
    protected boolean handleClose(@NonNull Throwable err) { return false; }

    @Override
    protected int handleCloseCause(@NonNull Throwable cause) {
        return (!(cause instanceof CertificateRevokedException)) ? 0 : C4Constants.NetworkError.TLS_CERT_EXPIRED;
    }
}
