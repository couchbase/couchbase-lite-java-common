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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Serial execution, patterned after AsyncTask's executor.
 * Tasks are queued on an unbounded queue and executed one at a time
 * on an underlying executor: the head of the queue is the currently running task.
 * Since this executor can have at most two tasks scheduled on the underlying
 * executor, ensuring space on that executor makes it unlikely that
 * a serial executor will refuse a task for execution.
 */
class SerialExecutor implements ExecutionService.CloseableExecutor {
    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    @NonNull
    private final ThreadPoolExecutor executor;

    @GuardedBy("this")
    @NonNull
    private final Queue<InstrumentedTask> pendingTasks = new LinkedList<>();

    // a non-null stop latch is the flag that this executor has been stopped
    @GuardedBy("this")
    @Nullable
    private CountDownLatch stopLatch;

    @GuardedBy("this")
    private boolean needsRestart;

    SerialExecutor(@NonNull ThreadPoolExecutor executor) {
        Preconditions.assertNotNull(executor, "executor");
        this.executor = executor;
    }

    /**
     * Schedule a task for in-order execution.
     *
     * @param task a task to be executed after all currently pending tasks.
     * @throws ExecutorClosedException    if the executor has been stopped
     * @throws RejectedExecutionException if the underlying executor rejects the task
     */
    @Override
    public void execute(@NonNull Runnable task) {
        Preconditions.assertNotNull(task, "task");

        synchronized (this) {
            if (stopLatch != null) { throw new ExecutorClosedException("Executor has been stopped"); }

            pendingTasks.add(new InstrumentedTask(task, this::scheduleNext));

            if (needsRestart || (pendingTasks.size() == 1)) { executeTask(null); }
        }
    }

    /**
     * Stop the executor.
     * If this call returns false, the executor has *not* yet stopped.
     * It will continue to run tasks from its queue until all have completed.
     *
     * @param timeout time to wait for shutdown
     * @param unit    time unit for shutdown wait
     * @return true if all currently scheduled tasks completed before the shutdown
     */
    @Override
    public boolean stop(long timeout, @NonNull TimeUnit unit) {
        Preconditions.assertNotNegative(timeout, "timeout");
        Preconditions.assertNotNull(unit, "time unit");

        final CountDownLatch latch;
        synchronized (this) {
            if (stopLatch == null) { stopLatch = new CountDownLatch(1); }
            if (pendingTasks.size() <= 0) { return true; }
            latch = stopLatch;
        }

        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }

        return false;
    }

    // Called on completion of the task at the head of the pending queue.
    private void scheduleNext() {
        final CountDownLatch latch;
        synchronized (this) {
            executeTask(pendingTasks.remove());
            latch = (pendingTasks.size() > 0) ? null : stopLatch;
        }

        if (latch != null) { latch.countDown(); }
    }

    @GuardedBy("this")
    private void executeTask(@Nullable InstrumentedTask prevTask) {
        final InstrumentedTask nextTask = pendingTasks.peek();
        if (nextTask == null) { return; }
        try {
            executor.execute(nextTask);
            needsRestart = false;
        }
        catch (RejectedExecutionException e) {
            needsRestart = true;
            dumpExecutorState(e, prevTask);
        }
    }

    private void dumpExecutorState(@NonNull RejectedExecutionException ex, @Nullable InstrumentedTask prev) {
        if (AbstractExecutionService.throttled()) { return; }

        AbstractExecutionService.dumpServiceState(executor, "size: " + pendingTasks.size(), ex);

        Log.w(DOMAIN, "==== Serial Executor status: " + this);
        if (needsRestart) { Log.w(DOMAIN, "= stalled"); }

        if (prev != null) { Log.w(DOMAIN, "== Previous task: " + prev, prev.origin); }

        if (pendingTasks.isEmpty()) { Log.w(DOMAIN, "== Queue is empty"); }
        else {
            final ArrayList<InstrumentedTask> waiting = new ArrayList<>(pendingTasks);

            final InstrumentedTask current = waiting.remove(0);
            Log.w(DOMAIN, "== Current task: " + current, current.origin);

            Log.w(DOMAIN, "== Pending tasks: " + waiting.size());
            int n = 0;
            for (InstrumentedTask t: waiting) {
                Log.w(
                    DOMAIN,
                    "@" + (++n) + ": " + t,
                    t.origin);
            }
        }
    }
}
