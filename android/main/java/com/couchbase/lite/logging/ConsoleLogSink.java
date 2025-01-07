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
import androidx.annotation.VisibleForTesting;

import java.util.Collection;
import java.util.Objects;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.logging.AbstractLogSink;


/**
 * A log sink that writes log messages the Android system message buffer.
 */
public final class ConsoleLogSink extends AbstractLogSink {
    @VisibleForTesting
    @FunctionalInterface
    interface Delegate {
        void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message);
    }

    @Nullable
    private final Delegate testDelegate;

    public ConsoleLogSink(@NonNull LogLevel level) { this(level, null); }

    public ConsoleLogSink(@NonNull LogLevel level, @NonNull LogDomain domain1, @NonNull LogDomain... domains) {
        this(level, aggregateDomains(domain1, domains));
    }

    public ConsoleLogSink(@NonNull LogLevel level, @Nullable Collection<LogDomain> domains) {
        this(level, defaultDomains(domains), null);
    }

    @VisibleForTesting
    ConsoleLogSink(
        @NonNull LogLevel level,
        @Nullable Collection<LogDomain> domains,
        @Nullable Delegate testDelegate) {
        super(level, defaultDomains(domains));
        this.testDelegate = testDelegate;
    }

    @NonNull
    @Override
    public String toString() { return "ConsoleLogSink{" + listDomains(getDomains()) + "@" + getLevel() + "}"; }

    @Override
    public int hashCode() { return Objects.hash(getLevel(), getDomains()); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof ConsoleLogSink)) { return false; }
        final ConsoleLogSink other = (ConsoleLogSink) o;
        return similarLevels(other) && similarDomains(other);
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

        // this is just test scaffolding
        if (testDelegate != null) { testDelegate.writeLog(level, domain, message); }
    }
}
