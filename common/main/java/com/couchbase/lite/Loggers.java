//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
import android.support.annotation.Nullable;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;


/**
 * Consistent view of system logging.
 * <p>
 * This class is NOT thread safe.
 */
public final class Loggers {
    @Nullable
    private volatile FileLogger fileLogger;

    @Nullable
    private volatile ConsoleLogger consoleLogger;

    @Nullable
    private volatile CustomLogger customLogger;

    /**
     * Gets the logger that writes to log files.
     *
     * @return The logger that writes to log files
     */
    @Nullable
    public FileLogger getFileLogger() { return fileLogger; }

    /**
     * Sets the logger that writes to log files.
     *
     * @param newLogger the file logger
     */
    @SuppressWarnings("PMD.PrematureDeclaration")
    public void setFileLogger(@Nullable FileLogger newLogger) {
        CouchbaseLiteInternal.requireInit("Logging not initialized");

        checkRequiredLogging(newLogger);

        final FileLogger prevLogger = fileLogger;
        fileLogger = newLogger;

        if (newLogger == null) {
            C4Log.configureFileLogger(null, LogLevel.NONE, 0, 0, false, null);
            return;
        }

        final LogLevel newLogLevel = newLogger.getLevel();

        if (newLogger.isSimilarTo(prevLogger)) {
            if (newLogLevel != prevLogger.getLevel()) { C4Log.setFileLoggerLevel(newLogLevel); }
            return;
        }

        C4Log.configureFileLogger(
            newLogger.getLogDir(),
            newLogLevel,
            newLogger.getMaxKeptFiles(),
            newLogger.getMaxFileSize(),
            newLogger.usePlaintext(),
            CBLVersion.getVersionInfo());
    }

    /**
     * Gets the logger that writes to the system console
     *
     * @return logger The logger that writes to the system console
     */
    @Nullable
    public ConsoleLogger getConsoleLogger() { return consoleLogger; }

    /**
     * Sets the logger that writes to the system console
     *
     * @param newLogger The logger that writes to the system console
     */
    public void setConsoleLogger(@Nullable ConsoleLogger newLogger) {
        CouchbaseLiteInternal.requireInit("Logging not initialized");
        setCallbackLevel(newLogger, consoleLogger, customLogger);
        consoleLogger = newLogger;
        initLogger(newLogger);
    }

    /**
     * Gets the logger that writes to the system console
     *
     * @return The logger that writes to the system console
     */
    @Nullable
    public CustomLogger getCustomLogger() { return customLogger; }

    /**
     * Sets an application specific logging method
     *
     * @param newLogger A Logger implementation that will receive logging messages
     */
    public void setCustomLogger(@Nullable CustomLogger newLogger) {
        CouchbaseLiteInternal.requireInit("Logging not initialized");
        setCallbackLevel(newLogger, customLogger, consoleLogger);
        customLogger = newLogger;
        initLogger(newLogger);
    }

    // warn if we are turning off the required logging (>= WARNING)
    // this has to happen *before* the new logger is installed
    private void checkRequiredLogging(@Nullable FileLogger newLogger) {
        final FileLogger prevLogger = fileLogger;
        if (((prevLogger != null) && (prevLogger.getLevel().compareTo(LogLevel.WARNING) <= 0))
            && ((newLogger == null) || (newLogger.getLevel().compareTo(LogLevel.WARNING) > 0))) {
            Log.warn();
        }
    }

    private void setCallbackLevel(
        @Nullable BaseLogger newLogger,
        @Nullable BaseLogger oldLogger,
        @NonNull BaseLogger... otherLoggers) {
        LogLevel curLogLevel = LogLevel.NONE;

        for (BaseLogger logger: otherLoggers) {
            if (logger == null) { continue; }
            final LogLevel level = logger.getLevel();
            if (level.compareTo(curLogLevel) < 0) { curLogLevel = level; }
        }

        LogLevel newLogLevel = curLogLevel;
        if (newLogger != null) {
            final LogLevel level = newLogger.getLevel();
            if (level.compareTo(newLogLevel) < 0) { newLogLevel = level; }
        }

        if (oldLogger != null) {
            final LogLevel level = oldLogger.getLevel();
            if (level.compareTo(curLogLevel) < 0) { curLogLevel = level; }
        }

        if (curLogLevel != newLogLevel) { C4Log.setCallbackLevel(newLogLevel); }
    }

    // use writeLog to bypass level/domain filtering
    private void initLogger(@Nullable BaseLogger logger) {
        if (logger == null) { return; }
        logger.writeLog(
            LogLevel.INFO,
            LogDomain.DATABASE,
            "Logger Initialized: " + CBLVersion.getVersionInfo());
    }
}
