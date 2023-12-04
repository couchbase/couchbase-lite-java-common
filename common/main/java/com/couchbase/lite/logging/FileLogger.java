//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.logging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.Objects;

import com.couchbase.lite.Defaults;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.logging.AbstractLogger;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.logging.LoggersImpl;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Do not subclass!
 * This class will be final in future version of Couchbase Lite
 */
public class FileLogger extends AbstractLogger {
    public static final class Builder {
        @Nullable
        private final String directory;
        @NonNull
        private LogLevel level = LogLevel.WARNING;
        private int maxKeptFiles = Defaults.LogFile.MAX_ROTATE_COUNT;
        private long maxFileSize = Defaults.LogFile.MAX_SIZE;

        public Builder(@NonNull String directory) {
            final String logDirPath = Preconditions.assertNotEmpty(directory, "directory");
            final File logDir = new File(logDirPath);
            if (logDir.exists()) {
                if (!logDir.isDirectory()) {
                    Log.w(LogDomain.DATABASE, logDir.getAbsolutePath() + " is not a directory");
                }
            }
            else {
                if (!logDir.mkdirs()) {
                    Log.w(LogDomain.DATABASE, "Cannot create log directory: " + logDir.getAbsolutePath());
                }
            }
            if (!logDir.canWrite()) {
                Log.w(LogDomain.DATABASE, logDir.getAbsolutePath() + " is not writable");
            }

            this.directory = logDirPath;
        }

        public Builder(@NonNull FileLogger logger) {
            this(logger.directory);
            this.level = logger.getLevel();
            this.maxKeptFiles = logger.maxKeptFiles;
            this.maxFileSize = logger.maxFileSize;
        }

        @NonNull
        public LogLevel getLevel() { return level; }

        @NonNull
        public Builder setLevel(@NonNull LogLevel level) {
            this.level = Preconditions.assertNotNull(level, "log level");
            return this;
        }

        public int getMaxKeptFiles() { return maxKeptFiles; }

        @NonNull
        public Builder setMaxKeptFiles(int maxKeptFiles) {
            this.maxKeptFiles = Preconditions.assertNotNegative(maxKeptFiles, "max kept files");
            return this;
        }

        public long getMaxFileSize() { return maxFileSize; }

        @NonNull
        public Builder setMaxFileSize(long maxFileSize) {
            this.maxFileSize = Preconditions.assertNotNegative(maxFileSize, "max file size");
            return this;
        }

        @NonNull
        public FileLogger build() { return new FileLogger(this); }

        @NonNull
        @Override
        public String toString() {
            return "FileLogger.Builder{" + directory + ", " + level + ", " + maxFileSize + ", " + maxKeptFiles + "}";
        }
    }


    @NonNull
    private final String directory;
    private final int maxKeptFiles;
    private final long maxFileSize;
    private final boolean plainText;

    @VisibleForTesting
    public FileLogger(@NonNull Builder builder) { this(false, builder); }

    @Internal
    protected FileLogger(boolean plainText, @NonNull Builder builder) {
        super(builder.level);

        this.plainText = plainText;
        this.maxKeptFiles = builder.maxKeptFiles;
        this.maxFileSize = builder.maxFileSize;

        final String dir = builder.directory;
        if (dir == null) { throw new IllegalStateException("A file logger must specify a log file directory path"); }
        this.directory = dir;
    }

    @NonNull
    public final String getDirectory() { return directory; }

    public final int getMaxKeptFiles() { return maxKeptFiles; }

    public final long getMaxFileSize() { return maxFileSize; }

    @Deprecated
    public final boolean isPlainText() { return plainText; }

    @NonNull
    @Override
    public final String toString() {
        return "FileLogger{"
            + ((plainText) ? "+" : "")
            + directory + ", " + getLevel() + ", " + maxFileSize + ", " + maxKeptFiles + "}";
    }

    @Override
    public final int hashCode() { return Objects.hash(directory, maxKeptFiles, maxFileSize); }

    @Override
    public final boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof FileLogger)) { return false; }
        final FileLogger other = (FileLogger) o;
        return similar(other) && (getLevel() == other.getLevel());
    }

    public final boolean similar(@Nullable FileLogger other) {
        return (other != null)
            && (directory.equals(other.directory))
            && (maxKeptFiles == other.maxKeptFiles)
            && (maxFileSize == other.maxFileSize)
            && (plainText == other.plainText);
    }

    @Internal
    @Override
    protected void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        LoggersImpl.logToCore(level, domain, message);
    }
}
