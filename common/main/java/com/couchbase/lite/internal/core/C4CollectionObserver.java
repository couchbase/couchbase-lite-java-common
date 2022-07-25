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
package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4CollectionObserver;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.support.Log;


public final class C4CollectionObserver extends C4NativePeer {
    public interface NativeImpl {
        long nCreate(long coll);
        @NonNull
        C4DocumentChange[] nGetChanges(long peer, int maxChanges);
        void nFree(long peer);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4CollectionObserver();

    private static final NativeRefPeerBinding<C4CollectionObserver> BOUND_OBSERVERS = new NativeRefPeerBinding<>();

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long peer) {
        Log.d(LogDomain.DATABASE, "C4CollectionObserver.callback @%x", peer);

        final C4CollectionObserver observer = BOUND_OBSERVERS.getBinding(peer);
        if (observer == null) { return; }

        observer.listener.run();
    }

    //-------------------------------------------------------------------------
    // Static factory methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4CollectionObserver newObserver(long c4Coll, @NonNull Runnable listener) {
        return newObserver(NATIVE_IMPL, c4Coll, listener);
    }

    @VisibleForTesting
    @NonNull
    static C4CollectionObserver newObserver(@NonNull NativeImpl impl, long c4Coll, @NonNull Runnable listener) {
        final long peer = impl.nCreate(c4Coll);
        final C4CollectionObserver observer = new C4CollectionObserver(impl, peer, listener);
        BOUND_OBSERVERS.bind(peer, observer);
        return observer;
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final Runnable listener;
    @NonNull
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4CollectionObserver(@NonNull NativeImpl impl, long peer, @NonNull Runnable listener) {
        super(peer);
        this.impl = impl;
        this.listener = listener;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    public C4DocumentChange[] getChanges(int maxChanges) {
        return withPeerOrThrow((peer) -> impl.nGetChanges(peer, maxChanges));
    }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                BOUND_OBSERVERS.unbind(peer);
                impl.nFree(peer);
            });
    }
}
