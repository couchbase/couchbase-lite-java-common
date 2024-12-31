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
package com.couchbase.lite.logging;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.logging.AbstractLogSink;


/**
 * A log sink that writes log messages the Android system message buffer.
 * <p>
 * Do not subclass!
 * This class will be final in future version of Couchbase Lite
 */
public class ConsoleLogSink extends AbstractLogSink {
    public ConsoleLogSink(@NonNull LogLevel level, @NonNull LogDomain domain1, @Nullable LogDomain... domains) {
        this(level, aggregateDomains(domain1, domains));
    }

    public ConsoleLogSink(@NonNull LogLevel level, @NonNull Set<LogDomain> domains) {
        super(level, Collections.unmodifiableSet(new HashSet<>(domains)));
    }

    @Override
    protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        final String tag = "CouchbaseLite/" + domain;
        switch (level) {
            case DEBUG:
                Log.d(tag, message);
                break;
            case VERBOSE:
                Log.v(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case WARNING:
                Log.w(tag, message);
                break;
            case ERROR:
                Log.e(tag, message);
                break;
        }
    }
}
