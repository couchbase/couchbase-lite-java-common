//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A class that describes the file configuration for the {@link FileLogger} class.
 * Once a configuration has been assigned to a Logger, it becomes read-only:
 * an attempt to mutate it will cause an exception.
 * To change the configuration of a logger, copy its configuration, mutate the
 * copy and then use it to replace the loggers current configuration.
 */
public final class LogFileConfiguration {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final boolean readonly;

    @NonNull
    private final String directory;

    private boolean usePlaintext;
    private int maxRotateCount;
    private long maxSize;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructs a file configuration object with the given directory
     *
     * @param directory The directory that the logs will be written to
     */
    public LogFileConfiguration(@NonNull String directory) { this(directory, null); }

    /**
     * Constructs a file configuration object based on another one so
     * that it may be modified
     *
     * @param config The other configuration to copy settings from
     */
    public LogFileConfiguration(@NonNull LogFileConfiguration config) {
        this((config == null) ? null : config.getDirectory(), config);
    }

    /**
     * Constructs a file configuration object based on another one but changing
     * the directory
     *
     * @param directory The directory that the logs will be written to
     * @param config    The other configuration to copy settings from
     */
    public LogFileConfiguration(@NonNull String directory, @Nullable LogFileConfiguration config) {
        this(directory, config, false);
    }

    LogFileConfiguration(@NonNull String directory, @Nullable LogFileConfiguration config, boolean readonly) {
        this(
            directory,
            (config == null) ? null : config.maxSize,
            (config == null) ? null : config.maxRotateCount,
            (config == null) ? null : config.usePlaintext,
            readonly);
    }

    LogFileConfiguration(
        @NonNull String directory,
        @Nullable Long maxSize,
        @Nullable Integer maxRotateCount,
        @Nullable Boolean usePlaintext,
        boolean readonly) {
        this.directory = Preconditions.assertNotNull(directory, "directory");
        this.maxSize = (maxSize != null) ? maxSize : Defaults.LogFile.MAX_SIZE;
        this.maxRotateCount = (maxRotateCount != null) ? maxRotateCount : Defaults.LogFile.MAX_ROTATE_COUNT;
        this.usePlaintext = (usePlaintext != null) ? usePlaintext : Defaults.LogFile.USE_PLAINTEXT;
        this.readonly = readonly;
    }

    @Override
    public int hashCode() { return Objects.hash(directory); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof LogFileConfiguration)) { return false; }
        final LogFileConfiguration that = (LogFileConfiguration) o;
        return (maxSize == that.maxSize)
            && (maxRotateCount == that.maxRotateCount)
            && (usePlaintext == that.usePlaintext)
            && directory.equals(that.directory);
    }

    @NonNull
    @Override
    public String toString() {
        return "LogFileConfig{"
            + ((readonly) ? "" : "!")
            + ((usePlaintext) ? "+" : "")
            + directory + ", " + maxSize + ", " + maxRotateCount + "}";
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets the max size of the log file in bytes.  If a log file
     * passes this size then a new log file will be started.  This
     * number is a best effort and the actual size may go over slightly.
     * The default size is 500Kb.
     *
     * @param maxSize The max size of the log file in bytes
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setMaxSize(long maxSize) {
        if (readonly) { throw new CouchbaseLiteError("LogFileConfiguration is readonly mode."); }

        this.maxSize = Preconditions.assertNotNegative(maxSize, "max size");
        return this;
    }

    /**
     * Sets the number of rotated logs that are saved.  For instance,
     * if the value is 1 then 2 logs will be present: the 'current' log
     * and the previous 'rotated' log.
     * The default value is 1.
     *
     * @param maxRotateCount The number of rotated logs to be saved
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setMaxRotateCount(int maxRotateCount) {
        if (readonly) { throw new CouchbaseLiteError("LogFileConfiguration is readonly mode."); }

        this.maxRotateCount = Preconditions.assertNotNegative(maxRotateCount, "max rotation count");
        return this;
    }

    /**
     * Sets whether or not CBL logs in plaintext.  The default (false) is
     * to log in a binary encoded format that is more CPU and I/O friendly.
     * Enabling plaintext is not recommended in production.
     *
     * @param usePlaintext Whether or not to log in plaintext
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setUsePlaintext(boolean usePlaintext) {
        if (readonly) { throw new CouchbaseLiteError("LogFileConfiguration is readonly mode."); }

        this.usePlaintext = usePlaintext;
        return this;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Gets the directory that the logs files are stored in.
     *
     * @return The directory that the logs files are stored in.
     */
    @NonNull
    public String getDirectory() { return directory; }

    /**
     * Gets the max size of the log file in bytes.  If a log file
     * passes this size then a new log file will be started.  This
     * number is a best effort and the actual size may go over slightly.
     * The default size is 500Kb.
     *
     * @return The max size of the log file in bytes
     */
    public long getMaxSize() { return maxSize; }

    /**
     * Gets the number of rotated logs that are saved.  For instance,
     * if the value is 1 then 2 logs will be present: the 'current' log
     * and the previous 'rotated' log.
     * The default value is 1.
     *
     * @return The number of rotated logs that are saved
     */
    public int getMaxRotateCount() { return maxRotateCount; }

    /**
     * Gets whether or not CBL is logging in plaintext.  The default (false) is
     * to log in a binary encoded format that is more CPU and I/O friendly.
     * Enabling plaintext is not recommended in production.
     *
     * @return Whether or not CBL is logging in plaintext
     */
    public boolean usesPlaintext() { return usePlaintext; }
}
