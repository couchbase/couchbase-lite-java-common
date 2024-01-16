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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;


/**
 * Platform console logging utility for tests
 */
public final class Report {
    private Report() { }

    private static final String DOMAIN = "CouchbaseLite/TEST";

    public static void log(@NonNull String message) { Log.i(DOMAIN, message); }

    public static void log(@Nullable Throwable err, @NonNull String message) {
        Log.i(DOMAIN, message, err);
    }

    public static void log(@NonNull String template, Object... args) {
        Log.i(DOMAIN, String.format(Locale.ENGLISH, template, args), null);
    }

    public static void log(@Nullable Throwable err, @NonNull String template, Object... args) {
        Log.i(DOMAIN, String.format(Locale.ENGLISH, template, args), err);
    }

    public static void warn(@Nullable Throwable err, @NonNull String message) { Log.w(DOMAIN, message, err); }
}
