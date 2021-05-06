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
package com.couchbase.lite.internal.exec;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * Base ExecutionService that provides the default implementation of serial and concurrent
 * executor.
 */
public abstract class AbstractExecutionService implements ExecutionService {
    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.DATABASE;
    private static final int DUMP_INTERVAL_MS = 2000; // 2 seconds

    @VisibleForTesting
    public static final int MIN_CAPACITY = 64;


    private static final Object DUMP_LOCK = new Object();

    //---------------------------------------------
    // Class members
    //---------------------------------------------
    private static long lastDump;

    //---------------------------------------------
    // Class methods
    //---------------------------------------------

    // check `throttled()` before calling.
    public static void dumpServiceState(@NonNull Executor ex, @NonNull String msg, @Nullable Exception e) {
        Log.w(LogDomain.DATABASE, "====== Catastrophic failure of executor " + ex + ": " + msg, e);

        final Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        Log.w(DOMAIN, "==== Threads: " + stackTraces.size());
        for (Map.Entry<Thread, StackTraceElement[]> stack: stackTraces.entrySet()) {
            Log.w(DOMAIN, "== Thread: " + stack.getKey());
            for (StackTraceElement frame: stack.getValue()) { Log.w(DOMAIN, "      at " + frame); }
        }

        if (!(ex instanceof ThreadPoolExecutor)) { return; }

        final ArrayList<Runnable> waiting = new ArrayList<>(((ThreadPoolExecutor) ex).getQueue());
        Log.w(DOMAIN, "==== Executor queue: " + waiting.size());
        int n = 0;
        for (Runnable r: waiting) {
            final Exception orig = (!(r instanceof InstrumentedTask)) ? null : ((InstrumentedTask) r).origin;
            Log.w(DOMAIN, "@" + (n++) + ": " + r, orig);
        }
    }

    public static boolean throttled() {
        final long now = System.currentTimeMillis();
        synchronized (DUMP_LOCK) {
            if ((now - lastDump) < DUMP_INTERVAL_MS) { return true; }
            lastDump = now;
        }
        return false;
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


    //---------------------------------------------
    // Package-private methods
    //---------------------------------------------

    @VisibleForTesting
    public void dumpExecutorState() { concurrentExecutor.dumpExecutorState(null, new RejectedExecutionException()); }
}

