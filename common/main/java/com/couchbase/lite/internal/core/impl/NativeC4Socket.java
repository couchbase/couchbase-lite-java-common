//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core.impl;

import androidx.annotation.Nullable;

import com.couchbase.lite.internal.core.C4Socket;


/**
 * The C4Listener companion object
 */
public class NativeC4Socket implements C4Socket.NativeImpl {

    @Override
    public long nFromNative(long token, String schema, String host, int port, String path, int framing) {
        return fromNative(token, schema, host, port, path, framing);
    }

    @Override
    public void nRetain(long peer) { retain(peer); }

    @Override
    public void nOpened(long peer) { opened(peer); }

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

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // wrap an existing Java C4Socket in a C-native C4Socket
    private static native long fromNative(
        long token,
        String schema,
        String host,
        int port,
        String path,
        int framing);

    private static native void retain(long peer);

    private static native void opened(long peer);

    private static native void gotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece);

    private static native void completedWrite(long peer, long byteCount);

    private static native void received(long peer, byte[] data);

    private static native void closeRequested(long peer, int status, @Nullable String message);

    private static native void closed(long peer, int errorDomain, int errorCode, String message);
}
