package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A class that describes the file configuration for the {@link FileLogger} class.
 * These options must be set atomically so they won't take effect unless a new
 * configuration object is set on the logger.  Attempting to modify an in-use
 * configuration object will result in an exception being thrown.
 */
public final class LogFileConfiguration {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final boolean readonly;

    @NonNull
    private final String directory;
    private boolean usePlaintext;
    private int maxRotateCount = 1;
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

    /**
     * Constructs a file configuration object based on another one so
     * that it may be modified
     *
     * @param config The other configuration to copy settings from
     */
    LogFileConfiguration(@NonNull String directory, @Nullable LogFileConfiguration config, boolean readonly) {
        this(
            Preconditions.assertNotNull(directory, "directory"),
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
        this.maxSize = (maxSize == null) ? 1024 * 500 : maxSize;
        this.maxRotateCount = (maxRotateCount == null) ? 1 : maxRotateCount;
        this.usePlaintext = (usePlaintext != null) && usePlaintext;
        this.readonly = readonly;
    }


    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets whether or not to log in plaintext.  The default is
     * to log in a binary encoded format that is more CPU and I/O friendly
     * and enabling plaintext is not recommended in production.
     *
     * @param usePlaintext Whether or not to log in plaintext
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setUsePlaintext(boolean usePlaintext) {
        if (readonly) { throw new IllegalStateException("LogFileConfiguration is readonly mode."); }

        this.usePlaintext = usePlaintext;
        return this;
    }

    /**
     * Gets the number of rotated logs that are saved (i.e.
     * if the value is 1, then 2 logs will be present:  the 'current'
     * and the 'rotated')
     *
     * @return The number of rotated logs that are saved
     */
    public int getMaxRotateCount() { return maxRotateCount; }

    /**
     * Sets the number of rotated logs that are saved (i.e.
     * if the value is 1, then 2 logs will be present:  the 'current'
     * and the 'rotated')
     *
     * @param maxRotateCount The number of rotated logs to be saved
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setMaxRotateCount(int maxRotateCount) {
        if (readonly) { throw new IllegalStateException("LogFileConfiguration is readonly mode."); }

        this.maxRotateCount = maxRotateCount;
        return this;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Gets the max size of the log file in bytes.  If a log file
     * passes this size then a new log file will be started.  This
     * number is a best effort and the actual size may go over slightly.
     *
     * @return The max size of the log file in bytes
     */
    public long getMaxSize() { return maxSize; }

    /**
     * Sets the max size of the log file in bytes.  If a log file
     * passes this size then a new log file will be started.  This
     * number is a best effort and the actual size may go over slightly.
     *
     * @param maxSize The max size of the log file in bytes
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setMaxSize(long maxSize) {
        if (readonly) { throw new IllegalStateException("LogFileConfiguration is readonly mode."); }

        this.maxSize = maxSize;
        return this;
    }

    /**
     * Gets whether or not CBL is logging in plaintext.  The default is
     * to log in a binary encoded format that is more CPU and I/O friendly
     * and enabling plaintext is not recommended in production.
     *
     * @return Whether or not CBL is logging in plaintext
     */
    public boolean usesPlaintext() { return usePlaintext; }

    /**
     * Gets the directory that the logs files are stored in.
     *
     * @return The directory that the logs files are stored in.
     */
    @NonNull
    public String getDirectory() { return directory; }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof LogFileConfiguration)) { return false; }
        final LogFileConfiguration that = (LogFileConfiguration) o;
        return (maxRotateCount == that.maxRotateCount)
            && directory.equals(that.directory)
            && (maxSize == that.maxSize)
            && (usePlaintext == that.usePlaintext);
    }

    @Override
    public int hashCode() { return Objects.hash(directory); }
}
