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

import java.util.Objects;

import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.logging.FileLogSink;
import com.couchbase.lite.logging.LogSinks;


/**
 * A logger for writing to a file in the application's storage so
 * that log messages can persist durably after the application has
 * stopped or encountered a problem.  Each log level is written to
 * a separate file.
 *
 * @deprecated Use com.couchbase.lite.logging.FileLogSink
 */
@SuppressWarnings("PMD.UnnecessaryFullyQualifiedName")
@Deprecated
public final class FileLogger implements Logger {
    private static final class ShimLogger extends FileLogSink {
        ShimLogger(@NonNull FileLogSink.Builder builder) { super(builder); }

        @Override
        protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            final FileLogSink curLogger = LogSinks.get().getFile();
            if (this == curLogger) { super.writeLog(level, domain, message); }
        }

        void doLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            super.log(level, domain, message);
        }

        protected boolean isLegacy() { return true; }
    }

    @Nullable
    private LogFileConfiguration configuration;
    @Nullable
    private ShimLogger logger;

    FileLogger() { }

    /**
     * Gets the level that will be logged via this logger.
     *
     * @return The maximum level to log
     */
    @Override
    @NonNull
    public LogLevel getLevel() {
        final FileLogSink curLogger = LogSinks.get().getFile();
        return (curLogger == null) ? LogLevel.NONE : curLogger.getLevel();
    }

    /**
     * Sets the lowest level that will be logged to the logging files.
     *
     * @param level The lowest (most verbose) level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        Preconditions.assertNotNull(level, "level");

        final LogFileConfiguration config = configuration;
        if (config == null) { throw new CouchbaseLiteError(Log.lookupStandardMessage("CannotSetLogLevel")); }

        // if the logging level has changed, install a new logger with the new level
        final LogLevel curLevel = getLevel();
        if (curLevel == level) { return; }

        installLogger(config, level);
    }

    /**
     * Gets the configuration currently in use by the file logger.
     * Note the configuration returned from this method is read-only
     * and cannot be modified.  An attempt to modify it will throw an exception.
     *
     * @return The configuration currently in use
     */
    @Nullable
    public LogFileConfiguration getConfig() {
        final FileLogSink fileLogger = LogSinks.get().getFile();
        return (fileLogger == null)
            ? null
            : new LogFileConfiguration(
                fileLogger.getDirectory(),
                fileLogger.getMaxFileSize(),
                fileLogger.getMaxKeptFiles() - 1,
                fileLogger.isPlainText(),
                true);
    }

    /**
     * Sets the configuration for use by the file logger.
     *
     * @param newConfig The configuration to use
     */
    public void setConfig(@Nullable LogFileConfiguration newConfig) {
        if (Objects.equals(getConfig(), newConfig)) { return; }
        final LogFileConfiguration config = (newConfig == null) ? null : new LogFileConfiguration(newConfig);
        installLogger(config, getLevel());
        configuration = config;
    }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (logger != null) { logger.doLog(level, domain, message); }
    }

    private void installLogger(@Nullable LogFileConfiguration config, @NonNull LogLevel level) {
        final ShimLogger newLogger = (config == null)
            ? null
            : new ShimLogger(
                new FileLogSink.Builder()
                    .setDirectory(config.getDirectory())
                    .setLevel(level)
                    .setMaxKeptFiles(config.getMaxRotateCount() + 1)
                    .setMaxFileSize(config.getMaxSize())
                    .setPlainText(config.usesPlaintext()));
        LogSinks.get().setFile(newLogger);
        this.logger = newLogger;
    }
}
