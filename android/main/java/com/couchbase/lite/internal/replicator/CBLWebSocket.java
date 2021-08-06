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

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.system.ErrnoException;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateRevokedException;
import java.util.List;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.utils.Fn;


public class CBLWebSocket extends AbstractCBLWebSocket {

    // Framing is always MESSAGE_STREAM
    public CBLWebSocket(
        long peer,
        @NonNull URI uri,
        @Nullable byte[] opts,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener)
        throws GeneralSecurityException {
        super(peer, uri, opts, cookieStore, serverCertsListener);
    }

    @Override
    protected boolean handleClose(@NonNull Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if ((cause instanceof ErrnoException)) {
                closed(C4Constants.ErrorDomain.POSIX, ((ErrnoException) cause).errno, error.toString());
                return true;
            }
        }

        return false;
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
