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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.AbstractExecutionService;
import com.couchbase.lite.internal.support.Log;


// ??? Kludge to keep changes minimal, until v 3.x
public class CBLExecutor extends ThreadPoolExecutor {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int POOL_SIZE = Math.max(4, CPU_COUNT - 1);

    private final String name;

    public CBLExecutor() {
        this("CBL Worker", POOL_SIZE, POOL_SIZE, new LinkedBlockingQueue<>(AbstractExecutionService.MIN_CAPACITY * 2));
    }

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
                    thread.setUncaughtExceptionHandler((t, e) ->
                        Log.w(LogDomain.DATABASE, "Uncaught exception on thread" + thread.getName(), e));
                    return thread;
                }
            });

        allowCoreThreadTimeOut(true);

        this.name = name;
    }

    @NonNull
    @Override
    public String toString() { return "CBLExecutor(" + name + "}"; }
}
