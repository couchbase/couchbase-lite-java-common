package com.couchbase.lite.internal.core;

import com.couchbase.lite.LiteCoreException;


public class NativeC4QueryObserver implements C4QueryObserver.NativeImpl {

    @Override
    public long nCreate(long c4Query, int token) { return create(c4Query, (long) token); }

    @Override
    public void nSetEnabled(long handle, boolean enabled) { setEnabled(handle, enabled); }

    @Override
    public void nFree(long handle) { free(handle); }

    @Override
    public long nGetEnumerator(long observer, boolean forget) throws LiteCoreException {
        return getEnumerator(observer, forget);
    }

    /**
     * Native methods
     */

    private static native long create(long c4Query, long ctxt);

    private static native void setEnabled(long handle, boolean enabled);

    private static native void free(long observer);

    private static native long getEnumerator(long observer, boolean forget) throws LiteCoreException;
}
