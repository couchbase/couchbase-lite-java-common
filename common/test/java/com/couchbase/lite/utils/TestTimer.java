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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;


// I just don't want to go through and annotate every single test with its timeout.
public class TestTimer implements MethodRule {
    static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private final long timeout;
    private final TimeUnit timeUnit;

    public TestTimer(long timeout, TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public Statement apply(Statement statement, FrameworkMethod method, Object target) {
        return new TestTimeout(statement, timeout, timeUnit);
    }
}

class FutureStatement extends FutureTask<Throwable> {
    FutureStatement(Statement statement, CyclicBarrier gate) {
        super(
            () -> {
                gate.await();
                try { statement.evaluate(); }
                catch (Throwable e) { return e; }
                return null;
            }
        );
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
        final CyclicBarrier gate = new CyclicBarrier(2);
        final List<Throwable> errors = new ArrayList<>();

        FutureTask<Throwable> futureTask = new FutureStatement(statement, gate);
        TestTimer.EXECUTOR.execute(futureTask);

        try {
            gate.await();

            Throwable e = (timeout <= 0) ? futureTask.get() : futureTask.get(timeout, timeUnit);
            if (e == null) { return; }

            errors.add(e);
        }
        catch (TimeoutException e) {
            // I am unclear as to why the @After methods get run here
            // I've tried this on several platforms, though, and the do.
            futureTask.cancel(true);
            errors.add(new TestTimedOutException(timeout, timeUnit));
        }
        catch (Exception e) { errors.add(e); }

        // this handles skipped tests (AssumptionViolatedException)
        if (errors.size() == 1) { throw errors.get(0); }

        // I think I have seen this cause a single test to fail multiple times...
        // which is weird...
        throw new MultipleFailureException(errors);
    }
}
