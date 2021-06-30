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

import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;


public final class C4Log {
    private C4Log() {} // Utility class

    @NonNull
    private static LogLevel callbackLevel = LogLevel.NONE;

    @VisibleForTesting
    public static class RawLog {
        public final String domain;
        public final int level;
        public final String message;

        RawLog(String domain, int level, String message) {
            this.domain = domain;
            this.level = level;
            this.message = message;
        }

        @NonNull
        @Override
        public String toString() { return "RawLog{" + domain + "/" + level + ": " + message + "}"; }
    }

    private static Fn.Consumer<RawLog> rawListener;

    @VisibleForTesting
    public static void registerListener(Fn.Consumer<RawLog> listener) { rawListener = listener; }

    // This class and this method are referenced by name, from native code.
    public static void logCallback(@NonNull String c4Domain, int c4Level, @NonNull String message) {
        if (rawListener != null) { rawListener.accept(new RawLog(c4Domain, c4Level, message)); }

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
        if (callbackLevel == newCallbackLevel) { return; }

        // This cannot be done synchronously because it will deadlock on the same mutex that is being held
        // for this callback
        CouchbaseLiteInternal.getExecutionService().getDefaultExecutor().execute(() -> setCoreCallbackLevel(level));
    }

    public static void setLevels(int level, @Nullable String... domains) {
        if ((domains == null) || (domains.length <= 0)) { return; }
        for (String domain: domains) { setLevel(domain, level); }
    }

    public static void forceCallbackLevel(@NonNull LogLevel logLevel) {
        setCallbackLevel(Log.getC4LevelForLogLevel(logLevel));
        callbackLevel = logLevel;
    }

    public static void setCallbackLevel(@NonNull LogLevel consoleLevel) {
        setCoreCallbackLevel(getCallbackLevel(consoleLevel, Database.log.getCustom()));
    }

    private static void setCoreCallbackLevel(@NonNull LogLevel logLevel) {
        if (callbackLevel == logLevel) { return; }
        forceCallbackLevel(logLevel);
    }

    @NonNull
    private static LogLevel getCallbackLevel(@NonNull LogLevel consoleLevel, @Nullable Logger customLogger) {
        if (customLogger == null) { return consoleLevel; }

        final LogLevel customLogLevel = customLogger.getLevel();
        return (customLogLevel.compareTo(consoleLevel) > 0) ? consoleLevel : customLogLevel;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public static native void log(String domain, int level, String message);

    public static native int getBinaryFileLevel();

    public static native void setBinaryFileLevel(int level);

    public static native void writeToBinaryFile(
        String path,
        int level,
        int maxRotateCount,
        long maxSize,
        boolean usePlaintext,
        String header);

    @VisibleForTesting
    public static native int getLevel(String domain);

    static native void setCallbackLevel(int level);

    private static native void setLevel(String domain, int level);
}
