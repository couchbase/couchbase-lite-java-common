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

/**
 * The Fleece implementations are different across the various platforms. Here's what
 * I can figure out (though I really have only rumors to go by).  I think that Jens did two
 * implementations, the second of which was an attempt to make life easier for the platforms.
 * Word has it that it did not succeed in doing that.  iOS still uses the first
 * implementation. Java tried to use the first implementation but, because of a problem with
 * running out of LocalRefs, the original developer for this platform (Java/Android) chose,
 * more or less, to port that first implementation into Java. I think that .NET did something
 * similar. As I understand it both Jim and Sandy tried to update .NET to use Jens' second
 * implementation somewhere in the 2.7 time-frame. They had, at most, partial success.
 * <p>
 * In 9/2020 (CBL-246), I tried to convert this code to use LiteCore's MutableFleece package
 * (that's Jens' second implementations). Both Jim and Jens warned me, without specifics,
 * that doing so might be more trouble than it was worth. Although the LiteCore
 * implementation of Mutable Fleece is relatively clear, this Java code is just plain
 * bizarre. It works, though. I don't think I have ever seen a problem that could be traced
 * to it. Instead of using the new LiteCore implementation, I've just cleaned this code up
 * a bit.  Other than that, I'm leaving it alone and I suggest you do the same, unless something
 * changes to make the benefit side of the C/B fraction more interesting.
 * <p>
 * The regrettable upside-down dependency on MValueConverter provides access to package
 * visible symbols in com.couchbase.lite.
 * <p>
 * It worries me that this isn't thread safe... but, as I say, I've never seen it be a problem.
 * <p>
 * 3/2024 (CBL-5486): I've seen a problem!
 * If the parent, the object holding the Fleece reference, is closed, the Fleece object backing
 * all of the contained objects, is freed.
 */

@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessivePublicCount"})
public class FLValue extends FLSlice<FLValue.NativeImpl> {
    public interface NativeImpl {
        long nFromTrustedData(byte[] data);
        long nFromSlice(long ptr, long size);
        int nGetType(long value);
        boolean nIsInteger(long value);
        boolean nIsUnsigned(long value);
        boolean nIsDouble(long value);
        @Nullable
        String nToString(long value);
        @Nullable
        String nToJSON(long value);
        @Nullable
        String nToJSON5(long value);
        @NonNull
        byte[] nAsData(long value);
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
    // Factory methods
    //-------------------------------------------------------------------------

    @NonNull
    public static FLValue fromData(@NonNull byte[] data) { return create(NATIVE_IMPL.nFromTrustedData(data)); }

    @Nullable
    public static FLValue fromSliceResult(@Nullable FLSliceResult slice) {
        return (slice == null) ? null : createOrNull(() -> NATIVE_IMPL.nFromSlice(slice.getBase(), slice.getSize()));
    }

    @NonNull
    public static <E extends Exception> FLValue create(@NonNull Fn.LongProviderThrows<E> fn) throws E {
        return create(fn.get());
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

    @VisibleForTesting
    @Nullable
    public static String getJSONForJSON5(@Nullable String json5) throws LiteCoreException {
        return NATIVE_IMPL.nJson5toJson(json5);
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    @VisibleForTesting
    public FLValue(@NonNull NativeImpl impl, long peer) { super(impl, peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Returns the data type of an arbitrary Value.
     *
     * @return int (FLValueType)
     */
    public int getType() { return impl.nGetType(peer); }

    @Override
    long count() { return 1; }

    /**
     * Is this value a number?
     *
     * @return true if value is a number
     */
    public boolean isNumber() { return getType() == FLSlice.ValueType.NUMBER; }

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
    public byte[] asData() { return impl.nAsData(peer); }

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
     * ??? If we are out of memory or the string cannot be decoded, we just return null
     *
     * @return String
     */
    @NonNull
    public String asString() { return impl.nAsString(peer); }

    @NonNull
    public List<Object> asArray() { return asFLArray().asArray(); }

    @NonNull
    public <T> List<T> asTypedArray(@NonNull Class<T> klass) { return asFLArray().asTypedArray(klass); }

    /**
     * Returns the contents as a dictionary.
     *
     * @return String
     */
    @NonNull
    public FLDict asFLDict() { return FLDict.create(impl.nAsDict(peer)); }

    /**
     * If a FLValue represents a map, returns it cast to FLDict, else nullptr.
     *
     * @return long (FLDict)
     */
    @NonNull
    public Map<String, Object> asDict() { return asFLDict().asDict(); }

    /**
     * Return an object of the appropriate type.
     *
     * @return Object
     */
    @Nullable
    public Object asJava() {
        switch (impl.nGetType(peer)) {
            case FLSlice.ValueType.BOOLEAN:
                return Boolean.valueOf(asBool());
            case FLSlice.ValueType.NUMBER:
                if (isInteger()) { return (isUnsigned()) ? Long.valueOf(asUnsigned()) : Long.valueOf(asInt()); }
                if (isDouble()) { return Double.valueOf(asDouble()); }
                return Float.valueOf(asFloat());
            case FLSlice.ValueType.STRING:
                return asString();
            case FLSlice.ValueType.DATA:
                return asData();
            case FLSlice.ValueType.ARRAY:
                return asArray();
            case FLSlice.ValueType.DICT:
                return asDict();
            case FLSlice.ValueType.NULL:
            default:
                return null;
        }
    }

    @NonNull
    FLArray asFLArray() { return FLArray.create(() -> impl.nAsArray(peer)); }
}

