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
package com.couchbase.lite.internal.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.util.Base64;


public final class PlatformUtilsDelegate implements PlatformUtils.Delegate {
    @Nullable
    public InputStream getAsset(@Nullable String asset) {
        if (asset == null) { return null; }
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(asset);
    }

    @NonNull
    public PlatformUtils.Base64Encoder getEncoder() {
        return new PlatformUtils.Base64Encoder() {
            private final Base64.Encoder encoder = Base64.getEncoder();

            @Nullable
            @Override
            public String encodeToString(@Nullable byte[] src) { return encoder.encodeToString(src); }
        };
    }

    @NonNull
    public PlatformUtils.Base64Decoder getDecoder() {
        return new PlatformUtils.Base64Decoder() {
            private final Base64.Decoder decoder = Base64.getDecoder();

            @Nullable
            @Override
            public byte[] decodeString(@Nullable String src) {
                try { return (src == null) ? null : decoder.decode(src); }
                catch (IllegalArgumentException e) { return null; }
            }
        };
    }
}
