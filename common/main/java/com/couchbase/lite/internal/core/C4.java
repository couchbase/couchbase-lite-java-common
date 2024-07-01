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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.impl.NativeC4;


public final class C4 {
    private C4() { }

    public interface NativeImpl {
        void nSetenv(@NonNull String name, @NonNull String value, int overwrite);
        @Nullable
        String nGetBuildInfo();
        @Nullable
        String nGetVersion();
        void nDebug(boolean debugging);
        void nSetTempDir(@NonNull String tempDir) throws LiteCoreException;
        void nEnableExtension(@NonNull String name, @NonNull String path) throws LiteCoreException;
        @Nullable
        String nGetMessage(int domain, int code, int internalInfo);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4();

    public static void setEnv(@NonNull String name, @NonNull String value, int overwrite) {
        NATIVE_IMPL.nSetenv(name, value, overwrite);
    }

    @Nullable
    public static String getBuildInfo() { return NATIVE_IMPL.nGetBuildInfo(); }

    @Nullable
    public static String getVersion() { return NATIVE_IMPL.nGetVersion(); }

    public static void debug(boolean debugging) { NATIVE_IMPL.nDebug(debugging); }

    public static void setTempDir(@NonNull String tempDir) throws LiteCoreException {
        NATIVE_IMPL.nSetTempDir(tempDir);
    }

    public static void enableExtension(@NonNull String name, @NonNull String path) throws LiteCoreException {
        NATIVE_IMPL.nEnableExtension(name, path);
    }

    @Nullable
    public static String getMessage(int domain, int code, int internalInfo) {
        return NATIVE_IMPL.nGetMessage(domain, code, internalInfo);
    }
}
