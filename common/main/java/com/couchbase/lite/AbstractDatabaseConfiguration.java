//
// Copyright (c) 2020, 2018 Couchbase, Inc.  All rights reserved.
//
// Licensed under the Couchbase License Agreement (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
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

import com.couchbase.lite.internal.BaseImmutableDatabaseConfiguration;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Preconditions;


abstract class AbstractDatabaseConfiguration {
    //---------------------------------------------
    // Data Members
    //---------------------------------------------
    private String dbDirectory;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    protected AbstractDatabaseConfiguration() { this((String) null); }

    protected AbstractDatabaseConfiguration(@Nullable AbstractDatabaseConfiguration config) {
        this((config == null) ? null : config.getDirectory());
    }

    protected AbstractDatabaseConfiguration(@Nullable BaseImmutableDatabaseConfiguration config) {
        this((config == null) ? null : config.getDirectory());
    }

    protected AbstractDatabaseConfiguration(@Nullable String dbDir) {
        CouchbaseLiteInternal.requireInit("Cannot create database configuration");
        this.dbDirectory = (dbDir != null) ? dbDir : CouchbaseLiteInternal.getRootDirPath();
    }

    //---------------------------------------------
    // Public API
    //---------------------------------------------

    /**
     * Set the canonical path of the directory to store the database in.
     * If the directory doesn't already exist it will be created.
     * If it cannot be created throw an IllegalStateException
     *
     * @param directory the directory
     * @return this.
     * @throws IllegalStateException if the directory does not exist anc cannot be created
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
     * @return the database directory
     */
    @NonNull
    public String getDirectory() { return dbDirectory; }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    @NonNull
    protected abstract DatabaseConfiguration getDatabaseConfiguration();
}
