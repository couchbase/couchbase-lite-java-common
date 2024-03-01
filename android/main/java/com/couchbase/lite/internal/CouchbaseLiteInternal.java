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
package com.couchbase.lite.internal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.R;
import com.couchbase.lite.internal.connectivity.AndroidConnectivityManager;
import com.couchbase.lite.internal.core.C4;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.replicator.NetworkConnectivityManager;
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
    private CouchbaseLiteInternal() { }

    public static final String PLATFORM = "CBL-ANDROID";

    public static final String SCRATCH_DIR_NAME = "CouchbaseLiteTemp";

    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";

    private static final AtomicReference<SoftReference<Context>> CONTEXT = new AtomicReference<>();
    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();
    private static final AtomicReference<NetworkConnectivityManager> CONNECTIVITY_MANAGER = new AtomicReference<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final Object LOCK = new Object();

    private static volatile boolean debugging;

    private static volatile File defaultDbDir;

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(
        @NonNull Context ctxt,
        boolean debug,
        @NonNull File defaultDbDir,
        @NonNull File scratchDir) {
        if (INITIALIZED.getAndSet(true)) { return; }

        // set early to catch initialization errors
        debugging = debug;

        CONTEXT.set(new SoftReference<>(Preconditions.assertNotNull(ctxt.getApplicationContext(), "context")));

        CouchbaseLiteInternal.defaultDbDir = FileUtils.verifyDir(defaultDbDir);

        System.loadLibrary(LITECORE_JNI_LIBRARY);

        C4.debug(debugging);

        Log.initLogging(loadErrorMessages(ctxt));

        setC4TmpDirPath(FileUtils.verifyDir(scratchDir));

        CBLVariantExtensions.initVariant(LOCK, ctxt);
    }

    @NonNull
    public static Context getContext() {
        requireInit("Application context not initialized");
        final SoftReference<Context> contextRef = CONTEXT.get();

        final Context ctxt = contextRef.get();
        if (ctxt == null) { throw new CouchbaseLiteError("Context is null"); }

        return ctxt;
    }

    public static boolean debugging() { return debugging; }

    @NonNull
    public static NetworkConnectivityManager getNetworkConnectivityManager() {
        final NetworkConnectivityManager connectivityMgr = CONNECTIVITY_MANAGER.get();
        if (connectivityMgr != null) { return connectivityMgr; }
        CONNECTIVITY_MANAGER.compareAndSet(null, AndroidConnectivityManager.newInstance());
        return CONNECTIVITY_MANAGER.get();
    }

    @NonNull
    public static ExecutionService getExecutionService() {
        final ExecutionService executionService = EXECUTION_SERVICE.get();
        if (executionService != null) { return executionService; }
        EXECUTION_SERVICE.compareAndSet(null, new AndroidExecutionService());
        return EXECUTION_SERVICE.get();
    }

    public static void requireInit(String message) {
        if (!INITIALIZED.get()) {
            throw new CouchbaseLiteError(message + ".  Did you forget to call CouchbaseLite.init()?");
        }
    }

    @NonNull
    public static File getDefaultDbDir() {
        requireInit("Can't create DB path");
        return defaultDbDir;
    }

    @NonNull
    public static String getDefaultDbDirPath() { return defaultDbDir.getAbsolutePath(); }

    @VisibleForTesting
    public static void reset(boolean state) { INITIALIZED.set(state); }

    @VisibleForTesting
    @NonNull
    public static Map<String, String> loadErrorMessages(@NonNull Context ctxt) {
        final Map<String, String> errorMessages = new HashMap<>();

        try (InputStream is = ctxt.getResources().openRawResource(R.raw.errors)) {
            final JSONObject root = new JSONObject(new Scanner(is, "UTF-8").useDelimiter("\\A").next());
            final Iterable<String> errors = root::keys;
            for (String error: errors) { errorMessages.put(error, root.getString(error)); }
        }
        catch (IOException | JSONException e) {
            Log.w(LogDomain.DATABASE, "Failed to load error messages", e);
        }

        return errorMessages;
    }

    private static void setC4TmpDirPath(@NonNull File scratchDir) {
        try {
            synchronized (LOCK) { C4.setTempDir(scratchDir.getAbsolutePath()); }
        }
        catch (LiteCoreException e) { Log.w(LogDomain.DATABASE, "Failed to set c4TmpDir", e); }
    }
}
