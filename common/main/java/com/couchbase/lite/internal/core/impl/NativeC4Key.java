package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.core.C4Key;


public final class NativeC4Key implements C4Key.NativeImpl {
    @Override
    @Nullable
    public byte[] nPbkdf2(@NonNull String password) { return pbkdf2(password); }

    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    // Thread safety verified as of 2025/5/15
    //-------------------------------------------------------------------------

    @Nullable
    private static native byte[] pbkdf2(@NonNull String password);
}
