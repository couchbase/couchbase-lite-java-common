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
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StringUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@SuppressWarnings("ConstantConditions")
public abstract class BaseTest extends PlatformBaseTest {
    public static final long STD_TIMEOUT_SEC = 10;
    public static final long LONG_TIMEOUT_SEC = 60;

    public static final long STD_TIMEOUT_MS = STD_TIMEOUT_SEC * 1000L;
    public static final long LONG_TIMEOUT_MS = LONG_TIMEOUT_SEC * 1000L;

    private static final List<String> SCRATCH_DIRS = new ArrayList<>();

    @NonNull
    public static String getUniqueName(@NonNull String prefix) { return StringUtils.getUniqueName(prefix, 12); }

    @BeforeClass
    public static void setUpPlatformSuite() { Report.log(">>>>>>>>>>>> Suite started"); }

    @AfterClass
    public static void tearDownBaseTestSuite() {
        for (String path: SCRATCH_DIRS) { FileUtils.eraseFileOrDir(path); }
        SCRATCH_DIRS.clear();

        Report.log("<<<<<<<<<<<< Suite completed");
    }

    // Used to protect calls that should not fail,
    // in tests that expect an exception
    public static void failOnError(String msg, Fn.TaskThrows<Exception> task) {
        try { task.run(); }
        catch (Exception e) { throw new AssertionError(msg, e); }
    }

    public static <T extends Exception> void assertThrows(Class<T> ex, Fn.TaskThrows<Exception> test) {
        try {
            test.run();
            fail("Expecting exception: " + ex);
        }
        catch (Throwable e) {
            try { ex.cast(e); }
            catch (ClassCastException e1) { fail("Expecting exception: " + ex + " but got " + e); }
        }
    }

    public static void assertThrowsCBL(String domain, int code, Fn.TaskThrows<CouchbaseLiteException> task) {
        try {
            task.run();
            fail("Expected CouchbaseLiteException{" + domain + ", " + code + "}");
        }
        catch (CouchbaseLiteException e) {
            assertEquals(code, e.getCode());
            assertEquals(domain, e.getDomain());
        }
    }


    protected ExecutionService.CloseableExecutor testSerialExecutor;
    private String testName;
    private long startTime;

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) { testName = description.getMethodName(); }
    };

    @Before
    public final void setUpBaseTest() {
        Report.log(">>>>>>>> Test started: " + testName);
        Log.initLogging();

        setupPlatform();

        testSerialExecutor = new ExecutionService.CloseableExecutor() {
            final ExecutorService executor = Executors.newSingleThreadExecutor();

            @Override
            public void execute(@NonNull Runnable task) {
                Report.log("task enqueued: " + task);
                executor.execute(() -> {
                    Report.log("task started: " + task);
                    task.run();
                    Report.log("task finished: " + task);
                });
            }

            @Override
            public boolean stop(long timeout, @NonNull TimeUnit unit) {
                executor.shutdownNow();
                return true;
            }
        };

        startTime = System.currentTimeMillis();
    }

    @After
    public final void tearDownBaseTest() {
        boolean succeeded = false;
        if (testSerialExecutor != null) { succeeded = testSerialExecutor.stop(2, TimeUnit.SECONDS); }
        Report.log("Executor stopped: " + succeeded);

        Report.log(
            "<<<<<<<< Test completed(%s): %s",
            formatInterval(System.currentTimeMillis() - startTime),
            testName);
    }

    protected final void skipTestWhen(@NonNull String tag) {
        final Exclusion exclusion = getExclusions(tag);
        if (exclusion != null) { Assume.assumeFalse(exclusion.msg, exclusion.test.get()); }
    }

    protected final String formatInterval(long ms) {
        final long min = TimeUnit.MILLISECONDS.toMinutes(ms);
        ms -= TimeUnit.MINUTES.toMillis(min);

        final long sec = TimeUnit.MILLISECONDS.toSeconds(ms);
        ms -= TimeUnit.SECONDS.toMillis(sec);

        return String.format("%02d:%02d.%03d", min, sec, ms);
    }

    // Run a boolean function every `waitMs` until it it true
    // If it is not true within `maxWaitMs` fail.
    @SuppressWarnings("BusyWait")
    protected final void waitUntil(long maxWaitMs, Fn.Provider<Boolean> test) {
        final long waitMs = 100L;
        final long endTime = System.currentTimeMillis() + maxWaitMs - waitMs;
        while (true) {
            if (test.get()) { break; }
            if (System.currentTimeMillis() > endTime) { throw new AssertionError("Operation timed out"); }
            try { Thread.sleep(waitMs); }
            catch (InterruptedException e) { throw new AssertionError("Operation interrupted", e); }
        }
    }

    protected final String getScratchDirectoryPath(@NonNull String name) {
        try {
            String path = FileUtils.verifyDir(new File(getTmpDir(), name)).getCanonicalPath();
            SCRATCH_DIRS.add(path);
            return path;
        }
        catch (IOException e) { throw new AssertionError("Failed creating scratch directory: " + name, e); }
    }

    // Prefer this method to any other way of creating a new database
    @NonNull
    protected final Database createDb(@NonNull String name) { return createDb(name, null); }

    // Prefer this method to any other way of creating a new database
    @NonNull
    protected final Database createDb(@NonNull String name, @Nullable DatabaseConfiguration config) {
        final String dbName = getUniqueName(name);
        final File dbDir = new File(
            (config != null) ? config.getDirectory() : CouchbaseLiteInternal.getDefaultDbDirPath(),
            dbName + C4Database.DB_EXTENSION);
        assertFalse(dbDir.exists());
        Database db;
        try { db = (config == null) ? new Database(dbName) : new Database(dbName, config); }
        catch (Exception e) { throw new AssertionError("Failed creating database " + name, e); }
        assertTrue(dbDir.exists());
        return db;
    }

    @NonNull
    protected final Database duplicateDb(@NonNull Database db) { return duplicateDb(db, null); }

    // Get a new instance of the db or fail.
    @NonNull
    protected final Database duplicateDb(@NonNull Database db, @Nullable DatabaseConfiguration config) {
        final String dbName = db.getName();
        try { return (config == null) ? new Database(dbName) : new Database(dbName, config); }
        catch (Exception e) { throw new AssertionError("Failed duplicating database " + db, e); }
    }

    @NonNull
    protected final Database reopenDb(@NonNull Database db) { return reopenDb(db, null); }

    // Close and reopen the db or fail.
    @NonNull
    protected final Database reopenDb(@NonNull Database db, @Nullable DatabaseConfiguration config) {
        final String dbName = db.getName();
        closeDb(db);
        try { return (config == null) ? new Database(dbName) : new Database(dbName, config); }
        catch (Exception e) { throw new AssertionError("Failed reopening database " + db, e); }
    }

    @NonNull
    protected final Database recreateDb(@NonNull Database db) { return recreateDb(db, null); }

    // Delete and recreate the db or fail.
    @NonNull
    protected final Database recreateDb(@NonNull Database db, @Nullable DatabaseConfiguration config) {
        final String dbName = db.getName();
        deleteDb(db);
        try { return (config == null) ? new Database(dbName) : new Database(dbName, config); }
        catch (Exception e) { throw new AssertionError("Failed recreating database " + db, e); }
    }

    // Close the db or fail.
    protected final void closeDb(@NonNull Database db) {
        assertNotNull(db);
        try { db.close(); }
        catch (Exception e) { throw new AssertionError("Failed closing database " + db, e); }
    }

    // Delete the db or fail.
    protected final void deleteDb(@NonNull Database db) {
        try {
            // there is a race here but probably small.
            if (db.isOpen()) {
                db.delete();
                return;
            }

            final File dbFile = db.getDbFile();
            if ((dbFile != null) && (dbFile.exists())) { FileUtils.eraseFileOrDir(dbFile); }
        }
        catch (Exception e) { throw new AssertionError("Failed deleting database " + db, e); }
    }

    // Test cleanup: Best effort to close the db.
    protected final void discardDb(@Nullable Database db) {
        if ((db == null) || (!db.isOpen())) { return; }
        try { db.close(); }
        catch (Exception e) { Report.log("Failed to close database %s", e, db); }
    }

    // Test cleanup: Best effort to delete the db.
    protected final void eraseDb(@Nullable Database db) {
        if (db == null) { return; }

        try {
            // there is a race here but probably small.
            if (db.isOpen()) {
                db.delete();
                return;
            }

            final File dbFile = db.getDbFile();
            if ((dbFile != null) && (dbFile.exists())) { FileUtils.eraseFileOrDir(dbFile); }
        }
        catch (Exception e) { Report.log("Failed to delete database %s", e, db); }
    }

    // Backing method is package protected
    protected final boolean mDictHasChanged(MutableDictionary dict) { return dict.isChanged(); }
}

