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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


/**
 * Represent the block of native heap memory whose ref is passed as a parameter
 * or returned returned by the Core "init" call. The caller takes ownership of the "managed" version's peer
 * and must call the close() method to release it. The "unmanaged" version's peer belongs to Core:
 * it will be release by the native code.
 */
public abstract class FLSliceResult extends C4NativePeer implements AutoCloseable {

    // unmanaged: the native code will free it
    static final class UnmanagedFLSliceResult extends FLSliceResult {
        UnmanagedFLSliceResult() { super(); }

        UnmanagedFLSliceResult(long handle) { super(handle); }

        @Override
        public void close() { getPeerAndClear(); }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedFLSliceResult extends FLSliceResult {
        ManagedFLSliceResult() { super(); }

        ManagedFLSliceResult(long handle) { super(handle); }

        @Override
        public void close() { free(); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try {
                if (free()) { Log.i(LogDomain.DATABASE, "FLSliceResult was not closed: " + this); }
            }
            finally { super.finalize(); }
        }

        private boolean free() {
            final long hdl = getPeerAndClear();
            if (hdl == 0L) { return false; }

            free(hdl);

            return true;
        }
    }

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    public static FLSliceResult getUnmanagedSliceResult() { return new UnmanagedFLSliceResult(); }

    public static FLSliceResult getUnmanagedSliceResult(long handle) { return new UnmanagedFLSliceResult(handle); }

    public static FLSliceResult getManagedSliceResult() { return new ManagedFLSliceResult(); }

    public static FLSliceResult getManagedSliceResult(long handle) { return new ManagedFLSliceResult(handle); }


    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private FLSliceResult() { super(init()); }

    private FLSliceResult(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "FLSliceResult{" + ClassUtils.objId(this) + "/" + getPeerUnchecked() + "}"; }

    @Override
    public abstract void close();

    // !!!  Exposes the peer handle
    public long getHandle() { return getPeer(); }

    public byte[] getBuf() { return getBuf(getPeer()); }

    public long getSize() { return getSize(getPeer()); }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    static native void free(long slice);

    private static native long init();

    private static native byte[] getBuf(long slice);

    private static native long getSize(long slice);
}
