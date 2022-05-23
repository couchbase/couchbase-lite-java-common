package com.couchbase.lite.internal.fleece.impl;

import com.couchbase.lite.internal.fleece.FLArray;


public class NativeFLArray implements FLArray.NativeImpl {
    @Override
    public long nCount(long array) { return count(array); }

    @Override
    public long nGet(long array, long index) { return get(array, index); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long count(long array);

    private static native long get(long array, long index);
}
