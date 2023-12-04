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
package com.couchbase.lite.internal.logging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.logging.ConsoleLogger;
import com.couchbase.lite.logging.CustomLogger;
import com.couchbase.lite.logging.FileLogger;
import com.couchbase.lite.logging.Loggers;


public final class LoggersImpl implements Loggers {
    private static final int LOG_QUEUE_MAX = 8;

    // the singleton implementation of Loggers
    @NonNull
    private static final AtomicReference<LoggersImpl> LOGGERS = new AtomicReference<>();

    @NonNull
    public static LoggersImpl getLoggers() {
        CouchbaseLiteInternal.requireInit("Logging not initialized");
        return Preconditions.assertNotNull(LOGGERS.get(), "loggers");
    }

    // The center of log system initialization.
    public static void initLogging() {
        final C4Log c4Log = C4Log.init();

        Log.init();

        final LoggersImpl loggers = new LoggersImpl(c4Log);
        final ConsoleLogger consoleLogger = new ConsoleLogger(LogLevel.WARNING);
        loggers.setConsoleLogger(consoleLogger);
        ((AbstractLogger) consoleLogger).writeLog(
            LogLevel.INFO,
            LogDomain.DATABASE,
            CouchbaseLiteInternal.PLATFORM + " Initialized: " + CBLVersion.getVersionInfo());
        LOGGERS.set(loggers);
    }

    public static void warnNoLogger() {
        final LoggersImpl loggers = LOGGERS.get();
        if (loggers == null) { return; }
        loggers.warnIfNoFileLogger();
    }


    @NonNull
    private final C4Log c4Log;
    @NonNull
    private final ExecutionService.CloseableExecutor customLogQueue;

    // The current level at which logs are generated
    @NonNull
    private final AtomicReference<LogLevel> logLevel = new AtomicReference<>(LogLevel.NONE);
    // The current level at which LiteCore propagates logs to us.
    @NonNull
    private final AtomicReference<LogLevel> callbackLevel = new AtomicReference<>(LogLevel.NONE);
    // If true, the client has been warned that logging is off.
    @NonNull
    private final AtomicBoolean warned = new AtomicBoolean();

    @Nullable
    private FileLogger fileLogger;

    @Nullable
    private ConsoleLogger consoleLogger;

    @Nullable
    private CustomLogger customLogger;

    private LoggersImpl(@NonNull C4Log c4Log) {
        this.c4Log = c4Log;
        customLogQueue = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
    }

    @Override
    @Nullable
    public FileLogger getFileLogger() { return fileLogger; }

    @Override
    public void setFileLogger(@Nullable FileLogger newLogger) {
        if (Objects.equals(fileLogger, newLogger)) { return; }

        final LogLevel newLevel;
        if (newLogger == null) {
            newLevel = LogLevel.NONE;
            c4Log.initFileLogger("", newLevel, 0, 0, false, "");
        }
        else {
            newLevel = newLogger.getLevel();
            if (newLogger.similar(fileLogger)) { c4Log.setFileLogLevel(newLevel); }
            else {
                c4Log.initFileLogger(
                    newLogger.getDirectory(),
                    newLevel,
                    newLogger.getMaxKeptFiles(),
                    newLogger.getMaxFileSize(),
                    newLogger.isPlainText(),
                    CBLVersion.getVersionInfo());
            }
        }

        fileLogger = newLogger;
        setLogLevel();

        warnIfNoFileLogger();
    }

    @Override
    @Nullable
    public ConsoleLogger getConsoleLogger() { return consoleLogger; }

    @Override
    public void setConsoleLogger(@Nullable ConsoleLogger newLogger) {
        consoleLogger = newLogger;
        setLogLevel();
    }

    @Override
    @Nullable
    public CustomLogger getCustomLogger() { return customLogger; }

    @Override
    public void setCustomLogger(@Nullable CustomLogger newLogger) {
        customLogger = newLogger;
        setLogLevel();
    }

    @SuppressWarnings("PMD.GuardLogStatement")
    public void writeToLoggers(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String msg) {
        try { log(fileLogger, level, domain, msg); }
        catch (Exception e) {
            log(consoleLogger, LogLevel.WARNING, LogDomain.DATABASE, "File logger failure: " + Log.formatStackTrace(e));
        }

        writeToLocalLoggers(level, domain, msg);
    }


    @SuppressWarnings({"RegexpSinglelineJava", "PMD.SystemPrintln", "PMD.GuardLogStatement"})
    public void writeToLocalLoggers(@NonNull LogLevel level, @NonNull LogDomain domain, @Nullable String message) {
        final String msg = (message == null) ? "" : message;

        final ConsoleLogger console = consoleLogger;
        try { log(console, level, domain, msg); }
        catch (Exception e) { System.err.println("Console logger failure" + Log.formatStackTrace(e)); }

        // A custom logger is client code: give it 1 second on a safe thread
        final CustomLogger custom = customLogger;
        if ((custom != null) && ((custom.getLevel().compareTo(level) <= 0))) {
            if (customLogQueue.getPending() > LOG_QUEUE_MAX) {
                log(console, LogLevel.WARNING, LogDomain.DATABASE, "Log queue overflow: Logs dropped");
                return;
            }

            customLogQueue.execute(() -> {
                try { custom.log(level, domain, msg); }
                catch (Exception e) {
                    log(console, LogLevel.WARNING, LogDomain.DATABASE, "Custom log failure" + Log.formatStackTrace(e));
                }
            });
        }
    }

    @NonNull
    public LogLevel getLogLevel() { return logLevel.get(); }

    private void setLogLevel() {
        LogLevel l;
        LogLevel callbackLevel = LogLevel.NONE;
        if (consoleLogger != null) {
            l = consoleLogger.getLevel();
            if (l.compareTo(callbackLevel) < 0) { callbackLevel = l; }
        }
        if (customLogger != null) {
            l = customLogger.getLevel();
            if (l.compareTo(callbackLevel) < 0) { callbackLevel = l; }
        }

        LogLevel level = LogLevel.NONE;
        if (fileLogger != null) { level = fileLogger.getLevel(); }
        if (callbackLevel.compareTo(level) < 0) { level = callbackLevel; }

        l = this.callbackLevel.getAndSet(callbackLevel);
        if (l != callbackLevel) { c4Log.setCallbackLevel(callbackLevel); }

        l = logLevel.getAndSet(level);
        if (l != level) { c4Log.setLogLevel(level); }
    }

    private void warnIfNoFileLogger() {
        final FileLogger logger = fileLogger;
        if ((logger != null) && (logger.getLevel() != LogLevel.NONE) && !warned.getAndSet(true)) { return; }
        ((AbstractLogger) new ConsoleLogger(LogLevel.WARNING)).writeLog(
            LogLevel.WARNING,
            LogDomain.DATABASE,
            "Database.log.getFile().getConfig() is now null: logging is disabled.  "
                + "Log files required for product support are not being generated.");
    }

    private void log(
        @Nullable AbstractLogger logger,
        @NonNull LogLevel level,
        @NonNull LogDomain domain,
        @NonNull String message) {
        if (logger != null) { logger.log(level, domain, message); }
    }
}
