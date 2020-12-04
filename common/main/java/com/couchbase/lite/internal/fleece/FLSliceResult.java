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


/*
 * Represent the block of native heap memory returned from the API call.
 * Normally, the caller takes ownership and must call close() method to release the memory.
 * If either if the two constructors with an 'isCoreOwned' param is called with that param true,
 * the heap memory will be released in native code.
 */
public class FLSliceResult extends C4NativePeer implements AutoCloseable {
    private final boolean isCoreOwned;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public FLSliceResult() { this(false); }

    public FLSliceResult(long handle) { this(handle, false); }

    /*
     * Create a FLSliceResult whose ownership may be passed to core.
     * Use these methods with arg "true" when the FLSliceResult will be freed by native code.
     */

    public FLSliceResult(boolean isCoreOwned) { this(init(), isCoreOwned); }

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
        if (!isCoreOwned) {
            free(false);
            return;
        }

        Log.w(LogDomain.DATABASE, "Attempt to close a core-owned FLSliceResult: " + this, new Exception());
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!isCoreOwned) { free(true); }
        }
        finally { super.finalize(); }
    }

    private void free(boolean shouldBeFree) {
        final long hdl = getPeerAndClear();
        if (hdl == 0L) { return; }

        free(hdl);

        if (shouldBeFree) { Log.w(LogDomain.DATABASE, "FLSliceResult was not closed: " + this); }
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    private static native long init();

    private static native void free(long slice);

    private static native byte[] getBuf(long slice);

    private static native long getSize(long slice);
}
