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
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.EnumSet;

import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The base console logger class.
 */
abstract class AbstractConsoleLogger implements Logger {
    // nullable for testing
    @NonNull
    private EnumSet<LogDomain> logDomains = LogDomain.ALL_DOMAINS;
    @NonNull
    private LogLevel logLevel = LogLevel.WARNING;

    // Singleton instance accessible from Database.log.getConsole()
    protected AbstractConsoleLogger() { }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((level.compareTo(logLevel) < 0) || (!logDomains.contains(domain))) { return; }
        doLog(level, domain, message);
    }

    @NonNull
    @Override
    public LogLevel getLevel() { return logLevel; }

    /**
     * Sets the lowest level that will be logged to the console.
     *
     * @param level The lowest (most verbose) level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        Preconditions.assertNotNull(level, "level");

        if (logLevel == level) { return; }

        logLevel = level;
        setC4Level(level);
    }

    /**
     * Get the set of domains currently being logged to the console.
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() { return logDomains; }

    /**
     * Sets the domains that will be considered for writing to the console log.
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        logDomains = Preconditions.assertNotNull(domains, "domains");
    }

    /**
     * Sets the domains that will be considered for writing to the console log.
     *
     * @param domains The domains to make active (vararg)
     */
    public void setDomains(@NonNull LogDomain... domains) {
        setDomains((domains.length <= 0) ? EnumSet.noneOf(LogDomain.class) : EnumSet.copyOf(Arrays.asList(domains)));
    }

    protected abstract void doLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message);

    protected void setC4Level(@NonNull LogLevel level) {
        C4Log.get().setCallbackLevel(level);
    }

    @VisibleForTesting
    final void reset() {
        logDomains = LogDomain.ALL_DOMAINS;
        logLevel = LogLevel.WARNING;
    }
}
