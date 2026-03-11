package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.internal.core.C4BTSocketFactory;

/**
 * JNI implementation of {@link C4BTSocketFactory.NativeImpl}.
 */
public final class NativeC4BTSocketFactory implements C4BTSocketFactory.NativeImpl {

    // -------------------------------------------------------------------------
    // C4BTSocketFactory.NativeImpl
    // -------------------------------------------------------------------------

    @Override
    public long nRegisterFactory() {
        return registerBTSocketFactory();
    }

    @Override
    public long nFromNative(long token, String scheme, String host, long port, String path, int framing) {
        return fromNative(token, scheme, host, port, path, framing);
    }

    // -------------------------------------------------------------------------
    // Native declarations
    // -------------------------------------------------------------------------

    /** Registers the BTSocketFactory with LiteCore and returns a context token. */
    private static native long registerBTSocketFactory();

    /** Wrap an existing Java C4BTSocket in a C-native C4Socket (peripheral side). */
    private static native long fromNative(long token, String scheme, String host, long port, String path, int framing);
}
