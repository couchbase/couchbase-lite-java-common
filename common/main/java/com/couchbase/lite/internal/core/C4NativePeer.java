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
import com.couchbase.lite.internal.logging.Log;
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
    @GuardedBy("getPeerLock()")
    private final long peer;
    @GuardedBy("getPeerLock()")
    private volatile boolean open = true;

    private volatile Exception history;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    protected C4NativePeer(@Nullable Long peer) {
        this(Preconditions.assertNotNull(peer, "peer handle").longValue());
    }

    protected C4NativePeer(long peer) {
        this.peer = Preconditions.assertNotZero(peer, "peer handle");
        updateHistory(peer, "Created at:");
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

    /**
     * This method is very dangerous.  A client that gets the peer reference
     * can use it after the peer has been disposed.
     *
     * @return the handle to the native peer.
     * @throws IllegalStateException if the peer has been closed.
     */
    protected final long getPeer() {
        synchronized (getPeerLock()) {
            if (open) { return this.peer; }
        }

        logBadCall();
        throw new IllegalStateException("Operation on closed peer");
    }

    protected final <E extends Exception> void withPeer(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            if (open) {
                fn.accept(this.peer);
                return;
            }
        }

        logBadCall();
    }

    @Nullable
    protected final <R, E extends Exception> R withPeerOrNull(@NonNull Fn.NullableFunctionThrows<Long, R, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            if (open) { return fn.apply(this.peer); }
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
            if (open) {
                final R val = fn.apply(this.peer);
                return (val != null) ? val : def;
            }
        }

        logBadCall();
        return def;
    }

    protected final <E extends Exception> void withPeerOrThrow(@NonNull Fn.ConsumerThrows<Long, E> fn) throws E {
        synchronized (getPeerLock()) {
            if (open) {
                fn.accept(this.peer);
                return;
            }
        }

        logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    @NonNull
    protected final <R, E extends Exception> R withPeerOrThrow(@NonNull Fn.FunctionThrows<Long, R, E> fn) throws E {
        synchronized (getPeerLock()) {
            if (open) { return fn.apply(this.peer); }
        }

        logBadCall();
        throw new IllegalStateException("Closed peer");
    }

    /**
     * Release the native peer, giving it the passed goodbye-kiss.
     * When this method is used to release a peer that should already have
     * been released (say, in a finalizer for an [Auto]Closable object) pass a non-null
     * domain to produce a log message if the peer has not already bee freed.
     * If this method is expecting to free the object (e.g., from a close() method)
     * passing a null domain will prevent it from logging.
     * <p>
     * Be careful about passing functions that seize locks: it would be easy to cause deadlocks.
     *
     * @param domain Domain for the error message if this call frees the peer.  No error message if null.
     * @param fn     The goodbye-kiss.  Be careful if this function seizes locks
     * @param <E>    The type of exception (typically none) thrown by the goodbye-kiss
     * @throws E the goodbye kiss failed.
     */
    @SuppressWarnings("PMD.PrematureDeclaration")
    protected final <E extends Exception> void releasePeer(
        @Nullable LogDomain domain,
        @Nullable Fn.ConsumerThrows<Long, E> fn)
        throws E {
        final long peer = releasePeer(fn);

        if (!CouchbaseLiteInternal.debugging()) { return; }

        if (domain == null) {
            // here (domain == null) if we don't expect the peer to have been closed

            // it was: this is bad: log the call
            if (peer == 0L) { logBadCall(); }
        }
        else {
            // here (domain != null) if we expect the peer to have been closed

            // it wasn't: this probably happens a lot (object is being finalized)
            if (peer != 0L) { logCall(domain, "Expected this peer to have been closed previously"); }
        }

        updateHistory(peer, "Released at:");
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private <E extends Exception> long releasePeer(@Nullable Fn.ConsumerThrows<Long, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = releasePeerLocked();
            if ((peer != 0L) && (fn != null)) { fn.accept(peer); }
            return peer;
        }
    }

    @GuardedBy("lock")
    private long releasePeerLocked() {
        final boolean open = this.open;
        this.open = false;
        return (!open) ? 0L : this.peer;
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void updateHistory(long peer, @NonNull String msg) {
        if ((peer == 0) || !CouchbaseLiteInternal.debugging()) { return; }
        history = new Exception(msg, history);
    }

    private void logBadCall() { logCall(LogDomain.DATABASE, "Operation on closed native peer"); }

    private void logCall(@NonNull LogDomain domain, @NonNull String message) {
        if (!CouchbaseLiteInternal.debugging()) { return; }
        final long peer = this.peer; // unsynchronized access: prolly ok for logging.
        Log.d(
            domain,
            "%s@0x%x: " + message,
            new Exception("At: ", history),
            getClass().getSimpleName(),
            peer);
    }
}
