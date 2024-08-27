//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
import androidx.annotation.NonNull;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4CollectionObserver;
import com.couchbase.lite.internal.core.C4DocumentChange;


public final class NativeC4CollectionObserver implements C4CollectionObserver.NativeImpl {

    @Override
    public long nCreate(long token, long coll) throws LiteCoreException { return create(token, coll); }

    @Override
    @NonNull
    public C4DocumentChange[] nGetChanges(long peer, int maxChanges) { return getChanges(peer, maxChanges); }

    @Override
    public void nFree(long peer) { free(peer); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    //-------------------------------------------------------------------------

    @GuardedBy("dbLock")
    private static native long create(long token, long coll) throws LiteCoreException;

    @NonNull
    private static native C4DocumentChange[] getChanges(long peer, int maxChanges);

    private static native void free(long peer);
}
