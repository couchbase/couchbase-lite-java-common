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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;

import static com.couchbase.lite.internal.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class QueryTest extends BaseQueryTest {
    private static final Expression EXPR_NUMBER1 = Expression.property("number1");
    private static final Expression EXPR_NUMBER2 = Expression.property("number2");

    private static final SelectResult SR_DOCID = SelectResult.expression(Meta.id);
    private static final SelectResult SR_REVID = SelectResult.expression(Meta.revisionID);
    private static final SelectResult SR_SEQUENCE = SelectResult.expression(Meta.sequence);
    private static final SelectResult SR_DELETED = SelectResult.expression(Meta.deleted);
    private static final SelectResult SR_EXPIRATION = SelectResult.expression(Meta.expiration);
    private static final SelectResult SR_ALL = SelectResult.all();
    private static final SelectResult SR_NUMBER1 = SelectResult.property("number1");

    private static class MathFn {
        final String name;
        final Expression expr;
        final double expected;

        public MathFn(String name, Expression expr, double expected) {
            this.name = name;
            this.expr = expr;
            this.expected = expected;
        }
    }

    private static class TestCase {
        final Expression expr;
        final List<String> docIds;

        public TestCase(Expression expr, int... documentIDs) {
            this.expr = expr;
            final List<String> docIds = new ArrayList<>();
            for (int id: documentIDs) { docIds.add("doc" + id); }
            this.docIds = Collections.unmodifiableList(docIds);
        }
    }

    @Test
    public void testQueryGetColumnNameAfter32Items() throws CouchbaseLiteException {
        MutableDocument document = new MutableDocument("doc");
        document.setString("key", "value");
        saveDocInBaseTestDb(document);

        String query = "select\n"
            + "                `1`,`2`,`3`,`4`,`5`,`6`,`7`,`8`,`9`,`10`,`11`,`12`,\n"
            + "                `13`,`14`,`15`,`16`,`17`,`18`,`19`,`20`,`21`,`22`,`23`,`24`,\n"
            + "                `25`,`26`,`27`,`28`,`29`,`30`,`31`,`32`, `key` from _ limit 1";

        Query queryBuild = QueryBuilder.createQuery(query,baseTestDb);
        Result result;

        //expected results
        String key = "key";
        String value = "value";

        List<String> arrayResult = new ArrayList();
        for (int i = 0; i < 32; i++){
            arrayResult.add(null);
        }
        arrayResult.add(value);

        Map<String,String> mapResult = new HashMap();
        mapResult.put(key,value);

        try (ResultSet rs = queryBuild.execute()) {
            while ((result = rs.next()) != null) {
                assertEquals("{\"key\":\"value\"}", result.toJSON());
                assertEquals(arrayResult, result.toList());
                assertEquals(mapResult, result.toMap());
                assertEquals(value,result.getValue("key").toString());
                assertEquals(value, result.getString("key"));
                assertEquals(value, result.getString(32));
            }
        }
    }
    @Test
    public void testQueryDocumentExpiration() throws CouchbaseLiteException, InterruptedException {
        long now = System.currentTimeMillis();

        // this one should expire
        MutableDocument doc = new MutableDocument("doc");
        doc.setInt("answer", 42);
        doc.setString("notHere", "string");
        saveDocInBaseTestDb(doc);
        baseTestDb.setDocumentExpiration("doc", new Date(now + 500L));

        // this one is deleted
        MutableDocument doc10 = new MutableDocument("doc10");
        doc10.setInt("answer", 42);
        doc10.setString("notHere", "string");
        saveDocInBaseTestDb(doc10);
        baseTestDb.setDocumentExpiration("doc10", new Date(now + 2000L)); //deleted doc
        baseTestDb.delete(doc10);

        // should be in the result set
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setInt("answer", 42);
        doc1.setString("a", "string");
        saveDocInBaseTestDb(doc1);
        baseTestDb.setDocumentExpiration("doc1", new Date(now + 2000L));

        // should be in the result set
        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setInt("answer", 42);
        doc2.setString("b", "string");
        saveDocInBaseTestDb(doc2);
        baseTestDb.setDocumentExpiration("doc2", new Date(now + 3000L));

        // should be in the result set
        MutableDocument doc3 = new MutableDocument("doc3");
        doc3.setInt("answer", 42);
        doc3.setString("c", "string");
        saveDocInBaseTestDb(doc3);
        baseTestDb.setDocumentExpiration("doc3", new Date(now + 4000L));

        Thread.sleep(1000);

        // This should get all but the one that has expired
        // and the one that was deleted
        Query query = QueryBuilder.select(SR_DOCID, SR_EXPIRATION)
            .from(DataSource.database(baseTestDb))
            .where(Meta.expiration.lessThan(Expression.longValue(now + 6000L)));

        int rows = verifyQuery(query, false, (n, result) -> { });
        assertEquals(3, rows);
    }

    @Test
    public void testQueryDocumentIsNotDeleted() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        baseTestDb.save(doc1a);

        Query query = QueryBuilder.select(SR_DOCID, SR_DELETED)
            .from(DataSource.database(baseTestDb))
            .where(Meta.id.equalTo(Expression.string("doc1"))
                .and(Meta.deleted.equalTo(Expression.booleanValue(false))));

        int rows = verifyQuery(
            query,
            false,
            (n, result) -> {
                assertEquals(result.getString(0), "doc1");
                assertFalse(result.getBoolean(1));
            });
        assertEquals(1, rows);
    }

    @Test
    public void testQueryDocumentIsDeleted() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        doc.setInt("answer", 42);
        doc.setString("a", "string");
        saveDocInBaseTestDb(doc);

        baseTestDb.delete(baseTestDb.getDocument("doc1"));

        Query query = QueryBuilder.select(SR_DOCID, SR_DELETED)
            .from(DataSource.database(baseTestDb))
            .where(Meta.deleted.equalTo(Expression.booleanValue(true))
                .and(Meta.id.equalTo(Expression.string("doc1"))));

        assertEquals(1, verifyQuery(query, false, (n, result) -> { }));
    }

    @Test
    public void testNoWhereQuery() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");
        int numRows = verifyQuery(
            QueryBuilder.select(SR_DOCID, SR_SEQUENCE).from(DataSource.database(baseTestDb)),
            (n, result) -> {
                String docID = result.getString(0);
                String expectedID = String.format(Locale.ENGLISH, "doc-%03d", n);
                assertEquals(expectedID, docID);

                int sequence = result.getInt(1);
                assertEquals(n, sequence);

                Document doc = baseTestDb.getDocument(docID);
                assertEquals(expectedID, doc.getId());
                assertEquals(n, doc.getSequence());
            });
        assertEquals(100, numRows);
    }

    @Test
    public void testWhereComparison() throws CouchbaseLiteException {
        loadNumberedDocs(10);
        runTestCases(
            new TestCase(EXPR_NUMBER1.lessThan(Expression.intValue(3)), 1, 2),
            new TestCase(EXPR_NUMBER1.greaterThanOrEqualTo(Expression.intValue(3)), 3, 4, 5, 6, 7, 8, 9, 10),
            new TestCase(EXPR_NUMBER1.lessThanOrEqualTo(Expression.intValue(3)), 1, 2, 3),
            new TestCase(EXPR_NUMBER1.greaterThan(Expression.intValue(3)), 4, 5, 6, 7, 8, 9, 10),
            new TestCase(EXPR_NUMBER1.greaterThan(Expression.intValue(6)), 7, 8, 9, 10),
            new TestCase(EXPR_NUMBER1.lessThanOrEqualTo(Expression.intValue(6)), 1, 2, 3, 4, 5, 6),
            new TestCase(EXPR_NUMBER1.greaterThanOrEqualTo(Expression.intValue(6)), 6, 7, 8, 9, 10),
            new TestCase(EXPR_NUMBER1.lessThan(Expression.intValue(6)), 1, 2, 3, 4, 5),
            new TestCase(EXPR_NUMBER1.equalTo(Expression.intValue(7)), 7),
            new TestCase(EXPR_NUMBER1.notEqualTo(Expression.intValue(7)), 1, 2, 3, 4, 5, 6, 8, 9, 10)
        );
    }

    @Test
    public void testWhereArithmetic() throws CouchbaseLiteException {
        loadNumberedDocs(10);
        runTestCases(
            new TestCase(
                EXPR_NUMBER1.multiply(Expression.intValue(2)).greaterThan(Expression.intValue(3)),
                2, 3, 4, 5, 6, 7, 8, 9, 10),
            new TestCase(
                EXPR_NUMBER1.divide(Expression.intValue(2)).greaterThan(Expression.intValue(3)),
                8, 9, 10),
            new TestCase(
                EXPR_NUMBER1.modulo(Expression.intValue(2)).equalTo(Expression.intValue(0)),
                2, 4, 6, 8, 10),
            new TestCase(
                EXPR_NUMBER1.add(Expression.intValue(5)).greaterThan(Expression.intValue(10)),
                6, 7, 8, 9, 10),
            new TestCase(
                EXPR_NUMBER1.subtract(Expression.intValue(5)).greaterThan(Expression.intValue(0)),
                6, 7, 8, 9, 10),
            new TestCase(
                EXPR_NUMBER1.multiply(EXPR_NUMBER2).greaterThan(Expression.intValue(10)),
                2, 3, 4, 5, 6, 7, 8),
            new TestCase(
                EXPR_NUMBER2.divide(EXPR_NUMBER1).greaterThan(Expression.intValue(3)),
                1, 2),
            new TestCase(
                EXPR_NUMBER2.modulo(EXPR_NUMBER1).equalTo(Expression.intValue(0)),
                1, 2, 5, 10),
            new TestCase(
                EXPR_NUMBER1.add(EXPR_NUMBER2).equalTo(Expression.intValue(10)),
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            new TestCase(
                EXPR_NUMBER1.subtract(EXPR_NUMBER2).greaterThan(Expression.intValue(0)),
                6, 7, 8, 9, 10)
        );
    }

    @Test
    public void testWhereAndOr() throws CouchbaseLiteException {
        loadNumberedDocs(10);
        runTestCases(
            new TestCase(
                EXPR_NUMBER1.greaterThan(Expression.intValue(3)).and(EXPR_NUMBER2.greaterThan(Expression.intValue(3))),
                4, 5, 6),
            new TestCase(
                EXPR_NUMBER1.lessThan(Expression.intValue(3)).or(EXPR_NUMBER2.lessThan(Expression.intValue(3))),
                1, 2, 8, 9, 10)
        );
    }

    // Remove, when deprecated isNullOrMissing is removed
    @Test
    public void testWhereNullOrMissing() throws CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        saveDocInBaseTestDb(doc1);

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Tiger");
        doc2.setValue("address", "123 1st ave.");
        doc2.setValue("age", 20);
        saveDocInBaseTestDb(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        TestCase[] cases = {
            new TestCase(name.isNullOrMissing()),
            new TestCase(name.notNullOrMissing(), 1, 2),
            new TestCase(address.isNullOrMissing(), 1),
            new TestCase(address.notNullOrMissing(), 2),
            new TestCase(age.isNullOrMissing(), 1),
            new TestCase(age.notNullOrMissing(), 2),
            new TestCase(work.isNullOrMissing(), 1, 2),
            new TestCase(work.notNullOrMissing())
        };

        for (TestCase testCase: cases) {
            int numRows = verifyQuery(
                QueryBuilder.select(SR_DOCID).from(DataSource.database(baseTestDb)).where(testCase.expr),
                (n, result) -> {
                    if (n < testCase.docIds.size()) {
                        assertEquals(testCase.docIds.get(n - 1), result.getString(0));
                    }
                });
            assertEquals(testCase.docIds.size(), numRows);
        }
    }

    @Test
    public void testWhereValued() throws CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        saveDocInBaseTestDb(doc1);

        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setValue("name", "Tiger");
        doc2.setValue("address", "123 1st ave.");
        doc2.setValue("age", 20);
        saveDocInBaseTestDb(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        TestCase[] cases = {
            new TestCase(name.isNotValued()),
            new TestCase(name.isValued(), 1, 2),
            new TestCase(address.isNotValued(), 1),
            new TestCase(address.isNotValued(), 2),
            new TestCase(age.isNotValued(), 1),
            new TestCase(age.isValued(), 2),
            new TestCase(work.isNotValued(), 1, 2),
            new TestCase(work.isValued())
        };

        for (TestCase testCase: cases) {
            int numRows = verifyQuery(
                QueryBuilder.select(SR_DOCID).from(DataSource.database(baseTestDb)).where(testCase.expr),
                (n, result) -> {
                    if (n < testCase.docIds.size()) {
                        assertEquals(testCase.docIds.get(n - 1), result.getString(0));
                    }
                });
            assertEquals(testCase.docIds.size(), numRows);
        }
    }

    @Test
    public void testWhereIs() throws CouchbaseLiteException {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("string", "string");
        saveDocInBaseTestDb(doc1);

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("string").is(Expression.string("string")));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                String docID = result.getString(0);
                assertEquals(doc1.getId(), docID);
                Document doc = baseTestDb.getDocument(docID);
                assertEquals(doc1.getValue("string"), doc.getValue("string"));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testWhereIsNot() throws CouchbaseLiteException {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("string", "string");
        saveDocInBaseTestDb(doc1);

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("string").isNot(Expression.string("string1")));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                String docID = result.getString(0);
                assertEquals(doc1.getId(), docID);
                Document doc = baseTestDb.getDocument(docID);
                assertEquals(doc1.getValue("string"), doc.getValue("string"));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testWhereBetween() throws CouchbaseLiteException {
        loadNumberedDocs(10);
        runTestCases(new TestCase(EXPR_NUMBER1.between(Expression.intValue(3), Expression.intValue(7)), 3, 4, 5, 6, 7));
    }

    @Test
    public void testWhereIn() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");

        final Expression[] expected = {
            Expression.string("Marcy"),
            Expression.string("Margaretta"),
            Expression.string("Margrett"),
            Expression.string("Marlen"),
            Expression.string("Maryjo")};

        Query query = QueryBuilder.select(SelectResult.property("name.first"))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("name.first").in(expected))
            .orderBy(Ordering.property("name.first"));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                String name = result.getString(0);
                assertEquals(expected[n - 1].asJSON(), name);
            });
        assertEquals(expected.length, numRows);
    }

    @Test
    public void testWhereLike() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");

        Expression w = Expression.property("name.first").like(Expression.string("%Mar%"));
        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(baseTestDb))
            .where(w)
            .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(
            query,
            false,
            (n, result) -> {
                String docID = result.getString(0);
                Document doc = baseTestDb.getDocument(docID);
                Map<String, Object> name = doc.getDictionary("name").toMap();
                String firstName = (String) name.get("first");
                if (firstName != null) { firstNames.add(firstName); }
            });
        assertEquals(5, numRows);
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereRegex() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("name.first").regex(Expression.string("^Mar.*")))
            .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(
            query,
            false,
            (n, result) -> {
                String docID = result.getString(0);
                Document doc = baseTestDb.getDocument(docID);
                Map<String, Object> name = doc.getDictionary("name").toMap();
                String firstName = (String) name.get("first");
                if (firstName != null) { firstNames.add(firstName); }
            });
        assertEquals(5, numRows);
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereIndexMatch() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("sentences.json");

        baseTestDb.createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));

        Query query = QueryBuilder
            .select(SR_DOCID, SelectResult.property("sentence"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("sentence").match("'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank("sentence")).descending());

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
        assertEquals(2, numRows);
    }

    @Test
    public void testWhereMatch() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("sentences.json");

        baseTestDb.createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));

        Query query = QueryBuilder
            .select(SR_DOCID, SelectResult.property("sentence"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextFunction.match("sentence", "'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank("sentence")).descending());

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
        assertEquals(2, numRows);
    }

    @Test
    public void testFullTextIndexConfig() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("sentences.json");

        final FullTextIndexConfiguration idxConfig = new FullTextIndexConfiguration("sentence", "nonesense");
        assertFalse(idxConfig.isIgnoringAccents());
        assertEquals("en", idxConfig.getLanguage());

        idxConfig.setLanguage("en-ca").ignoreAccents(true);
        assertEquals("en-ca", idxConfig.getLanguage());
        assertTrue(idxConfig.isIgnoringAccents());

        baseTestDb.createIndex("sentence", idxConfig);

        Query query = QueryBuilder
            .select(SR_DOCID, SelectResult.property("sentence"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("sentence").match("'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank("sentence")).descending());

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
        assertEquals(2, numRows);
    }

    // Test courtesy of Jayahari Vavachan
    @Test
    public void testN1QLFTSQuery() throws IOException, CouchbaseLiteException, JSONException {
        loadJSONResource("sentences.json");

        baseTestDb.createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));

        int numRows = verifyQuery(
            baseTestDb.createQuery("SELECT _id FROM _default WHERE MATCH(sentence, 'Dummie woman')"),
            (n, result) -> assertNotNull(result.getString(0)));

        assertEquals(2, numRows);
    }

    @Test
    public void testOrderBy() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");

        Ordering.SortOrder order = Ordering.expression(Expression.property("name.first"));

        // Don't replace this with Comparator.naturalOrder.
        // it doesn't exist on older versions of Android
        //noinspection Convert2MethodRef,ComparatorCombinators
        testOrdered(order.ascending(), (c1, c2) -> c1.compareTo(c2));
        testOrdered(order.descending(), String::compareTo);
    }

    // https://github.com/couchbase/couchbase-lite-ios/issues/1669
    // https://github.com/couchbase/couchbase-lite-core/issues/81
    @Test
    public void testSelectDistinct() throws CouchbaseLiteException {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("number", 20);
        saveDocInBaseTestDb(doc1);

        MutableDocument doc2 = new MutableDocument();
        doc2.setValue("number", 20);
        saveDocInBaseTestDb(doc2);

        SelectResult S_NUMBER = SelectResult.property("number");
        Query query = QueryBuilder.selectDistinct(S_NUMBER).from(DataSource.database(baseTestDb));

        int numRows = verifyQuery(query, (n, result) -> assertEquals(20, result.getInt(0)));
        assertEquals(1, numRows);
    }

    @Test
    public void testJoin() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final MutableDocument doc1 = new MutableDocument("joinme");
        doc1.setValue("theone", 42);
        saveDocInBaseTestDb(doc1);

        Join join = Join.join(DataSource.database(this.baseTestDb).as("secondary"))
            .on(Expression.property("number1").from("main").equalTo(Expression.property("theone").from("secondary")));

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id.from("main")))
            .from(DataSource.database(this.baseTestDb).as("main"))
            .join(join);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                String docID = result.getString(0);
                Document doc = baseTestDb.getDocument(docID);
                assertEquals(42, doc.getInt("number1"));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testLeftJoin() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final MutableDocument joinme = new MutableDocument("joinme");
        joinme.setValue("theone", 42);
        saveDocInBaseTestDb(joinme);

        //Expression mainPropExpr = ;

        Query query = QueryBuilder.select(
                SelectResult.expression(Expression.property("number2").from("main")),
                SelectResult.expression(Expression.property("theone").from("secondary")))
            .from(DataSource.database(this.baseTestDb).as("main"))
            .join(Join.leftJoin(DataSource.database(this.baseTestDb).as("secondary"))
                .on(Expression.property("number1").from("main")
                    .equalTo(Expression.property("theone").from("secondary"))));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                if (n == 41) {
                    assertEquals(59, result.getInt(0));
                    assertNull(result.getValue(1));
                }
                if (n == 42) {
                    assertEquals(58, result.getInt(0));
                    assertEquals(42, result.getInt(1));
                }
            });
        assertEquals(101, numRows);
    }

    @Test
    public void testCrossJoin() throws CouchbaseLiteException {
        loadNumberedDocs(10);

        Query query = QueryBuilder.select(
                SelectResult.expression(Expression.property("number1").from("main")),
                SelectResult.expression(Expression.property("number2").from("secondary")))
            .from(DataSource.database(this.baseTestDb).as("main"))
            .join(Join.crossJoin(DataSource.database(this.baseTestDb).as("secondary")));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                int num1 = result.getInt(0);
                int num2 = result.getInt(1);
                assertEquals((num1 - 1) % 10, (n - 1) / 10);
                assertEquals((10 - num2) % 10, n % 10);
            });
        assertEquals(100, numRows);
    }

    @Test
    public void testGroupBy() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");

        final List<String> expectedStates = Arrays.asList("AL", "CA", "CO", "FL", "IA");
        final List<Integer> expectedCounts = Arrays.asList(1, 6, 1, 1, 3);
        final List<String> expectedMaxZips = Arrays.asList("35243", "94153", "81223", "33612", "50801");

        DataSource ds = DataSource.database(this.baseTestDb);

        Expression state = Expression.property("contact.address.state");
        Expression count = Function.count(Expression.intValue(1));
        Expression zip = Expression.property("contact.address.zip");
        Expression maxZip = Function.max(zip);
        Expression gender = Expression.property("gender");

        SelectResult rsState = SelectResult.property("contact.address.state");
        SelectResult rsCount = SelectResult.expression(count);
        SelectResult rsMaxZip = SelectResult.expression(maxZip);

        Ordering ordering = Ordering.expression(state);

        Query query = QueryBuilder
            .select(rsState, rsCount, rsMaxZip)
            .from(ds)
            .where(gender.equalTo(Expression.string("female")))
            .groupBy(state)
            .orderBy(ordering);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                String state1 = (String) result.getValue(0);
                long count1 = (long) result.getValue(1);
                String maxZip1 = (String) result.getValue(2);
                if (n - 1 < expectedStates.size()) {
                    assertEquals(expectedStates.get(n - 1), state1);
                    assertEquals((int) expectedCounts.get(n - 1), count1);
                    assertEquals(expectedMaxZips.get(n - 1), maxZip1);
                }
            });
        assertEquals(31, numRows);

        // With HAVING:
        final List<String> expectedStates2 = Arrays.asList("CA", "IA", "IN");
        final List<Integer> expectedCounts2 = Arrays.asList(6, 3, 2);
        final List<String> expectedMaxZips2 = Arrays.asList("94153", "50801", "47952");

        Expression havingExpr = count.greaterThan(Expression.intValue(1));

        query = QueryBuilder
            .select(rsState, rsCount, rsMaxZip)
            .from(ds)
            .where(gender.equalTo(Expression.string("female")))
            .groupBy(state)
            .having(havingExpr)
            .orderBy(ordering);

        numRows = verifyQuery(
            query,
            (n, result) -> {
                String state12 = (String) result.getValue(0);
                long count12 = (long) result.getValue(1);
                String maxZip12 = (String) result.getValue(2);
                if (n - 1 < expectedStates2.size()) {
                    assertEquals(expectedStates2.get(n - 1), state12);
                    assertEquals((long) expectedCounts2.get(n - 1), count12);
                    assertEquals(expectedMaxZips2.get(n - 1), maxZip12);
                }
            });
        assertEquals(15, numRows);
    }

    @Test
    public void testParameters() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        Query query = QueryBuilder
            .select(SR_NUMBER1)
            .from(DataSource.database(this.baseTestDb))
            .where(EXPR_NUMBER1.between(Expression.parameter("num1"), Expression.parameter("num2")))
            .orderBy(Ordering.expression(EXPR_NUMBER1));

        Parameters params = new Parameters(query.getParameters())
            .setValue("num1", 2)
            .setValue("num2", 5);
        query.setParameters(params);

        final long[] expectedNumbers = {2, 3, 4, 5};
        int numRows = verifyQuery(
            query,
            (n, result) -> assertEquals(expectedNumbers[n - 1], (long) result.getValue(0)));
        assertEquals(4, numRows);
    }

    @Test
    public void testMeta() throws CouchbaseLiteException {
        loadNumberedDocs(5);

        Query query = QueryBuilder
            .select(SR_DOCID, SR_SEQUENCE, SR_REVID, SR_NUMBER1)
            .from(DataSource.database(this.baseTestDb))
            .orderBy(Ordering.expression(Meta.sequence));

        final String[] expectedDocIDs = {"doc1", "doc2", "doc3", "doc4", "doc5"};

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                String docID1 = (String) result.getValue(0);
                String docID2 = result.getString(0);
                String docID3 = (String) result.getValue("id");
                String docID4 = result.getString("id");

                long seq1 = (long) result.getValue(1);
                long seq2 = result.getLong(1);
                long seq3 = (long) result.getValue("sequence");
                long seq4 = result.getLong("sequence");

                String revId1 = (String) result.getValue(2);
                String revId2 = result.getString(2);
                String revId3 = (String) result.getValue("revisionID");
                String revId4 = result.getString("revisionID");

                long number = (long) result.getValue(3);

                assertEquals(docID1, docID2);
                assertEquals(docID2, docID3);
                assertEquals(docID3, docID4);
                assertEquals(docID4, expectedDocIDs[n - 1]);

                assertEquals(n, seq1);
                assertEquals(n, seq2);
                assertEquals(n, seq3);
                assertEquals(n, seq4);

                assertEquals(revId1, revId2);
                assertEquals(revId2, revId3);
                assertEquals(revId3, revId4);
                assertEquals(revId4, baseTestDb.getDocument(docID1).getRevisionID());

                assertEquals(n, number);
            });
        assertEquals(5, numRows);
    }

    @Test
    public void testRevisionIdInCreate() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        baseTestDb.save(doc);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.revisionID))
            .from(DataSource.database(this.baseTestDb))
            .where(Meta.id.equalTo(Expression.string(doc.getId())));

        int numRows = verifyQuery(query, (n, result) -> assertEquals(doc.getRevisionID(), result.getString(0)));

        assertEquals(1, numRows);
    }

    @Test
    public void testRevisionIdInUpdate() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        baseTestDb.save(doc);

        doc = baseTestDb.getDocument(doc.getId()).toMutable();
        doc.setString("DEC", "Maynard");
        baseTestDb.save(doc);
        final String revId = doc.getRevisionID();

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.revisionID))
            .from(DataSource.database(this.baseTestDb))
            .where(Meta.id.equalTo(Expression.string(doc.getId())));

        int numRows = verifyQuery(query, (n, result) -> assertEquals(revId, result.getString(0)));

        assertEquals(1, numRows);
    }

    @Test
    public void testRevisionIdInWhere() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        baseTestDb.save(doc);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(this.baseTestDb))
            .where(Meta.revisionID.equalTo(Expression.string(doc.getRevisionID())));

        int numRows = verifyQuery(query, (n, result) -> assertEquals(doc.getId(), result.getString(0)));

        assertEquals(1, numRows);
    }

    @Test
    public void testRevisionIdInDelete() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        baseTestDb.save(doc);

        final Document dbDoc = baseTestDb.getDocument(doc.getId());
        assertNotNull(dbDoc);

        baseTestDb.delete(dbDoc);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.revisionID))
            .from(DataSource.database(this.baseTestDb))
            .where(Meta.deleted.equalTo(Expression.booleanValue(true)));

        int numRows = verifyQuery(query, (n, result) -> assertEquals(dbDoc.getRevisionID(), result.getString(0)));

        assertEquals(1, numRows);
    }

    @Test
    public void testLimit() throws CouchbaseLiteException {
        loadNumberedDocs(10);

        DataSource dataSource = DataSource.database(this.baseTestDb);

        Query query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(Expression.intValue(5));

        final long[] expectedNumbers = {1, 2, 3, 4, 5};
        int numRows = verifyQuery(
            query,
            (n, result) -> {
                long number = (long) result.getValue(0);
                assertEquals(expectedNumbers[n - 1], number);
            });
        assertEquals(5, numRows);

        Expression paramExpr = Expression.parameter("LIMIT_NUM");
        query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(paramExpr);
        Parameters params = new Parameters(query.getParameters()).setValue("LIMIT_NUM", 3);
        query.setParameters(params);

        final long[] expectedNumbers2 = {1, 2, 3};
        numRows = verifyQuery(
            query,
            (n, result) -> {
                long number = (long) result.getValue(0);
                assertEquals(expectedNumbers2[n - 1], number);
            });
        assertEquals(3, numRows);
    }

    @Test
    public void testLimitOffset() throws CouchbaseLiteException {
        loadNumberedDocs(10);

        DataSource dataSource = DataSource.database(this.baseTestDb);

        Query query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(Expression.intValue(5), Expression.intValue(3));

        final long[] expectedNumbers = {4, 5, 6, 7, 8};
        int numRows = verifyQuery(
            query,
            (n, result) -> assertEquals(expectedNumbers[n - 1], (long) result.getValue(0)));
        assertEquals(5, numRows);

        Expression paramLimitExpr = Expression.parameter("LIMIT_NUM");
        Expression paramOffsetExpr = Expression.parameter("OFFSET_NUM");
        query = QueryBuilder
            .select(SR_NUMBER1)
            .from(dataSource)
            .orderBy(Ordering.expression(EXPR_NUMBER1))
            .limit(paramLimitExpr, paramOffsetExpr);
        Parameters params = new Parameters(query.getParameters())
            .setValue("LIMIT_NUM", 3)
            .setValue("OFFSET_NUM", 5);
        query.setParameters(params);

        final long[] expectedNumbers2 = {6, 7, 8};
        numRows = verifyQuery(
            query,
            (n, result) -> assertEquals(expectedNumbers2[n - 1], (long) result.getValue(0)));
        assertEquals(3, numRows);
    }

    @Test
    public void testQueryResult() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");
        Query query = QueryBuilder.select(
                SelectResult.property("name.first").as("firstname"),
                SelectResult.property("name.last").as("lastname"),
                SelectResult.property("gender"),
                SelectResult.property("contact.address.city"))
            .from(DataSource.database(baseTestDb));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(4, result.count());
                assertEquals(result.getValue(0), result.getValue("firstname"));
                assertEquals(result.getValue(1), result.getValue("lastname"));
                assertEquals(result.getValue(2), result.getValue("gender"));
                assertEquals(result.getValue(3), result.getValue("city"));
            });
        assertEquals(100, numRows);
    }

    @Test
    public void testQueryProjectingKeys() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.avg(EXPR_NUMBER1)),
                SelectResult.expression(Function.count(EXPR_NUMBER1)),
                SelectResult.expression(Function.min(EXPR_NUMBER1)).as("min"),
                SelectResult.expression(Function.max(EXPR_NUMBER1)),
                SelectResult.expression(Function.sum(EXPR_NUMBER1)).as("sum"))
            .from(DataSource.database(baseTestDb));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(5, result.count());
                assertEquals(result.getValue(0), result.getValue("$1"));
                assertEquals(result.getValue(1), result.getValue("$2"));
                assertEquals(result.getValue(2), result.getValue("min"));
                assertEquals(result.getValue(3), result.getValue("$3"));
                assertEquals(result.getValue(4), result.getValue("sum"));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testQuantifiedOperators() throws JSONException, IOException, CouchbaseLiteException {
        loadJSONResource("names_100.json");

        DataSource ds = DataSource.database(baseTestDb);

        Expression exprLikes = Expression.property("likes");
        VariableExpression exprVarLike = ArrayExpression.variable("LIKE");

        // ANY:
        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(ds)
            .where(ArrayExpression
                .any(exprVarLike)
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("climbing"))));

        final AtomicInteger i = new AtomicInteger(0);
        final String[] expected = {"doc-017", "doc-021", "doc-023", "doc-045", "doc-060"};
        int numRows = verifyQuery(
            query,
            false,
            (n, result) -> assertEquals(expected[i.getAndIncrement()], result.getString(0)));
        assertEquals(expected.length, numRows);

        // EVERY:
        query = QueryBuilder
            .select(SR_DOCID)
            .from(ds)
            .where(ArrayExpression
                .every(ArrayExpression.variable("LIKE"))
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("taxes"))));

        numRows = verifyQuery(
            query,
            false,
            (n, result) -> { if (n == 1) { assertEquals("doc-007", result.getString(0)); } }
        );
        assertEquals(42, numRows);

        // ANY AND EVERY:
        query = QueryBuilder
            .select(SR_DOCID)
            .from(ds)
            .where(ArrayExpression
                .anyAndEvery(ArrayExpression.variable("LIKE"))
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("taxes"))));

        numRows = verifyQuery(query, false, (n, result) -> { });
        assertEquals(0, numRows);
    }

    @Test
    public void testAggregateFunctions() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.avg(EXPR_NUMBER1)),
                SelectResult.expression(Function.count(EXPR_NUMBER1)),
                SelectResult.expression(Function.min(EXPR_NUMBER1)),
                SelectResult.expression(Function.max(EXPR_NUMBER1)),
                SelectResult.expression(Function.sum(EXPR_NUMBER1)))
            .from(DataSource.database(this.baseTestDb));
        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(50.5F, (float) result.getValue(0), 0.0F);
                assertEquals(100L, (long) result.getValue(1));
                assertEquals(1L, (long) result.getValue(2));
                assertEquals(100L, (long) result.getValue(3));
                assertEquals(5050L, (long) result.getValue(4));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testArrayFunctions() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);
        saveDocInBaseTestDb(doc);

        DataSource ds = DataSource.database(baseTestDb);

        Expression exprArray = Expression.property("array");

        Query query = QueryBuilder.select(SelectResult.expression(ArrayFunction.length(exprArray))).from(ds);

        int numRows = verifyQuery(query, (n, result) -> assertEquals(2, result.getInt(0)));
        assertEquals(1, numRows);

        query = QueryBuilder.select(
                SelectResult.expression(ArrayFunction.contains(exprArray, Expression.string("650-123-0001"))),
                SelectResult.expression(ArrayFunction.contains(exprArray, Expression.string("650-123-0003"))))
            .from(ds);

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertTrue(result.getBoolean(0));
                assertFalse(result.getBoolean(1));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testArrayFunctionsEmptyArgs() {
        Expression exprArray = Expression.property("array");

        assertThrows(
            IllegalArgumentException.class,
            () -> ArrayFunction.contains(null, Expression.string("650-123-0001")));

        assertThrows(IllegalArgumentException.class, () -> ArrayFunction.contains(exprArray, null));

        assertThrows(IllegalArgumentException.class, () -> ArrayFunction.length(null));
    }

    @Test
    public void testMathFunctions() throws CouchbaseLiteException {
        final String key = "number";
        final double num = 0.6;

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue(key, num);
        saveDocInBaseTestDb(doc);

        Expression propNumber = Expression.property(key);

        final MathFn[] fns = {
            new MathFn("abs", Function.abs(propNumber), Math.abs(num)),
            new MathFn("acos", Function.acos(propNumber), Math.acos(num)),
            new MathFn("asin", Function.asin(propNumber), Math.asin(num)),
            new MathFn("atan", Function.atan(propNumber), Math.atan(num)),
            new MathFn(
                "atan2",
                Function.atan2(Expression.doubleValue(90.0), Expression.doubleValue(num)),
                Math.atan2(90.0, num)),
            new MathFn("ceil", Function.ceil(propNumber), Math.ceil(num)),
            new MathFn("cos", Function.cos(propNumber), Math.cos(num)),
            new MathFn("degrees", Function.degrees(propNumber), num * 180.0 / Math.PI),
            new MathFn("exp", Function.exp(propNumber), Math.exp(num)),
            new MathFn("floor", Function.floor(propNumber), Math.floor(num)),
            new MathFn("ln", Function.ln(propNumber), Math.log(num)),
            new MathFn("log10", Function.log(propNumber), Math.log10(num)),
            new MathFn("pow", Function.power(propNumber, Expression.intValue(2)), Math.pow(num, 2)),
            new MathFn("rad", Function.radians(propNumber), num * Math.PI / 180.0),
            new MathFn("round", Function.round(propNumber), (double) Math.round(num)),
            new MathFn(
                "round 10",
                Function.round(propNumber, Expression.intValue(1)),
                Math.round(num * 10.0) / 10.0),
            new MathFn("sign", Function.sign(propNumber), 1.0),
            new MathFn("sin", Function.sin(propNumber), Math.sin(num)),
            new MathFn("sqrt", Function.sqrt(propNumber), Math.sqrt(num)),
            new MathFn("tan", Function.tan(propNumber), Math.tan(num)),
            new MathFn("trunc", Function.trunc(propNumber), 0.0),
            new MathFn("trunc 10", Function.trunc(propNumber, Expression.intValue(1)), 0.6)
        };

        for (MathFn f: fns) {
            int nRows = verifyQuery(
                QueryBuilder.select(SelectResult.expression(f.expr)).from(DataSource.database(baseTestDb)),
                (n, result) -> assertEquals(f.name, f.expected, result.getDouble(0), 1E-12));
            assertEquals(1, nRows);
        }
    }

    @Test
    public void testStringFunctions() throws CouchbaseLiteException {
        final String str = "  See you 18r  ";
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("greeting", str);
        saveDocInBaseTestDb(doc);

        DataSource ds = DataSource.database(baseTestDb);

        Expression prop = Expression.property("greeting");

        // Contains:
        Expression fnContains1 = Function.contains(prop, Expression.string("8"));
        Expression fnContains2 = Function.contains(prop, Expression.string("9"));
        SelectResult srFnContains1 = SelectResult.expression(fnContains1);
        SelectResult srFnContains2 = SelectResult.expression(fnContains2);

        Query query = QueryBuilder.select(srFnContains1, srFnContains2).from(ds);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertTrue(result.getBoolean(0));
                assertFalse(result.getBoolean(1));
            });
        assertEquals(1, numRows);

        // Length
        Expression fnLength = Function.length(prop);
        query = QueryBuilder.select(SelectResult.expression(fnLength)).from(ds);

        numRows = verifyQuery(query, (n, result) -> assertEquals(str.length(), result.getInt(0)));
        assertEquals(1, numRows);

        // Lower, Ltrim, Rtrim, Trim, Upper:
        Expression fnLower = Function.lower(prop);
        Expression fnLTrim = Function.ltrim(prop);
        Expression fnRTrim = Function.rtrim(prop);
        Expression fnTrim = Function.trim(prop);
        Expression fnUpper = Function.upper(prop);

        query = QueryBuilder.select(
                SelectResult.expression(fnLower),
                SelectResult.expression(fnLTrim),
                SelectResult.expression(fnRTrim),
                SelectResult.expression(fnTrim),
                SelectResult.expression(fnUpper))
            .from(ds);

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(str.toLowerCase(Locale.ENGLISH), result.getString(0));
                assertEquals(str.replaceAll("^\\s+", ""), result.getString(1));
                assertEquals(str.replaceAll("\\s+$", ""), result.getString(2));
                assertEquals(str.trim(), result.getString(3));
                assertEquals(str.toUpperCase(Locale.ENGLISH), result.getString(4));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testSelectAll() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final DataSource.As ds = DataSource.database(baseTestDb);
        final String dbName = baseTestDb.getName();

        // SELECT *
        Query query = QueryBuilder.select(SR_ALL).from(ds);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(dbName);
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            });
        assertEquals(100, numRows);

        // SELECT *, number1
        query = QueryBuilder.select(SR_ALL, SR_NUMBER1).from(ds);

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(2, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(dbName);
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
                assertEquals(n, result.getInt(1));
                assertEquals(n, result.getInt("number1"));
            });
        assertEquals(100, numRows);

        // SELECT testdb.*
        query = QueryBuilder.select(SelectResult.all().from(dbName)).from(ds.as(dbName));

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(dbName);
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            });
        assertEquals(100, numRows);

        // SELECT testdb.*, testdb.number1
        query = QueryBuilder.select(
                SelectResult.all().from(dbName),
                SelectResult.expression(Expression.property("number1").from(dbName)))
            .from(ds.as(dbName));

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(2, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(dbName);
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
                assertEquals(n, result.getInt(1));
                assertEquals(n, result.getInt("number1"));
            });
        assertEquals(100, numRows);
    }

    // With no locale, characters with diacritics should be
    // treated as the original letters A, E, I, O, U,
    @Test
    public void testUnicodeCollationWithLocaleNone() throws CouchbaseLiteException {
        createAlphaDocs();

        Collation noLocale = Collation.unicode()
            .setLocale(null)
            .setIgnoreCase(false)
            .setIgnoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.expression(Expression.property("string").collate(noLocale)));

        final String[] expected = {"A", "Å", "B", "Z"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
        assertEquals(expected.length, numRows);
    }

    // In the Spanish alphabet, the six characters with diacritics Á, É, Í, Ó, Ú, Ü
    // are treated as the original letters A, E, I, O, U,
    @Test
    public void testUnicodeCollationWithLocaleSpanish() throws CouchbaseLiteException {
        createAlphaDocs();

        Collation localeEspanol = Collation.unicode()
            .setLocale("es")
            .setIgnoreCase(false)
            .setIgnoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.expression(Expression.property("string").collate(localeEspanol)));

        final String[] expected = {"A", "Å", "B", "Z"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
        assertEquals(expected.length, numRows);
    }

    // In the Swedish alphabet, there are three extra vowels
    // placed at its end (..., X, Y, Z, Å, Ä, Ö),
    // Early versions of Android do not support the Swedish Locale
    @Test
    public void testUnicodeCollationWithLocaleSwedish() throws CouchbaseLiteException {
        skipTestWhen("SWEDISH UNSUPPORTED");

        createAlphaDocs();

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.expression(Expression.property("string")
                .collate(Collation.unicode()
                    .setLocale("sv")
                    .setIgnoreCase(false)
                    .setIgnoreAccents(false))));

        String[] expected = {"A", "B", "Z", "Å"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
        assertEquals(expected.length, numRows);
    }

    @Test
    public void testCompareWithUnicodeCollation() throws CouchbaseLiteException {
        class CollationTest {
            private final String val;
            private final String test;
            private final boolean mode;
            private final Collation collation;

            public CollationTest(String val, String test, boolean mode, Collation collation) {
                this.val = val;
                this.test = test;
                this.mode = mode;
                this.collation = collation;
            }

            @Override
            @NonNull
            public String toString() { return "test '" + val + "' " + ((mode) ? "=" : "<") + " '" + test + "'"; }
        }

        Collation bothSensitive = Collation.unicode().setLocale(null).setIgnoreCase(false).setIgnoreAccents(false);
        Collation accentSensitive = Collation.unicode().setLocale(null).setIgnoreCase(true).setIgnoreAccents(false);
        Collation caseSensitive = Collation.unicode().setLocale(null).setIgnoreCase(false).setIgnoreAccents(true);
        Collation noSensitive = Collation.unicode().setLocale(null).setIgnoreCase(true).setIgnoreAccents(true);

        List<CollationTest> testData = Arrays.asList(
            // Edge cases: empty and 1-char strings:
            new CollationTest("", "", true, bothSensitive),
            new CollationTest("", "a", false, bothSensitive),
            new CollationTest("a", "a", true, bothSensitive),

            // Case sensitive: lowercase come first by unicode rules:
            new CollationTest("a", "A", false, bothSensitive),
            new CollationTest("abc", "abc", true, bothSensitive),
            new CollationTest("Aaa", "abc", false, bothSensitive),
            new CollationTest("abc", "abC", false, bothSensitive),
            new CollationTest("AB", "abc", false, bothSensitive),

            // Case insensitive:
            new CollationTest("ABCDEF", "ZYXWVU", false, accentSensitive),
            new CollationTest("ABCDEF", "Z", false, accentSensitive),

            new CollationTest("a", "A", true, accentSensitive),
            new CollationTest("abc", "ABC", true, accentSensitive),
            new CollationTest("ABA", "abc", false, accentSensitive),

            new CollationTest("commonprefix1", "commonprefix2", false, accentSensitive),
            new CollationTest("commonPrefix1", "commonprefix2", false, accentSensitive),

            new CollationTest("abcdef", "abcdefghijklm", false, accentSensitive),
            new CollationTest("abcdeF", "abcdefghijklm", false, accentSensitive),

            // Now bring in non-ASCII characters:
            new CollationTest("a", "á", false, accentSensitive),
            new CollationTest("", "á", false, accentSensitive),
            new CollationTest("á", "á", true, accentSensitive),
            new CollationTest("•a", "•A", true, accentSensitive),

            new CollationTest("test a", "test á", false, accentSensitive),
            new CollationTest("test á", "test b", false, accentSensitive),
            new CollationTest("test á", "test Á", true, accentSensitive),
            new CollationTest("test á1", "test Á2", false, accentSensitive),

            // Case sensitive, diacritic sensitive:
            new CollationTest("ABCDEF", "ZYXWVU", false, bothSensitive),
            new CollationTest("ABCDEF", "Z", false, bothSensitive),
            new CollationTest("a", "A", false, bothSensitive),
            new CollationTest("abc", "ABC", false, bothSensitive),
            new CollationTest("•a", "•A", false, bothSensitive),
            new CollationTest("test a", "test á", false, bothSensitive),
            new CollationTest("Ähnlichkeit", "apple", false, bothSensitive), // Because 'h'-vs-'p' beats 'Ä'-vs-'a'
            new CollationTest("ax", "Äz", false, bothSensitive),
            new CollationTest("test a", "test Á", false, bothSensitive),
            new CollationTest("test Á", "test e", false, bothSensitive),
            new CollationTest("test á", "test Á", false, bothSensitive),
            new CollationTest("test á", "test b", false, bothSensitive),
            new CollationTest("test u", "test Ü", false, bothSensitive),

            // Case sensitive, diacritic insensitive
            new CollationTest("abc", "ABC", false, caseSensitive),
            new CollationTest("test á", "test a", true, caseSensitive),
            new CollationTest("test a", "test á", true, caseSensitive),
            new CollationTest("test á", "test A", false, caseSensitive),
            new CollationTest("test á", "test b", false, caseSensitive),
            new CollationTest("test á", "test Á", false, caseSensitive),

            // Case and diacritic insensitive
            new CollationTest("test á", "test Á", true, noSensitive)
        );

        for (CollationTest data: testData) {
            MutableDocument mDoc = new MutableDocument();
            mDoc.setValue("value", data.val);
            Document doc = saveDocInBaseTestDb(mDoc);

            Expression test = Expression.value(data.test);
            Expression comparison = Expression.property("value").collate(data.collation);
            comparison = data.mode ? comparison.equalTo(test) : comparison.lessThan(test);

            Query query = QueryBuilder.select().from(DataSource.database(baseTestDb)).where(comparison);

            int numRows = verifyQuery(
                query,
                (n, result) -> {
                    assertEquals(1, n);
                    assertNotNull(result);
                });
            assertEquals(data.toString(), 1, numRows);

            baseTestDb.delete(doc);
        }
    }

    @Test
    public void testLiveQuery() throws CouchbaseLiteException, InterruptedException {
        loadNumberedDocs(100);

        Query query = QueryBuilder
            .select(SR_DOCID)
            .from(DataSource.database(baseTestDb))
            .where(EXPR_NUMBER1.lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(2);
        QueryChangeListener listener = change -> {
            ResultSet rs = change.getResults();
            if (latch.getCount() == 2) {
                int count = 0;
                while (rs.next() != null) { count++; }
                assertEquals(9, count);
            }
            else if (latch.getCount() == 1) {
                int count = 0;
                for (Result result: rs) {
                    if (count == 0) {
                        Document doc = baseTestDb.getDocument(result.getString(0));
                        assertEquals(-1L, doc.getValue("number1"));
                    }
                    count++;
                }
                assertEquals(10, count);
            }

            latch.countDown();
        };

        ListenerToken token = query.addChangeListener(testSerialExecutor, listener);
        try {
            // create one doc
            executeAsync(500, () -> {
                try { createNumberedDocInBaseTestDb(-1, 100); }
                catch (CouchbaseLiteException e) { throw new RuntimeException(e); }
            });
            // wait till listener is called
            assertTrue(latch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    @Test
    public void testLiveQueryNoUpdate() throws CouchbaseLiteException, InterruptedException {
        testLiveQueryNoUpdate(false);
    }

    @SlowTest
    @Test
    public void testLiveQueryNoUpdateConsumeAll() throws CouchbaseLiteException, InterruptedException {
        testLiveQueryNoUpdate(true);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1356
    @Test
    public void testCountFunctions() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        DataSource ds = DataSource.database(this.baseTestDb);
        Expression cnt = Function.count(EXPR_NUMBER1);

        SelectResult rsCnt = SelectResult.expression(cnt);
        Query query = QueryBuilder.select(rsCnt).from(ds);

        int numRows = verifyQuery(query, (n, result) -> assertEquals(100L, (long) result.getValue(0)));
        assertEquals(1, numRows);
    }

    @Test
    public void testJoinWithArrayContains() throws CouchbaseLiteException {
        // Data preparation
        // Hotels
        MutableDocument hotel1 = new MutableDocument("hotel1");
        hotel1.setString("type", "hotel");
        hotel1.setString("name", "Hilton");
        baseTestDb.save(hotel1);

        MutableDocument hotel2 = new MutableDocument("hotel2");
        hotel2.setString("type", "hotel");
        hotel2.setString("name", "Sheraton");
        baseTestDb.save(hotel2);

        MutableDocument hotel3 = new MutableDocument("hotel2");
        hotel3.setString("type", "hotel");
        hotel3.setString("name", "Marriott");
        baseTestDb.save(hotel3);

        // Bookmark
        MutableDocument bookmark1 = new MutableDocument("bookmark1");
        bookmark1.setString("type", "bookmark");
        bookmark1.setString("title", "Bookmark For Hawaii");
        MutableArray hotels1 = new MutableArray();
        hotels1.addString("hotel1");
        hotels1.addString("hotel2");
        bookmark1.setArray("hotels", hotels1);
        baseTestDb.save(bookmark1);

        MutableDocument bookmark2 = new MutableDocument("bookmark2");
        bookmark2.setString("type", "bookmark");
        bookmark2.setString("title", "Bookmark for New York");
        MutableArray hotels2 = new MutableArray();
        hotels2.addString("hotel3");
        bookmark2.setArray("hotels", hotels2);
        baseTestDb.save(bookmark2);

        // Join Query
        DataSource mainDS = DataSource.database(this.baseTestDb).as("main");
        DataSource secondaryDS = DataSource.database(this.baseTestDb).as("secondary");

        Expression typeExpr = Expression.property("type").from("main");
        Expression hotelsExpr = Expression.property("hotels").from("main");
        Expression hotelIdExpr = Meta.id.from("secondary");
        Expression joinExpr = ArrayFunction.contains(hotelsExpr, hotelIdExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult srMainAll = SelectResult.all().from("main");
        SelectResult srSecondaryAll = SelectResult.all().from("secondary");
        Query query = QueryBuilder
            .select(srMainAll, srSecondaryAll)
            .from(mainDS)
            .join(join)
            .where(typeExpr.equalTo(Expression.string("bookmark")));

        verifyQuery(query, (n, result) -> Report.log(LogLevel.INFO, "RESULT: " + result.toMap()));
    }

    @Test
    public void testJoinWithEmptyArgs() {
        DataSource mainDS = DataSource.database(this.baseTestDb).as("main");

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).join((Join[]) null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).where(null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).groupBy((Expression[]) null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).orderBy((Ordering[]) null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).limit(null));

        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all()).from(mainDS).limit(null, null));
    }

    //https://github.com/couchbase/couchbase-lite-android/issues/1785
    @Test
    public void testResultToMapWithBoolean() throws CouchbaseLiteException {
        MutableDocument exam1 = new MutableDocument("exam1");
        exam1.setString("exam type", "final");
        exam1.setString("question", "There are 45 states in the US.");
        exam1.setBoolean("answer", false);
        baseTestDb.save(exam1);

        MutableDocument exam2 = new MutableDocument("exam2");
        exam2.setString("exam type", "final");
        exam2.setString("question", "There are 100 senators in the US.");
        exam2.setBoolean("answer", true);
        baseTestDb.save(exam2);

        Query query = QueryBuilder.select(SelectResult.all())
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("exam type").equalTo(Expression.string("final"))
                .and(Expression.property("answer").equalTo(Expression.booleanValue(true))));

        final String dbName = baseTestDb.getName();
        verifyQuery(
            query,
            (n, result) -> {
                Map<String, Object> maps = result.toMap();
                assertNotNull(maps);
                Map<?, ?> map = (Map<?, ?>) maps.get(dbName);
                assertNotNull(map);
                if ("There are 45 states in the US.".equals(map.get("question"))) {
                    assertFalse((Boolean) map.get("answer"));
                }
                if ("There are 100 senators in the US.".equals(map.get("question"))) {
                    assertTrue((Boolean) map.get("answer"));
                }
            });
    }

    //https://github.com/couchbase/couchbase-lite-android-ce/issues/34
    @Test
    public void testResultToMapWithBoolean2() throws CouchbaseLiteException {
        MutableDocument exam1 = new MutableDocument("exam1");
        exam1.setString("exam type", "final");
        exam1.setString("question", "There are 45 states in the US.");
        exam1.setBoolean("answer", true);

        baseTestDb.save(exam1);

        Query query = QueryBuilder
            .select(
                SelectResult.property("exam type"),
                SelectResult.property("question"),
                SelectResult.property("answer")
            )
            .from(DataSource.database(baseTestDb))
            .where(Meta.id.equalTo(Expression.string("exam1")));

        verifyQuery(query, (n, result) -> assertTrue((Boolean) result.toMap().get("answer")));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1385
    @Test
    public void testQueryDeletedDocument() throws CouchbaseLiteException {
        // STEP 1: Insert two documents
        Document task1 = createTaskDocument("Task 1", false);
        Document task2 = createTaskDocument("Task 2", false);
        assertEquals(2, baseTestDb.getCount());

        // STEP 2: query documents before deletion
        Query query = QueryBuilder.select(SR_DOCID, SR_ALL)
            .from(DataSource.database(this.baseTestDb))
            .where(Expression.property("type").equalTo(Expression.string("task")));

        int rows = verifyQuery(query, (n, result) -> { });
        assertEquals(2, rows);

        // STEP 3: delete task 1
        baseTestDb.delete(task1);
        assertEquals(1, baseTestDb.getCount());
        assertNull(baseTestDb.getDocument(task1.getId()));

        // STEP 4: query documents again after deletion
        rows = verifyQuery(query, (n, result) -> assertEquals(task2.getId(), result.getString(0)));
        assertEquals(1, rows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1389
    @Test
    public void testQueryWhereBooleanExpression() throws CouchbaseLiteException {
        // STEP 1: Insert three documents
        createTaskDocument("Task 1", false);
        createTaskDocument("Task 2", true);
        createTaskDocument("Task 3", true);
        assertEquals(3, baseTestDb.getCount());

        Expression exprType = Expression.property("type");
        Expression exprComplete = Expression.property("complete");
        SelectResult srCount = SelectResult.expression(Function.count(Expression.intValue(1)));

        // regular query - true
        Query query = QueryBuilder.select(SR_ALL)
            .from(DataSource.database(baseTestDb))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(true))));

        int numRows = verifyQuery(
            query,
            false,
            (n, result) -> {
                Dictionary dict = result.getDictionary(baseTestDb.getName());
                assertTrue(dict.getBoolean("complete"));
                assertEquals("task", dict.getString("type"));
                assertTrue(dict.getString("title").startsWith("Task "));
            });
        assertEquals(2, numRows);

        // regular query - false
        query = QueryBuilder.select(SR_ALL)
            .from(DataSource.database(baseTestDb))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(false))));

        numRows = verifyQuery(
            query,
            false,
            (n, result) -> {
                Dictionary dict = result.getDictionary(baseTestDb.getName());
                assertFalse(dict.getBoolean("complete"));
                assertEquals("task", dict.getString("type"));
                assertTrue(dict.getString("title").startsWith("Task "));
            });
        assertEquals(1, numRows);

        // aggregation query - true
        query = QueryBuilder.select(srCount)
            .from(DataSource.database(baseTestDb))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(true))));

        numRows = verifyQuery(query, false, (n, result) -> assertEquals(2, result.getInt(0)));
        assertEquals(1, numRows);

        // aggregation query - false
        query = QueryBuilder.select(srCount)
            .from(DataSource.database(baseTestDb))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(false))));

        numRows = verifyQuery(query, false, (n, result) -> assertEquals(1, result.getInt(0)));
        assertEquals(1, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1413
    @Test
    public void testJoinAll() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final MutableDocument doc1 = new MutableDocument("joinme");
        doc1.setValue("theone", 42);
        saveDocInBaseTestDb(doc1);

        DataSource mainDS = DataSource.database(this.baseTestDb).as("main");
        DataSource secondaryDS = DataSource.database(this.baseTestDb).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult MAIN_ALL = SelectResult.all().from("main");
        SelectResult SECOND_ALL = SelectResult.all().from("secondary");

        Query query = QueryBuilder.select(MAIN_ALL, SECOND_ALL).from(mainDS).join(join);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                Dictionary mainAll1 = result.getDictionary(0);
                Dictionary mainAll2 = result.getDictionary("main");
                Dictionary secondAll1 = result.getDictionary(1);
                Dictionary secondAll2 = result.getDictionary("secondary");
                assertEquals(42, mainAll1.getInt("number1"));
                assertEquals(42, mainAll2.getInt("number1"));
                assertEquals(58, mainAll1.getInt("number2"));
                assertEquals(58, mainAll2.getInt("number2"));
                assertEquals(42, secondAll1.getInt("theone"));
                assertEquals(42, secondAll2.getInt("theone"));
            });
        assertEquals(1, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1413
    @Test
    public void testJoinByDocID() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final MutableDocument doc1 = new MutableDocument("joinme");
        doc1.setValue("theone", 42);
        doc1.setString("numberID", "doc1"); // document ID of number documents.
        saveDocInBaseTestDb(doc1);

        DataSource mainDS = DataSource.database(this.baseTestDb).as("main");
        DataSource secondaryDS = DataSource.database(this.baseTestDb).as("secondary");

        Expression mainPropExpr = Meta.id.from("main");
        Expression secondaryExpr = Expression.property("numberID").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult MAIN_DOC_ID = SelectResult.expression(Meta.id.from("main")).as("mainDocID");
        SelectResult SECONDARY_DOC_ID = SelectResult.expression(Meta.id.from("secondary")).as("secondaryDocID");
        SelectResult SECONDARY_THEONE = SelectResult.expression(Expression.property("theone").from("secondary"));

        Query query = QueryBuilder.select(MAIN_DOC_ID, SECONDARY_DOC_ID, SECONDARY_THEONE).from(mainDS).join(join);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, n);
                String docID = result.getString("mainDocID");
                Document doc = baseTestDb.getDocument(docID);
                assertEquals(1, doc.getInt("number1"));
                assertEquals(99, doc.getInt("number2"));

                // data from secondary
                assertEquals("joinme", result.getString("secondaryDocID"));
                assertEquals(42, result.getInt("theone"));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testGenerateJSONCollation() {
        Collation[] collations = {
            Collation.ascii().setIgnoreCase(false),
            Collation.ascii().setIgnoreCase(true),
            Collation.unicode().setLocale(null).setIgnoreCase(false).setIgnoreAccents(false),
            Collation.unicode().setLocale(null).setIgnoreCase(true).setIgnoreAccents(false),
            Collation.unicode().setLocale(null).setIgnoreCase(true).setIgnoreAccents(true),
            Collation.unicode().setLocale("en").setIgnoreCase(false).setIgnoreAccents(false),
            Collation.unicode().setLocale("en").setIgnoreCase(true).setIgnoreAccents(false),
            Collation.unicode().setLocale("en").setIgnoreCase(true).setIgnoreAccents(true)
        };

        List<Map<String, Object>> expected = new ArrayList<>();
        Map<String, Object> json1 = new HashMap<>();
        json1.put("UNICODE", false);
        json1.put("LOCALE", null);
        json1.put("CASE", true);
        json1.put("DIAC", true);
        expected.add(json1);
        Map<String, Object> json2 = new HashMap<>();
        json2.put("UNICODE", false);
        json2.put("LOCALE", null);
        json2.put("CASE", false);
        json2.put("DIAC", true);
        expected.add(json2);
        Map<String, Object> json3 = new HashMap<>();
        json3.put("UNICODE", true);
        json3.put("LOCALE", null);
        json3.put("CASE", true);
        json3.put("DIAC", true);
        expected.add(json3);
        Map<String, Object> json4 = new HashMap<>();
        json4.put("UNICODE", true);
        json4.put("LOCALE", null);
        json4.put("CASE", false);
        json4.put("DIAC", true);
        expected.add(json4);
        Map<String, Object> json5 = new HashMap<>();
        json5.put("UNICODE", true);
        json5.put("LOCALE", null);
        json5.put("CASE", false);
        json5.put("DIAC", false);
        expected.add(json5);
        Map<String, Object> json6 = new HashMap<>();
        json6.put("UNICODE", true);
        json6.put("LOCALE", "en");
        json6.put("CASE", true);
        json6.put("DIAC", true);
        expected.add(json6);
        Map<String, Object> json7 = new HashMap<>();
        json7.put("UNICODE", true);
        json7.put("LOCALE", "en");
        json7.put("CASE", false);
        json7.put("DIAC", true);
        expected.add(json7);
        Map<String, Object> json8 = new HashMap<>();
        json8.put("UNICODE", true);
        json8.put("LOCALE", "en");
        json8.put("CASE", false);
        json8.put("DIAC", false);
        expected.add(json8);

        for (int i = 0; i < collations.length; i++) { assertEquals(expected.get(i), collations[i].asJSON()); }
    }

    @Test
    public void testAllComparison() throws CouchbaseLiteException {
        String[] values = {"Apple", "Aardvark", "Ångström", "Zebra", "äpple"};
        for (String value: values) {
            MutableDocument doc = new MutableDocument();
            doc.setString("hey", value);
            saveDocInBaseTestDb(doc);
        }
        List<List<Object>> testData = new ArrayList<>();
        testData.add(Arrays.asList("BINARY collation", Collation.ascii(),
            Arrays.asList("Aardvark", "Apple", "Zebra", "Ångström", "äpple")));
        testData.add(Arrays.asList("NOCASE collation", Collation.ascii().setIgnoreCase(true),
            Arrays.asList("Aardvark", "Apple", "Zebra", "Ångström", "äpple")));
        testData.add(Arrays.asList(
            "Unicode case-sensitive, diacritic-sensitive collation",
            Collation.unicode(),
            Arrays.asList("Aardvark", "Ångström", "Apple", "äpple", "Zebra")));
        testData.add(Arrays.asList(
            "Unicode case-INsensitive, diacritic-sensitive collation",
            Collation.unicode().setIgnoreCase(true),
            Arrays.asList("Aardvark", "Ångström", "Apple", "äpple", "Zebra")));
        testData.add(Arrays.asList(
            "Unicode case-sensitive, diacritic-INsensitive collation",
            Collation.unicode().setIgnoreAccents(true),
            Arrays.asList("Aardvark", "Ångström", "äpple", "Apple", "Zebra")));
        testData.add(Arrays.asList(
            "Unicode case-INsensitive, diacritic-INsensitive collation",
            Collation.unicode().setIgnoreAccents(true).setIgnoreCase(true),
            Arrays.asList("Aardvark", "Ångström", "Apple", "äpple", "Zebra")));

        Expression property = Expression.property("hey");
        for (List<Object> data: testData) {
            Query query = QueryBuilder.select(SelectResult.property("hey"))
                .from(DataSource.database(baseTestDb))
                .orderBy(Ordering.expression(property.collate((Collation) data.get(1))));

            final List<String> list = new ArrayList<>();
            verifyQuery(query, false, (n, result) -> list.add(result.getString(0)));
            assertEquals(data.get(2), list);
        }
    }

    @Test
    public void testDeleteDatabaseWithActiveLiveQuery() throws InterruptedException, CouchbaseLiteException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch1.countDown());
        try {
            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
            baseTestDb.delete();
        }
        finally { query.removeChangeListener(token); }
    }

    @Test
    public void testCloseDatabaseWithActiveLiveQuery() throws InterruptedException, CouchbaseLiteException {
        final CountDownLatch latch = new CountDownLatch(1);
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch.countDown());
        try {
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
            baseTestDb.close();
        }
        finally { query.removeChangeListener(token); }
    }

    @Test
    public void testFunctionCount() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final MutableDocument doc = new MutableDocument();
        doc.setValue("string", "STRING");
        doc.setValue("date", null);
        saveDocInBaseTestDb(doc);

        DataSource ds = DataSource.database(this.baseTestDb);
        Expression cntNum1 = Function.count(EXPR_NUMBER1);
        Expression cntInt1 = Function.count(Expression.intValue(1));
        Expression cntAstr = Function.count(Expression.string("*"));
        Expression cntAll = Function.count(Expression.all());
        Expression cntStr = Function.count(Expression.property("string"));
        Expression cntDate = Function.count(Expression.property("date"));
        Expression cntNotExist = Function.count(Expression.property("notExist"));

        SelectResult rsCntNum1 = SelectResult.expression(cntNum1);
        SelectResult rsCntInt1 = SelectResult.expression(cntInt1);
        SelectResult rsCntAstr = SelectResult.expression(cntAstr);
        SelectResult rsCntAll = SelectResult.expression(cntAll);
        SelectResult rsCntStr = SelectResult.expression(cntStr);
        SelectResult rsCntDate = SelectResult.expression(cntDate);
        SelectResult rsCntNotExist = SelectResult.expression(cntNotExist);

        Query query = QueryBuilder
            .select(rsCntNum1, rsCntInt1, rsCntAstr, rsCntAll, rsCntStr, rsCntDate, rsCntNotExist)
            .from(ds);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(100L, (long) result.getValue(0));
                assertEquals(101L, (long) result.getValue(1));
                assertEquals(101L, (long) result.getValue(2));
                assertEquals(101L, (long) result.getValue(3));
                assertEquals(1L, (long) result.getValue(4));
                assertEquals(1L, (long) result.getValue(5));
                assertEquals(0L, (long) result.getValue(6));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testFunctionCountAll() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final DataSource.As ds = DataSource.database(baseTestDb);
        final String dbName = baseTestDb.getName();

        Expression countAll = Function.count(Expression.all());
        Expression countAllFrom = Function.count(Expression.all().from(dbName));
        SelectResult srCountAll = SelectResult.expression(countAll);
        SelectResult srCountAllFrom = SelectResult.expression(countAllFrom);

        // SELECT count(*)
        Query query = QueryBuilder.select(srCountAll).from(ds);
        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                assertEquals(100L, (long) result.getValue(0));
            });
        assertEquals(1, numRows);

        // SELECT count(testdb.*)
        query = QueryBuilder.select(srCountAllFrom).from(ds.as(dbName));
        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                assertEquals(100L, (long) result.getValue(0));
            });
        assertEquals(1, numRows);
    }

    @Test
    public void testResultSetEnumeration() throws CouchbaseLiteException {
        loadNumberedDocs(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.property("number1"));

        // Type 1: Enumeration by ResultSet.next()
        int i = 0;
        Result result;
        try (ResultSet rs = query.execute()) {
            while ((result = rs.next()) != null) {
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), result.getString(0));
                i++;
            }
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 2: Enumeration by ResultSet.iterator()
        i = 0;
        try (ResultSet rs = query.execute()) {
            for (Result r: rs) {
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
                i++;
            }
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 3: Enumeration by ResultSet.allResults().get(int index)
        i = 0;
        try (ResultSet rs = query.execute()) {
            List<Result> list = rs.allResults();
            for (Result r: list) {
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
                i++;
            }
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 4: Enumeration by ResultSet.allResults().iterator()
        i = 0;
        try (ResultSet rs = query.execute()) {
            for (Result r: rs.allResults()) {
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
                i++;
            }
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }
    }

    @Test
    public void testGetAllResults() throws CouchbaseLiteException {
        loadNumberedDocs(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.property("number1"));

        List<Result> results;

        // Get all results by get(int)
        int i = 0;
        try (ResultSet rs = query.execute()) {
            results = rs.allResults();
            for (int j = 0; j < results.size(); j++) {
                Result r = results.get(j);
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
                i++;
            }
            assertEquals(5, results.size());
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Get all results by iterator
        i = 0;
        try (ResultSet rs = query.execute()) {
            results = rs.allResults();
            for (Result r: results) {
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
                i++;
            }
            assertEquals(5, results.size());
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Partial enumerating then get all results:
        try (ResultSet rs = query.execute()) {
            assertNotNull(rs.next());
            assertNotNull(rs.next());
            results = rs.allResults();
            i = 2;
            for (Result r: results) {
                assertEquals(String.format(Locale.ENGLISH, "doc%d", i + 1), r.getString(0));
                i++;
            }
            assertEquals(3, results.size());
            assertEquals(5, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }
    }

    @Test
    public void testResultSetEnumerationZeroResults() throws CouchbaseLiteException {
        loadNumberedDocs(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("number1").is(Expression.intValue(100)))
            .orderBy(Ordering.property("number1"));

        // Type 1: Enumeration by ResultSet.next()
        int i = 0;
        try (ResultSet rs = query.execute()) {
            while (rs.next() != null) { i++; }
            assertEquals(0, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 2: Enumeration by ResultSet.iterator()
        i = 0;
        try (ResultSet rs = query.execute()) {
            for (Result r: rs) { i++; }
            assertEquals(0, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 3: Enumeration by ResultSet.allResults().get(int index)
        i = 0;
        try (ResultSet rs = query.execute()) {
            List<Result> list = rs.allResults();
            for (int j = 0; j < list.size(); j++) {
                list.get(j);
                i++;
            }
            assertEquals(0, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 4: Enumeration by ResultSet.allResults().iterator()
        i = 0;
        try (ResultSet rs = query.execute()) {
            for (Result r: rs.allResults()) { i++; }
            assertEquals(0, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }
    }

    @Test
    public void testMissingValue() throws CouchbaseLiteException {
        MutableDocument doc1 = new MutableDocument("doc1");
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        saveDocInBaseTestDb(doc1);

        Query query = QueryBuilder.select(
                SelectResult.property("name"),
                SelectResult.property("address"),
                SelectResult.property("age"))
            .from(DataSource.database(baseTestDb));

        // Array:
        verifyQuery(
            query,
            (n, result) -> {
                assertEquals(3, result.count());
                assertEquals("Scott", result.getString(0));
                assertNull(result.getValue(1));
                assertNull(result.getValue(2));
                assertEquals(Arrays.asList("Scott", null, null), result.toList());
            });

        // Dictionary:
        verifyQuery(
            query,
            (n, result) -> {
                assertEquals("Scott", result.getString("name"));
                assertNull(result.getString("address"));
                assertTrue(result.contains("address"));
                assertNull(result.getString("age"));
                assertFalse(result.contains("age"));
                Map<String, Object> expected = new HashMap<>();
                expected.put("name", "Scott");
                expected.put("address", null);
                assertEquals(expected, result.toMap());
            });
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1603
    @Test
    public void testExpressionNot() throws CouchbaseLiteException {
        loadNumberedDocs(10);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("number1"))
            .from(DataSource.database(baseTestDb))
            .where(Expression.not(Expression.property("number1")
                .between(Expression.intValue(3), Expression.intValue(5))))
            .orderBy(Ordering.expression(Expression.property("number1")).ascending());

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                if (n < 3) { assertEquals(n, result.getInt("number1")); }
                else { assertEquals(n + 3, result.getInt("number1")); }
            });
        assertEquals(7, numRows);
    }

    @Test
    public void testLimitValueIsLargerThanResult() throws CouchbaseLiteException {
        final int N = 4;
        loadNumberedDocs(N);

        Query query = QueryBuilder
            .select(SelectResult.all())
            .from(DataSource.database(baseTestDb))
            .limit(Expression.intValue(10));

        int numRows = verifyQuery(query, (n, result) -> { });
        assertEquals(N, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1614
    @Test
    public void testFTSStemming() throws CouchbaseLiteException {
        MutableDocument mDoc0 = new MutableDocument("doc0");
        mDoc0.setString("content", "hello");
        saveDocInBaseTestDb(mDoc0);

        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("content", "beauty");
        saveDocInBaseTestDb(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("content", "beautifully");
        saveDocInBaseTestDb(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("content", "beautiful");
        saveDocInBaseTestDb(mDoc3);

        MutableDocument mDoc4 = new MutableDocument("doc4");
        mDoc4.setString("content", "pretty");
        saveDocInBaseTestDb(mDoc4);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        ftsIndex.setLanguage(Locale.ENGLISH.getLanguage());
        baseTestDb.createIndex("ftsIndex", ftsIndex);

        String[] expectedIDs = {"doc1", "doc2", "doc3"};
        String[] expectedContents = {"beauty", "beautifully", "beautiful"};

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("beautiful"))
            .orderBy(Ordering.expression(Meta.id));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(expectedIDs[n - 1], result.getString("id"));
                assertEquals(expectedContents[n - 1], result.getString("content"));
            });
        assertEquals(3, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-net/blob/master/src/Couchbase.Lite.Tests.Shared/QueryTest.cs#L1721
    @Test
    public void testFTSStemming2() throws CouchbaseLiteException {
        baseTestDb.createIndex(
            "passageIndex",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("passage")).setLanguage("en"));
        baseTestDb.createIndex(
            "passageIndexStemless",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("passage")).setLanguage(null));

        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("passage", "The boy said to the child, 'Mommy, I want a cat.'");
        saveDocInBaseTestDb(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("passage", "The mother replied 'No, you already have too many cats.'");
        saveDocInBaseTestDb(mDoc2);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("passageIndex").match("cat"));

        int numRows = verifyQuery(
            query,
            (n, result) -> assertEquals("doc" + n, result.getString(0)));
        assertEquals(2, numRows);

        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("passageIndexStemless").match("cat"));

        numRows = verifyQuery(
            query,
            (n, result) -> assertEquals("doc" + n, result.getString(0)));
        assertEquals(1, numRows);
    }

    // 3.1. Set Operations Using The Enhanced Query Syntax
    // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax
    // https://github.com/couchbase/couchbase-lite-android/issues/1620
    @Test
    public void testFTSSetOperations() throws CouchbaseLiteException {
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("content", "a database is a software system");
        saveDocInBaseTestDb(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("content", "sqlite is a software system");
        saveDocInBaseTestDb(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("content", "sqlite is a database");
        saveDocInBaseTestDb(mDoc3);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        baseTestDb.createIndex("ftsIndex", ftsIndex);

        // The enhanced query syntax
        // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax

        // AND binary set operator
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("sqlite AND database"))
            .orderBy(Ordering.expression(Meta.id));

        final String[] expectedIDs = {"doc3"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs[n - 1], result.getString("id")));
        assertEquals(expectedIDs.length, numRows);

        // implicit AND operator
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("sqlite database"))
            .orderBy(Ordering.expression(Meta.id));

        final String[] expectedIDs2 = {"doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs2[n - 1], result.getString("id")));
        assertEquals(expectedIDs2.length, numRows);

        // OR operator
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("sqlite OR database"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs3 = {"doc1", "doc2", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs3[n - 1], result.getString("id")));
        assertEquals(expectedIDs3.length, numRows);

        // NOT operator
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("database NOT sqlite"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs4 = {"doc1"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs4[n - 1], result.getString("id")));
        assertEquals(expectedIDs4.length, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1621
    @Test
    public void testFTSMixedOperators() throws CouchbaseLiteException {
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("content", "a database is a software system");
        saveDocInBaseTestDb(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("content", "sqlite is a software system");
        saveDocInBaseTestDb(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("content", "sqlite is a database");
        saveDocInBaseTestDb(mDoc3);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        baseTestDb.createIndex("ftsIndex", ftsIndex);

        // The enhanced query syntax
        // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax

        // A AND B AND C
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("sqlite AND software AND system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs = {"doc2"};
        int numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs[n - 1], result.getString("id")));
        assertEquals(expectedIDs.length, numRows);

        // (A AND B) OR C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite AND software) OR database"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs2 = {"doc1", "doc2", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs2[n - 1], result.getString("id")));
        assertEquals(expectedIDs2.length, numRows);

        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite AND software) OR system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs3 = {"doc1", "doc2"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs3[n - 1], result.getString("id")));
        assertEquals(expectedIDs3.length, numRows);

        // (A OR B) AND C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite OR software) AND database"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs4 = {"doc1", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs4[n - 1], result.getString("id")));
        assertEquals(expectedIDs4.length, numRows);

        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("(sqlite OR software) AND system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs5 = {"doc1", "doc2"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs5[n - 1], result.getString("id")));
        assertEquals(expectedIDs5.length, numRows);

        // A OR B OR C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.database(baseTestDb))
            .where(FullTextExpression.index("ftsIndex").match("database OR software OR system"))
            .orderBy(Ordering.expression(Meta.id));

        String[] expectedIDs6 = {"doc1", "doc2", "doc3"};
        numRows = verifyQuery(query, (n, result) -> assertEquals(expectedIDs6[n - 1], result.getString("id")));
        assertEquals(expectedIDs6.length, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1628
    @Test
    public void testLiveQueryResultsCount() throws CouchbaseLiteException, InterruptedException {
        loadNumberedDocs(50);

        Query query = QueryBuilder
            .select()
            .from(DataSource.database(baseTestDb))
            .where(EXPR_NUMBER1.greaterThan(Expression.intValue(25)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        QueryChangeListener listener = change -> {
            int count = 0;
            ResultSet rs = change.getResults();
            while (rs.next() != null) { count++; }
            if (count == 75) { latch.countDown(); } // 26-100
        };
        ListenerToken token = query.addChangeListener(testSerialExecutor, listener);

        try {
            // create one doc
            final CountDownLatch latchAdd = new CountDownLatch(1);
            executeAsync(500, () -> {
                try { loadNumberedDocs(51, 100); }
                catch (Exception e) { e.printStackTrace(); }
                latchAdd.countDown();
            });

            assertTrue(latchAdd.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            assertTrue(latch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://forums.couchbase.com/t/
    //     how-to-be-notifed-that-document-is-changed-but-livequerys-query-isnt-catching-it-anymore/16199/9
    @Test
    public void testLiveQueryNotification() throws CouchbaseLiteException, InterruptedException {
        // save doc1 with number1 -> 5
        MutableDocument doc = new MutableDocument("doc1");
        doc.setInt("number1", 5);
        baseTestDb.save(doc);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("number1"))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("number1").lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property("number1"));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> {
            int matches = 0;
            for (Result r: change.getResults()) { matches++; }

            // match doc1 with number1 -> 5 which is less than 10
            if (matches == 1) { latch1.countDown(); }
            // Not match with doc1 because number1 -> 15 which does not match the query criteria
            else { latch2.countDown(); }
        });

        try {
            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

            doc = baseTestDb.getDocument("doc1").toMutable();
            doc.setInt("number1", 15);
            baseTestDb.save(doc);

            assertTrue(latch2.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1689
    @Test
    public void testQueryAndNLikeOperators() throws CouchbaseLiteException {
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setString("name", "food");
        mDoc1.setString("description", "bar");
        saveDocInBaseTestDb(mDoc1);

        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("name", "foo");
        mDoc2.setString("description", "unknown");
        saveDocInBaseTestDb(mDoc2);

        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setString("name", "water");
        mDoc3.setString("description", "drink");
        saveDocInBaseTestDb(mDoc3);

        MutableDocument mDoc4 = new MutableDocument("doc4");
        mDoc4.setString("name", "chocolate");
        mDoc4.setString("description", "bar");
        saveDocInBaseTestDb(mDoc4);

        // LIKE operator only
        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("name").like(Expression.string("%foo%")))
            .orderBy(Ordering.expression(Meta.id));

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                if (n == 1) { assertEquals("doc1", result.getString(0)); }
                else { assertEquals("doc2", result.getString(0)); }
            });
        assertEquals(2, numRows);

        // EQUAL operator only
        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("description").equalTo(Expression.string("bar")))
            .orderBy(Ordering.expression(Meta.id));

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                if (n == 1) { assertEquals("doc1", result.getString(0)); }
                else { assertEquals("doc4", result.getString(0)); }
            });
        assertEquals(2, numRows);

        // AND and LIKE operators
        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("name").like(Expression.string("%foo%"))
                .and(Expression.property("description").equalTo(Expression.string("bar"))))
            .orderBy(Ordering.expression(Meta.id));

        numRows = verifyQuery(
            query,
            (n, result) -> {
                assertEquals(1, result.count());
                assertEquals("doc1", result.getString(0));
            });
        assertEquals(1, numRows);
    }

    // https://forums.couchbase.com/t/
    //     how-to-implement-an-index-join-clause-in-couchbase-lite-2-0-using-objective-c-api/16246
    // https://github.com/couchbase/couchbase-lite-core/issues/497
    @Test
    public void testQueryJoinAndSelectAll() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        final MutableDocument joinme = new MutableDocument("joinme");
        joinme.setValue("theone", 42);
        saveDocInBaseTestDb(joinme);

        DataSource mainDS = DataSource.database(this.baseTestDb).as("main");
        DataSource secondaryDS = DataSource.database(this.baseTestDb).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.leftJoin(secondaryDS).on(joinExpr);

        SelectResult sr1 = SelectResult.all().from("main");
        SelectResult sr2 = SelectResult.all().from("secondary");

        Query query = QueryBuilder.select(sr1, sr2).from(mainDS).join(join);

        int numRows = verifyQuery(
            query,
            (n, result) -> {
                if (n == 41) {
                    assertEquals(59, result.getDictionary("main").getInt("number2"));
                    assertNull(result.getDictionary("secondary"));
                }
                if (n == 42) {
                    assertEquals(58, result.getDictionary("main").getInt("number2"));
                    assertEquals(42, result.getDictionary("secondary").getInt("theone"));
                }
            });
        assertEquals(101, numRows);
    }

    @Test
    public void testResultSetAllResults() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        baseTestDb.save(doc1a);

        Query query = QueryBuilder.select(SR_DOCID, SR_DELETED)
            .from(DataSource.database(baseTestDb))
            .where(Meta.id.equalTo(Expression.string("doc1")));

        try (ResultSet rs = query.execute()) {
            assertEquals(1, rs.allResults().size());
            assertEquals(0, rs.allResults().size());
        }
    }

    @Test
    public void testAggregateFunctionEmptyArgs() {
        Function.count(null);

        assertThrows(IllegalArgumentException.class, () -> Function.avg(null));

        assertThrows(IllegalArgumentException.class, () -> Function.min(null));

        assertThrows(IllegalArgumentException.class, () -> Function.max(null));

        assertThrows(IllegalArgumentException.class, () -> Function.sum(null));
    }

    @Test
    public void testMathFunctionEmptyArgs() {
        assertThrows(IllegalArgumentException.class, () -> Function.abs(null));

        assertThrows(IllegalArgumentException.class, () -> Function.acos(null));

        assertThrows(IllegalArgumentException.class, () -> Function.asin(null));

        assertThrows(IllegalArgumentException.class, () -> Function.atan(null));

        assertThrows(IllegalArgumentException.class, () -> Function.atan2(null, Expression.doubleValue(0.7)));

        assertThrows(IllegalArgumentException.class, () -> Function.atan2(Expression.doubleValue(0.7), null));

        assertThrows(IllegalArgumentException.class, () -> Function.ceil(null));

        assertThrows(IllegalArgumentException.class, () -> Function.cos(null));

        assertThrows(IllegalArgumentException.class, () -> Function.degrees(null));

        assertThrows(IllegalArgumentException.class, () -> Function.exp(null));

        assertThrows(IllegalArgumentException.class, () -> Function.floor(null));

        assertThrows(IllegalArgumentException.class, () -> Function.ln(null));

        assertThrows(IllegalArgumentException.class, () -> Function.log(null));

        assertThrows(IllegalArgumentException.class, () -> Function.power(null, Expression.intValue(2)));

        assertThrows(IllegalArgumentException.class, () -> Function.power(Expression.intValue(2), null));

        assertThrows(IllegalArgumentException.class, () -> Function.radians(null));

        assertThrows(IllegalArgumentException.class, () -> Function.round(null));

        assertThrows(IllegalArgumentException.class, () -> Function.round(null, Expression.intValue(2)));

        assertThrows(IllegalArgumentException.class, () -> Function.round(Expression.doubleValue(0.567), null));

        assertThrows(IllegalArgumentException.class, () -> Function.sign(null));

        assertThrows(IllegalArgumentException.class, () -> Function.sin(null));

        assertThrows(IllegalArgumentException.class, () -> Function.sqrt(null));

        assertThrows(IllegalArgumentException.class, () -> Function.tan(null));

        assertThrows(IllegalArgumentException.class, () -> Function.trunc(null));

        assertThrows(IllegalArgumentException.class, () -> Function.trunc(null, Expression.intValue(1)));

        assertThrows(IllegalArgumentException.class, () -> Function.trunc(Expression.doubleValue(79.15), null));
    }

    @Test
    public void testStringFunctionEmptyArgs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Function.contains(null, Expression.string("someSubString")));

        assertThrows(
            IllegalArgumentException.class,
            () -> Function.contains(Expression.string("somestring"), null));

        assertThrows(IllegalArgumentException.class, () -> Function.length(null));

        assertThrows(IllegalArgumentException.class, () -> Function.lower(null));

        assertThrows(IllegalArgumentException.class, () -> Function.ltrim(null));

        assertThrows(IllegalArgumentException.class, () -> Function.rtrim(null));

        assertThrows(IllegalArgumentException.class, () -> Function.trim(null));

        assertThrows(IllegalArgumentException.class, () -> Function.upper(null));
    }

    @Test
    public void testStringToMillis() throws CouchbaseLiteException {
        createDateDocs();

        ArrayList<Number> expectedJST = new ArrayList<>();
        expectedJST.add(null);
        expectedJST.add(499105260000L);
        expectedJST.add(499105290000L);
        expectedJST.add(499105290500L);
        expectedJST.add(499105290550L);
        expectedJST.add(499105290555L);

        ArrayList<Number> expectedPST = new ArrayList<>();
        expectedPST.add(null);
        expectedPST.add(499166460000L);
        expectedPST.add(499166490000L);
        expectedPST.add(499166490500L);
        expectedPST.add(499166490550L);
        expectedPST.add(499166490555L);

        ArrayList<Number> expectedUTC = new ArrayList<>();
        expectedUTC.add(null);
        expectedUTC.add(499137660000L);
        expectedUTC.add(499137690000L);
        expectedUTC.add(499137690500L);
        expectedUTC.add(499137690550L);
        expectedUTC.add(499137690555L);

        long offset = new GregorianCalendar().getTimeZone().getOffset(499132800000L);
        Report.log("Local offset: %d", offset);
        ArrayList<Number> expectedLocal = new ArrayList<>();
        expectedLocal.add(499132800000L - offset);
        boolean first = true;
        for (Number entry: expectedUTC) {
            if (first) {
                first = false;
                continue;
            }
            expectedLocal.add((long) entry - offset);
        }

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.stringToMillis(Expression.property("local"))),
                SelectResult.expression(Function.stringToMillis(Expression.property("JST"))),
                SelectResult.expression(Function.stringToMillis(Expression.property("JST2"))),
                SelectResult.expression(Function.stringToMillis(Expression.property("PST"))),
                SelectResult.expression(Function.stringToMillis(Expression.property("PST2"))),
                SelectResult.expression(Function.stringToMillis(Expression.property("UTC"))))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.property("local").ascending());

        verifyQuery(
            query,
            (n, result) -> {
                assertEquals(expectedLocal.get(n - 1), result.getNumber(0));
                assertEquals(expectedJST.get(n - 1), result.getNumber(1));
                assertEquals(expectedJST.get(n - 1), result.getNumber(2));
                assertEquals(expectedPST.get(n - 1), result.getNumber(3));
                assertEquals(expectedPST.get(n - 1), result.getNumber(4));
                assertEquals(expectedUTC.get(n - 1), result.getNumber(5));
            });
    }

    @Test
    public void testStringToUTC() throws CouchbaseLiteException, ParseException {
        createDateDocs();

        ArrayList<String> expectedLocal = new ArrayList<>();
        expectedLocal.add(localToUTC("yyyy-MM-dd", "1985-10-26"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm", "1985-10-26 01:21"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss", "1985-10-26 01:21:30"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss.SSS", "1985-10-26 01:21:30.500"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss.SSS", "1985-10-26 01:21:30.550"));
        expectedLocal.add(localToUTC("yyyy-MM-dd HH:mm:ss.SSS", "1985-10-26 01:21:30.555"));

        ArrayList<String> expectedJST = new ArrayList<>();
        expectedJST.add(null);
        expectedJST.add("1985-10-25T16:21:00Z");
        expectedJST.add("1985-10-25T16:21:30Z");
        expectedJST.add("1985-10-25T16:21:30.500Z");
        expectedJST.add("1985-10-25T16:21:30.550Z");
        expectedJST.add("1985-10-25T16:21:30.555Z");

        ArrayList<String> expectedPST = new ArrayList<>();
        expectedPST.add(null);
        expectedPST.add("1985-10-26T09:21:00Z");
        expectedPST.add("1985-10-26T09:21:30Z");
        expectedPST.add("1985-10-26T09:21:30.500Z");
        expectedPST.add("1985-10-26T09:21:30.550Z");
        expectedPST.add("1985-10-26T09:21:30.555Z");

        ArrayList<String> expectedUTC = new ArrayList<>();
        expectedUTC.add(null);
        expectedUTC.add("1985-10-26T01:21:00Z");
        expectedUTC.add("1985-10-26T01:21:30Z");
        expectedUTC.add("1985-10-26T01:21:30.500Z");
        expectedUTC.add("1985-10-26T01:21:30.550Z");
        expectedUTC.add("1985-10-26T01:21:30.555Z");

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.stringToUTC(Expression.property("local"))),
                SelectResult.expression(Function.stringToUTC(Expression.property("JST"))),
                SelectResult.expression(Function.stringToUTC(Expression.property("JST2"))),
                SelectResult.expression(Function.stringToUTC(Expression.property("PST"))),
                SelectResult.expression(Function.stringToUTC(Expression.property("PST2"))),
                SelectResult.expression(Function.stringToUTC(Expression.property("UTC"))))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.property("local").ascending());

        verifyQuery(
            query,
            (n, result) -> {
                assertEquals(expectedLocal.get(n - 1), result.getString(0));
                assertEquals(expectedJST.get(n - 1), result.getString(1));
                assertEquals(expectedJST.get(n - 1), result.getString(2));
                assertEquals(expectedPST.get(n - 1), result.getString(3));
                assertEquals(expectedPST.get(n - 1), result.getString(4));
                assertEquals(expectedUTC.get(n - 1), result.getString(5));
            });
    }

    @Test
    public void testMillisConversion() throws CouchbaseLiteException {
        final Number[] millis = new Number[] {
            499132800000L,
            499137660000L,
            499137690000L,
            499137690500L,
            499137690550L,
            499137690555L};

        final List<String> expectedUTC = Arrays.asList(
            "1985-10-26T00:00:00Z",
            "1985-10-26T01:21:00Z",
            "1985-10-26T01:21:30Z",
            "1985-10-26T01:21:30.500Z",
            "1985-10-26T01:21:30.550Z",
            "1985-10-26T01:21:30.555Z");

        //ArrayList<String> expectedLocal = new ArrayList<>();

        for (Number t: millis) { baseTestDb.save(new MutableDocument().setNumber("timestamp", t)); }

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.millisToString(Expression.property("timestamp"))),
                SelectResult.expression(Function.millisToUTC(Expression.property("timestamp"))))
            .from(DataSource.database(baseTestDb))
            .orderBy(Ordering.property("timestamp").ascending());

        verifyQuery(
            query,
            (n, result) -> {
                final int i = n - 1;
                assertEquals(expectedUTC.get(i), result.getString(1));
            });
    }

    @Test
    public void testQueryDocumentWithDollarSign() throws CouchbaseLiteException {
        baseTestDb.save(new MutableDocument("doc1")
            .setString("$type", "book")
            .setString("$description", "about cats")
            .setString("$price", "$100"));
        baseTestDb.save(new MutableDocument("doc2")
            .setString("$type", "book")
            .setString("$description", "about dogs")
            .setString("$price", "$95"));
        baseTestDb.save(new MutableDocument("doc3")
            .setString("$type", "animal")
            .setString("$description", "puppy")
            .setString("$price", "$195"));

        int cheapBooks = 0;
        int books = 0;

        Where q = QueryBuilder.select(
                SelectResult.expression(Meta.id),
                SelectResult.expression(Expression.property("$type")),
                SelectResult.expression(Expression.property("$price")))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("$type").equalTo(Expression.string("book")));

        try (ResultSet res = q.execute()) {
            for (Result r: res) {
                books++;
                String p = r.getString("$price");
                if (Integer.parseInt(p.substring(1)) < 100) { cheapBooks++; }
            }
            assertEquals(2, books);
            assertEquals(1, cheapBooks);
        }
    }

    // ??? This is a ridiculously expensive test
    @SlowTest
    private void testLiveQueryNoUpdate(final boolean consumeAll) throws
        CouchbaseLiteException, InterruptedException {
        loadNumberedDocs(100);

        Query query = QueryBuilder
            .select()
            .from(DataSource.database(baseTestDb))
            .where(EXPR_NUMBER1.lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(2);
        QueryChangeListener listener = change -> {
            if (consumeAll) {
                ResultSet rs = change.getResults();
                while (rs.next() != null) { }
            }

            latch.countDown();
            // should happen only once!
        };

        ListenerToken token = query.addChangeListener(testSerialExecutor, listener);
        try {
            // create one doc
            executeAsync(500, () -> {
                try { createNumberedDocInBaseTestDb(111, 100); }
                catch (CouchbaseLiteException e) { throw new RuntimeException(e); }
            });

            // Wait 5 seconds
            // The latch should not pop, because the listener should be called only once
            assertFalse(latch.await(5 * 1000, TimeUnit.MILLISECONDS));
            assertEquals(1, latch.getCount());
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    @Test
    public void testN1QLSelect() throws CouchbaseLiteException {
        loadNumberedDocs(100);

        int numRows = verifyQuery(
            baseTestDb.createQuery("SELECT number1, number2 FROM _default"),
            (n, result) -> {
                assertEquals(n, result.getInt("number1"));
                assertEquals(n, result.getInt(0));
                assertEquals(100 - n, result.getInt("number2"));
                assertEquals(100 - n, result.getInt(1));
            });

        assertEquals(100, numRows);
    }

    @Test
    public void testN1QLSelectStarFromDefault() throws CouchbaseLiteException {
        loadNumberedDocs(100);
        final String dbName = baseTestDb.getName();

        int numRows = verifyQuery(
            baseTestDb.createQuery("SELECT * FROM _default"),
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary("_default");
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            });

        assertEquals(100, numRows);
    }

    @Test
    public void testN1QLSelectStarFromDatabase() throws CouchbaseLiteException {
        loadNumberedDocs(100);
        final String dbName = baseTestDb.getName();

        int numRows = verifyQuery(
            baseTestDb.createQuery("SELECT * FROM " + dbName),
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(dbName);
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            });

        assertEquals(100, numRows);
    }

    @Test
    public void testN1QLSelectStarFromUnderscore() throws CouchbaseLiteException {
        loadNumberedDocs(100);
        final String dbName = baseTestDb.getName();
        int numRows = verifyQuery(
            baseTestDb.createQuery("SELECT * FROM _"),
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary("_");
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            });

        assertEquals(100, numRows);
    }

    private void runTestCases(TestCase... cases) throws CouchbaseLiteException {
        for (TestCase testCase: cases) {
            final List<String> docIdList = new ArrayList<>(testCase.docIds);

            int numRows = verifyQuery(
                QueryBuilder.select(SR_DOCID).from(DataSource.database(baseTestDb)).where(testCase.expr),
                (n, result) -> {
                    String docID = result.getString(0);
                    docIdList.remove(docID);
                });
            assertEquals(0, docIdList.size());
            assertEquals(testCase.docIds.size(), numRows);
        }
    }

    private void createAlphaDocs() throws CouchbaseLiteException {
        String[] letters = {"B", "Z", "Å", "A"};
        for (String letter: letters) {
            MutableDocument doc = new MutableDocument();
            doc.setValue("string", letter);
            saveDocInBaseTestDb(doc);
        }
    }

    private void createDateDocs() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        doc.setString("local", "1985-10-26");
        baseTestDb.save(doc);

        ArrayList<String> dateTimeFormats = new ArrayList<>();
        dateTimeFormats.add("1985-10-26 01:21");
        dateTimeFormats.add("1985-10-26 01:21:30");
        dateTimeFormats.add("1985-10-26 01:21:30.5");
        dateTimeFormats.add("1985-10-26 01:21:30.55");
        dateTimeFormats.add("1985-10-26 01:21:30.555");

        for (String format: dateTimeFormats) {
            doc = new MutableDocument();
            doc.setString("local", format);
            doc.setString("JST", format + "+09:00");
            doc.setString("JST2", format + "+0900");
            doc.setString("PST", format + "-08:00");
            doc.setString("PST2", format + "-0800");
            doc.setString("UTC", format + "Z");
            baseTestDb.save(doc);
        }
    }

    private String localToUTC(String format, String dateStr) throws ParseException {
        TimeZone tz = TimeZone.getDefault();
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(tz);
        Date date = df.parse(dateStr);
        df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date).replace(".000", "");
    }

    private String toLocal(long timestamp) {
        TimeZone tz = TimeZone.getDefault();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        df.setTimeZone(tz);
        return df.format(new Date(timestamp)).replace(".000", "");
    }

    private Document createTaskDocument(String title, boolean complete) throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        doc.setString("type", "task");
        doc.setString("title", title);
        doc.setBoolean("complete", complete);
        return saveDocInBaseTestDb(doc);
    }

    private void testOrdered(Ordering ordering, Comparator<String> cmp) throws CouchbaseLiteException {
        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(
            QueryBuilder.select(SR_DOCID).from(DataSource.database(baseTestDb)).orderBy(ordering),
            (n, result) -> {
                String docID = result.getString(0);
                Document doc = baseTestDb.getDocument(docID);
                Map<String, Object> name = doc.getDictionary("name").toMap();
                String firstName = (String) name.get("first");
                firstNames.add(firstName);
            });
        assertEquals(100, numRows);

        List<String> sorted = new ArrayList<>(firstNames);
        Collections.sort(sorted, cmp);
        String[] array1 = firstNames.toArray(new String[0]);
        String[] array2 = firstNames.toArray(new String[sorted.size()]);
        assertArrayEquals(array1, array2);
    }
}
