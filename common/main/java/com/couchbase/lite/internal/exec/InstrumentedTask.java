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
package com.couchbase.lite.internal.exec;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


@VisibleForTesting
class InstrumentedTask implements Runnable {
    // Putting a `new Exception()` here is useful but pretty expensive
    @SuppressWarnings("PMD.FinalFieldCouldBeStatic")
    final Exception origin = null; // new Exception();

    @NonNull
    private final Runnable task;

    private final long createdAt = System.currentTimeMillis();
    private long startedAt;
    private long finishedAt;
    private long completedAt;

    @Nullable
    private volatile Runnable onComplete;

    InstrumentedTask(@NonNull Runnable task) { this(task, null); }

    InstrumentedTask(@NonNull Runnable task, @Nullable Runnable onComplete) {
        this.task = task;
        this.onComplete = onComplete;
    }

    public void setCompletion(@NonNull Runnable onComplete) { this.onComplete = onComplete; }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void run() {
        try {
            startedAt = System.currentTimeMillis();
            task.run();
            finishedAt = System.currentTimeMillis();
        }
        catch (Throwable t) {
            Log.w(
                LogDomain.DATABASE,
                "Uncaught exception on thread " + Thread.currentThread().getName() + " in " + this,
                t);
            throw t;
        }
        finally {
            final Runnable completionTask = onComplete;
            if (completionTask != null) { completionTask.run(); }
        }
        completedAt = System.currentTimeMillis();
    }

    @NonNull
    @Override
    public String toString() {
        return "task[" + createdAt + "," + startedAt + "," + finishedAt + "," + completedAt + " @" + task + "]";
    }
}
