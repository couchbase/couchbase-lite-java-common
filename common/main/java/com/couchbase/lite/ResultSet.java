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
    private final Object lock = new Object();

    @NonNull
    private final AbstractQuery query;
    @NonNull
    private final Map<String, Integer> columnNames;
    @NonNull
    private final DbContext context;

    @GuardedBy("lock")
    @Nullable
    private C4QueryEnumerator c4enum;

    @GuardedBy("lock")
    private boolean isAllEnumerated;

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

    private ResultSet(@NonNull ResultSet other) {
        this.query = other.query;
        this.columnNames = other.columnNames;
        this.context = other.context;
        synchronized (other.lock) {
            this.c4enum = (other.c4enum == null) ? null : other.c4enum.copy(); // a new ref to the c4enum
            this.isAllEnumerated = other.isAllEnumerated;
        }
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

        String msg;
        LiteCoreException err = null;
        synchronized (lock) {
            try {
                if (c4enum == null) { return null; }
                else if (isAllEnumerated) { msg = "ResultSetAlreadyEnumerated"; }
                else if (!c4enum.next()) {
                    isAllEnumerated = true;
                    msg = "End of query enumeration";
                }
                else { return new Result(this, c4enum, context); }
            }
            catch (LiteCoreException e) {
                msg = "Error enumerating query";
                err = e;
            }
        }

        // Log outside the the synchronized block
        Log.w(DOMAIN, msg, err);
        return null;
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
        final C4QueryEnumerator qEnum;
        synchronized (lock) {
            if (c4enum == null) { return; }
            qEnum = c4enum;
            c4enum = null;
        }
        synchronized (getDbLock()) { qEnum.close(); }
    }

    //---------------------------------------------
    // Protected access
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        try {
            // ??? Hail Mary: no lock, no synchronization...
            if (c4enum != null) { c4enum.close(); }
        }
        finally { super.finalize(); }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @NonNull
    ResultSet copy() { return new ResultSet(this); }

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
        final C4QueryEnumerator newEnum;
        synchronized (getDbLock()) {
            synchronized (lock) {
                if (c4enum == null) { return null; }
                try { newEnum = c4enum.refresh(); }
                catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
            }
        }

        return (newEnum == null) ? null : new ResultSet(query, newEnum, columnNames);
    }


    //---------------------------------------------
    // Private level access
    //---------------------------------------------

    @NonNull
    private Object getDbLock() {
        final AbstractQuery q = query;
        if (q != null) {
            final AbstractDatabase db = q.getDatabase();
            if (db != null) { return db.getDbLock(); }
        }
        throw new IllegalStateException("Could not obtain db lock");
    }
}

