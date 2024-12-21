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
package com.couchbase.lite.internal.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CouchbaseLiteInternal;


public final class SystemStream {
    private SystemStream() { }

    private static final String LOG_TAG = "/CouchbaseLite/";
    private static final int THREAD_FIELD_LEN = 7;
    private static final String THREAD_FIELD_PAD = String.join("", Collections.nCopies(THREAD_FIELD_LEN, " "));
    private static final ThreadLocal<DateTimeFormatter> TS_FORMAT
        = ThreadLocal.withInitial(() -> DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS"));

    public static void print(
        @NonNull LogLevel level,
        @NonNull String domain,
        @NonNull String message,
        @Nullable Throwable err) {
        final PrintStream logStream = ((CouchbaseLiteInternal.debugging()) || (LogLevel.WARNING.compareTo(level) > 0))
            ? System.out
            : System.err;

        final String tf = THREAD_FIELD_PAD + Thread.currentThread().getId();
        logStream.println(TS_FORMAT.get().format(LocalDateTime.now())
            + tf.substring(tf.length() - THREAD_FIELD_LEN)
            + " " + level + LOG_TAG + domain
            + ": " + message);

        if (err != null) { err.printStackTrace(logStream); }
    }
}
