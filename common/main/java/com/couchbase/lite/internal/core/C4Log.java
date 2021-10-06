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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;


public class C4Log {
    @VisibleForTesting
    @NonNull
    public static final AtomicReference<C4Log> LOGGER = new AtomicReference<>(new C4Log());

    @NonNull
    private static final AtomicReference<LogLevel> CALLBACK_LEVEL = new AtomicReference<>(LogLevel.NONE);

    @NonNull
    public static C4Log get() { return LOGGER.get(); }

    // This class and this method are referenced by name, from native code.
    public static void logCallback(@NonNull String c4Domain, int c4Level, @NonNull String message) {
        get().logInternal(c4Domain, c4Level, message);
    }


    public final void logToCore(String domain, int level, String message) { log(domain, level, message); }

    public final int getFileLogLevel() { return getBinaryFileLevel(); }

    public final void setFileFileLevel(int level) { setBinaryFileLevel(level); }

    public final void initFileLogger(
        String path,
        int level,
        int maxRotate,
        long maxSize,
        boolean plainText,
        String header) {
        writeToBinaryFile(path, level, maxRotate, maxSize, plainText, header);
    }

    public final void setLevels(int level, @Nullable String... domains) {
        if ((domains == null) || (domains.length <= 0)) { return; }
        for (String domain: domains) { setLevel(domain, level); }
    }

    public final void setCallbackLevel(@NonNull LogLevel consoleLevel) {
        final LogLevel newLogLevel = getCallbackLevel(consoleLevel, Database.log.getCustom());
        if (CALLBACK_LEVEL.getAndSet(newLogLevel) == newLogLevel) { return; }
        setCoreCallbackLevel();
    }

    @NonNull
    public final LogLevel getCallbackLevel() { return CALLBACK_LEVEL.get(); }

    @VisibleForTesting
    public final int getLogLevel(String domain) { return getLevel(domain); }

    @VisibleForTesting
    public final void forceCallbackLevel(@NonNull LogLevel logLevel) {
        CALLBACK_LEVEL.set(logLevel);
        setCoreCallbackLevel();
    }

    @VisibleForTesting
    protected void logInternal(@NonNull String c4Domain, int c4Level, @NonNull String message) {
        final LogLevel level = Log.getLogLevelForC4Level(c4Level);
        final LogDomain domain = Log.getLoggingDomainForC4Domain(c4Domain);

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

    private void setCoreCallbackLevel() {
        final LogLevel logLevel = CALLBACK_LEVEL.get();
        setCallbackLevel(Log.getC4LevelForLogLevel(logLevel));
    }

    @NonNull
    private LogLevel getCallbackLevel(@NonNull LogLevel consoleLevel, @Nullable Logger customLogger) {
        if (customLogger == null) { return consoleLevel; }

        final LogLevel customLogLevel = customLogger.getLevel();
        return (customLogLevel.compareTo(consoleLevel) > 0) ? consoleLevel : customLogLevel;
    }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native void log(String domain, int level, String message);

    private static native int getLevel(String domain);

    private static native void setLevel(String domain, int level);

    private static native void setCallbackLevel(int level);

    private static native int getBinaryFileLevel();

    private static native void setBinaryFileLevel(int level);

    private static native void writeToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header);
}
