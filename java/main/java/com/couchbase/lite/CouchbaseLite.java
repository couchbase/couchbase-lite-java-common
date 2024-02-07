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

import java.io.File;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.FileUtils;


/**
 * One of the methods from this utility class MUST be called prior to any use of CouchbaseLite.
 * If you intend to use Vector Search, you must use one of the two methods that specify a
 * extDir for the vector search library.  To use the default search path for the vector
 * search library specify VS_USE_DEFAULT as the extDir.
 */
public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() { }

    /**
     * Use VS_USE_DEFAULT to specify that Couchbase Lite should look for extension libraries in the default location
     */
    public static final File DEFAULT_EXT_DIR = new File("*");

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     * <p>
     * This method expects the current directory to be writeable
     * and will throw an <code>IllegalStateException</code> if it is not.
     * Use <code>init(boolean, File, File)</code> to specify alternative root and scratch directories.
     *
     * @throws IllegalStateException on initialization failure
     */
    public static void init() { init(false); }

    /**
     * Initialize CouchbaseLite library. Call this method before using CouchbaseLite.
     * <p>
     * This method expects the current directory to be writeable
     * and will throw an <code>IllegalStateException</code> if it is not.
     * Use <code>init(boolean, File, File)</code> to specify alternative root and scratch directories.
     *
     * @param debug true if debugging
     * @throws IllegalStateException on initialization failure
     */
    public static void init(boolean debug) {
        final File curDir = FileUtils.getCurrentDirectory();
        init(DEFAULT_EXT_DIR, debug, curDir, new File(curDir, CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library. Call this method before using CouchbaseLite.
     * <p>
     * This method expects the current directory to be writeable
     * and will throw an <code>IllegalStateException</code> if it is not.
     * Use <code>init(boolean, File, File)</code> to specify alternative root and scratch directories.
     *
     * @param extDir directory containing the vector library
     * @throws IllegalStateException on initialization failure
     */
    public static void init(@NonNull File extDir) {
        final File curDir = FileUtils.getCurrentDirectory();
        init(extDir, false, curDir, new File(curDir, CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library. Call this method before using CouchbaseLite.
     * <p>
     * This method expects the current directory to be writeable
     * and will throw an <code>IllegalStateException</code> if it is not.
     * Use <code>init(boolean, File, File)</code> to specify alternative root and scratch directories.
     *
     * @param extDir directory containing the vector library
     * @param debug  true if debugging
     * @throws IllegalStateException on initialization failure
     */
    public static void init(@NonNull File extDir, boolean debug) {
        final File curDir = FileUtils.getCurrentDirectory();
        init(extDir, debug, curDir, new File(curDir, CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library. Call this method before using CouchbaseLite.
     * <p>
     * This method allows specifying a default root directory for database files,
     * and the scratch directory used for temporary files (the native libraries, etc).
     * Both directories must be writable by this process.  The method will throw an
     * <code>IllegalStateException</code> if they are not.
     *
     * @param debug      true if debugging
     * @param rootDir    default directory for databases
     * @param scratchDir scratch directory for SQLite
     * @throws IllegalStateException on initialization failure
     */
    public static void init(boolean debug, @NonNull File rootDir, @NonNull File scratchDir) {
        init(DEFAULT_EXT_DIR, debug, rootDir, scratchDir);
    }

    /**
     * Initialize CouchbaseLite library. Call this method before using CouchbaseLite.
     * <p>
     * This method allows specifying a default root directory for database files,
     * and the scratch directory used for temporary files (the native library, etc).
     * Both directories must be writable by this process.  The method will throw an
     * <code>IllegalStateException</code> if they are not.
     *
     * @param extDir     directory containing the vector library
     * @param debug      true if debugging
     * @param rootDir    default directory for databases
     * @param scratchDir scratch directory for SQLite
     * @throws IllegalStateException on initialization failure
     */
    public static void init(@NonNull File extDir, boolean debug, @NonNull File rootDir, @NonNull File scratchDir) {
        CouchbaseLiteInternal.init(extDir, debug, rootDir, scratchDir);
    }
}
