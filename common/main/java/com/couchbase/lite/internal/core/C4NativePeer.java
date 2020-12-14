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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
 * The lambda tricks are similarly vulnerable.
 */
public abstract class C4NativePeer extends AtomicLong implements AutoCloseable {
    private static final String HANDLE_NAME = "peer handle";

    @GuardedBy("this")
    private Exception closedAt;

    protected C4NativePeer() {}

    protected C4NativePeer(long peer) { setPeerHandle(peer); }

    @NonNull
    @Override
    public String toString() { return "NativePeer{" + Long.toHexString(get()) + "}"; }

    protected final void setPeer(long peer) { setPeerHandle(peer); }

    protected final long getPeer() {
        final long peer = get();
        if (peer == 0) {
            logBadCall();
            throw new IllegalStateException("Operation on closed native peer");
        }

        return peer;
    }

    protected long getPeerAndClear() {
        if (CouchbaseLiteInternal.isDebugging()) {
            synchronized (this) { closedAt = new Exception(); }
        }
        return getAndSet(0L);
    }

    protected final long getPeerUnchecked() { return get(); }

    protected <T> T withPeer(T def, Fn.Function<Long, T> fn) {
        final long peer = get();
        if (peer == 0) {
            logBadCall();
            return def;
        }

        return fn.apply(peer);
    }

    protected <T> T withPeerThrows(T def, Fn.FunctionThrows<Long, T, LiteCoreException> fn) throws LiteCoreException {
        final long peer = get();
        if (peer == 0) {
            logBadCall();
            return def;
        }

        return fn.apply(peer);
    }

    protected boolean verifyPeerClosed(long peer, @Nullable LogDomain domain) {
        if (peer == 0L) { return true; }

        if (domain != null) { Log.v(domain, "Peer %x for %s was not closed", peer, getClass().getSimpleName()); }

        return false;
    }

    private void setPeerHandle(long peer) {
        Preconditions.assertZero(getAndSet(Preconditions.assertNotZero(peer, HANDLE_NAME)), HANDLE_NAME);
    }

    private void logBadCall() {
        Log.e(LogDomain.DATABASE, "Operation on closed native peer", new Exception());
        final Exception closedLoc;
        synchronized (this) { closedLoc = closedAt; }
        if (closedLoc != null) { Log.e(LogDomain.DATABASE, "Closed at", closedLoc); }
    }
}
