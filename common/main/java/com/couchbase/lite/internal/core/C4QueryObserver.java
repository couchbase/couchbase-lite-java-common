package com.couchbase.lite.internal.core;

import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4Listener;


public class C4QueryObserver extends C4NativePeer {

    public interface NativeImpl {
        long nCreate(long c4Query);
        void nSetEnabled(long handle, boolean enabled);
        void nFree(long handle);
        long nGetEnumerator(long handle, boolean forget) throws LiteCoreException;
    }

    @VisibleForTesting
    public C4QueryObserver(NativeImpl impl) { this.impl = impl; }

    @Override
    public void close() throws Exception { closePeer(null); }

    public C4QueryObserver newObserver(C4Query c4Query) {
        // TODO: implement newObserver()
        return null;
    }

    public void setEnabled(boolean enabled) {
        //TODO: implement setEnabled()
    }

    public C4QueryEnumerator getEnumerator(boolean forget) throws CouchbaseLiteException {
        //TODO: implement getEnumerator()
        return null;
    }

    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.LISTENER); }
        finally { super.finalize(); }
    }

    @VisibleForTesting
    static NativeImpl nativeImpl = new NativeC4QueryObserver();

    private final NativeImpl impl;

    private void closePeer(LogDomain domain) { releasePeer(domain, impl::nFree); }
}
