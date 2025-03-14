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

import java.util.Collection;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.logging.AbstractLogSink;


/**
 * Base class for Custom log sinks.
 * Override the constructors and implement the writeLog method to send logs to a custom destination.
 * Only logs that match the filter level and domain will be passed to the writeLog method.
 */
public abstract class BaseLogSink extends AbstractLogSink {
    public BaseLogSink(@NonNull LogLevel level) { this(level, (Collection<LogDomain>) null); }

    public BaseLogSink(@NonNull LogLevel level, @NonNull LogDomain domain1, @Nullable LogDomain... domains) {
        this(level, aggregateDomains(domain1, domains));
    }

    protected BaseLogSink(@NonNull LogLevel level, @Nullable Collection<LogDomain> domains) {
        super(level, defaultDomains(domains));
    }
}
