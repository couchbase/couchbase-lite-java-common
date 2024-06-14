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
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


/**
 * Represent the encoder object whose ref is passed as a parameter or returned returned
 * by the Core "init" call. The caller takes ownership of the "managed" version's peer
 * and must call the close() method to release it. The "unmanaged" version's peer belongs to Core:
 * it will be release by the native code.
 */
@SuppressWarnings("PMD.TooManyMethods")
public abstract class FLEncoder extends C4NativePeer {
    @Nullable
    public static byte[] encodeMap(@Nullable Map<String, Object> options) {
        if ((options == null) || options.isEmpty()) { return null; }

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(options);
            return enc.finish();
        }
        catch (LiteCoreException e) { Log.w(LogDomain.REPLICATOR, "Failed encoding replicator options", e); }

        return null;
    }


    // unmanaged: the native code will free it
    static final class UnmanagedFLEncoder extends FLEncoder {
        UnmanagedFLEncoder(long peer) { super(peer); }

        @Override
        public void close() {
            releasePeer(
                null,
                peer -> {
                    synchronized (arguments) { arguments.clear(); }
                    reset(peer);
                });
        }
    }

    // managed: Java code is responsible for freeing it
    static class ManagedFLEncoder extends FLEncoder {
        ManagedFLEncoder(long peer) { super(peer); }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, FLEncoder::free); }
    }

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static FLEncoder getUnmanagedEncoder(long peer) { return new UnmanagedFLEncoder(peer); }

    @NonNull
    public static FLEncoder getManagedEncoder() { return new ManagedFLEncoder(newFleeceEncoder()); }


    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    protected final Map<String, Object> arguments = new HashMap<>();

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private FLEncoder(long peer) { super(peer); }

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

    public boolean writeNull() { return writeNull(getPeer()); }

    public boolean writeString(String value) { return writeString(getPeer(), value); }

    public boolean writeData(byte[] value) { return writeData(getPeer(), value); }

    public boolean beginDict(long reserve) { return beginDict(getPeer(), reserve); }

    public boolean endDict() { return endDict(getPeer()); }

    public boolean beginArray(long reserve) { return beginArray(getPeer(), reserve); }

    public boolean endArray() { return endArray(getPeer()); }

    public boolean writeKey(String slice) { return writeKey(getPeer(), slice); }

    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    public boolean writeValue(@Nullable Object value) {
        final long peer = getPeer();
        // null
        if (value == null) { return writeNull(peer); }

        // boolean
        if (value instanceof Boolean) { return writeBool(peer, (Boolean) value); }

        // Number
        if (value instanceof Number) {
            // Integer
            if (value instanceof Integer) { return writeInt(peer, ((Integer) value).longValue()); }

            // Long
            if (value instanceof Long) { return writeInt(peer, (Long) value); }

            // Short
            if (value instanceof Short) { return writeInt(peer, ((Short) value).longValue()); }

            // Double
            if (value instanceof Double) { return writeDouble(peer, (Double) value); }

            // Float
            return writeFloat(peer, (Float) value);
        }

        // String
        if (value instanceof String) { return writeString(peer, (String) value); }

        // byte[]
        if (value instanceof byte[]) { return writeData(peer, (byte[]) value); }

        // List
        if (value instanceof List) { return write((List<?>) value); }

        // Map
        if (value instanceof Map) { return write((Map<String, Object>) value); }

        // FLValue
        if (value instanceof FLValue) {
            final Boolean val = ((FLValue) value).withContent(hdl -> writeValue(peer, hdl));
            return (val != null) && val.booleanValue();
        }

        // FLDict
        if (value instanceof FLDict) {
            final Boolean val = ((FLDict) value).withContent(hdl -> writeValue(peer, hdl));
            return (val != null) && val.booleanValue();
        }

        // FLArray
        if (value instanceof FLArray) {
            final Boolean val = ((FLArray) value).withContent(hdl -> writeValue(peer, hdl));
            return (val != null) && val.booleanValue();
        }

        // FLEncodable
        if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(this);
            return true;
        }

        return false;
    }

    public boolean write(@Nullable Map<String, Object> map) {
        if (map == null) { beginDict(0); }
        else {
            beginDict(map.size());
            for (Map.Entry<String, Object> entry: map.entrySet()) {
                writeKey(entry.getKey());
                writeValue(entry.getValue());
            }
        }
        return endDict();
    }

    public boolean write(@Nullable List<?> list) {
        if (list == null) { beginArray(0); }
        else {
            beginArray(list.size());
            for (Object item: list) { writeValue(item); }
        }
        return endArray();
    }

    public void reset() { reset(getPeer()); }

    @NonNull
    public byte[] finish() throws LiteCoreException { return finish(getPeer()); }

    // NOTE: the FLSliceResult returned by this method must be released by the caller
    @NonNull
    public FLSliceResult finish2() throws LiteCoreException { return finish2(getPeer()); }

    // NOTE: We expect the FLSliceResult returned by this method to be handed, immediately,
    // to someone else (LiteCore) who will release it.  Java does release the memory.
    @NonNull
    public FLSliceResult finish3() throws LiteCoreException { return finish3(getPeer()); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    static native long newFleeceEncoder();

    static native void free(long encoder);

    static native void reset(long encoder);

    private static native boolean writeNull(long encoder);

    private static native boolean writeBool(long encoder, boolean value);

    private static native boolean writeInt(long encoder, long value); // 64bit

    private static native boolean writeFloat(long encoder, float value);

    private static native boolean writeDouble(long encoder, double value);

    private static native boolean writeString(long encoder, String value);

    private static native boolean writeData(long encoder, byte[] value);

    private static native boolean writeValue(long encoder, long value /*FLValue*/);

    private static native boolean beginArray(long encoder, long reserve);

    private static native boolean endArray(long encoder);

    private static native boolean beginDict(long encoder, long reserve);

    private static native boolean endDict(long encoder);

    private static native boolean writeKey(long encoder, String slice);

    @NonNull
    private static native byte[] finish(long encoder) throws LiteCoreException;

    @NonNull
    private static native FLSliceResult finish2(long encoder) throws LiteCoreException;

    @NonNull
    private static native FLSliceResult finish3(long encoder) throws LiteCoreException;
}

