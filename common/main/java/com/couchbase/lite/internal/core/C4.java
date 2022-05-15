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

import com.couchbase.lite.internal.core.impl.NativeC4;


public final class C4 {
    private C4() { }

    public interface NativeImpl {
        void nSetenv(@NonNull String name, @NonNull String value, int overwrite);
        @NonNull
        String nGetenv(@NonNull String name);
        @Nullable
        String nGetBuildInfo();
        @Nullable
        String nGetVersion();
    }

    @NonNull
    @VisibleForTesting
    static volatile NativeImpl nativeImpl = new NativeC4();

    public static void setenv(@NonNull String name, @NonNull String value, int overwrite) {
        nativeImpl.nSetenv(name, value, overwrite);
    }

    @NonNull
    public String getEnv(@NonNull String name){ return nativeImpl.nGetenv(name); }

    @Nullable
    public static String getBuildInfo(){ return nativeImpl.nGetBuildInfo(); }

    @Nullable
    public static String getVersion() { return nativeImpl.nGetVersion(); }
}
