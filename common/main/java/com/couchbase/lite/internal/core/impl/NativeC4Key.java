package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Key;


public class NativeC4Key implements C4Key.NativeImpl{

    @Override
    @NonNull
    public byte[] nPbkdf2(@NonNull String password) { return pbkdf2(password); }

    @Override
    @NonNull
    public byte[] nDeriveKeyFromPassword(@NonNull String password) { return deriveKeyFromPassword(password); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    @NonNull
    private static native byte[] pbkdf2(@NonNull String password);

    @NonNull
    private static native byte[] deriveKeyFromPassword(@NonNull String password);
}
