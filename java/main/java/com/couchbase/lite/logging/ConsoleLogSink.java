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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.logging.AbstractLogSink;
import com.couchbase.lite.internal.utils.SystemStream;


/**
 * A class for sending log messages to the console.
 */
public class ConsoleLogSink extends AbstractLogSink {
    public ConsoleLogSink(@NonNull LogLevel level, @NonNull Set<LogDomain> domains) {
        super(level, Collections.unmodifiableSet(new HashSet<>(domains)));
    }

    public ConsoleLogSink(@NonNull LogLevel level, @NonNull LogDomain domain1, @Nullable LogDomain... domains) {
        this(level, aggregateDomains(domain1, domains));
    }

    @Override
    protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        SystemStream.print(level, domain.name(), message, null);
    }
}
