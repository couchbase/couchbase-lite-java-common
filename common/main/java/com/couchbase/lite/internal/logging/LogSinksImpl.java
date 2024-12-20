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
import androidx.annotation.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.logging.BaseLogSink;
import com.couchbase.lite.logging.ConsoleLogSink;
import com.couchbase.lite.logging.FileLogSink;
import com.couchbase.lite.logging.LogSinks;


public final class LogSinksImpl implements LogSinks {
    private static final int LOG_QUEUE_MAX = 8;

    // the singleton implementation of Loggers
    @NonNull
    private static final AtomicReference<LogSinksImpl> LOGGERS = new AtomicReference<>();

    @Nullable
    public static LogSinksImpl getLoggers() { return LOGGERS.get(); }

    // The center of log system initialization.
    public static void initLogging() {
        Log.init();

        final LogSinksImpl loggers = new LogSinksImpl(C4Log.create());

        final ConsoleLogSink consoleLogger
            = new ConsoleLogSink(CouchbaseLiteInternal.debugging() ? LogLevel.DEBUG : LogLevel.WARNING);
        loggers.setConsole(consoleLogger);

        ((AbstractLogSink) consoleLogger).writeLog(
            LogLevel.INFO,
            LogDomain.DATABASE,
            CouchbaseLiteInternal.PLATFORM + " Initialized: " + CBLVersion.getVersionInfo());

        LOGGERS.set(loggers);
    }

    public static void logToCore(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        final LogSinksImpl loggers = LOGGERS.get();
        if (loggers == null) { return; }
        loggers.c4Log.logToCore(domain, level, message);
    }

    public static void logFromCore(@NonNull LogLevel level, @NonNull LogDomain domain, @Nullable String message) {
        final LogSinksImpl loggers = LOGGERS.get();
        if (loggers == null) { return; }
        loggers.writeToLocalLoggers(level, domain, message);
    }

    public static void warnNoLogger() {
        final LogSinksImpl loggers = LOGGERS.get();
        if (loggers == null) { return; }
        loggers.warnIfNoFileLogger();
    }


    // The current level at which logs are generated
    @NonNull
    private final AtomicReference<LogLevel> logLevel = new AtomicReference<>(LogLevel.NONE);

    // The current level at which LiteCore propagates logs to us.
    @NonNull
    private final AtomicReference<LogLevel> callbackLevel = new AtomicReference<>(LogLevel.NONE);

    // If true, the client has been warned that logging is off.
    @NonNull
    private final AtomicBoolean warned = new AtomicBoolean();

    @NonNull
    private final ExecutionService.CloseableExecutor customLogQueue;

    @NonNull
    private C4Log c4Log;

    @Nullable
    private FileLogSink fileLogger;

    @Nullable
    private ConsoleLogSink consoleLogger;

    @Nullable
    private BaseLogSink customLogger;

    private LogSinksImpl(@NonNull C4Log c4Log) {
        this.c4Log = c4Log;
        customLogQueue = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
    }

    @Override
    @Nullable
    public FileLogSink getFile() { return fileLogger; }

    @Override
    public void setFile(@Nullable FileLogSink newLogger) {
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
    public ConsoleLogSink getConsole() { return consoleLogger; }

    @Override
    public void setConsole(@Nullable ConsoleLogSink newLogger) {
        consoleLogger = newLogger;
        setLogLevel();
    }

    @Override
    @Nullable
    public BaseLogSink getCustom() { return customLogger; }

    @Override
    public void setCustom(@Nullable BaseLogSink newLogger) {
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

        final ConsoleLogSink console = consoleLogger;
        try { log(console, level, domain, msg); }
        catch (Exception e) { System.err.println("Console logger failure" + Log.formatStackTrace(e)); }

        // A custom logger is client code: give it 1 second on a safe thread
        final BaseLogSink custom = customLogger;
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

    @VisibleForTesting
    @NonNull
    public C4Log getC4Log() { return c4Log; }

    @VisibleForTesting
    public void setC4Log(@NonNull C4Log c4Log) { this.c4Log = c4Log; }

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
        final FileLogSink logger = fileLogger;
        if ((logger != null) && (logger.getLevel() != LogLevel.NONE) && !warned.getAndSet(true)) { return; }
        ((AbstractLogSink) new ConsoleLogSink(LogLevel.WARNING)).writeLog(
            LogLevel.WARNING,
            LogDomain.DATABASE,
            "Database.log.getFile().getConfig() is now null: logging is disabled.  "
                + "Log files required for product support are not being generated.");
    }

    private void log(
        @Nullable AbstractLogSink logger,
        @NonNull LogLevel level,
        @NonNull LogDomain domain,
        @NonNull String message) {
        if (logger != null) { logger.log(level, domain, message); }
    }
}
