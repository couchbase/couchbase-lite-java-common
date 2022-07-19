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

import com.couchbase.lite.internal.fleece.FLConstants;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JSONUtils;


final class Fleece {
    private Fleece() {}

    private static final String SUPPORTED_TYPES
        = "MutableDictionary, Dictionary, MutableArray, Array, Map, List, Date, String, Number, Boolean, null";

    // Assume that array and dict values are always different to avoid expensive comparisons.
    static boolean willMutate(Object newValue, @NonNull MValue oldValue, MCollection container) {
        final FLValue val = oldValue.getValue();

        final int oldType = (val != null) ? val.getType() : FLConstants.ValueType.UNDEFINED;
        return ((oldType == FLConstants.ValueType.UNDEFINED)
            || (oldType == FLConstants.ValueType.DICT)
            || (newValue instanceof Dictionary)
            || (oldType == FLConstants.ValueType.ARRAY)
            || (newValue instanceof Array)
            || !Objects.equals(newValue, oldValue.asNative(container)));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static Object toCBLObject(@Nullable Object value) {
        if ((value == null)
            || (value instanceof Boolean)
            || (value instanceof Number)
            || (value instanceof String)
            || (value instanceof Blob)
            || (value instanceof MutableArray)
            || (value instanceof MutableDictionary)) {
            return value;
        }
        else if (value instanceof Map) { return new MutableDictionary((Map<String, Object>) value); }
        else if (value instanceof Dictionary) { return ((Dictionary) value).toMutable(); }
        else if (value instanceof List) { return new MutableArray((List<Object>) value); }
        else if (value instanceof Array) { return ((Array) value).toMutable(); }
        else if (value instanceof Date) { return JSONUtils.toJSONString((Date) value); }

        throw new IllegalArgumentException(
            Log.formatStandardMessage(
                "InvalidCouchbaseObjType",
                value.getClass().getSimpleName(),
                SUPPORTED_TYPES));
    }

    @Nullable
    static Object toObject(@Nullable Object value) {
        if (value == null) { return null; }
        else if (value instanceof Dictionary) { return ((Dictionary) value).toMap(); }
        else if (value instanceof Array) { return ((Array) value).toList(); }
        else { return value; }
    }
}
