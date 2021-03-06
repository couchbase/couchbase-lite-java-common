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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.fleece.FLArrayIterator;


/**
 * C4QueryEnumerator
 * A query result enumerator
 * Created by c4db_query. Must be freed with c4queryenum_free.
 * The fields of this struct represent the current matched index row.
 * They are valid until the next call to c4queryenum_next or c4queryenum_free.
 */
public class C4QueryEnumerator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4QueryEnumerator(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public boolean next() throws LiteCoreException { return next(getPeer()); }

    public long getRowCount() throws LiteCoreException { return getRowCount(getPeer()); }

    @Nullable
    public C4QueryEnumerator refresh() throws LiteCoreException {
        final long newPeer = refresh(getPeer());
        return (newPeer == 0) ? null : new C4QueryEnumerator(newPeer);
    }

    /**
     * FLArrayIterator columns
     * The columns of this result, in the same order as in the query's `WHAT` clause.
     * NOTE: FLArrayIterator is member variable of C4QueryEnumerator. Not necessary to release.
     */
    @NonNull
    public FLArrayIterator getColumns() { return FLArrayIterator.getUnmanagedArrayIterator(getColumns(getPeer())); }

    /**
     * Returns a bitmap in which a 1 bit represents a column whose value is MISSING.
     * This is how you tell a missing property value from a value that is JSON 'null',
     * since the value in the `columns` array will be a Fleece `null` either way.
     */
    public long getMissingColumns() { return getMissingColumns(getPeer()); }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    @VisibleForTesting
    public boolean seek(long rowIndex) throws LiteCoreException { return seek(getPeer(), rowIndex); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.QUERY); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    /**
     * Return the number of full-text matches (i.e. the number of items in `getFullTextMatches`)
     */
    long getFullTextMatchCount() { return getFullTextMatchCount(getPeer()); }

    /**
     * Return an array of details of each full-text match
     */
    @NonNull
    C4FullTextMatch getFullTextMatches(int idx) { return new C4FullTextMatch(getFullTextMatch(getPeer(), idx)); }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4QueryEnumerator::free); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native boolean next(long peer) throws LiteCoreException;

    private static native long getRowCount(long peer) throws LiteCoreException;

    private static native boolean seek(long peer, long rowIndex) throws LiteCoreException;

    private static native long refresh(long peer) throws LiteCoreException;

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static native void close(long peer);

    private static native void free(long peer);

    private static native long getColumns(long peer);

    private static native long getMissingColumns(long peer);

    private static native long getFullTextMatchCount(long peer);

    private static native long getFullTextMatch(long peer, int idx);
}
