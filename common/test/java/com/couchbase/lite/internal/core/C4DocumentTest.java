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

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


// !!! SEVERAL OF THESE TESTS DEPEND ON USE OF THE DEFAULT COLLECTION

public class C4DocumentTest extends C4BaseTest {
    interface Verification {
        void verify(C4Document doc) throws LiteCoreException;
    }

    // - "Invalid docID"

    @Test
    public void testInvalidDocIDEmpty() throws LiteCoreException { testInvalidDocID(""); }

    @Test
    public void testInvalidDocIDControlCharacter() throws LiteCoreException { testInvalidDocID("oops\noops"); }

    @Test
    public void testInvalidDocIDTooLong() throws LiteCoreException {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < 241; i++) { str.append('x'); }
        testInvalidDocID(str.toString());
    }

    // - "FleeceDocs"
    @Test
    public void testFleeceDocs() throws LiteCoreException, IOException { loadJsonAsset("names_100.json"); }

    // - "Document Put"
    @Test
    public void testPut() throws LiteCoreException {
        boolean commit = false;
        c4Database.beginTransaction();
        try {
            // Creating doc given ID:
            C4Document doc = C4Document.create(
                c4Database,
                fleeceBody,
                DOC_ID,
                0,
                false,
                false,
                new String[0],
                true,
                0,
                0);
            assertNotNull(doc);
            assertEquals(DOC_ID, doc.getDocID());
            String kExpectedRevID = "1-042ca1d3a1d16fd5ab2f87efc7ebbf50b7498032";
            assertEquals(kExpectedRevID, doc.getRevID());
            assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
            assertEquals(kExpectedRevID, doc.getSelectedRevID());
            doc.close();

            // Update doc:
            String[] history = {kExpectedRevID};

            doc = C4Document.create(
                c4Database,
                json2fleece("{'ok':'go'}"),
                DOC_ID,
                0,
                false,
                false,
                history,
                true,
                0,
                0);
            assertNotNull(doc);
            // NOTE: With current JNI binding, unable to check commonAncestorIndex value
            String kExpectedRevID2 = "2-201796aeeaa6ddbb746d6cab141440f23412ac51";
            assertEquals(kExpectedRevID2, doc.getRevID());
            assertEquals(C4Constants.DocumentFlags.EXISTS, doc.getFlags());
            assertEquals(kExpectedRevID2, doc.getSelectedRevID());
            doc.close();

            // Insert existing rev that conflicts:
            String kConflictRevID = "2-deadbeef";
            String[] history2 = {kConflictRevID, kExpectedRevID};
            doc = C4Document.create(
                c4Database,
                json2fleece("{'from':'elsewhere'}"),
                DOC_ID,
                0,
                true,
                true,
                history2,
                true,
                0,
                1);
            assertNotNull(doc);
            // NOTE: With current JNI binding, unable to check commonAncestorIndex value
            assertEquals(kExpectedRevID2, doc.getRevID());
            assertEquals(C4Constants.DocumentFlags.EXISTS | C4Constants.DocumentFlags.CONFLICTED, doc.getFlags());
            assertEquals(kConflictRevID, doc.getSelectedRevID());
            doc.close();

            commit = true;
        }
        finally {
            c4Database.endTransaction(commit);
        }
    }

    private void testInvalidDocID(String docID) throws LiteCoreException {
        c4Database.beginTransaction();
        try {
            C4Document.create(c4Database, fleeceBody, docID, 0, false, false,
                new String[0], true, 0, 0);
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.BAD_DOC_ID, e.code);
        }
        finally {
            c4Database.endTransaction(false);
        }
    }
}
