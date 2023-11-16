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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.JSONUtils;
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

    public static final String TEST_DATE = "2019-02-21T05:37:22.014Z";
    public static final String BLOB_CONTENT = "Knox on fox in socks in box. Socks on Knox and Knox in box.";

    public static final String TEST_DOC_SORT_KEY = "TEST_SORT_ASC";
    public static final String TEST_DOC_REV_SORT_KEY = "TEST_SORT_DESC";
    public static final String TEST_DOC_TAG_KEY = "TEST_TAG";

    private static final List<String> SCRATCH_DIRS = new ArrayList<>();

    @BeforeClass
    public static void setUpPlatformSuite() { Report.log(">>>>>>>>>>>> Suite started"); }

    @AfterClass
    public static void tearDownBaseTestSuite() {
        for (String path: SCRATCH_DIRS) { FileUtils.eraseFileOrDir(path); }
        SCRATCH_DIRS.clear();

        Report.log("<<<<<<<<<<<< Suite completed");
    }

    // Make this package protected method visible
    public static boolean mDictHasChanged(MutableDictionary dict) { return dict.isChanged(); }

    @NonNull
    public static String getUniqueName(@NonNull String prefix) { return StringUtils.getUniqueName(prefix, 8); }

    // Run a boolean function every `waitMs` until it is true
    // If it is not true within `maxWaitMs` fail.
    @SuppressWarnings({"BusyWait", "ConditionalBreakInInfiniteLoop"})
    public static void waitUntil(long maxWaitMs, Fn.Provider<Boolean> test) {
        final long waitMs = 100L;
        final long endTime = System.currentTimeMillis() + maxWaitMs - waitMs;
        while (true) {
            if (test.get()) { break; }
            if (System.currentTimeMillis() > endTime) { throw new AssertionError("Operation timed out"); }
            try { Thread.sleep(waitMs); }
            catch (InterruptedException e) { throw new AssertionError("Operation interrupted", e); }
        }
    }


    ///////////////////////////////   E X C E P T I O N   A S S E R T I O N S   ///////////////////////////////

    // Please do *NOT* use the @Test(expected=...) annotation.  It is entirely too prone to error.
    // Even though it can work pretty will in a very limited number of cases, please, always prefer
    // one of these methods (or their equivalents in C4BaseTest and OKHttpSocketTest

    public static <T extends Exception> void assertThrows(Class<T> ex, @NonNull Fn.TaskThrows<Exception> test) {
        try { test.run(); }
        catch (Throwable e) {
            if (!ex.equals(e.getClass())) { fail("Expecting exception: " + ex + " but got " + e); }
            return;
        }
        fail("Expecting exception: " + ex);
    }

    public static void assertIsCBLException(@Nullable Exception e, @Nullable String domain, int code) {
        assertNotNull(e);
        if (!(e instanceof CouchbaseLiteException)) {
            throw new AssertionError("Expected CBL exception (" + domain + ", " + code + ") but got:", e);
        }
        final CouchbaseLiteException err = (CouchbaseLiteException) e;
        if (domain != null) { assertEquals(domain, err.getDomain()); }
        if (code > 0) { assertEquals(code, err.getCode()); }
    }

    public static void assertThrowsCBLException(
        @Nullable String domain,
        int code,
        @NonNull Fn.TaskThrows<Exception> block) {
        try {
            block.run();
            fail("Expected CBL exception (" + domain + ", " + code + ")");
        }
        catch (Exception e) {
            assertIsCBLException(e, domain, code);
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
        Report.log(">>>>>>>> Test started: %s", testName);
        Log.initLogging();

        setupPlatform();

        testSerialExecutor = new ExecutionService.CloseableExecutor() {
            final ExecutorService executor = Executors.newSingleThreadExecutor(
                new ThreadFactory() {        // thread factory that gives our threads nice recognizable names
                    private final AtomicInteger threadId = new AtomicInteger(0);

                    @NonNull
                    public Thread newThread(@NonNull Runnable r) {
                        final Thread thread = new Thread(r, "TEST-THREAD #" + threadId.incrementAndGet());
                        thread.setUncaughtExceptionHandler((t, e) ->
                            Report.log(e, "Uncaught exception on test thread %s", thread.getName()));
                        Report.log("New test thread: %s", thread.getName());
                        return thread;
                    }
                });

            @Override
            public void execute(@NonNull Runnable task) {
                Report.log("task enqueued: %s", task);
                executor.execute(() -> {
                    Report.log("task started: %s", task);
                    task.run();
                    Report.log("task finished: %s", task);
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
        Report.log("Executor stopped: %s", succeeded);

        Report.log(
            "<<<<<<<< Test completed(%s): %s",
            formatInterval(System.currentTimeMillis() - startTime),
            testName);
    }

    protected final void skipTestWhen(@NonNull String tag) {
        final Exclusion exclusion = getExclusions(tag);
        if (exclusion != null) { Assume.assumeFalse(exclusion.msg, exclusion.test.get()); }
    }

    protected final String getScratchDirectoryPath(@NonNull String name) {
        try {
            String path = FileUtils.verifyDir(new File(getTmpDir(), name)).getCanonicalPath();
            SCRATCH_DIRS.add(path);
            return path;
        }
        catch (IOException e) {
            throw new AssertionError("Failed creating scratch directory: " + name, e);
        }
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
        catch (Exception e) { Report.log(e, "Failed to delete database %s", db); }
    }

    protected final MutableDocument createTestDoc() { return createTestDoc(1, 1, getUniqueName("no-tag")); }

    protected final MutableDocument createTestDoc(String tag) { return createTestDoc(1, 1, tag); }

    protected final List<MutableDocument> createTestDocs(int first, int n) {
        return createTestDocs(first, n, getUniqueName("no-tag"));
    }

    protected final List<MutableDocument> createTestDocs(int first, int n, String tag) {
        final List<MutableDocument> docs = new ArrayList<>();
        final int last = first + n - 1;
        for (int i = first; i <= last; i++) { docs.add(createTestDoc(i, last, tag)); }
        return docs;
    }

    protected final MutableDocument createComplexTestDoc() {
        return createComplexTestDoc(getUniqueName("tag"));
    }

    protected final MutableDocument createComplexTestDoc(String tag) {
        return addComplexData(createTestDoc(1, 1, tag));
    }

    protected final List<MutableDocument> createComplexTestDocs(int n, String tag) {
        return createComplexTestDocs(1000, n, tag);
    }

    protected final List<MutableDocument> createComplexTestDocs(int first, int n, String tag) {
        final List<MutableDocument> docs = new ArrayList<>();
        final int last = first + n - 1;
        for (int i = first; i <= last; i++) { docs.add(addComplexData(createTestDoc(i, last, tag))); }
        return docs;
    }

    // Comparing documents isn't trivial: Fleece
    // will compress numeric values into the smallest
    // type that can be used to represent them.
    // This doc is sufficiently complex to make simple
    // comparison interesting but uses only values/types
    // that are seem to survive the Fleece round-trip, unchanged
    private MutableDocument createTestDoc(int id, int top, String tag) {
        MutableDocument mDoc = new MutableDocument();
        mDoc.setValue("nullValue", null);
        mDoc.setBoolean("booleanTrue", true);
        mDoc.setBoolean("booleanFalse", false);
        mDoc.setLong("longZero", 0);
        mDoc.setLong("longBig", 4000000000L);
        mDoc.setLong("longSmall", -4000000000L);
        mDoc.setDouble("doubleBig", 1.0E200);
        mDoc.setDouble("doubleSmall", -1.0E200);
        mDoc.setString("stringNull", null);
        mDoc.setString("stringPunk", "Jett");
        mDoc.setDate("dateNull", null);
        mDoc.setDate("dateCB", JSONUtils.toDate(TEST_DATE));
        mDoc.setBlob("blobNull", null);
        mDoc.setString(TEST_DOC_TAG_KEY, tag);
        mDoc.setLong(TEST_DOC_SORT_KEY, id);
        mDoc.setLong(TEST_DOC_REV_SORT_KEY, top - id);
        return mDoc;
    }

    private MutableDocument addComplexData(MutableDocument mDoc) {
        // Dictionary:
        MutableDictionary address = new MutableDictionary();
        address.setValue("street", "1 Main street");
        address.setValue("city", "Mountain View");
        address.setValue("state", "CA");
        mDoc.setValue("address", address);

        // Array:
        MutableArray phones = new MutableArray();
        phones.addValue("650-123-0001");
        phones.addValue("650-123-0002");
        mDoc.setValue("phones", phones);

        return mDoc;
    }

    private String formatInterval(long ms) {
        final long min = TimeUnit.MILLISECONDS.toMinutes(ms);
        ms -= TimeUnit.MINUTES.toMillis(min);

        final long sec = TimeUnit.MILLISECONDS.toSeconds(ms);
        ms -= TimeUnit.SECONDS.toMillis(sec);

        return String.format("%02d:%02d.%03d", min, sec, ms);
    }
}

