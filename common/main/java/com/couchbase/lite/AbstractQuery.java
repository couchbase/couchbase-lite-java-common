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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.core.C4QueryOptions;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.utils.Preconditions;


abstract class AbstractQuery implements Query {
    protected static final LogDomain DOMAIN = LogDomain.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Object lock = new Object();
    // column names
    protected Map<String, Integer> columnNames;
    @GuardedBy("lock")
    private C4Query c4query;
    @GuardedBy("lock")
    private LiveQuery liveQuery;
    // PARAMETERS
    private Parameters parameters;

    /**
     * Returns a copies of the current parameters.
     */
    @Override
    public Parameters getParameters() { return parameters; }

    /**
     * Set parameters should copy the given parameters. Set a new parameter will
     * also re-execute the query if there is at least one listener listening for
     * changes.
     */
    @Override
    public void setParameters(Parameters parameters) {
        final LiveQuery newQuery;
        synchronized (lock) {
            this.parameters = (parameters == null) ? null : parameters.readonlyCopy();
            newQuery = liveQuery;
        }

        // https://github.com/couchbase/couchbase-lite-android/issues/1727
        // Shouldn't call start() method inside the lock to prevent deadlock:
        if (newQuery != null) { newQuery.start(true); }
    }

    /**
     * Executes the query. The returning a result set that enumerates result rows one at a time.
     * You can run the query any number of times, and you can even have multiple ResultSet active at
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
            try (FLSliceResult params = parameters.encode()) {
                synchronized (getDbLock()) {
                    synchronized (lock) {
                        if (c4query == null) { c4query = prepQueryLocked(); }
                        c4enum = c4query.run(options, params);
                    }
                }
            }
            return new ResultSet(this, c4enum, columnNames);
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
     * * The first line is the SQLite SELECT statement.
     * * The subsequent lines are the output of SQLite's "EXPLAIN QUERY PLAN" command applied to that
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
                if (c4query == null) { c4query = prepQueryLocked(); }
                final String exp = c4query.explain();
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
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(Executor executor, @NonNull QueryChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return getLiveQuery().addChangeListener(executor, listener);
    }

    /**
     * Removes a change listener wih the given listener token.
     *
     * @param token The listener token.
     */
    @Override
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        getLiveQuery().removeChangeListener(token);
    }

    protected abstract C4Query prepQueryLocked() throws CouchbaseLiteException;

    @Nullable
    protected abstract AbstractDatabase getDatabase();

    @VisibleForTesting
    LiveQuery getLiveQuery() {
        synchronized (lock) {
            if (liveQuery == null) { liveQuery = new LiveQuery(this); }
            return liveQuery;
        }
    }

    private Object getDbLock() {
        final BaseDatabase db = getDatabase();
        if (db != null) { return db.getDbLock(); }
        throw new IllegalStateException("Cannot seize DB lock");
    }
}
