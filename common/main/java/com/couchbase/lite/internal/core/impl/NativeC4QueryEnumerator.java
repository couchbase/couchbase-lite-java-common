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
package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4QueryEnumerator;


public final class NativeC4QueryEnumerator implements C4QueryEnumerator.NativeImpl {
    @GuardedBy("queryEnumLock")
    @Override
    public boolean nNext(long peer) throws LiteCoreException { return next(peer); }

    @Override
    public void nFree(long peer) { free(peer); }

    @Override
    public long nGetColumns(long peer) { return getColumns(peer); }

    @Override
    public long nGetMissingColumns(long peer) { return getMissingColumns(peer); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    // Thread safety verified as of 2025/5/15
    //-------------------------------------------------------------------------

    @GuardedBy("queryEnumLock")
    private static native boolean next(long peer) throws LiteCoreException;

    private static native long getColumns(long peer);

    private static native long getMissingColumns(long peer);

    private static native void free(long peer);
}
