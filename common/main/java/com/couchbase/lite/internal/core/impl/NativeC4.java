package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4;


public final class NativeC4 implements C4.NativeImpl {
    @Override
    public void nSetenv(@NonNull String name, @NonNull String value, int overwrite) { setenv(name, value, overwrite); }

    @Override
    @Nullable
    public String nGetBuildInfo() { return getBuildInfo(); }

    @Override
    @Nullable
    public String nGetVersion() { return getVersion(); }

    @Override
    public void nDebug(boolean debugging) { debug(debugging); }

    @GuardedBy("this")
    @Override
    public void nSetTempDir(@NonNull String tempDir) throws LiteCoreException { setTempDir(tempDir); }

    @GuardedBy("this")
    @Override
    public void nEnableExtension(@NonNull String name, @NonNull String path) throws LiteCoreException {
        enableExtension(name, path);
    }

    @Override
    @Nullable
    public String nGetMessage(int domain, int code, int internalInfo) { return getMessage(domain, code, internalInfo); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    // Thread safety verified as of 2025/5/15
    //-------------------------------------------------------------------------

    private static native void setenv(@NonNull String name, @NonNull String value, int overwrite);

    @Nullable
    public static native String getBuildInfo();

    @Nullable
    public static native String getVersion();

    private static native void debug(boolean debugging);

    @GuardedBy("this")
    private static native void setTempDir(@NonNull String tempDir) throws LiteCoreException;

    @GuardedBy("this")
    private static native void enableExtension(@NonNull String name, @NonNull String path) throws LiteCoreException;

    @Nullable
    private static native String getMessage(int domain, int code, int internalInfo);
}
