//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.DatabaseConfiguration;


/**
 * A bit odd.  Why are these properties not simply properties on the AbstractDatabase object?
 * Because they are mandated by a spec:
 * https://docs.google.com/document/d/16XmIOw7aZ_NcFc6Dy6fc1jV7sc994r6iv5qm9_J7qKo/edit#heading=h.kt1n12mtpzx4
 */
public class BaseImmutableDatabaseConfiguration {
    //-------------------------------------------------------------------------
    // Data members
    //-------------------------------------------------------------------------
    @NonNull
    private final String dbDir;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------
    protected BaseImmutableDatabaseConfiguration(@Nullable DatabaseConfiguration config) {
        final String dbDirectory = (config == null) ? null : config.getDirectory();
        this.dbDir = (dbDirectory != null) ? dbDirectory : CouchbaseLiteInternal.getDefaultDbDirPath();
    }

    //-------------------------------------------------------------------------
    // Properties
    //-------------------------------------------------------------------------
    @NonNull
    public final String getDirectory() { return dbDir; }
}
