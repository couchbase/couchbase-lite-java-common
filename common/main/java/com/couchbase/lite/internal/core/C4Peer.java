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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
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
        @Nullable
        private final PeerCleaner cleaner;

        // Instrumentation
        @Nullable
        private final String name;
        @NonNull
        private final AtomicReference<Exception> lifecycle;

        PeerHolder(@NonNull String name, long peer, @Nullable PeerCleaner cleaner) {
            this.peerRef = new AtomicLong(peer);
            this.cleaner = cleaner;

            this.name = "peer-" + name;
            this.lifecycle
                = new AtomicReference<>((!CouchbaseLiteInternal.debugging()) ? null : new Exception("Created at:"));
        }

        /**
         * Cleanup: Release the native peer.
         */
        @SuppressFBWarnings("JLM_JSR166_UTILCONCURRENT_MONITORENTER")
        public final void clean(boolean finalizing) {
            final long peer = peerRef.getAndSet(0);
            if (peer == 0) { return; }

            // don't release the peer while it is in use
            if (cleaner != null) {
                synchronized (peerRef) { cleaner.dispose(peer); }
            }

            final Exception createdAt = lifecycle.get();
            if (createdAt == null) { return; }

            // Debug instrumentation: done only if CouchbaseLiteInternal.debugging is true
            if (!finalizing) {
                lifecycle.set(new Exception("Closed at:", createdAt));
                return;
            }

            // Keep this at the debug level and only do it if debugging.  It frightens the children.
            // Apparently this call is bizarrely expensive: just don't do it unless you really need it.
            if (name == null) { Log.d(LogDomain.DATABASE, "%s was not explicitly closed", createdAt, name); }
        }


        // Log an attempt to use a closed peer.
        private void logBadCall() {
            Log.w(LogDomain.DATABASE, "Operation on closed native peer", new Exception("Used at:", lifecycle.get()));
        }

        @NonNull
        Object getPeerLock() { return peerRef; }
    }

    private static final Cleaner CLEANER = new Cleaner("c4peer", 3);


    @NonNull
    private final String name;
    @NonNull
    private final PeerHolder peerHolder;
    @NonNull
    final Cleaner.Cleanable cleaner;


    protected C4Peer(long peer, @Nullable PeerCleaner cleaner) {
        this.name = getClass().getSimpleName() + "(0x" + Long.toHexString(peer) + ")" + ClassUtils.objId(this);

        this.peerHolder = new PeerHolder(name, Preconditions.assertNotZero(peer, "peer"), cleaner);

        // WARNING:
        // Anything to which the peerHolder holds a reference will be reachable until the cleaner is run
        this.cleaner = CLEANER.register(this, peerHolder);
    }

    //-------------------------------------------------------------------------
    // Object methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return name; }

    /**
     * Explicit close: use the cleaner to clean up
     * the Cleaner, and, exactly once, call dispose.
     */
    @Override
    @CallSuper
    public void close() { cleaner.clean(false); }

    protected final <E extends Exception> void voidWithPeerOrWarn(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) {
                fn.accept(peer);
                return;
            }

            peerHolder.logBadCall();
        }
    }

    protected final <E extends Exception> void voidWithPeerOrThrow(@NonNull Fn.ConsumerThrows<Long, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) {
                fn.accept(peer);
                return;
            }
        }

        peerHolder.logBadCall();
        throw new CouchbaseLiteError("Closed peer");
    }

    @Nullable
    protected final <R, E extends Exception> R withPeerOrNull(@NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = peerHolder.peerRef.get();
            if (peer != 0) { return fn.apply(peer); }

            peerHolder.logBadCall();
            return null;
        }
    }

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

    @NonNull
    protected final <R, E extends Exception> R withPeerOrThrow(
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
