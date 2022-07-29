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

package com.couchbase.lite;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The representation of a query result. The result set is an iterator over
 * {@code Result} objects.
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

    // This object is the sole owner of the c4enum passed as the second argument.
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
     * <p>Caution:  {@link ResultSet#next}, {@link ResultSet#iterator} and {@link ResultSet#iterator}
     * method share same data structure. They cannot be used together.</p>
     * <p>Caution: When a ResultSet is obtained from a QueryChangeListener and the QueryChangeListener is
     * removed from Query, the ResultSet will be freed and this method will return null.</p>
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
     * Return a List of all Results.
     * <p>Caution:  {@link ResultSet#next}, {@link ResultSet#iterator} and {@link ResultSet#iterator}
     * method share same data structure. They cannot be used together.</p>
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
     * <p>Caution:  {@link ResultSet#next}, {@link ResultSet#iterator} and {@link ResultSet#iterator}
     * method share same data structure. They cannot be used together.</p>
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
    // Package level access
    //---------------------------------------------

    @NonNull
    AbstractQuery getQuery() { return query; }

    int getColumnCount() { return columnNames.size(); }

    @NonNull
    List<String> getColumnNames() { return new ArrayList<>(columnNames.keySet()); }

    int getColumnIndex(@NonNull String name) {
        final Integer idx = columnNames.get(name);
        return (idx == null) ? -1 : idx;
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

