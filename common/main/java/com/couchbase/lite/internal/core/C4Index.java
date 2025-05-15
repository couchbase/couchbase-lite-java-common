//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4Index;


public final class C4Index extends C4NativePeer {
    public interface NativeImpl {
        @GuardedBy("dbLock")
        long nBeginUpdate(long peer, int limit) throws LiteCoreException;
        void nReleaseIndex(long peer);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Index();

    @NonNull
    public static C4Index create(long peer, @NonNull Object dbLock) { return new C4Index(NATIVE_IMPL, peer, dbLock); }


    private final NativeImpl impl;

    // Always seize this lock *after* the C4Peer lock
    @NonNull
    private final Object dbLock;

    private C4Index(@NonNull NativeImpl impl, long peer, @NonNull Object dbLock) {
        super(peer);
        this.impl = impl;
        this.dbLock = dbLock;
    }

    @Nullable
    public C4IndexUpdater beginUpdate(int limit) throws LiteCoreException {
        return nullableWithPeerOrThrow(peer -> {
            final long updater;
            synchronized (dbLock) { updater = impl.nBeginUpdate(peer, limit); }
            return (updater == 0L) ? null : C4IndexUpdater.create(updater);
        });
    }

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
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            peer -> {
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nReleaseIndex(peer); }
            });
    }
}
