package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4DocumentObserver;


public class NativeC4DocumentObserver implements C4DocumentObserver.NativeImpl {
    @Override
    public long nCreate(long coll, String docId) throws LiteCoreException { return create(coll, docId); }

    @Override
    public void nFree(long peer) { free(peer); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long coll, String docID) throws LiteCoreException;
    private static native void free(long peer);
}
