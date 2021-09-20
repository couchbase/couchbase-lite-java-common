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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;


public final class Preconditions {

    private Preconditions() {}

    public static long assertPositive(long n, @NonNull String name) {
        if (n <= 0) { throw new IllegalArgumentException(name + " must be >0"); }
        return n;
    }

    public static int assertPositive(int n, @NonNull String name) {
        if (n <= 0) { throw new IllegalArgumentException(name + " must be >0"); }
        return n;
    }

    public static long assertNegative(long n, @NonNull String name) {
        if (n >= 0) { throw new IllegalArgumentException(name + " must be <0"); }
        return n;
    }

    public static int assertNegative(int n, @NonNull String name) {
        if (n >= 0) { throw new IllegalArgumentException(name + " must be <0"); }
        return n;
    }

    public static long assertNotNegative(long n, @NonNull String name) {
        if (n < 0) { throw new IllegalArgumentException(name + " must be >=0"); }
        return n;
    }

    public static int assertNotNegative(int n, @NonNull String name) {
        if (n < 0) { throw new IllegalArgumentException(name + " must be >=0"); }
        return n;
    }

    public static long assertZero(long n, @NonNull String name) {
        if (n != 0) { throw new IllegalArgumentException(name + " must be 0"); }
        return n;
    }

    public static int assertZero(int n, @NonNull String name) {
        if (n != 0) { throw new IllegalArgumentException(name + " must be 0"); }
        return n;
    }

    public static long assertNotZero(long n, @NonNull String name) {
        if (n == 0) { throw new IllegalArgumentException(name + " must not be 0"); }
        return n;
    }

    public static int assertNotZero(int n, @NonNull String name) {
        if (n == 0) { throw new IllegalArgumentException(name + " must not be 0"); }
        return n;
    }

    @NonNull
    public static <T> T assertNotNull(@Nullable T obj, @NonNull String name) {
        if (obj == null) { throw new IllegalArgumentException(name + " must not be null"); }
        return obj;
    }

    @NonNull
    public static String assertNotEmpty(@Nullable String str, @NonNull String name) {
        if (StringUtils.isEmpty(str)) { throw new IllegalArgumentException(name + " must not be empty"); }
        return str;
    }

    @NonNull
    public static <K extends Collection<T>, T> K assertNotEmpty(@Nullable K obj, @NonNull String name) {
        if ((obj == null) || obj.isEmpty()) { throw new IllegalArgumentException(name + " must not be null or empty"); }
        return obj;
    }

    @Nullable
    public static <T> T assertThat(@Nullable T obj, @NonNull String msg, @NonNull Fn.Predicate<T> predicate) {
        if (!predicate.test(obj)) { throw new IllegalArgumentException(msg); }
        return obj;
    }
}
