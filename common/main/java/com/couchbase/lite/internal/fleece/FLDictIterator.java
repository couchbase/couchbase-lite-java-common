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


import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4NativePeer;


public class FLDictIterator extends C4NativePeer {
    // Hold a reference to the object over which we iterate.
    @SuppressWarnings({"PMD.SingularField", "PMD.UnusedPrivateField", "FieldCanBeLocal", "unused"})
    private final FLDict dict;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    public FLDictIterator(@NonNull FLDict dict) {
        super(dict.withContent(FLDictIterator::init));
        this.dict = dict;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public long getCount() { return getCount(getPeer()); }

    public boolean next() { return next(getPeer()); }

    @Nullable
    public String getKey() { return getKey(getPeer()); }

    @Nullable
    public FLValue getValue() { return new FLValue(getValue(getPeer())); }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, FLDictIterator::free); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Initialize a FLDictIterator instance
     *
     * @return long (FLDictIterator *)
     */
    private static native long init(long dict);

    /**
     * Returns the number of items remaining to be iterated, including the current one.
     *
     * @param itr (FLDictIterator *)
     */
    private static native long getCount(long itr);

    /**
     * Advances the iterator to the next value, or returns false if at the end.
     *
     * @param itr (FLDictIterator *)
     */
    private static native boolean next(long itr);

    /**
     * Returns the key's string value.
     *
     * @param itr (FLDictIterator *)
     * @return key string
     */
    @Nullable
    private static native String getKey(long itr);

    /**
     * Returns the current value being iterated over.
     *
     * @param itr (FLDictIterator *)
     * @return long (FLValue)
     */
    private static native long getValue(long itr);

    /**
     * Free FLDictIterator instance
     *
     * @param itr (FLDictIterator *)
     */
    private static native void free(long itr);
}
