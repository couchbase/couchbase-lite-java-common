//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.StringUtils;


@SuppressWarnings("PMD.MoreThanOneLogger")
public final class C4Log {
    private C4Log() {} // Utility class

    @VisibleForTesting
    @FunctionalInterface
    public interface RawLogListener {
        void accept(@NonNull String domain, int level, @Nullable String message);
    }

    private static final Map<String, LogDomain> LOGGING_DOMAINS_FROM_C4;
    static {
        final Map<String, LogDomain> m = new HashMap<>();
        m.put(C4Constants.LogDomain.DATABASE, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.SQL, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.ZIP, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.WEB_SOCKET, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.BLIP, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.TLS, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.SYNC, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.SYNC_BUSY, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.QUERY, LogDomain.QUERY);
        m.put(C4Constants.LogDomain.LISTENER, LogDomain.LISTENER);
        LOGGING_DOMAINS_FROM_C4 = Collections.unmodifiableMap(m);
    }

    private static final Map<LogDomain, String> LOGGING_DOMAINS_TO_C4;
    static {
        final Map<LogDomain, String> m = new HashMap<>();
        m.put(LogDomain.DATABASE, C4Constants.LogDomain.DATABASE);
        m.put(LogDomain.NETWORK, C4Constants.LogDomain.WEB_SOCKET);
        m.put(LogDomain.REPLICATOR, C4Constants.LogDomain.SYNC);
        m.put(LogDomain.QUERY, C4Constants.LogDomain.QUERY);
        m.put(LogDomain.LISTENER, C4Constants.LogDomain.LISTENER);
        LOGGING_DOMAINS_TO_C4 = Collections.unmodifiableMap(m);
    }

    private static final Map<Integer, LogLevel> LOG_LEVEL_FROM_C4;
    static {
        final Map<Integer, LogLevel> m = new HashMap<>();
        m.put(C4Constants.LogLevel.DEBUG, LogLevel.DEBUG);
        m.put(C4Constants.LogLevel.VERBOSE, LogLevel.VERBOSE);
        m.put(C4Constants.LogLevel.INFO, LogLevel.INFO);
        m.put(C4Constants.LogLevel.WARNING, LogLevel.WARNING);
        m.put(C4Constants.LogLevel.ERROR, LogLevel.ERROR);
        m.put(C4Constants.LogLevel.NONE, LogLevel.NONE);
        LOG_LEVEL_FROM_C4 = Collections.unmodifiableMap(m);
    }

    private static final Map<LogLevel, Integer> LOG_LEVEL_TO_C4;
    static {
        final Map<LogLevel, Integer> m = new HashMap<>();
        m.put(LogLevel.DEBUG, C4Constants.LogLevel.DEBUG);
        m.put(LogLevel.VERBOSE, C4Constants.LogLevel.VERBOSE);
        m.put(LogLevel.INFO, C4Constants.LogLevel.INFO);
        m.put(LogLevel.WARNING, C4Constants.LogLevel.WARNING);
        m.put(LogLevel.ERROR, C4Constants.LogLevel.ERROR);
        m.put(LogLevel.NONE, C4Constants.LogLevel.NONE);
        LOG_LEVEL_TO_C4 = Collections.unmodifiableMap(m);
    }

    private static RawLogListener rawListener;

    // This class and this method are referenced by name, from native code.
    public static void logCallback(String c4Domain, int c4Level, String message) {
        if (rawListener != null) { rawListener.accept(c4Domain, c4Level, message); }
        try { Log.sendToLocalLoggers(getLoggingDomainForC4Domain(c4Domain), getLogLevelForC4Level(c4Level), message); }
        catch (RuntimeException ignore) {}
    }

    public static void initLogging() {
        setCallbackLevel(LogLevel.NONE);

        // set the log levels for all known domains, to max logging: DEBUG
        // this affects only the logs that core generates, not
        // what actually appears in the file log, or our two loggers.
        for (String domain: LOGGING_DOMAINS_FROM_C4.keySet()) {
            // ??? I have no idea what this is about.
            // An attempt to set the lever for the LISTENER domain
            // will reset the levels all of the domains
            if (domain.equals(C4Constants.LogDomain.LISTENER)) { continue; }
            setLevel(domain, C4Constants.LogLevel.DEBUG);
        }
    }

    public static void setCallbackLevel(@Nullable LogLevel level) {
        if (level == null) { return; }
        setCallbackLevel(getC4LevelForLogLevel(level));
    }

    public static void configureFileLogger(
        @Nullable String path,
        @NonNull LogLevel level,
        int maxFiles,
        long maxFileSize,
        boolean plaintext,
        String header) {
        writeToBinaryFile(path, getC4LevelForLogLevel(level), maxFiles, maxFileSize, plaintext, header);
    }

    public static void setFileLoggerLevel(@NonNull LogLevel level) { setBinaryFileLevel(getC4LevelForLogLevel(level)); }

    public static void logToFile(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        log(getC4DomainForLoggingDomain(domain), getC4LevelForLogLevel(level), message);
    }

    @VisibleForTesting
    public static void registerRawListener(@Nullable RawLogListener listener) { rawListener = listener; }

    @VisibleForTesting
    public static int getLevelForDomain(@NonNull String domain) { return getLevel(domain); }

    @VisibleForTesting
    public static void setLevelForDomain(@Nullable String domain, int level) {
        if (StringUtils.isEmpty(domain)) { return; }
        setLevel(domain, level);
    }

    @NonNull
    private static LogLevel getLogLevelForC4Level(int c4Level) {
        final LogLevel level = LOG_LEVEL_FROM_C4.get(c4Level);
        return (level != null) ? level : LogLevel.INFO;
    }

    private static int getC4LevelForLogLevel(@NonNull LogLevel logLevel) {
        final Integer c4level = LOG_LEVEL_TO_C4.get(logLevel);
        return (c4level != null) ? c4level : C4Constants.LogLevel.INFO;
    }

    @NonNull
    private static String getC4DomainForLoggingDomain(@NonNull LogDomain domain) {
        final String c4Domain = LOGGING_DOMAINS_TO_C4.get(domain);
        return (c4Domain != null) ? c4Domain : C4Constants.LogDomain.DATABASE;
    }

    @NonNull
    private static LogDomain getLoggingDomainForC4Domain(@NonNull String c4Domain) {
        final LogDomain domain = LOGGING_DOMAINS_FROM_C4.get(c4Domain);
        return (domain != null) ? domain : LogDomain.DATABASE;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void log(String domain, int level, String message);

    private static native void writeToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header);

    private static native void setBinaryFileLevel(int level);

    private static native void setCallbackLevel(int level);

    private static native int getLevel(String domain);

    private static native void setLevel(String domain, int level);
}
