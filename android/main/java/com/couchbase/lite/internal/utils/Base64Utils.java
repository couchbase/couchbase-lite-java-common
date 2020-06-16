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

import android.os.Build;


public final class Base64Utils {
    private Base64Utils() { }

    interface Base64Encoder {
        String encodeToString(byte[] src);
    }

    public static Base64Encoder getEncoder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return src -> android.util.Base64.encodeToString(src, android.util.Base64.DEFAULT);
        }
        else {
            return new Base64Encoder() {
                java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();

                @Override
                public String encodeToString(byte[] src) { return encoder.encodeToString(src); }
            };
        }
    }
}
