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

import java.io.PrintStream;
import java.util.Locale;

import com.couchbase.lite.LogLevel;
import com.couchbase.lite.logging.ConsoleLogger;


/**
 * Platform console logging utility for tests
 */
public final class Report {
    private Report() { }

    private static final String DOMAIN = "TEST";

    public static void log(@NonNull String message) {
        Report.log(LogLevel.INFO, message, (Throwable) null);
    }

    public static void log(@Nullable Throwable err, @NonNull String message) {
        Report.log(LogLevel.INFO, message, err);
    }

    public static void log(@NonNull String template, Object... args) {
        Report.log(LogLevel.INFO, String.format(Locale.ENGLISH, template, args), null);
    }

    public static void log(@Nullable Throwable err, @NonNull String template, Object... args) {
        Report.log(LogLevel.INFO, String.format(Locale.ENGLISH, template, args), err);
    }

    public static void warn(@Nullable Throwable err, @NonNull String message) {
        Report.log(LogLevel.INFO, message, err);
    }

    private static void log(@NonNull LogLevel level, @NonNull String message, @Nullable Throwable err) {
        final PrintStream logStream = ConsoleLogger.getLogStream(level);
        logStream.println(ConsoleLogger.formatLog(level, DOMAIN, message));
        if (err != null) { err.printStackTrace(logStream); }
    }
}
