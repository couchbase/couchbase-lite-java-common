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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.listener.ChangeListenerToken;
import com.couchbase.lite.internal.listener.ChangeNotifier;
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
    static final long UPDATE_INTERVAL_MS = 200; // 0.2sec (200ms)

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
    @Nullable
    private ListenerToken dbListenerToken;

    @GuardedBy("lock")
    @Nullable
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
    public String toString() { return "LiveQuery{" + ClassUtils.objId(this) + "," + query + "}"; }

    //---------------------------------------------
    // Implementation of DatabaseChangeListener
    //---------------------------------------------

    @Override
    public void changed(@NonNull DatabaseChange change) { update(UPDATE_INTERVAL_MS); }

    //---------------------------------------------
    // package
    //---------------------------------------------

    /**
     * Adds a change listener.
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    @NonNull
    ListenerToken addChangeListener(@Nullable Executor executor, @NonNull QueryChangeListener listener) {
        final ChangeListenerToken<?> token = changeNotifier.addChangeListener(executor, listener);
        start(false);
        return token;
    }

    void removeChangeListener(@NonNull ListenerToken token) {
        if (changeNotifier.removeChangeListener(token) <= 0) { stop(); }
    }

    /**
     * Starts observing database changes and reports changes in the query result.
     */
    void start(boolean shouldClearResults) {
        final boolean started;

        final AbstractDatabase db = Preconditions.assertNotNull(query.getDatabase(), "Live query database");
        synchronized (db.getDbLock()) {
            // can't have the db closing while a query is starting.
            db.mustBeOpen();

            started = state.compareAndSet(State.STOPPED, State.STARTED);

            if (started) {
                synchronized (lock) { dbListenerToken = db.addActiveLiveQuery(this); }
            }
        }

        // There are two ways that the query might already be running:
        // 1) when adding another listener
        // 2) when the query parameters have changed.
        // In either case we should kick off a new query.
        // In the latter case, though, the current query results are irrelevant and need to be cleared.
        if ((!started) && (shouldClearResults)) { closePrevResults(); }

        update(0);
    }

    void stop() {
        final AbstractDatabase db = query.getDatabase();
        if (db == null) {
            if (State.STOPPED != state.get()) { Log.w(LogDomain.DATABASE, "Null db when stopping LiveQuery"); }
            return;
        }

        synchronized (db.getDbLock()) {
            if (State.STOPPED == state.getAndSet(State.STOPPED)) { return; }
            closeDbListener(db);
        }

        closePrevResults();
    }

    @NonNull
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

    // Runs on the query.database.queryExecutor which is single threaded
    // CAUTION: This is very sensitive code!
    // Several bugs have been discovered in this method.
    @SuppressWarnings("PMD.CloseResource")
    private void refreshResults() {
        final ResultSet prevResults;
        synchronized (lock) { prevResults = previousResults; }

        if (!state.compareAndSet(State.SCHEDULED, State.STARTED)) { return; }

        // Assumes that call to `prevResults.refresh` is safe, even if it has been closed/freed.
        final ResultSet newResults;
        try { newResults = (prevResults == null) ? query.execute() : prevResults.refresh(); }
        catch (CouchbaseLiteException err) {
            changeNotifier.postChange(new QueryChange(query, null, err));
            return;
        }

        Log.i(DOMAIN, "LiveQuery refresh: %s ==> %s", prevResults, newResults);

        // Refresh returns null if there have been no changes
        if (newResults == null) { return; }

        if (prevResults != null) { prevResults.release(); }

        // There is a race here: if client code stops
        // the live query between this line and the next
        // we will leak a result set.  Since the LiveQuery
        // itself is likely to be GCed soon, perhaps that
        // isn't such a big deal.
        if (state.get() == State.STOPPED) { return; }

        // We need this result set.  Don't let the user close it.
        newResults.retain();
        synchronized (lock) { previousResults = newResults; }

        // Listeners may be notified even after the LiveQuery has been stopped.
        // This call to postChange will cause a call to LiveQuery.changed,
        // if there is anybody still listening to the query.
        changeNotifier.postChange(new QueryChange(query, newResults, null));
    }

    @SuppressWarnings("PMD.CloseResource")
    private void closePrevResults() {
        final ResultSet prevResults;
        synchronized (lock) {
            prevResults = previousResults;
            previousResults = null;
        }

        if (prevResults != null) { prevResults.release(); }
    }

    private void closeDbListener(AbstractDatabase db) {
        final ListenerToken token;
        synchronized (lock) {
            token = dbListenerToken;
            dbListenerToken = null;
        }
        if (token == null) { return; }
        db.removeActiveLiveQuery(this, token);
    }
}

