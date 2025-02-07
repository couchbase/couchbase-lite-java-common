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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.internal.fleece.impl.NativeFLSliceResult;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This object a frames a piece of native memory for which Java is responsible.
 * `base` points at the start of the block, base + size is its end. The JNI just creates one of these
 * whenever LiteCore returns a native FLSliceResult (or its alias, C4SliceResult).
 */
public final class FLSliceResult implements AutoCloseable {
    public interface NativeImpl {
        @Nullable
        byte[] nGetBuf(long base, long size);
        void nRelease(long base, long size);
    }

    private static final FLSliceResult.NativeImpl NATIVE_IMPL = new NativeFLSliceResult();

    // This method is used by reflection.  Don't change its signature.
    @NonNull
    public static FLSliceResult create(long base, long size) { return new FLSliceResult(NATIVE_IMPL, base, size); }

    @VisibleForTesting
    @NonNull
    public static FLSliceResult createTestSlice() { return create(0, 0); }


    // These fields are used by reflection.  Don't change them.
    private final long base;
    private final long size;

    // Not using an AtomicBoolean here because the Android VM can
    // GC an object's data members before finalizing the object
    @Nullable
    @GuardedBy("this")
    private NativeImpl impl;

    private FLSliceResult(@NonNull NativeImpl impl, long base, long size) {
        this.impl = impl;
        this.base = base;
        this.size = Preconditions.assertNotNegative(size, "size");
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    // Exposes a native pointer!
    // There is a race: this object could become invalid between the time the this method returns
    // and the time the caller uses the return value.  There's not much point in synchronizing the method.
    public long getBase() {
        getValidImpl();
        return base;
    }

    // There is a race: this object could become invalid between the time the this method returns
    // and the time the caller uses the return value.  There's not much point in synchronizing the method.
    public long getSize() {
        getValidImpl();
        return size;
    }

    // This returns a *copy* of the data
    // !!! This is a bad idea:  we should just pass this object around
    // instead of copying the slice into Java memory.
    @Nullable
    public byte[] getContent() {
        synchronized (this) { return getValidImpl().nGetBuf(base, size); }
    }

    @NonNull
    @Override
    public String toString() {
        return "SliceResult{" + ClassUtils.objId(this) + " @0x0" + Long.toHexString(base) + ", " + size + "}";
    }

    @Override
    public void close() {
        final NativeImpl ni;
        synchronized (this) {
            ni = impl;
            impl = null;
        }

        if (ni != null) { ni.nRelease(base, size); }
    }

    // The memory framed by this object no longer belongs to us:
    // Probably we gave it to someone else.
    public void unbind() {
        synchronized (this) { impl = null; }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { close(); }
        finally { super.finalize(); }
    }

    @NonNull
    private NativeImpl getValidImpl() {
        synchronized (this) {
            if (impl == null) { throw new CouchbaseLiteError("Attempt to use an invalid slice"); }
            return impl;
        }
    }
}
