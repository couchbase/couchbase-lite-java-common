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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;


public class CBLExecutor extends ThreadPoolExecutor {
    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    public static final int POOL_SIZE = Math.max(4, CPU_COUNT - 1);

    @NonNull
    private final String name;

    @GuardedBy("name")
    private long n;
    @GuardedBy("name")
    private int qSizeMax;
    @GuardedBy("name")
    private float qSizeMean;
    @GuardedBy("name")
    private float qSizeVariance;
    @GuardedBy("name")
    private float m2;

    // A fixed sized pool backed by a very large queue.
    public CBLExecutor(@NonNull String name) { this(name, POOL_SIZE, POOL_SIZE, new LinkedBlockingQueue<>()); }

    public CBLExecutor(@NonNull String name, int min, int max, @NonNull BlockingQueue<Runnable> workQueue) {
        this(name, min, max, 30, workQueue);  // unused threads die after 30 sec
    }

    public CBLExecutor(
        @NonNull String name,
        int min,
        int max,
        long ttlSecs,
        @NonNull BlockingQueue<Runnable> queue) {
        super(min, max,
            ttlSecs, TimeUnit.SECONDS,
            queue,
            new ThreadFactory() {        // thread factory that gives our threads nice recognizable names
                private final String threadName = name + " #";
                private final AtomicInteger threadId = new AtomicInteger(0);

                @NonNull
                public Thread newThread(@NonNull Runnable r) {
                    final int id = threadId.incrementAndGet();

                    final Thread thread = new Thread(r, threadName + id);
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, e) ->
                        Log.e(LogDomain.DATABASE, "Uncaught exception on thread %s", e, thread.getName()));

                    Log.i(LogDomain.DATABASE, "New thread: %s", thread.getName());
                    return thread;
                }
            });

        allowCoreThreadTimeOut(true);

        this.name = name;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        try { super.execute(task); }
        finally { computeQueueStats(); }
    }

    @NonNull
    @Override
    public String toString() { return "CBLExecutor{" + name + "}"; }

    public void dumpState() {
        final int max;
        final float mean;
        final double variance;
        synchronized (name) {
            max = qSizeMax;
            mean = qSizeMean;
            variance = qSizeVariance;
        }

        Log.w(
            LogDomain.DATABASE,
            "==== CBL Executor \"%s\" (%s)",
            name,
            (isShutdown()) ? "x" : ((isTerminated()) ? "-" : ((isTerminating()) ? "o" : "+")));

        Log.w(LogDomain.DATABASE, "== Tasks: %d, %d", getTaskCount(), getCompletedTaskCount());

        Log.w(
            LogDomain.DATABASE,
            "== Pool: %d, %d, %d, %d",
            getPoolSize(),
            getCorePoolSize(),
            getLargestPoolSize(),
            getMaximumPoolSize());

        final ArrayList<Runnable> waiting = new ArrayList<>(getQueue());
        Log.w(LogDomain.DATABASE, "== Queue: %d, %d, %.2f, %.4f", waiting.size(), max, mean, Math.sqrt(variance));

        int n = 0;
        for (Runnable r: waiting) {
            Log.w(
                LogDomain.DATABASE,
                "@%d: %s",
                (!(r instanceof InstrumentedTask)) ? null : ((InstrumentedTask) r).origin,
                n++,
                r);
        }
    }

    private void computeQueueStats() {
        final int qSize = getQueue().size();
        synchronized (name) {
            if (qSizeMax < qSize) { qSizeMax = qSize; }

            n++;
            final float delta = ((float) qSize) - qSizeMean;
            qSizeMean += delta / n;
            m2 += delta * (((float) qSize) - qSizeMean);
            if (n > 2) { qSizeVariance = m2 / (n - 1); }
        }
    }
}
