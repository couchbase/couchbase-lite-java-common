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
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Base;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Among the other things that this class attempts to abstract away, is access to the file system.
 * On both Android, and in a Web Container, file system access is pretty problematic.
 * Among other things, some code make the tacit assumption that there is a single root directory
 * that contains both a scratch (temp) directory and the database directory.  The scratch directory
 * is also used, occasionally, as the home for log files.
 */
public final class CouchbaseLiteInternal {
    // Utility class
    private CouchbaseLiteInternal() {}

    public static final String SCRATCH_DIR_NAME = "CouchbaseLiteTemp";

    private static final String ERRORS_PROPERTIES_PATH = "/errors.properties";

    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final Object LOCK = new Object();

    private static volatile boolean debugging;
    private static volatile File rootDir;
    private static volatile File scratchDir;

    public static void init(
        @NonNull MValue.Delegate mValueDelegate,
        boolean debug,
        @NonNull File rootDir,
        @NonNull File scratchDir) {
        if (INITIALIZED.getAndSet(true)) { return; }

        debugging = debug;

        Preconditions.assertNotNull(mValueDelegate, "mValueDelegate");

        CouchbaseLiteInternal.rootDir = Preconditions.assertNotNull(FileUtils.verifyDir(rootDir), "rootDir");
        CouchbaseLiteInternal.scratchDir = Preconditions.assertNotNull(FileUtils.verifyDir(scratchDir), "scratchDir");

        NativeLibrary.load(scratchDir);

        C4Base.debug(debugging());

        setC4TmpDirPath(scratchDir);

        MValue.registerDelegate(mValueDelegate);

        Log.initLogging(loadErrorMessages());
    }

    public static boolean debugging() { return debugging; }

    /**
     * This method is for internal used only and will be removed in the future release.
     */
    @NonNull
    public static ExecutionService getExecutionService() {
        final ExecutionService executionService = EXECUTION_SERVICE.get();
        if (executionService != null) { return executionService; }
        EXECUTION_SERVICE.compareAndSet(null, new JavaExecutionService());
        return EXECUTION_SERVICE.get();
    }

    public static void requireInit(String message) {
        if (!INITIALIZED.get()) {
            throw new IllegalStateException(message + ".  Did you forget to call CouchbaseLite.init()?");
        }
    }

    @NonNull
    public static File getRootDir() {
        requireInit("Can't create DB path");
        return rootDir;
    }

    @NonNull
    public static String getRootDirPath() { return rootDir.getAbsolutePath(); }

    @NonNull
    public static File getScratchDir() {
        requireInit("Can't create Scratch path");
        return scratchDir;
    }

    @NonNull
    public static String getScratchDirPath() { return scratchDir.getAbsolutePath(); }

    @VisibleForTesting
    public static void reset(boolean state) { INITIALIZED.set(state); }

    @VisibleForTesting
    @SuppressWarnings({"unchecked", "rawtypes"})
    @NonNull
    public static Map<String, String> loadErrorMessages() {
        final Properties errors = new Properties();
        try (InputStream is = CouchbaseLiteInternal.class.getResourceAsStream(ERRORS_PROPERTIES_PATH)) {
            errors.load(is);
        }
        catch (IOException e) { Log.i(LogDomain.DATABASE, "Failed to load error messages!", e); }
        return (Map<String, String>) (Map) errors;
    }

    private static void setC4TmpDirPath(@NonNull File scratchDir) {
        try {
            synchronized (LOCK) { C4Base.setTempDir(scratchDir.getAbsolutePath()); }
        }
        catch (LiteCoreException e) { Log.w(LogDomain.DATABASE, "Failed to set c4TmpDir", e); }
    }
}
