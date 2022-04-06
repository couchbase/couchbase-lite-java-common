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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;


public class C4Query extends C4NativePeer {
    public static void createIndex(
        @NonNull C4Database db,
        @NonNull String name,
        @NonNull String queryExpression,
        @NonNull AbstractIndex.QueryLanguage queryLanguage,
        @NonNull AbstractIndex.IndexType indexType,
        @Nullable String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        Log.d(LogDomain.QUERY, "creating index: %s", queryExpression);
        createIndex(
            db.getPeer(),
            name,
            queryExpression,
            queryLanguage.getValue(),
            indexType.getValue(),
            language,
            ignoreDiacritics);
    }

    @NonNull
    public static FLValue getIndexInfo(@NonNull C4Database db) throws LiteCoreException {
        return new FLValue(getIndexInfo(db.getPeer()));
    }

    public static void deleteIndex(@NonNull C4Database db, String name) throws LiteCoreException {
        deleteIndex(db.getPeer(), name);
    }


    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    C4Query(long db, @NonNull AbstractIndex.QueryLanguage queryLanguage, @NonNull String expression)
        throws LiteCoreException {
        super(createQuery(db, queryLanguage.getValue(), expression));
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    public void setParameters(@NonNull FLSliceResult params) { setParameters(getPeer(), params.getPeer()); }

    @Nullable
    public String explain() { return withPeerOrNull(C4Query::explain); }

    @Nullable
    public C4QueryEnumerator run(@NonNull C4QueryOptions opts, @NonNull FLSliceResult params) throws LiteCoreException {
        return withPeerOrNull(h -> C4QueryEnumerator.create(run(h, opts.isRankFullText(), params.getHandle())));
    }

    public int getColumnCount() { return withPeer(0, C4Query::columnCount); }

    @Nullable
    public String getColumnNameForIndex(int idx) { return withPeerOrNull(peer -> columnName(peer, idx)); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        // Despite the fact that the documentation insists that this call be made
        // while holding the database lock, doing so can block the finalizer thread
        // causing it to abort.
        // Jens Alfke says: in practice it should be ok.
        // Jim Borden says: if the object is being finalized, it is not possible for client
        //   code to affect the query: in this case, freeing wo/ the lock is ok.
        //   That's how .NET does it.
        try { closePeer(LogDomain.QUERY); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    @Nullable
    @VisibleForTesting
    C4QueryEnumerator run(@NonNull C4QueryOptions opts) throws LiteCoreException {
        try (FLSliceResult params = FLSliceResult.getManagedSliceResult()) { return run(opts, params); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        // Despite the fact that the documentation insists that the call to "free"
        // be made while holding the database lock, doing so can block the finalizer
        // thread causing it to abort.
        // Jens Alfke says: in practice it should be ok.
        // Jim Borden says:
        //   If the object is being finalized, it is not possible for client
        //   code to affect the query: in this case, freeing wo/ the lock is ok.
        //   That's how .NET does it.
        releasePeer(domain, C4Query::free);
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------


    //////// QUERIES:

    private static native long createQuery(long db, int language, String params) throws LiteCoreException;

    private static native void setParameters(long peer, long params);

    /**
     * @param peer (C4Query*)
     * @return C4StringResult
     */
    @Nullable
    private static native String explain(long peer);

    private static native long run(long peer, boolean rankFullText, /*FLSliceResult*/ long parameters)
        throws LiteCoreException;

    /**
     * Returns the number of columns (the values specified in the WHAT clause) in each row.
     *
     * @param peer (C4Query*)
     * @return the number of columns
     */
    private static native int columnCount(long peer);

    /**
     * Returns the name of the indexed column.
     *
     * @param peer   (C4Query*)
     * @param colIdx the index of the column whose name is sought
     */
    @Nullable
    private static native String columnName(long peer, int colIdx);

    /**
     * Free C4Query* instance
     *
     * @param peer (C4Query*)
     */
    private static native void free(long peer);


    //////// INDICES

    private static native boolean createIndex(
        long db,
        String name,
        String queryExpressions,
        int queryLanguage,
        int indexType,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException;

    /**
     * Gets a fleece encoded array of indexes in the given database
     * that were created by `c4db_createIndex`
     */
    private static native long getIndexInfo(long db) throws LiteCoreException;

    private static native void deleteIndex(long db, String name) throws LiteCoreException;
}
