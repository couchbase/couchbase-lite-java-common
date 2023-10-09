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
import androidx.annotation.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Base ExecutionService that provides the default implementation of serial and concurrent
 * executor.
 */
public abstract class AbstractExecutionService implements ExecutionService {
    //---------------------------------------------
    // Constants
    //---------------------------------------------

    // Don't dump stats more than once every two seconds
    private static final int DUMP_INTERVAL_MS = 2000;

    private static final Object DUMP_LOCK = new Object();

    //---------------------------------------------
    // Class members
    //---------------------------------------------
    private static long lastDump;

    //---------------------------------------------
    // Class methods
    //---------------------------------------------

    public static boolean throttled() {
        final long now = System.currentTimeMillis();
        synchronized (DUMP_LOCK) {
            if ((now - lastDump) < DUMP_INTERVAL_MS) { return true; }
            lastDump = now;
        }
        return false;
    }

    // check `throttled()` before calling.
    public static void dumpThreads() {
        final Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        if (stackTraces.isEmpty()) { return; }

        final Thread curThread = Thread.currentThread();

        Log.w(LogDomain.DATABASE, "==== Threads: " + stackTraces.size());
        for (Map.Entry<Thread, StackTraceElement[]> stack: stackTraces.entrySet()) {
            final Thread thread = stack.getKey();
            Log.w(
                LogDomain.DATABASE,
                ((thread.equals(curThread) ? "**" : "==")) + " " + thread + "(" + thread.getState() + ")");
            for (StackTraceElement frame: stack.getValue()) { Log.w(LogDomain.DATABASE, "      at " + frame); }
        }
    }


    //---------------------------------------------
    // Instance members
    //---------------------------------------------
    @NonNull
    private final ThreadPoolExecutor baseExecutor;
    @NonNull
    private final ConcurrentExecutor concurrentExecutor;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    protected AbstractExecutionService(@NonNull ThreadPoolExecutor baseExecutor) {
        this.baseExecutor = baseExecutor;
        concurrentExecutor = new ConcurrentExecutor(baseExecutor);
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------
    @NonNull
    @Override
    public CloseableExecutor getSerialExecutor() { return new SerialExecutor(baseExecutor); }

    @NonNull
    @Override
    public CloseableExecutor getConcurrentExecutor() { return concurrentExecutor; }

    @Override
    public void cancelDelayedTask(@NonNull Cancellable cancellableTask) {
        Preconditions.assertNotNull(cancellableTask, "cancellableTask");
        cancellableTask.cancel();
    }

    @VisibleForTesting
    public void dumpState() { concurrentExecutor.dumpState(null); }
}

