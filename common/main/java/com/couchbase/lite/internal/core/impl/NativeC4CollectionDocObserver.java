package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.internal.core.C4CollectionDocObserver;


public class NativeC4CollectionDocObserver implements C4CollectionDocObserver.NativeImpl {
    @Override
    public long nCreate(long coll, String docId) { return create(coll, docId); }

    @Override
    public void nFree(long peer) { free(peer); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long coll, String docID);
    private static native void free(long peer);
}
