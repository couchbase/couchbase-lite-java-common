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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.JSONUtils;


@SuppressWarnings("ConstantConditions")
public class ResultTest extends BaseQueryTest {

    @Test
    public void testGetValueByKey() {
        runTest((query) -> {
            // run query
            int rows = verifyQueryWithEnumerator(
                query,
                (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getValue("null"));
                    Assert.assertEquals(true, r.getValue("true"));
                    Assert.assertEquals(false, r.getValue("false"));
                    Assert.assertEquals("string", r.getValue("string"));
                    Assert.assertEquals(0L, r.getValue("zero"));
                    Assert.assertEquals(1L, r.getValue("one"));
                    Assert.assertEquals(-1L, r.getValue("minus_one"));
                    Assert.assertEquals(1.1, r.getValue("one_dot_one"));
                    Assert.assertEquals(TEST_DATE, r.getValue("date"));
                    Assert.assertTrue(r.getValue("dict") instanceof Dictionary);
                    Assert.assertTrue(r.getValue("array") instanceof Array);
                    Assert.assertTrue(r.getValue("blob") instanceof Blob);
                    Assert.assertNull(r.getValue("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getValue(null));

                    Assert.assertNull(r.getValue("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        });
    }


    private void runTest(Fn.Consumer<Query> test) {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);
            test.accept(query);
        }
    }


    @Test
    public void testGetValue() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            // run query
            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getValue(0));
                    Assert.assertEquals(true, r.getValue(1));
                    Assert.assertEquals(false, r.getValue(2));
                    Assert.assertEquals("string", r.getValue(3));
                    Assert.assertEquals(0L, r.getValue(4));
                    Assert.assertEquals(1L, r.getValue(5));
                    Assert.assertEquals(-1L, r.getValue(6));
                    Assert.assertEquals(1.1, r.getValue(7));
                    Assert.assertEquals(TEST_DATE, r.getValue(8));
                    Assert.assertTrue(r.getValue(9) instanceof Dictionary);
                    Assert.assertTrue(r.getValue(10) instanceof Array);
                    Assert.assertTrue(r.getValue(11) instanceof Blob);
                    Assert.assertNull(r.getValue(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getValue(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getValue(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetStringByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getString("null"));
                    Assert.assertNull(r.getString("true"));
                    Assert.assertNull(r.getString("false"));
                    Assert.assertEquals("string", r.getString("string"));
                    Assert.assertNull(r.getString("zero"));
                    Assert.assertNull(r.getString("one"));
                    Assert.assertNull(r.getString("minus_one"));
                    Assert.assertNull(r.getString("one_dot_one"));
                    Assert.assertEquals(TEST_DATE, r.getString("date"));
                    Assert.assertNull(r.getString("dict"));
                    Assert.assertNull(r.getString("array"));
                    Assert.assertNull(r.getString("blob"));
                    Assert.assertNull(r.getString("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getString(null));

                    Assert.assertNull(r.getString("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetString() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getString(0));
                    Assert.assertNull(r.getString(1));
                    Assert.assertNull(r.getString(2));
                    Assert.assertEquals("string", r.getString(3));
                    Assert.assertNull(r.getString(4));
                    Assert.assertNull(r.getString(5));
                    Assert.assertNull(r.getString(6));
                    Assert.assertNull(r.getString(7));
                    Assert.assertEquals(TEST_DATE, r.getString(8));
                    Assert.assertNull(r.getString(9));
                    Assert.assertNull(r.getString(10));
                    Assert.assertNull(r.getString(11));
                    Assert.assertNull(r.getString(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getString(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getString(100));
                });

            Assert.assertEquals(1, rows);
        }
    }


    @Test
    public void testGetNumberByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getNumber("null"));
                    Assert.assertEquals(1, r.getNumber("true").intValue());
                    Assert.assertEquals(0, r.getNumber("false").intValue());
                    Assert.assertNull(r.getNumber("string"));
                    Assert.assertEquals(0, r.getNumber("zero").intValue());
                    Assert.assertEquals(1, r.getNumber("one").intValue());
                    Assert.assertEquals(-1, r.getNumber("minus_one").intValue());
                    Assert.assertEquals(1.1, r.getNumber("one_dot_one"));
                    Assert.assertNull(r.getNumber("date"));
                    Assert.assertNull(r.getNumber("dict"));
                    Assert.assertNull(r.getNumber("array"));
                    Assert.assertNull(r.getNumber("blob"));
                    Assert.assertNull(r.getNumber("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getNumber(null));

                    Assert.assertNull(r.getNumber("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetNumber() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getNumber(0));  // null
                    Assert.assertEquals(1, r.getNumber(1).intValue());  // true
                    Assert.assertEquals(0, r.getNumber(2).intValue());  // false
                    Assert.assertNull(r.getNumber(3));  // string
                    Assert.assertEquals(0, r.getNumber(4).intValue());
                    Assert.assertEquals(1, r.getNumber(5).intValue());
                    Assert.assertEquals(-1, r.getNumber(6).intValue());
                    Assert.assertEquals(1.1, r.getNumber(7));
                    Assert.assertNull(r.getNumber(8));
                    Assert.assertNull(r.getNumber(9));
                    Assert.assertNull(r.getNumber(10));
                    Assert.assertNull(r.getNumber(11));
                    Assert.assertNull(r.getNumber(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getNumber(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getNumber(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetIntegerByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0, r.getInt("null"));
                    Assert.assertEquals(1, r.getInt("true"));
                    Assert.assertEquals(0, r.getInt("false"));
                    Assert.assertEquals(0, r.getInt("string"));
                    Assert.assertEquals(0, r.getInt("zero"));
                    Assert.assertEquals(1, r.getInt("one"));
                    Assert.assertEquals(-1, r.getInt("minus_one"));
                    Assert.assertEquals(1, r.getInt("one_dot_one"));
                    Assert.assertEquals(0, r.getInt("date"));
                    Assert.assertEquals(0, r.getInt("dict"));
                    Assert.assertEquals(0, r.getInt("array"));
                    Assert.assertEquals(0, r.getInt("blob"));
                    Assert.assertEquals(0, r.getInt("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getInt(null));

                    Assert.assertEquals(0, r.getInt("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetInteger() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0, r.getInt(0));
                    Assert.assertEquals(1, r.getInt(1));
                    Assert.assertEquals(0, r.getInt(2));
                    Assert.assertEquals(0, r.getInt(3));
                    Assert.assertEquals(0, r.getInt(4));
                    Assert.assertEquals(1, r.getInt(5));
                    Assert.assertEquals(-1, r.getInt(6));
                    Assert.assertEquals(1, r.getInt(7));
                    Assert.assertEquals(0, r.getInt(8));
                    Assert.assertEquals(0, r.getInt(9));
                    Assert.assertEquals(0, r.getInt(10));
                    Assert.assertEquals(0, r.getInt(11));
                    Assert.assertEquals(0, r.getInt(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getInt(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getInt(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetLongByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0, r.getLong("null"));
                    Assert.assertEquals(1, r.getLong("true"));
                    Assert.assertEquals(0, r.getLong("false"));
                    Assert.assertEquals(0, r.getLong("string"));
                    Assert.assertEquals(0, r.getLong("zero"));
                    Assert.assertEquals(1, r.getLong("one"));
                    Assert.assertEquals(-1, r.getLong("minus_one"));
                    Assert.assertEquals(1, r.getLong("one_dot_one"));
                    Assert.assertEquals(0, r.getLong("date"));
                    Assert.assertEquals(0, r.getLong("dict"));
                    Assert.assertEquals(0, r.getLong("array"));
                    Assert.assertEquals(0, r.getLong("blob"));
                    Assert.assertEquals(0, r.getLong("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getLong(null));

                    Assert.assertEquals(0, r.getLong("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetLong() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0, r.getLong(0));
                    Assert.assertEquals(1, r.getLong(1));
                    Assert.assertEquals(0, r.getLong(2));
                    Assert.assertEquals(0, r.getLong(3));
                    Assert.assertEquals(0, r.getLong(4));
                    Assert.assertEquals(1, r.getLong(5));
                    Assert.assertEquals(-1, r.getLong(6));
                    Assert.assertEquals(1, r.getLong(7));
                    Assert.assertEquals(0, r.getLong(8));
                    Assert.assertEquals(0, r.getLong(9));
                    Assert.assertEquals(0, r.getLong(10));
                    Assert.assertEquals(0, r.getLong(11));
                    Assert.assertEquals(0, r.getLong(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getLong(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getLong(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetFloatByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0.0f, r.getFloat("null"), 0.0f);
                    Assert.assertEquals(1.0f, r.getFloat("true"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("false"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("string"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("zero"), 0.0f);
                    Assert.assertEquals(1.0f, r.getFloat("one"), 0.0f);
                    Assert.assertEquals(-1.0f, r.getFloat("minus_one"), 0.0f);
                    Assert.assertEquals(1.1f, r.getFloat("one_dot_one"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("date"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("dict"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("array"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("blob"), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat("non_existing_key"), 0.0f);

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getFloat(null));

                    Assert.assertEquals(0.0f, r.getFloat("not_in_query_select"), 0.0f);
                });
            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetFloat() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0.0f, r.getFloat(0), 0.0f);
                    Assert.assertEquals(1.0f, r.getFloat(1), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(2), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(3), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(4), 0.0f);
                    Assert.assertEquals(1.0f, r.getFloat(5), 0.0f);
                    Assert.assertEquals(-1.0f, r.getFloat(6), 0.0f);
                    Assert.assertEquals(1.1f, r.getFloat(7), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(8), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(9), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(10), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(11), 0.0f);
                    Assert.assertEquals(0.0f, r.getFloat(12), 0.0f);

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getFloat(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getFloat(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDoubleByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0.0, r.getDouble("null"), 0.0);
                    Assert.assertEquals(1.0, r.getDouble("true"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("false"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("string"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("zero"), 0.0);
                    Assert.assertEquals(1.0, r.getDouble("one"), 0.0);
                    Assert.assertEquals(-1.0, r.getDouble("minus_one"), 0.0);
                    Assert.assertEquals(1.1, r.getDouble("one_dot_one"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("date"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("dict"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("array"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("blob"), 0.0);
                    Assert.assertEquals(0.0, r.getDouble("non_existing_key"), 0.0);

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getDouble(null));

                    Assert.assertEquals(0.0, r.getDouble("not_in_query_select"), 0.0);
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDouble() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertEquals(0.0, r.getDouble(0), 0.0);
                    Assert.assertEquals(1.0, r.getDouble(1), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(2), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(3), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(4), 0.0);
                    Assert.assertEquals(1.0, r.getDouble(5), 0.0);
                    Assert.assertEquals(-1.0, r.getDouble(6), 0.0);
                    Assert.assertEquals(1.1, r.getDouble(7), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(8), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(9), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(10), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(11), 0.0);
                    Assert.assertEquals(0.0, r.getDouble(12), 0.0);

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getDouble(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getDouble(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBooleanByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertFalse(r.getBoolean("null"));
                    Assert.assertTrue(r.getBoolean("true"));
                    Assert.assertFalse(r.getBoolean("false"));
                    Assert.assertTrue(r.getBoolean("string"));
                    Assert.assertFalse(r.getBoolean("zero"));
                    Assert.assertTrue(r.getBoolean("one"));
                    Assert.assertTrue(r.getBoolean("minus_one"));
                    Assert.assertTrue(r.getBoolean("one_dot_one"));
                    Assert.assertTrue(r.getBoolean("date"));
                    Assert.assertTrue(r.getBoolean("dict"));
                    Assert.assertTrue(r.getBoolean("array"));
                    Assert.assertTrue(r.getBoolean("blob"));
                    Assert.assertFalse(r.getBoolean("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getBoolean(null));

                    Assert.assertFalse(r.getBoolean("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBoolean() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertFalse(r.getBoolean(0));
                    Assert.assertTrue(r.getBoolean(1));
                    Assert.assertFalse(r.getBoolean(2));
                    Assert.assertTrue(r.getBoolean(3));
                    Assert.assertFalse(r.getBoolean(4));
                    Assert.assertTrue(r.getBoolean(5));
                    Assert.assertTrue(r.getBoolean(6));
                    Assert.assertTrue(r.getBoolean(7));
                    Assert.assertTrue(r.getBoolean(8));
                    Assert.assertTrue(r.getBoolean(9));
                    Assert.assertTrue(r.getBoolean(10));
                    Assert.assertTrue(r.getBoolean(11));
                    Assert.assertFalse(r.getBoolean(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getBoolean(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getBoolean(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDateByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getDate("null"));
                    Assert.assertNull(r.getDate("true"));
                    Assert.assertNull(r.getDate("false"));
                    Assert.assertNull(r.getDate("string"));
                    Assert.assertNull(r.getDate("zero"));
                    Assert.assertNull(r.getDate("one"));
                    Assert.assertNull(r.getDate("minus_one"));
                    Assert.assertNull(r.getDate("one_dot_one"));
                    Assert.assertEquals(TEST_DATE, JSONUtils.toJSONString(r.getDate("date")));
                    Assert.assertNull(r.getDate("dict"));
                    Assert.assertNull(r.getDate("array"));
                    Assert.assertNull(r.getDate("blob"));
                    Assert.assertNull(r.getDate("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getDate(null));

                    Assert.assertNull(r.getDate("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDate() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getDate(0));
                    Assert.assertNull(r.getDate(1));
                    Assert.assertNull(r.getDate(2));
                    Assert.assertNull(r.getDate(3));
                    Assert.assertNull(r.getDate(4));
                    Assert.assertNull(r.getDate(5));
                    Assert.assertNull(r.getDate(6));
                    Assert.assertNull(r.getDate(7));
                    Assert.assertEquals(TEST_DATE, JSONUtils.toJSONString(r.getDate(8)));
                    Assert.assertNull(r.getDate(9));
                    Assert.assertNull(r.getDate(10));
                    Assert.assertNull(r.getDate(11));
                    Assert.assertNull(r.getDate(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getDate(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getDate(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBlobByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getBlob("null"));
                    Assert.assertNull(r.getBlob("true"));
                    Assert.assertNull(r.getBlob("false"));
                    Assert.assertNull(r.getBlob("string"));
                    Assert.assertNull(r.getBlob("zero"));
                    Assert.assertNull(r.getBlob("one"));
                    Assert.assertNull(r.getBlob("minus_one"));
                    Assert.assertNull(r.getBlob("one_dot_one"));
                    Assert.assertNull(r.getBlob("date"));
                    Assert.assertNull(r.getBlob("dict"));
                    Assert.assertNull(r.getBlob("array"));
                    Assert.assertEquals(BLOB_CONTENT, new String(r.getBlob("blob").getContent()));
                    Assert.assertArrayEquals(
                        BLOB_CONTENT.getBytes(StandardCharsets.UTF_8),
                        r.getBlob("blob").getContent());
                    Assert.assertNull(r.getBlob("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getBlob(null));

                    Assert.assertNull(r.getBlob("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBlob() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getBlob(0));
                    Assert.assertNull(r.getBlob(1));
                    Assert.assertNull(r.getBlob(2));
                    Assert.assertNull(r.getBlob(3));
                    Assert.assertNull(r.getBlob(4));
                    Assert.assertNull(r.getBlob(5));
                    Assert.assertNull(r.getBlob(6));
                    Assert.assertNull(r.getBlob(7));
                    Assert.assertNull(r.getBlob(8));
                    Assert.assertNull(r.getBlob(9));
                    Assert.assertNull(r.getBlob(10));
                    Assert.assertEquals(BLOB_CONTENT, new String(r.getBlob(11).getContent()));
                    Assert.assertArrayEquals(
                        BLOB_CONTENT.getBytes(StandardCharsets.UTF_8),
                        r.getBlob(11).getContent());
                    Assert.assertNull(r.getBlob(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getBlob(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getBlob(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDictionaryByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getDictionary("null"));
                    Assert.assertNull(r.getDictionary("true"));
                    Assert.assertNull(r.getDictionary("false"));
                    Assert.assertNull(r.getDictionary("string"));
                    Assert.assertNull(r.getDictionary("zero"));
                    Assert.assertNull(r.getDictionary("one"));
                    Assert.assertNull(r.getDictionary("minus_one"));
                    Assert.assertNull(r.getDictionary("one_dot_one"));
                    Assert.assertNull(r.getDictionary("date"));
                    Assert.assertNotNull(r.getDictionary("dict"));
                    Map<String, Object> dict = new HashMap<>();
                    dict.put("street", "1 Main street");
                    dict.put("city", "Mountain View");
                    dict.put("state", "CA");
                    Assert.assertEquals(dict, r.getDictionary("dict").toMap());
                    Assert.assertNull(r.getDictionary("array"));
                    Assert.assertNull(r.getDictionary("blob"));
                    Assert.assertNull(r.getDictionary("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getDictionary(null));

                    Assert.assertNull(r.getDictionary("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDictionary() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getDictionary(0));
                    Assert.assertNull(r.getDictionary(1));
                    Assert.assertNull(r.getDictionary(2));
                    Assert.assertNull(r.getDictionary(3));
                    Assert.assertNull(r.getDictionary(4));
                    Assert.assertNull(r.getDictionary(5));
                    Assert.assertNull(r.getDictionary(6));
                    Assert.assertNull(r.getDictionary(7));
                    Assert.assertNull(r.getDictionary(8));
                    Assert.assertNotNull(r.getDictionary(9));
                    Map<String, Object> dict = new HashMap<>();
                    dict.put("street", "1 Main street");
                    dict.put("city", "Mountain View");
                    dict.put("state", "CA");
                    Assert.assertEquals(dict, r.getDictionary(9).toMap());
                    Assert.assertNull(r.getDictionary(10));
                    Assert.assertNull(r.getDictionary(11));
                    Assert.assertNull(r.getDictionary(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getDictionary(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getDictionary(100));
                });

            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetArrayByKey() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getArray("null"));
                    Assert.assertNull(r.getArray("true"));
                    Assert.assertNull(r.getArray("false"));
                    Assert.assertNull(r.getArray("string"));
                    Assert.assertNull(r.getArray("zero"));
                    Assert.assertNull(r.getArray("one"));
                    Assert.assertNull(r.getArray("minus_one"));
                    Assert.assertNull(r.getArray("one_dot_one"));
                    Assert.assertNull(r.getArray("date"));
                    Assert.assertNull(r.getArray("dict"));
                    Assert.assertNotNull(r.getArray("array"));
                    List<Object> list = Arrays.asList("650-123-0001", "650-123-0002");
                    Assert.assertEquals(list, r.getArray("array").toList());
                    Assert.assertNull(r.getArray("blob"));
                    Assert.assertNull(r.getArray("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.getArray(null));

                    Assert.assertNull(r.getArray("not_in_query_select"));
                });
            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetArray() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    Assert.assertEquals(13, r.count());

                    Assert.assertNull(r.getArray(0));
                    Assert.assertNull(r.getArray(1));
                    Assert.assertNull(r.getArray(2));
                    Assert.assertNull(r.getArray(3));
                    Assert.assertNull(r.getArray(4));
                    Assert.assertNull(r.getArray(5));
                    Assert.assertNull(r.getArray(6));
                    Assert.assertNull(r.getArray(7));
                    Assert.assertNull(r.getArray(8));
                    Assert.assertNull(r.getArray(9));
                    Assert.assertNotNull(r.getArray(10));
                    List<Object> list = Arrays.asList("650-123-0001", "650-123-0002");
                    Assert.assertEquals(list, r.getArray(10).toList());
                    Assert.assertNull(r.getArray(11));
                    Assert.assertNull(r.getArray(12));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getArray(-1));

                    Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getArray(100));
                });
            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testGetKeys() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    List<String> keys = r.getKeys();
                    Assert.assertNotNull(keys);
                    Assert.assertEquals(13, keys.size());
                    Collections.sort(keys);
                    List<String> expected = Arrays.asList(
                        "null",
                        "true",
                        "false",
                        "string",
                        "zero",
                        "one",
                        "minus_one",
                        "one_dot_one",
                        "date",
                        "dict",
                        "array",
                        "blob",
                        "non_existing_key");
                    Collections.sort(expected);
                    Assert.assertEquals(expected, keys);

                    // Result.iterator() test
                    Iterator<String> itr = r.iterator();
                    int i1 = 0;
                    while (itr.hasNext()) {
                        Assert.assertTrue(expected.contains(itr.next()));
                        i1++;
                    }

                    Assert.assertEquals(expected.size(), i1);
                });
            Assert.assertEquals(1, rows);
        }
    }

    @Test
    public void testContains() {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);
            Query query = generateQuery(docID);

            int rows = verifyQueryWithEnumerator(
                query, (n, r) -> {
                    // exists -> true
                    List<String> expected = Arrays.asList(
                        "null", "true", "false", "string", "zero", "one",
                        "minus_one", "one_dot_one", "date", "dict", "array",
                        "blob");
                    for (String key: expected) { Assert.assertTrue(r.contains(key)); }
                    // not exists -> false
                    Assert.assertFalse(r.contains("non_existing_key"));

                    Assert.assertThrows(IllegalArgumentException.class, () -> r.contains(null));

                    Assert.assertFalse(r.contains("not_in_query_select"));
                });

            Assert.assertEquals(1, rows);
        }
    }

    // Contributed by Bryan Welter:
    // https://github.com/couchbase/couchbase-lite-android-ce/issues/27
    @Test
    public void testEmptyDict() throws CouchbaseLiteException {
        String docId = docId();
        String key = getUniqueName("emptyDict");

        MutableDocument mDoc = new MutableDocument(docId);
        mDoc.setDictionary(key, new MutableDictionary());
        saveDocInTestCollection(mDoc);

        final Query query = QueryBuilder.select(SelectResult.property(key))
            .from(DataSource.collection(getTestCollection()))
            .where(Meta.id.equalTo(Expression.string(docId)));

        try (ResultSet results = query.execute()) {
            Assert.assertNotNull(results);
            for (Result result: results.allResults()) {
                Assert.assertNotNull(result);
                Assert.assertEquals(1, result.toMap().size());
                Dictionary emptyDict = result.getDictionary(key);
                Assert.assertNotNull(emptyDict);
                Assert.assertTrue(emptyDict.isEmpty());
            }
        }
    }

    @Test
    public void testResultRefAfterClose() throws CouchbaseLiteException {
        MutableDocument mDoc = makeDocument();
        saveDocInTestCollection(mDoc);

        final Collection testCollection = getTestCollection();
        final Result result;
        final Dictionary dict;
        final Array array;
        final ResultSet results = QueryBuilder
            .createQuery("SELECT * FROM " + testCollection.getFullName(), testCollection.getDatabase())
            .execute();

        result = results.next();
        Assert.assertNotNull(result);

        dict = result.getDictionary(0);
        Assert.assertNotNull(dict);

        array = dict.getArray("doc-25");
        Assert.assertNotNull(array);

        Object val = array.getString(20);
        Assert.assertNotNull(val);

        results.close();

        Assert.assertNull(results.next());
        Assert.assertThrows(CouchbaseLiteError.class, () -> result.getDictionary(0));
        Assert.assertThrows(CouchbaseLiteError.class, () -> dict.getArray("doc-25"));
        Assert.assertThrows(CouchbaseLiteError.class, () -> array.getString(20));
    }


    /// ////////////  JSON tests

    // JSON 3.8
    @Test
    public void testResultToJSON() throws CouchbaseLiteException, JSONException {
        for (int i = 0; i < 5; i++) {
            MutableDocument mDoc = makeDocument();
            mDoc.setString("id", "jsonQuery-" + i);
            saveDocInTestCollection(mDoc);
        }

        SelectResult[] projection = new SelectResult[29];
        // `makeDocument` creates a document with 29 properties named doc-1 through doc-29
        for (int i = 1; i <= 29; i++) { projection[i - 1] = SelectResult.property("doc-" + i); }

        try (ResultSet results = QueryBuilder.select(projection)
            .from(DataSource.collection(getTestCollection()))
            .where(Expression.property("id").equalTo(Expression.string("jsonQuery-4")))
            .execute()) {

            Result result = results.next();
            Assert.assertNotNull(result);
            Assert.assertNull(results.next());

            verifyDocument(result, false);
            verifyDocument(new JSONObject(result.toJSON()));
        }
    }


    /// ////////////  Tooling

    // !!! Should be using the standard data tools
    private String docId() { return BaseTest.getUniqueName("doc"); }

    private String docId(int i) { return String.format(Locale.ENGLISH, "doc-%03d", i); }

    private String prepareData(int i) {
        MutableDocument mDoc = new MutableDocument(docId(i));
        if (i % 2 == 1) { populateData(mDoc); }
        else { populateDataByTypedSetter(mDoc); }
        saveDocInTestCollection(mDoc);
        return mDoc.getId();
    }

    private void populateData(MutableDocument doc) {
        doc.setValue("true", true);
        doc.setValue("false", false);
        doc.setValue("string", "string");
        doc.setValue("zero", 0);
        doc.setValue("one", 1);
        doc.setValue("minus_one", -1);
        doc.setValue("one_dot_one", 1.1);
        doc.setValue("date", JSONUtils.toDate(TEST_DATE));
        doc.setValue("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setValue("street", "1 Main street");
        dict.setValue("city", "Mountain View");
        dict.setValue("state", "CA");
        doc.setValue("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);

        // Blob:
        doc.setValue("blob", new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    private void populateDataByTypedSetter(MutableDocument doc) {
        doc.setBoolean("true", true);
        doc.setBoolean("false", false);
        doc.setString("string", "string");
        doc.setNumber("zero", 0);
        doc.setInt("one", 1);
        doc.setLong("minus_one", -1);
        doc.setDouble("one_dot_one", 1.1);
        doc.setDate("date", JSONUtils.toDate(TEST_DATE));
        doc.setString("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setString("street", "1 Main street");
        dict.setString("city", "Mountain View");
        dict.setString("state", "CA");
        doc.setDictionary("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addString("650-123-0001");
        array.addString("650-123-0002");
        doc.setArray("array", array);

        // Blob:
        doc.setValue("blob", new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    private Query generateQuery(String docID) { return generateQuery(docID, getTestCollection()); }

    private Query generateQuery(String docID, Collection collection) {
        return QueryBuilder.select(
                SelectResult.property("null"),
                SelectResult.property("true"),
                SelectResult.property("false"),
                SelectResult.property("string"),
                SelectResult.property("zero"),
                SelectResult.property("one"),
                SelectResult.property("minus_one"),
                SelectResult.property("one_dot_one"),
                SelectResult.property("date"),
                SelectResult.property("dict"),
                SelectResult.property("array"),
                SelectResult.property("blob"),
                SelectResult.property("non_existing_key"))
            .from(DataSource.collection(collection))
            .where(Meta.id.equalTo(Expression.string(docID)));
    }
}
