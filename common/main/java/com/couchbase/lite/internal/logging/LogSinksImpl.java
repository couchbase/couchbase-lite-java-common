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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.logging.BaseLogSink;
import com.couchbase.lite.logging.ConsoleLogSink;
import com.couchbase.lite.logging.FileLogSink;
import com.couchbase.lite.logging.LogSinks;


public final class LogSinksImpl implements LogSinks {
    private static final int LOG_QUEUE_MAX = 16;

    // the singleton implementation of LogSinks
    @NonNull
    private static final AtomicReference<LogSinksImpl> LOG_SINKS = new AtomicReference<>();

    @Nullable
    public static LogSinksImpl getLogSinks() { return LOG_SINKS.get(); }

    // The center of log system initialization.
    public static void initLogging() {
        Log.init();

        final LogSinksImpl logSinks = new LogSinksImpl(C4Log.create());

        final ConsoleLogSink consoleLogSink
            = new ConsoleLogSink(CouchbaseLiteInternal.debugging() ? LogLevel.DEBUG : LogLevel.WARNING, LogDomain.ALL);
        logSinks.setConsole(consoleLogSink);

        ((AbstractLogSink) consoleLogSink).writeLog(
            LogLevel.INFO,
            LogDomain.DATABASE,
            CouchbaseLiteInternal.PLATFORM + " Initialized: " + CBLVersion.getVersionInfo());

        LOG_SINKS.set(logSinks);

        logSinks.usedLegacyLogging = null;
    }

    public static void logToCore(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        final LogSinksImpl sinks = LOG_SINKS.get();
        if (sinks == null) { return; }
        sinks.c4Log.logToCore(domain, level, message);
    }

    // CAUTION: generating a log messages that will go to LiteCore
    // in or below this method will cause a deadlock.
    public static void logFromCore(@NonNull LogLevel level, @NonNull LogDomain domain, @Nullable String message) {
        final LogSinksImpl sinks = LOG_SINKS.get();
        if (sinks == null) { return; }
        sinks.writeToLocalLogSinks(level, domain, message);
    }

    public static void warnNoFileLogSink() {
        final LogSinksImpl sinks = LOG_SINKS.get();
        if (sinks == null) { return; }
        sinks.warnIfNoFileLogSink();
    }

    // Something in the Logging system has failed.
    // Try to log the failure to the console.
    // If that doesn't work, log to System err
    @SuppressWarnings({"RegexpSinglelineJava", "PMD.SystemPrintln"})
    public static void logFailure(@NonNull String loc, @Nullable String msg, @Nullable Exception err) {
        String message = "Logging failure in " + loc;

        if (msg != null) { message += ": " + msg; }

        // Only log the exception when debugging:
        if ((err != null) && CouchbaseLiteInternal.debugging()) { message += "\n" + Log.formatStackTrace(err); }

        final LogSinksImpl sinks = LOG_SINKS.get();
        if (sinks != null) {
            final ConsoleLogSink console = sinks.consoleLogSink;
            if (console != null) {
                try {
                    console.log(LogLevel.WARNING, LogDomain.DATABASE, message);
                    return;
                }
                catch (Exception ignore) { }
            }
        }

        System.err.println("WARNING: " + message);
    }


    // The current level at which logs are generated
    @NonNull
    private final AtomicReference<LogLevel> logLevel = new AtomicReference<>(LogLevel.NONE);

    // The current level at which LiteCore propagates logs to us.
    @NonNull
    private final AtomicReference<LogLevel> callbackLevel = new AtomicReference<>(LogLevel.NONE);

    // The domain filter: a Set of LogDomains that are enabled.
    @NonNull
    private final AtomicReference<Set<LogDomain>> logDomains = new AtomicReference<>(new HashSet<>());

    // If true, the client has been warned that file logging is off.
    @NonNull
    private final AtomicBoolean warned = new AtomicBoolean();

    @NonNull
    private final AtomicReference<ExecutorService> customLogQueue = new AtomicReference<>();

    @NonNull
    private final C4Log c4Log;

    @Nullable
    private FileLogSink fileLogSink;

    @Nullable
    private ConsoleLogSink consoleLogSink;

    @Nullable
    private BaseLogSink customLogSink;

    private Boolean usedLegacyLogging;

    private LogSinksImpl(@NonNull C4Log c4Log) { this.c4Log = c4Log; }

    @Override
    @Nullable
    public FileLogSink getFile() { return fileLogSink; }

    @Override
    public void setFile(@Nullable FileLogSink newSink) {
        if (Objects.equals(fileLogSink, newSink)) { return; }
        forbidNewAndLegacyLogging(newSink);

        final LogLevel newLevel;
        if (newSink == null) {
            newLevel = LogLevel.NONE;
            c4Log.initFileLogging("", newLevel, 0, 0, false, "");
        }
        else {
            newLevel = newSink.getLevel();
            if (newSink.similar(fileLogSink)) { c4Log.setFileLogLevel(newLevel); }
            else {
                c4Log.initFileLogging(
                    newSink.getDirectory(),
                    newLevel,
                    newSink.getMaxKeptFiles(),
                    newSink.getMaxFileSize(),
                    newSink.isPlainText(),
                    CBLVersion.getVersionInfo());
            }
        }

        fileLogSink = newSink;
        setLogFilter();

        warnIfNoFileLogSink();
    }

    @Override
    @Nullable
    public ConsoleLogSink getConsole() { return consoleLogSink; }

    @Override
    public void setConsole(@Nullable ConsoleLogSink newSink) {
        forbidNewAndLegacyLogging(newSink);
        consoleLogSink = newSink;
        setLogFilter();
    }

    @Override
    @Nullable
    public BaseLogSink getCustom() { return customLogSink; }

    @Override
    public void setCustom(@Nullable BaseLogSink newSink) {
        forbidNewAndLegacyLogging(newSink);
        customLogSink = newSink;
        if (newSink == null) {
            final ExecutorService execSvc = customLogQueue.getAndSet(null);
            if (execSvc != null) { execSvc.shutdown(); }
        }
        else if (customLogQueue.get() == null) {
            customLogQueue.compareAndSet(
                null,
                // allowCoreThreadTimeOut not set, so these threads will not timeout
                new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(LOG_QUEUE_MAX)));
        }
        setLogFilter();
    }

    @SuppressWarnings("PMD.GuardLogStatement")
    public void writeToSinks(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String msg) {
        try { log(fileLogSink, level, domain, msg); }
        catch (Exception e) { logFailure("FileSink", msg, e); }
        writeToLocalLogSinks(level, domain, msg);
    }

    // CAUTION: generating a log messages that will go to LiteCore
    // in or below this method will cause a deadlock.
    @SuppressWarnings({"RegexpSinglelineJava", "PMD.GuardLogStatement"})
    public void writeToLocalLogSinks(@NonNull LogLevel level, @NonNull LogDomain domain, @Nullable String message) {
        final String msg = (message == null) ? "" : message;

        final ConsoleLogSink console = consoleLogSink;
        try { log(console, level, domain, msg); }
        catch (Exception e) { logFailure("ConsoleSink", msg, e); }

        final Executor logQueue = this.customLogQueue.get();
        final BaseLogSink custom = customLogSink;
        if ((logQueue == null) || (custom == null) || ((custom.getLevel().compareTo(level) > 0))) { return; }

        try {
            logQueue.execute(() -> {
                try { custom.log(level, domain, msg); }
                catch (Exception e) { logFailure("CustomSink", msg, e); }
            });
        }
        catch (Exception e) { logFailure("CustomSinkQueue", msg, e); }
    }

    public boolean shouldLog(@NonNull LogLevel level, @NonNull LogDomain domain) {
        return (logLevel.get().compareTo(level) <= 0) && logDomains.get().contains(domain);
    }

    @VisibleForTesting
    @NonNull
    public C4Log getC4Log() { return c4Log; }

    private void forbidNewAndLegacyLogging(@Nullable AbstractLogSink newSink) {
        if (newSink == null) { return; }

        final Boolean usedLegacyLogging = this.usedLegacyLogging;
        this.usedLegacyLogging = newSink.isLegacy();

        if (usedLegacyLogging == null) { return; }

        if (!usedLegacyLogging.equals(this.usedLegacyLogging)) {
            throw new CouchbaseLiteError("Cannot mix new and legacy logging");
        }
    }

    private void setLogFilter() {
        final Set<LogDomain> newDomains = new HashSet<>();
        LogLevel platformLogLevel = LogLevel.NONE;

        AbstractLogSink sink;
        LogLevel l;

        sink = this.consoleLogSink;
        if (sink != null) {
            l = sink.getLevel();
            if (l.compareTo(platformLogLevel) < 0) { platformLogLevel = l; }
            newDomains.addAll(sink.getDomains());
        }

        sink = this.customLogSink;
        if (sink != null) {
            l = sink.getLevel();
            if (l.compareTo(platformLogLevel) < 0) { platformLogLevel = l; }
            newDomains.addAll(sink.getDomains());
        }

        // ignore the file log sink's domains
        LogLevel fileLogLevel = LogLevel.NONE;
        if (fileLogSink != null) { fileLogLevel = fileLogSink.getLevel(); }

        // fileLogLevel is the min of the Console, Custom, and File levels:
        // core loggers must log at this level to be sure the File sink
        // gets what it needs
        if (platformLogLevel.compareTo(fileLogLevel) < 0) { fileLogLevel = platformLogLevel; }

        // ... the callback level, though, is just the min of the Console
        // and Custom levels, because that's all the platform needs.
        l = this.callbackLevel.getAndSet(platformLogLevel);
        if (l != platformLogLevel) { c4Log.setCallbackLevel(platformLogLevel); }

        this.logLevel.set(fileLogLevel);
        this.logDomains.set(newDomains);

        // Because of the way the file log sink works, we will touch the log level
        // of every known domain.  There's no point in trying to optimize this.
        c4Log.setLogFilter(fileLogLevel, platformLogLevel, newDomains);
    }

    private void warnIfNoFileLogSink() {
        final FileLogSink sink = fileLogSink;
        if ((sink != null) && (sink.getLevel() != LogLevel.NONE) && !warned.getAndSet(true)) { return; }
        ((AbstractLogSink) new ConsoleLogSink(LogLevel.WARNING, LogDomain.DATABASE)).writeLog(
            LogLevel.WARNING,
            LogDomain.DATABASE,
            "Database.log.getFile().getConfig() is now null: logging is disabled.  "
                + "Log files required for product support are not being generated.");
    }

    // Log to a non-null sink
    private void log(
        @Nullable AbstractLogSink sink,
        @NonNull LogLevel level,
        @NonNull LogDomain domain,
        @NonNull String message) {
        if (sink != null) { sink.log(level, domain, message); }
    }
}
