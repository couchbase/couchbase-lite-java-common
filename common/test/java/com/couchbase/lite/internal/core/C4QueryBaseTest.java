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
        if (query != null) { query.close(); }
    }

    protected final void compileSelect(String queryStr) throws LiteCoreException {
        if (query != null) {
            query.close();
            query = null;
        }
        query = c4Database.createJsonQuery(queryStr);
        assertNotNull(query);
    }

    protected final void compile(String whereExpr) throws LiteCoreException { compile(whereExpr, null); }

    protected final void compile(String whereExpr, String sortExpr) throws LiteCoreException {
        compile(whereExpr, sortExpr, false);
    }

    protected final void compile(String whereExpr, String sortExpr, boolean addOffsetLimit)
        throws LiteCoreException {
        StringBuilder json = new StringBuilder();
        json.append("[\"SELECT\", {\"WHERE\": ");
        json.append(whereExpr);
        if (sortExpr != null && sortExpr.length() > 0) {
            json.append(", \"ORDER_BY\": ");
            json.append(sortExpr);
        }
        if (addOffsetLimit) { json.append(", \"OFFSET\": [\"$offset\"], \"LIMIT\":  [\"$limit\"]"); }
        json.append("}]");

        if (query != null) {
            query.close();
            query = null;
        }
        query = c4Database.createJsonQuery(json.toString());
        assertNotNull(query);
    }

    protected List<String> run() throws LiteCoreException { return run(null); }

    protected final List<String> run(Map<String, Object> params) throws LiteCoreException {
        List<String> docIDs = new ArrayList<>();
        C4QueryOptions opts = new C4QueryOptions();

        final C4QueryEnumerator e;
        try (FLSliceResult encodedParams = encodeParameters(params)) { e = query.run(opts, encodedParams); }
        assertNotNull(e);

        try {
            while (e.next()) { docIDs.add(e.getColumns().getValueAt(0).asString()); }
            return docIDs;
        }
        finally { e.close(); }
    }


    protected final List<List<List<Long>>> runFTS() throws LiteCoreException {
        final List<List<List<Long>>> matches = new ArrayList<>();
        final C4QueryOptions opts = new C4QueryOptions();

        final C4QueryEnumerator e;
        try (FLSliceResult encodedParams = encodeParameters(null)) { e = query.run(opts, encodedParams); }
        assertNotNull(e);

        try {
            while (e.next()) {
                List<List<Long>> match = new ArrayList<>();
                for (int i = 0; i < e.getFullTextMatchCount(); i++) { match.add(e.getFullTextMatches(i).toList()); }
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
