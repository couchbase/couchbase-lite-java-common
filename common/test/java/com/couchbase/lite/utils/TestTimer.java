//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.utils;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

// Unfortunately, I don't see a way, right offhand, to run the
// @After methods the the test times out.
public class TestTimer implements TestRule {
    public static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private final long timeout;
    private final TimeUnit timeUnit;

    public TestTimer(long timeout, TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    public Statement apply(Statement statement, Description description) {
        return new TestTimeout(statement, timeout, timeUnit);
    }
}

class TestTimeout extends Statement {
    private final Statement statement;
    private final TimeUnit timeUnit;
    private final long timeout;

    TestTimeout(Statement statement, long timeout, TimeUnit timeUnit) {
        this.statement = statement;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public void evaluate() throws Throwable {
        final CyclicBarrier startingGate = new CyclicBarrier(2);

        final FutureTask<Throwable> task = new FutureTask<>(
            () -> {
                try {
                    startingGate.await();
                    statement.evaluate();
                 }
                catch (Throwable e) { return e; }
                return null;
            }
        );

        TestTimer.EXECUTOR.execute(task);

        startingGate.await();
        try {
            Throwable e = (timeout <= 0) ? task.get() : task.get(timeout, timeUnit);
            if (e != null) { throw e; }
        }
        catch (ExecutionException e) {
            final Throwable ee = e.getCause();
            throw (ee == null)? e : ee;
        }
        catch (TimeoutException e) { throw new TestTimedOutException(timeout, timeUnit); }
    }
}
