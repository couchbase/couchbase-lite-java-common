//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.support.Log;


/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Java";
    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%&'()_+{}][=-.,;'ABCDEabcde";
    public static final String DB_EXTENSION = AbstractDatabase.DB_EXTENSION;
    private static final String LOG_DIR = ".cbl-test-logs";
    private static final String SCRATCH_DIR = ".cbl-test-scratch";
    private static final long MAX_LOG_FILE_BYTES = Long.MAX_VALUE; // lots
    private static final int MAX_LOG_FILES = Integer.MAX_VALUE; // lots

    // for testing, use the current directory as the root
    public static void initCouchbase() { CouchbaseLite.init(); }

    public static void deinitCouchbase() { CouchbaseLiteInternal.reset(); }

    // this should probably go in the BaseTest but
    // there are several tests (C4 tests) that are not subclasses
    static { initCouchbase(); }

    private static LogFileConfiguration logConfig;


    // set up the file logger...
    @Override
    public void setupPlatform() {
        if (logConfig == null) {
            logConfig = new LogFileConfiguration(getDirPath(new File(LOG_DIR)))
                .setUsePlaintext(true)
                .setMaxSize(MAX_LOG_FILE_BYTES)
                .setMaxRotateCount(MAX_LOG_FILES);
        }

        final FileLogger fileLogger = Database.log.getFile();
        if (!logConfig.equals(fileLogger.getConfig())) { fileLogger.setConfig(logConfig); }
        fileLogger.setLevel(LogLevel.DEBUG);
    }

    @Override
    public void reloadStandardErrorMessages() { Log.initLogging(CouchbaseLiteInternal.loadErrorMessages()); }

    @Nullable
    @Override
    public InputStream getAsset(String assetFile) { return getClass().getClassLoader().getResourceAsStream(assetFile); }

    @NotNull
    @Override
    public String getDatabaseDirectoryPath() { return CouchbaseLiteInternal.getDbDirectoryPath(); }

    @Override
    public String getScratchDirectoryPath(String name) { return getDirPath(new File(SCRATCH_DIR, name)); }

    @Override
    public final void failImmediatelyForPlatform(String testName) { }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

    @NotNull
    private String getDirPath(File dir) {
        try {
            if (dir.exists() || dir.mkdirs()) { return dir.getCanonicalPath(); }
            throw new IOException("Cannot create directory: " + dir);
        }
        catch (IOException e) { throw new IllegalStateException("Cannot create log directory", e); }
    }
}



