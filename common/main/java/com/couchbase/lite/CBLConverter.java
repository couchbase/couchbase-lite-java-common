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
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.ClassUtils;


final class CBLConverter {
    private CBLConverter() { }

    @Nullable
    static Number asNumber(@Nullable Object value) {
        // special handling for Boolean
        if (value instanceof Boolean) { return ((Boolean) value) ? 1 : 0; }

        return ClassUtils.castOrNull(Number.class, value);
    }

    static boolean asBoolean(@Nullable Object value) {
        if (value == null) { return false; }

        if (value instanceof Boolean) { return ((Boolean) value).booleanValue(); }

        if (value instanceof Number) { return ((Number) value).intValue() != 0; }

        return true;
    }

    static int asInteger(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return (int) value.asInt(); }

        final Number num = asNumber(val.toJava(container));
        return num != null ? num.intValue() : 0;
    }

    static long asLong(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return value.asInt(); }

        final Number num = asNumber(val.toJava(container));
        return num != null ? num.longValue() : 0L;
    }

    static float asFloat(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return value.asFloat(); }

        final Number num = asNumber(val.toJava(container));
        return num != null ? num.floatValue() : 0L;
    }

    static double asDouble(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return value.asDouble(); }

        final Number num = asNumber(val.toJava(container));
        return num != null ? num.doubleValue() : 0L;
    }
}
