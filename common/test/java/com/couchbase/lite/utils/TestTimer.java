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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;


// I just don't want to go through and annotate every single test with its timeout.
// Getting the @After methods to run after a timeout, though, is a bit of a hack.
public class TestTimer implements MethodRule {
    private final long timeout;
    private final TimeUnit timeUnit;

    public TestTimer(long timeout, TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public Statement apply(Statement statement, FrameworkMethod method, Object target) {
        // Because this will wrap RunAfters, we need to find the after methods and run them, if we timeout
        return new TestTimeout(statement, timeout, timeUnit, target, findAfters(method.getDeclaringClass()));
    }

    // Return the list of after methods, starting at the parameter and in order up the superclasses
    private List<Method> findAfters(Class<?> clazz) {
        List<Method> afters = new ArrayList<>();
        for (Class<?> clazzes: getSuperClasses(clazz)) {
            for (Method method: clazzes.getDeclaredMethods()) {
                if (method.getAnnotation(After.class) != null) { afters.add(method); }
            }
        }
        return Collections.unmodifiableList(afters);
    }

    // Return the list of superclasses, starting at the parameter and in order up the superclasses
    private List<Class<?>> getSuperClasses(Class<?> testClass) {
        List<Class<?>> results = new ArrayList<>();
        Class<?> current = testClass;
        while (current != null) {
            results.add(current);
            current = current.getSuperclass();
        }
        return results;
    }
}

class TestTimeout extends Statement {
    private final Statement statement;
    private final TimeUnit timeUnit;
    private final long timeout;
    private final Object target;
    private final List<Method> afters;

    TestTimeout(Statement statement, long timeout, TimeUnit timeUnit, Object target, List<Method> afters) {
        this.statement = statement;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.target = target;
        this.afters = afters;
    }

    @Override
    public void evaluate() throws Throwable {
        final CyclicBarrier startingGate = new CyclicBarrier(2);

        final FutureTask<Throwable> task = new FutureTask<>(
            () -> {
                try {
                    startingGate.await();
                    statement.evaluate();
                    return null;
                }
                catch (Throwable e) { return e; }
            }
        );

        new Thread(task).start();

        List<Throwable> errors = new ArrayList<>();
        startingGate.await();
        try {
            Throwable e = (timeout <= 0) ? task.get() : task.get(timeout, timeUnit);
            if (e == null) { return; }

            errors.add(e);
        }
        catch (TimeoutException e) {
            errors.add(new TestTimedOutException(timeout, timeUnit));
        }

        for (Method after: afters) {
            try { after.invoke(target); }
            catch (Throwable e) { errors.add(e); }
        }

        // this handles skipped tests (AssumptionViolatedException)
        if (errors.size() == 1) { throw errors.get(0); }

        // I think I have seen this cause a single test to fail multiple times
        // which is weird...
        throw new MultipleFailureException(errors);
    }
}
