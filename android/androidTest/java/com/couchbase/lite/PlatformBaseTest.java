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

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.internal.AndroidExecutionService;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.support.Log;


/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Android";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%^&()_+{}][=-.,;'12345ABCDEabcde";

    private static final Map<String, Exclusion> PLATFORM_DEPENDENT_TESTS;
    static {
        final Map<String, Exclusion> m = new HashMap<>();
        m.put("android<21", new Exclusion("Not supported on Android API < 21", () -> Build.VERSION.SDK_INT < 21));
        PLATFORM_DEPENDENT_TESTS = Collections.unmodifiableMap(m);
    }

    static { CouchbaseLite.init(InstrumentationRegistry.getTargetContext(), true); }
    @Override
    public void setupPlatform() {
        final ConsoleLogger console = Database.log.getConsole();
        console.setLevel(LogLevel.DEBUG);
        console.setDomains(LogDomain.ALL_DOMAINS);
    }

    @Override
    public void reloadStandardErrorMessages() {
        Log.initLogging(CouchbaseLiteInternal.loadErrorMessages(InstrumentationRegistry.getTargetContext()));
    }

    @Override
    public AbstractExecutionService getExecutionService(ThreadPoolExecutor executor) {
        return new AndroidExecutionService(executor);
    }

    @Override
    public final void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getDefaultExecutor(), task);
    }

    @Override
    public final Exclusion getExclusions(@NonNull String tag) { return PLATFORM_DEPENDENT_TESTS.get(tag); }
}
