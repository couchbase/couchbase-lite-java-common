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
import android.support.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;


public abstract class FLArrayIterator extends C4NativePeer {

    // unmanaged: the native code will free it
    static final class UnmanagedFLArrayIterator extends FLArrayIterator {
        UnmanagedFLArrayIterator(long peer) { super(peer); }

        @Override
        public void close() { releasePeer(); }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedFLArrayIterator extends FLArrayIterator {
        ManagedFLArrayIterator() { super(init()); }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, FLArrayIterator::free); }
    }

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static FLArrayIterator getUnmanagedArrayIterator(long peer) {
        return new FLArrayIterator.UnmanagedFLArrayIterator(peer);
    }

    @NonNull
    public static FLArrayIterator getManagedArrayIterator() {
        return new FLArrayIterator.ManagedFLArrayIterator();
    }

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public FLArrayIterator() { super(init()); }

    public FLArrayIterator(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void begin(@NonNull FLArray array) {
        final long peer = getPeer();
        array.withContent(hdl -> {
            begin(hdl, peer);
            return null;
        });
    }

    public boolean next() { return next(getPeer()); }

    @Nullable
    public FLValue getValue() {
        final long hValue = getValue(getPeer());
        return hValue == 0L ? null : new FLValue(hValue);
    }

    @Nullable
    public FLValue getValueAt(int index) {
        final long hValue = getValueAt(getPeer(), index);
        return hValue == 0L ? null : new FLValue(hValue);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Create FLArrayIterator instance
     *
     * @return long (FLArrayIterator *)
     */
    static native long init();

    /**
     * Free FLArrayIterator instance
     *
     * @param peer (FLArrayIterator *)
     */
    static native void free(long peer);

    /**
     * Initializes a FLArrayIterator struct to iterate over an array.
     *
     * @param array (FLArray)
     * @param peer  (FLArrayIterator *)
     */
    private static native void begin(long array, long peer);

    /**
     * Returns the current value being iterated over.
     *
     * @param peer (FLArrayIterator *)
     * @return long (FLValue)
     */
    private static native long getValue(long peer);

    /**
     * @param peer   (FLArrayIterator *)
     * @param offset Array offset
     * @return long (FLValue)
     */
    private static native long getValueAt(long peer, int offset);

    /**
     * Advances the iterator to the next value, or returns false if at the end.
     *
     * @param peer (FLArrayIterator *)
     */
    private static native boolean next(long peer);
}
