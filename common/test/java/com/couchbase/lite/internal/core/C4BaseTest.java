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
import org.junit.Before;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.ConsoleLogger;
import com.couchbase.lite.CouchbaseLite;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StopWatch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class C4BaseTest extends BaseTest {
    public static final String DOC_ID = "mydoc";
    public static final String REV_ID_1 = "1-abcd";
    public static final String REV_ID_2 = "2-c001d00d";
    public static final String REV_ID_3 = "3-deadbeef";


    protected C4Database c4Database;

    protected String dbParentDirPath;
    protected String dbName;

    protected byte[] fleeceBody;

    private String testName;

    @Before
    public final void setUpC4BaseTest() throws CouchbaseLiteException {
        final String testDirName = getUniqueName("c4_test");
        try {
            C4.setenv("TMPDIR", getScratchDirectoryPath(testDirName), 1);

            final File parentDir = new File(CouchbaseLiteInternal.getRootDir(), testDirName);
            if (!parentDir.mkdirs()) { throw new IOException("Can't create test db directory: " + parentDir); }
            dbParentDirPath = parentDir.getCanonicalPath();

            dbName = getUniqueName("c4-test-db");

            c4Database = C4Database.getDatabase(
                dbParentDirPath,
                dbName,
                getFlags(),
                C4Constants.EncryptionAlgorithm.NONE,
                null);

            Map<String, Object> body = new HashMap<>();
            body.put("ans*wer", 42);
            fleeceBody = createFleeceBody(body);
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        catch (IOException e) { throw new IllegalStateException("IO error setting up directories", e); }
    }

    @After
    public final void tearDownC4BaseTest() throws LiteCoreException {
        final C4Database db = c4Database;
        c4Database = null;
        if (db != null) { db.closeDb(); }

        FileUtils.eraseFileOrDir(dbParentDirPath);
    }

    protected int getFlags() { return C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.SHARED_KEYS; }

    protected C4DocEnumerator enumerateChanges(C4Database db, long since, int flags) throws LiteCoreException {
        return new C4DocEnumerator(db.getPeer(), since, flags);
    }

    protected C4DocEnumerator enumerateAllDocs(C4Database db, int flags) throws LiteCoreException {
        return new C4DocEnumerator(db.getPeer(), flags);
    }

    protected void createRev(String docID, String revID, byte[] body) throws LiteCoreException {
        createRev(docID, revID, body, 0);
    }

    protected void createRev(C4Database db, String docID, String revID, byte[] body)
        throws LiteCoreException {
        createRev(db, docID, revID, body, 0);
    }

    protected long loadJsonAsset(String name) throws LiteCoreException, IOException {
        return loadJsonAsset(name, "");
    }

    protected long loadJsonAsset(String name, String idPrefix) throws LiteCoreException, IOException {
        try (InputStream is = PlatformUtils.getAsset(name)) {
            return loadJsonAsset(is, idPrefix, 120, true);
        }
    }

    protected void createRev(String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        createRev(this.c4Database, docID, revID, body, flags);
    }

    protected void reopenDB() throws LiteCoreException {
        closeC4Database();
        c4Database = C4Database.getDatabase(
            dbParentDirPath,
            dbName,
            getFlags(),
            C4Constants.EncryptionAlgorithm.NONE,
            null);
        assertNotNull(c4Database);
    }

    protected void reopenDBReadOnly() throws LiteCoreException {
        closeC4Database();
        int flags = getFlags() & ~C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.READ_ONLY;
        c4Database = C4Database.getDatabase(
            dbParentDirPath,
            dbName,
            flags,
            C4Constants.EncryptionAlgorithm.NONE,
            null);
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
            json = FLValue.getJSONForJSON5(input);
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

    // Read a file that contains a JSON document per line. Each line becomes a document.
    private long loadJsonAsset(InputStream is, String idPrefix, double timeout, boolean verbose)
        throws LiteCoreException, IOException {
        long numDocs = 0;
        boolean commit = false;

        StopWatch timer = new StopWatch();

        c4Database.beginTransaction();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                try (FLSliceResult body = c4Database.encodeJSON(l)) {
                    String docID = String.format(Locale.ENGLISH, "%s%07d", idPrefix, numDocs + 1);
                    try (C4Document doc = c4Database.put(body, docID, 0, false, false, new String[0], true, 0, 0)) {
                        assertNotNull(doc);
                    }
                }

                numDocs++;

                if (verbose && numDocs % 100000 == 0) { Report.log(LogLevel.VERBOSE, "...docs loaded: " + numDocs); }

                if (((numDocs % 1000) == 0) && (timer.getElapsedTimeSecs() >= timeout)) {
                    throw new IOException(String.format(
                        Locale.ENGLISH, "Stopping JSON import after %.3f sec",
                        timer.getElapsedTimeSecs()));
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
