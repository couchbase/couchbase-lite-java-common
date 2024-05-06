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

import com.couchbase.lite.internal.CouchbaseLiteInternal;


/**
 * Synchronous safe execution of a client task.
 * Motto: Their failure is not our failure.
 * <p>
 * Design notes:
 * It is necessary to use a Future and an Executor to impose a timeout.
 * If it weren't for that, we could just run the task on the calling thread
 * surrounded by a try/catch block.
 * As of 2024-05-01, all of the tests in the test suite pass with a MAX_POOL_SIZE
 * of 2.  The chosen size is a heuristic: I have been told that LiteCore has a
 * pool of 2 * virtual cores threads.  If that is true then it should not be
 * able to run more that that number of tasks concurrently.
 * After some consideration, I have decided to use a SynchronousQueue in front
 * of the thread pool.  I do not think that there is anything to be gained
 * from queuing tassks.  If there are insufficient resources to run the task,
 * it will be rejected and the caller will have to deal with it.  This decision
 * require review.
 *
 * @param <T> type of the value returned by the wrapped task.
 */
public class ClientTask<T> {
    public static final int MAX_POOL_SIZE = (CBLExecutor.CPU_COUNT * 2) + 1;

    private static final CBLExecutor EXECUTOR
        = new CBLExecutor("Client worker", 0, MAX_POOL_SIZE, new SynchronousQueue<>());


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
            if (CouchbaseLiteInternal.debugging()) { EXECUTOR.dumpState(); }
            setFailure(e);
            return;
        }

        // block until complete or timeout
        // Note that it is quite likely that Future.get will return
        // before the InstrumentedTask's finally clause has been executed.
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
}
