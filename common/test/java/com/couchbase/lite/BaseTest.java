//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StringUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public abstract class BaseTest extends PlatformBaseTest {
    public static final long STD_TIMEOUT_SEC = 10;
    public static final long LONG_TIMEOUT_SEC = 30;

    public static final long STD_TIMEOUT_MS = STD_TIMEOUT_SEC * 1000L;
    public static final long LONG_TIMEOUT_MS = LONG_TIMEOUT_SEC * 1000L;

    public static final String TEST_DATE = "2019-02-21T05:37:22.014Z";
    public static final String BLOB_CONTENT = "Knox on fox in socks in box. Socks on Knox and Knox in box.";

    private final AtomicReference<AssertionError> testFailure = new AtomicReference<>();

    protected ExecutionService.CloseableExecutor testSerialExecutor;

    @BeforeClass
    public static void setUpPlatformSuite() { Report.log(LogLevel.INFO, ">>>>>>>>>>>> Suite started"); }

    @AfterClass
    public static void tearDownBaseTestSuite() {
        for (String path: SCRATCH_DIRS) { FileUtils.eraseFileOrDir(path); }
        SCRATCH_DIRS.clear();

        Report.log(LogLevel.INFO, "<<<<<<<<<<<< Suite completed");
    }

    public static void logTestInitializationComplete(@NonNull String testName) {
        Report.log(LogLevel.INFO, "==== %s test initialized", testName);
    }

    public static void logTestTeardownBegun(@NonNull String testName) {
        Report.log(LogLevel.INFO, "==== %s  test teardown", testName);
    }

    public static String getScratchDirPath(@NonNull String name) {
        try {
            String path = FileUtils.verifyDir(new File(CouchbaseLiteInternal.getScratchDir().getCanonicalFile(), name))
                .getCanonicalPath();
            SCRATCH_DIRS.add(path);
            return path;
        }
        catch (IOException e) { throw new IllegalStateException("Failed creating scratch directory: " + name, e); }
    }

    public static void waitUntil(long maxTime, Fn.Provider<Boolean> test) {
        final long delay = 100;
        if (maxTime <= delay) { assertTrue(test.get()); }

        final long endTimes = System.currentTimeMillis() + maxTime - delay;
        do {
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { break; }
            if (test.get()) { return; }
        }
        while (System.currentTimeMillis() < endTimes);

        assertTrue(false); // more relevant message than using fail...
    }

    private static final List<String> SCRATCH_DIRS = new ArrayList<>();


    private String testName;

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) { testName = description.getMethodName(); }
    };

    @Before
    public final void setUpBaseTest() {
        Report.log(LogLevel.INFO, ">>>>>>>> Test started: " + testName);
        Log.initLogging();

        setupPlatform();

        testFailure.set(null);

        testSerialExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

        logTestInitializationComplete("Base");
    }

    @After
    public final void tearDownBaseTest() {
        logTestTeardownBegun("Base");

        boolean succeeded = false;
        if (testSerialExecutor != null) { succeeded = testSerialExecutor.stop(2, TimeUnit.SECONDS); }
        Report.log(LogLevel.INFO, "Executor stopped: " + succeeded);

        Report.log(LogLevel.INFO, "<<<<<<<< Test completed: " + testName);
    }

    protected final String getUniqueName(@NonNull String prefix) { return StringUtils.getUniqueName(prefix, 12); }

    public static String getScratchDirectoryPath(@NonNull String name) { return getScratchDirPath(name); }

    // Prefer this method to any other way of creating a new database
    protected final Database createDb(@NonNull String name) throws CouchbaseLiteException {
        return createDb(name, null);
    }

    // Prefer this method to any other way of creating a new database
    protected final Database createDb(@NonNull String name, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        if (config == null) { config = new DatabaseConfiguration(); }
        final String dbName = getUniqueName(name);
        final File dbDir = new File(config.getDirectory(), dbName + C4Database.DB_EXTENSION);
        assertFalse(dbDir.exists());
        final Database db = new Database(dbName, config);
        assertTrue(dbDir.exists());
        return db;
    }

    protected final Database duplicateDb(@NonNull Database db) throws CouchbaseLiteException {
        return duplicateDb(db, new DatabaseConfiguration());
    }

    protected final Database duplicateDb(@NonNull Database db, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        return new Database(db.getName(), (config != null) ? config : new DatabaseConfiguration());
    }

    protected final Database reopenDb(@NonNull Database db) throws CouchbaseLiteException {
        return reopenDb(db, null);
    }

    protected final Database reopenDb(@NonNull Database db, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        final String dbName = db.getName();
        assertTrue(closeDb(db));
        return new Database(dbName, (config != null) ? config : new DatabaseConfiguration());
    }

    protected final Database recreateDb(@NonNull Database db) throws CouchbaseLiteException {
        return recreateDb(db, null);
    }

    protected final Database recreateDb(@NonNull Database db, @Nullable DatabaseConfiguration config)
        throws CouchbaseLiteException {
        final String dbName = db.getName();
        assertTrue(deleteDb(db));
        return new Database(dbName, (config != null) ? config : new DatabaseConfiguration());
    }

    protected final boolean closeDb(@Nullable Database db) {
        synchronized (db.getDbLock()) {
            if ((db == null) || (!db.isOpen())) { return true; }
        }
        return doSafely("Close db " + db.getName(), db::close);
    }

    protected final boolean deleteDb(@Nullable Database db) {
        if (db == null) { return true; }
        final boolean isOpen;
        synchronized (db.getDbLock()) { isOpen = db.isOpen(); }
        // there is a race here... probably small.
        return (isOpen)
            ? doSafely("Delete db " + db.getName(), db::delete)
            : FileUtils.eraseFileOrDir(db.getDbFile());
    }


    protected final void runSafely(Runnable test) {
        try { test.run(); }
        catch (AssertionError failure) {
            Report.log(LogLevel.DEBUG, "Test failed", failure);
            testFailure.compareAndSet(null, failure);
        }
    }

    protected final void runSafelyInThread(CountDownLatch latch, Runnable test) {
        new Thread(() -> {
            try { test.run(); }
            catch (AssertionError failure) {
                Report.log(LogLevel.DEBUG, "Test failed", failure);
                testFailure.compareAndSet(null, failure);
            }
            finally { latch.countDown(); }
        }).start();
    }

    protected final void checkForFailure() {
        AssertionError failure = testFailure.get();
        if (failure != null) { throw new AssertionError(failure); }
    }

    private boolean doSafely(@NonNull String taskDesc, @NonNull Fn.TaskThrows<CouchbaseLiteException> task) {
        try {
            task.run();
            Report.log(LogLevel.DEBUG, taskDesc + " succeeded");
            return true;
        }
        catch (CouchbaseLiteException ex) {
            Report.log(LogLevel.WARNING, taskDesc + " failed", ex);
        }
        return false;
    }
}

