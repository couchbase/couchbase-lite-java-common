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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StopWatch;
import com.couchbase.lite.internal.utils.StringUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class C4BaseTest extends BaseTest {
    public static final long MOCK_PEER = 500005L;
    public static final long MOCK_TOKEN = 0xba5eba11;

    @NonNull
    public static C4Document getOrCreateDocument(@Nullable C4Collection collection, @NonNull String docId)
        throws LiteCoreException {
        assertNotNull(collection);
        return C4Document.getOrCreateDocument(collection, docId);
    }

    public static void assertIsLiteCoreException(@Nullable Exception e, int domain, int code) {
        assertNotNull(e);
        if (!(e instanceof LiteCoreException)) {
            throw new AssertionError("Expected CBL exception (" + domain + ", " + code + ") but got:", e);
        }
        final LiteCoreException err = (LiteCoreException) e;
        if (domain > 0) { assertEquals(domain, err.domain); }
        if (code > 0) { assertEquals(code, err.code); }
    }

    public static void assertThrowsLiteCoreException(int domain, int code, @NonNull Fn.TaskThrows<Exception> block) {
        try {
            block.run();
            fail("Expected LiteCore exception (" + domain + ", " + code + ")");
        }
        catch (Exception e) {
            assertIsLiteCoreException(e, domain, code);
        }
    }


    protected final String REV_ID_1 = getTestRevId("abcd", 1);
    protected final String REV_ID_2 = getTestRevId("c001d00d", 2);

    protected C4Database c4Database;
    protected C4Collection c4Collection;

    protected String dbParentDirPath;
    protected String dbName;

    protected byte[] fleeceBody;

    @Before
    public final void setUpC4BaseTest() throws CouchbaseLiteException {
        final String testDirName = getUniqueName("c4_test");
        try {
            C4.setEnv("TMPDIR", getScratchDirectoryPath(testDirName), 1);

            final File parentDir = new File(CouchbaseLiteInternal.getDefaultDbDir(), testDirName);
            if (!parentDir.mkdirs()) { throw new IOException("Can't create test db directory: " + parentDir); }
            dbParentDirPath = parentDir.getCanonicalPath();

            dbName = getUniqueName("c4_test_db");

            c4Database = C4Database.getDatabase(dbParentDirPath, dbName, getTestDbFlags());

            c4Collection = c4Database.addCollection(
                getUniqueName("c4_test_collection"),
                getUniqueName("c4_test_scope"));

            Map<String, Object> body = new HashMap<>();
            body.put("ans*wer", 42);

            try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
                enc.beginDict(body.size());
                for (String key: body.keySet()) {
                    enc.writeKey(key);
                    enc.writeValue(body.get(key));
                }

                enc.endDict();
                fleeceBody = enc.finish();
            }
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        catch (IOException e) { throw new CouchbaseLiteError("IO error setting up directories", e); }
    }

    @After
    public final void tearDownC4BaseTest() throws LiteCoreException {
        final C4Collection coll = c4Collection;
        c4Collection = null;
        if (coll != null) { coll.close(); }

        final C4Database db = c4Database;
        c4Database = null;
        if (db != null) { db.closeDb(); }

        FileUtils.eraseFileOrDir(dbParentDirPath);
    }

    protected void checkChanges(
        C4CollectionObserver observer,
        List<String> expectedDocIds,
        List<String> expectedRevIds,
        boolean external) {
        final List<C4DocumentChange> changes = observer.getChanges(100);
        assertNotNull(changes);
        final int n = expectedDocIds.size();
        assertEquals(n, changes.size());
        for (int i = 0; i < n; i++) {
            final C4DocumentChange ch = changes.get(i);
            assertEquals(expectedDocIds.get(i), ch.getDocID());
            assertEquals(trimRevId(expectedRevIds.get(i)), trimRevId(ch.getRevID()));
            assertEquals(external, ch.isExternal());
        }
    }

    protected int getTestDbFlags() {
        int flags = C4Database.DB_FLAGS;
        if (C4Database.VERSION_VECTORS_ENABLED) { flags |= C4Constants.DatabaseFlags.FAKE_CLOCK; }
        return flags;
    }

    protected String getTestRevId(String node, int gen) {
        return (!C4Database.VERSION_VECTORS_ENABLED)
            ? gen + "-" + node
            : gen + "@" + PlatformUtils.getEncoder().encodeToString(
                    StringUtils.getUniqueName(node, 16).substring(0, 16).getBytes(StandardCharsets.US_ASCII))
                .substring(0, 22);
    }

    protected String trimRevId(String revId) {
        if (revId == null) { return null; }
        return (!revId.matches(".*[@;].*")) ? revId : revId.split("[@;]", 2)[0];
    }

    protected long loadJsonAsset(String name) throws LiteCoreException, IOException { return loadJsonAsset(name, ""); }

    protected long loadJsonAsset(String name, String idPrefix) throws LiteCoreException, IOException {
        try (InputStream is = PlatformUtils.getAsset(name)) {
            return loadJsonAsset(is, idPrefix, 120, true);
        }
    }

    protected void reopenDB() throws LiteCoreException {
        closeC4Database();
        c4Database = C4Database.getDatabase(dbParentDirPath, dbName, getTestDbFlags());
        assertNotNull(c4Database);
    }

    protected byte[] json2fleece(String json) throws LiteCoreException {
        boolean commit = false;
        c4Database.beginTransaction();
        try (FLSliceResult body = C4TestUtils.encodeJSONInDb(c4Database, json5(json))) {
            byte[] bytes = body.getContent();
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

    protected void createRev(String docID, String revID, byte[] body) throws LiteCoreException {
        createRev(docID, revID, body, 0);
    }

    protected void createRev(C4Collection coll, String docID, String revID, byte[] body)
        throws LiteCoreException {
        createRev(coll, docID, revID, body, 0);
    }

    protected void createRev(String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        createRev(c4Collection, docID, revID, body, flags);
    }

    protected void closeC4Database() throws LiteCoreException {
        if (c4Database != null) {
            c4Database.closeDb();
            c4Database = null;
        }
    }

    private void createRev(C4Collection coll, String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        boolean commit = false;

        C4Database db = coll.getDb();
        db.beginTransaction();
        try {
            C4Document curDoc = getOrCreateDocument(coll, docID);
            assertNotNull(curDoc);

            List<String> revIDs = new ArrayList<>();
            revIDs.add(revID);
            if (curDoc.getRevID() != null) { revIDs.add(curDoc.getRevID()); }
            String[] history = revIDs.toArray(new String[0]);
            C4Document doc = C4TestUtils.create(coll, body, docID, flags, true, false, history, true, 0, 0);
            assertNotNull(doc);

            doc.close();
            // don't try to close the C4Document

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
                String docID = String.format(Locale.ENGLISH, "%s%07d", idPrefix, numDocs + 1);

                try (FLSliceResult body = C4TestUtils.encodeJSONInDb(c4Database, l)) {
                    // Don't try to autoclose this: See C4Document.close()
                    assertNotNull(C4TestUtils.create(
                        c4Collection,
                        body,
                        docID,
                        0,
                        false,
                        false,
                        new String[0],
                        true,
                        0,
                        0));
                }

                numDocs++;

                if (verbose && numDocs % 100000 == 0) { Report.log("...docs loaded: %d", numDocs); }

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
}
