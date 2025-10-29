//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.exec.ExecutionService;

enum ConflictResolverState {
    RUNNING,
    STOPPING,
    STOPPED
}

@FunctionalInterface
interface ConflictResolutionCompletion {
    void completed(ConflictResolutionTask task, ReplicatedDocument rDoc);
}

interface ConflictResolutionTaskInterface extends Runnable {
    void onResolved(@Nullable CouchbaseLiteException err);
}

class ConflictResolutionTask implements ConflictResolutionTaskInterface {
    @NonNull
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    @NonNull
    private final Database db;
    @Nullable
    private final ConflictResolver resolver;
    @NonNull
    private final ReplicatedDocument rDoc;
    @NonNull
    private final ConflictResolutionCompletion completion;

    ConflictResolutionTask(
            @NonNull Database db,
            @Nullable ConflictResolver resolver,
            @NonNull ReplicatedDocument rDoc,
            @NonNull ConflictResolutionCompletion completion) {
        this.db = db;
        this.resolver = resolver;
        this.rDoc = rDoc;
        this.completion = completion;
    }

    @Override
    public void run() {
        if (!cancelled.get()) { db.resolveReplicationConflict(resolver, rDoc, this); }
    }

    public void cancel() { cancelled.set(true); }

    public boolean isCancelled() { return cancelled.get(); }

    public void onResolved(@Nullable CouchbaseLiteException err) {
        rDoc.setError(err);
        completion.completed(this, rDoc);
    }
}

class ConflictResolverService {
    @NonNull
    private final ExecutionService.CloseableExecutor concurrentExecutor =
            CouchbaseLiteInternal.getExecutionService().getConcurrentExecutor();

    @NonNull
    private final Object lock = new Object();

    @Nullable
    @GuardedBy("lock")
    private Runnable pendingShutdownCompletion;

    @GuardedBy("lock)")
    @NonNull
    private final Set<ConflictResolutionTask> pendingResolutions = new HashSet<>();

    @GuardedBy("lock")
    private ConflictResolverState state = ConflictResolverState.RUNNING;

    public boolean shutdown(boolean wait, @NonNull Runnable onFinished) {
        Set<ConflictResolutionTask> tasksToCancel = new HashSet<>();
        synchronized (lock) {
            if (state != ConflictResolverState.RUNNING) {
                return false;
            }

            if (pendingResolutions.isEmpty()) {
                state = ConflictResolverState.STOPPED;
                concurrentExecutor.execute(onFinished);
                return true;
            }

            state = ConflictResolverState.STOPPING;
            pendingShutdownCompletion = onFinished;
            if (!wait) {
                tasksToCancel = new HashSet<>(pendingResolutions);
            }
        }

        for (ConflictResolutionTask task : tasksToCancel) {
            task.cancel();
        }

        return true;
    }

    @SuppressWarnings("checkstyle:CheckFunctionalParameters")
    public void addConflict(
            @NonNull ReplicatedDocument doc,
            @NonNull Database database,
            @Nullable ConflictResolver resolver,
            @NonNull ConflictResolutionCompletion onFinished) {
        synchronized (lock) {
            final ConflictResolutionTask  resolutionTask =
                    new ConflictResolutionTask(database, resolver, doc, (t, d) -> {
                        onFinished.completed(t, d);
                        removePendingTask(t);
                    });
            if (state != ConflictResolverState.RUNNING) {
                resolutionTask.cancel();
                onFinished.completed(resolutionTask, doc);
                return;
            }

            pendingResolutions.add(resolutionTask);
            concurrentExecutor.execute(resolutionTask);
        }
    }

    public boolean hasPendingResolutions() {
        synchronized (lock) {
            return !pendingResolutions.isEmpty();
        }
    }

    public boolean isActive() {
        synchronized (lock) {
            return state != ConflictResolverState.STOPPED;
        }
    }

    private void removePendingTask(ConflictResolutionTask task) {
        synchronized (lock) {
            pendingResolutions.remove(task);
            if (state == ConflictResolverState.STOPPING && pendingResolutions.isEmpty()) {
                state = ConflictResolverState.STOPPED;
                if (pendingShutdownCompletion != null) {
                    pendingShutdownCompletion.run();
                }
            }
        }
    }
}
