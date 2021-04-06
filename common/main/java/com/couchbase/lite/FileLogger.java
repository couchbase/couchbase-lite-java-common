//
// Copyright (c) 2020, 2018 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A logger for writing to the LiteCore file logging system.
 */
public final class FileLogger {
    public static final class Builder {
        private String directory;
        private Boolean usePlaintext;
        private int maxKeptFiles;
        private long maxFileSize;
        private LogLevel logLevel;

        public Builder(@NonNull String logPath) {
            this(new File(Preconditions.assertNotNull(logPath, "directory path")));
        }

        public Builder(@NonNull File logDir) {
            Preconditions.assertNotNull(logDir, "log directory");
            this.directory = checkDir(logDir);
        }

        public Builder(@NonNull FileLogger logger) {
            Preconditions.assertNotNull(logger, "logger");
            directory = logger.directory;
            usePlaintext = logger.usePlaintext;
            maxKeptFiles = logger.maxKeptFiles;
            maxFileSize = logger.maxFileSize;
            logLevel = logger.logLevel;
        }

        /**
         * Sets the root directory in which the log files will be cataloged.
         *
         * @param directory The path to a writable directory
         * @return this
         */
        @NonNull
        public Builder setDirectory(@NonNull String directory) {
            this.directory = checkDir(new File(directory));
            return this;
        }

        /**
         * Sets lowest level logs that this logger will show.
         *
         * @param logLevel lowest level logs that this logger will show.
         * @return this
         */
        @NonNull
        public Builder setLevel(@NonNull LogLevel logLevel) {
            Preconditions.assertNotNull(logLevel, "log level");
            this.logLevel = logLevel;
            return this;
        }

        /**
         * Sets whether or not to log in plaintext.  The default is to log in a binary encoded format
         * that is more CPU and I/O friendly.  Enabling plaintext is not recommended in production.
         *
         * @param usePlaintext Whether or not to log in plaintext
         * @return this
         */
        @NonNull
        public Builder setUsePlaintext(boolean usePlaintext) {
            this.usePlaintext = usePlaintext;
            return this;
        }

        /**
         * Sets the number of rotated logs to be saved.  For example, if the value is 1, then 2 logs will be present:
         * the 'current' and one that was 'rotated' out,
         * An argument of 0 will turn file rotation off.
         * The default value is 1.
         *
         * @param maxRotateCount The number of rotated logs to be saved
         * @return this
         */
        @NonNull
        public Builder setMaxRotateCount(int maxRotateCount) {
            this.maxKeptFiles = Preconditions.assertNotNegative(maxRotateCount, "max rotation");
            return this;
        }

        /**
         * Sets the the maximum size (in bytes) to which a log file can grow, before it is rotated.
         * Remember that there may be 5 * maxRotate files, each of this size.
         * The minimum size is 1K.
         * The default size is 500K.
         * An argument of Integer.MAX_VALUE will let each file grow to 0.2G
         *
         * @param maxFileSize the max size for a log file
         * @return this
         */
        @NonNull
        public Builder setMaxFileSize(int maxFileSize) {
            if (maxFileSize < 1024) {
                throw new IllegalArgumentException("Ridiculously small log file size: " + maxFileSize);
            }
            this.maxFileSize = maxFileSize;
            return this;
        }

        public FileLogger build() {
            if (directory == null) { throw new IllegalStateException("A file logger must specify a log directory"); }

            return new FileLogger(
                directory,
                (logLevel == null) ? LogLevel.WARNING : logLevel,
                (usePlaintext != null) && usePlaintext,
                (maxKeptFiles <= 0) ? 1 : maxKeptFiles,
                (maxFileSize <= 0) ? 1024 * 500 : maxFileSize);
        }

        @NonNull
        private String checkDir(@NonNull File logDir) {
            if ((logDir.exists() || logDir.mkdirs()) && (logDir.isDirectory() && logDir.canWrite())) {
                try { return logDir.getCanonicalPath(); }
                catch (IOException ignore) { }
            }

            throw new IllegalArgumentException("Cannot find writable directory: " + directory);
        }
    }


    private final String directory;
    private final LogLevel logLevel;
    private final boolean usePlaintext;
    private final int maxKeptFiles;
    private final long maxFileSize;

    /**
     * @param directory    the root directory into which log files will be put.
     * @param logLevel     the minimum level for log messages that this logger will log.
     * @param usePlaintext Log in plaintext: binary is faster, smaller, and default.
     * @param maxKeptFiles max number of rotated logs to keep: default is 1
     * @param maxFileSize  max log file size before rotated: default is 500K
     */
    private FileLogger(
        @NonNull String directory,
        @NonNull LogLevel logLevel,
        boolean usePlaintext,
        int maxKeptFiles,
        long maxFileSize) {
        this.directory = directory;
        this.logLevel = logLevel;
        this.usePlaintext = usePlaintext;
        this.maxKeptFiles = maxKeptFiles;
        this.maxFileSize = maxFileSize;
    }

    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (level.compareTo(logLevel) < 0) { return; }
        C4Log.logToFile(level, domain, message);
    }

    @NonNull
    public LogLevel getLevel() { return logLevel; }

    public String getLogDir() { return directory; }

    public boolean usePlaintext() { return usePlaintext; }

    public int getMaxKeptFiles() { return maxKeptFiles; }

    public long getMaxFileSize() { return maxFileSize; }

    boolean isSimilarTo(@Nullable FileLogger other) {
        return (other != null)
            && directory.equals(other.directory)
            && (usePlaintext == other.usePlaintext)
            && (maxKeptFiles == other.maxKeptFiles)
            && (maxFileSize == other.maxFileSize);
    }

    @NonNull
    @Override
    public String toString() {
        return "FileLogger{@" + directory
            + "#" + logLevel
            + ": " + maxKeptFiles
            + " * "
            + maxKeptFiles
            + ", "
            + usePlaintext + "}";
    }
}

