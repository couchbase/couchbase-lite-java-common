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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This executor schedules tasks on an underlying thread pool executor
 * <br>
 */
class ConcurrentExecutor implements ExecutionService.CloseableExecutor {
    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    @NonNull
    private final ThreadPoolExecutor executor;

    // a non-null stop latch is the flag that this executor has been stopped
    @GuardedBy("this")
    @Nullable
    private CountDownLatch stopLatch;

    // "running" includes tasks that are not actually running
    // but are enqueued to run on the underlying executor
    @GuardedBy("this")
    private int running;

    ConcurrentExecutor(@NonNull ThreadPoolExecutor executor) {
        Preconditions.assertNotNull(executor, "executor");
        this.executor = executor;
    }

    /**
     * Schedule a task for concurrent execution.
     * There are absolutely no guarantees about execution order, on this executor.
     *
     * @param task a task for concurrent execution.
     * @throws ExecutorClosedException    if the executor has been stopped
     * @throws RejectedExecutionException if the underlying executor rejects the task
     */
    @Override
    public void execute(@NonNull Runnable task) {
        Preconditions.assertNotNull(task, "task");
        synchronized (this) {
            if (stopLatch != null) { throw new ExecutorClosedException("Executor has been stopped"); }
            executeTask(new InstrumentedTask(task, this::finishTask));
        }
    }

    /**
     * Stop the executor.
     * If this call returns false, the executor has *not* yet stopped: tasks it scheduled are still running.
     *
     * @param timeout time to wait for shutdown
     * @param unit    time unit for shutdown wait
     * @return true if all currently scheduled tasks have completed
     */
    @Override
    public boolean stop(long timeout, @NonNull TimeUnit unit) {
        Preconditions.assertNotNegative(timeout, "timeout");
        Preconditions.assertNotNull(unit, "time unit");

        final CountDownLatch latch;
        synchronized (this) {
            if (stopLatch == null) { stopLatch = new CountDownLatch(1); }
            if (running <= 0) { return true; }
            latch = stopLatch;
        }

        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }

        return false;
    }

    @NonNull
    @Override
    public String toString() { return "CBL concurrent executor"; }

    public void dumpState(@Nullable InstrumentedTask current, @Nullable Exception e) {
        if (AbstractExecutionService.throttled()) { return; }

        AbstractExecutionService.dumpState(executor, toString(), e);

        Log.w(DOMAIN, "==== Executor");

        if (current != null) { Log.w(DOMAIN, "== Rejected task: " + current, current.origin); }

        final int nowRunning;
        synchronized (this) { nowRunning = running; }
        Log.w(DOMAIN, "== Tasks: " + nowRunning);
    }

    void finishTask() {
        final CountDownLatch latch;
        synchronized (this) {
            if (--running > 0) { return; }
            latch = stopLatch;
        }

        if (latch != null) { latch.countDown(); }
    }

    @GuardedBy("this")
    private void executeTask(@NonNull InstrumentedTask newTask) {
        try {
            executor.execute(newTask);
            running++;
        }
        catch (RuntimeException e) {
            dumpState(newTask, e);
            throw e;
        }
    }
}
