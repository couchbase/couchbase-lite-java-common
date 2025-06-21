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
package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.core.C4Socket;


/**
 * The C4Listener companion object
 */
public final class NativeC4Socket implements C4Socket.NativeImpl {
    @Override
    public long nFromNative(long token, String schema, String host, int port, String path, int framing) {
        return fromNative(token, schema, host, port, path, framing);
    }

    @Override
    public void nOpened(long peer) { opened(peer); }

    @Override
    public boolean nGotPeerCertificate(long peer, byte[] cert, String hostname) {
        return gotPeerCertificate(peer, cert, hostname);
    }

    @Override
    public void nGotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece) {
        gotHTTPResponse(peer, httpStatus, responseHeadersFleece);
    }

    @Override
    public void nCompletedWrite(long peer, long byteCount) { completedWrite(peer, byteCount); }

    @Override
    public void nReceived(long peer, byte[] data) { received(peer, data); }

    @Override
    public void nCloseRequested(long peer, int status, @Nullable String message) {
        closeRequested(peer, status, message);
    }

    @Override
    public void nClosed(long peer, int errorDomain, int errorCode, String message) {
        closed(peer, errorDomain, errorCode, message);
    }

    // This method is only used by mocks in testing
    @Override
    public void nCreated(long ignore) { }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    //-------------------------------------------------------------------------

    // wrap an existing Java C4Socket in a C-native C4Socket
    private static native long fromNative(
        long token,
        String schema,
        String host,
        int port,
        String path,
        int framing);

    @GuardedBy("socLock")
    private static native void opened(long peer);

    @GuardedBy("socLock")
    private static native boolean gotPeerCertificate(long peer, byte[] cert, String hostname);

    @GuardedBy("socLock")
    private static native void gotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece);

    @GuardedBy("socLock")
    private static native void completedWrite(long peer, long byteCount);

    @GuardedBy("socLock")
    private static native void received(long peer, byte[] data);

    @GuardedBy("socLock")
    private static native void closeRequested(long peer, int status, @Nullable String message);

    @GuardedBy("socLock")
    private static native void closed(long peer, int errorDomain, int errorCode, String message);
}
