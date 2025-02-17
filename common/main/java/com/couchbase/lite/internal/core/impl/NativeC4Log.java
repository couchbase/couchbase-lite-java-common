package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Log;


public final class NativeC4Log implements C4Log.NativeImpl {

    @Override
    public void nLog(@NonNull String domain, int level, @NonNull String message) { log(domain, level, message); }

    @Override
    public void nSetLevel(@NonNull String domain, int level) { setLevel(domain, level); }

    @Override
    public void nSetCallbackLevel(int level) { setCallbackLevel(level); }

    @Override
    public void nSetBinaryFileLevel(int level) { setBinaryFileLevel(level); }

    @Override
    public void nWriteToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header) {
        writeToBinaryFile(path, level, maxRotateCount, maxSize, usePlaintext, header);
    }

    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    //-------------------------------------------------------------------------

    private static native void log(@NonNull String domain, int level, @NonNull String message);

    private static native void setLevel(@NonNull String domain, int level);

    private static native void setCallbackLevel(int level);

    private static native void setBinaryFileLevel(int level);

    private static native void writeToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header);
}
