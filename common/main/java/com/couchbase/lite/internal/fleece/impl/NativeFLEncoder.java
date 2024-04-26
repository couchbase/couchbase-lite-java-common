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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;


@SuppressWarnings("PMD.TooManyMethods")
public final class NativeFLEncoder implements FLEncoder.NativeImpl {
    @Override
    public long nCreateFleeceEncoder() { return newFleeceEncoder(); }

    @Override
    public long nCreateJSONEncoder() { return newJSONEncoder(); }

    @Override
    public boolean nWriteNull(long peer) { return writeNull(peer); }

    @Override
    public boolean nWriteBool(long peer, boolean value) { return writeBool(peer, value); }

    @Override
    public boolean nWriteInt(long peer, long value) { return writeInt(peer, value); }

    @Override
    public boolean nWriteFloat(long peer, float value) { return writeFloat(peer, value); }

    @Override
    public boolean nWriteDouble(long peer, double value) { return writeDouble(peer, value); }

    @Override
    public boolean nWriteString(long peer, @NonNull String value) { return writeString(peer, value); }

    @Override
    public boolean nWriteStringChars(long peer, @NonNull char[] value) { return writeStringChars(peer, value); }

    @Override
    public boolean nWriteData(long peer, @NonNull byte[] value) { return writeData(peer, value); }

    @Override
    public boolean nWriteValue(long peer, long value) { return writeValue(peer, value); }

    @Override
    public boolean nBeginArray(long peer, long reserve) { return beginArray(peer, reserve); }

    @Override
    public boolean nEndArray(long peer) { return endArray(peer); }

    @Override
    public boolean nBeginDict(long peer, long reserve) { return beginDict(peer, reserve); }

    @Override
    public boolean nWriteKey(long peer, @NonNull String slice) { return writeKey(peer, slice); }

    @Override
    public boolean nEndDict(long peer) { return endDict(peer); }

    @Override
    public void nReset(long peer) { reset(peer); }

    @NonNull
    @Override
    public byte[] nFinish(long peer) throws LiteCoreException { return finish(peer); }

    @NonNull
    @Override
    public FLSliceResult nFinish2(long peer) throws LiteCoreException { return finish2(peer); }

    @NonNull
    @Override
    public String nFinishJSON(long peer) throws LiteCoreException { return finishJSON(peer); }

    @Override
    public void nFree(long peer) { free(peer); }


    // JSON encoders

    private static native long newJSONEncoder();

    @NonNull
    private static native String finishJSON(long peer) throws LiteCoreException;


    // FLEncoders

    private static native long newFleeceEncoder();

    private static native void free(long peer);

    private static native void reset(long peer);

    private static native boolean writeNull(long peer);

    private static native boolean writeBool(long peer, boolean value);

    private static native boolean writeInt(long peer, long value); // 64bit

    private static native boolean writeFloat(long peer, float value);

    private static native boolean writeDouble(long peer, double value);

    private static native boolean writeString(long peer, @NonNull String value);

    private static native boolean writeStringChars(long peer, char[] value);

    private static native boolean writeData(long peer, @NonNull byte[] value);

    private static native boolean writeValue(long peer, long value /*FLValue*/);

    private static native boolean beginArray(long peer, long reserve);

    private static native boolean endArray(long peer);

    private static native boolean beginDict(long peer, long reserve);

    private static native boolean endDict(long peer);

    private static native boolean writeKey(long peer, @NonNull String slice);

    @NonNull
    private static native byte[] finish(long peer) throws LiteCoreException;

    @NonNull
    private static native FLSliceResult finish2(long peer) throws LiteCoreException;
}
