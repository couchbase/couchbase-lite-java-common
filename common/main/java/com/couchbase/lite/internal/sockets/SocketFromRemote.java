//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import okhttp3.OkHttpClient;


/**
 * +------+                                                                      +--------+
 * |      | ==> SocketFromCore ==> AbstractCBLWebSocket ==>   SocketToCore   ==> |        |
 * | core |                                                                      | remote |
 * |      | <==  SocketToCore  <== AbstractCBLWebSocket <== SocketFromRemote <== |        |
 * +------+                                                                      +--------+
 */
public interface SocketFromRemote {
    enum Constants implements SocketFromRemote {
        NULL;

        @NonNull
        @Override
        public Object getLock() { throw new UnsupportedOperationException(); }

        @Override
        public void setupRemoteSocketFactory(@NonNull OkHttpClient.Builder builder) { }

        @Override
        public void remoteOpened(int code, @Nullable Map<String, Object> headers) { }

        @Override
        public void remoteWrites(@NonNull byte[] data) { }

        @Override
        public void remoteRequestsClose(@NonNull CloseStatus status) { }

        @Override
        public void remoteClosed(@NonNull CloseStatus status) { }

        @Override
        public void remoteFailed(@NonNull Throwable err) { }
    }


    @NonNull
    Object getLock();

    // Set up the remote socket factory
    // This is a small concession to separation of concerns: it drags in a dependency on okhttp.
    // It probably just isn't worth the trouble of wrapping the OkHttp.Builder in something more abstract.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    void setupRemoteSocketFactory(@NonNull OkHttpClient.Builder builder) throws Exception;

    // Remote connections is open
    void remoteOpened(int code, @Nullable Map<String, Object> headers);

    // Remote sent data
    void remoteWrites(@NonNull byte[] data);

    // Remote wants to close the connection
    void remoteRequestsClose(@NonNull CloseStatus status);

    // Remote connection has been closed
    void remoteClosed(@NonNull CloseStatus status);

    // Remote connection failed
    // Invoked when a web socket has been closed due to an error reading from or writing to the network.
    // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
    void remoteFailed(@NonNull Throwable err);
}
