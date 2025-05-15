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
package com.couchbase.lite.internal.core;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.core.impl.NativeC4Query;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public final class C4Query extends C4Peer {
    public interface NativeImpl {
        @GuardedBy("dbLock")
        long nCreateQuery(long db, int language, @NonNull String params) throws LiteCoreException;
        void nSetParameters(long peer, long paramPtr, long paramSize);
        @GuardedBy("dbLock")
        @Nullable
        String nExplain(long peer);
        @GuardedBy("dbLock")
        long nRun(long peer, long paramPtr, long paramSize) throws LiteCoreException;
        @GuardedBy("queryLock")
        int nColumnCount(long peer);
        @GuardedBy("queryLock")
        @Nullable
        String nColumnName(long peer, int colIdx);
        void nFree(long peer);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Query();

    @NonNull
    public static C4Query create(
        @NonNull C4Database c4db,
        @NonNull QueryLanguage language,
        @NonNull String expression)
        throws LiteCoreException {
        return create(NATIVE_IMPL, c4db, language, expression);
    }

    @VisibleForTesting
    @NonNull
    public static C4Query create(
        @NonNull NativeImpl impl,
        @NonNull C4Database c4db,
        @NonNull QueryLanguage language,
        @NonNull String expression)
        throws LiteCoreException {
        final Object dbLock = c4db.getDbLock();
        return c4db.withPeerOrThrow(dbPeer -> {
            final long peer = impl.nCreateQuery(dbPeer, language.getCode(), expression);
            return new C4Query(impl, peer, dbLock);
        });
    }


    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;
    // Always seize this lock *after* the C4Peer lock
    @NonNull
    private final Object dbLock;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Query(@NonNull NativeImpl impl, long peer, @NonNull Object dbLock) {
        super(peer, impl::nFree);
        this.impl = impl;
        this.dbLock = dbLock;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void setParameters(@NonNull FLSliceResult params) {
        voidWithPeerOrThrow(peer -> impl.nSetParameters(peer, params.getBase(), params.getSize()));
    }

    @Nullable
    public String explain() {
        return withPeerOrNull(peer -> {
            synchronized (dbLock) { return impl.nExplain(peer); }
        });
    }

    @Nullable
    public C4QueryEnumerator run(@NonNull FLSliceResult params) throws LiteCoreException {
        return withPeerOrNull(peer -> {
            synchronized (dbLock) {
                return C4QueryEnumerator.create(impl.nRun(peer, params.getBase(), params.getSize()));
            }
        });
    }

    // the C4Peer lock is sufficient to protect this method
    public int getColumnCount() { return withPeerOrDefault(0, impl::nColumnCount); }

    // the C4Peer lock is sufficient to protect this method
    @Nullable
    public String getColumnNameForIndex(int idx) { return withPeerOrNull(peer -> impl.nColumnName(peer, idx)); }

    //-------------------------------------------------------------------------
    // package methods
    //-------------------------------------------------------------------------

    @NonNull
    Object getDbLock() { return dbLock; }
}
