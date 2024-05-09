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
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.Objects;

import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A logger for writing to a file in the application's storage so
 * that log messages can persist durably after the application has
 * stopped or encountered a problem.  Each log level is written to
 * a separate file.
 * <p>
 * Threading policy: This class is certain to be used from multiple
 * threads.  As long as it, itself, is thread safe, the various race conditions
 * are unlikely and the penalties very small.  "Volatile" ensures
 * the thread safety and the several races are tolerable.
 */
public final class FileLogger implements Logger {
    @NonNull
    private final C4Log c4Log;
    @Nullable
    private volatile LogFileConfiguration config;
    @NonNull
    private volatile LogLevel logLevel = LogLevel.NONE;

    // The singleton instance is available from Database.log.getFile()
    FileLogger(@NonNull C4Log c4Log) { this.c4Log = c4Log; }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        Preconditions.assertNotNull(level, "level");
        Preconditions.assertNotNull(domain, "domain");
        if ((config == null) || (level.compareTo(logLevel) < 0)) { return; }
        c4Log.logToCore(domain, level, message);
    }

    @NonNull
    @Override
    public LogLevel getLevel() { return logLevel; }

    /**
     * Sets the overall logging level that will be written to the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        if (config == null) { throw new CouchbaseLiteError(Log.lookupStandardMessage("CannotSetLogLevel")); }

        if (logLevel == level) { return; }

        c4Log.setFileLogLevel(level);
        logLevel = level;

        if (level == LogLevel.NONE) { Log.warn(); }
    }

    /**
     * Gets the configuration currently in use by the file logger.
     * Note that once a configuration has been installed in a logger,
     * it is read-only and can no longer be modified.
     * An attempt to modify the configuration returned by this method will cause an exception.
     *
     * @return The configuration currently in use
     */
    @Nullable
    public LogFileConfiguration getConfig() { return config; }

    /**
     * Sets the configuration for use by the file logger.
     *
     * @param newConfig The configuration to use
     */
    public void setConfig(@Nullable LogFileConfiguration newConfig) {
        final LogFileConfiguration oldConfig = config;
        if (Objects.equals(newConfig, oldConfig)) { return; }

        if ((newConfig == null) || newConfig.getDirectory().isEmpty()) {
            reset(oldConfig != null);
            Log.warn();
            return;
        }

        final String logDirPath = newConfig.getDirectory();
        final File logDir = new File(logDirPath);
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                Log.w(LogDomain.DATABASE, "Cannot create log directory: " + logDir.getAbsolutePath());
                return;
            }
        }
        else {
            if (!logDir.isDirectory()) {
                Log.w(LogDomain.DATABASE, logDir.getAbsolutePath() + " is not a directory");
                return;
            }

            if (!logDir.canWrite()) {
                Log.w(LogDomain.DATABASE, logDir.getAbsolutePath() + " is not writable");
                return;
            }
        }

        final LogFileConfiguration cfg = new LogFileConfiguration(logDirPath, newConfig, true);
        c4Log.initFileLogger(
            logDirPath,
            logLevel,
            cfg.getMaxRotateCount(),
            cfg.getMaxSize(),
            cfg.usesPlaintext(),
            CBLVersion.getVersionInfo());

        config = cfg;
    }

    @VisibleForTesting
    void reset(boolean hard) {
        config = null;
        logLevel = LogLevel.NONE;
        if (hard) { c4Log.initFileLogger("", LogLevel.NONE, 0, 0, false, ""); }
    }
}
