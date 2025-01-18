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

package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.AbstractJFleeceCollection;
import com.couchbase.lite.Array;
import com.couchbase.lite.Blob;
import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.Dictionary;
import com.couchbase.lite.MutableArray;
import com.couchbase.lite.MutableDictionary;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.MathUtils;


/**
 * Behavior common to all JFleece-like collections.
 * This includes Parameters and IndexUpdater which do not implement FleeceEncodable.
 */
public abstract class BaseJFleeceCollection {
    private static final String PRIMITIVE_TYPES = "String, Number, Boolean and null";

    private static final String JFLEECE_TYPES
        = "MutableDictionary, Dictionary, MutableArray, Array, Blob, Date, " + PRIMITIVE_TYPES;

    private static final String SUPPORTED_TYPES
        = "Map<String, JFLEECE_TYPE>, List<JFLEECE_TYPE>, " + JFLEECE_TYPES;

    protected boolean asBoolean(@Nullable Object value) {
        if (value == null) { return false; }
        if (value instanceof Boolean) { return ((Boolean) value).booleanValue(); }
        if (value instanceof Number) { return ((Number) value).intValue() != 0; }
        return true;
    }

    @Nullable
    protected Number asNumber(@Nullable Object value) {
        // special handling for Boolean
        if (value instanceof Boolean) { return ((Boolean) value) ? 1 : 0; }
        return !(value instanceof Number) ? null : (Number) value;
    }

    @Nullable
    protected String asString(@Nullable Object value) { return !(value instanceof String) ? null : (String) value; }

    @Nullable
    protected Blob asBlob(@Nullable Object value) { return !(value instanceof Blob) ? null : (Blob) value; }

    @Nullable
    protected Array asArray(@Nullable Object value) { return !(value instanceof Array) ? null : (Array) value; }

    @Nullable
    protected Dictionary asDictionary(@Nullable Object value) {
        return !(value instanceof Dictionary) ? null : (Dictionary) value;
    }

    @Nullable
    protected <T> T asValue(@NonNull Class<T> klass, @Nullable Object value) {
        return !(klass.isInstance(value)) ? null : klass.cast(value);
    }

    protected boolean toBoolean(@Nullable FLValue val) { return (val != null) && val.asBool(); }

    protected int toInteger(@Nullable FLValue val) { return (val == null) ? 0 : MathUtils.asSignedInt(val.asInt()); }

    protected int toInteger(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return MathUtils.asSignedInt(value.asInt()); }

        final Number num = asNumber(val.toJFleece(container));
        return num == null ? 0 : num.intValue();
    }

    protected long toLong(@Nullable FLValue val) { return (val == null) ? 0L : val.asInt(); }

    protected long toLong(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return value.asInt(); }

        final Number num = asNumber(val.toJFleece(container));
        return num == null ? 0L : num.longValue();
    }

    protected float toFloat(@Nullable FLValue val) { return (val == null) ? 0.0F : val.asFloat(); }

    protected float toFloat(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return value.asFloat(); }

        final Number num = asNumber(val.toJFleece(container));
        return num == null ? 0L : num.floatValue();
    }

    protected double toDouble(@Nullable FLValue val) { return (val == null) ? 0.0 : val.asDouble(); }

    protected double toDouble(@NonNull MValue val, @Nullable MCollection container) {
        final FLValue value = val.getFLValue();
        if (value != null) { return value.asDouble(); }

        final Number num = asNumber(val.toJFleece(container));
        return num == null ? 0L : num.doubleValue();
    }

    @Nullable
    protected Object toJFleeceCollection(@Nullable AbstractJFleeceCollection<?> value) {
        if (value instanceof Array) { return ((Array) value).toList(); }
        if (value instanceof Dictionary) { return ((Dictionary) value).toMap(); }
        throw new CouchbaseLiteError("Unexpected collection type: " + value);
    }

    // This method converts a Java object of a SUPPORTED TYPE to an object of the corresponding JFLEECE_TYPE.
    // If actual parameter is already JFleece, it just gets returned as is.  If it is an immutable JFleece
    // container, it is converted to the corresponding mutable JFleece container type. Note that if the
    // parameter is already an immutable JFleece object, this method does not return a copy!  It returns
    // the parameter reference.  Mutations on the returned object are mutations of the parameter object.
    // If the actual parameter is not a SUPPORTED_TYPE it cannot be converted and that is an error.
    @Nullable
    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    protected Object toJFleece(@Nullable Object value) {
        if ((value == null)
            || (value instanceof Boolean)
            || (value instanceof Number)
            || (value instanceof String)
            || (value instanceof Blob)
            || (value instanceof MutableArray)
            || (value instanceof MutableDictionary)) {
            return value;
        }
        if (value instanceof Date) { return JSONUtils.toJSONString((Date) value); }
        if (value instanceof List) { return new MutableArray((List<Object>) value); }
        if (value instanceof Map) { return new MutableDictionary((Map<String, Object>) value); }
        if (value instanceof AbstractJFleeceCollection<?>) {
            return ((AbstractJFleeceCollection<?>) value).toMutable();
        }

        throw new IllegalArgumentException(
            Log.formatStandardMessage(
                "InvalidCouchbaseObjType",
                value.getClass().getSimpleName(),
                SUPPORTED_TYPES));
    }
}
