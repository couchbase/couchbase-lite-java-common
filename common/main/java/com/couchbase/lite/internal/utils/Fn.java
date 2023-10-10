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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public interface Fn {
    @FunctionalInterface
    interface NullableFunction<T, R> {
        @Nullable
        R apply(@NonNull T x);
    }

    @Deprecated
    @FunctionalInterface
    interface Function<T, R> {
        @Nullable
        R apply(@NonNull T x);
    }

    @FunctionalInterface
    interface NonNullFunction<T, R> {
        @NonNull
        R apply(@NonNull T x);
    }

    @FunctionalInterface
    interface NullableFunctionThrows<T, R, E extends Exception> {
        @Nullable
        R apply(@NonNull T x) throws E;
    }

    @FunctionalInterface
    interface NonNullFunctionThrows<T, R, E extends Exception> {
        @NonNull
        R apply(@NonNull T x) throws E;
    }

    @Deprecated
    @FunctionalInterface
    interface FunctionThrows<T, R, E extends Exception> {
        @NonNull
        R apply(@NonNull T x) throws E;
    }

    @FunctionalInterface
    interface BiFunction<T1, T2, R> {
        @Nullable
        R apply(@NonNull T1 x, @NonNull T2 y);
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
    interface LongProviderThrows<E extends Exception> { long get() throws E; }

    @FunctionalInterface
    interface Predicate<T> {
        boolean test(@NonNull T x);
    }

    @FunctionalInterface
    interface NullablePredicate<T> {
        boolean test(@Nullable T x);
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
    interface ConsumerThrows<T, E extends Exception> {
        void accept(@NonNull T x) throws E;
    }

    @FunctionalInterface
    interface TaskThrows<E extends Exception> {
        void run() throws E;
    }

    @FunctionalInterface
    interface Runner {
        void run(@NonNull Runnable r);
    }

    static <T> void forAll(@NonNull Collection<? extends T> c, @NonNull Consumer<T> op) {
        for (T e: c) { op.accept(e); }
    }

    @Nullable
    static <T> T firstOrNull(@NonNull Collection<? extends T> c, @NonNull Predicate<T> pred) {
        for (T e: c) {
            if (pred.test(e)) { return e; }
        }
        return null;
    }

    @Nullable
    static <T, R> R foldR(@NonNull Collection<? extends T> c, @NonNull R init, @NonNull BiFunction<R, T, R> fn) {
        R r = init;
        for (T e: c) { r = fn.apply(r, e); }
        return r;
    }

    @NonNull
    static <T> List<T> filterToList(@NonNull Collection<? extends T> s, @NonNull NullablePredicate<T> pred) {
        final List<T> r = new ArrayList<>(s.size());
        for (T e: s) {
            if (pred.test(e)) { r.add(e); }
        }
        return r;
    }

    @NonNull
    static <T> Set<T> filterToSet(@NonNull Collection<? extends T> s, @NonNull Predicate<T> pred) {
        final Set<T> r = new HashSet<>(s.size());
        for (T e: s) {
            if (pred.test(e)) { r.add(e); }
        }
        return r;
    }

    @NonNull
    static <T, R, E extends Exception> List<R> mapToList(
        @NonNull Collection<? extends T> l,
        @NonNull NonNullFunctionThrows<T, R, E> fn)
        throws E {
        final List<R> r = new ArrayList<>(l.size());
        for (T e: l) { r.add(fn.apply(e)); }
        return r;
    }

    @NonNull
    static <T, R, E extends Exception> Set<R> mapToSet(
        @NonNull Collection<? extends T> s,
        @NonNull NonNullFunctionThrows<T, R, E> fn)
        throws E {
        final Set<R> r = new HashSet<>(s.size());
        for (T e: s) { r.add(fn.apply(e)); }
        return r;
    }
}
