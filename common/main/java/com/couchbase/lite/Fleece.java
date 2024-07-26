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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.couchbase.lite.internal.fleece.FLSlice;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.JSONUtils;


class Fleece {
    private static final String SUPPORTED_TYPES
        = "MutableDictionary, Dictionary, Map, MutableArray, Array, List, Blob, Date, String, Number, Boolean and null";

    // Assume that array and dict values are always different to avoid expensive comparisons.
    protected boolean willMutate(Object newValue, @NonNull MValue oldValue, MCollection container) {
        final FLValue val = oldValue.getFLValue();
        final int oldType = (val == null) ? FLSlice.ValueType.UNDEFINED : val.getType();
        return (oldType == FLSlice.ValueType.UNDEFINED)
            || (oldType == FLSlice.ValueType.DICT)
            || (newValue instanceof Dictionary)
            || (oldType == FLSlice.ValueType.ARRAY)
            || (newValue instanceof Array)
            || !Objects.equals(newValue, oldValue.toJava(container));
    }

    @Nullable
    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    protected Object toCBLObject(@Nullable Object value) {
        if ((value == null)
            || (value instanceof Boolean)
            || (value instanceof Number)
            || (value instanceof String)
            || (value instanceof Blob)
            || (value instanceof MutableArray)
            || (value instanceof MutableDictionary)) {
            return value;
        }
        if (value instanceof Map) { return new MutableDictionary((Map<String, Object>) value); }
        if (value instanceof Dictionary) { return ((Dictionary) value).toMutable(); }
        if (value instanceof List) { return new MutableArray((List<Object>) value); }
        if (value instanceof Array) { return ((Array) value).toMutable(); }
        if (value instanceof Date) { return JSONUtils.toJSONString((Date) value); }

        throw new IllegalArgumentException(
            Log.formatStandardMessage("InvalidCouchbaseObjType", value.getClass().getSimpleName(), SUPPORTED_TYPES));
    }

    @Nullable
    protected Object toObject(@Nullable Object value) {
        if (value == null) { return null; }
        if (value instanceof Dictionary) { return ((Dictionary) value).toMap(); }
        if (value instanceof Array) { return ((Array) value).toList(); }
        return value;
    }
}
