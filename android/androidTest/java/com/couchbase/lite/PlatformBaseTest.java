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

import android.os.Build;
import android.support.test.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.support.Log;

import static org.junit.Assert.fail;


/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Android";
    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%^&*()_+{}|\\][=-/.,<>?\":;'ABCDEabcde";
    public static final String DB_EXTENSION = AbstractDatabase.DB_EXTENSION;

    private static final Map<String, Runnable> PLATFORM_DEPENDENT_TESTS;

    static {
        final Map<String, Runnable> m = new HashMap<>();
        m.put(
            "testStopReplicatorAfterOffline",
            () -> { if (Build.VERSION.SDK_INT < 21) { fail("Websockets not supported on Android v < 21"); }});
        m.put(
            "testStartSingleShotReplicatorInOffline",
            () -> { if (Build.VERSION.SDK_INT < 21) { fail("Websockets not supported on Android v < 21"); }});
        PLATFORM_DEPENDENT_TESTS = Collections.unmodifiableMap(m);
    }

    // this should probably go in the BaseTest but
    // there are several tests (C4 tests) that are not subclasses
    static { initCouchbase(); }

    public static void initCouchbase() { CouchbaseLite.init(InstrumentationRegistry.getTargetContext()); }

    public static void deinitCouchbase() { CouchbaseLiteInternal.reset(); }

    @BeforeClass
    public static void setUpPlatformSuite() { android.util.Log.d(">>>>>>>>>>>>>>>>>>>>>>>>>", " Suite started"); }

    @AfterClass
    public static void tearDownBaseTestClass() { android.util.Log.d("<<<<<<<<<<<<<<<<<<<<<<<<<", " Suite completed"); }


    private String tmpDirPath;

    @Before
    public void setUpPlatformTest() { android.util.Log.d(">>>>>>>>>", " Test started"); }

    @After
    public void tearDownPlatformTest() { android.util.Log.d("<<<<<<<<<", " Test completed"); }

    @Override
    public void setupPlatform() { Database.log.getConsole().setLevel(LogLevel.DEBUG); }

    @Override
    public void reloadStandardErrorMessages() {
        Log.initLogging(CouchbaseLiteInternal.loadErrorMessages(InstrumentationRegistry.getTargetContext()));

    }

    @Override
    public InputStream getAsset(String asset) {
        try { return CouchbaseLiteInternal.getContext().getAssets().open(asset); }
        catch (IOException ignore) { }
        return null;
    }

    @Override
    public String getDatabaseDirectoryPath() { return CouchbaseLiteInternal.getDbDirectoryPath(); }

    @Override
    public String getScratchDirectoryPath(String name) {
        if (tmpDirPath == null) { tmpDirPath = CouchbaseLiteInternal.getTmpDirectoryPath(); }

        try { return new File(tmpDirPath, name).getCanonicalPath(); }
        catch (IOException e) { throw new RuntimeException("Could not open tmp directory: " + name, e); }
    }

    @Override
    public final void failImmediatelyForPlatform(String testName) {
        Runnable test = PLATFORM_DEPENDENT_TESTS.get(testName);
        if (test != null) { test.run(); }
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

    private static String getSystemProperty(String name) throws Exception {
        Class<?> systemPropertyClazz = Class.forName("android.os.SystemProperties");
        return (String) systemPropertyClazz
            .getMethod("get", String.class)
            .invoke(systemPropertyClazz, new Object[] {name});
    }
}
