//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


/**
 * A class for sending log messages to the console.
 */
public class ConsoleLogger extends AbstractConsoleLogger {
    private static final String LOG_TAG = "/CouchbaseLite/";
    private static final int THREAD_FIELD_LEN = 7;
    private static final String THREAD_FIELD_PAD = String.join("", Collections.nCopies(THREAD_FIELD_LEN, " "));
    private static final ThreadLocal<DateTimeFormatter> TS_FORMAT
        = ThreadLocal.withInitial(() -> DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS"));

    @NonNull
    public static PrintStream getLogStream(@NonNull LogLevel level) {
        return ((CouchbaseLiteInternal.debugging()) || (LogLevel.WARNING.compareTo(level) > 0))
            ? System.out
            : System.err;
    }

    @NonNull
    public static String formatLog(@NonNull LogLevel level, @NonNull String domain, @NonNull String message) {
        final String tf = THREAD_FIELD_PAD + Thread.currentThread().getId();
        return TS_FORMAT.get().format(LocalDateTime.now())
            + tf.substring(tf.length() - THREAD_FIELD_LEN)
            + " " + level + LOG_TAG + domain + ": "
            + message;
    }


    ConsoleLogger() { }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        super.log(level, domain, message);
    }

    @Override
    protected void doLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        getLogStream(level).println(formatLog(level, domain.name(), message));
    }
}
