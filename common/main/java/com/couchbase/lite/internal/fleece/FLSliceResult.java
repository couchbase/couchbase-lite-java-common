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

import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This object a frames a piece of native memory for which the recipient (caller) is responsible.
 * `base` points at the start of the block, base + size is its end. The JNI just creates one of these
 * whenever LiteCore returns a native FLSliceResult (or its alias, C4SliceResult).
 */
public abstract class FLSliceResult implements AutoCloseable {
    // We manage the FLSliceResult in almost all cases.
    // We own the block of memory it frames and must release it.
    private static final class ManagedFLSliceResult extends FLSliceResult {
        ManagedFLSliceResult(long base, long size) { super(base, size); }

        @Override
        protected void release() { release(base, size); }

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
        UnmanagedFLSliceResult(long base, long size) { super(base, size); }

        @Override
        protected void release() { }
    }

    // This method is used by reflection.  Don't change its signature.
    @NonNull
    public static FLSliceResult createManagedSlice(long base, long size) {
        return new ManagedFLSliceResult(base, size);
    }

    @NonNull
    public static FLSliceResult createUnmanagedSlice(long base, long size) {
        return new UnmanagedFLSliceResult(base, size);
    }

    @VisibleForTesting
    @NonNull
    public static FLSliceResult createTestSlice() { return createManagedSlice(0, 0); }


    // These fields are used by reflection.  Don't change them.
    final long base;
    final long size;

    @GuardedBy("this")
    boolean closed;

    private FLSliceResult(long base, long size) {
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
            if (closed) { throw new IllegalStateException("Attempt to use a closed slice"); }
            return getBuf(base, size);
        }
    }

    @Override
    public void close() {
        final boolean wasClosed;
        synchronized (this) {
            wasClosed = closed;
            closed = true;
        }

        if (!wasClosed) { release(); }
    }

    protected abstract void release();

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    protected static native void release(long base, long size);

    @Nullable
    private static native byte[] getBuf(long base, long size);
}
