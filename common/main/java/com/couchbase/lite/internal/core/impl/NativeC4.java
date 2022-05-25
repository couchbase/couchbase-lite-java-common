package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4;


public class NativeC4 implements C4.NativeImpl{
    @Override
    public void nSetenv(@NonNull String name, @NonNull String value, int overwrite) { setenv(name, value, overwrite); }

    @Override
    @NonNull
    public String nGetenv(@NonNull String name) { return getenv(name); }

    @Override
    @Nullable
    public String nGetBuildInfo() { return getBuildInfo(); }

    @Override
    @Nullable
    public String nGetVersion() { return getVersion(); }

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

    public static native void setenv(@NonNull String name, @NonNull String value, int overwrite);

    @NonNull
    public static native String getenv(@NonNull String name);

    @Nullable
    public static native String getBuildInfo();

    @Nullable
    public static native String getVersion();
}
