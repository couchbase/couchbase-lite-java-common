//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.fleece.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLValue;

@SuppressWarnings("PMD.TooManyMethods")
public final class NativeFLValue implements FLValue.NativeImpl {
    @Override
    public long nFromTrustedData(byte[] data) { return fromTrustedData(data); }

    @Override
    public long nFromData(long ptr, long size) { return fromData(ptr, size); }

    @Override
    public int nGetType(long value) { return getType(value); }

    @Override
    public boolean nIsInteger(long value) { return isInteger(value); }

    @Override
    public boolean nIsUnsigned(long value) { return isUnsigned(value); }

    @Override
    public boolean nIsDouble(long value) { return isDouble(value); }

    @Override
    @Nullable
    public String nToString(long peer) { return toString(peer); }

    @Override
    @Nullable
    public String nToJSON(long peer) { return toJSON(peer); }

    @Override
    @Nullable
    public String nToJSON5(long peer) { return toJSON5(peer); }

    @NonNull
    public byte[] nAsByteArray(long value) { return asData(value); }

    @Override
    public boolean nAsBool(long value) { return asBool(value); }

    @Override
    public long nAsUnsigned(long value) { return asUnsigned(value); }

    @Override
    public long nAsInt(long value) { return asInt(value); }

    @Override
    public float nAsFloat(long value) { return asFloat(value); }

    @Override
    public double nAsDouble(long value) { return asDouble(value); }

    @Override
    @NonNull
    public String nAsString(long value) { return asString(value); }

    @Override
    public long nAsArray(long value) { return asArray(value); }

    @Override
    public long nAsDict(long value) { return asDict(value); }

    @Override
    @Nullable
    public String nJson5toJson(@Nullable String json) throws LiteCoreException { return json5toJson(json); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long fromTrustedData(byte[] data);

    private static native long fromData(long ptr, long size);

    private static native int getType(long value);

    private static native boolean isInteger(long value);

    private static native boolean isUnsigned(long value);

    private static native boolean isDouble(long value);

    @Nullable
    private static native String toString(long peer);

    @Nullable
    private static native String toJSON(long peer);

    @Nullable
    private static native String toJSON5(long peer);

    @NonNull
    private static native byte[] asData(long value);

    private static native boolean asBool(long value);

    private static native long asUnsigned(long value);

    private static native long asInt(long value);

    private static native float asFloat(long value);

    private static native double asDouble(long value);

    @NonNull
    private static native String asString(long value);

    private static native long asArray(long value);

    private static native long asDict(long value);

    @Nullable
    private static native String json5toJson(@Nullable String json) throws LiteCoreException;
}
