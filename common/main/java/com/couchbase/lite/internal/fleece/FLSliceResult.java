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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/*
 * Represent a block of memory returned from the API call. The caller takes ownership, and must
 * call free() method to release the memory except the managed() method is called to indicate
 * that the memory will be managed and released by the native code.
 */
public class FLSliceResult extends C4NativePeer implements AutoCloseable, AllocSlice {
    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private final boolean isCoreOwned;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public FLSliceResult() { this(false); }

    /*
     * Create a FLSliceResult whose ownership will be passed to core.
     * Use this method when the FLSliceResult will be freed by the native code.
     */
    public FLSliceResult(boolean isCoreOwned) { this(init(), isCoreOwned); }

    public FLSliceResult(byte[] bytes) { this(initWithBytes(Preconditions.assertNotNull(bytes, "raw bytes"))); }

    public FLSliceResult(long handle) { this(handle, false); }

    FLSliceResult(long handle, boolean isCoreOwned) {
        super(handle);
        this.isCoreOwned = isCoreOwned;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    public long getHandle() { return getPeer(); }

    public byte[] getBuf() { return getBuf(getPeer()); }

    public long getSize() { return getSize(getPeer()); }

    @Override
    public void close() {
        if (isCoreOwned) {
            Log.w(LogDomain.DATABASE, "Attempt to free core-owned FLSliceResult: " + this);
            return;
        }

        final long hdl = getPeerAndClear();
        if (hdl == 0L) { return; }

        free(hdl);
    }

    @Override
    public void free() { close(); }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { freeOwned(); }
        finally { super.finalize(); }
    }

    private void freeOwned() {
        if (isCoreOwned) { return; }

        final long hdl = getPeerUnchecked();
        if (hdl == 0L) { return; }

        Log.w(LogDomain.DATABASE, "FLSliceResult was not freed: " + this);

        free(hdl);
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    private static native long init();

    private static native long initWithBytes(byte[] bytes);

    private static native void free(long slice);

    private static native byte[] getBuf(long slice);

    private static native long getSize(long slice);
}
