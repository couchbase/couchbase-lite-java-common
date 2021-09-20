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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * Synchronous safe execution of a client task.
 * Motto: Their failure is not our failure.
 * <p>
 * I think it is ok to permit this executor pool to get very large: it is used only
 * for Core callbacks that run client code and.  There should not, therefore, be
 * more threads here than there are Core threads (the Core thread is suspended
 * until this task completes).  That means that it shouldn't, actually, get all that big.
 *
 * @param <T> type of the value returned by the wrapped task.
 */
public class ClientTask<T> {
    private static final CBLExecutor EXECUTOR
        = new CBLExecutor("Client worker", 128, 128, new SynchronousQueue<>());

    public static void dumpState() {
        EXECUTOR.dumpState();
        AbstractExecutionService.dumpThreads();
    }


    @NonNull
    private final Callable<T> task;

    private T result;
    @Nullable
    private Exception err;

    public ClientTask(@NonNull Callable<T> task) { this.task = task; }

    public void execute() { execute(30, TimeUnit.SECONDS); }

    @SuppressWarnings({"PMD.PreserveStackTrace", "PMD.AvoidThrowingRawExceptionTypes"})
    public void execute(long timeout, @NonNull TimeUnit timeUnit) {
        final FutureTask<T> future = new FutureTask<>(task);
        try { EXECUTOR.execute(new InstrumentedTask(future, null)); }
        catch (RuntimeException e) {
            Log.w(LogDomain.DATABASE, "!!! Catastrophic executor failure (ClientTask)", e);
            if (!AbstractExecutionService.throttled()) { dumpState(); }
            throw e;
        }

        // block until complete or timeout
        try { result = future.get(timeout, timeUnit); }
        catch (InterruptedException | TimeoutException e) { err = e; }
        catch (ExecutionException e) {
            final Throwable t = e.getCause();
            if (!(t instanceof Exception)) { throw new Error("Client task error", t); }
            err = (Exception) t;
        }
    }

    @Nullable
    public T getResult() { return result; }

    @Nullable
    public Exception getFailure() { return err; }
}
