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

package com.couchbase.lite;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A result set representing the query result. The result set is an iterator of
 * the {@code Result} objects.
 */
public class ResultSet implements Iterable<Result>, AutoCloseable {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final AbstractQuery query;
    @NonNull
    private final Map<String, Integer> columnNames;
    @NonNull
    private final DbContext context;

    @GuardedBy("getDbLock()")
    @Nullable
    private C4QueryEnumerator c4enum;

    @GuardedBy("getDbLock()")
    private boolean isAllEnumerated;

    @GuardedBy("getDbLock()")
    private boolean uncloseable;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    ResultSet(
        @NonNull AbstractQuery query,
        @Nullable C4QueryEnumerator c4enum,
        @NonNull Map<String, Integer> cols) {
        this.query = query;
        this.columnNames = cols;
        this.context = new DbContext(query.getDatabase());
        this.c4enum = c4enum;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Move the cursor forward one row from its current row position.
     * Caution: next() method and iterator() method share same data structure.
     * Please don't use them together.
     * Caution: In case ResultSet is obtained from QueryChangeListener, and QueryChangeListener is
     * already removed from Query, ResultSet is already freed. And this next() method returns null.
     *
     * @return the Result after moving the cursor forward. Returns {@code null} value
     * if there are no more rows, or ResultSet is freed already.
     */
    @Nullable
    public Result next() {
        Preconditions.assertNotNull(query, "query");

        synchronized (getDbLock()) {
            try {
                if (c4enum == null) { return null; }
                else if (isAllEnumerated) {
                    Log.w(DOMAIN, "ResultSetAlreadyEnumerated");
                    return null;
                }
                else if (!c4enum.next()) {
                    if (CouchbaseLiteInternal.debugging()) { Log.d(DOMAIN, "End of query enumeration"); }
                    isAllEnumerated = true;
                    return null;
                }
                else {
                    return new Result(this, c4enum, context);
                }
            }
            catch (LiteCoreException e) {
                Log.w(DOMAIN, "Error enumerating query", e);
                return null;
            }
        }
    }

    /**
     * Return List of Results. List is unmodifiable and only supports
     * int get(int index), int size(), boolean isEmpty() and Iterator&lt;Result&gt; iterator() methods.
     * Once called allResults(), next() method return null. Don't call next() and allResults()
     * together.
     *
     * @return List of Results
     */
    @NonNull
    public List<Result> allResults() {
        final List<Result> results = new ArrayList<>();
        Result result;
        while ((result = next()) != null) { results.add(result); }
        return results;
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    /**
     * Return Iterator of Results.
     * Once called iterator(), next() method return null. Don't call next() and iterator()
     * together.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    @NonNull
    @Override
    public Iterator<Result> iterator() { return allResults().iterator(); }

    @Override
    public void close() {
        synchronized (getDbLock()) {
            if (!uncloseable) {
                forceClose();
                return;
            }
        }
        // Make a guess about why this is retained...
        Log.i(LogDomain.QUERY, "Attempt to close a retained ResultSet.  Do not close the Results from a LiveQuery");
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // An ugly little hack for LiveQueries
    void retain() {
        synchronized (getDbLock()) { uncloseable = true; }
    }

    void forceClose() {
        synchronized (getDbLock()) {
            if (c4enum == null) { return; }
            c4enum.close();
            c4enum = null;
        }
    }

    @NonNull
    AbstractQuery getQuery() { return query; }

    int getColumnCount() { return columnNames.size(); }

    @NonNull
    List<String> getColumnNames() { return new ArrayList<>(columnNames.keySet()); }

    int getColumnIndex(@NonNull String name) {
        final Integer idx = columnNames.get(name);
        return (idx == null) ? -1 : idx;
    }

    @Nullable
    ResultSet refresh() throws CouchbaseLiteException {
        Preconditions.assertNotNull(query, "query");

        synchronized (getDbLock()) {
            if (c4enum == null) { return null; }
            try {
                final C4QueryEnumerator newEnum = c4enum.refresh();
                return (newEnum == null) ? null : new ResultSet(query, newEnum, columnNames);
            }
            catch (LiteCoreException e) {
                throw CouchbaseLiteException.convertException(e);
            }
        }
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------

    private Object getDbLock() {
        final AbstractQuery q = query;
        if (q != null) {
            final AbstractDatabase db = q.getDatabase();
            if (db != null) { return db.getDbLock(); }
        }
        throw new IllegalStateException("Could not obtain db lock");
    }
}

