package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4DocumentObserver;


public final class NativeC4DocumentObserver implements C4DocumentObserver.NativeImpl {
    @Override
    public long nCreate(long peer, long token, String docId) throws LiteCoreException {
        return create(peer, token, docId);
    }

    @Override
    public void nFree(long peer) { free(peer); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    // Thread safety verified as of 2025/5/15
    //-------------------------------------------------------------------------

    private static native long create(long peer, long token, String docID) throws LiteCoreException;

    private static native void free(long peer);
}
