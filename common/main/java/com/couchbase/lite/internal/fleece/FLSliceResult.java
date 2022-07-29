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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;
import com.couchbase.lite.internal.utils.ClassUtils;


/**
 * This is an interesting object.  In the C code it is a struct.  It is a little bit
 * clumsy to pass structs back adn forth across the JNI boundary, so, instead,
 * the JNI creates a C4SliceResult on the heap, copies the struct into it and the hands
 * the reference to it, to Java.  Similarly, when it comes time to pass the C4SliceResult
 * to C code, the JNI must copy the contents out of the heap into a struct.  It must also
 * <b>free the memory!</b>  The heap artifact is nothing that LiteCore knows anything about.
 *
 * If LiteCore handed the C4SliceResult to the JNI (and the JNI allocated heap space for it)
 * then the Java code must free it.  That's a <code>ManageFLSliceResult</code>.  If, on the
 * other hand, this is a C4SliceResult that the Java is handing to core, then the JNI will
 * free it after it copies the contents: as Jim Borden says: "The bus has reached its last stop"
 * That is an <code>UnmanagedFLSliceResult</code>
 */
public abstract class FLSliceResult extends C4NativePeer {

    // unmanaged: the native code will free it
    static final class UnmanagedFLSliceResult extends FLSliceResult {
        UnmanagedFLSliceResult(long peer) { super(peer); }

        @Override
        public void close() { releasePeer(null, null); }
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

    @Nullable
    public byte[] getBuf() { return getBuf(getPeer()); }

    public long getSize() { return getSize(getPeer()); }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    static native void free(long slice);

    private static native long init();

    @Nullable
    private static native byte[] getBuf(long slice);

    private static native long getSize(long slice);
}
