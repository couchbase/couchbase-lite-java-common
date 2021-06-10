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
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.exec.CBLExecutor;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * ExecutionService for Java.
 */
public class JavaExecutionService extends AbstractExecutionService {
    //---------------------------------------------
    // Types
    //---------------------------------------------
    private static final class CancellableTask implements Cancellable {
        private final Future<?> future;

        private CancellableTask(@NonNull Future<?> future) {
            Preconditions.assertNotNull(future, "future");
            this.future = future;
        }

        @Override
        public void cancel() { future.cancel(false); }
    }


    //---------------------------------------------
    // Instance variables
    //---------------------------------------------
    private final Executor defaultExecutor;
    private final ScheduledExecutorService scheduler;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    public JavaExecutionService() { this(new CBLExecutor("CBL worker")); }

    @VisibleForTesting
    public JavaExecutionService(@NonNull ThreadPoolExecutor executor) {
        super(executor);
        defaultExecutor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------
    @NonNull
    @Override
    public Executor getDefaultExecutor() { return defaultExecutor; }

    @NonNull
    @Override
    public Cancellable postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task) {
        Preconditions.assertNotNull(executor, "executor");
        Preconditions.assertNotNull(task, "task");

        final Runnable delayedTask = () -> {
            try { executor.execute(task); }
            catch (RejectedExecutionException e) {
                if (!throttled()) { dumpState(executor, "after: " + delayMs, e); }
            }
        };

        final Future<?> future = scheduler.schedule(delayedTask, delayMs, TimeUnit.MILLISECONDS);
        return new CancellableTask(future);
    }
}
