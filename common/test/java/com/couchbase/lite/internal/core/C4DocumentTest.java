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

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;


public class C4DocumentTest extends C4BaseTest {

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

    @Test
    public void testFleeceDocs() throws LiteCoreException, IOException { loadJsonAsset("names_100.json"); }

    private void testInvalidDocID(String docID) throws LiteCoreException {
        c4Database.beginTransaction();
        try {
            C4TestUtils.create(c4Collection, fleeceBody, docID, 0, false, false, new String[0], true, 0, 0);
            Assert.fail();
        }
        catch (LiteCoreException e) {
            Assert.assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            Assert.assertEquals(C4Constants.LiteCoreError.BAD_DOC_ID, e.code);
        }
        finally {
            c4Database.endTransaction(false);
        }
    }
}
