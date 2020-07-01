//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.InputStream;


public final class PlatformUtils {
    private PlatformUtils() {}

    public interface Base64Encoder {
        @Nullable
        String encodeToString(@Nullable byte[] src);
    }

    public interface Base64Decoder {
        @Nullable
        byte[] decodeString(@Nullable String src);
    }

    interface Delegate {
        @Nullable
        InputStream getAsset(@Nullable String asset);

        @NonNull
        Base64Encoder getEncoder();

        @NonNull
        Base64Decoder getDecoder();
    }

    private static final Delegate DELEGATE = new PlatformUtilsDelegate();

    @Nullable
    public static InputStream getAsset(@Nullable String asset) { return DELEGATE.getAsset(asset); }

    @NonNull
    public static Base64Encoder getEncoder() { return DELEGATE.getEncoder(); }

    @NonNull
    public static Base64Decoder getDecoder() { return DELEGATE.getDecoder(); }
}
