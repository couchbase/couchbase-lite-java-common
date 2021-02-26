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
 */
public abstract class C4NativePeer implements AutoCloseable {
    private static final String HANDLE_NAME = "peer handle";

    private final Object lock = new Object();

    @GuardedBy("lock")
    private volatile long peer;

    // Instrumentation
    @GuardedBy("lock")
    private Exception closedAt;

    // ??? questionable design
    protected C4NativePeer() { }

    protected C4NativePeer(long peer) { setPeerInternal(peer); }

    @NonNull
    @Override
    public String toString() { return Long.toHexString(peer); }

    protected final <T, E extends Exception> T withPeer(T def, Fn.FunctionThrows<Long, T, E> fn) throws E {
        synchronized (getLock()) {
            final long peer = get();
            if (peer == 0) {
                logBadCall();
                return def;
            }

            return fn.apply(peer);
        }
    }

    /**
     * Mark the peer as released.
     */
    protected final void releasePeer() {
        synchronized (lock) { releasePeerLocked(); }
    }

    /**
     * Release the native peer, giving it the passed goodbye-kiss.
     * When this method is used to release a peer that should already have been release
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
    protected final <E extends Exception> void releasePeer(@Nullable LogDomain domain, Fn.ConsumerThrows<Long, E> fn)
        throws E {
        final long peer;

        // !!! java.lang.NullPointerException: Null reference used for synchronization (monitor-enter)
        synchronized (lock) {
            peer = releasePeerLocked();
            if (peer == 0L) { return; }

            fn.accept(peer);
        }

        if (domain != null) { Log.v(domain, "Peer %x for %s was not closed", peer, getClass().getSimpleName()); }
    }

    // The next three methods should go away.
    // They invite race conditions and are accidents waiting to happen.
    // Ideally, this class should never expose the peer handle

    // ??? questionable design
    protected final void setPeer(long peer) { setPeerInternal(peer); }

    // ??? questionable design
    protected final long getPeerUnchecked() { return get(); }

    // ??? questionable design
    protected final long getPeer() {
        final long peer = get();
        if (peer == 0) {
            logBadCall();
            throw new IllegalStateException("Operation on closed native peer");
        }

        return peer;
    }

    /**
     * Get this object's lock.
     * Enables subclasses to assure atomicity with this class' internal behavior.
     * You *know* you gotta be careful with this, right?
     *
     * @return the lock used by this object
     */
    protected final Object getLock() { return lock; }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private long get() {
        synchronized (getLock()) { return peer; }
    }

    private void setPeerInternal(long peer) {
        Preconditions.assertNotZero(peer, HANDLE_NAME);
        synchronized (getLock()) {
            Preconditions.assertZero(this.peer, HANDLE_NAME);
            this.peer = peer;
        }
    }

    @GuardedBy("lock")
    private long releasePeerLocked() {
        final long peer = this.peer;
        if ((this.peer != 0) && CouchbaseLiteInternal.isDebugging()) { closedAt = new Exception(); }
        this.peer = 0L;
        return peer;
    }

    private void logBadCall() {
        Log.e(LogDomain.DATABASE, "Operation on closed native peer", new Exception());
        final Exception closedLoc;
        synchronized (getLock()) { closedLoc = closedAt; }
        if (closedLoc != null) { Log.e(LogDomain.DATABASE, "Closed at", closedLoc); }
    }
}
