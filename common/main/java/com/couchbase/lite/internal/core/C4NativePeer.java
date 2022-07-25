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
package com.couchbase.lite.internal.core;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Objects with native peers subclass this type.
 * This object's log is part of its API, so that subclasses can create actions that are
 * atomic WRT the peer.
 * Ideally, it would never expose the peer handle at all...
 * <p>
 * WARNING: This class (and subclasses, of course) are their own lock!
 * Other entities must never lock on them!
 * Other designs cause occasional failures with the error message:
 * java.lang.NullPointerException: Null reference used for synchronization (monitor-enter)
 */
public abstract class C4NativePeer implements AutoCloseable {
    private static final String HANDLE_NAME = "peer handle";

    @GuardedBy("this")
    private volatile long peer;

    // Instrumentation
    private final Exception createdAt;
    private volatile Exception releasedAt;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    protected C4NativePeer(long peer) {
        this.peer = Preconditions.assertNotZero(peer, HANDLE_NAME);
        createdAt = (!CouchbaseLiteInternal.debugging()) ? null : new Exception("Created:");
    }

    //-------------------------------------------------------------------------
    // Object methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "@x" + Long.toHexString(peer); }

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------

    /**
     * Get this object's lock.
     * Enables subclasses to assure atomicity with this class' internal behavior.
     * You *know* you gotta be careful with this, right?
     *
     * @return the lock used by this object
     */
    @NonNull
    protected final Object getPeerLock() { return this; }

    protected final void logCall(@NonNull LogDomain domain, @NonNull String message) {
        if (!CouchbaseLiteInternal.debugging()) { return; }
        Log.d(domain, message, new Exception("Called at:", (releasedAt != null) ? releasedAt : createdAt));
    }

    /**
     * This method is incredibly dangerous.  A client that gets the peer reference
     * can use it after the peer has been disposed.
     *
     * @return the handle to the native peer.
     */
    protected final long getPeer() {
        final long peer = get();
        if (peer != 0L) { return peer; }

        logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    protected final <E extends Exception> void withPeer(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) {
                fn.accept(peer);
                return;
            }
        }

        logBadCall();
    }

    @Nullable
    protected final <R, E extends Exception> R withPeerOrNull(@NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) { return fn.apply(peer); }
        }

        logBadCall();
        return null;
    }

    @NonNull
    protected final <R, E extends Exception> R withPeerOrDefault(
        @NonNull R def,
        @NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) {
                final R val = fn.apply(peer);
                return (val != null) ? val : def;
            }
        }

        logBadCall();
        return def;
    }

    @NonNull
    protected final <R, E extends Exception> R withPeerOrThrow(@NonNull Fn.FunctionThrows<Long, R, E> fn) throws E {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) { return fn.apply(peer); }
        }

        logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    /**
     * Convenience method: release peer with no message or goodbye-kiss.
     * Useful from close() methods.
     */
    protected final void releasePeer() { releasePeer(null, null); }

    /**
     * Release the native peer, giving it the passed goodbye-kiss.
     * When this method is used to release a peer that should already have been released
     * (say, in a finalizer for an [Auto]Closable object) pass a non-null domain to produce
     * an error message if the peer has not already been freed.
     * If this method is expecting to free the object (e.g., from the close() method)
     * passing a null domain will prevent it from logging an error.
     * <p>
     * Be careful about passing functions that seize locks: it would be easy to cause deadlocks.
     *
     * @param domain Domain for the error message if this call frees the peer.  No error message if null.
     * @param fn     The goodbye-kiss.  Be careful if this function seizes locks
     * @param <E>    The type of exception (typically none) thrown by the goodbye-kiss
     * @throws E the goodbye kiss failed.
     */
    protected final <E extends Exception> void releasePeer(
        @Nullable LogDomain domain,
        @Nullable Fn.ConsumerThrows<Long, E> fn)
        throws E {
        final long peer;
        synchronized (getPeerLock()) {
            peer = releasePeerLocked();
            if ((peer != 0L) && (fn != null)) { fn.accept(peer); }
        }

        if (!CouchbaseLiteInternal.debugging()) { return; }

        // domain == null means we don't expect the peer to have been closed
        if (domain == null) {
            // if it was closed, log this call
            if (peer == 0L) { logBadCall(); }
            return;
        }

        // here if we expected the peer to have been closed, and it wasn't
        if (peer != 0L) { Log.d(domain, "%s@0x%x not closed", getClass().getSimpleName(), peer); }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private long get() {
        synchronized (getPeerLock()) { return peer; }
    }

    @GuardedBy("lock")
    private long releasePeerLocked() {
        final long peer = this.peer;
        this.peer = 0L;
        if ((peer != 0L) && CouchbaseLiteInternal.debugging()) { releasedAt = new Exception("Released:", createdAt); }
        return peer;
    }

    private void logBadCall() { logCall(LogDomain.DATABASE, "Operation on closed native peer"); }
}
