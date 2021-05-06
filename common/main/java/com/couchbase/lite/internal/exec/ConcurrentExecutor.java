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
 * This executor schedules tasks on an underlying thread pool executor
 * (probably some application-wide executor: the Async Task's on Android).
 * <br>
 * If the underlying executor is low on resources, this executor reverts
 * to serial execution, using an unbounded pending queue.
 * <br>
 * If the executor is stopped while there are unscheduled pending tasks
 * (in the pendingTask queue), all of those tasks are simply discarded.
 * If the pendingTask queue is non-empty, either the head task is scheduled
 * or <code>needsRestart</code> is true (see below) .
 * <br>
 * Soft resource exhaustion, <code>spaceAvailable</code>is intended to make it
 * unlikely that this executor ever encounters a <code>RejectedExecutionException</code>.
 * There are two circumstances under which a <code>RejectedExecutionException</code>
 * is possible:
 * <nl>
 * <li> The underlying executor rejects the execution of a new task, even though
 * <code>spaceAvailable</code> returns true.  This exception will be passed back
 * to client code.
 * <li> A task on the pending queue attempts to schedule the next task from the queue
 * for execution.  When this happens, the queue is stalled and <code>needsRestart</code>
 * is set true.  Subsequent calls to <code>execute</code> will make a best-effort attempt
 * to restart the queue.
 * </nl>
 */
class ConcurrentExecutor implements ExecutionService.CloseableExecutor {
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
    private int running;

    @GuardedBy("this")
    private boolean needsRestart;

    ConcurrentExecutor(@NonNull ThreadPoolExecutor executor) {
        Preconditions.assertNotNull(executor, "executor");
        this.executor = executor;
    }

    /**
     * Schedule a task for concurrent execution.
     * There are absolutely no guarantees about execution order, on this executor,
     * particularly once it fails back to using the pending task queue.
     * If there is insufficient room to schedule the task, safely, on the underlying
     * executor, the task is added to the pendingTask queue and executed when space
     * is available.
     * This method may throw a <code>RejectedExecutionException</code> if the underlying
     * executor's resources are completely exhausted even though <code>spaceAvailable</code>
     * returns true.
     *
     * @param task a task for concurrent execution.
     * @throws ExecutorClosedException    if the executor has been stopped
     * @throws RejectedExecutionException if the underlying executor rejects the task
     */
    @Override
    public void execute(@NonNull Runnable task) {
        Preconditions.assertNotNull(task, "task");

        final int pendingTaskCount;
        synchronized (this) {
            if (stopLatch != null) { throw new ExecutorClosedException("Executor has been stopped"); }

            if (spaceAvailable()) {
                if (needsRestart) { restartQueue(); }

                executeTask(new InstrumentedTask(task, this::finishTask));

                return;
            }

            pendingTasks.add(new InstrumentedTask(task));

            pendingTaskCount = pendingTasks.size();
            if (needsRestart || (pendingTaskCount == 1)) { restartQueue(); }
        }

        Log.w(DOMAIN, "Parallel executor overflow: " + pendingTaskCount);
    }

    /**
     * Stop the executor.
     * If there are pending (unscheduled) tasks, they are abandoned.
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
            if (stopLatch == null) {
                pendingTasks.clear();
                stopLatch = new CountDownLatch(1);
            }
            if (running <= 0) { return true; }
            latch = stopLatch;
        }

        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }

        return false;
    }

    void finishTask() {
        final CountDownLatch latch;
        synchronized (this) {
            if (--running > 0) { return; }
            latch = stopLatch;
        }

        if (latch != null) { latch.countDown(); }
    }

    // Called on completion of the task at the head of the pending queue.
    void scheduleNext() {
        synchronized (this) {
            // the executor has been stopped
            if (pendingTasks.size() <= 0) { return; }

            // completing task is head of queue: remove it
            pendingTasks.remove();

            // run as many tasks as possible
            try {
                while (true) {
                    final InstrumentedTask task = pendingTasks.peek();
                    if (task == null) { return; }

                    if (!spaceAvailable()) { break; }

                    task.setCompletion(this::finishTask);
                    executeTask(task);

                    pendingTasks.remove();
                }
            }
            catch (RejectedExecutionException ignore) { }

            // assert: on exiting the loop, head of queue is first unexecutable (soft or hard) task
            // it has not been submitted, successfully, for execution.
            restartQueue();
        }
    }

    // This shouldn't happen.  Checking `spaceAvailable` should guarantee that the
    // underlying executor always has resources when we attempt to execute something.
    void dumpExecutorState(@Nullable InstrumentedTask current, @Nullable RejectedExecutionException ex) {
        if (AbstractExecutionService.throttled()) { return; }

        AbstractExecutionService.dumpServiceState(executor, "size: " + running, ex);

        Log.w(DOMAIN, "==== Concurrent Executor status: " + this);
        if (needsRestart) { Log.w(DOMAIN, "= stalled"); }

        if (current != null) { Log.w(DOMAIN, "== Current task: " + current, current.origin); }

        final ArrayList<InstrumentedTask> waiting = new ArrayList<>(pendingTasks);
        Log.w(DOMAIN, "== Pending tasks: " + waiting.size());
        int n = 0;
        for (InstrumentedTask t: waiting) { Log.w(DOMAIN, "@" + (++n) + ": " + t, t.origin); }
    }

    // assert: queue is not empty.
    private void restartQueue() {
        final InstrumentedTask task = pendingTasks.peek();
        try {
            if (task != null) {
                task.setCompletion(this::scheduleNext);
                executeTask(task);
            }
            needsRestart = false;
            return;
        }
        catch (RejectedExecutionException ignore) { }

        needsRestart = true;
    }

    @GuardedBy("this")
    private void executeTask(@NonNull InstrumentedTask newTask) {
        try {
            executor.execute(newTask);
            running++;
        }
        catch (RejectedExecutionException e) {
            dumpExecutorState(newTask, e);
            throw e;
        }
    }

    // Note that this is only accurate at the moment it is called...
    private boolean spaceAvailable() {
        return executor.getQueue().remainingCapacity() > AbstractExecutionService.MIN_CAPACITY;
    }
}
