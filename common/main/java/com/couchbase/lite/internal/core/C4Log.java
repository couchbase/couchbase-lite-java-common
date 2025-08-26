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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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

    @VisibleForTesting
    public interface Instrumentation {
        boolean onCallback(@Nullable String c4Domain, int c4Level, @Nullable String message);
        boolean onLogToCore(@NonNull LogDomain domain, @NonNull LogLevel level, @NonNull String message);
    }

    // Prevent the LiteCore thread that invokes the logging callback,
    // from making a direct recursive call to the LiteCore logger.
    // Note that this actually leaves a couple of big holes:
    // - Before the logging thread ever gets here, it has to attach the JVM. That will probably cause allocation
    //   which may, in turn run GC code on the calling thread, which may attempt to free a LiteCore object... which
    //   may cause logging
    // - Anything this thread does that causes a call into LiteCore (allocation, like the above case, or
    //   a call to some rando LiteCore function) may cause LiteCore to try to log something.  Boom.
    private static final class CallbackGuard extends ThreadLocal<Boolean> {
        @NonNull
        @Override
        protected Boolean initialValue() { return Boolean.FALSE; }

        public boolean isInUse() {
            final Boolean val = super.get();
            return (val != null) && val;
        }

        public void setInUse(boolean val) { super.set(val); }

        // Just being careful....
        @Override
        public void set(@Nullable Boolean value) { throw new UnsupportedOperationException("set not supported"); }

        @Nullable
        @Override
        public Boolean get() { throw new UnsupportedOperationException("get not supported"); }
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

    @NonNull
    private static final Map<String, LogDomain> LOGGING_DOMAIN_FROM_C4;
    static {
        final Map<String, LogDomain> m = new HashMap<>();
        m.put(C4Constants.LogDomain.DEFAULT, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.ACTOR, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.BLIP, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.BLIP_MESSAGES, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.BLOB, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.CHANGES, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.DATABASE, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.MDNS, LogDomain.PEER_DISCOVERY);
        m.put(C4Constants.LogDomain.DISCOVERY, LogDomain.PEER_DISCOVERY);
        m.put(C4Constants.LogDomain.ENUM, LogDomain.QUERY);
        m.put(C4Constants.LogDomain.LISTENER, LogDomain.LISTENER);
        m.put(C4Constants.LogDomain.P2P, LogDomain.MULTIPEER);
        m.put(C4Constants.LogDomain.QUERY, LogDomain.QUERY);
        m.put(C4Constants.LogDomain.SQL, LogDomain.DATABASE);
        m.put(C4Constants.LogDomain.SYNC, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.SYNC_BUSY, LogDomain.REPLICATOR);
        m.put(C4Constants.LogDomain.TLS, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.WEB_SOCKET, LogDomain.NETWORK);
        m.put(C4Constants.LogDomain.ZIP, LogDomain.NETWORK);
        LOGGING_DOMAIN_FROM_C4 = Collections.unmodifiableMap(m);
    }

    @NonNull
    private static final Map<LogDomain, Set<String>> LOGGING_DOMAIN_TO_C4;
    private static final Set<String> KNOWN_C4_LOGGING_DOMAINS;
    static {
        final Set<String> s = new HashSet<>();
        final Map<LogDomain, Set<String>> m = new HashMap<>();
        for (Map.Entry<String, LogDomain> entry: LOGGING_DOMAIN_FROM_C4.entrySet()) {
            final LogDomain domain = entry.getValue();

            Set<String> domains = m.get(domain);
            if (domains == null) {
                domains = new HashSet<>();
                m.put(domain, domains);
            }

            domains.add(entry.getKey());
        }
        for (Map.Entry<LogDomain, Set<String>> entry: m.entrySet()) {
            final Set<String> domains = entry.getValue();
            m.put(entry.getKey(), Collections.unmodifiableSet(domains));
            s.addAll(domains);
        }
        LOGGING_DOMAIN_TO_C4 = Collections.unmodifiableMap(m);
        KNOWN_C4_LOGGING_DOMAINS = Collections.unmodifiableSet(s);
    }

    @NonNull
    private static final Map<LogDomain, String> LOGGING_DOMAIN_TO_CANONICAL_C4;
    static {
        final Map<LogDomain, String> m = new HashMap<>();
        m.put(LogDomain.DATABASE, C4Constants.LogDomain.DATABASE);
        m.put(LogDomain.NETWORK, C4Constants.LogDomain.WEB_SOCKET);
        m.put(LogDomain.REPLICATOR, C4Constants.LogDomain.SYNC);
        m.put(LogDomain.QUERY, C4Constants.LogDomain.QUERY);
        m.put(LogDomain.LISTENER, C4Constants.LogDomain.LISTENER);
        m.put(LogDomain.MULTIPEER, C4Constants.LogDomain.P2P);
        m.put(LogDomain.PEER_DISCOVERY, C4Constants.LogDomain.DISCOVERY);
        LOGGING_DOMAIN_TO_CANONICAL_C4 = Collections.unmodifiableMap(m);
    }

    private static final CallbackGuard CALLBACK = new CallbackGuard();

    private static final AtomicReference<Instrumentation> CALLBACK_INSTRUMENTATION
        = new AtomicReference<>(null);

    // This method is used by reflection.  Don't change its signature.
    public static void logCallback(@Nullable String c4Domain, int c4Level, @Nullable String message) {
        CALLBACK.setInUse(true);

        final Instrumentation instrumentation = CALLBACK_INSTRUMENTATION.get();
        if ((instrumentation == null) || instrumentation.onCallback(c4Domain, c4Level, message)) {
            LogSinksImpl.logFromCore(getLogLevelForC4Level(c4Level), getLoggingDomainForC4Domain(c4Domain), message);
        }

        CALLBACK.setInUse(false);
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
        final LogDomain domain = LOGGING_DOMAIN_FROM_C4.get(c4Domain);
        return (domain != null) ? domain : LogDomain.DATABASE;
    }


    @NonNull
    private final NativeImpl impl;

    private C4Log(@NonNull NativeImpl impl) { this.impl = impl; }

    public void logToCore(@NonNull LogDomain domain, @NonNull LogLevel level, @NonNull String message) {
        if (CALLBACK.isInUse()) {
            LogSinksImpl.logFailure("logToCore", message, null);
            return;
        }

        final Instrumentation instrumentation = CALLBACK_INSTRUMENTATION.get();
        if ((instrumentation == null) || instrumentation.onLogToCore(domain, level, message)) {
            // Yes, there is a small race here...
            impl.nLog(getCanonicalC4DomainForLoggingDomain(domain), getC4LevelForLogLevel(level), message);
        }
    }

    public void initFileLogging(
        String path,
        LogLevel level,
        int maxKept,
        long maxSize,
        boolean plainText,
        String header) {
        impl.nWriteToBinaryFile(path, getC4LevelForLogLevel(level), maxKept - 1, maxSize, plainText, header);
    }

    public void setFileLogLevel(@NonNull LogLevel newLevel) {
        impl.nSetBinaryFileLevel(getC4LevelForLogLevel(newLevel));
    }

    public void setCallbackLevel(@NonNull LogLevel newLevel) {
        impl.nSetCallbackLevel(getC4LevelForLogLevel(newLevel));
    }

    // ??? Most of this function might be pushed into the JNI
    public void setLogFilter(
        @NonNull LogLevel fileLevel,
        @NonNull LogLevel platformLevel,
        @NonNull Set<LogDomain> platformDomains) {
        final Set<String> fileDomains = new HashSet<>(KNOWN_C4_LOGGING_DOMAINS);

        // If the platform wants logging for its domains at higher levels than the file
        // log wants, set the levels for its domains separately.  The file logger will
        // get more than it wants for those domains. Tough.
        final Set<String> domains = getC4DomainsForLoggingDomains(platformDomains);
        if (platformLevel.compareTo(fileLevel) < 0) {
            for (String domain: domains) { setLogLevel(domain, platformLevel); }
            fileDomains.removeAll(domains);
        }

        // Any domain for which the platform level is greater than or equal to
        // the file log's level has to log at the file log level.
        for (String domain: fileDomains) { setLogLevel(domain, fileLevel); }
    }

    @VisibleForTesting
    public void setLogLevel(@NonNull LogDomain domain, @NonNull LogLevel level) {
        final Set<String> c4Domains = LOGGING_DOMAIN_TO_C4.get(domain);
        if (c4Domains == null) {
            return;
        }

        for (String c4Domain : c4Domains) {
            setLogLevel(c4Domain, getC4LevelForLogLevel(level));
        }
    }

    @VisibleForTesting
    public void setCallbackInstrumentation(@Nullable Instrumentation instrumentation) {
        CALLBACK_INSTRUMENTATION.set(instrumentation);
    }

    private void setLogLevel(@NonNull String domain, @NonNull LogLevel level) {
        setLogLevel(domain, getC4LevelForLogLevel(level));
    }

    private void setLogLevel(@NonNull String domain, int level) {
        if (CALLBACK.isInUse()) {
            LogSinksImpl.logFailure("setLogLevel", null, null);
            return;
        }
        // Yes, there is a small race here...
        impl.nSetLevel(domain, level);
    }

    @NonNull
    private Set<String> getC4DomainsForLoggingDomains(@NonNull Set<LogDomain> domains) {
        final Set<String> newC4Domains = new HashSet<>();
        for (LogDomain domain: domains) {
            final Set<String> c4Domains = LOGGING_DOMAIN_TO_C4.get(domain);
            if (c4Domains != null) { newC4Domains.addAll(c4Domains); }
        }
        return newC4Domains;
    }

    @NonNull
    private String getCanonicalC4DomainForLoggingDomain(@NonNull LogDomain domain) {
        final String c4Domain = LOGGING_DOMAIN_TO_CANONICAL_C4.get(domain);
        return (c4Domain != null) ? c4Domain : C4Constants.LogDomain.DATABASE;
    }

    private int getC4LevelForLogLevel(@NonNull LogLevel logLevel) {
        final Integer c4level = LOG_LEVEL_TO_C4.get(logLevel);
        return (c4level != null) ? c4level : C4Constants.LogLevel.INFO;
    }
}
