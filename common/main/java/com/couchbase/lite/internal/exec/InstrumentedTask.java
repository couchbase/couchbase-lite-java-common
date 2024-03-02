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
package com.couchbase.lite.internal.exec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.lite.CouchbaseLiteError;


public class InstrumentedTask implements Runnable {
    private static final AtomicLong ID = new AtomicLong(0);


    // Putting a `new Exception()` here is useful but pretty expensive
    @SuppressWarnings("PMD.FinalFieldCouldBeStatic")
    final Exception origin = null; // new Exception("InstrumentedTask submitted at:");

    private final long createdAt = System.currentTimeMillis();
    private final long id;
    @NonNull
    private final Runnable task;

    private volatile long startedAt;
    private volatile long finishedAt;
    private volatile long completedAt;

    @Nullable
    private final Runnable onComplete;

    public InstrumentedTask(@NonNull Runnable task, @Nullable Runnable onComplete) {
        this.task = task;
        this.onComplete = onComplete;
        this.id = ID.incrementAndGet();
    }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void run() {
        synchronized (this) {
            if (startedAt != 0L) {
                throw new CouchbaseLiteError("Attempt to execute a task multiple times");
            }
            startedAt = System.currentTimeMillis();
        }

        try {
            task.run();
            finishedAt = System.currentTimeMillis();
        }
        finally {
            final Runnable completionTask = onComplete;
            if (completionTask != null) { completionTask.run(); }
            completedAt = System.currentTimeMillis();
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "task[#" + id
            + " @" + createdAt
            + "(" + startedAt
            + "<" + finishedAt
            + "<" + completedAt
            + "):" + task + "]";
    }
}
