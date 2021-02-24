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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.system.ErrnoException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.util.List;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.utils.Fn;


public class CBLWebSocket extends AbstractCBLWebSocket {
    // Posix errno values with Android.
    // from sysroot/usr/include/asm-generic/errno.h
    private static final int ECONNRESET = 104;    // java.net.SocketException
    private static final int ECONNREFUSED = 111;  // java.net.ConnectException


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
        if (checkErrnoException(error)) { return true; }

        // ConnectException
        if (error instanceof ConnectException) {
            closed(C4Constants.ErrorDomain.POSIX, ECONNREFUSED, null);
            return true;
        }

        // SocketException
        if (error instanceof SocketException) {
            closed(C4Constants.ErrorDomain.POSIX, ECONNRESET, null);
            return true;
        }

        return false;
    }

    private boolean checkErrnoException(@NonNull Throwable error) {
        Throwable cause = error.getCause();
        if (cause == null) { return false; }

        cause = cause.getCause();
        if (!(cause instanceof ErrnoException)) { return false; }

        closed(C4Constants.ErrorDomain.POSIX, ((ErrnoException) cause).errno, null);

        return true;
    }
}
