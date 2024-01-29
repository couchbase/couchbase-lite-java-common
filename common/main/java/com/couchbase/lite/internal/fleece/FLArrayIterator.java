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


public abstract class FLArrayIterator extends C4NativePeer {
    // unmanaged: the native code will free it
    static final class UnmanagedFLArrayIterator extends FLArrayIterator {
        UnmanagedFLArrayIterator(@NonNull FLArray.NativeImpl impl, long peer) { super(impl, peer); }

        @Override
        public void close() { releasePeer(null, null); }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedFLArrayIterator extends FLArrayIterator {
        // Hold a reference to the object over which we iterate.
        @SuppressWarnings({"FieldCanBeLocal", "unused, PMD.SingularField", "PMD.UnusedPrivateField"})
        private final FLArray array;

        ManagedFLArrayIterator(@NonNull FLArray.NativeImpl impl, @NonNull FLArray array) {
            super(impl, array.withContent(impl::nInit));
            this.array = array;
        }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) {
            releasePeer(
                domain,
                (peer) -> {
                    final FLArray.NativeImpl nativeImpl = impl;
                    if (nativeImpl != null) { nativeImpl.nFree(peer); }
                });
        }
    }

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    protected final FLArray.NativeImpl impl;

    FLArrayIterator(@NonNull FLArray.NativeImpl impl, @Nullable Long peer) {
        super(peer);
        this.impl = impl;
    }

    FLArrayIterator(@NonNull FLArray.NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // Our close doesn't throw.
    public abstract void close();

    @Nullable
    public FLValue getValueAt(int index) {
        final long hValue = impl.nGetValueAt(getPeer(), index);
        return hValue == 0L ? null : FLValue.getFLValue(hValue);
    }

    /**
     * Advances the iterator to the next value.
     * NOTE: It is illegal to call this when the iterator is already at the end.
     * In particular, calling this when the array is empty is always illegal
     */
    public void next() { impl.nNext(getPeer()); }

    @Nullable
    public FLValue getValue() {
        final long hValue = impl.nGetValue(getPeer());
        return hValue == 0L ? null : FLValue.getFLValue(hValue);
    }
}
