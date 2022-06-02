package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4CollectionDocObserver;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.support.Log;


public final class C4CollectionDocObserver extends C4NativePeer {
    public interface NativeImpl {
        long nCreate(long coll, String docId);
        void nFree(long peer);
    }

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4CollectionDocObserver();

    private static final NativeRefPeerBinding<C4CollectionDocObserver> BOUND_OBSERVERS = new NativeRefPeerBinding<>();


    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long peer, @Nullable String docID, long sequence) {
        Log.d(
            LogDomain.DATABASE,
            "C4CollectionDocObserver.callback @0x%x (%s): %s", peer, sequence, docID);

        final C4CollectionDocObserver observer = BOUND_OBSERVERS.getBinding(peer);
        if (observer == null) { return; }

        observer.listener.run();
    }

    //-------------------------------------------------------------------------
    // Static factory methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4CollectionDocObserver newObserver(long c4Coll, @NonNull String docId, @NonNull Runnable listener) {
        return newObserver(NATIVE_IMPL, c4Coll, docId, listener);
    }

    @VisibleForTesting
    @NonNull
    static C4CollectionDocObserver newObserver(
        @NonNull NativeImpl impl,
        long c4Coll,
        @NonNull String id,
        @NonNull Runnable listener) {
        final C4CollectionDocObserver observer = new C4CollectionDocObserver(impl, impl.nCreate(c4Coll, id), listener);
        BOUND_OBSERVERS.bind(observer.getPeer(), observer);
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

    private C4CollectionDocObserver(@NonNull NativeImpl impl, long collection, @NonNull Runnable listener) {
        super(collection);
        this.impl = impl;
        this.listener = listener;
    }

    @Override
    public void close() { closePeer(null); }

    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) {
        BOUND_OBSERVERS.unbind(getPeerUnchecked());
        releasePeer(domain, impl::nFree);
    }
}
