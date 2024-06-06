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

import com.couchbase.lite.internal.fleece.impl.NativeFLSliceResult;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This object a frames a piece of native memory for which the recipient (caller) is responsible.
 * `base` points at the start of the block, base + size is its end. The JNI just creates one of these
 * whenever LiteCore returns a native FLSliceResult (or its alias, C4SliceResult).
 */
public abstract class FLSliceResult implements AutoCloseable {
    public interface NativeImpl {
        @Nullable
        byte[] nGetBuf(long base, long size);
        void nRelease(long base, long size);
    }

    // We manage the FLSliceResult in almost all cases.
    // We own the block of memory it frames and must release it.
    private static final class ManagedFLSliceResult extends FLSliceResult {
        ManagedFLSliceResult(@NonNull NativeImpl impl, long base, long size) { super(impl, base, size); }

        @Override
        protected void release(@NonNull NativeImpl impl) { impl.nRelease(base, size); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { close(); }
            finally { super.finalize(); }
        }
    }

    // If we are going to return this FLSliceResult to someone else (LiteCore),
    // though, it will belong to *them* and we must not release it.
    private static final class UnmanagedFLSliceResult extends FLSliceResult {
        UnmanagedFLSliceResult(@NonNull NativeImpl impl, long base, long size) { super(impl, base, size); }

        @Override
        protected void release(@NonNull NativeImpl impl) { }
    }

    private static final FLSliceResult.NativeImpl NATIVE_IMPL = new NativeFLSliceResult();

    // This method is used by reflection.  Don't change its signature.
    @NonNull
    public static FLSliceResult createManagedSlice(long base, long size) {
        return new ManagedFLSliceResult(NATIVE_IMPL, base, size);
    }

    @NonNull
    public static FLSliceResult createUnmanagedSlice(long base, long size) {
        return new UnmanagedFLSliceResult(NATIVE_IMPL, base, size);
    }

    @VisibleForTesting
    @NonNull
    public static FLSliceResult createTestSlice() { return createManagedSlice(0, 0); }


    // These fields are used by reflection.  Don't change them.
    final long base;
    final long size;

    // Not using an AtomicBoolean here because the Android VM
    // will GC an object's data members before finalizing the object
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

    @NonNull
    @Override
    public String toString() {
        return "SliceResult{" + ClassUtils.objId(this) + " @0x0" + Long.toHexString(base) + ", " + size + "}";
    }

    // ??? Exposes a native pointer
    public long getBase() { return base; }

    public long getSize() { return size; }

    // this returns a *copy* of the data
    @Nullable
    public byte[] getContent() {
        synchronized (this) {
            if (impl == null) { throw new IllegalStateException("Attempt to use a closed slice"); }
            return impl.nGetBuf(base, size);
        }
    }

    @Override
    public void close() {
        final NativeImpl ni;
        synchronized (this) {
            ni = impl;
            impl = null;
        }

        if (ni != null) { release(ni); }
    }

    protected abstract void release(@NonNull NativeImpl ni);
}
