//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.core.C4QueryObserver;
import com.couchbase.lite.internal.core.C4QueryOptions;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.listener.ChangeListenerToken;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


abstract class AbstractQuery implements Query {
    protected static final LogDomain DOMAIN = LogDomain.QUERY;


    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final Map<ChangeListenerToken<QueryChange>, C4QueryObserver> listeners = new HashMap<>();

    private final Object lock = new Object();
    // column names
    @GuardedBy("lock")
    private Map<String, Integer> columnNames;
    @GuardedBy("lock")
    private C4Query c4query;

    @Nullable
    private Parameters parameters;

    /**
     * Returns a copy of the current parameters.
     */
    @Nullable
    @Override
    public Parameters getParameters() { return parameters; }

    /**
     * Set query parameters.
     * Setting new parametera will re-execute a query if there is at least one listener listening for changes.
     *
     * @throws IllegalStateException    on failure to create the query (e.g., database closed)
     * @throws IllegalArgumentException on failure to encode the parameters (e.g., parameter value not supported)
     */
    @Override
    public void setParameters(@Nullable Parameters parameters) {
        synchronized (lock) {
            if (parameters != null) { parameters = parameters.readonlyCopy(); }

            this.parameters = parameters;

            if (parameters == null) { return; }

            try { getC4QueryLocked().setParameters(parameters.encode()); }
            catch (CouchbaseLiteException e) { throw new IllegalStateException("Failed creating query", e); }
            catch (LiteCoreException e) { throw new IllegalArgumentException("Failed encoding parameters", e); }
        }
    }

    /**
     * Executes the query returning a result set that enumerates result rows one at a time.
     * You can run the query any number of times and you can even have multiple ResultSet active at
     * once.
     * <p>
     * The results come from a snapshot of the database taken at the moment the run() method
     * is called, so they will not reflect any changes made to the database afterwards.
     * </p>
     *
     * @return the ResultSet for the query result.
     * @throws CouchbaseLiteException if there is an error when running the query.
     */
    @NonNull
    @Override
    public ResultSet execute() throws CouchbaseLiteException {
        try {
            final C4QueryOptions options = new C4QueryOptions();
            if (parameters == null) { parameters = new Parameters(); }
            final C4QueryEnumerator c4enum;
            final Map<String, Integer> colNames;
            try (FLSliceResult params = parameters.encode()) {
                synchronized (getDbLock()) {
                    synchronized (lock) {
                        c4enum = getC4QueryLocked().run(options, params);
                        colNames = columnNames;
                    }
                }
            }
            return new ResultSet(this, c4enum, new HashMap<>(colNames));
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
    }

    /**
     * Returns a string describing the implementation of the compiled query.
     * This is intended to be read by a developer for purposes of optimizing the query, especially
     * to add database indexes. It's not machine-readable and its format may change.
     * As currently implemented, the result is two or more lines separated by newline characters:
     * <ul>
     * <li> The first line is the SQLite SELECT statement.
     * <li> The subsequent lines are the output of SQLite's "EXPLAIN QUERY PLAN" command applied to that
     * </ul>
     * statement; for help interpreting this, see https://www.sqlite.org/eqp.html . The most
     * important thing to know is that if you see "SCAN TABLE", it means that SQLite is doing a
     * slow linear scan of the documents instead of using an index.
     *
     * @return a string describing the implementation of the compiled query.
     * @throws CouchbaseLiteException if an error occurs
     */
    @NonNull
    @Override
    public String explain() throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            synchronized (lock) {
                final String exp = getC4QueryLocked().explain();
                if (exp == null) { throw new CouchbaseLiteException("Could not explain query"); }
                return exp;
            }
        }
    }

    /**
     * Adds a query change listener. Changes will be posted on the main queue.
     *
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(@NonNull QueryChangeListener listener) {
        return addChangeListener(null, listener);
    }

    /**
     * Adds a query change listener with the dispatch queue on which changes
     * will be posted. If the dispatch queue is not specified, the changes will be
     * posted on the main queue.
     *
     * @param executor The executor object that calls listener. If null, use default executor.
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     * @throws IllegalStateException on failure to create the query (e.g., database closed)
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull QueryChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");

        final ChangeListenerToken<QueryChange> token = new ChangeListenerToken<>(executor, listener);
        final C4QueryObserver queryObserver;
        try {
            queryObserver = C4QueryObserver.create(
                getC4QueryLocked(),
                (results, err) -> onQueryChanged(token, results, err));
        }
        catch (CouchbaseLiteException e) { throw new IllegalStateException("Failed creating query", e); }
        listeners.put(token, queryObserver);

        final ExecutionService exec = CouchbaseLiteInternal.getExecutionService();
        exec.postDelayedOnExecutor(
            10, // !!! 10 ms delay. work around for CBL-2543. There won't be any delay after CBL-2543 is fixed
            executor != null ? executor : exec.getDefaultExecutor(),
            () -> queryObserver.setEnabled(true));

        return token;
    }

    /**
     * Removes a change listener wih the given listener token.
     *
     * @param token The listener token.
     */
    @Override
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        final C4QueryObserver observer = listeners.remove(token);
        if (observer != null) { observer.close(); }
    }

    @Nullable
    protected abstract AbstractDatabase getDatabase();

    @GuardedBy("lock")
    @NonNull
    protected abstract C4Query prepQueryLocked(@NonNull AbstractDatabase db) throws CouchbaseLiteException;

    /**
     * Find out if a query has an observer
     */
    @VisibleForTesting
    boolean isLive(ListenerToken token) { return listeners.get(token) != null; }

    @GuardedBy("lock")
    @NonNull
    private C4Query getC4QueryLocked() throws CouchbaseLiteException {
        if (c4query != null) { return c4query; }

        final AbstractDatabase db = getDatabase();
        if (db == null) { throw new IllegalStateException("Attempt to prep query with no database"); }

        final C4Query c4Q = prepQueryLocked(db);

        final int nCols = c4Q.getColumnCount();
        final Map<String, Integer> colNames = new HashMap<>();
        for (int i = 0; i < nCols; i++) {
            final String colName = c4Q.getColumnNameForIndex(i);
            if (colName == null) { continue; }

            if (colNames.containsKey(colName)) {
                throw new CouchbaseLiteException(
                    Log.formatStandardMessage("DuplicateSelectResultName", colName),
                    CBLError.Domain.CBLITE,
                    CBLError.Code.INVALID_QUERY);
            }

            colNames.put(colName, i);
        }

        columnNames = colNames;

        c4query = c4Q;
        return c4query;
    }

    private void onQueryChanged(
        ChangeListenerToken<QueryChange> token,
        C4QueryEnumerator enumerator,
        LiteCoreException err) {
        token.postChange(new QueryChange(this, new ResultSet(this, enumerator, columnNames), err));
    }

    @NonNull
    private Object getDbLock() {
        final BaseDatabase db = getDatabase();
        if (db != null) { return db.getDbLock(); }
        throw new IllegalStateException("Cannot seize DB lock");
    }
}
