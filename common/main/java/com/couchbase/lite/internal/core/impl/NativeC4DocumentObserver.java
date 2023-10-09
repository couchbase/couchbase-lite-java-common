package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4DocumentObserver;


public final class NativeC4DocumentObserver implements C4DocumentObserver.NativeImpl {
    @Override
    public long nCreate(long token, long coll, String docId) throws LiteCoreException {
        return create(token, coll, docId);
    }

    @Override
    public void nFree(long peer) { free(peer); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long token, long coll, String docID) throws LiteCoreException;
    private static native void free(long peer);
}
