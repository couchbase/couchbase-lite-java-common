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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CouchbaseLiteInternal;


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

    private static final String DEFAULT_MSG = "Unknown error";

    private static volatile Map<String, String> errorMessages;

    /**
     * Setup logging.
     */
    public static void init() { setStandardErrorMessages(CouchbaseLiteInternal.loadErrorMessages()); }

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
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void i(@NonNull LogDomain domain, @NonNull String msg) { log(LogLevel.INFO, domain, null, msg); }

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

    @NonNull
    public static String formatStackTrace(@NonNull Throwable err) {
        final StringWriter sw = new StringWriter();
        err.printStackTrace(new PrintWriter(sw));
        return System.lineSeparator() + sw;
    }

    @VisibleForTesting
    public static void setStandardErrorMessages(@NonNull Map<String, String> stdErrMsgs) {
        errorMessages = Collections.unmodifiableMap(new HashMap<>(stdErrMsgs));
    }

    private static void log(
        @NonNull LogLevel level,
        @NonNull LogDomain domain,
        @Nullable Throwable err,
        @NonNull String msg,
        @Nullable Object... args) {
        // Don't let logging errors cause a failure
        if (level == null) { level = LogLevel.INFO; }

        // only generate logs >= current priority.
        final LogSinksImpl logSinks = LogSinksImpl.getLogSinks();
        if ((logSinks == null) || (!logSinks.shouldLog(level, domain))) { return; }

        String message;
        if (msg == null) { message = ""; }
        else {
            message = lookupStandardMessage(msg);

            if ((args != null) && (args.length > 0)) { message = formatMessage(message, args); }
        }

        if (err != null) { message += formatStackTrace(err); }

        logSinks.writeToSinks(level, (domain != null) ? domain : LogDomain.DATABASE, LOG_HEADER + message);
    }

    @NonNull
    private static String formatMessage(@NonNull String msg, @NonNull Object... args) {
        try { return String.format(Locale.ENGLISH, msg, args); }
        catch (IllegalFormatException | FormatterClosedException ignore) { }
        return msg;
    }
}
