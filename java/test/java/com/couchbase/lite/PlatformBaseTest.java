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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.JavaExecutionService;
import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Report;

import com.couchbase.lite.internal.core.C4Database;

/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Java";
    public static final String SCRATCH_DIR_NAME = "cbl_test_scratch";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%&'()_+{}][=-.,;'ABCDEabcde";

    public static final String LOG_DIR = "logs";

    private static final long MAX_LOG_FILE_BYTES = Long.MAX_VALUE; // lots
    private static final int MAX_LOG_FILES = Integer.MAX_VALUE; // lots

    private static final Map<String, Exclusion> PLATFORM_DEPENDENT_TESTS;
    static {
        final Map<String, Exclusion> m = new HashMap<>();
        m.put(
            "NOT WINDOWS",
            new Exclusion(
                "Supported only on Windows",
                () -> !System.getProperty("os.name").toLowerCase().contains("windows")));
        m.put(
            "WINDOWS",
            new Exclusion(
                "Not supported on Windows",
                () -> System.getProperty("os.name").toLowerCase().contains("windows")));
        m.put(
            "SWEDISH UNSUPPORTED",
            new Exclusion(
                "Swedish locale not supported",
                () -> !Arrays.asList(Locale.getAvailableLocales()).contains(new Locale("sv"))));
        PLATFORM_DEPENDENT_TESTS = Collections.unmodifiableMap(m);
    }

    private static LogFileConfiguration logConfig;
    static { CouchbaseLite.init(true); }

    @Override
    public final void setupPlatform() { }

    @Override
    public final File getTmpDir() {
        return FileUtils.verifyDir(new File(FileUtils.getCurrentDirectory(), SCRATCH_DIR_NAME));
    }

    @Override
    public final AbstractExecutionService getExecutionService(ThreadPoolExecutor executor) {
        return new JavaExecutionService(executor);
    }

    @Override
    public final void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getDefaultExecutor(), task);
    }

    @Override
    public final Exclusion getExclusions(@NonNull String tag) { return PLATFORM_DEPENDENT_TESTS.get(tag); }

    @Override
    public final String getDevice() {
        final String device = System.getProperty("os.name");
        Report.log("Test device: %s", device);
        return device.toLowerCase(Locale.getDefault()).substring(0, 3);
    }
}
