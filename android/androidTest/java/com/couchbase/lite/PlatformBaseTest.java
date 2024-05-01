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
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.couchbase.lite.internal.AndroidExecutionService;
import com.couchbase.lite.internal.CBLVariantExtensions;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.utils.FileUtils;


/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Android";
    public static final String SCRATCH_DIR_NAME = "cbl_test_scratch";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%^&()_+{}][=-.,;'12345ABCDEabcde";

    private static final Map<String, Exclusion> PLATFORM_DEPENDENT_TESTS;
    static {
        final Map<String, Exclusion> m = new HashMap<>();
        m.put("NEXUS5", new Exclusion("Fails on Nexus 5", () -> "Nexus 5".equals(android.os.Build.MODEL)));
        m.put("ANDROID<21", new Exclusion("Not supported on Android API < 21", () -> Build.VERSION.SDK_INT < 21));
        m.put("WINDOWS", new Exclusion("Supported only on Windows", () -> false));
        m.put(
            "VECTORSEARCH",
            new Exclusion(
                "Requires the VectorSarch Library",
                () -> CBLVariantExtensions.setExtensionPath(new Object(), getAppContext())));
        m.put(
            "SWEDISH UNSUPPORTED",
            new Exclusion(
                "Swedish locale not supported",
                () -> !Arrays.asList(Locale.getAvailableLocales()).contains(new Locale("sv"))));
        PLATFORM_DEPENDENT_TESTS = Collections.unmodifiableMap(m);
    }

    static {
        try { Runtime.getRuntime().exec("logcat --prune /" + android.os.Process.myPid()).waitFor(); }
        catch (Exception e) { android.util.Log.w("TEST", "Failed adding to chatty whitelist", e); }
    }

    private static Context getAppContext() { return ApplicationProvider.getApplicationContext(); }

    @BeforeClass
    public static void setUpPlatformBaseTestSuite() { CouchbaseLite.init(getAppContext(), true); }

    @AfterClass
    public static void tearDownPlatformBaseTestSuite() { }


    @Override
    public final File getTmpDir() {
        return FileUtils.verifyDir(new File(getAppContext().getFilesDir(), SCRATCH_DIR_NAME));
    }

    @Override
    public final AbstractExecutionService getExecutionService(ThreadPoolExecutor executor) {
        return new AndroidExecutionService(executor);
    }

    @Override
    public final void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getDefaultExecutor(), task);
    }

    @Override
    public final Exclusion getExclusions(@NonNull String tag) { return PLATFORM_DEPENDENT_TESTS.get(tag); }

    @Override
    public final String getDevice() { return android.os.Build.PRODUCT; }
}
