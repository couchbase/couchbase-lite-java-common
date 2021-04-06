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

import android.content.Context;
import android.support.annotation.NonNull;

import java.io.File;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


public final class CouchbaseLite {
    // Singleton
    private CouchbaseLite() {}

    private static final Loggers LOGGERS = new Loggers();


    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(@NonNull Context ctxt) { init(ctxt, BuildConfig.CBL_DEBUG); }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(@NonNull Context ctxt, boolean debug) {
        init(ctxt, debug, ctxt.getFilesDir(), ctxt.getExternalFilesDir(CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library.
     * This method allows specifying a root directory for CBL files.
     * Use this version with great caution.
     *
     * @param ctxt       Application context
     * @param debug      true if debugging
     * @param rootDbDir  default directory for databases
     * @param scratchDir scratch directory for SQLite
     */
    public static void init(@NonNull Context ctxt, boolean debug, @NonNull File rootDbDir, @NonNull File scratchDir) {
        CouchbaseLiteInternal.init(new MValueDelegate(), debug, rootDbDir, scratchDir, ctxt);
    }

    /**
     * Get the system loggers.
     *
     * @return the system loggers.
     */
    public static Loggers getLoggers() { return LOGGERS; }
}
