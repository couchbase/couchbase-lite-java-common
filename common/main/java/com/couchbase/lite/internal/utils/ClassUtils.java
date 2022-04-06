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

    public static boolean isEqual(Object o1, Object o2) {
        return (o1 == null) ? o2 == null : o1.equals(o2);
    }

    public static int hash(Object... objs) {
        if (objs == null) { return 0; }

        int result = 1;
        for (Object o : objs) { result = 31 * result + (o == null ? 0 : o.hashCode()); }

        return result;
    }
}
