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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.fleece.FLArrayIterator;
import com.couchbase.lite.internal.fleece.FLConstants;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class C4QueryTest extends C4QueryBaseTest {

    @Before
    public final void setUpC4QueryTest() throws LiteCoreException, IOException {
        loadJsonAsset("names_100.json");
    }

    //-------------------------------------------------------------------------
    // tests
    //-------------------------------------------------------------------------

    // -- Query parser error messages
    @Test
    public void testDatabaseErrorMessages() {
        try {
            c4Database.createJsonQuery("[\"=\"]");
            fail();
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.INVALID_QUERY, e.code);
        }
    }

    // - DB Query
    @Test
    public void testDBQuery() throws LiteCoreException {
        compile("['=', ['.', 'contact', 'address', 'state'], 'CA']");
        assertEquals(
            Arrays.asList("0000001", "0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"),
            run());

        compile("['=', ['.', 'contact', 'address', 'state'], 'CA']", "", true);
        Map<String, Object> params = new HashMap<>();
        params.put("offset", 1);
        params.put("limit", 8);
        assertEquals(
            Arrays.asList("0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"),
            run(params));

        params = new HashMap<>();
        params.put("offset", 1);
        params.put("limit", 4);
        assertEquals(Arrays.asList("0000015", "0000036", "0000043", "0000053"), run(params));

        compile(
            "['AND', ['=', ['array_count()', ['.', 'contact', 'phone']], 2],['=', ['.', 'gender'], 'male']]");
        assertEquals(
            Arrays.asList(
                "0000002",
                "0000014",
                "0000017",
                "0000027",
                "0000031",
                "0000033",
                "0000038",
                "0000039",
                "0000045",
                "0000047",
                "0000049",
                "0000056",
                "0000063",
                "0000065",
                "0000075",
                "0000082",
                "0000089",
                "0000094",
                "0000097"),
            run());

        // MISSING means no value is present (at that array index or dict key)
        compile("['IS', ['.', 'contact', 'phone', [0]], ['MISSING']]", "", true);
        params = new HashMap<>();
        params.put("offset", 0);
        params.put("limit", 4);
        assertEquals(Arrays.asList("0000004", "0000006", "0000008", "0000015"), run(params));

        // ...whereas null is a JSON null value
        compile("['IS', ['.', 'contact', 'phone', [0]], null]", "", true);
        params = new HashMap<>();
        params.put("offset", 0);
        params.put("limit", 4);
        assertEquals(Collections.emptyList(), run(params));
    }

    // - DB Query LIKE
    @Test
    public void testDBQueryLIKE() throws LiteCoreException {
        compile("['LIKE', ['.name.first'], '%j%']");
        assertEquals(Collections.singletonList("0000085"), run());

        compile("['LIKE', ['.name.first'], '%J%']");
        assertEquals(
            Arrays.asList(
                "0000002",
                "0000004",
                "0000008",
                "0000017",
                "0000028",
                "0000030",
                "0000045",
                "0000052",
                "0000067",
                "0000071",
                "0000088",
                "0000094"),
            run());

        compile("['LIKE', ['.name.first'], 'Jen%']");
        assertEquals(Arrays.asList("0000008", "0000028"), run());
    }

    // - DB Query IN
    @Test
    public void testDBQueryIN() throws LiteCoreException {
        // Type 1: RHS is an expression; generates a call to array_contains
        compile("['IN', 'reading', ['.', 'likes']]");
        assertEquals(Arrays.asList("0000004", "0000056", "0000064", "0000079", "0000099"), run());

        // Type 2: RHS is an array literal; generates a SQL "IN" expression
        compile("['IN', ['.', 'name', 'first'], ['[]', 'Eddie', 'Verna']]");
        assertEquals(Arrays.asList("0000091", "0000093"), run());
    }

    // - DB Query sorted
    @Test
    public void testDBQuerySorted() throws LiteCoreException {
        compile(
            "['=', ['.', 'contact', 'address', 'state'], 'CA']", "[['.', 'name', 'last']]");
        assertEquals(
            Arrays.asList("0000015", "0000036", "0000072", "0000043", "0000001", "0000064", "0000073", "0000053"),
            run());
    }

    // - DB Query bindings
    @Test
    public void testDBQueryBindings() throws LiteCoreException {
        compile("['=', ['.', 'contact', 'address', 'state'], ['$', 1]]");
        Map<String, Object> params = new HashMap<>();
        params.put("1", "CA");
        assertEquals(
            Arrays.asList("0000001", "0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"),
            run(params));

        compile("['=', ['.', 'contact', 'address', 'state'], ['$', 'state']]");
        params = new HashMap<>();
        params.put("state", "CA");
        assertEquals(
            Arrays.asList("0000001", "0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"),
            run(params));
    }

    // - DB Query ANY
    @Test
    public void testDBQueryANY() throws LiteCoreException {
        compile("['ANY', 'like', ['.', 'likes'], ['=', ['?', 'like'], 'climbing']]");
        assertEquals(Arrays.asList("0000017", "0000021", "0000023", "0000045", "0000060"), run());

        // This EVERY query has lots of results because every empty `likes` array matches it
        compile("['EVERY', 'like', ['.', 'likes'], ['=', ['?', 'like'], 'taxes']]");
        List<String> result = run();
        assertEquals(42, result.size());
        assertEquals("0000007", result.get(0));

        // Changing the op to ANY AND EVERY returns no results
        compile("['ANY AND EVERY', 'like', ['.', 'likes'], ['=', ['?', 'like'], 'taxes']]");
        assertEquals(Collections.emptyList(), run());

        // Look for people where every like contains an L:
        compile("['ANY AND EVERY', 'like', ['.', 'likes'], ['LIKE', ['?', 'like'], '%l%']]");
        assertEquals(Arrays.asList("0000017", "0000027", "0000060", "0000068"), run());
    }

    // - DB Query ANY w/paths
    // NOTE: in C4PathsQueryTest.java

    // - DB Query ANY of dict
    @Test
    public void testDBQueryANYofDict() throws LiteCoreException {
        compile("['ANY', 'n', ['.', 'name'], ['=', ['?', 'n'], 'Arturo']]");
        assertEquals(Collections.singletonList("0000090"), run());

        compile("['ANY', 'n', ['.', 'name'], ['contains()', ['?', 'n'], 'V']]");
        assertEquals(Arrays.asList("0000044", "0000048", "0000053", "0000093"), run());
    }

    // - DB Query expression index
    @Test
    public void testDBQueryExpressionIndex() throws LiteCoreException {
        c4Collection.createValueIndex(
            "length",
            QueryLanguage.JSON.getCode(),
            json5("[['length()', ['.name.first']]]"),
            null);
        compile("['=', ['length()', ['.name.first']], 9]");
        assertEquals(Arrays.asList("0000015", "0000099"), run());
    }

    // - Delete indexed doc
    @Test
    public void testDeleteIndexedDoc() throws LiteCoreException {
        // Create the same index as the above test:
        c4Collection.createValueIndex(
            "length",
            QueryLanguage.JSON.getCode(),
            json5("[['length()', ['.name.first']]]"),
            null);

        // Delete doc "0000015":
        {
            boolean commit = false;
            c4Database.beginTransaction();
            try {
                C4Document doc = c4Collection.getDocument("0000015");
                assertNotNull(doc);
                String[] history = {doc.getRevID()};
                C4Document updatedDoc = C4TestUtils.create(
                    c4Collection,
                    (byte[]) null,
                    C4TestUtils.idForDoc(doc),
                    C4Constants.RevisionFlags.DELETED,
                    false,
                    false,
                    history,
                    true,
                    0,
                    0);
                assertNotNull(updatedDoc);
                commit = true;
            }
            finally {
                c4Database.endTransaction(commit);
            }
        }

        // Now run a query that would have returned the deleted doc, if it weren't deleted:
        compile("['=', ['length()', ['.name.first']], 9]");
        assertEquals(Collections.singletonList("0000099"), run());
    }

    // - Missing columns
    @Test
    public void testMissingColumns() throws LiteCoreException {
        compileSelect("'WHAT': [['.name'], ['.gender']], 'LIMIT': 1");
        C4QueryEnumerator e = runQuery(query);
        while (e.next()) { assertEquals(0x00, e.getMissingColumns()); }
        e.close();

        compileSelect("'WHAT': [['.XX'], ['.name'], ['.YY'], ['.gender'], ['.ZZ']], 'LIMIT': 1");
        e = runQuery(query);
        while (e.next()) { assertEquals(0x15, e.getMissingColumns()); }
        e.close();
    }

    // ----- FTS:

    // - Full-text query
    @Test
    public void testFullTextQuery() throws LiteCoreException {
        c4Collection.createFullTextIndex(
            "byStreet",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.street\"]]",
            null,
            true,
            null);
        compile("['MATCH()', 'byStreet', 'Hwy']");
        assertEquals(
            Arrays.asList(
                Collections.singletonList(new C4FullTextMatch(13L, 0L, 0L, 10L, 3L)),
                Collections.singletonList(new C4FullTextMatch(15L, 0L, 0L, 11L, 3L)),
                Collections.singletonList(new C4FullTextMatch(43L, 0L, 0L, 12L, 3L)),
                Collections.singletonList(new C4FullTextMatch(44L, 0L, 0L, 12L, 3L)),
                Collections.singletonList(new C4FullTextMatch(52L, 0L, 0L, 11L, 3L))
            ),
            runFTS());
    }

    // - Full-text multiple properties
    @Test
    public void testFullTextMultipleProperties() throws LiteCoreException {
        c4Collection.createFullTextIndex(
            "byAddress",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.street\"], [\".contact.address.city\"], [\".contact.address.state\"]]",
            null,
            true,
            null);

        // Some docs match 'Santa' in the street name, some in the city name
        compile("['MATCH()', 'byAddress', 'Santa']");
        assertEquals(
            Arrays.asList(
                Collections.singletonList(new C4FullTextMatch(15L, 1L, 0L, 0L, 5L)),
                Collections.singletonList(new C4FullTextMatch(44L, 0L, 0L, 3L, 5L)),
                Collections.singletonList(new C4FullTextMatch(68L, 0L, 0L, 3L, 5L)),
                Collections.singletonList(new C4FullTextMatch(72L, 1L, 0L, 0L, 5L))
            ),
            runFTS());

        // Search only the street name:
        compile("['MATCH()', 'byAddress', 'contact.address.street:Santa']");
        assertEquals(
            Arrays.asList(
                Collections.singletonList(new C4FullTextMatch(44L, 0L, 0L, 3L, 5L)),
                Collections.singletonList(new C4FullTextMatch(68L, 0L, 0L, 3L, 5L))
            ),
            runFTS());

        // Search for 'Santa' in the street name, and 'Saint' in either:
        compile("['MATCH()', 'byAddress', 'contact.address.street:Santa Saint']");
        assertEquals(
            Collections.singletonList(
                Arrays.asList(
                    new C4FullTextMatch(68L, 0L, 0L, 3L, 5L),
                    new C4FullTextMatch(68L, 1L, 1L, 0L, 5L))
            ),
            runFTS());

        // Search for 'Santa' in the street name, _or_ 'Saint' in either:
        compile("['MATCH()', 'byAddress', 'contact.address.street:Santa OR Saint']");
        assertEquals(
            Arrays.asList(
                Collections.singletonList(new C4FullTextMatch(20L, 1L, 1L, 0L, 5L)),
                Collections.singletonList(new C4FullTextMatch(44L, 0L, 0L, 3L, 5L)),
                Arrays.asList(
                    new C4FullTextMatch(68L, 0L, 0L, 3L, 5L),
                    new C4FullTextMatch(68L, 1L, 1L, 0L, 5L)),
                Collections.singletonList(new C4FullTextMatch(77L, 1L, 1L, 0L, 5L))
            ),
            runFTS());
    }


    // - Multiple Full-text indexes
    @Test
    public void testMultipleFullTextIndexes() throws LiteCoreException {
        c4Collection.createFullTextIndex(
            "byStreet",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.street\"]]",
            null,
            true,
            null);
        c4Collection.createFullTextIndex(
            "byCity",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.city\"]]",
            null,
            true,
            null);
        compile("['AND', ['MATCH()', 'byStreet', 'Hwy'], ['MATCH()', 'byCity',   'Santa']]");
        assertEquals(Collections.singletonList("0000015"), run());
        assertEquals(
            Collections.singletonList(Collections.singletonList(new C4FullTextMatch(15L, 0L, 0L, 11L, 3L))),
            runFTS());
    }

    // - Full-text query in multiple ANDs
    @Test
    public void testFullTextQueryInMultipleANDs() throws LiteCoreException {
        c4Collection.createFullTextIndex(
            "byStreet",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.street\"]]",
            null,
            true,
            null);
        c4Collection.createFullTextIndex(
            "byCity",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.city\"]]",
            null,
            true,
            null);
        compile("['AND',['AND',['=',['.gender'],'male'],"
            + "['MATCH()','byCity','Santa']],['=',['.name.first'],'Cleveland']]");
        assertEquals(Collections.singletonList("0000015"), run());
        assertEquals(
            Collections.singletonList(Collections.singletonList(new C4FullTextMatch(15L, 0L, 0L, 0L, 5L))),
            runFTS());
    }

    // - Multiple Full-text queries
    @Test
    public void testMultipleFullTextQueries() throws LiteCoreException {
        // You can't query the same FTS index multiple times in a query (says SQLite)
        c4Collection.createFullTextIndex(
            "byStreet",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.street\"]]",
            null,
            true,
            null);
        try {
            c4Database.createJsonQuery(
                "['AND', ['MATCH()', 'byStreet', 'Hwy'], ['MATCH()', 'byStreet', 'Blvd']]");
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.INVALID_QUERY, e.code);
        }
    }

    // - Buried Full-text queries
    @Test
    public void testBuriedFullTextQueries() throws LiteCoreException {
        // You can't put an FTS match inside an expression other than a top-level AND (says SQLite)
        c4Collection.createFullTextIndex(
            "byStreet",
            QueryLanguage.JSON.getCode(),
            "[[\".contact.address.street\"]]",
            null,
            true,
            null);
        try {
            c4Database.createJsonQuery(
                "['OR', ['MATCH()', 'byStreet', 'Hwy'], ['=', ['.', 'contact', 'address', 'state'], 'CA']]");
        }
        catch (LiteCoreException e) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, e.domain);
            assertEquals(C4Constants.LiteCoreError.INVALID_QUERY, e.code);
        }
    }

    // - WHAT, JOIN, etc:

    // - DB Query WHAT
    @Test
    public void testDBQueryWHAT() throws LiteCoreException {
        List<String> expectedFirst = Arrays.asList("Cleveland", "Georgetta", "Margaretta");
        List<String> expectedLast = Arrays.asList("Bejcek", "Kolding", "Ogwynn");
        compileSelect(
            "WHAT:['.name.first', '.name.last'],"
                + "WHERE: ['>=', ['length()', ['.name.first']], 9],"
                + "ORDER_BY: [['.name.first']]");

        assertEquals(2, query.getColumnCount());

        C4QueryEnumerator e = runQuery(query);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            assertEquals(itr.getValue().asString(), expectedFirst.get(i));
            itr.next();
            assertEquals(itr.getValue().asString(), expectedLast.get(i));
            i++;
        }
        e.close();
        assertEquals(3, i);
    }

    // - DB Query WHAT returning object
    @Test
    public void testDBQueryWHATReturningObject() throws LiteCoreException {
        List<String> expectedFirst = Arrays.asList("Cleveland", "Georgetta", "Margaretta");
        List<String> expectedLast = Arrays.asList("Bejcek", "Kolding", "Ogwynn");
        compileSelect("WHAT: ['.name'], WHERE: ['>=', ['length()', ['.name.first']], 9], "
            + "ORDER_BY: [['.name.first']]");
        assertEquals(1, query.getColumnCount());

        C4QueryEnumerator e = runQuery(query);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            FLValue col = itr.getValueAt(0);
            assertEquals(col.getType(), FLConstants.ValueType.DICT);
            FLDict name = col.asFLDict();
            assertEquals(expectedFirst.get(i), name.get("first").asString());
            assertEquals(expectedLast.get(i), name.get("last").asString());
            i++;
        }
        e.close();
        assertEquals(3, i);
    }

    // - DB Query Aggregate
    @Test
    public void testDBQueryAggregate() throws LiteCoreException {
        compileSelect("WHAT: [['min()', ['.name.last']], ['max()', ['.name.last']]]");

        C4QueryEnumerator e = runQuery(query);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            assertEquals(itr.getValue().asString(), "Aerni");
            itr.next();
            assertEquals(itr.getValue().asString(), "Zirk");
            i++;
        }
        e.close();
        assertEquals(1, i);
    }

    // - DB Query Grouped
    @Test
    public void testDBQueryGrouped() throws LiteCoreException {

        final List<String> expectedState = Arrays.asList("AL", "AR", "AZ", "CA");
        final List<String> expectedMin = Arrays.asList("Laidlaw", "Okorududu", "Kinatyan", "Bejcek");
        final List<String> expectedMax = Arrays.asList("Mulneix", "Schmith", "Kinatyan", "Visnic");
        final int expectedRowCount = 42;

        compileSelect(
            "WHAT: [['.contact.address.state'], ['min()', ['.name.last']], ['max()', ['.name.last']]],"
                + "GROUP_BY: [['.contact.address.state']]");

        C4QueryEnumerator e = runQuery(query);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            if (i < expectedState.size()) {
                assertEquals(itr.getValue().asString(), expectedState.get(i));
                itr.next();
                assertEquals(itr.getValue().asString(), expectedMin.get(i));
                itr.next();
                assertEquals(itr.getValue().asString(), expectedMax.get(i));
            }
            i++;
        }
        e.close();
        assertEquals(expectedRowCount, i);
    }

    // - DB Query Join
    // @formatter:off
    @Test
    public void testDBQueryJoin() throws IOException, LiteCoreException {
        loadJsonAsset("states_titlecase_line.json", "state-");
        List<String> expectedFirst = Arrays.asList("Cleveland", "Georgetta", "Margaretta");
        List<String> expectedState = Arrays.asList("California", "Ohio", "South Dakota");
        query = c4Database.createJsonQuery(json5(
            "{ 'WHAT': [ '.person.name.first', '.state.name' ],"
                + "  'FROM': ["
                + "    { 'COLLECTION': '" + c4Collection.getScope() + "." + c4Collection.getName() + "', 'AS': 'person' },"
                + "    { 'COLLECTION': '" + c4Collection.getScope() + "." + c4Collection.getName() + "', 'AS': 'state',"
                + "       'ON': [ '=', [ '.state.abbreviation' ], [ '.person.contact.address.state' ] ] }"
                + "  ],"
                + "  'WHERE': [ '>=', [ 'length()', [ '.person.name.first' ] ], 9 ],"
                + "  'ORDER_BY': [ [ '.person.name.first' ] ]"
                + "}"));
        C4QueryEnumerator e = runQuery(query);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            if (i < expectedState.size()) {
                assertEquals(itr.getValue().asString(), expectedFirst.get(i));
                itr.next();
                assertEquals(itr.getValue().asString(), expectedState.get(i));
            }
            i++;
        }
        e.close();
        assertEquals(3, i);
    }
    // @formatter:on

    // - DB Query ANY nested
    // NOTE: in C4NestedQueryTest

    // - Query parser error messages
    @Test
    public void testQueryParserErrorMessages() {
        try {
            query = C4Query.create(c4Database, QueryLanguage.JSON, "[\"=\"]");
            fail();
        }
        catch (LiteCoreException ex) {
            assertEquals(C4Constants.ErrorDomain.LITE_CORE, ex.domain);
            assertEquals(C4Constants.LiteCoreError.INVALID_QUERY, ex.code);
        }
    }
}
