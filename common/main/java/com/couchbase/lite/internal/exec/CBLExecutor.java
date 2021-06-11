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
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


public class CBLExecutor extends ThreadPoolExecutor {
    private final String name;

    private long n;
    private int qSizeMax;
    private float qSizeMean;
    private float qSizeVariance;
    private float m2;

    public CBLExecutor(@NonNull String name) { this(name, 2, 6, new LinkedBlockingQueue<>()); }

    @VisibleForTesting
    public CBLExecutor(@NonNull String name, int min, int max, @NonNull BlockingQueue<Runnable> workQueue) {
        super(min, max,
            30, TimeUnit.SECONDS,        // unused threads die after 30 sec
            workQueue,
            new ThreadFactory() {        // thread factory that gives our threads nice recognizable names
                private final String threadName = name + " #";
                private final AtomicInteger threadId = new AtomicInteger(0);

                public Thread newThread(@NonNull Runnable r) {
                    final Thread thread = new Thread(r, threadName + threadId.incrementAndGet());

                    // InstrumentedTask should prevent this from ever happening
                    thread.setUncaughtExceptionHandler((t, e) ->
                        Log.w(LogDomain.DATABASE, "Uncaught exception on thread" + thread.getName(), e));

                    return thread;
                }
            });

        allowCoreThreadTimeOut(true);

        this.name = name;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        super.execute(task);

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

    @NonNull
    @Override
    public String toString() { return "CBLExecutor(" + name + "}"; }

    public void dumpState() {
        final int max;
        final float mean;
        final double stdDev;
        synchronized (name) {
            max = qSizeMax;
            mean = qSizeMean;
            stdDev = Math.sqrt(qSizeVariance);
        }

        Log.w(LogDomain.DATABASE, "==== Executor %s: %s, %s, %s", name, isTerminating(), isTerminated(), isShutdown());

        Log.w(LogDomain.DATABASE, "== Tasks: %d, %d", getTaskCount(), getCompletedTaskCount());

        Log.w(LogDomain.DATABASE, "== Pool: %d, %d", getPoolSize(), getLargestPoolSize());

        final ArrayList<Runnable> waiting = new ArrayList<>(getQueue());
        if (waiting.isEmpty()) { return; }

        Log.w(LogDomain.DATABASE, "== Queue: %d, %d, %.2f, %.4f", waiting.size(), max, mean, stdDev);
        int n = 0;
        for (Runnable r: waiting) {
            final Exception orig = (!(r instanceof InstrumentedTask)) ? null : ((InstrumentedTask) r).origin;
            Log.w(LogDomain.DATABASE, "@" + (n++) + ": " + r, orig);
        }
    }
}
