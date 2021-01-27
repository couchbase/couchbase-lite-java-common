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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Java";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%&'()_+{}][=-.,;'ABCDEabcde";

    public static final String DB_EXTENSION = AbstractDatabase.DB_EXTENSION;

    public static final String SCRATCH_DIR = "cbl-scratch";

    private static final long MAX_LOG_FILE_BYTES = Long.MAX_VALUE; // lots
    private static final int MAX_LOG_FILES = Integer.MAX_VALUE; // lots

    private static final Map<String, Fn.Provider<Boolean>> PLATFORM_DEPENDENT_TESTS;
    static {
        final Map<String, Fn.Provider<Boolean>> m = new HashMap<>();
        m.put("windows", () -> {
            final String os = System.getProperty("os.name");
            return (os != null) && os.toLowerCase().contains("win");
        });
        PLATFORM_DEPENDENT_TESTS = Collections.unmodifiableMap(m);
    }

    private static LogFileConfiguration logConfig;

    static { CouchbaseLite.init(); }

    public static String getScratchDirPath() {
        try {
            return new File(SCRATCH_DIR).getCanonicalPath();
        }
        catch (IOException e) { throw new IllegalStateException("Could not create scratch directory", e); }
    }


    // set up the file logger...
    @Override
    public void setupPlatform() {
        if (logConfig == null) {
            logConfig = new LogFileConfiguration(getScratchDirectoryPath("logs"))
                .setUsePlaintext(true)
                .setMaxSize(MAX_LOG_FILE_BYTES)
                .setMaxRotateCount(MAX_LOG_FILES);
        }

        final FileLogger fileLogger = Database.log.getFile();
        if (!logConfig.equals(fileLogger.getConfig())) { fileLogger.setConfig(logConfig); }
        fileLogger.setLevel(LogLevel.DEBUG);
    }

    @Override
    public final String getScratchDirectoryPath(@NonNull String name) {
        try { return new File(getScratchDirPath(), name).getCanonicalPath(); }
        catch (IOException e) { throw new IllegalStateException("cannot create scratch directory: " + name); }
    }

    @Override
    public void reloadStandardErrorMessages() { Log.initLogging(CouchbaseLiteInternal.loadErrorMessages()); }

    @Override
    public final boolean handlePlatformSpecially(String tag) {
        final Fn.Provider<Boolean> test = PLATFORM_DEPENDENT_TESTS.get(tag);
        return (test != null) && test.get();
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }
}



