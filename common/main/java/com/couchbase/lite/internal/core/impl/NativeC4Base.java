package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Base;


public class NativeC4Base implements C4Base.NativeImpl {
    @Override
    public void nDebug(boolean debugging) { debug(debugging); }

    @Override
    public void nSetTempDir(@NonNull String tempDir) throws LiteCoreException { setTempDir(tempDir); }

    @Override
    @Nullable
    public String nGetMessage(int domain, int code, int internalInfo) { return getMessage(domain, code, internalInfo); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void debug(boolean debugging);

    private static native void setTempDir(@NonNull String tempDir) throws LiteCoreException;

    @Nullable
    private static native String getMessage(int domain, int code, int internalInfo);
}
