//
// Copyright (c) 2020 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;

import com.couchbase.lite.CBLError;
import com.couchbase.lite.CouchbaseLiteException;


public final class C4Key {
    private C4Key() { }

    @NonNull
    public static byte[] getPbkdf2Key(@NonNull String password) throws CouchbaseLiteException {
        final byte[] key = C4Key.pbkdf2(password);
        if (key != null) { return key; }

        throw new CouchbaseLiteException("Could not generate key", CBLError.Domain.CBLITE, CBLError.Code.CRYPTO);
    }

    @NonNull
    public static byte[] getCoreKey(@NonNull String password) throws CouchbaseLiteException {
        final byte[] key = C4Key.deriveKeyFromPassword(password);
        if (key != null) { return key; }

        throw new CouchbaseLiteException("Could not generate key", CBLError.Domain.CBLITE, CBLError.Code.CRYPTO);
    }

    @NonNull
    private static native byte[] pbkdf2(@NonNull String password);

    @NonNull
    private static native byte[] deriveKeyFromPassword(@NonNull String password);
}
