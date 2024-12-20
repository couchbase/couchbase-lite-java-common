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

import java.util.Set;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.logging.BaseLogSink;
import com.couchbase.lite.logging.LogSinks;


/**
 * Holder for the three Couchbase Lite loggers:  console, file, and custom.
 *
 * @deprecated Use com.couchbase.lite.logging.Loggers
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName", "DeprecatedIsStillUsed"})
@Deprecated
public final class Log {
    private final class ShimLogger extends BaseLogSink {
        ShimLogger(@NonNull LogLevel level, @NonNull Set<LogDomain> domains) { super(level, domains); }

        @Override
        protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            final BaseLogSink curLogger = LogSinks.get().getCustom();
            if (this != curLogger) { return; }

            final Logger logger = customLogger;
            if (logger == null) { return; }

            logger.log(level, domain, message);

            // if the custom logger has changed its level since the last log, install a new one.
            // NOTE: this may call back into lite core!
            // If it was called on a LiteCore thread it may deadlock
            if (getLevel() != logger.getLevel()) { installCustomLogger(logger); }
        }
    }


    // Singleton instance.
    private final ConsoleLogger consoleLogger = new ConsoleLogger();

    // Singleton instance.
    private final FileLogger fileLogger = new FileLogger();

    // Singleton instance.
    @Nullable
    private Logger customLogger;

    // The singleton instance is available from Database.log
    Log() { }

    /**
     * Gets the logger that writes to the system console
     *
     * @return The logger that writes to the system console
     * @deprecated Use com.couchbase.lite.logging.Loggers.getConsoleLogger
     */
    @Deprecated
    @NonNull
    public ConsoleLogger getConsole() {
        CouchbaseLiteInternal.requireInit("Console logging not initialized");
        return consoleLogger;
    }

    /**
     * Gets the logger that writes to log files
     *
     * @return The logger that writes to log files
     * @deprecated Use com.couchbase.lite.logging.Loggers.getFileLogger
     */
    @Deprecated
    @NonNull
    public FileLogger getFile() {
        CouchbaseLiteInternal.requireInit("File logging not initialized");
        return fileLogger;
    }

    /**
     * Gets the custom logger that was registered by the
     * application (if any)
     *
     * @return The custom logger that was registered by
     * the application, or null.
     * @deprecated Use com.couchbase.lite.logging.Loggers.getCustomLogger
     */
    @Deprecated
    @Nullable
    public Logger getCustom() { return customLogger; }

    /**
     * Sets an application specific logging method
     *
     * @param customLogger A Logger implementation that will receive logging messages
     * @deprecated Use com.couchbase.lite.logging.Loggers.getCustomLogger
     */
    @Deprecated
    public void setCustom(@Nullable Logger customLogger) {
        this.customLogger = customLogger;
        installCustomLogger(customLogger);
    }

    private void installCustomLogger(@Nullable Logger logger) {
        LogSinks.get().setCustom((logger == null)
            ? null
            : new ShimLogger(logger.getLevel(), LogDomain.ALL));
    }
}
