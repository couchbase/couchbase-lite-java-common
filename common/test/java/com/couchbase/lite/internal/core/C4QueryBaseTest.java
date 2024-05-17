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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;

import static org.junit.Assert.assertNotNull;


public class C4QueryBaseTest extends C4BaseTest {
    protected C4Query query;

    @After
    public final void tearDownC4QueryBaseTest() {
        final C4Query q = query;
        query = null;
        if (q != null) { q.close(); }
    }

    protected final void compileSelect(String json) throws LiteCoreException {
        if (query != null) {
            query.close();
            query = null;
        }

        json = "{'FROM': [{'COLLECTION':'" + c4Collection.getScope() + "." + c4Collection.getName() + "'}],"
            + json + "}";

        query = c4Database.createJsonQuery(json5(json));
        assertNotNull(query);
    }

    protected final void compile(String whereExpr) throws LiteCoreException { compile(whereExpr, null); }

    protected final void compile(String whereExpr, String sortExpr) throws LiteCoreException {
        compile(whereExpr, sortExpr, false);
    }

    protected final void compile(String whereExpr, String sortExpr, boolean addOffsetLimit)
        throws LiteCoreException {
        StringBuilder json = new StringBuilder("{'FROM': [ {'COLLECTION':'");
        json.append(c4Collection.getScope()).append(".");
        json.append(c4Collection.getName()).append("'}],");
        json.append("\"WHERE\": ");
        json.append(whereExpr);
        if ((sortExpr != null) && !sortExpr.isEmpty()) {
            json.append(", \"ORDER_BY\": ");
            json.append(sortExpr);
        }
        if (addOffsetLimit) { json.append(", \"OFFSET\": [\"$offset\"], \"LIMIT\":  [\"$limit\"]"); }
        json.append("}");

        if (query != null) {
            query.close();
            query = null;
        }
        query = c4Database.createJsonQuery(json5(json.toString()));
        assertNotNull(query);
    }

    protected List<String> run() throws LiteCoreException { return run(null); }

    protected final List<String> run(Map<String, Object> params) throws LiteCoreException {
        List<String> docIDs = new ArrayList<>();

        final C4QueryEnumerator e;
        FLSliceResult encodedParams = encodeParameters(params);
        e = query.run(encodedParams);
        assertNotNull(e);

        try {
            while (e.next()) { docIDs.add(e.getColumns().getValueAt(0).asString()); }
            return docIDs;
        }
        finally { e.close(); }
    }

    protected final C4QueryEnumerator runQuery(@NonNull C4Query query)
        throws LiteCoreException {
        return query.run(new FLSliceResult(0, 0));
    }

    protected final List<List<C4FullTextMatch>> runFTS() throws LiteCoreException {
        final List<List<C4FullTextMatch>> matches = new ArrayList<>();

        final C4QueryEnumerator e;
        FLSliceResult encodedParams = encodeParameters(null);
        e = query.run(encodedParams);
        assertNotNull(e);

        try {
            while (e.next()) {
                List<C4FullTextMatch> match = new ArrayList<>();
                for (int i = 0; i < C4FullTextMatch.getFullTextMatchCount(e); i++) {
                    match.add(C4FullTextMatch.getFullTextMatches(e, i).load());
                }
                matches.add(match);
            }
        }
        finally { e.close(); }


        return matches;
    }

    private FLSliceResult encodeParameters(Map<String, Object> params) throws LiteCoreException {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(params);
            return enc.finish2();
        }
    }
}
