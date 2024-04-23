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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;


/**
 * Synchronous safe execution of a client task.
 * Motto: Their failure is not our failure.
 *
 * @param <T> type of the value returned by the wrapped task.
 */
public class ClientTask<T> {
    private static final CBLExecutor EXECUTOR
        = new CBLExecutor("Client worker", 1, CBLExecutor.CPU_COUNT * 2 + 1, new SynchronousQueue<>());

    @NonNull
    private final Callable<T> task;

    @Nullable
    private T result;
    @Nullable
    private Exception err;

    public ClientTask(@NonNull Callable<T> task) { this.task = task; }

    public void execute() { execute(30, TimeUnit.SECONDS); }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public void execute(long timeout, @NonNull TimeUnit timeUnit) {
        final FutureTask<T> future = new FutureTask<>(task);
        try { EXECUTOR.execute(new InstrumentedTask(future, null)); }
        catch (Throwable e) {
            dumpState();
            setFailure(e);
            return;
        }

        // block until complete or timeout
        try { result = future.get(timeout, timeUnit); }
        catch (ExecutionException e) { setFailure(e.getCause()); }
        catch (Throwable e) { setFailure(e); }
    }

    @Nullable
    public T getResult() { return result; }

    @Nullable
    public Exception getFailure() { return err; }

    private void setFailure(@Nullable Throwable t) {
        if ((t instanceof Error)) { throw (Error) t; }
        if ((t == null) || (err != null)) { return; }
        err = (Exception) t;
    }

    public void dumpState() {
        if (AbstractExecutionService.throttled()) { return; }
        EXECUTOR.dumpState();
        AbstractExecutionService.dumpThreads();
    }
}
