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

import java.util.Random;


public final class MathUtils {
    private MathUtils() { }

    @NonNull
    public static final ThreadLocal<Random> RANDOM = new ThreadLocal<Random>() {
        @NonNull
        protected synchronized Random initialValue() { return new Random(); }
    };

    public static int asUnsignedInt(long x) { return (x > Integer.MAX_VALUE) ? -1 : (int) x; }

    public static int asSignedInt(long x) {
        return ((x > Integer.MAX_VALUE) || (x < Integer.MIN_VALUE)) ? 0 : (int) x;
    }
}
