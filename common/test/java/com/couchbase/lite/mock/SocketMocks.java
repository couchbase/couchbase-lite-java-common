//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
import com.couchbase.lite.internal.sockets.CloseStatus;
import com.couchbase.lite.internal.sockets.SocketFromCore;
import com.couchbase.lite.internal.sockets.SocketFromRemote;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.sockets.SocketToRemote;
import com.couchbase.lite.internal.utils.Fn;


// Implementing this class in Kotlin will crash Kotlin compilers 2.[01].x
public class SocketMocks {
    public static final class CBLWebSocket extends AbstractCBLWebSocket {
        public CBLWebSocket(
            @NonNull SocketToRemote toRemote,
            @NonNull SocketToCore toCore,
            @NonNull URI uri,
            byte[] opts,
            @NonNull CBLCookieStore cookieStore,
            @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
            super(toRemote, toCore, uri, opts, cookieStore, serverCertsListener);
        }

        @Nullable
        protected CloseStatus handleClose(@NonNull Throwable error) { return null; }

        protected int handleCloseCause(@NonNull Throwable error) { return 0; }
    }

    public static class Core implements SocketToCore {
        @NonNull
        private final Object lock = new Object();

        @NonNull
        public Object getLock() { return this.lock; }

        public void init(@NonNull SocketFromCore listener) { throw new NotImplementedError(); }

        public void close() { throw new NotImplementedError(); }

        public void ackWriteToCore(long byteCount) { throw new NotImplementedError(); }

        public void writeToCore(@NonNull byte[] data) { throw new NotImplementedError(); }

        public void requestCoreClose(@NonNull CloseStatus status) { throw new NotImplementedError(); }

        public void closeCore(@NonNull CloseStatus status) { throw new NotImplementedError(); }

        public void ackOpenToCore(int httpStatus, @Nullable byte[] responseHeadersFleece) {
            throw new NotImplementedError();
        }
    }

    public static class Remote implements SocketToRemote {
        public void close() { throw new NotImplementedError(); }

        public void init(@NonNull SocketFromRemote listener) { throw new NotImplementedError(); }

        public boolean writeToRemote(@NonNull byte[] data) { throw new NotImplementedError(); }

        public boolean closeRemote(@NonNull CloseStatus status) { throw new NotImplementedError(); }

        public void cancelRemote() { throw new NotImplementedError(); }

        public boolean openRemote(@NonNull URI uri, @Nullable Map<String, Object> options) {
            throw new NotImplementedError();
        }
    }

    public static class CookieStore implements CBLCookieStore {
        public void setCookies(@NonNull URI uri, @NonNull List<String> cookies, boolean acceptParents) {
            throw new NotImplementedError();
        }

        @Nullable
        public String getCookies(@NonNull URI uri) { throw new NotImplementedError(); }
    }

    public static class NotImplementedError extends RuntimeException {
        public NotImplementedError() { super("Not Implemented"); }
    }
}
