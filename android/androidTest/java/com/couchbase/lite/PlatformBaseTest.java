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

import com.couchbase.lite.internal.AndroidExecutionService;
import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.utils.FileUtils;


/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Android";
    public static final String SCRATCH_DIR_NAME = "cbl_test_scratch";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%^&()_+{}][=-.,;'12345ABCDEabcde";

    static {
        try { Runtime.getRuntime().exec("logcat --prune /" + android.os.Process.myPid()).waitFor(); }
        catch (Exception e) { android.util.Log.w("TEST", "Failed adding to chatty whitelist", e); }
    }

    protected static void initCouchbase() { CouchbaseLite.init(getAppContext(), true); }

    private static Context getAppContext() { return ApplicationProvider.getApplicationContext(); }


    @Override
    public final String getDevice() { return android.os.Build.PRODUCT; }

    @Override
    public final int getVMVersion() { return Build.VERSION.SDK_INT; }

    @Override
    public final File getTmpDir() {
        return FileUtils.verifyDir(new File(getAppContext().getFilesDir(), SCRATCH_DIR_NAME));
    }

    @Override
    public final AbstractExecutionService getExecutionService(ThreadPoolExecutor executor) {
        return new AndroidExecutionService(executor);
    }
}
