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
package com.couchbase.lite.internal.core;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;


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

    //////// RUNNING QUERIES:

    @Nullable
    public String explain() { return withPeerOrNull(C4Query::explain); }

    //////// INDEXES:

    // - Creates a database index, to speed up subsequent queries.

    @Nullable
    public C4QueryEnumerator run(@NonNull C4QueryOptions opts, @NonNull FLSliceResult params) throws LiteCoreException {
        return withPeerOrNull(h -> new C4QueryEnumerator(run(h, opts.isRankFullText(), params.getHandle())));
    }

    @Nullable
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public byte[] getFullTextMatched(@NonNull C4FullTextMatch match) throws LiteCoreException {
        return withPeerOrNull(h -> getFullTextMatched(h, match.getPeer()));
    }

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

    int columnCount() { return withPeer(0, C4Query::columnCount); }

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


    //////// DATABASE QUERIES:

    /**
     * Gets a fleece encoded array of indexes in the given database
     * that were created by `c4db_createIndex`
     */
    private static native long getIndexInfo(long db) throws LiteCoreException;

    private static native void deleteIndex(long db, String name) throws LiteCoreException;

    private static native boolean createIndex(
        long db,
        String name,
        String queryExpressions,
        int queryLanguage,
        int indexType,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException;

    private static native long createQuery(long db, int language, String expression) throws LiteCoreException;

    /**
     * Free C4Query* instance
     *
     * @param peer (C4Query*)
     */
    private static native void free(long peer);

    /**
     * @param peer (C4Query*)
     * @return C4StringResult
     */
    @Nullable
    private static native String explain(long peer);

    /**
     * Returns the number of columns (the values specified in the WHAT clause) in each row.
     *
     * @param peer (C4Query*)
     * @return the number of columns
     */
    private static native int columnCount(long peer);

    private static native long run(long peer, boolean rankFullText, /*FLSliceResult*/ long parameters)
        throws LiteCoreException;

    /**
     * Given a docID and sequence number from the enumerator, returns the text that was emitted
     * during indexing.
     */
    @NonNull
    private static native byte[] getFullTextMatched(long peer, long fullTextMatch) throws LiteCoreException;
}
