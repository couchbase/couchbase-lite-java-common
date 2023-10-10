//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicLong;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


public abstract class C4Peer implements AutoCloseable {
    /**
     * WARNING:
     * Be very careful with the implementations of this class!
     * Anything to which a PeerCleaner holds a reference (even implicitly, as a lambda)
     * will be visible until its clean() method is called  If the reference is to the object
     * that the Cleaner is supposed to clean up, that object will be permanently visible
     * and never eligible for collection: a permanent memory leak
     */
    public interface PeerCleaner {
        void dispose(long peer);
    }

    private static class PeerHolder implements Cleaner.Cleanable {

        @NonNull
        private final AtomicLong peerRef;
        @NonNull
        private final PeerCleaner cleaner;

        // Instrumentation
        @Nullable
        private final Exception createdAt;
        @Nullable
        private final String name;

        @GuardedBy("getPeerLock()")
        @Nullable
        private Exception closedAt;

        PeerHolder(long peer, @NonNull PeerCleaner cleaner) {
            this.peerRef = new AtomicLong(peer);
            this.cleaner = cleaner;

            final boolean debug = CouchbaseLiteInternal.debugging();
            createdAt = (!debug) ? null : new Exception();
            this.name = (!debug) ? getClass().getSimpleName() : toString();
        }

        /**
         * Cleanup: Release the native peer.
         */
        @SuppressFBWarnings("JLM_JSR166_UTILCONCURRENT_MONITORENTER")
        public final void clean(boolean finalizing) {
            final long peer = peerRef.getAndSet(0);
            if (peer == 0) { return; }

            if (finalizing) {
                Log.d(LogDomain.DATABASE, "Peer %x for %s was not explicitly closed", createdAt, peer, name);
            }

            final Exception closeLoc = (!CouchbaseLiteInternal.debugging()) ? null : new Exception("Closed at");

            // don't release the peer while it is in use
            synchronized (peerRef) {
                closedAt = closeLoc;
                cleaner.dispose(peer);
            }
        }

        // Log an attempt to use a closed peer.
        private void logBadCall() {
            final Exception closedLoc;
            synchronized (getPeerLock()) { closedLoc = closedAt; }
            Log.w(LogDomain.DATABASE, "Operation on closed native peer", new Exception("Used at", closedLoc));
        }

        @NonNull
        private Object getPeerLock() { return peerRef; }
    }

    private static final Cleaner CLEANER = new Cleaner("c4peer", 3);


    @NonNull
    private final PeerHolder peerHolder;
    @NonNull
    final Cleaner.Cleanable cleaner;

    protected C4Peer(long peer, @NonNull PeerCleaner cleaner) {
        this.peerHolder = new PeerHolder(Preconditions.assertNotZero(peer, "peer"), cleaner);
        this.cleaner = CLEANER.register(this, peerHolder);
    }

    /**
     * Explicit close: use the cleaner to clean up
     * the Cleaner, and, exactly once, call dispose.
     */
    @Override
    @CallSuper
    public void close() { cleaner.clean(false); }

    // Guarantees that the peer cannot be released while performing fn
    protected final <E extends Exception> void voidWithPeer(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) {
                fn.accept(peer);
                return;
            }

            peerHolder.logBadCall();
        }
    }

    // Guarantees that the peer cannot be released while performing fn
    protected final <E extends Exception> void voidWithPeerOrThrow(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) {
                fn.accept(peer);
                return;
            }
        }

        peerHolder.logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    // Guarantees that the peer cannot be released while performing fn
    @NonNull
    protected final <R, E extends Exception> R withPeerOrDefault(
        @NonNull R def,
        @NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) {
                final R val = fn.apply(peer);
                return (val != null) ? val : def;
            }
        }

        peerHolder.logBadCall();
        return def;
    }

    // Guarantees that the peer cannot be released while performing fn
    @Nullable
    protected final <R, E extends Exception> R nullableWithPeerOrNull(@NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) { return fn.apply(peer); }

            peerHolder.logBadCall();
            return null;
        }
    }

    // Guarantees that the peer cannot be released while performing fn
    @NonNull
    protected final <R, E extends Exception> R nonNullWithPeerOrThrow(
        @NonNull Fn.NonNullFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) { return fn.apply(peer); }
        }

        peerHolder.logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    /**
     * Get this object's lock.
     * Enables subclasses to assure atomicity with this class' internal behavior.
     * You *know* you gotta be careful with this, right?
     *
     * @return the lock used by this object
     */
    @NonNull
    protected final Object getPeerLock() { return peerHolder.getPeerLock(); }
}
