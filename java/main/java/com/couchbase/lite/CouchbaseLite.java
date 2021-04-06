//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import java.io.File;
import java.io.IOException;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


public final class CouchbaseLite {
    // Singleton
    private CouchbaseLite() {}

    private static final Loggers LOGGERS = new Loggers();

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init() { init(false); }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(boolean debug) {
        final File curDir;
        try { curDir = new File("").getCanonicalFile(); }
        catch (IOException e) { throw new IllegalStateException("cannot find current directory", e); }
        init(debug, curDir, new File(curDir, CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library.
     * This method allows specifying a root directory for CBL files.
     *
     * @param debug      true if debugging
     * @param rootDbDir  default directory for databases
     * @param scratchDir scratch directory for SQLite
     */
    public static void init(boolean debug, @NonNull File rootDbDir, @NonNull File scratchDir) {
        CouchbaseLiteInternal.init(new MValueDelegate(), debug, rootDbDir, scratchDir);
    }

    /**
     * Get the system loggers.
     *
     * @return the system loggers.
     */
    public static Loggers getLoggers() { return LOGGERS; }
}
