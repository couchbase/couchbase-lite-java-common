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

import okhttp3.OkHttpClient;
import okhttp3.Response;


public interface SocketFromRemote {
    @NonNull
    Object getLock();

    // Set up the remote socket factory
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    void setupRemoteSocketFactory(@NonNull OkHttpClient.Builder builder) throws Exception;

    // Remote connections is open
    void remoteOpened(@NonNull Response resp);

    // Remote sent data
    void remoteWrites(@NonNull byte[] data);

    // Remote wants to close the connection
    void remoteRequestedClose(int code, @NonNull String reason);

    // Remote connection has been closed
    void remoteClosed(int code, @NonNull String reason);

    // Remote connection failed
    // Invoked when a web socket has been closed due to an error reading from or writing to the network.
    // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
    void remoteFailed(@NonNull Throwable err, @Nullable Response resp);
}
