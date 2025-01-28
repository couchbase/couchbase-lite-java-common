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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.impl.NativeFLValue;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessivePublicCount"})
public class FLValue {
    public static final int UNDEFINED = -1; // Type of a nullptr FLValue (i.e. no such value)
    public static final int NULL = 0;
    public static final int BOOLEAN = 1;
    public static final int NUMBER = 2;
    public static final int STRING = 3;
    public static final int DATA = 4;
    public static final int ARRAY = 5;
    public static final int DICT = 6;

    public static final class Error {
        private Error() { }

        public static final int NO_ERROR = 0;
        public static final int MEMORY_ERROR = 1;   // Out of memory, or allocation failed
        public static final int OUT_OF_RANGE = 2;   // Array index or iterator out of range
        public static final int INVALID_DATA = 3;   // Bad input data (NaN, non-string key, etc.)
        public static final int ENCODE_ERROR = 4;   // Structural error encoding (missing value, too many ends, etc.)
        public static final int JSON_ERROR = 5;     // Error parsing JSON
        public static final int UNKNOWN_VALUE = 6;  // Unparsable data in a Value (corrupt or from a distant future?)
        public static final int INTERNAL_ERROR = 7; // Something that shouldn't happen
        public static final int NOT_FOUND = 8;
    }

    public interface NativeImpl {
        long nFromTrustedData(byte[] data);
        long nFromData(long ptr, long size);
        int nGetType(long value);
        boolean nIsInteger(long value);
        boolean nIsUnsigned(long value);
        boolean nIsDouble(long value);
        @Nullable
        String nToString(long handle);
        @Nullable
        String nToJSON(long handle);
        @Nullable
        String nToJSON5(long handle);
        @NonNull
        byte[] nAsByteArray(long value);
        boolean nAsBool(long value);
        long nAsUnsigned(long value);
        long nAsInt(long value);
        float nAsFloat(long value);
        double nAsDouble(long value);
        @NonNull
        String nAsString(long value);
        long nAsArray(long value);
        long nAsDict(long value);
        @Nullable
        String nJson5toJson(@Nullable String json5) throws LiteCoreException;
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeFLValue();

    //-------------------------------------------------------------------------
    // public static methods
    //-------------------------------------------------------------------------

    @NonNull
    public static FLValue getFLValue(long peer) { return new FLValue(NATIVE_IMPL, peer); }

    @NonNull
    public static FLValue fromData(@NonNull byte[] data) { return fromData(NATIVE_IMPL, data); }

    @Nullable
    public static FLValue fromData(@Nullable FLSliceResult slice) { return fromData(NATIVE_IMPL, slice); }

    /**
     * Converts valid JSON5 to JSON.
     *
     * @param json5 String
     * @return JSON String
     * @throws LiteCoreException on parse failure
     */
    @Nullable
    public static String getJSONForJSON5(@Nullable String json5) throws LiteCoreException {
        return getJSONForJSON5(NATIVE_IMPL, json5);
    }

    @VisibleForTesting
    @Nullable
    public static String getJSONForJSON5(@NonNull NativeImpl impl, @Nullable String json5) throws LiteCoreException {
        return impl.nJson5toJson(json5);
    }

    @NonNull
    private static FLValue fromData(@NonNull NativeImpl impl, @NonNull byte[] data) {
        return FLValue.getFLValue(impl.nFromTrustedData(data));
    }

    @Nullable
    private static FLValue fromData(@NonNull NativeImpl impl, @Nullable FLSliceResult slice) {
        if (slice == null) { return null; }
        final long value = impl.nFromData(slice.getBase(), slice.getSize());
        return value == 0 ? null : FLValue.getFLValue(value);
    }

    @Nullable
    public static <E extends Exception> FLValue createOrNull(@NonNull Fn.LongProviderThrows<E> fn) throws E {
        final long peer = fn.get();
        return (peer == 0) ? null : create(peer);
    }

    // Don't use this outside this class unless you really must (LiteCore passed you the ref or something)
    // Our peer is nobody else's business.
    @NonNull
    public static FLValue create(long peer) { return new FLValue(NATIVE_IMPL, peer); }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final NativeImpl impl;
    private final long peer; // pointer to FLValue

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    @VisibleForTesting
    public FLValue(@NonNull NativeImpl impl, long peer) {
        this.impl = Preconditions.assertNotNull(impl, "impl");
        this.peer = Preconditions.assertNotZero(peer, "peer");
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Returns the data type of an arbitrary Value.
     *
     * @return int (FLValueType)
     */
    public int getType() { return impl.nGetType(peer); }

    /**
     * Is this value a number?
     *
     * @return true if value is a number
     */
    public boolean isNumber() { return getType() == NUMBER; }

    /**
     * Is this value an integer?
     *
     * @return true if value is a number
     */
    public boolean isInteger() { return impl.nIsInteger(peer); }

    /**
     * Returns true if the value is non-nullptr and represents an _unsigned_ integer that can only
     * be represented natively as a `uint64_t`.
     *
     * @return boolean
     */
    public boolean isUnsigned() { return impl.nIsUnsigned(peer); }

    /**
     * Is this a 64-bit floating-point value?
     *
     * @return true if value is a double
     */
    public boolean isDouble() { return impl.nIsDouble(peer); }

    /**
     * Returns the string representation.
     *
     * @return string rep
     */
    @Nullable
    public String toStr() { return impl.nToString(peer); }

    /**
     * Returns the json representation.
     *
     * @return json rep
     */
    @Nullable
    public String toJSON() { return impl.nToJSON(peer); }

    /**
     * Returns the string representation.
     *
     * @return json5 rep
     */
    @Nullable
    public String toJSON5() { return impl.nToJSON5(peer); }

    /**
     * Returns the exact contents of a data value, or null for all other types.
     *
     * @return byte[]
     */
    @NonNull
    public byte[] asByteArray() { return impl.nAsByteArray(peer); }

    /**
     * Returns a value coerced to boolean.
     *
     * @return boolean
     */
    public boolean asBool() { return impl.nAsBool(peer); }

    /**
     * Returns a value coerced to an integer.
     * NOTE: litecore treats integer with 2^64. So this JNI method returns long value
     *
     * @return long
     */
    public long asInt() { return impl.nAsInt(peer); }

    /**
     * Returns a value coerced to an unsigned integer.
     *
     * @return long
     */
    public long asUnsigned() { return impl.nAsUnsigned(peer); }

    /**
     * Returns a value coerced to a 32-bit floating point number.
     *
     * @return float
     */
    public float asFloat() { return impl.nAsFloat(peer); }

    /**
     * Returns a value coerced to a 64-bit floating point number.
     *
     * @return double
     */
    public double asDouble() { return impl.nAsDouble(peer); }

    /**
     * Returns the exact contents of a string value, or null for all other types.
     * ??? If we are out of memory or the string cannot be decoded, we just drop it on the floor
     *
     * @return String
     */
    @NonNull
    public String asString() { return impl.nAsString(peer); }

    @NonNull
    public <T> List<T> asList(@NonNull Class<T> klass) { return asFLArray().asList(klass); }

    /**
     * Returns the contents as a dictionary.
     *
     * @return String
     */
    @NonNull
    public FLDict asFLDict() { return FLDict.create(impl.nAsDict(peer)); }

    /**
     * If a FLValue represents an array, returns it cast to FLDict, else nullptr.
     *
     * @return long (FLDict)
     */
    @NonNull
    public <K, V> Map<K, V> asMap(@NonNull Class<K> keyClass, @NonNull Class<V> valueClass) {
        return asFLDict().asMap(keyClass, valueClass);
    }

    /**
     * Return a Java object of the appropriate type.
     *
     * @return Object
     */
    @Nullable
    public Object toJava() {
        switch (impl.nGetType(peer)) {
            case BOOLEAN:
                return Boolean.valueOf(asBool());
            case NUMBER:
                if (isInteger()) { return (isUnsigned()) ? Long.valueOf(asUnsigned()) : Long.valueOf(asInt()); }
                if (isDouble()) { return Double.valueOf(asDouble()); }
                return Float.valueOf(asFloat());
            case STRING:
                return asString();
            case DATA:
                return asByteArray();
            case ARRAY:
                return asList(Object.class);
            case DICT:
                return asMap(String.class, Object.class);
            case NULL:
            default:
                return null;
        }
    }

    @Nullable
    <T> T withContent(@NonNull Fn.NonNullFunction<Long, T> fn) { return fn.apply(peer); }

    @NonNull
    FLArray asFLArray() { return FLArray.create(impl.nAsArray(peer)); }
}

