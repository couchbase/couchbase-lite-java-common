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
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A Query subclass that automatically refreshes the result rows every time the database changes.
 * <p>
 * Be careful with the state machine here:
 * A query that has been STOPPED can be STARTED again!
 * In particular, a query that is stopping when it receives a request to restart
 * should suspend the restart request, finish stopping, and then restart.
 */
final class LiveQuery implements DatabaseChangeListener {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.QUERY;

    @VisibleForTesting
    static final long LIVE_QUERY_UPDATE_INTERVAL_MS = 200; // 0.2sec (200ms)

    @VisibleForTesting
    enum State {STOPPED, STARTED, SCHEDULED}

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final Object lock = new Object();

    @NonNull
    private final ChangeNotifier<QueryChange> changeNotifier = new ChangeNotifier<>();

    @NonNull
    private final AbstractQuery query;

    @NonNull
    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

    @GuardedBy("lock")
    private ListenerToken dbListenerToken;

    @GuardedBy("lock")
    private ResultSet previousResults;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    LiveQuery(@NonNull AbstractQuery query) {
        Preconditions.assertNotNull(query, "query");
        this.query = query;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() { return "LiveQuery{" + ClassUtils.objId(this) + "," + query.toString() + "}"; }

    //---------------------------------------------
    // Implementation of DatabaseChangeListener
    //---------------------------------------------

    @Override
    public void changed(@NonNull DatabaseChange change) { update(LIVE_QUERY_UPDATE_INTERVAL_MS); }

    //---------------------------------------------
    // package
    //---------------------------------------------

    /**
     * Adds a change listener.
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    ListenerToken addChangeListener(Executor executor, QueryChangeListener listener) {
        final ChangeListenerToken<?> token = changeNotifier.addChangeListener(executor, listener);
        start(false);
        return token;
    }

    void removeChangeListener(ListenerToken token) {
        if (changeNotifier.removeChangeListener(token) <= 0) { stop(); }
    }

    /**
     * Starts observing database changes and reports changes in the query result.
     */
    void start(boolean shouldClearResults) {
        final AbstractDatabase db = Preconditions.assertNotNull(query.getDatabase(), "Live query database");

        // can't have the db closing while a query is starting.
        synchronized (db.getDbLock()) {
            db.mustBeOpen();

            if (state.compareAndSet(State.STOPPED, State.STARTED)) {
                synchronized (lock) { dbListenerToken = db.addActiveLiveQuery(this); }
            }
            else {
                // Here if the live query was already running.  This can happen in two ways:
                // 1) when adding another listener
                // 2) when the query parameters have changed.
                // In either case we probably want to kick off a new query.
                // In the latter case the current query results are irrelevant and need to be cleared.
                if (shouldClearResults) {
                    synchronized (lock) { closePrevResults(); }
                }
            }
        }
        update(0);
    }

    void stop() {
        final AbstractDatabase db = query.getDatabase();
        if (db == null) {
            if (State.STOPPED != state.get()) {
                Log.w(LogDomain.DATABASE, "Null db when stopping LiveQuery");
            }
            return;
        }

        synchronized (db.getDbLock()) {
            if (State.STOPPED == state.getAndSet(State.STOPPED)) { return; }

            synchronized (lock) {
                closePrevResults();

                final ListenerToken token = dbListenerToken;
                dbListenerToken = null;
                if (token == null) { return; }

                db.removeActiveLiveQuery(this, token);
            }
        }
    }

    State getState() { return state.get(); }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void update(long delay) {
        if (!state.compareAndSet(State.STARTED, State.SCHEDULED)) { return; }
        final AbstractDatabase db = query.getDatabase();
        if (db == null) { throw new IllegalStateException("Live query with no database"); }
        db.scheduleOnQueryExecutor(this::refreshResults, delay);
    }

    // Runs on the query.database.queryExecutor
    // Assumes that call to `previousResults.refresh` is safe, even if previousResults has been freed.
    @SuppressWarnings("PMD.CloseResource")
    private void refreshResults() {
        try {
            final ResultSet prevResults;
            synchronized (lock) {
                if (!state.compareAndSet(State.SCHEDULED, State.STARTED)) { return; }
                prevResults = previousResults;
            }

            final ResultSet newResults;
            if (prevResults == null) { newResults = query.execute(); }
            else {
                newResults = prevResults.refresh();
                previousResults.forceClose();
            }
            Log.i(DOMAIN, "LiveQuery refresh: %s > %s", prevResults, newResults);

            if (newResults == null) { return; }

            newResults.retain();

            boolean update = false;
            synchronized (lock) {
                if (state.get() != State.STOPPED) {
                    previousResults = newResults;
                    update = true;
                }
            }

            // Listeners may be notified even after the LiveQuery has been stopped.
            if (update) { changeNotifier.postChange(new QueryChange(query, newResults, null)); }
        }
        catch (CouchbaseLiteException err) {
            changeNotifier.postChange(new QueryChange(query, null, err));
        }
    }

    @GuardedBy("lock")
    private void closePrevResults() {
        if (previousResults == null) { return; }
        previousResults.forceClose();
        previousResults = null;
    }
}

