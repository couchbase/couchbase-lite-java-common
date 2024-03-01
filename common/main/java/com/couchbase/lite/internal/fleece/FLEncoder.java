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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;
import com.couchbase.lite.internal.fleece.impl.NativeFLEncoder;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


/**
 * Represent the encoder object whose ref is passed as a parameter or returned returned
 * by the Core "init" call. The caller takes ownership of the "managed" version's peer
 * and must call the close() method to release it. The "unmanaged" version's peer belongs to Core:
 * it will be release by the native code.
 */
@SuppressWarnings("PMD.ExcessivePublicCount")
public abstract class FLEncoder extends C4NativePeer {
    public interface NativeImpl {
        long nCreateFleeceEncoder();
        long nCreateJSONEncoder();
        boolean nWriteNull(long peer);
        boolean nWriteBool(long peer, boolean value);
        boolean nWriteInt(long peer, long value); // 64bit
        boolean nWriteFloat(long peer, float value);
        boolean nWriteDouble(long peer, double value);
        boolean nWriteString(long peer, @NonNull String value);
        boolean nWriteStringChars(long peer, @NonNull char[] value);
        boolean nWriteData(long peer, @NonNull byte[] value);
        boolean nWriteValue(long peer, long value /*FLValue*/);
        boolean nBeginArray(long peer, long reserve);
        boolean nEndArray(long peer);
        boolean nBeginDict(long peer, long reserve);
        boolean nEndDict(long peer);
        boolean nWriteKey(long peer, @NonNull String slice);
        void nReset(long peer);
        @NonNull
        byte[] nFinish(long peer) throws LiteCoreException;
        @NonNull
        FLSliceResult nFinish2(long peer) throws LiteCoreException;
        @NonNull
        String nFinishJSON(long peer) throws LiteCoreException;
        void nFree(long peer);
    }

    // unmanaged: the native code will free it
    static final class UnmanagedFLEncoder extends FLEncoder {
        UnmanagedFLEncoder(@NonNull NativeImpl impl, long peer) { super(impl, peer); }

        @Override
        public void close() {
            releasePeer(
                null,
                peer -> {
                    synchronized (arguments) { arguments.clear(); }
                    impl.nReset(peer);
                });
        }
    }

    // managed: Java code is responsible for freeing it
    static class ManagedFLEncoder extends FLEncoder {
        ManagedFLEncoder(@NonNull NativeImpl impl, long peer) { super(impl, peer); }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        public void closePeer(@Nullable LogDomain domain) {
            releasePeer(
                domain,
                (peer) -> {
                    final NativeImpl nativeImpl = impl;
                    if (nativeImpl != null) { nativeImpl.nFree(peer); }
                });
        }
    }

    // special managed flencoder for JSON
    public static final class JSONEncoder extends ManagedFLEncoder {
        private JSONEncoder(@NonNull NativeImpl impl, long peer) { super(impl, peer); }

        @NonNull
        public String finishJSON() throws LiteCoreException { return withPeerOrThrow(impl::nFinishJSON); }

        @Override
        @NonNull
        public byte[] finish() {
            throw new UnsupportedOperationException("finish not supported for JSONEncoders");
        }

        @Override
        @NonNull
        public FLSliceResult finish2() {
            throw new UnsupportedOperationException("finish2 not supported for JSONEncoders");
        }
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeFLEncoder();

    @NonNull
    public static FLEncoder getUnmanagedEncoder(long peer) { return new UnmanagedFLEncoder(NATIVE_IMPL, peer); }

    @NonNull
    public static FLEncoder getManagedEncoder() {
        return new ManagedFLEncoder(NATIVE_IMPL, NATIVE_IMPL.nCreateFleeceEncoder());
    }

    @NonNull
    public static JSONEncoder getJSONEncoder() {
        return new JSONEncoder(NATIVE_IMPL, NATIVE_IMPL.nCreateJSONEncoder());
    }

    @Nullable
    public static byte[] encodeMap(@Nullable Map<String, Object> options) {
        if ((options == null) || options.isEmpty()) { return null; }

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(options);
            return enc.finish();
        }
        catch (LiteCoreException e) { Log.w(LogDomain.REPLICATOR, "Failed encoding map: " + options, e); }

        return null;
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    protected final NativeImpl impl;

    protected final Map<String, Object> arguments = new HashMap<>();

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private FLEncoder(@NonNull NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("FLEncoder{")
            .append(ClassUtils.objId(this)).append('/').append(super.toString())
            .append('[');
        boolean first = true;
        for (Map.Entry<?, ?> arg: arguments.entrySet()) {
            if (first) { first = false; }
            else { buf.append(','); }
            buf.append(arg.getKey()).append("=>").append(arg.getValue());
        }
        return buf.append("]}").toString();
    }

    // remove the Exception from the signature.
    @Override
    public abstract void close();

    @NonNull
    public FLEncoder setArg(@NonNull String key, @Nullable Object arg) {
        synchronized (arguments) { arguments.put(key, arg); }
        return this;
    }

    @Nullable
    public Object getArg(@NonNull String key) {
        synchronized (arguments) { return arguments.get(key); }
    }

    public boolean writeNull() { return impl.nWriteNull(getPeer()); }

    public boolean writeString(@NonNull String value) { return impl.nWriteString(getPeer(), value); }

    public boolean writeString(@NonNull char[] value) { return impl.nWriteStringChars(getPeer(), value); }

    public boolean writeData(@NonNull byte[] value) { return impl.nWriteData(getPeer(), value); }

    public boolean beginDict(long reserve) { return impl.nBeginDict(getPeer(), reserve); }

    public boolean endDict() { return impl.nEndDict(getPeer()); }

    public boolean beginArray(long reserve) { return impl.nBeginArray(getPeer(), reserve); }

    public boolean endArray() { return impl.nEndArray(getPeer()); }

    public boolean writeKey(String slice) { return impl.nWriteKey(getPeer(), slice); }

    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    public boolean writeValue(@Nullable Object value) {
        final long peer = getPeer();
        // null
        if (value == null) { return impl.nWriteNull(peer); }

        // boolean
        if (value instanceof Boolean) { return impl.nWriteBool(peer, (Boolean) value); }

        // Number
        if (value instanceof Number) {
            // Integer
            if (value instanceof Integer) { return impl.nWriteInt(peer, ((Integer) value).longValue()); }

            // Long
            if (value instanceof Long) { return impl.nWriteInt(peer, (Long) value); }

            // Short
            if (value instanceof Short) { return impl.nWriteInt(peer, ((Short) value).longValue()); }

            // Double
            if (value instanceof Double) { return impl.nWriteDouble(peer, (Double) value); }

            // Float
            return impl.nWriteFloat(peer, (Float) value);
        }

        // String
        if (value instanceof String) { return impl.nWriteString(peer, (String) value); }

        // String (represented as char[])
        if (value instanceof char[]) { return writeString((char[]) value); }

        // byte[]
        if (value instanceof byte[]) { return impl.nWriteData(peer, (byte[]) value); }

        // List
        if (value instanceof List) { return write((List<?>) value); }

        // Map
        if (value instanceof Map) { return write((Map<String, Object>) value); }

        // FLValue
        if (value instanceof FLValue) {
            final Boolean val = ((FLValue) value).withContent(hdl -> impl.nWriteValue(peer, hdl));
            return (val != null) && val;
        }

        // FLDict
        if (value instanceof FLDict) {
            final Boolean val = ((FLDict) value).withContent(hdl -> impl.nWriteValue(peer, hdl));
            return (val != null) && val;
        }

        // FLArray
        if (value instanceof FLArray) {
            final Boolean val = ((FLArray) value).withContent(hdl -> impl.nWriteValue(peer, hdl));
            return (val != null) && val;
        }

        // FLEncodable
        if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(this);
            return true;
        }

        return false;
    }

    public boolean write(@Nullable Map<String, Object> map) {
        boolean ok;
        if (map == null) { ok = beginDict(0); }
        else {
            ok = beginDict(map.size());
            for (Map.Entry<String, Object> entry: map.entrySet()) {
                ok = ok && writeKey(entry.getKey());
                ok = ok && writeValue(entry.getValue());
            }
        }
        return ok && endDict();
    }

    public boolean write(@Nullable List<?> list) {
        boolean ok;
        if (list == null) { ok = beginArray(0); }
        else {
            ok = beginArray(list.size());
            for (Object item: list) { ok = ok && writeValue(item); }
        }
        return ok && endArray();
    }

    public void reset() { impl.nReset(getPeer()); }

    @NonNull
    public byte[] finish() throws LiteCoreException { return impl.nFinish(getPeer()); }

    @NonNull
    public FLSliceResult finish2() throws LiteCoreException { return impl.nFinish2(getPeer()); }
}
