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
package com.couchbase.lite.internal.utils;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;


public interface Fn {
    @FunctionalInterface
    interface FunctionThrows<T, R, E extends Exception> {
        @Nullable
        R apply(@NonNull T x) throws E;
    }

    @FunctionalInterface
    interface Function<T, R> {
        @Nullable
        R apply(@NonNull T x);
    }

    @FunctionalInterface
    interface Provider<T> {
        @Nullable
        T get();
    }

    @FunctionalInterface
    interface ProviderThrows<T, E extends Exception> {
        @Nullable
        T get() throws E;
    }

    @FunctionalInterface
    interface Predicate<T> {
        boolean test(@NonNull T x);
    }

    @FunctionalInterface
    interface NullablePredicate<T> {
        boolean test(@Nullable T x);
    }

    @FunctionalInterface
    interface ConsumerThrows<T, E extends Exception> {
        void accept(@NonNull T x) throws E;
    }

    @FunctionalInterface
    interface Consumer<T> {
        void accept(@NonNull T x);
    }

    @FunctionalInterface
    interface NullableConsumer<T> {
        void accept(@Nullable T x);
    }

    @FunctionalInterface
    interface TaskThrows<E extends Exception> {
        void run() throws E;
    }

    @FunctionalInterface
    interface Runner {
        void run(@NonNull Runnable r);
    }

    @NonNull
    static <T, R, C extends Collection<? extends T>> Collection<R> map(@NonNull C c, @NonNull Function<T, R> fn) {
        final Class<C> klass = (Class<C>) c.getClass();
        final Collection<R> r;
        try { r = (Collection<R>) klass.newInstance(); }
        catch (IllegalAccessException | InstantiationException e) {
            throw new UnsupportedOperationException("Cannot create instance of class: " + klass, e);
        }
        for (T e: c) { r.add(fn.apply(e)); }
        return r;
    }

    @Nullable
    static <T, C extends Collection<? extends T>> T filter(@NonNull C c, @NonNull Predicate<T> pred) {
        for (T e: c) {
            if (pred.test(e)) { return e; }
        }
        return null;
    }
}
