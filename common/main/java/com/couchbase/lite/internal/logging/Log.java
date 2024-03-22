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
package com.couchbase.lite.internal.logging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.FormatterClosedException;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.Database;
import com.couchbase.lite.FileLogger;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;


/**
 * Couchbase Lite Internal Log Utility.
 * <p>
 * Log levels are as follows:
 * e: internal errors that are unrecoverable
 * w: internal errors that are recoverable; client errors that may not be recoverable
 * i: essential state info and client errors that are probably recoverable
 * v: used by core: please do not use in platform coded.
 * d: low-level debugging information
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class Log {
    private Log() { } // Utility class

    public static final String LOG_HEADER = "[JAVA] ";

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    private static final String DEFAULT_MSG = "Unknown error";

    private static volatile Map<String, String> errorMessages;

    /**
     * Setup logging.
     */
    public static void initLogging(@NonNull Map<String, String> errorMessages, @NonNull LogLevel initLogLevel) {
        initLoggingInternal();

        setStandardErrorMessages(Collections.unmodifiableMap(errorMessages));

        // Init the console logger.  The FileLogger will take care of itself.
        final ConsoleLogger logger = Database.log.getConsole();
        logger.setLevel(LogLevel.INFO);
        Log.i(LogDomain.DATABASE, CouchbaseLiteInternal.PLATFORM + " Initialized: " + CBLVersion.getVersionInfo());
        logger.setLevel(initLogLevel);
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.DEBUG, domain, null, msg); }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.DEBUG, domain, err, msg);
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.DEBUG, domain, null, msg, args);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void d(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.DEBUG, domain, err, msg, args);
    }

    /**
     * Send a VERBOSE message and log the exception.
     * Please do not use verbose level logging
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void v(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.VERBOSE, domain, err, msg, args);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.INFO, domain, null, msg); }

    public static void info(@NonNull LogDomain domain, @NonNull String msg) { i(domain, msg); }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.INFO, domain, err, msg);
    }

    public static void info(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        i(domain, msg, err);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.INFO, domain, null, msg, args);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.INFO, domain, err, msg, args);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.WARNING, domain, null, msg); }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.WARNING, domain, err, msg);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.WARNING, domain, null, msg, args);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void w(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.WARNING, domain, err, msg, args);
    }

    /**
     * Send an ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.ERROR, domain, null, msg); }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param err    An exception to log
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err) {
        log(LogLevel.ERROR, domain, err, msg);
    }

    /**
     * Send a ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg, Object... args) {
        log(LogLevel.ERROR, domain, null, msg, args);
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The string you would like logged plus format specifiers.
     * @param err    An exception to log
     * @param args   Variable number of Object args to be used as params to formatString.
     */
    public static void e(@NonNull LogDomain domain, @NonNull String msg, @Nullable Throwable err, Object... args) {
        log(LogLevel.ERROR, domain, err, msg, args);
    }

    @NonNull
    public static String lookupStandardMessage(@Nullable String msg) {
        if (msg == null) { return DEFAULT_MSG; }  // Don't let logging errors cause an abort
        final String message = (errorMessages == null) ? msg : errorMessages.get(msg);
        return (message == null) ? msg : message;
    }

    @NonNull
    public static String formatStandardMessage(@Nullable String msg, @NonNull Object... args) {
        return String.format(Locale.ENGLISH, lookupStandardMessage(msg), args);
    }

    public static void warn() {
        final FileLogger fl = Database.log.getFile();
        if (WARNED.getAndSet(true) || ((fl.getConfig() != null) && (fl.getLevel() != LogLevel.NONE))) { return; }
        Log.w(
            LogDomain.DATABASE,
            "Database.log.getFile().getConfig() is now null: logging is disabled.  "
                + "Log files required for product support are not being generated.");
    }

    @VisibleForTesting
    public static void initLoggingInternal() {
        final C4Log c4Log = C4Log.get();
        c4Log.forceCallbackLevel(Database.log.getConsole().getLevel());
        c4Log.setC4LogLevel(LogDomain.ALL_DOMAINS, LogLevel.DEBUG);
    }

    @VisibleForTesting
    @NonNull
    public static Map<String, String> setStandardErrorMessages(@NonNull Map<String, String> stdErrMsgs) {
        final Map<String, String> prevMessages = errorMessages;
        errorMessages = Collections.unmodifiableMap(new HashMap<>(stdErrMsgs));
        return prevMessages;
    }

    private static void log(
        @NonNull LogLevel level,
        @NonNull LogDomain domain,
        @Nullable Throwable err,
        @NonNull String msg,
        @Nullable Object... args) {
        // Don't let logging errors cause a failure
        if (level == null) { level = LogLevel.INFO; }
        if (!shouldLog(level)) { return; }

        if (domain == null) { domain = LogDomain.DATABASE; }
        String message = lookupStandardMessage(msg);

        if ((args != null) && (args.length > 0)) { message = formatMessage(message, args); }

        if (err != null) {
            final StringWriter sw = new StringWriter();
            err.printStackTrace(new PrintWriter(sw));
            message += System.lineSeparator() + sw;
        }

        sendToLoggers(level, domain, LOG_HEADER + message);
    }

    private static boolean shouldLog(@NonNull LogLevel logLevel) {
        if ((LogLevel.VERBOSE.compareTo(logLevel) >= 0) && !CouchbaseLiteInternal.debugging()) { return false; }
        final LogLevel callbackLevel = C4Log.get().getCallbackLevel();
        final LogLevel fileLogLevel = Database.log.getFile().getLevel();
        return ((callbackLevel.compareTo(fileLogLevel) < 0) ? callbackLevel : fileLogLevel).compareTo(logLevel) <= 0;
    }

    @NonNull
    private static String formatMessage(@NonNull String msg, @NonNull Object... args) {
        try { return String.format(Locale.ENGLISH, msg, args); }
        catch (IllegalFormatException | FormatterClosedException ignore) { }
        return msg;
    }

    private static void sendToLoggers(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String msg) {
        final com.couchbase.lite.Log logger = Database.log;

        // Console logging:
        final ConsoleLogger consoleLogger = logger.getConsole();
        Exception consoleErr = null;
        try { consoleLogger.log(level, domain, msg); }
        catch (Exception e) { consoleErr = e; }

        // File logging:
        final FileLogger fileLogger = logger.getFile();
        try {
            fileLogger.log(level, domain, msg);
            if (consoleErr != null) { consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, consoleErr.toString()); }
        }
        catch (Exception e) {
            if (consoleErr == null) { fileLogger.log(LogLevel.ERROR, LogDomain.DATABASE, e.toString()); }
        }

        // Custom logging:
        final Logger custom = logger.getCustom();
        if (custom != null) {
            try { custom.log(level, domain, msg); }
            catch (Exception ignore) { }
        }
    }
}
