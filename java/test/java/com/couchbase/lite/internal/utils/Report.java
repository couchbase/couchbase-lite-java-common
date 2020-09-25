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

import java.io.PrintStream;
import java.util.Locale;

import com.couchbase.lite.LogLevel;


/**
 * Platform console logging utility for tests
 */
public final class Report {
    private Report() {}

    private static final String DOMAIN = "/CouchbaseLite/TEST: ";

    public static void log(LogLevel level, String message) {
        Report.log(level, message, (Throwable) null);
    }

    public static void log(LogLevel level, String template, Object... args) {
        Report.log(level, String.format(Locale.ENGLISH, template, args));
    }

    public static void log(LogLevel level, String message, Throwable err) {
        final String header = level + DOMAIN;
        final PrintStream log = (LogLevel.WARNING.compareTo(level) <= 0) ? System.err : System.out;
        log.println(header + message);
        if (err != null) {
            log.println(header + err.getMessage());
            err.printStackTrace(log);
        }
    }
}