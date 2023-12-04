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

import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.logging.Loggers;


/**
 * A class that sends log messages to Android's system log, available via 'adb logcat'.
 * <p/>
 * Do not subclass! This class is not final only for testing.
 *
 * @deprecated Use com.couchbase.lite.logging.ConsoleLogger
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName", "DeprecatedIsStillUsed"})
@Deprecated
public class ConsoleLogger implements Logger {
    @VisibleForTesting
    static class ShimLogger extends com.couchbase.lite.logging.ConsoleLogger {
        @NonNull
        private EnumSet<LogDomain> logDomains;

        ShimLogger(@NonNull LogLevel level, @NonNull EnumSet<LogDomain> logDomains) {
            super(level);
            this.logDomains = logDomains;
        }

        protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            if (logDomains.contains(domain)) { doWriteLog(level, domain, message); }
        }

        @VisibleForTesting
        protected void doWriteLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            final com.couchbase.lite.logging.ConsoleLogger curLogger = Loggers.get().getConsoleLogger();
            if (this == curLogger) { super.writeLog(level, domain, message); }
        }

        void doLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            super.log(level, domain, message);
        }
    }


    @Nullable
    private ShimLogger logger;

    ConsoleLogger() { }

    /**
     * Gets the level that will be logged via this logger.
     *
     * @return The maximum level to log
     */
    @NonNull
    public LogLevel getLevel() {
        final com.couchbase.lite.logging.ConsoleLogger curLogger = Loggers.get().getConsoleLogger();
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
        Loggers.get().setConsoleLogger(logger);
    }

    /**
     * Get the set of domains currently being logged to the console.
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() {
        final com.couchbase.lite.logging.ConsoleLogger curLogger = Loggers.get().getConsoleLogger();
        return ((logger == null) || (logger != curLogger)) ? LogDomain.ALL_DOMAINS : EnumSet.copyOf(logger.logDomains);
    }

    /**
     * Sets the domains that will be considered for writing to the console log.
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        final EnumSet<LogDomain> logDomains = Preconditions.assertNotNull(domains, "domains");

        final Loggers loggers = Loggers.get();
        final com.couchbase.lite.logging.ConsoleLogger curLogger = loggers.getConsoleLogger();
        if ((logger != null) && (logger == curLogger)) {
            // trivial optimization: if the domain filter hasn't changed, we're done
            if (domains.equals(logger.logDomains)) { return; }

            // otherwise, just set the new domains
            logger.logDomains = logDomains;
            return;
        }

        // otherwise, install a new shim
        logger = shimFactory(getLevel(), logDomains);
        loggers.setConsoleLogger(logger);
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
