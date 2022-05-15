package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public static native void setenv(@NonNull String name, @NonNull String value, int overwrite);

    @NonNull
    public static native String getenv(@NonNull String name);

    @Nullable
    public static native String getBuildInfo();

    @Nullable
    public static native String getVersion();
}
