package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;


public final class C4CollectionDocObserver extends C4DocumentObserver {

    @NonNull
    public static C4CollectionDocObserver newObserver(long c4Coll, @NonNull String id, @NonNull Runnable listener) {
        return newObserver(NATIVE_IMPL, c4Coll, id, listener);
    }

    @VisibleForTesting
    @NonNull
    static C4CollectionDocObserver newObserver(
        @NonNull NativeImpl impl,
        long c4Coll,
        @NonNull String id,
        @NonNull Runnable listener
    ) {
        final long peer = impl.nCreate(c4Coll, id);
        final C4CollectionDocObserver observer = new C4CollectionDocObserver(impl, peer, listener);
        BOUND_OBSERVERS.bind(peer, observer);
        return observer;
    }

    private C4CollectionDocObserver(@NonNull NativeImpl impl, long peer, @NonNull Runnable listener) {
        super(impl, peer, listener);
    }
}
