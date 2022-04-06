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
    private CouchbaseLite() {}

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     * <p>
     * This method expects <code>Context.getExternalFilesDir(...)</code> to return a non-null value
     * and will throw an <code>IllegalStateException</code> if it does not.
     * On the rare device on which that occurs, you may want to do something like this:
     * <code>
     * try { init(); }
     * catch (IllegalStateException e) {
     *     final File rootDir = ctxt.getFilesDir();
     *     init(false, rootDIr, new File(rootDir, "cbl_scratch"));
     * }
     * </code>
     *
     * @param ctxt the ApplicationContext.
     * @throws IllegalStateException on initialization failure
     */
    public static void init(@NonNull Context ctxt) { init(ctxt, BuildConfig.CBL_DEBUG); }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     * <p>
     * This method expects <code>Context.getExternalFilesDir(...)</code> to return a non-null value
     * and will throw an <code>IllegalStateException</code> if it does not.
     * On the rare device on which that occurs, you may want to do something like this:
     * <code>
     * try { init(); }
     * catch (IllegalStateException e) {
     *     final File rootDir = ctxt.getFilesDir();
     *     init(false, rootDIr, new File(rootDir, "cbl_scratch"));
     * }
     * </code>
     *
     * @param ctxt the ApplicationContext.
     * @param debug true to enable debugging
     * @throws IllegalStateException on initialization failure
     */
    public static void init(@NonNull Context ctxt, boolean debug) {
        init(ctxt, debug, ctxt.getFilesDir(), ctxt.getExternalFilesDir(CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library.
     * This method allows specifying a default root directory for database files,
     * and the scratch directory used for SQLite temporary files.
     * Use it with great caution.
     *
     * @param ctxt       Application context
     * @param debug      to enable debugging
     * @param rootDir    default directory for databases
     * @param scratchDir scratch directory for SQLite
     * @throws IllegalStateException on initialization failure
     */
    public static void init(@NonNull Context ctxt, boolean debug, @NonNull File rootDir, @NonNull File scratchDir) {
        CouchbaseLiteInternal.init(new MValueDelegate(), debug, rootDir, scratchDir, ctxt);
    }
}
