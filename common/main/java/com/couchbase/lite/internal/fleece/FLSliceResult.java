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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;
import com.couchbase.lite.internal.utils.ClassUtils;


/**
 * Represent the block of native heap memory whose ref is passed as a parameter
 * or returned returned by the Core "init" call. The caller takes ownership of the "managed" version's peer
 * and must call the close() method to release it. The "unmanaged" version's peer belongs to Core:
 * it will be release by the native code.
 */
public abstract class FLSliceResult extends C4NativePeer {

    // unmanaged: the native code will free it
    static final class UnmanagedFLSliceResult extends FLSliceResult {
        UnmanagedFLSliceResult() { }

        UnmanagedFLSliceResult(long peer) { super(peer); }

        @Override
        public void close() { releasePeer(); }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedFLSliceResult extends FLSliceResult {
        ManagedFLSliceResult() { }

        ManagedFLSliceResult(long peer) { super(peer); }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, FLSliceResult::free); }
    }

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static FLSliceResult getUnmanagedSliceResult() { return new UnmanagedFLSliceResult(); }

    @NonNull
    public static FLSliceResult getUnmanagedSliceResult(long peer) { return new UnmanagedFLSliceResult(peer); }

    @NonNull
    public static FLSliceResult getManagedSliceResult() { return new ManagedFLSliceResult(); }

    @NonNull
    public static FLSliceResult getManagedSliceResult(long peer) { return new ManagedFLSliceResult(peer); }


    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private FLSliceResult() { this(init()); }

    private FLSliceResult(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "FLSliceResult{" + ClassUtils.objId(this) + "/" + super.toString() + "}"; }

    @Override
    public abstract void close();

    // !!!  Exposes the peer handle
    public long getHandle() { return getPeer(); }

    @NonNull
    public byte[] getBuf() { return getBuf(getPeer()); }

    public long getSize() { return getSize(getPeer()); }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    static native void free(long slice);

    private static native long init();

    @NonNull
    private static native byte[] getBuf(long slice);

    private static native long getSize(long slice);
}
