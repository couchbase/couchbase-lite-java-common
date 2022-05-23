package com.couchbase.lite.internal.fleece.impl;

import com.couchbase.lite.internal.fleece.FLDict;


public class NativeFLDict implements FLDict.NativeImpl {
    @Override
    public long nCount(long dict) { return count(dict); }

    @Override
    public long nGet(long dict, byte[] keyString) { return get(dict, keyString); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Returns the number of items in a dictionary, or 0 if the pointer is nullptr.
     *
     * @param dict FLDict
     * @return uint32_t
     */
    private static native long count(long dict);

    /**
     * Looks up a key in a _sorted_ dictionary, using a shared-keys mapping.
     *
     * @param dict      FLDict
     * @param keyString FLSlice
     * @return FLValue
     */
    private static native long get(long dict, byte[] keyString);
}
