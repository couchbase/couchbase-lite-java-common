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
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.EnumSet;


/**
 * The base for Console and Custom loggers.
 */
abstract class BaseLogger {
    private final LogLevel logLevel;
    private final EnumSet<LogDomain> logDomains;

    protected BaseLogger(@NonNull LogLevel level) { this(level, LogDomain.ALL_DOMAINS); }

    protected BaseLogger(@NonNull LogLevel level, @NonNull LogDomain first, @NonNull LogDomain... rest) {
        this(level, EnumSet.of(first, rest));
    }

    protected BaseLogger(@NonNull LogLevel level, @NonNull EnumSet<LogDomain> domains) {
        this.logLevel = level;
        this.logDomains = domains;
    }

    public abstract void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message);

    public final void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((level.compareTo(logLevel) < 0) || (!logDomains.contains(domain))) { return; }
        writeLog(level, domain, message);
    }

    @NonNull
    public final LogLevel getLevel() { return logLevel; }

    /**
     * Gets the domains that will be considered for logging.
     *
     * @return The currently active domains
     */
    @NonNull
    public final EnumSet<LogDomain> getDomains() { return logDomains; }

    @NonNull
    @Override
    public String toString() { return "Logger{#" + logLevel + logDomains + "}"; }
}
