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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4CollectionObserver;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.listener.ChangeNotifier;
import com.couchbase.lite.internal.logging.Log;


public final class C4CollectionObserver
    extends C4NativePeer
    implements ChangeNotifier.C4ChangeProducer<C4DocumentChange> {
    public interface NativeImpl {
        long nCreate(long token, long coll) throws LiteCoreException;
        @NonNull
        C4DocumentChange[] nGetChanges(long peer, int maxChanges);
        void nFree(long peer);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4CollectionObserver();

    private static final TaggedWeakPeerBinding<C4CollectionObserver> BOUND_OBSERVERS = new TaggedWeakPeerBinding<>();

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long token) {
        Log.d(LogDomain.DATABASE, "C4CollectionObserver.callback @%x", token);

        final C4CollectionObserver observer = BOUND_OBSERVERS.getBinding(token);
        if (observer == null) { return; }

        observer.listener.run();
    }

    //-------------------------------------------------------------------------
    // Static factory methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4CollectionObserver newObserver(long c4Coll, @NonNull Runnable listener) throws LiteCoreException {
        return newObserver(NATIVE_IMPL, c4Coll, listener);
    }

    @VisibleForTesting
    @NonNull
    static C4CollectionObserver newObserver(@NonNull NativeImpl impl, long c4Coll, @NonNull Runnable listener)
        throws LiteCoreException {
        final long token = BOUND_OBSERVERS.reserveKey();
        final long peer = impl.nCreate(token, c4Coll);
        final C4CollectionObserver observer = new C4CollectionObserver(impl, token, peer, listener);
        BOUND_OBSERVERS.bind(token, observer);
        return observer;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @VisibleForTesting
    final long token;
    @NonNull
    private final Runnable listener;
    @NonNull
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4CollectionObserver(@NonNull NativeImpl impl, long token, long peer, @NonNull Runnable listener) {
        super(peer);
        this.impl = impl;
        this.token = token;
        this.listener = listener;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    @Nullable
    public List<C4DocumentChange> getChanges(int maxChanges) {
        final C4DocumentChange[] changes = this.<C4DocumentChange[], RuntimeException>withPeerOrThrow(peer ->
            impl.nGetChanges(peer, maxChanges));
        return (changes.length <= 0) ? null : Arrays.asList(changes);
    }

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
                BOUND_OBSERVERS.unbind(token);
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
