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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.impl.NativeC4Base;


public final class C4Base {
    private C4Base() { }

    public interface NativeImpl {
        void nDebug(boolean debugging);
        void nSetTempDir(@NonNull String tempDir) throws LiteCoreException;
        @Nullable
        String nGetMessage(int domain, int code, int internalInfo);
    }

    @NonNull
    @VisibleForTesting
    static volatile C4Base.NativeImpl nativeImpl = new NativeC4Base();

    public static void debug(boolean debugging) { nativeImpl.nDebug(debugging); }

    public static void setTempDir(@NonNull String tempDir) throws LiteCoreException { nativeImpl.nSetTempDir(tempDir); }

    @Nullable
    public static String getMessage(int domain, int code, int internalInfo) {
        return nativeImpl.nGetMessage(domain, code, internalInfo);
    }
}
