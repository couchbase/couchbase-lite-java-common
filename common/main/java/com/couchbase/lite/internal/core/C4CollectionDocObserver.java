package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;


public final class C4CollectionDocObserver extends C4DocumentObserver {
    @NonNull
    public static C4CollectionDocObserver newObserver(long c4Coll, @NonNull String id, @NonNull Runnable listener)
        throws LiteCoreException {
        return newObserver(NATIVE_IMPL, c4Coll, id, listener);
    }

    @VisibleForTesting
    @NonNull
    static C4CollectionDocObserver newObserver(
        @NonNull NativeImpl impl,
        long c4Coll,
        @NonNull String id,
        @NonNull Runnable listener)
        throws LiteCoreException {
        final long token = BOUND_OBSERVERS.reserveKey();
        final C4CollectionDocObserver observer;
        try { observer = new C4CollectionDocObserver(impl, token, impl.nCreate(c4Coll, token, id), listener); }
        catch (LiteCoreException e) {
            BOUND_OBSERVERS.unbind(token);
            throw e;
        }
        BOUND_OBSERVERS.bind(token, observer);
        return observer;
    }

    private C4CollectionDocObserver(@NonNull NativeImpl impl, long token, long peer, @NonNull Runnable listener) {
        super(impl, token, peer, listener);
    }
}
