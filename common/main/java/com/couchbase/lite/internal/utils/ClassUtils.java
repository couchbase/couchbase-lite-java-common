//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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


public final class ClassUtils {
    private ClassUtils() { }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T castOrNull(@NonNull Class<T> clazz, @Nullable Object obj) {
        return (!clazz.isInstance(obj)) ? null : (T) obj;
    }

    @NonNull
    public static String objId(@NonNull Object obj) {
        return "@0x" + Integer.toHexString(System.identityHashCode(obj));
    }
}
