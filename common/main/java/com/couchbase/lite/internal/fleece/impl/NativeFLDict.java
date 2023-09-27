package com.couchbase.lite.internal.fleece.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.fleece.FLDict;


public final class NativeFLDict implements FLDict.NativeImpl {
    @Override
    public long nCount(long dict) { return count(dict); }

    @Override
    public long nGet(long dict, @NonNull byte[] keyString) { return get(dict, keyString); }

    // Iterator

    @Override
    public long nInit(long dict) { return init(dict); }

    @Override
    public long nGetCount(long itr) { return getCount(itr); }

    @Override
    public boolean nNext(long itr) { return next(itr); }

    @Override
    @Nullable
    public String nGetKey(long itr) { return getKey(itr); }

    @Override
    public long nGetValue(long itr) { return getValue(itr); }

    @Override
    public void nFree(long itr) { free(itr); }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long count(long dict);

    private static native long get(long dict, @NonNull byte[] keyString);

    // Iterator

    private static native long init(long dict);

    private static native long getCount(long itr);

    private static native boolean next(long itr);

    @Nullable
    private static native String getKey(long itr);

    private static native long getValue(long itr);

    private static native void free(long itr);
}
