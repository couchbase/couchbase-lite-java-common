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
package com.couchbase.lite.internal.core;

import android.support.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.PlatformBaseTest;
import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StopWatch;
import com.couchbase.lite.internal.utils.StringUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class C4BaseTest extends PlatformBaseTest {
    public static final String DOC_ID = "mydoc";
    public static final String REV_ID_1 = "1-abcd";
    public static final String REV_ID_2 = "2-c001d00d";
    public static final String REV_ID_3 = "3-deadbeef";

    @BeforeClass
    public static void setUpC4BaseTestSuite() { BaseTest.setUpPlatformSuite(); }

    @AfterClass
    public static void tearDownC4BaseTestSuite() { BaseTest.tearDownBaseTestSuite(); }


    protected C4Database c4Database;

    protected File rootDir;
    protected String dbName;
    protected String dbDirPath;

    protected byte[] fleeceBody;

    private String testName;

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) { testName = description.getMethodName(); }
    };

    @Before
    public final void setUpC4BaseTest() throws CouchbaseLiteException {
        Report.log(LogLevel.INFO, ">>>>>>>> C4 Test started: " + testName);
        Log.initLogging();

        setupPlatform();

        final String tmpDirName = getUniqueName("c4_test");
        try {
            String tmpDir = getScratchDirectoryPath(tmpDirName);
            C4.setenv("TMPDIR", tmpDir, 1);

            rootDir = new File(CouchbaseLiteInternal.getRootDir(), tmpDirName);

            dbName = getUniqueName("c4-test-db");
            dbDirPath = new File(rootDir, dbName).getCanonicalPath();
            if (!rootDir.mkdirs()) { throw new IOException("Can't create directory: " + rootDir); }

            c4Database = C4Database.getDatabase(
                dbDirPath,
                getFlags(),
                null,
                getVersioning(),
                encryptionAlgorithm(),
                encryptionKey());

            Map<String, Object> body = new HashMap<>();
            body.put("ans*wer", 42);
            fleeceBody = createFleeceBody(body);
        }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        catch (IOException e) { throw new IllegalStateException("IO error setting up directories", e); }

        BaseTest.logTestInitializationComplete("C4");
    }

    @After
    public final void tearDownC4BaseTest() throws LiteCoreException {
        BaseTest.logTestTeardownBegun("C4");
        final C4Database db = c4Database;
        c4Database = null;
        if (db != null) { db.closeDb(); }

        FileUtils.eraseFileOrDir(rootDir);

        Report.log(LogLevel.INFO, "<<<<<<<< C4 Test completed: " + testName);
    }

    protected final String getUniqueName(@NonNull String prefix) { return StringUtils.getUniqueName(prefix, 12); }

    protected String getScratchDirectoryPath(@NonNull String name) { return BaseTest.getScratchDirPath(name); }

    protected C4DocEnumerator enumerateChanges(C4Database db, long since, int flags) throws LiteCoreException {
        return new C4DocEnumerator(db.getPeer(), since, flags);
    }

    protected C4DocEnumerator enumerateAllDocs(C4Database db, int flags) throws LiteCoreException {
        return new C4DocEnumerator(db.getPeer(), flags);
    }

    protected void createRev(String docID, String revID, byte[] body) throws LiteCoreException {
        createRev(docID, revID, body, 0);
    }

    protected int getFlags() { return C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.SHARED_KEYS; }

    protected int getVersioning() { return C4Constants.DocumentVersioning.REVISION_TREES; }

    protected int encryptionAlgorithm() { return C4Constants.EncryptionAlgorithm.NONE; }

    protected byte[] encryptionKey() { return null; }

    protected long importJSONLines(String name) throws LiteCoreException, IOException {
        return importJSONLines(PlatformUtils.getAsset(name));
    }

    protected long importJSONLines(String name, String idPrefix) throws LiteCoreException, IOException {
        return importJSONLines(PlatformUtils.getAsset(name), idPrefix);
    }

    protected long importJSONLinesSafely(String name) throws CouchbaseLiteException {
        try { return importJSONLines(name); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e); }
        catch (IOException e) { throw new IllegalStateException("IO error importing JSON", e); }
    }

    protected void createRev(C4Database db, String docID, String revID, byte[] body)
        throws LiteCoreException {
        createRev(db, docID, revID, body, 0);
    }

    protected void createRev(String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        createRev(this.c4Database, docID, revID, body, flags);
    }

    protected void reopenDB() throws LiteCoreException {
        closeC4Database();
        c4Database = C4Database.getDatabase(
            dbDirPath,
            getFlags(),
            null,
            getVersioning(),
            encryptionAlgorithm(),
            encryptionKey());
        assertNotNull(c4Database);
    }

    protected void reopenDBReadOnly() throws LiteCoreException {
        closeC4Database();
        int flag = getFlags() & ~C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.READ_ONLY;
        c4Database = C4Database.getDatabase(
            dbDirPath,
            flag,
            null,
            getVersioning(),
            encryptionAlgorithm(),
            encryptionKey());
        assertNotNull(c4Database);
    }

    protected byte[] json2fleece(String json) throws LiteCoreException {
        boolean commit = false;
        c4Database.beginTransaction();
        try (FLSliceResult body = c4Database.encodeJSON(json5(json))) {
            byte[] bytes = body.getBuf();
            commit = true;
            return bytes;
        }
        finally {
            c4Database.endTransaction(commit);
        }
    }

    protected String json5(String input) {
        String json = null;
        try {
            json = FLValue.json5ToJson(input);
        }
        catch (LiteCoreException e) {
            fail(e.getMessage());
        }
        assertNotNull(json);
        return json;
    }

    /**
     * @param flags C4RevisionFlags
     */
    private void createRev(C4Database db, String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            C4Document curDoc = db.get(docID, false);
            assertNotNull(curDoc);
            List<String> revIDs = new ArrayList<>();
            revIDs.add(revID);
            if (curDoc.getRevID() != null) { revIDs.add(curDoc.getRevID()); }
            String[] history = revIDs.toArray(new String[0]);
            C4Document doc = db.put(body, docID, flags, true, false, history, true, 0, 0);
            assertNotNull(doc);
            doc.close();
            curDoc.close();
            commit = true;
        }
        finally {
            db.endTransaction(commit);
        }
    }

    private long importJSONLines(InputStream is) throws LiteCoreException, IOException {
        return importJSONLines(is, 120, true);
    }

    private long importJSONLines(InputStream is, String idPrefix) throws LiteCoreException, IOException {
        return importJSONLines(is, idPrefix, 120, true);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    private long importJSONLines(InputStream is, double timeout, boolean verbose)
        throws IOException, LiteCoreException {
        return importJSONLines(is, "", timeout, verbose);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    private long importJSONLines(InputStream is, String idPrefix, double timeout, boolean verbose)
        throws LiteCoreException, IOException {
        StopWatch timer = new StopWatch();

        long numDocs = 0;
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    try (FLSliceResult body = c4Database.encodeJSON(line)) {
                        String docID = String.format(Locale.ENGLISH, "%s%07d", idPrefix, numDocs + 1);

                        try (C4Document doc = c4Database.put(body, docID, 0, false, false, new String[0], true, 0, 0)) {
                            assertNotNull(doc);
                        }
                    }

                    numDocs++;
                    if ((numDocs % 1000) == 0 && (timer.getElapsedTimeSecs() >= timeout)) {
                        String msg = String.format(
                            Locale.ENGLISH, "Stopping JSON import after %.3f sec",
                            timer.getElapsedTimeSecs());
                        throw new IOException(msg);
                    }
                    if (verbose && numDocs % 100000 == 0) {
                        Report.log(LogLevel.VERBOSE, String.valueOf(numDocs));
                    }
                }
            }
            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }

        return numDocs;
    }

    private byte[] createFleeceBody(Map<String, Object> body) throws LiteCoreException {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            if (body == null) { enc.beginDict(0); }
            else {
                enc.beginDict(body.size());
                for (String key: body.keySet()) {
                    enc.writeKey(key);
                    enc.writeValue(body.get(key));
                }
            }
            enc.endDict();
            return enc.finish();
        }
    }

    private void closeC4Database() throws LiteCoreException {
        if (c4Database != null) {
            c4Database.closeDb();
            c4Database = null;
        }
    }
}
