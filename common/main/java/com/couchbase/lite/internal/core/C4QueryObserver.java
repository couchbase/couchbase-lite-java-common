package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import javax.annotation.Nullable;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;


public class C4QueryObserver extends C4NativePeer {

    public interface NativeImpl {
        long nCreate(long c4Query);
        void nSetEnabled(long handle, boolean enabled);
        void nFree(long handle);
        long nGetEnumerator(long handle, boolean forget) throws LiteCoreException;
    }

    // Not final for testing.
    @NonNull
    @VisibleForTesting
    private static final NativeImpl NATIVE_IMPL = new NativeC4QueryObserver();

    @NonNull
    public static C4QueryObserver create() { return new C4QueryObserver(NATIVE_IMPL); }


    @NonNull
    private final NativeImpl impl;

    @VisibleForTesting
    C4QueryObserver(@NonNull NativeImpl impl) { this.impl = impl; }

    @Override
    public void close() throws Exception { closePeer(null); }

    @Nullable
    public C4QueryObserver newObserver(@NonNull C4Query c4Query) {
        // TODO: implement newObserver()
        return null;
    }

    public void setEnabled(boolean enabled) {
        //TODO: implement setEnabled()
    }

    @Nullable
    public C4QueryEnumerator getEnumerator(boolean forget) throws CouchbaseLiteException {
        //TODO: implement getEnumerator()
        return null;
    }

    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.LISTENER); }
        finally { super.finalize(); }
    }

    private void closePeer(LogDomain domain) { releasePeer(domain, impl::nFree); }
}
