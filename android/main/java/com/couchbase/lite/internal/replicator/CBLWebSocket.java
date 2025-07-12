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
package com.couchbase.lite.internal.replicator;

import android.annotation.SuppressLint;
import android.os.Build;
import android.system.ErrnoException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.CertificateRevokedException;
import java.util.List;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.sockets.CloseStatus;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.sockets.SocketToRemote;
import com.couchbase.lite.internal.utils.Fn;


public final class CBLWebSocket extends AbstractCBLWebSocket {
    // Framing is always MESSAGE_STREAM
    public CBLWebSocket(
        @NonNull SocketToRemote toRemote,
        @NonNull SocketToCore toCore,
        @NonNull URI uri,
        @Nullable byte[] opts,
        @Nullable CBLCookieStore cookieStore,
        @Nullable Fn.Consumer<List<Certificate>> serverCertsListener) {
        super(toRemote, toCore, uri, opts, cookieStore, serverCertsListener);
    }

    @Nullable
    @Override
    protected CloseStatus handleClose(@NonNull Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ErrnoException) {
                return new CloseStatus(
                    C4Constants.ErrorDomain.POSIX,
                    ((ErrnoException) cause).errno,
                    error.toString());
            }
        }

        return null;
    }

    @SuppressLint("NewApi")
    @Override
    protected int handleCloseCause(@NonNull Throwable cause) {
        return (Build.VERSION.SDK_INT < 24) ? 0 : handleCloseCausePostAPI23(cause);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int handleCloseCausePostAPI23(Throwable cause) {
        return (!(cause instanceof CertificateRevokedException)) ? 0 : C4Constants.NetworkError.TLS_CERT_EXPIRED;
    }
}
