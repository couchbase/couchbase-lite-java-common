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


public final class Preconditions {

    private Preconditions() { }

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

    public static long assertUnsigned(long n, @NonNull String name) {
        if ((n < 0) || (n >= (1L << 32))) {
            throw new IllegalArgumentException(name + " must be 0 <= " + n + " < 2^32");
        }
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

    @NonNull
    public static char[] assertNotEmpty(@Nullable char[] str, @NonNull String name) {
        if ((str == null) || (str.length <= 0)) { throw new IllegalArgumentException(name + " must not be empty"); }
        return str;
    }

    public static void assertThat(boolean condition, @NonNull String msg) {
        if (!condition) { throw new IllegalArgumentException(msg); }
    }

    @Nullable
    public static <T> T assertThat(@Nullable T obj, @NonNull String msg, @NonNull Fn.NullablePredicate<T> predicate) {
        if (!predicate.test(obj)) { throw new IllegalArgumentException(msg); }
        return obj;
    }
}
