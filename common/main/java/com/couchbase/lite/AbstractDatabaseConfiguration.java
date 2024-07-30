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

import com.couchbase.lite.internal.BaseImmutableDatabaseConfiguration;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Preconditions;


abstract class AbstractDatabaseConfiguration {
    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    private String dbDirectory;
    private boolean fullSync;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    protected AbstractDatabaseConfiguration() { this(null, Defaults.Database.FULL_SYNC); }

    protected AbstractDatabaseConfiguration(@Nullable AbstractDatabaseConfiguration config) {
        this(
            (config == null) ? null : config.getDirectory(),
            (config == null) ? Defaults.Database.FULL_SYNC : config.isFullSync()
        );
    }

    protected AbstractDatabaseConfiguration(@NonNull BaseImmutableDatabaseConfiguration config) {
        this(config.getDirectory(), config.isFullSync());
    }

    private AbstractDatabaseConfiguration(@Nullable String dbDir, boolean fullSync) {
        CouchbaseLiteInternal.requireInit("Cannot create database configuration");
        this.dbDirectory = (dbDir != null) ? dbDir : CouchbaseLiteInternal.getDefaultDbDirPath();
        this.fullSync = fullSync;
    }

    //---------------------------------------------
    // Public API
    //---------------------------------------------

    /**
     * Set the canonical path of the directory in which to store the database.
     * If the directory doesn't already exist it will be created.
     * If it cannot be created an CouchbaseLiteError will be thrown.
     *
     * Note: The directory set by this method is the canonical path to the
     * directory whose path is passed.  It is *NOT* necessarily the case that
     * directory.equals(config.setDirectory(directory).getDirectory())
     *
     * @param directory the directory
     * @return this
     * @throws CouchbaseLiteError if the directory does not exist and cannot be created
     */
    @NonNull
    public DatabaseConfiguration setDirectory(@NonNull String directory) {
        Preconditions.assertNotNull(directory, "directory");

        // a bunch of code assumes that this string is the *canonical* path to the directory
        dbDirectory = FileUtils.verifyDir(directory).getAbsolutePath();

        return getDatabaseConfiguration();
    }

    /**
     * Returns the path to the directory that contains the database.
     * If this path has not been set explicitly (see: <code>setDirectory</code> below),
     * then it is the system default.
     *
     * Note: The directory returned by this method is the canonical path to the
     * directory whose path was set.  It is *NOT* necessarily the case that
     * directory.equals(config.setDirectory(directory).getDirectory())
     *
     * @return the database directory
     */
    @NonNull
    public String getDirectory() { return dbDirectory; }

    /**
     * As Couchbase Lite normally configures its databases, there is a very small (though non-zero) chance that a
     * power failure at just the wrong time could cause the most recently committed transaction's changes to be lost.
     * This would cause the database to appear as it did immediately before that transaction. Setting this mode true
     * ensures that an operating system crash or power failure will not cause the loss of any data. Full sync mode is
     * very safe but it is also <b>dramatically</b> slower.
     */
    @NonNull
    public DatabaseConfiguration setFullSync(boolean isfullSync) {
        this.fullSync = isfullSync;
        return getDatabaseConfiguration();
    }

    public boolean isFullSync() { return fullSync; }


    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    @NonNull
    protected abstract DatabaseConfiguration getDatabaseConfiguration();
}
