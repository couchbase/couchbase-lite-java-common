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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.Preconditions;


public final class C4Log {
    public interface NativeImpl {
        void nLog(@NonNull String domain, int level, @NonNull String message);
        void nSetLevel(@NonNull String domain, int level);
        void nSetCallbackLevel(int level);
        void nSetBinaryFileLevel(int level);
        void nWriteToBinaryFile(
            String path,
            int level,
            int maxRotateCount,
            long maxSize,
            boolean usePlaintext,
            String header);
    }

    @NonNull
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

    @NonNull
    private static final Map<Integer, LogLevel> LOG_LEVEL_FROM_C4;
    static {
        final Map<Integer, LogLevel> m = new HashMap<>();
        m.put(C4Constants.LogLevel.DEBUG, LogLevel.DEBUG);
        m.put(C4Constants.LogLevel.VERBOSE, LogLevel.VERBOSE);
        m.put(C4Constants.LogLevel.INFO, LogLevel.INFO);
        m.put(C4Constants.LogLevel.WARNING, LogLevel.WARNING);
        m.put(C4Constants.LogLevel.ERROR, LogLevel.ERROR);
        LOG_LEVEL_FROM_C4 = Collections.unmodifiableMap(m);
    }

    @NonNull
    private static final Map<String, LogDomain> LOGGING_DOMAINS_FROM_C4;
    static {
        final Map<String, LogDomain> m = new HashMap<>();
        m.put(C4Constants.LogDomain.DEFAULT, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.ACTOR, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.BLIP, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.BLIP_MESSAGES, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.BLOB, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.CHANGES, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.DATABASE, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.ENUM, LogDomain.QUERY);
        m.put(C4Constants.LogDomain.LISTENER, LogDomain.LISTENER);
        m.put(C4Constants.LogDomain.QUERY, LogDomain.QUERY);
        m.put(C4Constants.LogDomain.SQL, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.SYNC, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.SYNC_BUSY, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.TLS, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.WEB_SOCKET, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.ZIP, LogDomain.NETWORK);
        LOGGING_DOMAINS_FROM_C4 = Collections.unmodifiableMap(m);
    }

    @NonNull
    private static final Map<LogLevel, Integer> LOG_LEVEL_TO_C4;
    static {
        final Map<LogLevel, Integer> m = new HashMap<>();
        m.put(LogLevel.DEBUG, C4Constants.LogLevel.DEBUG);
        m.put(LogLevel.VERBOSE, C4Constants.LogLevel.VERBOSE);
        m.put(LogLevel.INFO, C4Constants.LogLevel.INFO);
        m.put(LogLevel.WARNING, C4Constants.LogLevel.WARNING);
        m.put(LogLevel.ERROR, C4Constants.LogLevel.ERROR);
        LOG_LEVEL_TO_C4 = Collections.unmodifiableMap(m);
    }

    @NonNull
    private static final AtomicReference<C4Log> LOGGER = new AtomicReference<>();

    @NonNull
    private static final AtomicReference<LogLevel> CALLBACK_LEVEL = new AtomicReference<>(LogLevel.NONE);

    // This method is used by reflection.  Don't change its signature.
    public static void logCallback(@Nullable String c4Domain, int c4Level, @Nullable String message) {
        get().logInternal((c4Domain == null) ? "???" : c4Domain, c4Level, (message == null) ? "" : message);
    }

    @NonNull
    public static C4Log get() { return Preconditions.assertNotNull(LOGGER.get(), "C4 logger"); }

    public static void set(@NonNull C4Log logger) { LOGGER.set(logger); }


    @NonNull
    private final NativeImpl impl;

    @VisibleForTesting
    public C4Log(@NonNull NativeImpl impl) { this.impl = impl; }

    public void logToCore(@NonNull LogDomain domain, @NonNull LogLevel level, @NonNull String message) {
        impl.nLog(getC4DomainForLoggingDomain(domain), getC4LevelForLogLevel(level), message);
    }

    public void initFileLogger(
        String path,
        LogLevel level,
        int maxRotate,
        long maxSize,
        boolean plainText,
        String header) {
        impl.nWriteToBinaryFile(path, getC4LevelForLogLevel(level), maxRotate, maxSize, plainText, header);
    }

    public void setFileLogLevel(LogLevel level) { impl.nSetBinaryFileLevel(getC4LevelForLogLevel(level)); }

    public void setLevels(int level, @Nullable String... domains) {
        if ((domains == null) || (domains.length <= 0)) { return; }
        for (String domain: domains) {
            if (domain != null) { impl.nSetLevel(domain, level); }
        }
    }

    @NonNull
    public LogLevel getCallbackLevel() { return CALLBACK_LEVEL.get(); }

    public void setCallbackLevel(@NonNull LogLevel consoleLevel) {
        final LogLevel newLogLevel = getCallbackLevel(consoleLevel, Database.log.getCustom());
        if (CALLBACK_LEVEL.getAndSet(newLogLevel) == newLogLevel) { return; }
        setCoreCallbackLevel();
    }

    // This, apparently, should be the inverse of LOGGING_DOMAINS_FROM_C4
    public void setC4LogLevel(@NonNull EnumSet<LogDomain> domains, @NonNull LogLevel level) {
        final int c4Level = getC4LevelForLogLevel(level);
        for (LogDomain domain: domains) {
            switch (domain) {
                case DATABASE:
                    setLevels(
                        c4Level,
                        C4Constants.LogDomain.DEFAULT,
                        C4Constants.LogDomain.BLOB,
                        C4Constants.LogDomain.CHANGES,
                        C4Constants.LogDomain.DATABASE,
                        C4Constants.LogDomain.SQL);
                    break;

                case LISTENER:
                    setLevels(c4Level, C4Constants.LogDomain.LISTENER);
                    break;

                case NETWORK:
                    setLevels(
                        c4Level,
                        C4Constants.LogDomain.BLIP,
                        C4Constants.LogDomain.BLIP_MESSAGES,
                        C4Constants.LogDomain.TLS,
                        C4Constants.LogDomain.WEB_SOCKET,
                        C4Constants.LogDomain.ZIP);
                    break;

                case QUERY:
                    setLevels(
                        c4Level,
                        C4Constants.LogDomain.ENUM,
                        C4Constants.LogDomain.QUERY);
                    break;

                case REPLICATOR:
                    setLevels(
                        c4Level,
                        C4Constants.LogDomain.ACTOR,
                        C4Constants.LogDomain.SYNC,
                        C4Constants.LogDomain.SYNC_BUSY);
                    break;

                default:
                    logInternal(
                        getC4DomainForLoggingDomain(LogDomain.DATABASE),
                        c4Level,
                        "Unexpected log domain: " + domain);
                    break;
            }
        }
    }

    @VisibleForTesting
    public void forceCallbackLevel(@NonNull LogLevel logLevel) {
        CALLBACK_LEVEL.set(logLevel);
        setCoreCallbackLevel();
    }

    @VisibleForTesting
    private void logInternal(@NonNull String c4Domain, int c4Level, @NonNull String message) {
        final LogLevel level = getLogLevelForC4Level(c4Level);
        final LogDomain domain = getLoggingDomainForC4Domain(c4Domain);

        final com.couchbase.lite.Log logger = Database.log;

        final ConsoleLogger console = logger.getConsole();
        console.log(level, domain, message);

        final Logger custom = logger.getCustom();
        if (custom != null) { custom.log(level, domain, message); }

        // This is necessary because there is no way to tell when the log level is set on a custom logger.
        // The only way to find out is to ask it.  As each new message comes in from Core,
        // we find the min level for the console and custom loggers and, if necessary, reset the callback level.
        final LogLevel newCallbackLevel = getCallbackLevel(console.getLevel(), custom);
        if (CALLBACK_LEVEL.getAndSet(newCallbackLevel) == newCallbackLevel) { return; }

        // This cannot be done synchronously because it will deadlock
        // on the same mutex that is being held for this callback
        CouchbaseLiteInternal.getExecutionService().getDefaultExecutor().execute(this::setCoreCallbackLevel);
    }

    @NonNull
    private LogLevel getLogLevelForC4Level(int c4Level) {
        final LogLevel level = LOG_LEVEL_FROM_C4.get(c4Level);
        return (level != null) ? level : LogLevel.INFO;
    }

    @NonNull
    private LogDomain getLoggingDomainForC4Domain(@Nullable String c4Domain) {
        final LogDomain domain = LOGGING_DOMAINS_FROM_C4.get(c4Domain);
        return (domain != null) ? domain : LogDomain.DATABASE;
    }

    @NonNull
    private String getC4DomainForLoggingDomain(@NonNull LogDomain domain) {
        final String c4Domain = LOGGING_DOMAINS_TO_C4.get(domain);
        return (c4Domain != null) ? c4Domain : C4Constants.LogDomain.DATABASE;
    }

    private int getC4LevelForLogLevel(@NonNull LogLevel logLevel) {
        final Integer c4level = LOG_LEVEL_TO_C4.get(logLevel);
        return (c4level != null) ? c4level : C4Constants.LogLevel.INFO;
    }

    private void setCoreCallbackLevel() { impl.nSetCallbackLevel(getC4LevelForLogLevel(CALLBACK_LEVEL.get())); }

    @NonNull
    private LogLevel getCallbackLevel(@NonNull LogLevel consoleLevel, @Nullable Logger customLogger) {
        if (customLogger == null) { return consoleLevel; }

        final LogLevel customLogLevel = customLogger.getLevel();
        return (customLogLevel.compareTo(consoleLevel) > 0) ? consoleLevel : customLogLevel;
    }
}
