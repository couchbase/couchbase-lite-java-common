package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.internal.core.C4Log;


public final class NativeC4Log implements C4Log.NativeImpl {

    @Override
    public void nLog(String domain, int level, String message) { log(domain, level, message); }

    @Override
    public void nSetLevel(String domain, int level) { setLevel(domain, level); }

    @Override
    public void nSetCallbackLevel(int level) { setCallbackLevel(level); }

    @Override
    public void nSetBinaryFileLevel(int level) { setBinaryFileLevel(level); }

    @Override
    public void nWriteToBinaryFile(
        String path, int level, int maxRotateCount, long maxSize, boolean usePlaintext, String header) {
        writeToBinaryFile(path, level, maxRotateCount, maxSize, usePlaintext, header);
    }
    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void log(String domain, int level, String message);

    private static native void setLevel(String domain, int level);

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
