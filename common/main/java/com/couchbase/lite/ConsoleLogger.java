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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.logging.ConsoleLogSink;
import com.couchbase.lite.logging.LogSinks;


/**
 * A class that sends log messages to Android's system log, available via 'adb logcat'.
 *
 * @deprecated Use com.couchbase.lite.logging.ConsoleLogSink
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName", "DeprecatedIsStillUsed"})
@Deprecated
public class ConsoleLogger implements Logger {
    @VisibleForTesting
    static class ShimLogger extends ConsoleLogSink {
        ShimLogger(@NonNull LogLevel level, @NonNull EnumSet<LogDomain> logDomains) { super(level, logDomains); }

        protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            doWriteLog(level, domain, message);
        }

        @VisibleForTesting
        protected void doWriteLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            final ConsoleLogSink curLogger = LogSinks.get().getConsole();
            if (this == curLogger) { super.writeLog(level, domain, message); }
        }

        void doLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            super.log(level, domain, message);
        }

        protected boolean isLegacy() { return true; }
    }


    @Nullable
    private ShimLogger logger;

    ConsoleLogger() { }

    /**
     * Gets the level that will be logged by the current logger.
     *
     * @return The maximum level to log
     */
    @NonNull
    public LogLevel getLevel() {
        final ConsoleLogSink curLogger = LogSinks.get().getConsole();
        return (curLogger == null) ? LogLevel.NONE : curLogger.getLevel();
    }

    /**
     * Sets the lowest level that will be logged to the console.
     *
     * @param level The lowest (most verbose) level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        Preconditions.assertNotNull(level, "level");

        // if the logging level has changed, install a new logger with the new level
        final LogLevel curLevel = getLevel();
        if (curLevel == level) { return; }

        logger = shimFactory(level, getDomains());
        LogSinks.get().setConsole(logger);
    }

    /**
     * Get the set of domains currently being logged to the console.
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() {
        final ConsoleLogSink curLogger = LogSinks.get().getConsole();
        if (curLogger == null) { return EnumSet.noneOf(LogDomain.class); }
        final Set<LogDomain> curDomains = curLogger.getDomains();
        return (curDomains.isEmpty())
            ? EnumSet.noneOf(LogDomain.class)
            : EnumSet.copyOf(curDomains);
    }

    /**
     * Sets the domains that will be considered for writing to the console log.
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        final LogSinks loggers = LogSinks.get();

        // trivial optimization: if the domain filter hasn't changed, we're done
        final ConsoleLogSink curLogger = loggers.getConsole();
        if ((curLogger != null) && domains.equals(curLogger.getDomains())) { return; }

        // otherwise, install a new shim
        logger = shimFactory(getLevel(), Preconditions.assertNotNull(domains, "domains"));
        loggers.setConsole(logger);
    }

    /**
     * Sets the domains that will be considered for writing to the console log.
     *
     * @param domains The domains to make active (vararg)
     */
    public void setDomains(@NonNull LogDomain... domains) {
        Preconditions.assertNotNull(domains, "domains");
        setDomains((domains.length <= 0) ? EnumSet.noneOf(LogDomain.class) : EnumSet.copyOf(Arrays.asList(domains)));
    }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (logger != null) { logger.doLog(level, domain, message); }
    }

    @VisibleForTesting
    @NonNull
    ShimLogger shimFactory(@NonNull LogLevel level, @NonNull EnumSet<LogDomain> domains) {
        return new ShimLogger(level, domains);
    }
}
