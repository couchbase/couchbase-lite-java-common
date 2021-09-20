//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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


import androidx.annotation.Nullable;


public interface Fn {
    @FunctionalInterface
    interface FunctionThrows<T, R, E extends Throwable> {
        @Nullable
        R apply(@Nullable T x) throws E;
    }

    @FunctionalInterface
    interface Function<T, R> {
        @Nullable
        R apply(@Nullable T x);
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
        boolean test(@Nullable T x);
    }

    @FunctionalInterface
    interface ConsumerThrows<T, E extends Exception> {
        void accept(@Nullable T x) throws E;
    }

    @FunctionalInterface
    interface Consumer<T> {
        void accept(@Nullable T x);
    }

    @FunctionalInterface
    interface TaskThrows<E extends Exception> {
        void run() throws E;
    }

    @FunctionalInterface
    interface Runner {
        void run(@Nullable Runnable r);
    }
}
