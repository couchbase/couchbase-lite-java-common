package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.core.C4Key;


public class NativeC4Key implements C4Key.NativeImpl {

    @Override
    @Nullable
    public byte[] nPbkdf2(@NonNull String password) { return pbkdf2(password); }

    @Override
    @Nullable
    public byte[] nDeriveKeyFromPassword(@NonNull String password) { return deriveKeyFromPassword(password); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    @Nullable
    private static native byte[] pbkdf2(@NonNull String password);

    @Nullable
    private static native byte[] deriveKeyFromPassword(@NonNull String password);
}
