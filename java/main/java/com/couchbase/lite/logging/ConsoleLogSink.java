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
package com.couchbase.lite.logging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.logging.AbstractLogSink;
import com.couchbase.lite.internal.utils.SystemStream;


/**
 * A log sink that writes log messages the system console.
 * <p>
 * Do not subclass!
 * This class will be final in future versions of Couchbase Lite
 */
public class ConsoleLogSink extends AbstractLogSink {
    public ConsoleLogSink(@NonNull LogLevel level) { this(level, (Collection<LogDomain>) null); }

    public ConsoleLogSink(@NonNull LogLevel level, @NonNull LogDomain... domains) {
        this(level, Arrays.asList(domains));
    }

    public ConsoleLogSink(@NonNull LogLevel level, @Nullable Collection<LogDomain> domains) {
        super(level, defaultDomains(domains));
    }

    @NonNull
    @Override
    public final String toString() {
        return "ConsoleLogSink{" + ((isLegacy()) ? "!" : "") + listDomains(getDomains()) + "@" + getLevel() + "}";
    }

    @Override
    public final int hashCode() { return Objects.hash(getLevel(), getDomains()); }

    @Override
    public final boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof ConsoleLogSink)) { return false; }
        final ConsoleLogSink other = (ConsoleLogSink) o;
        return similarLevels(other) && similarDomains(other);
    }

    @Override
    protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        SystemStream.print(level, domain.name(), message, null);
    }
}
