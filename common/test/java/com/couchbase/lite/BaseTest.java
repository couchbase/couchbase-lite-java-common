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

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StringUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public abstract class BaseTest extends PlatformBaseTest {
    public static final long STD_TIMEOUT_SEC = 10;
    public static final long LONG_TIMEOUT_SEC = 30;

    public static final long STD_TIMEOUT_MS = STD_TIMEOUT_SEC * 1000L;
    public static final long LONG_TIMEOUT_MS = LONG_TIMEOUT_SEC * 1000L;

    private static final List<String> SCRATCH_DIRS = new ArrayList<>();

    @BeforeClass
    public static void setUpPlatformSuite() { Report.log(LogLevel.INFO, ">>>>>>>>>>>> Suite started"); }

    @AfterClass
    public static void tearDownBaseTestSuite() {
        for (String path: SCRATCH_DIRS) { FileUtils.eraseFileOrDir(path); }
        SCRATCH_DIRS.clear();

        Report.log(LogLevel.INFO, "<<<<<<<<<<<< Suite completed");
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
        Report.log(LogLevel.INFO, ">>>>>>>> Test started: " + testName);
        Log.initLogging();

        setupPlatform();

        testSerialExecutor = new ExecutionService.CloseableExecutor() {
            ExecutorService executor = Executors.newSingleThreadExecutor();

            @Override
            public void execute(@NotNull Runnable task) {
                Report.log("task enqueued: " + task);
                executor.execute(() -> {
                    Report.log("task started: " + task);
                    task.run();
                    Report.log("task finished: " + task);
                });
            }

            @Override
            public boolean stop(long timeout, @NonNull @NotNull TimeUnit unit) {
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
        Report.log(LogLevel.DEBUG, "Executor stopped: " + succeeded);

        Report.log(
            LogLevel.INFO,
            "<<<<<<<< Test completed(%s): %s",
            formatInterval(System.currentTimeMillis() - startTime),
            testName);
    }

    protected final void skipTestWhen(@NonNull String tag) {
        final Exclusion exclusion = getExclusions(tag);
        if (exclusion != null) { Assume.assumeFalse(exclusion.msg, exclusion.test.get()); }
    }

    protected final String getUniqueName(@NonNull String prefix) { return StringUtils.getUniqueName(prefix, 12); }

    @SuppressWarnings("BusyWait")
    protected final void waitUntil(long maxTime, Fn.Provider<Boolean> test) {
        final long delay = 100;
        if (maxTime <= delay) { assertTrue(test.get()); }

        final long endTimes = System.currentTimeMillis() + maxTime - delay;
        do {
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { break; }
            if (test.get()) { return; }
        }
        while (System.currentTimeMillis() < endTimes);

        // assertTrue() provides a more relevant message than fail()
        //noinspection SimplifiableAssertion
        assertTrue(false);
    }

    protected final String getScratchDirectoryPath(@NonNull String name) {
        try {
            String path = FileUtils.verifyDir(new File(getTmpDir(), name)).getCanonicalPath();
            SCRATCH_DIRS.add(path);
            return path;
        }
        catch (IOException e) { throw new IllegalStateException("Failed creating scratch directory: " + name, e); }
    }

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

    protected final String formatInterval(long ms) {
        final long min = TimeUnit.MILLISECONDS.toMinutes(ms);
        ms -= TimeUnit.MINUTES.toMillis(min);

        final long sec = TimeUnit.MILLISECONDS.toSeconds(ms);
        ms -= TimeUnit.SECONDS.toMillis(sec);

        return String.format("%02d:%02d.%03d", min, sec, ms);
    }

    protected final boolean doSafely(@NonNull String taskDesc, @NonNull Fn.TaskThrows<CouchbaseLiteException> task) {
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

