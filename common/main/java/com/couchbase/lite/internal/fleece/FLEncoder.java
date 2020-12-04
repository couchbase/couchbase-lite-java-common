//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;

import java.util.List;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;
import com.couchbase.lite.internal.support.Log;


@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass"})
public class FLEncoder extends C4NativePeer implements AutoCloseable {
    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private final boolean isCoreOwned;

    private Object extraInfo;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public FLEncoder() { this(init(), false); }

    /*
     * Create a FLEncoder whose ownership may be passed to core.
     * Use this method with arg "true" when the FLEncoder will be freed by native code.
     */

    public FLEncoder(long handle, boolean managed) {
        super(handle);
        this.isCoreOwned = managed;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public boolean writeString(String value) { return writeString(getPeer(), value); }

    public boolean writeData(byte[] value) { return writeData(getPeer(), value); }

    public boolean beginDict(long reserve) { return beginDict(getPeer(), reserve); }

    public boolean endDict() { return endDict(getPeer()); }

    public boolean beginArray(long reserve) { return beginArray(getPeer(), reserve); }

    public boolean endArray() { return endArray(getPeer()); }

    public boolean writeKey(String slice) { return writeKey(getPeer(), slice); }

    @SuppressWarnings({"unchecked", "PMD.NPathComplexity"})
    public boolean writeValue(Object value) {
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
            return ((FLValue) value).withContent(hdl -> (writeValue(peer, hdl)));
        }

        // FLDict
        if (value instanceof FLDict) {
            return ((FLDict) value).withContent(hdl -> (writeValue(peer, hdl)));
        }

        // FLArray
        if (value instanceof FLArray) {
            return ((FLArray) value).withContent(hdl -> (writeValue(peer, hdl)));
        }

        // FLEncodable
        if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(this);
            return true;
        }

        return false;
    }

    public boolean writeNull() { return writeNull(getPeer()); }

    public boolean write(Map<String, Object> map) {
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

    public boolean write(List<?> list) {
        if (list == null) { beginArray(0); }
        else {
            beginArray(list.size());
            for (Object item: list) { writeValue(item); }
        }
        return endArray();
    }

    public byte[] finish() throws LiteCoreException { return finish(getPeer()); }

    @NonNull
    public FLSliceResult finish2() throws LiteCoreException {
        return new FLSliceResult(finish2(getPeer()));
    }

    @NonNull
    public FLSliceResult managedFinish2() throws LiteCoreException {
        return new FLSliceResult(finish2(getPeer()), true);
    }

    public Object getExtraInfo() { return extraInfo; }

    public void setExtraInfo(Object info) { extraInfo = info; }

    public void reset() { reset(getPeer()); }

    public void close() { free(false); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { free(true); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void free(boolean shouldBeFree) {
        final long hdl = getPeerAndClear();
        if (hdl == 0L) { return; }

        if (shouldBeFree) { Log.w(LogDomain.DATABASE, "FLEncoder was not closed: " + this); }

        if (!isCoreOwned) { free(hdl); }
        else {
            reset(hdl);
            extraInfo = null;
        }
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long init(); // FLEncoder FLEncoder_New(void);

    private static native void free(long encoder);

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

    private static native byte[] finish(long encoder) throws LiteCoreException;

    private static native long finish2(long encoder) throws LiteCoreException;

    private static native void reset(long encoder);
}
