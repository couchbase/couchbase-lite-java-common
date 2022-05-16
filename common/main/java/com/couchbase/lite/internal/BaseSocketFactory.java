//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.sockets.SocketFromCore;
import com.couchbase.lite.internal.sockets.SocketToCore;


public interface BaseSocketFactory {

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Lookup table: maps a handle to a peer native socket to its Java companion
    @NonNull
    @VisibleForTesting
    TaggedWeakPeerBinding<BaseSocketFactory> BOUND_SOCKET_FACTORIES = new TaggedWeakPeerBinding<>();

    //-------------------------------------------------------------------------
    // Public static Methods
    //-------------------------------------------------------------------------

    static long bindSocketFactory(@NonNull BaseSocketFactory socketFactory) {
        final long token = BOUND_SOCKET_FACTORIES.reserveKey();
        BOUND_SOCKET_FACTORIES.bind(token, socketFactory);
        return token;
    }

    @Nullable
    static BaseSocketFactory getBoundSocketFactory(long token) { return BOUND_SOCKET_FACTORIES.getBinding(token); }

    static void unbindSocketFactory(long token) { BOUND_SOCKET_FACTORIES.unbind(token); }


    //-------------------------------------------------------------------------
    // Interface Methods
    //-------------------------------------------------------------------------

    @NonNull
    SocketFromCore createSocket(
        @NonNull SocketToCore toCore,
        @NonNull String scheme,
        @NonNull String host,
        int port,
        @NonNull String path,
        @NonNull byte[] opts);
}
