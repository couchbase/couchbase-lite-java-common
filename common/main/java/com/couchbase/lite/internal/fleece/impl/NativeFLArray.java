package com.couchbase.lite.internal.fleece.impl;

import com.couchbase.lite.internal.fleece.FLArray;


public final class NativeFLArray implements FLArray.NativeImpl {
    @Override
    public long nCount(long array) { return count(array); }

    @Override
    public long nGet(long array, long index) { return get(array, index); }


    // Iterator

    @Override
    public long nInit(long array) { return init(array); }

    @Override
    public long nGetValue(long peer) { return getValue(peer); }

    @Override
    public long nGetValueAt(long peer, int offset) { return getValueAt(peer, offset); }

    @Override
    public boolean nNext(long peer) { return next(peer); }

    @Override
    public void nFree(long peer) { free(peer); }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long count(long array);

    private static native long get(long array, long index);


    // Iterator

    private static native long init(long array);

    private static native long getValue(long peer);

    private static native long getValueAt(long peer, int offset);

    private static native boolean next(long peer);

    private static native void free(long peer);
}
