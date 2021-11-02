package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4QueryObserver;


public class NativeC4QueryObserver implements C4QueryObserver.NativeImpl {

    @Override
    public long nCreate(long token, long c4Query) { return create(token, c4Query); }

    @Override
    public void nSetEnabled(long peer, boolean enabled) { setEnabled(peer, enabled); }

    @Override
    public long nGetEnumerator(long observer, boolean forget) throws LiteCoreException {
        return getEnumerator(observer, forget);
    }

    @Override
    public void nFree(long peer) { free(peer); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long token, long c4Query);

    private static native void setEnabled(long peer, boolean enabled);

    private static native long getEnumerator(long peer, boolean forget) throws LiteCoreException;

    private static native void free(long peer);
}
