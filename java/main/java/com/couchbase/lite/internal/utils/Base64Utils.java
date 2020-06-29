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

import java.util.Base64;


public final class Base64Utils {
    private Base64Utils() { }

    public interface Base64Encoder {
        @Nullable
        String encodeToString(@Nullable byte[] src);
    }

    public interface Base64Decoder {
        @Nullable
        byte[] decodeString(@Nullable String src);
    }

    @NonNull
    public static Base64Encoder getEncoder() {
        return new Base64Encoder() {
            private final Base64.Encoder encoder = Base64.getEncoder();

            @Override
            public String encodeToString(byte[] src) { return encoder.encodeToString(src); }
        };
    }

    @NonNull
    public static Base64Decoder getDecoder() {
        return new Base64Decoder() {
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
