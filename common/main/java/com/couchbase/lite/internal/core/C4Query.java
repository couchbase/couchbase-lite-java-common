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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.core.impl.NativeC4Query;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public final class C4Query extends C4Peer {
    public interface NativeImpl {
        long nCreateQuery(long db, int language, @NonNull String params) throws LiteCoreException;
        void nSetParameters(long peer, long paramPtr, long paramSize);
        @Nullable
        String nExplain(long peer);
        long nRun(long peer, long paramPtr, long paramSize) throws LiteCoreException;
        int nColumnCount(long peer);
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
        return c4db.withPeerOrThrow(dbPeer -> {
            final long peer = impl.nCreateQuery(dbPeer, language.getCode(), expression);
            return new C4Query(impl, peer);
        });
    }


    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Query(@NonNull NativeImpl impl, long peer) {
        super(peer, impl::nFree);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void setParameters(@NonNull FLSliceResult params) {
        voidWithPeerOrThrow(peer -> impl.nSetParameters(peer, params.getBase(), params.getSize()));
    }

    @Nullable
    public String explain() { return withPeerOrNull(impl::nExplain); }

    @Nullable
    public C4QueryEnumerator run(@NonNull FLSliceResult params) throws LiteCoreException {
        return withPeerOrNull(h -> C4QueryEnumerator.create(impl.nRun(h, params.getBase(), params.getSize())));
    }

    public int getColumnCount() { return withPeerOrDefault(0, impl::nColumnCount); }

    @Nullable
    public String getColumnNameForIndex(int idx) { return withPeerOrNull(peer -> impl.nColumnName(peer, idx)); }
}
