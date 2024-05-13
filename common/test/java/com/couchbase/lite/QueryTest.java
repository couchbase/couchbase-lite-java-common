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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.MathUtils;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class QueryTest extends BaseQueryTest {
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

        public TestCase(Expression expr, String... documentIDs) {
            this.expr = expr;
            this.docIds = Collections.unmodifiableList(Arrays.asList(documentIDs));
        }

        public TestCase(Expression expr, List<String> docIds, int... pos) {
            this.expr = expr;
            List<String> ids = new ArrayList<>();
            for (int p: pos) { ids.add(docIds.get(p - 1)); }
            this.docIds = Collections.unmodifiableList(ids);
        }
    }


    @Test
    public void testQueryGetColumnNameAfter32Items() throws CouchbaseLiteException {
        final String value = getUniqueName("value");

        MutableDocument document = new MutableDocument();
        document.setString(TEST_DOC_TAG_KEY, value);
        saveDocInTestCollection(document);

        Query queryBuild = QueryBuilder.createQuery(
            "select\n"
                + "  `1`,`2`,`3`,`4`,`5`,`6`,`7`,`8`,`9`,`10`,`11`,`12`, `13`,`14`,`15`,`16`,`17`,`18`,`19`,`20`,\n"
                + "  `21`,`22`,`23`,`24`,`25`,`26`,`27`,`28`,`29`,`30`,`31`,`32`,`key`\n"
                + "  from _ \n"
                + " limit 1",
            getTestDatabase());

        String[] res = new String[33];
        res[32] = value;
        List<String> arrayResult = Arrays.asList(res);

        Map<String, String> mapResult = new HashMap<>();
        mapResult.put(TEST_DOC_TAG_KEY, "value");

        try (ResultSet rs = queryBuild.execute()) {
            Result result;
            while ((result = rs.next()) != null) {
                assertEquals("{\"key\":\"value\"}", result.toJSON());
                assertEquals(arrayResult, result.toList());
                assertEquals(mapResult, result.toMap());
                assertEquals("value", result.getValue(TEST_DOC_TAG_KEY).toString());
                assertEquals("value", result.getString(TEST_DOC_TAG_KEY));
                assertEquals("value", result.getString(32));
            }
        }
    }

    @Test
    public void testQueryDocumentExpiration() throws CouchbaseLiteException, InterruptedException {
        long now = System.currentTimeMillis();

        // this one should expire
        MutableDocument doc = new MutableDocument();
        doc.setInt("answer", 42);
        doc.setString("notHere", "string");
        saveDocInTestCollection(doc);
        getTestCollection().setDocumentExpiration(doc.getId(), new Date(now + 500L));

        // this one is deleted
        MutableDocument doc10 = new MutableDocument();
        doc10.setInt("answer", 42);
        doc10.setString("notHere", "string");
        saveDocInTestCollection(doc10);
        getTestCollection().setDocumentExpiration(doc10.getId(), new Date(now + 2000L)); //deleted doc
        getTestCollection().delete(doc10);

        // should be in the result set
        MutableDocument doc1 = new MutableDocument();
        doc1.setInt("answer", 42);
        doc1.setString("a", "string");
        saveDocInTestCollection(doc1);
        getTestCollection().setDocumentExpiration(doc1.getId(), new Date(now + 2000L));

        // should be in the result set
        MutableDocument doc2 = new MutableDocument();
        doc2.setInt("answer", 42);
        doc2.setString("b", "string");
        saveDocInTestCollection(doc2);
        getTestCollection().setDocumentExpiration(doc2.getId(), new Date(now + 3000L));

        // should be in the result set
        MutableDocument doc3 = new MutableDocument();
        doc3.setInt("answer", 42);
        doc3.setString("c", "string");
        saveDocInTestCollection(doc3);
        getTestCollection().setDocumentExpiration(doc3.getId(), new Date(now + 4000L));

        Thread.sleep(1000);

        // This should get all but the one that has expired
        // and the one that was deleted
        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.expiration))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.expiration.lessThan(Expression.longValue(now + 6000L)));

        assertEquals(3, verifyQueryWithEnumerator(query, (r, n) -> { }));
    }

    @Test
    public void testQueryDocumentIsNotDeleted() {
        MutableDocument doc1a = new MutableDocument();
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        saveDocInTestCollection(doc1a);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.deleted))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.equalTo(Expression.string(doc1a.getId()))
                .and(Meta.deleted.equalTo(Expression.booleanValue(false))));

        assertEquals(
            1,
            verifyQueryWithEnumerator(
                query,
                (n, result) -> {
                    assertEquals(result.getString(0), doc1a.getId());
                    assertFalse(result.getBoolean(1));
                }));
    }

    @Test
    public void testQueryDocumentIsDeleted() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        doc.setInt("answer", 42);
        doc.setString("a", "string");
        saveDocInTestCollection(doc);

        getTestCollection().delete(getTestCollection().getDocument(doc.getId()));

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.deleted))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.deleted.equalTo(Expression.booleanValue(true))
                .and(Meta.id.equalTo(Expression.string(doc.getId()))));

        assertEquals(1, verifyQueryWithEnumerator(query, (n, result) -> { }));
    }

    @Test
    public void testNoWhereQuery() {
        loadJSONResourceIntoCollection("names_100.json");

        verifyQuery(
            QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.sequence))
                .from(DataSource.collection(getTestCollection())),
            100,
            (n, result) -> {
                String docID = result.getString(0);
                String expectedID = jsonDocId(n);
                int sequence = result.getInt(1);

                assertEquals(expectedID, docID);

                assertEquals(n, sequence);

                Document doc = getTestCollection().getDocument(docID);
                assertEquals(expectedID, doc.getId());
                assertEquals(n, doc.getSequence());
            });
    }

    // Throws clause prevents Windows compiler error
    @SuppressWarnings("RedundantThrows")
    @Test
    public void testWhereComparison() throws Exception {
        List<String> docIds = Fn.mapToList(loadDocuments(10), Document::getId);
        runTests(
            new TestCase(Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(3)), docIds, 1, 2),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).greaterThanOrEqualTo(Expression.intValue(3)),
                docIds,
                3, 4, 5, 6, 7, 8, 9, 10),
            new TestCase(Expression.property(TEST_DOC_SORT_KEY).lessThanOrEqualTo(Expression.intValue(3)),
                docIds,
                1, 2, 3),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).greaterThan(Expression.intValue(3)),
                docIds,
                4, 5, 6, 7, 8, 9, 10),
            new TestCase(Expression.property(TEST_DOC_SORT_KEY).greaterThan(Expression.intValue(6)),
                docIds,
                7, 8, 9, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).lessThanOrEqualTo(Expression.intValue(6)),
                docIds,
                1, 2, 3, 4, 5, 6),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).greaterThanOrEqualTo(Expression.intValue(6)),
                docIds,
                6, 7, 8, 9, 10),
            new TestCase(Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(6)),
                docIds,
                1, 2, 3, 4, 5),
            new TestCase(Expression.property(TEST_DOC_SORT_KEY).equalTo(Expression.intValue(7)), docIds, 7),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).notEqualTo(Expression.intValue(7)),
                docIds,
                1, 2, 3, 4, 5, 6, 8, 9, 10)
        );
    }

    // Throws clause prevents Windows compiler error
    @SuppressWarnings("RedundantThrows")
    @Test
    public void testWhereArithmetic() throws Exception {
        List<String> docIds = Fn.mapToList(loadDocuments(10), Document::getId);
        runTests(
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).multiply(Expression.intValue(2))
                    .greaterThan(Expression.intValue(3)),
                docIds,
                2, 3, 4, 5, 6, 7, 8, 9, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).divide(Expression.intValue(2))
                    .greaterThan(Expression.intValue(3)),
                docIds,
                8, 9, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).modulo(Expression.intValue(2)).equalTo(Expression.intValue(0)),
                docIds,
                2, 4, 6, 8, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).add(Expression.intValue(5)).greaterThan(Expression.intValue(10)),
                docIds,
                6, 7, 8, 9, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).subtract(Expression.intValue(5))
                    .greaterThan(Expression.intValue(0)),
                docIds,
                6, 7, 8, 9, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).multiply(Expression.property(TEST_DOC_REV_SORT_KEY))
                    .greaterThan(Expression.intValue(10)),
                docIds,
                2, 3, 4, 5, 6, 7, 8),
            new TestCase(
                Expression.property(TEST_DOC_REV_SORT_KEY).divide(Expression.property(TEST_DOC_SORT_KEY))
                    .greaterThan(Expression.intValue(3)),
                docIds,
                1, 2),
            new TestCase(
                Expression.property(TEST_DOC_REV_SORT_KEY).modulo(Expression.property(TEST_DOC_SORT_KEY))
                    .equalTo(Expression.intValue(0)),
                docIds,
                1, 2, 5, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).add(Expression.property(TEST_DOC_REV_SORT_KEY))
                    .equalTo(Expression.intValue(10)),
                docIds,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).subtract(Expression.property(TEST_DOC_REV_SORT_KEY))
                    .greaterThan(Expression.intValue(0)),
                docIds,
                6, 7, 8, 9, 10)
        );
    }

    // Throws clause prevents Windows compiler error
    @SuppressWarnings("RedundantThrows")
    @Test
    public void testWhereAndOr() throws Exception {
        List<String> docIds = Fn.mapToList(loadDocuments(10), Document::getId);
        runTests(
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).greaterThan(Expression.intValue(3))
                    .and(Expression.property(TEST_DOC_REV_SORT_KEY).greaterThan(Expression.intValue(3))),
                docIds,
                4, 5, 6),
            new TestCase(
                Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(3))
                    .or(Expression.property(TEST_DOC_REV_SORT_KEY).lessThan(Expression.intValue(3))),
                docIds,
                1, 2, 8, 9, 10)
        );
    }

    @Test
    public void testWhereValued() {
        MutableDocument doc1 = new MutableDocument();
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        saveDocInTestCollection(doc1);

        MutableDocument doc2 = new MutableDocument();
        doc2.setValue("name", "Tiger");
        doc2.setValue("address", "123 1st ave.");
        doc2.setValue("age", 20);
        saveDocInTestCollection(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        for (TestCase testCase: new TestCase[] {
            new TestCase(name.isNotValued()),
            new TestCase(name.isValued(), doc1.getId(), doc2.getId()),
            new TestCase(address.isNotValued(), doc1.getId()),
            new TestCase(address.isValued(), doc2.getId()),
            new TestCase(age.isNotValued(), doc1.getId()),
            new TestCase(age.isValued(), doc2.getId()),
            new TestCase(work.isNotValued(), doc1.getId(), doc2.getId()),
            new TestCase(work.isValued())}) {
            int nIds = testCase.docIds.size();
            verifyQuery(
                QueryBuilder.select(SelectResult.expression(Meta.id))
                    .from(DataSource.collection(getTestCollection()))
                    .where(testCase.expr),
                nIds,
                (n, result) -> { if (n <= nIds) { assertEquals(testCase.docIds.get(n - 1), result.getString(0)); } });
        }
    }

    @Test
    public void testWhereIs() {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("string", "string");
        saveDocInTestCollection(doc1);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("string").is(Expression.string("string")));


        verifyQuery(
            query,
            1,
            (n, result) -> {
                String docID = result.getString(0);
                assertEquals(doc1.getId(), docID);
                Document doc = getTestCollection().getDocument(docID);
                assertEquals(doc1.getValue("string"), doc.getValue("string"));
            });
    }

    @Test
    public void testWhereIsNot() {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("string", "string");
        saveDocInTestCollection(doc1);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("string").isNot(Expression.string("string1")));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                String docID = result.getString(0);
                assertEquals(doc1.getId(), docID);
                Document doc = getTestCollection().getDocument(docID);
                assertEquals(doc1.getValue("string"), doc.getValue("string"));
            });
    }

    // Throws clause prevents Windows compiler error
    @SuppressWarnings("RedundantThrows")
    @Test
    public void testWhereBetween() throws Exception {
        List<String> docIds = Fn.mapToList(loadDocuments(10), Document::getId);
        runTests(new TestCase(Expression.property(TEST_DOC_SORT_KEY)
            .between(Expression.intValue(3), Expression.intValue(7)), docIds, 3, 4, 5, 6, 7));
    }

    @Test
    public void testWhereIn() {
        loadJSONResourceIntoCollection("names_100.json");

        final Expression[] expected = {
            Expression.string("Marcy"),
            Expression.string("Margaretta"),
            Expression.string("Margrett"),
            Expression.string("Marlen"),
            Expression.string("Maryjo")};

        Query query = QueryBuilder.select(SelectResult.property("name.first"))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("name.first").in(expected))
            .orderBy(Ordering.property("name.first"));

        verifyQuery(query, 5, (n, result) -> assertEquals(expected[n - 1].asJSON(), result.getString(0)));
    }

    @Test
    public void testWhereLike() {
        loadJSONResourceIntoCollection("names_100.json");

        Expression w = Expression.property("name.first").like(Expression.string("%Mar%"));
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(w)
            .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        assertEquals(
            5,
            verifyQueryWithEnumerator(
                query,
                (n, result) -> {
                    String docID = result.getString(0);
                    Document doc = getTestCollection().getDocument(docID);
                    Map<String, Object> name = doc.getDictionary("name").toMap();
                    String firstName = (String) name.get("first");
                    if (firstName != null) { firstNames.add(firstName); }
                }));
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereRegex() {
        loadJSONResourceIntoCollection("names_100.json");

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("name.first").regex(Expression.string("^Mar.*")))
            .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        assertEquals(
            5,
            verifyQueryWithEnumerator(
                query,
                (n, result) -> {
                    String docID = result.getString(0);
                    Document doc = getTestCollection().getDocument(docID);
                    Map<String, Object> name = doc.getDictionary("name").toMap();
                    String firstName = (String) name.get("first");
                    if (firstName != null) { firstNames.add(firstName); }
                }));
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testRank() {
        Expression expr = FullTextFunction.rank(Expression.fullTextIndex("abc"));
        assertNotNull(expr);
        Object obj = expr.asJSON();
        assertNotNull(obj);
        assertTrue(obj instanceof List);
        assertEquals(Arrays.asList("RANK()", "abc"), obj);
    }

    @Test
    public void testWhereIndexMatch() throws CouchbaseLiteException {
        loadJSONResourceIntoCollection("sentences.json");

        getTestCollection().createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));
        IndexExpression idx = Expression.fullTextIndex("sentence");

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("sentence"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank(idx)).descending());

        verifyQuery(
            query,
            2,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
    }

    @Test
    public void testWhereMatch() throws CouchbaseLiteException {
        loadJSONResourceIntoCollection("sentences.json");

        getTestCollection().createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));
        IndexExpression idx = Expression.fullTextIndex("sentence");

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("sentence"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank(idx)).descending());

        verifyQuery(
            query,
            2,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
    }

    @Test
    public void testFullTextIndexConfigDefaults() {
        final FullTextIndexConfiguration idxConfig = new FullTextIndexConfiguration("sentence", "nonsense");
        assertEquals(Defaults.FullTextIndex.IGNORE_ACCENTS, idxConfig.isIgnoringAccents());
        assertEquals(Locale.getDefault().getLanguage(), idxConfig.getLanguage());

        idxConfig.setLanguage(null);
        assertNull(idxConfig.getLanguage());
    }

    @Test
    public void testFullTextIndexConfig() throws CouchbaseLiteException {
        loadJSONResourceIntoCollection("sentences.json");

        final FullTextIndexConfiguration idxConfig = new FullTextIndexConfiguration("sentence", "nonsense")
            .setLanguage("en-ca")
            .ignoreAccents(true);
        assertEquals("en-ca", idxConfig.getLanguage());
        assertTrue(idxConfig.isIgnoringAccents());

        getTestCollection().createIndex("sentence", idxConfig);
        IndexExpression idx = Expression.fullTextIndex("sentence");

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("sentence"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank(idx)).descending());

        verifyQuery(
            query,
            2,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
    }

    // Test courtesy of Jayahari Vavachan
    @Test
    public void testN1QLFTSQuery() throws CouchbaseLiteException {
        loadJSONResourceIntoCollection("sentences.json");

        getTestCollection().createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));

        Query query = getTestDatabase().createQuery(
            "SELECT _id FROM " + BaseDbTestKt.getQualifiedName(getTestCollection())
                + " WHERE MATCH(sentence, 'Dummie woman')");

        verifyQuery(query, 2, (n, result) -> assertNotNull(result.getString(0)));
    }

    @Test
    public void testOrderBy() {
        loadJSONResourceIntoCollection("names_100.json");

        Ordering.SortOrder order = Ordering.expression(Expression.property("name.first"));

        // Don't replace this with Comparator.naturalOrder.
        // it doesn't exist on older versions of Android
        testOrdered(order.ascending(), String::compareTo);
        //noinspection ComparatorCombinators
        testOrdered(order.descending(), (c1, c2) -> c2.compareTo(c1));
    }

    // https://github.com/couchbase/couchbase-lite-ios/issues/1669
    // https://github.com/couchbase/couchbase-lite-core/issues/81
    @Test
    public void testSelectDistinct() {
        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("number", 20);
        saveDocInTestCollection(doc1);

        MutableDocument doc2 = new MutableDocument();
        doc2.setValue("number", 20);
        saveDocInTestCollection(doc2);

        verifyQuery(
            QueryBuilder.selectDistinct(SelectResult.property("number"))
                .from(DataSource.collection(getTestCollection())),
            1,
            (n, result) -> assertEquals(20, result.getInt(0)));
    }

    @Test
    public void testJoin() {
        loadDocuments(100);

        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("theone", 42);
        saveDocInTestCollection(doc1);

        Join join = Join.join(DataSource.collection(getTestCollection()).as("secondary"))
            .on(Expression.property(TEST_DOC_SORT_KEY).from("main")
                .equalTo(Expression.property("theone").from("secondary")));

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id.from("main")))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(join);

        verifyQuery(
            query,
            1,
            (n, result) -> {
                String docID = result.getString(0);
                Document doc = getTestCollection().getDocument(docID);
                assertEquals(42, doc.getInt(TEST_DOC_SORT_KEY));
            });
    }

    @Test
    public void testLeftJoin() {
        loadDocuments(100);

        final MutableDocument joinme = new MutableDocument();
        joinme.setValue("theone", 42);
        saveDocInTestCollection(joinme);

        Query query = QueryBuilder.select(
                SelectResult.expression(Expression.property(TEST_DOC_REV_SORT_KEY).from("main")),
                SelectResult.expression(Expression.property("theone").from("secondary")))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(Join.leftJoin(DataSource.collection(getTestCollection()).as("secondary"))
                .on(Expression.property(TEST_DOC_SORT_KEY).from("main")
                    .equalTo(Expression.property("theone").from("secondary"))));

        verifyQuery(
            query,
            101,
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
    }

    @Test
    public void testCrossJoin() {
        loadDocuments(10);

        Query query = QueryBuilder.select(
                SelectResult.expression(Expression.property(TEST_DOC_SORT_KEY).from("main")),
                SelectResult.expression(Expression.property(TEST_DOC_REV_SORT_KEY).from("secondary")))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(Join.crossJoin(DataSource.collection(getTestCollection()).as("secondary")));

        verifyQuery(
            query,
            100,
            (n, result) -> {
                int num1 = result.getInt(0);
                int num2 = result.getInt(1);
                assertEquals((num1 - 1) % 10, (n - 1) / 10);
                assertEquals((10 - num2) % 10, n % 10);
            });
    }

    @Test
    public void testGroupBy() {
        loadJSONResourceIntoCollection("names_100.json");

        final List<String> expectedStates = Arrays.asList("AL", "CA", "CO", "FL", "IA");
        final List<Integer> expectedCounts = Arrays.asList(1, 6, 1, 1, 3);
        final List<String> expectedMaxZips = Arrays.asList("35243", "94153", "81223", "33612", "50801");

        Expression state = Expression.property("contact.address.state");
        Query query = QueryBuilder
            .select(
                SelectResult.property("contact.address.state"),
                SelectResult.expression(Function.count(Expression.intValue(1))),
                SelectResult.expression(Function.max(Expression.property("contact.address.zip"))))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("gender").equalTo(Expression.string("female")))
            .groupBy(state)
            .orderBy(Ordering.expression(state));

        verifyQuery(
            query,
            31,
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

        // With HAVING:
        final List<String> expectedStates2 = Arrays.asList("CA", "IA", "IN");
        final List<Integer> expectedCounts2 = Arrays.asList(6, 3, 2);
        final List<String> expectedMaxZips2 = Arrays.asList("94153", "50801", "47952");

        query = QueryBuilder
            .select(
                SelectResult.property("contact.address.state"),
                SelectResult.expression(Function.count(Expression.intValue(1))),
                SelectResult.expression(Function.max(Expression.property("contact.address.zip"))))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("gender").equalTo(Expression.string("female")))
            .groupBy(state)
            .having(Function.count(Expression.intValue(1)).greaterThan(Expression.intValue(1)))
            .orderBy(Ordering.expression(state));

        verifyQuery(
            query,
            15,
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
    }

    @Test
    public void testParameters() throws CouchbaseLiteException {
        loadDocuments(100);

        Query query = QueryBuilder
            .select(SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY)
                .between(Expression.parameter("num1"), Expression.parameter("num2")))
            .orderBy(Ordering.expression(Expression.property(TEST_DOC_SORT_KEY)));

        Parameters params = new Parameters(query.getParameters())
            .setValue("num1", 2)
            .setValue("num2", 5);
        query.setParameters(params);

        final long[] expectedNumbers = {2, 3, 4, 5};
        verifyQuery(query, 4, (n, result) -> assertEquals(expectedNumbers[n - 1], (long) result.getValue(0)));
    }

    // Throws clause prevents Windows compiler error
    @SuppressWarnings("RedundantThrows")
    @Test
    public void testMeta() throws Exception {
        List<String> expected = Fn.mapToList(loadDocuments(5), Document::getId);

        Query query = QueryBuilder
            .select(
                SelectResult.expression(Meta.id),
                SelectResult.expression(Meta.sequence),
                SelectResult.expression(Meta.revisionID),
                SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Meta.sequence));

        verifyQuery(
            query,
            5,
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
                assertEquals(docID4, expected.get(n - 1));

                assertEquals(n, seq1);
                assertEquals(n, seq2);
                assertEquals(n, seq3);
                assertEquals(n, seq4);

                assertEquals(revId1, revId2);
                assertEquals(revId2, revId3);
                assertEquals(revId3, revId4);
                assertEquals(revId4, getTestCollection().getDocument(docID1).getRevisionID());

                assertEquals(n, number);
            });
    }

    @Test
    public void testRevisionIdInCreate() {
        MutableDocument doc = new MutableDocument();
        saveDocInTestCollection(doc);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.revisionID))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.equalTo(Expression.string(doc.getId())));

        verifyQuery(query, 1, (n, result) -> assertEquals(doc.getRevisionID(), result.getString(0)));
    }

    @Test
    public void testRevisionIdInUpdate() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        saveDocInTestCollection(doc);

        doc = getTestCollection().getDocument(doc.getId()).toMutable();
        doc.setString("DEC", "Maynard");
        saveDocInTestCollection(doc);
        final String revId = doc.getRevisionID();

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.revisionID))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.equalTo(Expression.string(doc.getId())));

        verifyQuery(query, 1, (n, result) -> assertEquals(revId, result.getString(0)));
    }

    @Test
    public void testRevisionIdInWhere() {
        MutableDocument doc = new MutableDocument();
        saveDocInTestCollection(doc);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.revisionID.equalTo(Expression.string(doc.getRevisionID())));

        verifyQuery(query, 1, (n, result) -> assertEquals(doc.getId(), result.getString(0)));
    }

    @Test
    public void testRevisionIdInDelete() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        saveDocInTestCollection(doc);

        final Document dbDoc = getTestCollection().getDocument(doc.getId());
        assertNotNull(dbDoc);

        getTestCollection().delete(dbDoc);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.revisionID))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.deleted.equalTo(Expression.booleanValue(true)));

        verifyQuery(query, 1, (n, result) -> assertEquals(dbDoc.getRevisionID(), result.getString(0)));
    }

    @Test
    public void testLimit() throws CouchbaseLiteException {
        loadDocuments(10);

        Query query = QueryBuilder
            .select(SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property(TEST_DOC_SORT_KEY)))
            .limit(Expression.intValue(5));

        final long[] expectedNumbers = {1, 2, 3, 4, 5};
        verifyQuery(
            query,
            5,
            (n, result) -> {
                long number = (long) result.getValue(0);
                assertEquals(expectedNumbers[n - 1], number);
            });

        Expression paramExpr = Expression.parameter("LIMIT_NUM");
        query = QueryBuilder
            .select(SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property(TEST_DOC_SORT_KEY)))
            .limit(paramExpr);
        Parameters params = new Parameters(query.getParameters()).setValue("LIMIT_NUM", 3);
        query.setParameters(params);

        final long[] expectedNumbers2 = {1, 2, 3};
        verifyQuery(
            query,
            3,
            (n, result) -> {
                long number = (long) result.getValue(0);
                assertEquals(expectedNumbers2[n - 1], number);
            });
    }

    @Test
    public void testLimitOffset() throws CouchbaseLiteException {
        loadDocuments(10);

        Query query = QueryBuilder
            .select(SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property(TEST_DOC_SORT_KEY)))
            .limit(Expression.intValue(5), Expression.intValue(3));

        final long[] expectedNumbers = {4, 5, 6, 7, 8};
        verifyQuery(
            query,
            5,
            (n, result) -> assertEquals(expectedNumbers[n - 1], (long) result.getValue(0)));

        Expression paramLimitExpr = Expression.parameter("LIMIT_NUM");
        Expression paramOffsetExpr = Expression.parameter("OFFSET_NUM");
        query = QueryBuilder
            .select(SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property(TEST_DOC_SORT_KEY)))
            .limit(paramLimitExpr, paramOffsetExpr);
        Parameters params = new Parameters(query.getParameters())
            .setValue("LIMIT_NUM", 3)
            .setValue("OFFSET_NUM", 5);
        query.setParameters(params);

        final long[] expectedNumbers2 = {6, 7, 8};
        verifyQuery(
            query,
            3,
            (n, result) -> assertEquals(expectedNumbers2[n - 1], (long) result.getValue(0)));
    }

    @Test
    public void testQueryResult() {
        loadJSONResourceIntoCollection("names_100.json");
        Query query = QueryBuilder.select(
                SelectResult.property("name.first").as("firstname"),
                SelectResult.property("name.last").as("lastname"),
                SelectResult.property("gender"),
                SelectResult.property("contact.address.city"))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            100,
            (n, result) -> {
                assertEquals(4, result.count());
                assertEquals(result.getValue(0), result.getValue("firstname"));
                assertEquals(result.getValue(1), result.getValue("lastname"));
                assertEquals(result.getValue(2), result.getValue("gender"));
                assertEquals(result.getValue(3), result.getValue("city"));
            });
    }

    @Test
    public void testQueryProjectingKeys() {
        loadDocuments(100);

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.avg(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.count(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.min(Expression.property(TEST_DOC_SORT_KEY))).as("min"),
                SelectResult.expression(Function.max(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.sum(Expression.property(TEST_DOC_SORT_KEY))).as("sum"))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(5, result.count());
                assertEquals(result.getValue(0), result.getValue("$1"));
                assertEquals(result.getValue(1), result.getValue("$2"));
                assertEquals(result.getValue(2), result.getValue("min"));
                assertEquals(result.getValue(3), result.getValue("$3"));
                assertEquals(result.getValue(4), result.getValue("sum"));
            });
    }

    @Test
    public void testQuantifiedOperators() {
        loadJSONResourceIntoCollection("names_100.json");

        Expression exprLikes = Expression.property("likes");
        VariableExpression exprVarLike = ArrayExpression.variable("LIKE");

        // ANY:
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(ArrayExpression
                .any(exprVarLike)
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("climbing"))));

        final AtomicInteger i = new AtomicInteger(0);
        final String[] expected = {"doc-017", "doc-021", "doc-023", "doc-045", "doc-060"};
        assertEquals(
            expected.length,
            verifyQueryWithEnumerator(
                query,
                (n, result) -> assertEquals(expected[i.getAndIncrement()], result.getString(0))));

        // EVERY:
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(ArrayExpression
                .every(ArrayExpression.variable("LIKE"))
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("taxes"))));

        assertEquals(
            42,
            verifyQueryWithEnumerator(
                query,
                (n, result) -> { if (n == 1) { assertEquals("doc-007", result.getString(0)); } }
            ));

        // ANY AND EVERY:
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(ArrayExpression
                .anyAndEvery(ArrayExpression.variable("LIKE"))
                .in(exprLikes)
                .satisfies(exprVarLike.equalTo(Expression.string("taxes"))));

        assertEquals(0, verifyQueryWithEnumerator(query, (n, result) -> { }));
    }

    @Test
    public void testAggregateFunctions() {
        loadDocuments(100);

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.avg(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.count(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.min(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.max(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.sum(Expression.property(TEST_DOC_SORT_KEY))))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(50.5, (Double) result.getValue(0), 0.0F);
                assertEquals(100L, (long) result.getValue(1));
                assertEquals(1L, (long) result.getValue(2));
                assertEquals(100L, (long) result.getValue(3));
                assertEquals(5050L, (long) result.getValue(4));
            });
    }

    @Test
    public void testArrayFunctions() {
        MutableDocument doc = new MutableDocument();
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);
        saveDocInTestCollection(doc);

        Expression exprArray = Expression.property("array");

        Query query = QueryBuilder.select(SelectResult.expression(ArrayFunction.length(exprArray)))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(query, 1, (n, result) -> assertEquals(2, result.getInt(0)));

        query = QueryBuilder.select(
                SelectResult.expression(ArrayFunction.contains(exprArray, Expression.string("650-123-0001"))),
                SelectResult.expression(ArrayFunction.contains(exprArray, Expression.string("650-123-0003"))))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertTrue(result.getBoolean(0));
                assertFalse(result.getBoolean(1));
            });
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
    public void testMathFunctions() {
        final String key = "number";
        final double num = 0.6;
        final Expression propNumber = Expression.property(key);

        MutableDocument doc = new MutableDocument();
        doc.setValue(key, num);
        saveDocInTestCollection(doc);

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
            verifyQuery(
                QueryBuilder.select(SelectResult.expression(f.expr)).from(DataSource.collection(getTestCollection())),
                1,
                (n, result) -> assertEquals(f.name, f.expected, result.getDouble(0), 1E-12));
        }
    }

    @Test
    public void testStringFunctions() {
        final String str = "  See you 18r  ";
        final Expression prop = Expression.property("greeting");

        final MutableDocument doc = new MutableDocument();
        doc.setValue("greeting", str);
        saveDocInTestCollection(doc);

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.contains(prop, Expression.string("8"))),
                SelectResult.expression(Function.contains(prop, Expression.string("9"))))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertTrue(result.getBoolean(0));
                assertFalse(result.getBoolean(1));
            });

        // Length
        query = QueryBuilder.select(SelectResult.expression(Function.length(prop)))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(query, 1, (n, result) -> assertEquals(str.length(), result.getInt(0)));

        // Lower, Ltrim, Rtrim, Trim, Upper:
        query = QueryBuilder.select(
                SelectResult.expression(Function.lower(prop)),
                SelectResult.expression(Function.ltrim(prop)),
                SelectResult.expression(Function.rtrim(prop)),
                SelectResult.expression(Function.trim(prop)),
                SelectResult.expression(Function.upper(prop)))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(str.toLowerCase(Locale.ENGLISH), result.getString(0));
                assertEquals(str.replaceAll("^\\s+", ""), result.getString(1));
                assertEquals(str.replaceAll("\\s+$", ""), result.getString(2));
                assertEquals(str.trim(), result.getString(3));
                assertEquals(str.toUpperCase(Locale.ENGLISH), result.getString(4));
            });
    }

    @Test
    public void testSelectAll() {
        loadDocuments(100);

        final String collectionName = getTestCollection().getName();

        // SELECT *
        verifyQuery(
            QueryBuilder.select(SelectResult.all()).from(DataSource.collection(getTestCollection())),
            100,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(collectionName);
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
            });

        // SELECT *, number1
        Query query = QueryBuilder.select(SelectResult.all(), SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            100,
            (n, result) -> {
                assertEquals(2, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(collectionName);
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, result.getInt(1));
                assertEquals(n, result.getInt(TEST_DOC_SORT_KEY));
            });

        // SELECT testdb.*
        query = QueryBuilder.select(SelectResult.all().from(collectionName))
            .from(DataSource.collection(getTestCollection()).as(collectionName));

        verifyQuery(
            query,
            100,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(collectionName);
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
            });

        // SELECT testdb.*, testdb.number1
        query = QueryBuilder.select(
                SelectResult.all().from(collectionName),
                SelectResult.expression(Expression.property(TEST_DOC_SORT_KEY).from(collectionName)))
            .from(DataSource.collection(getTestCollection()).as(collectionName));

        verifyQuery(
            query,
            100,
            (n, result) -> {
                assertEquals(2, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(collectionName);
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, result.getInt(1));
                assertEquals(n, result.getInt(TEST_DOC_SORT_KEY));
            });
    }

    // With no locale, characters with diacritics should be
    // treated as the original letters A, E, I, O, U,
    @Test
    public void testUnicodeCollationWithLocaleNone() {
        createAlphaDocs();

        Collation noLocale = Collation.unicode()
            .setLocale(null)
            .setIgnoreCase(false)
            .setIgnoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property("string").collate(noLocale)));

        final String[] expected = {"A", "Å", "B", "Z"};
        verifyQuery(query, expected.length, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
    }

    // In the Spanish alphabet, the six characters with diacritics Á, É, Í, Ó, Ú, Ü
    // are treated as the original letters A, E, I, O, U,
    @Test
    public void testUnicodeCollationWithLocaleSpanish() {
        createAlphaDocs();

        Collation localeEspanol = Collation.unicode()
            .setLocale("es")
            .setIgnoreCase(false)
            .setIgnoreAccents(false);

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property("string").collate(localeEspanol)));

        final String[] expected = {"A", "Å", "B", "Z"};
        verifyQuery(query, expected.length, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
    }

    // In the Swedish alphabet, there are three extra vowels
    // placed at its end (..., X, Y, Z, Å, Ä, Ö),
    // Early versions of Android do not support the Swedish Locale
    @Test
    public void testUnicodeCollationWithLocaleSwedish() {
        skipTestWhen("SWEDISH UNSUPPORTED");

        createAlphaDocs();

        Query query = QueryBuilder.select(SelectResult.property("string"))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.expression(Expression.property("string")
                .collate(Collation.unicode()
                    .setLocale("sv")
                    .setIgnoreCase(false)
                    .setIgnoreAccents(false))));

        String[] expected = {"A", "B", "Z", "Å"};
        verifyQuery(query, expected.length, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
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
            Document doc = saveDocInTestCollection(mDoc);

            Expression test = Expression.value(data.test);
            Expression comparison = Expression.property("value").collate(data.collation);
            comparison = data.mode ? comparison.equalTo(test) : comparison.lessThan(test);

            verifyQuery(
                QueryBuilder.select().from(DataSource.collection(getTestCollection())).where(comparison),
                1,
                (n, result) -> {
                    assertEquals(1, n);
                    assertNotNull(result);
                });

            getTestCollection().delete(doc);
        }
    }

    // This is a pretty finickey test: the numbers are important.
    // It will fail by timing out; you'll have to figure out why.
    @Test
    public void testLiveQuery() {
        List<MutableDocument> firstLoad = loadDocuments(100, 20);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(110)))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        List<String> secondBatch = Collections.synchronizedList(new ArrayList<>());
        QueryChangeListener listener = change -> {
            ResultSet rs = change.getResults();
            int count = 0;
            Result r;
            while ((r = rs.next()) != null) {
                count++;
                // The first run of this query should see a result set with 10 results:
                // There are 20 docs in the db, with sort keys 100 .. 119. Only 10
                // meet the where criteria < 110: (100 .. 109)
                if (latch1.getCount() > 0) {
                    if (count >= 10) { latch1.countDown(); }
                    continue;
                }

                // When we add 10 more documents, sort keys 1 .. 10, the live
                // query should report 20 docs matching the query
                secondBatch.add(r.getString("id"));
                if (count >= 20) { latch2.countDown(); }
            }
        };

        try (ListenerToken token = query.addChangeListener(getTestSerialExecutor(), listener)) {
            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

            // create some more docs
            List<MutableDocument> secondLoad = loadDocuments(10);

            // wait till listener sees them all
            assertTrue(latch2.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            // verify that the listener saw, in the second batch
            // the first 10 of the first load of documents
            List<String> expected = new ArrayList<>(Fn.mapToList(firstLoad.subList(0, 10), Document::getId));
            // and all of the second load.
            expected.addAll(Fn.mapToList(secondLoad, Document::getId));
            Collections.sort(expected);
            Collections.sort(secondBatch);
            assertEquals(expected, secondBatch);
        }
        // Catch clause prevents Windows compiler error
        catch (Exception e) { throw new AssertionError("Unexpected exception", e); }
    }

    @SlowTest
    @Test
    public void testLiveQueryNoUpdate1() throws InterruptedException { liveQueryNoUpdate(change -> { }); }

    @SuppressWarnings("StatementWithEmptyBody")
    @SlowTest
    @Test
    public void testLiveQueryNoUpdate2() throws InterruptedException {
        liveQueryNoUpdate(change -> {
            ResultSet rs = change.getResults();
            while (rs.next() != null) { }
        });
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1356
    @Test
    public void testCountFunctions() {
        loadDocuments(100);

        Query query =
            QueryBuilder.select(SelectResult.expression(Function.count(Expression.property(TEST_DOC_SORT_KEY))))
                .from(DataSource.collection(getTestCollection()));

        verifyQuery(query, 1, (n, result) -> assertEquals(100L, (long) result.getValue(0)));
    }

    @Test
    public void testJoinWithArrayContains() {
        // Data preparation
        // Hotels
        MutableDocument hotel1 = new MutableDocument();
        hotel1.setString("type", "hotel");
        hotel1.setString("name", "Hilton");
        saveDocInTestCollection(hotel1);

        MutableDocument hotel2 = new MutableDocument();
        hotel2.setString("type", "hotel");
        hotel2.setString("name", "Sheraton");
        saveDocInTestCollection(hotel2);

        MutableDocument hotel3 = new MutableDocument();
        hotel3.setString("type", "hotel");
        hotel3.setString("name", "Marriott");
        saveDocInTestCollection(hotel3);

        // Bookmark
        MutableDocument bookmark1 = new MutableDocument();
        bookmark1.setString("type", "bookmark");
        bookmark1.setString("title", "Bookmark For Hawaii");
        MutableArray hotels1 = new MutableArray();
        hotels1.addString("hotel1");
        hotels1.addString("hotel2");
        bookmark1.setArray("hotels", hotels1);
        saveDocInTestCollection(bookmark1);

        MutableDocument bookmark2 = new MutableDocument();
        bookmark2.setString("type", "bookmark");
        bookmark2.setString("title", "Bookmark for New York");
        MutableArray hotels2 = new MutableArray();
        hotels2.addString("hotel3");
        bookmark2.setArray("hotels", hotels2);
        saveDocInTestCollection(bookmark2);

        QueryBuilder
            .select(SelectResult.all().from("main"), SelectResult.all().from("secondary"))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(Join.join(DataSource.collection(getTestCollection()).as("secondary"))
                .on(ArrayFunction.contains(Expression.property("hotels").from("main"), Meta.id.from("secondary"))))
            .where(Expression.property("type").from("main").equalTo(Expression.string("bookmark")));
    }

    @Test
    public void testJoinWithEmptyArgs1() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all())
                .from(DataSource.collection(getTestCollection()).as("main"))
                .join((Join[]) null));
    }

    @Test
    public void testJoinWithEmptyArgs2() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all())
                .from(DataSource.collection(getTestCollection()).as("main"))
                .where(null));
    }

    @Test
    public void testJoinWithEmptyArgs3() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all())
                .from(DataSource.collection(getTestCollection()).as("main"))
                .groupBy((Expression[]) null));
    }

    @Test
    public void testJoinWithEmptyArgs4() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all())
                .from(DataSource.collection(getTestCollection()).as("main"))
                .orderBy((Ordering[]) null));
    }

    @Test
    public void testJoinWithEmptyArgs5() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all())
                .from(DataSource.collection(getTestCollection()).as("main"))
                .limit(null));
    }

    @Test
    public void testJoinWithEmptyArgs6() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryBuilder.select(SelectResult.all())
                .from(DataSource.collection(getTestCollection()).as("main"))
                .limit(null, null));
    }

    //https://github.com/couchbase/couchbase-lite-android/issues/1785
    @Test
    public void testResultToMapWithBoolean() {
        MutableDocument exam1 = new MutableDocument();
        exam1.setString("exam type", "final");
        exam1.setString("question", "There are 45 states in the US.");
        exam1.setBoolean("answer", false);
        saveDocInTestCollection(exam1);

        MutableDocument exam2 = new MutableDocument();
        exam2.setString("exam type", "final");
        exam2.setString("question", "There are 100 senators in the US.");
        exam2.setBoolean("answer", true);
        saveDocInTestCollection(exam2);

        Query query = QueryBuilder.select(SelectResult.all())
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("exam type").equalTo(Expression.string("final"))
                .and(Expression.property("answer").equalTo(Expression.booleanValue(true))));

        final String collectionName = getTestCollection().getName();
        verifyQuery(
            query,
            1,
            (n, result) -> {
                Map<String, Object> maps = result.toMap();
                assertNotNull(maps);
                Map<?, ?> map = (Map<?, ?>) maps.get(collectionName);
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
    public void testResultToMapWithBoolean2() {
        MutableDocument mDoc = new MutableDocument();
        mDoc.setString("exam type", "final");
        mDoc.setString("question", "There are 45 states in the US.");
        mDoc.setBoolean("answer", true);

        saveDocInTestCollection(mDoc);

        Query query = QueryBuilder
            .select(
                SelectResult.property("exam type"),
                SelectResult.property("question"),
                SelectResult.property("answer")
            )
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.equalTo(Expression.string(mDoc.getId())));

        verifyQuery(query, 1, (n, result) -> assertTrue((Boolean) result.toMap().get("answer")));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1385
    @Test
    public void testQueryDeletedDocument() throws CouchbaseLiteException {
        // Insert two documents
        Document task1 = createTaskDocument("Task 1", false);
        Document task2 = createTaskDocument("Task 2", false);
        assertEquals(2, getTestCollection().getCount());

        // query documents before deletion
        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.all())
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("type").equalTo(Expression.string("task")));

        verifyQuery(query, 2, (n, result) -> { });

        // delete artifacts from task 1
        getTestCollection().delete(task1);
        assertEquals(1, getTestCollection().getCount());
        assertNull(getTestCollection().getDocument(task1.getId()));

        // query documents again after deletion
        verifyQuery(query, 1, (n, result) -> assertEquals(task2.getId(), result.getString(0)));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1389
    @Test
    public void testQueryWhereBooleanExpression() {
        // STEP 1: Insert three documents
        createTaskDocument("Task 1", false);
        createTaskDocument("Task 2", true);
        createTaskDocument("Task 3", true);
        assertEquals(3, getTestCollection().getCount());

        Expression exprType = Expression.property("type");
        Expression exprComplete = Expression.property("complete");
        SelectResult srCount = SelectResult.expression(Function.count(Expression.intValue(1)));

        // regular query - true
        Query query = QueryBuilder.select(SelectResult.all())
            .from(DataSource.collection(getTestCollection()))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(true))));

        int numRows = verifyQueryWithEnumerator(
            query,
            (n, result) -> {
                Dictionary dict = result.getDictionary(getTestCollection().getName());
                assertTrue(dict.getBoolean("complete"));
                assertEquals("task", dict.getString("type"));
                assertTrue(dict.getString("title").startsWith("Task "));
            });
        assertEquals(2, numRows);

        // regular query - false
        query = QueryBuilder.select(SelectResult.all())
            .from(DataSource.collection(getTestCollection()))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(false))));

        numRows = verifyQueryWithEnumerator(
            query,
            (n, result) -> {
                Dictionary dict = result.getDictionary(getTestCollection().getName());
                assertFalse(dict.getBoolean("complete"));
                assertEquals("task", dict.getString("type"));
                assertTrue(dict.getString("title").startsWith("Task "));
            });
        assertEquals(1, numRows);

        // aggregation query - true
        query = QueryBuilder.select(srCount)
            .from(DataSource.collection(getTestCollection()))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(true))));

        numRows = verifyQueryWithEnumerator(query, (n, result) -> assertEquals(2, result.getInt(0)));
        assertEquals(1, numRows);

        // aggregation query - false
        query = QueryBuilder.select(srCount)
            .from(DataSource.collection(getTestCollection()))
            .where(exprType.equalTo(Expression.string("task"))
                .and(exprComplete.equalTo(Expression.booleanValue(false))));

        numRows = verifyQueryWithEnumerator(query, (n, result) -> assertEquals(1, result.getInt(0)));
        assertEquals(1, numRows);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1413
    @Test
    public void testJoinAll() {
        loadDocuments(100);

        final MutableDocument doc1 = new MutableDocument();
        doc1.setValue("theone", 42);
        saveDocInTestCollection(doc1);

        Query query = QueryBuilder.select(SelectResult.all().from("main"), SelectResult.all().from("secondary"))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(Join.join(DataSource.collection(getTestCollection()).as("secondary"))
                .on(Expression.property(TEST_DOC_SORT_KEY).from("main")
                    .equalTo(Expression.property("theone").from("secondary"))));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                Dictionary mainAll1 = result.getDictionary(0);
                Dictionary mainAll2 = result.getDictionary("main");
                Dictionary secondAll1 = result.getDictionary(1);
                Dictionary secondAll2 = result.getDictionary("secondary");
                assertEquals(42, mainAll1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(42, mainAll2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(58, mainAll1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(58, mainAll2.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(42, secondAll1.getInt("theone"));
                assertEquals(42, secondAll2.getInt("theone"));
            });
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1413
    @Test
    public void testJoinByDocID() {
        // Load a bunch of documents and pick one randomly
        Document doc1 = loadDocuments(100).get(MathUtils.RANDOM.get().nextInt(100));

        final MutableDocument mDoc = new MutableDocument();
        mDoc.setValue("theone", 42);
        mDoc.setString("numberID", doc1.getId()); // document ID of number documents.
        saveDocInTestCollection(mDoc);

        Query query = QueryBuilder.select(
                SelectResult.expression(Meta.id.from("main")).as("mainDocID"),
                SelectResult.expression(Meta.id.from("secondary")).as("secondaryDocID"),
                SelectResult.expression(Expression.property("theone").from("secondary")))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(Join.join(DataSource.collection(getTestCollection()).as("secondary"))
                .on(Meta.id.from("main").equalTo(Expression.property("numberID").from("secondary"))));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(1, n);

                Document doc3 = getTestCollection().getDocument(result.getString("mainDocID"));
                assertEquals(doc1.getInt(TEST_DOC_SORT_KEY), doc3.getInt(TEST_DOC_SORT_KEY));
                assertEquals(doc1.getInt(TEST_DOC_REV_SORT_KEY), doc3.getInt(TEST_DOC_REV_SORT_KEY));

                // data from secondary
                assertEquals(mDoc.getId(), result.getString("secondaryDocID"));
                assertEquals(42, result.getInt("theone"));
            });
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
    public void testAllComparison() {
        String[] values = {"Apple", "Aardvark", "Ångström", "Zebra", "äpple"};
        for (String value: values) {
            MutableDocument doc = new MutableDocument();
            doc.setString("hey", value);
            saveDocInTestCollection(doc);
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
                .from(DataSource.collection(getTestCollection()))
                .orderBy(Ordering.expression(property.collate((Collation) data.get(1))));

            final List<String> list = new ArrayList<>();
            verifyQueryWithEnumerator(query, (n, result) -> list.add(result.getString(0)));
            assertEquals(data.get(2), list);
        }
    }

    @Test
    public void testDeleteDatabaseWithActiveLiveQuery() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(1);
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()));

        try (ListenerToken token = query.addChangeListener(getTestSerialExecutor(), change -> latch1.countDown())) {
            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
            deleteDb(getTestDatabase());
        }
    }

    @Test
    public void testCloseDatabaseWithActiveLiveQuery() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()));

        ListenerToken token = query.addChangeListener(getTestSerialExecutor(), change -> latch.countDown());
        try {
            assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
            closeDb(getTestDatabase());
        }
        finally { token.remove(); }
    }

    @Test
    public void testFunctionCount() {
        loadDocuments(100);

        final MutableDocument doc = new MutableDocument();
        doc.setValue("string", "STRING");
        doc.setValue("date", null);
        saveDocInTestCollection(doc);

        Query query = QueryBuilder
            .select(
                SelectResult.expression(Function.count(Expression.property(TEST_DOC_SORT_KEY))),
                SelectResult.expression(Function.count(Expression.intValue(1))),
                SelectResult.expression(Function.count(Expression.string("*"))),
                SelectResult.expression(Function.count(Expression.all())),
                SelectResult.expression(Function.count(Expression.property("string"))),
                SelectResult.expression(Function.count(Expression.property("date"))),
                SelectResult.expression(Function.count(Expression.property("notExist"))))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(100L, (long) result.getValue(0));
                assertEquals(101L, (long) result.getValue(1));
                assertEquals(101L, (long) result.getValue(2));
                assertEquals(101L, (long) result.getValue(3));
                assertEquals(1L, (long) result.getValue(4));
                assertEquals(1L, (long) result.getValue(5));
                assertEquals(0L, (long) result.getValue(6));
            });
    }

    @Test
    public void testFunctionCountAll() {
        loadDocuments(100);

        // SELECT count(*)
        Query query = QueryBuilder.select(SelectResult.expression(Function.count(Expression.all())))
            .from(DataSource.collection(getTestCollection()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(1, result.count());
                assertEquals(100L, (long) result.getValue(0));
            });

        // SELECT count(testdb.*)
        query = QueryBuilder.select(SelectResult.expression(Function.count(Expression.all()
                .from(getTestCollection().getName()))))
            .from(DataSource.collection(getTestCollection()).as(getTestCollection().getName()));

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(1, result.count());
                assertEquals(100L, (long) result.getValue(0));
            });
    }

    // Throws clause prevents Windows compiler error
    @Test
    public void testResultSetEnumeration() throws Exception {
        List<String> docIds = Fn.mapToList(loadDocuments(5), Document::getId);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY));

        // Type 1: Enumeration by ResultSet.next()
        int i = 0;
        Result result;
        try (ResultSet rs = query.execute()) {
            while ((result = rs.next()) != null) {
                assertTrue(docIds.contains(result.getString(0)));
                i++;
            }
            assertEquals(docIds.size(), i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 2: Enumeration by ResultSet.iterator()
        i = 0;
        try (ResultSet rs = query.execute()) {
            for (Result r: rs) {
                assertTrue(docIds.contains(r.getString(0)));
                i++;
            }
            assertEquals(docIds.size(), i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 3: Enumeration by ResultSet.allResults().get(int index)
        i = 0;
        try (ResultSet rs = query.execute()) {
            List<Result> list = rs.allResults();
            for (Result r: list) {
                assertTrue(docIds.contains(r.getString(0)));
                i++;
            }
            assertEquals(docIds.size(), i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Type 4: Enumeration by ResultSet.allResults().iterator()
        i = 0;
        try (ResultSet rs = query.execute()) {
            for (Result r: rs.allResults()) {
                assertTrue(docIds.contains(r.getString(0)));
                i++;
            }
            assertEquals(docIds.size(), i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }
    }

    // Throws clause prevents Windows compiler error
    @Test
    public void testGetAllResults() throws Exception {
        List<String> docIds = Fn.mapToList(loadDocuments(5), Document::getId);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY));

        List<Result> results;

        // Get all results by get(int)
        int i = 0;
        try (ResultSet rs = query.execute()) {
            results = rs.allResults();
            for (int j = 0; j < results.size(); j++) {
                assertTrue(docIds.contains(results.get(j).getString(0)));
                i++;
            }
            assertEquals(docIds.size(), results.size());
            assertEquals(docIds.size(), i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Get all results by iterator
        i = 0;
        try (ResultSet rs = query.execute()) {
            results = rs.allResults();
            for (Result r: results) {
                assertTrue(docIds.contains(r.getString(0)));
                i++;
            }
            assertEquals(docIds.size(), results.size());
            assertEquals(docIds.size(), i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }

        // Partial enumerating then get all results:
        i = 0;
        try (ResultSet rs = query.execute()) {
            assertNotNull(rs.next());
            assertNotNull(rs.next());
            results = rs.allResults();
            for (Result r: results) {
                assertTrue(docIds.contains(r.getString(0)));
                i++;
            }
            assertEquals(docIds.size() - 2, results.size());
            assertEquals(docIds.size() - 2, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testResultSetEnumerationZeroResults() throws CouchbaseLiteException {
        loadDocuments(5);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY).is(Expression.intValue(100)))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY));

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
            for (Result ignored: rs) { i++; }
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
            for (Result ignored: rs.allResults()) { i++; }
            assertEquals(0, i);
            assertNull(rs.next());
            assertEquals(0, rs.allResults().size());
        }
    }

    @Test
    public void testMissingValue() {
        MutableDocument doc1 = new MutableDocument();
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        saveDocInTestCollection(doc1);

        Query query = QueryBuilder.select(
                SelectResult.property("name"),
                SelectResult.property("address"),
                SelectResult.property("age"))
            .from(DataSource.collection(getTestCollection()));

        // Array:
        verifyQuery(
            query,
            1,
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
            1,
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
    public void testExpressionNot() {
        loadDocuments(10);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.not(Expression.property(TEST_DOC_SORT_KEY)
                .between(Expression.intValue(3), Expression.intValue(5))))
            .orderBy(Ordering.expression(Expression.property(TEST_DOC_SORT_KEY)).ascending());

        verifyQuery(
            query,
            7,
            (n, result) -> {
                if (n < 3) { assertEquals(n, result.getInt(TEST_DOC_SORT_KEY)); }
                else { assertEquals(n + 3, result.getInt(TEST_DOC_SORT_KEY)); }
            });
    }

    @Test
    public void testLimitValueIsLargerThanResult() {
        List<MutableDocument> docIds = loadDocuments(4);

        Query query = QueryBuilder
            .select(SelectResult.all())
            .from(DataSource.collection(getTestCollection()))
            .limit(Expression.intValue(10));

        verifyQuery(query, docIds.size(), (n, result) -> { });
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1614
    @Test
    public void testFTSStemming() throws CouchbaseLiteException {
        MutableDocument mDoc0 = new MutableDocument();
        mDoc0.setString("content", "hello");
        mDoc0.setInt(TEST_DOC_SORT_KEY, 0);
        saveDocInTestCollection(mDoc0);

        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setString("content", "beauty");
        mDoc1.setInt(TEST_DOC_SORT_KEY, 10);
        saveDocInTestCollection(mDoc1);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setString("content", "beautifully");
        mDoc2.setInt(TEST_DOC_SORT_KEY, 20);
        saveDocInTestCollection(mDoc2);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setString("content", "beautiful");
        mDoc3.setInt(TEST_DOC_SORT_KEY, 30);
        saveDocInTestCollection(mDoc3);

        MutableDocument mDoc4 = new MutableDocument();
        mDoc4.setString("content", "pretty");
        mDoc4.setInt(TEST_DOC_SORT_KEY, 40);
        saveDocInTestCollection(mDoc4);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        ftsIndex.setLanguage(Locale.ENGLISH.getLanguage());
        getTestCollection().createIndex("ftsIndex", ftsIndex);
        IndexExpression idx = Expression.fullTextIndex("ftsIndex");

        String[] expectedIDs = {mDoc1.getId(), mDoc2.getId(), mDoc3.getId()};
        String[] expectedContents = {"beauty", "beautifully", "beautiful"};

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "beautiful"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        verifyQuery(
            query,
            3,
            (n, result) -> {
                assertEquals(expectedIDs[n - 1], result.getString("id"));
                assertEquals(expectedContents[n - 1], result.getString("content"));
            });
    }

    // https://github.com/couchbase/couchbase-lite-net/blob/master/src/Couchbase.Lite.Tests.Shared/QueryTest.cs#L1721
    @Test
    public void testFTSStemming2() throws CouchbaseLiteException {
        getTestCollection().createIndex(
            "passageIndex",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("passage")).setLanguage("en"));
        IndexExpression idx = Expression.fullTextIndex("passageIndex");

        getTestCollection().createIndex(
            "passageIndexStemless",
            IndexBuilder.fullTextIndex(FullTextIndexItem.property("passage")).setLanguage(null));
        IndexExpression stemlessIdx = Expression.fullTextIndex("passageIndexStemless");

        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setString("passage", "The boy said to the child, 'Mommy, I want a cat.'");
        saveDocInTestCollection(mDoc1);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setString("passage", "The mother replied 'No, you already have too many cats.'");
        saveDocInTestCollection(mDoc2);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "cat"));

        String[] expected = new String[] {mDoc1.getId(), mDoc2.getId()};

        verifyQuery(query, 2, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));

        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(stemlessIdx, "cat"));

        verifyQuery(query, 1, (n, result) -> assertEquals(expected[n - 1], result.getString(0)));
    }

    // 3.1. Set Operations Using The Enhanced Query Syntax
    // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax
    // https://github.com/couchbase/couchbase-lite-android/issues/1620
    @Test
    public void testFTSSetOperations() throws CouchbaseLiteException {
        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setString("content", "a database is a software system");
        mDoc1.setInt(TEST_DOC_SORT_KEY, 100);
        saveDocInTestCollection(mDoc1);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setString("content", "sqlite is a software system");
        mDoc2.setInt(TEST_DOC_SORT_KEY, 200);
        saveDocInTestCollection(mDoc2);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setString("content", "sqlite is a database");
        mDoc3.setInt(TEST_DOC_SORT_KEY, 300);
        saveDocInTestCollection(mDoc3);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        getTestCollection().createIndex("ftsIndex", ftsIndex);
        IndexExpression idx = Expression.fullTextIndex("ftsIndex");

        // The enhanced query syntax
        // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax

        // AND binary set operator
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "sqlite AND database"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());
        verifyQuery(query, 1, (n, result) -> assertEquals(mDoc3.getId(), result.getString("id")));

        // implicit AND operator
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "sqlite database"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());
        verifyQuery(query, 1, (n, result) -> assertEquals(mDoc3.getId(), result.getString("id")));

        // OR operator
        query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "sqlite OR database"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());
        String[] expected = {mDoc1.getId(), mDoc2.getId(), mDoc3.getId()};
        verifyQuery(query, 3, (n, result) -> assertEquals(expected[n - 1], result.getString("id")));

        // NOT operator
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "database NOT sqlite"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());
        verifyQuery(query, 1, (n, result) -> assertEquals(mDoc1.getId(), result.getString("id")));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1621
    @Test
    public void testFTSMixedOperators() throws CouchbaseLiteException {
        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setString("content", "a database is a software system");
        mDoc1.setInt(TEST_DOC_SORT_KEY, 10);
        saveDocInTestCollection(mDoc1);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setString("content", "sqlite is a software system");
        mDoc2.setInt(TEST_DOC_SORT_KEY, 20);
        saveDocInTestCollection(mDoc2);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setString("content", "sqlite is a database");
        mDoc3.setInt(TEST_DOC_SORT_KEY, 30);
        saveDocInTestCollection(mDoc3);

        FullTextIndex ftsIndex = IndexBuilder.fullTextIndex(FullTextIndexItem.property("content"));
        getTestCollection().createIndex("ftsIndex", ftsIndex);
        IndexExpression idx = Expression.fullTextIndex("ftsIndex");

        // The enhanced query syntax
        // https://www.sqlite.org/fts3.html#_set_operations_using_the_enhanced_query_syntax

        // A AND B AND C
        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "sqlite AND software AND system"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        verifyQuery(query, 1, (n, result) -> assertEquals(mDoc2.getId(), result.getString("id")));


        // (A AND B) OR C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "(sqlite AND software) OR database"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        String[] expectedIDs2 = {mDoc1.getId(), mDoc2.getId(), mDoc3.getId()};
        verifyQuery(query, 3, (n, result) -> assertEquals(expectedIDs2[n - 1], result.getString("id")));

        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "(sqlite AND software) OR system"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        String[] expectedIDs3 = {mDoc1.getId(), mDoc2.getId()};
        verifyQuery(query, 2, (n, result) -> assertEquals(expectedIDs3[n - 1], result.getString("id")));

        // (A OR B) AND C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "(sqlite OR software) AND database"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        String[] expectedIDs4 = {mDoc1.getId(), mDoc3.getId()};
        verifyQuery(query, 2, (n, result) -> assertEquals(expectedIDs4[n - 1], result.getString("id")));

        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "(sqlite OR software) AND system"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        String[] expectedIDs5 = {mDoc1.getId(), mDoc2.getId()};
        verifyQuery(query, 2, (n, result) -> assertEquals(expectedIDs5[n - 1], result.getString("id")));

        // A OR B OR C
        query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property("content"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match(idx, "database OR software OR system"))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        String[] expectedIDs6 = {mDoc1.getId(), mDoc2.getId(), mDoc3.getId()};
        verifyQuery(query, 3, (n, result) -> assertEquals(expectedIDs6[n - 1], result.getString("id")));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1628
    @Test
    public void testLiveQueryResultsCount() throws InterruptedException {
        loadDocuments(50);

        Query query = QueryBuilder
            .select()
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY).greaterThan(Expression.intValue(25)))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final QueryChangeListener listener = change -> {
            int count = 0;
            ResultSet rs = change.getResults();
            while (rs.next() != null) {
                count++;
                // The first run of this query should see a result set with 25 results:
                // 50 docs in the db minus 25 with values < 25
                // When we add 50 more documents, after the first latch springs,
                // there are 100 docs in the db, 75 of which have vaules > 25
                if ((count >= 25) && (latch1.getCount() > 0)) { latch1.countDown(); }
                else if (count >= 75) { latch2.countDown(); }
            }
        };

        try (ListenerToken token = query.addChangeListener(getTestSerialExecutor(), listener)) {
            assertTrue(latch1.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));

            loadDocuments(51, 50, getTestCollection());

            assertTrue(latch2.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }

    // https://forums.couchbase.com/t/
    //     how-to-be-notifed-that-document-is-changed-but-livequerys-query-isnt-catching-it-anymore/16199/9
    @Test
    public void testLiveQueryNotification() throws CouchbaseLiteException, InterruptedException {
        // save doc1 with sort key = 5
        MutableDocument doc = new MutableDocument();
        doc.setInt(TEST_DOC_SORT_KEY, 5);
        saveDocInTestCollection(doc);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.property(TEST_DOC_SORT_KEY))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(10)))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final QueryChangeListener listener = change -> {
            int matches = 0;
            for (Result ignored: change.getResults()) { matches++; }

            // match doc1 with number1 -> 5 which is less than 10
            if (matches == 1) { latch1.countDown(); }
            // Not match with doc1 because number1 -> 15 which does not match the query criteria
            else { latch2.countDown(); }
        };

        try (ListenerToken token = query.addChangeListener(getTestSerialExecutor(), listener)) {
            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

            doc = getTestCollection().getDocument(doc.getId()).toMutable();
            doc.setInt(TEST_DOC_SORT_KEY, 15);
            saveDocInTestCollection(doc);

            assertTrue(latch2.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1689
    @Test
    public void testQueryAndNLikeOperators() {
        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setString("name", "food");
        mDoc1.setString("description", "bar");
        mDoc1.setInt(TEST_DOC_SORT_KEY, 10);
        saveDocInTestCollection(mDoc1);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setString("name", "foo");
        mDoc2.setString("description", "unknown");
        mDoc2.setInt(TEST_DOC_SORT_KEY, 20);
        saveDocInTestCollection(mDoc2);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setString("name", "water");
        mDoc3.setString("description", "drink");
        mDoc3.setInt(TEST_DOC_SORT_KEY, 30);
        saveDocInTestCollection(mDoc3);

        MutableDocument mDoc4 = new MutableDocument();
        mDoc4.setString("name", "chocolate");
        mDoc4.setString("description", "bar");
        mDoc4.setInt(TEST_DOC_SORT_KEY, 40);
        saveDocInTestCollection(mDoc4);

        // LIKE operator only
        Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("name").like(Expression.string("%foo%")))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        verifyQuery(
            query,
            2,
            (n, result) -> {
                assertEquals(1, result.count());
                if (n == 1) { assertEquals(mDoc1.getId(), result.getString(0)); }
                else { assertEquals(mDoc2.getId(), result.getString(0)); }
            });

        // EQUAL operator only
        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("description").equalTo(Expression.string("bar")))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        verifyQuery(
            query,
            2,
            (n, result) -> {
                assertEquals(1, result.count());
                if (n == 1) { assertEquals(mDoc1.getId(), result.getString(0)); }
                else { assertEquals(mDoc4.getId(), result.getString(0)); }
            });

        // AND and LIKE operators
        query = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("name").like(Expression.string("%foo%"))
                .and(Expression.property("description").equalTo(Expression.string("bar"))))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        verifyQuery(
            query,
            1,
            (n, result) -> {
                assertEquals(1, result.count());
                assertEquals(mDoc1.getId(), result.getString(0));
            });
    }

    // https://forums.couchbase.com/t/
    //     how-to-implement-an-index-join-clause-in-couchbase-lite-2-0-using-objective-c-api/16246
    // https://github.com/couchbase/couchbase-lite-core/issues/497
    @Test
    public void testQueryJoinAndSelectAll() {
        loadDocuments(100);

        final MutableDocument joinme = new MutableDocument();
        joinme.setValue("theone", 42);
        saveDocInTestCollection(joinme);

        Query query = QueryBuilder.select(SelectResult.all().from("main"), SelectResult.all().from("secondary"))
            .from(DataSource.collection(getTestCollection()).as("main"))
            .join(Join.leftJoin(DataSource.collection(getTestCollection()).as("secondary"))
                .on(Expression.property(TEST_DOC_SORT_KEY).from("main").equalTo(Expression.property("theone")
                    .from("secondary"))));

        verifyQuery(
            query,
            101,
            (n, result) -> {
                if (n == 41) {
                    assertEquals(59, result.getDictionary("main").getInt(TEST_DOC_REV_SORT_KEY));
                    assertNull(result.getDictionary("secondary"));
                }
                if (n == 42) {
                    assertEquals(58, result.getDictionary("main").getInt(TEST_DOC_REV_SORT_KEY));
                    assertEquals(42, result.getDictionary("secondary").getInt("theone"));
                }
            });
    }

    @Test
    public void testResultSetAllResults() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument();
        doc1a.setInt("answer", 42);
        doc1a.setString("a", "string");
        saveDocInTestCollection(doc1a);

        Query query = QueryBuilder.select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.deleted))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.equalTo(Expression.string(doc1a.getId())));

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
    public void testStringToMillis() {
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
        Report.log("Local time offset: %d", offset);
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
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.property("local").ascending());

        verifyQuery(
            query,
            6,
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
    public void testStringToUTC() {
        createDateDocs();

        ArrayList<String> expectedLocal = new ArrayList<>();
        expectedLocal.add("1985-10-26T07:00:00Z");
        expectedLocal.add("1985-10-26T08:21:00Z");
        expectedLocal.add("1985-10-26T08:21:30Z");
        expectedLocal.add("1985-10-26T08:21:30.500Z");
        expectedLocal.add("1985-10-26T08:21:30.550Z");
        expectedLocal.add("1985-10-26T08:21:30.555Z");

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
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.property("local").ascending());

        verifyQuery(
            query,
            6,
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
    public void testMillisConversion() {

        final List<String> expectedUTC = Arrays.asList(
            "1985-10-26T00:00:00Z",
            "1985-10-26T01:21:00Z",
            "1985-10-26T01:21:30Z",
            "1985-10-26T01:21:30.500Z",
            "1985-10-26T01:21:30.550Z",
            "1985-10-26T01:21:30.555Z");

        for (Number t: new Number[] {
            499132800000L,
            499137660000L,
            499137690000L,
            499137690500L,
            499137690550L,
            499137690555L}) {
            saveDocInTestCollection(new MutableDocument().setNumber("timestamp", t));
        }

        Query query = QueryBuilder.select(
                SelectResult.expression(Function.millisToString(Expression.property("timestamp"))),
                SelectResult.expression(Function.millisToUTC(Expression.property("timestamp"))))
            .from(DataSource.collection(getTestCollection()))
            .orderBy(Ordering.property("timestamp").ascending());

        verifyQuery(
            query,
            6,
            (n, result) -> {
                final int i = n - 1;
                assertEquals(expectedUTC.get(i), result.getString(1));
            });
    }

    @Test
    public void testQueryDocumentWithDollarSign() throws CouchbaseLiteException {
        saveDocInTestCollection(new MutableDocument()
            .setString("$type", "book")
            .setString("$description", "about cats")
            .setString("$price", "$100"));
        saveDocInTestCollection(new MutableDocument()
            .setString("$type", "book")
            .setString("$description", "about dogs")
            .setString("$price", "$95"));
        saveDocInTestCollection(new MutableDocument()
            .setString("$type", "animal")
            .setString("$description", "puppy")
            .setString("$price", "$195"));

        int cheapBooks = 0;
        int books = 0;

        Where q = QueryBuilder.select(
                SelectResult.expression(Meta.id),
                SelectResult.expression(Expression.property("$type")),
                SelectResult.expression(Expression.property("$price")))
            .from(DataSource.collection(getTestCollection()))
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

    @Test
    public void testN1QLSelect() {
        loadDocuments(100);

        Query query = getTestDatabase().createQuery(
            "SELECT " + TEST_DOC_SORT_KEY + ", " + TEST_DOC_REV_SORT_KEY
                + " FROM " + BaseDbTestKt.getQualifiedName(getTestCollection()));

        verifyQuery(
            query,
            100,
            (n, result) -> {
                assertEquals(n, result.getInt(TEST_DOC_SORT_KEY));
                assertEquals(n, result.getInt(0));
                assertEquals(100 - n, result.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(100 - n, result.getInt(1));
            });
    }

    @Test
    public void testN1QLSelectStarFromDefault() throws CouchbaseLiteException {
        loadDocuments(100, getTestDatabase().getDefaultCollection());
        verifyQuery(
            getTestDatabase().createQuery("SELECT * FROM _default"),
            100,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary("_default");
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
            });
    }

    @Test
    public void testN1QLSelectStarFromCollection() {
        loadDocuments(100);

        verifyQuery(
            getTestDatabase().createQuery("SELECT * FROM " + BaseDbTestKt.getQualifiedName(getTestCollection())),
            100,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary(getTestCollection().getName());
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
            });
    }

    @Test
    public void testN1QLSelectStarFromUnderscore() throws CouchbaseLiteException {
        loadDocuments(100, getTestDatabase().getDefaultCollection());
        verifyQuery(
            getTestDatabase().createQuery("SELECT * FROM _"),
            100,
            (n, result) -> {
                assertEquals(1, result.count());
                Dictionary a1 = result.getDictionary(0);
                Dictionary a2 = result.getDictionary("_");
                assertEquals(n, a1.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a1.getInt(TEST_DOC_REV_SORT_KEY));
                assertEquals(n, a2.getInt(TEST_DOC_SORT_KEY));
                assertEquals(100 - n, a2.getInt(TEST_DOC_REV_SORT_KEY));
            });
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testWhereNullOrMissing() {
        MutableDocument doc1 = new MutableDocument();
        doc1.setValue("name", "Scott");
        doc1.setValue("address", null);
        saveDocInTestCollection(doc1);

        MutableDocument doc2 = new MutableDocument();
        doc2.setValue("name", "Tiger");
        doc2.setValue("address", "123 1st ave.");
        doc2.setValue("age", 20);
        saveDocInTestCollection(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        for (TestCase testCase: new TestCase[] {
            new TestCase(name.isNullOrMissing()),
            new TestCase(name.notNullOrMissing(), doc1.getId(), doc2.getId()),
            new TestCase(address.isNullOrMissing(), doc1.getId()),
            new TestCase(address.notNullOrMissing(), doc2.getId()),
            new TestCase(age.isNullOrMissing(), doc1.getId()),
            new TestCase(age.notNullOrMissing(), doc2.getId()),
            new TestCase(work.isNullOrMissing(), doc1.getId(), doc2.getId()),
            new TestCase(work.notNullOrMissing())
        }) {
            verifyQuery(
                QueryBuilder.select(SelectResult.expression(Meta.id))
                    .from(DataSource.collection(getTestCollection()))
                    .where(testCase.expr),
                testCase.docIds.size(),
                (n, result) -> {
                    if (n <= testCase.docIds.size()) {
                        assertEquals(testCase.docIds.get(n - 1), result.getString(0));
                    }
                });
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testLegacyIndexMatch() throws CouchbaseLiteException {
        loadJSONResourceIntoCollection("sentences.json");

        getTestCollection().createIndex("sentence", IndexBuilder.fullTextIndex(FullTextIndexItem.property("sentence")));

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id), SelectResult.property("sentence"))
            .from(DataSource.collection(getTestCollection()))
            .where(FullTextFunction.match("sentence", "'Dummie woman'"))
            .orderBy(Ordering.expression(FullTextFunction.rank("sentence")).descending());

        verifyQuery(
            query,
            2,
            (n, result) -> {
                assertNotNull(result.getString(0));
                assertNotNull(result.getString(1));
            });
    }

    // Utility Functions

    protected final void loadJSONResourceIntoCollection(String resName) {
        loadJSONResourceIntoCollection(resName, getTestCollection());
    }

    protected final void loadJSONResourceIntoCollection(String resName, Collection collection) {
        loadJSONResourceIntoCollection(resName, "doc-%03d", collection);
    }

    private void runTests(TestCase... cases) {
        for (TestCase testCase: cases) {
            final List<String> docIdList = new ArrayList<>(testCase.docIds);
            Query query = QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(getTestCollection()))
                .where(testCase.expr);
            verifyQuery(
                query,
                testCase.docIds.size(),
                (n, result) -> docIdList.remove(result.getString(0)));

            assertEquals(0, docIdList.size());
        }
    }

    private void testOrdered(Ordering ordering, Comparator<String> cmp) {
        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQueryWithEnumerator(
            QueryBuilder.select(SelectResult.expression(Meta.id)).from(DataSource.collection(getTestCollection()))
                .orderBy(ordering),
            (n, result) -> {
                String docID = result.getString(0);
                Document doc = getTestCollection().getDocument(docID);
                Map<String, Object> name = doc.getDictionary("name").toMap();
                String firstName = (String) name.get("first");
                firstNames.add(firstName);
            });
        assertEquals(100, numRows);
        assertEquals(100, firstNames.size());

        List<String> sorted = new ArrayList<>(firstNames);
        Collections.sort(sorted, cmp);
        assertArrayEquals(sorted.toArray(new String[0]), firstNames.toArray(new String[0]));
    }

    private void createAlphaDocs() {
        for (String letter: new String[] {"B", "Z", "Å", "A"}) {
            MutableDocument doc = new MutableDocument();
            doc.setValue("string", letter);
            saveDocInTestCollection(doc);
        }
    }

    private void createDateDocs() {
        MutableDocument doc = new MutableDocument();
        doc.setString("local", "1985-10-26");
        saveDocInTestCollection(doc);

        for (String format:
            new String[] {
                "1985-10-26 01:21",
                "1985-10-26 01:21:30",
                "1985-10-26 01:21:30.5",
                "1985-10-26 01:21:30.55",
                "1985-10-26 01:21:30.555"}) {
            doc = new MutableDocument();
            doc.setString("local", format);
            doc.setString("JST", format + "+09:00");
            doc.setString("JST2", format + "+0900");
            doc.setString("PST", format + "-08:00");
            doc.setString("PST2", format + "-0800");
            doc.setString("UTC", format + "Z");
            saveDocInTestCollection(doc);
        }
    }

    private String jsonDocId(int i) { return String.format(Locale.ENGLISH, "doc-%03d", i); }

    private Document createTaskDocument(String title, boolean complete) {
        MutableDocument doc = new MutableDocument();
        doc.setString("type", "task");
        doc.setString("title", title);
        doc.setBoolean("complete", complete);
        return saveDocInTestCollection(doc);
    }

    private void liveQueryNoUpdate(Fn.Consumer<QueryChange> test) throws InterruptedException {
        loadDocuments(100);

        Query query = QueryBuilder
            .select(SelectResult.expression(Expression.property(TEST_DOC_SORT_KEY)))
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property(TEST_DOC_SORT_KEY).lessThan(Expression.intValue(50)))
            .orderBy(Ordering.property(TEST_DOC_SORT_KEY).ascending());

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        QueryChangeListener listener = change -> {
            test.accept(change);
            // Attaching the listener should run the query and get the results
            // That will pop the latch allowing the addition of a bunch more docs
            // Those new docs, however, do not fit the where clause and should
            // not cause the listener to be called again.
            if (latch1.getCount() > 0) { latch1.countDown(); }
            else { latch2.countDown(); }
        };

        try (ListenerToken token = query.addChangeListener(getTestSerialExecutor(), listener)) {
            assertTrue(latch1.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));

            // create more docs
            loadDocuments(101, 100);

            // Wait 5 seconds
            // The latch should not pop, because the listener should be called only once
            // ??? This is a very expensive way to test
            assertFalse(latch2.await(5 * 1000, TimeUnit.MILLISECONDS));
            assertEquals(1, latch2.getCount());
        }
    }
}
