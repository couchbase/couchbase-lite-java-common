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

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() { }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     *
     * @param ctxt the ApplicationContext.
     * @throws CouchbaseLiteError on initialization failure
     */
    public static void init(@NonNull Context ctxt) { init(ctxt, BuildConfig.CBL_DEBUG); }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     * Debugging mode is not supported for client code.  Please use it only when advised to do
     * so by Couchbase Support Engineering
     *
     * @param debug true to enable debugging (Unsupported)
     * @throws CouchbaseLiteError on initialization failure
     */
    public static void init(@NonNull Context ctxt, boolean debug) {
        init(ctxt, debug, ctxt.getFilesDir(), new File(ctxt.getFilesDir(), CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library.
     * Debugging mode is not supported for client code.  Please use it only when advised to do
     * so by Couchbase Support Engineering
     * This method allows specifying a default root directory for database files,
     * and the scratch directory used for SQLite temporary files. Use it with great caution.
     *
     * @param ctxt       Application context
     * @param debug      to enable debugging
     * @param rootDir    default directory for databases
     * @param scratchDir scratch directory for SQLite
     * @throws CouchbaseLiteError on initialization failure
     */
    public static void init(@NonNull Context ctxt, boolean debug, @NonNull File rootDir, @NonNull File scratchDir) {
        CouchbaseLiteInternal.init(ctxt, debug, rootDir, scratchDir);
    }
}
