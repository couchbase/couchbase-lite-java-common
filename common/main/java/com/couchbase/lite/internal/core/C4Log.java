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
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.core.impl.NativeC4Log;
import com.couchbase.lite.internal.logging.LogSinksImpl;


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

    // This method is used by reflection.  Don't change its signature.
    public static void logCallback(@Nullable String c4Domain, int c4Level, @Nullable String message) {
        LogSinksImpl.logFromCore(getLogLevelForC4Level(c4Level), getLoggingDomainForC4Domain(c4Domain), message);
    }

    @NonNull
    public static C4Log create() { return new C4Log(new NativeC4Log()); }

    @NonNull
    private static LogLevel getLogLevelForC4Level(int c4Level) {
        final LogLevel level = LOG_LEVEL_FROM_C4.get(c4Level);
        return (level != null) ? level : LogLevel.INFO;
    }

    @NonNull
    private static LogDomain getLoggingDomainForC4Domain(@Nullable String c4Domain) {
        final LogDomain domain = LOGGING_DOMAINS_FROM_C4.get(c4Domain);
        return (domain != null) ? domain : LogDomain.DATABASE;
    }


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

    public void setFileLogLevel(@NonNull LogLevel newLevel) {
        impl.nSetBinaryFileLevel(getC4LevelForLogLevel(newLevel));
    }

    public void setCallbackLevel(@NonNull LogLevel newLevel) {
        impl.nSetCallbackLevel(getC4LevelForLogLevel(newLevel));
    }

    public void setLogLevel(@NonNull LogLevel newLevel) {
        final int level = getC4LevelForLogLevel(newLevel);
        // ??? this loop might be pushed into the JNI
        // when the legacy API is removed.
        for (String domain: LOGGING_DOMAINS_FROM_C4.keySet()) { setLogLevel(domain, level); }
    }

    @VisibleForTesting
    public void setLogLevel(@NonNull LogDomain domain, @NonNull LogLevel level) {
        setLogLevel(getC4DomainForLoggingDomain(domain), getC4LevelForLogLevel(level));
    }

    @VisibleForTesting
    public void setLogLevel(@NonNull String domain, int level) { impl.nSetLevel(domain, level); }

    @NonNull
    private String getC4DomainForLoggingDomain(@NonNull LogDomain domain) {
        final String c4Domain = LOGGING_DOMAINS_TO_C4.get(domain);
        return (c4Domain != null) ? c4Domain : C4Constants.LogDomain.DATABASE;
    }

    private int getC4LevelForLogLevel(@NonNull LogLevel logLevel) {
        final Integer c4level = LOG_LEVEL_TO_C4.get(logLevel);
        return (c4level != null) ? c4level : C4Constants.LogLevel.INFO;
    }
}
