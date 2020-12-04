//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This approach doesn't actually solve the peer management problem.
 * It is still entirely possible that the peer whose handle is returned
 * by getPeer will be freed while the client is still using it.
 * <p>
 * It also exposes the native handle because anybody can call `get`.
 */
public abstract class C4NativePeer extends AtomicLong {
    private static final String HANDLE_NAME = "peer handle";
    private Exception closedAt;

    protected C4NativePeer() {}

    protected C4NativePeer(long handle) { setPeerHandle(handle); }

    protected final void setPeer(long handle) { setPeerHandle(handle); }

    protected final long getPeerUnchecked() { return get(); }

    protected long getPeerAndClear() {
        if (CouchbaseLiteInternal.isDebugging()) { closedAt = new Exception(); }
        return getAndSet(0L);
    }

    protected final long getPeer() {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            throw new IllegalStateException("Operation on closed native peer");
        }

        return handle;
    }

    // !!! This does not prevent the peer from being closed while the lambda is running
    protected <T> T withPeer(T def, Fn.Function<Long, T> fn) {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            return def;
        }

        return fn.apply(handle);
    }

    // !!! This does not prevent the peer from being closed while the lambda is running
    protected <T> T withPeerThrows(T def, Fn.FunctionThrows<Long, T, LiteCoreException> fn) throws LiteCoreException {
        final long handle = get();
        if (handle == 0) {
            logBadCall();
            return def;
        }

        return fn.apply(handle);
    }

    private void setPeerHandle(long handle) {
        Preconditions.assertZero(getAndSet(Preconditions.assertNotZero(handle, HANDLE_NAME)), HANDLE_NAME);
    }

    private void logBadCall() {
        Log.e(LogDomain.DATABASE, "Operation on closed native peer", new Exception());
        if (closedAt != null) { Log.e(LogDomain.DATABASE, "Closed at", closedAt); }
    }
}
