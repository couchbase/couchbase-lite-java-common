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

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4Query;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.utils.Preconditions;


public final class C4Query extends C4NativePeer {
    public interface NativeImpl {
        long nCreateQuery(long db, int language, @NonNull String params) throws LiteCoreException;
        void nSetParameters(long peer, long paramPtr, long paramSize);
        @NonNull
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
        @NonNull C4Database db,
        @NonNull AbstractIndex.QueryLanguage language,
        @NonNull String expression)
        throws LiteCoreException {
        return new C4Query(NATIVE_IMPL, db.getPeer(), language, expression);
    }


    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Query(
        @NonNull NativeImpl impl,
        long db,
        @NonNull AbstractIndex.QueryLanguage language,
        @NonNull String expression)
        throws LiteCoreException {
        super(impl.nCreateQuery(Preconditions.assertNotZero(db, "db peer ref"), language.getValue(), expression));
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // Documentation recommends that this call be made while holding the database lock
    @Override
    public void close() { closePeer(null); }

    public void setParameters(@NonNull FLSliceResult params) {
        impl.nSetParameters(getPeer(), params.getBase(), params.getSize());
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

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        // Despite the fact that the documentation recommends that this call be made
        // while holding the database lock, doing so can block the finalizer thread
        // causing it to abort.
        // Jens Alfke says: in practice it should be ok.
        // Jim Borden says:
        //   if the object is being finalized, it is not possible for client
        //   code to affect the query: in this case, freeing wo/ the lock is ok.
        //   That's how .NET does it.
        try { closePeer(LogDomain.QUERY); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
