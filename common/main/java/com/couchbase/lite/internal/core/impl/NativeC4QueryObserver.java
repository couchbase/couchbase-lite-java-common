//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;

import com.couchbase.lite.internal.core.C4QueryObserver;


public final class NativeC4QueryObserver implements C4QueryObserver.NativeImpl {

    @Override
    public long nCreate(long peer, long token) { return create(peer, token); }

    @GuardedBy("dbLock")
    @Override
    public void nEnable(long peer) { enable(peer); }

    @Override
    public void nFree(long peer) { free(peer); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    // Thread safety verified as of 2025/5/15
    //-------------------------------------------------------------------------

    private static native long create(long peer, long token);

    @GuardedBy("dbLock")
    private static native void enable(long peer);

    private static native void free(long peer);
}
