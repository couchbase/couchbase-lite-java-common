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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.StringUtils;
import com.couchbase.lite.internal.utils.VerySlowTest;


// Tests for the Document Iterator tests are in IteratorTest
@SuppressWarnings("ConstantConditions")
public class DocumentTest extends BaseDbTest {
    @FunctionalInterface
    interface DocValidator extends Fn.ConsumerThrows<Document, CouchbaseLiteException> { }

    @Test
    public void testCreateDoc() {
        MutableDocument doc1a = new MutableDocument();
        Assert.assertNotNull(doc1a);
        Assert.assertFalse(doc1a.getId().isEmpty());
        Assert.assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        Document doc1b = saveDocInTestCollection(doc1a);
        Assert.assertNotNull(doc1b);
        Assert.assertNotSame(doc1a, doc1b);
        Assert.assertTrue(doc1b.exists());
        Assert.assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithID() {
        MutableDocument doc1a = new MutableDocument("doc1");
        Assert.assertNotNull(doc1a);
        Assert.assertEquals("doc1", doc1a.getId());
        Assert.assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        Document doc1b = saveDocInTestCollection(doc1a);
        Assert.assertNotNull(doc1b);
        Assert.assertNotSame(doc1a, doc1b);
        Assert.assertTrue(doc1b.exists());
        Assert.assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithEmptyStringID() {
        final MutableDocument doc1a = new MutableDocument("");
        Assert.assertNotNull(doc1a);
        assertThrowsCBLException(
            CBLError.Domain.CBLITE,
            CBLError.Code.BAD_DOC_ID,
            () -> getTestCollection().save(doc1a));
    }

    @Test
    public void testCreateDocWithNilID() {
        MutableDocument doc1a = new MutableDocument((String) null);
        Assert.assertNotNull(doc1a);
        Assert.assertFalse(doc1a.getId().isEmpty());
        Assert.assertEquals(new HashMap<String, Object>(), doc1a.toMap());

        Document doc1b = saveDocInTestCollection(doc1a);
        Assert.assertNotNull(doc1b);
        Assert.assertNotSame(doc1a, doc1b);
        Assert.assertTrue(doc1b.exists());
        Assert.assertEquals(doc1a.getId(), doc1b.getId());
    }

    @Test
    public void testCreateDocWithDict() {
        final Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30L);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        final MutableDocument doc1a = new MutableDocument(dict);
        Assert.assertNotNull(doc1a);
        Assert.assertFalse(doc1a.getId().isEmpty());
        Assert.assertEquals(dict, doc1a.toMap());

        Document doc1b = saveDocInTestCollection(doc1a);
        Assert.assertNotNull(doc1b);
        Assert.assertNotSame(doc1a, doc1b);
        Assert.assertTrue(doc1b.exists());
        Assert.assertEquals(doc1a.getId(), doc1b.getId());
        Assert.assertEquals(dict, doc1b.toMap());
    }

    @Test
    public void testCreateDocWithIDAndDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30L);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        MutableDocument doc1a = new MutableDocument("doc1", dict);
        Assert.assertNotNull(doc1a);
        Assert.assertEquals("doc1", doc1a.getId());
        Assert.assertEquals(dict, doc1a.toMap());

        Document doc1b = saveDocInTestCollection(doc1a);
        Assert.assertNotNull(doc1b);
        Assert.assertNotSame(doc1a, doc1b);
        Assert.assertTrue(doc1b.exists());
        Assert.assertEquals(doc1a.getId(), doc1b.getId());
        Assert.assertEquals(dict, doc1b.toMap());
    }

    @Test
    public void testSetDictionaryContent() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "Scott Tiger");
        dict.put("age", 30L);

        Map<String, Object> address = new HashMap<>();
        address.put("street", "1 Main street");
        address.put("city", "Mountain View");
        address.put("state", "CA");
        dict.put("address", address);

        dict.put("phones", Arrays.asList("650-123-0001", "650-123-0002"));

        MutableDocument doc = new MutableDocument("doc1");
        doc.setData(dict);
        Assert.assertEquals(dict, doc.toMap());

        Document savedDoc = saveDocInTestCollection(doc);
        //doc = db.getDocument("doc1");
        Assert.assertEquals(dict, savedDoc.toMap());

        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("name", "Danial Tiger");
        nuDict.put("age", 32L);

        Map<String, Object> nuAddress = new HashMap<>();
        nuAddress.put("street", "2 Main street");
        nuAddress.put("city", "Palo Alto");
        nuAddress.put("state", "CA");
        nuDict.put("address", nuAddress);

        nuDict.put("phones", Arrays.asList("650-234-0001", "650-234-0002"));

        doc = savedDoc.toMutable();
        doc.setData(nuDict);
        Assert.assertEquals(nuDict, doc.toMap());

        savedDoc = saveDocInTestCollection(doc);
        Assert.assertEquals(nuDict, savedDoc.toMap());
    }

    @Test
    public final void testMutateEmptyDocument() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        getTestCollection().save(doc);

        doc = getTestCollection().getDocument(doc.getId()).toMutable();
        doc.setString("foo", "bar");
        getTestCollection().save(doc);
    }

    @Test
    public void testGetValueFromDocument() {
        MutableDocument doc = new MutableDocument("doc1");
        validateAndSaveDocInTestCollection(
            doc, d -> {
                Assert.assertEquals(0, d.getInt(TEST_DOC_TAG_KEY));
                Assert.assertEquals(0.0f, d.getFloat(TEST_DOC_TAG_KEY), 0.0f);
                Assert.assertEquals(0.0, d.getDouble(TEST_DOC_TAG_KEY), 0.0);
                Assert.assertFalse(d.getBoolean(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getBlob(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getDate(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getNumber(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getValue(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getString(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getArray(TEST_DOC_TAG_KEY));
                Assert.assertNull(d.getDictionary(TEST_DOC_TAG_KEY));
                Assert.assertEquals(new HashMap<String, Object>(), d.toMap());
            });
    }

    @Test
    public void testSaveThenGetFromAnotherDB() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setValue("name", "Scott Tiger");
        saveDocInTestCollection(doc1a);

        try (Database anotherDb = duplicateDb(getTestDatabase())) {
            Document doc1b = BaseDbTestKt.getSimilarCollection(anotherDb, getTestCollection()).getDocument("doc1");
            Assert.assertNotSame(doc1a, doc1b);
            Assert.assertEquals(doc1a.getId(), doc1b.getId());
            Assert.assertEquals(doc1a.toMap(), doc1b.toMap());
        }
    }

    @Test
    public void testNoCacheNoLive() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("doc1");
        doc1a.setValue("name", "Scott Tiger");

        saveDocInTestCollection(doc1a);

        Document doc1b = getTestCollection().getDocument("doc1");
        Document doc1c = getTestCollection().getDocument("doc1");

        try (Database anotherDb = duplicateDb(getTestDatabase())) {
            Document doc1d = BaseDbTestKt.getSimilarCollection(anotherDb, getTestCollection()).getDocument("doc1");

            Assert.assertNotSame(doc1a, doc1b);
            Assert.assertNotSame(doc1a, doc1c);
            Assert.assertNotSame(doc1a, doc1d);
            Assert.assertNotSame(doc1b, doc1c);
            Assert.assertNotSame(doc1b, doc1d);
            Assert.assertNotSame(doc1c, doc1d);

            Assert.assertEquals(doc1a.toMap(), doc1b.toMap());
            Assert.assertEquals(doc1a.toMap(), doc1c.toMap());
            Assert.assertEquals(doc1a.toMap(), doc1d.toMap());

            MutableDocument mDoc1b = doc1b.toMutable();
            mDoc1b.setValue("name", "Daniel Tiger");
            doc1b = saveDocInTestCollection(mDoc1b);

            Assert.assertNotEquals(doc1b.toMap(), doc1a.toMap());
            Assert.assertNotEquals(doc1b.toMap(), doc1c.toMap());
            Assert.assertNotEquals(doc1b.toMap(), doc1d.toMap());
        }
    }

    @Test
    public void testSetString() {
        DocValidator validator4Save = d -> {
            Assert.assertEquals("", d.getValue("string1"));
            Assert.assertEquals("string", d.getValue("string2"));
        };

        DocValidator validator4SUpdate = d -> {
            Assert.assertEquals("string", d.getValue("string1"));
            Assert.assertEquals("", d.getValue("string2"));
        };

        // -- setValue
        // save
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("string1", "");
        mDoc.setValue("string2", "string");
        Document doc = validateAndSaveDocInTestCollection(mDoc, validator4Save);
        // update
        mDoc = doc.toMutable();
        mDoc.setValue("string1", "string");
        mDoc.setValue("string2", "");
        validateAndSaveDocInTestCollection(mDoc, validator4SUpdate);

        // -- setString
        // save
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setString("string1", "");
        mDoc2.setString("string2", "string");
        Document doc2 = validateAndSaveDocInTestCollection(mDoc2, validator4Save);

        // update
        mDoc2 = doc2.toMutable();
        mDoc2.setString("string1", "string");
        mDoc2.setString("string2", "");
        validateAndSaveDocInTestCollection(mDoc2, validator4SUpdate);
    }

    @Test
    public void testGetString() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertNull(d.getString("null"));
                    Assert.assertNull(d.getString("true"));
                    Assert.assertNull(d.getString("false"));
                    Assert.assertEquals("string", d.getString("string"));
                    Assert.assertNull(d.getString("zero"));
                    Assert.assertNull(d.getString("one"));
                    Assert.assertNull(d.getString("minus_one"));
                    Assert.assertNull(d.getString("one_dot_one"));
                    Assert.assertEquals(TEST_DATE, d.getString("date"));
                    Assert.assertNull(d.getString("dict"));
                    Assert.assertNull(d.getString("array"));
                    Assert.assertNull(d.getString("blob"));
                    Assert.assertNull(d.getString("non_existing_key"));
                });
        }
    }

    @Test
    public void testSetNumber() {
        DocValidator validator4Save = d -> {
            Assert.assertEquals(1, ((Number) d.getValue("number1")).intValue());
            Assert.assertEquals(0, ((Number) d.getValue("number2")).intValue());
            Assert.assertEquals(-1, ((Number) d.getValue("number3")).intValue());
            Assert.assertEquals(-10, ((Number) d.getValue("number4")).intValue());
        };

        DocValidator validator4SUpdate = d -> {
            Assert.assertEquals(0, ((Number) d.getValue("number1")).intValue());
            Assert.assertEquals(1, ((Number) d.getValue("number2")).intValue());
            Assert.assertEquals(-10, ((Number) d.getValue("number3")).intValue());
            Assert.assertEquals(-1, ((Number) d.getValue("number4")).intValue());
        };

        // -- setValue
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("number1", 1);
        mDoc.setValue("number2", 0);
        mDoc.setValue("number3", -1);
        mDoc.setValue("number4", -10);
        Document doc = validateAndSaveDocInTestCollection(mDoc, validator4Save);

        // Update:
        mDoc = doc.toMutable();
        mDoc.setValue("number1", 0);
        mDoc.setValue("number2", 1);
        mDoc.setValue("number3", -10);
        mDoc.setValue("number4", -1);
        validateAndSaveDocInTestCollection(mDoc, validator4SUpdate);

        // -- setNumber
        // save
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setNumber("number1", 1);
        mDoc2.setNumber("number2", 0);
        mDoc2.setNumber("number3", -1);
        mDoc2.setNumber("number4", -10);
        Document doc2 = validateAndSaveDocInTestCollection(mDoc2, validator4Save);

        // Update:
        mDoc2 = doc2.toMutable();
        mDoc2.setNumber("number1", 0);
        mDoc2.setNumber("number2", 1);
        mDoc2.setNumber("number3", -10);
        mDoc2.setNumber("number4", -1);
        validateAndSaveDocInTestCollection(mDoc2, validator4SUpdate);

        // -- setInt
        // save
        MutableDocument mDoc3 = new MutableDocument("doc3");
        mDoc3.setInt("number1", 1);
        mDoc3.setInt("number2", 0);
        mDoc3.setInt("number3", -1);
        mDoc3.setInt("number4", -10);
        Document doc3 = validateAndSaveDocInTestCollection(mDoc3, validator4Save);

        // Update:
        mDoc3 = doc3.toMutable();
        mDoc3.setInt("number1", 0);
        mDoc3.setInt("number2", 1);
        mDoc3.setInt("number3", -10);
        mDoc3.setInt("number4", -1);
        validateAndSaveDocInTestCollection(mDoc3, validator4SUpdate);

        // -- setLong
        // save
        MutableDocument mDoc4 = new MutableDocument("doc4");
        mDoc4.setLong("number1", 1);
        mDoc4.setLong("number2", 0);
        mDoc4.setLong("number3", -1);
        mDoc4.setLong("number4", -10);
        Document doc4 = validateAndSaveDocInTestCollection(mDoc4, validator4Save);

        // Update:
        mDoc4 = doc4.toMutable();
        mDoc4.setLong("number1", 0);
        mDoc4.setLong("number2", 1);
        mDoc4.setLong("number3", -10);
        mDoc4.setLong("number4", -1);
        validateAndSaveDocInTestCollection(mDoc4, validator4SUpdate);

        // -- setFloat
        // save
        MutableDocument mDoc5 = new MutableDocument("doc5");
        mDoc5.setFloat("number1", 1);
        mDoc5.setFloat("number2", 0);
        mDoc5.setFloat("number3", -1);
        mDoc5.setFloat("number4", -10);
        Document doc5 = validateAndSaveDocInTestCollection(mDoc5, validator4Save);

        // Update:
        mDoc5 = doc5.toMutable();
        mDoc5.setFloat("number1", 0);
        mDoc5.setFloat("number2", 1);
        mDoc5.setFloat("number3", -10);
        mDoc5.setFloat("number4", -1);
        validateAndSaveDocInTestCollection(mDoc5, validator4SUpdate);

        // -- setDouble
        // save
        MutableDocument mDoc6 = new MutableDocument("doc6");
        mDoc6.setDouble("number1", 1);
        mDoc6.setDouble("number2", 0);
        mDoc6.setDouble("number3", -1);
        mDoc6.setDouble("number4", -10);
        Document doc6 = validateAndSaveDocInTestCollection(mDoc6, validator4Save);

        // Update:
        mDoc6 = doc6.toMutable();
        mDoc6.setDouble("number1", 0);
        mDoc6.setDouble("number2", 1);
        mDoc6.setDouble("number3", -10);
        mDoc6.setDouble("number4", -1);
        validateAndSaveDocInTestCollection(mDoc6, validator4SUpdate);
    }

    @Test
    public void testGetNumber() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertNull(d.getNumber("null"));
                    Assert.assertEquals(1, d.getNumber("true").intValue());
                    Assert.assertEquals(0, d.getNumber("false").intValue());
                    Assert.assertNull(d.getNumber("string"));
                    Assert.assertEquals(0, d.getNumber("zero").intValue());
                    Assert.assertEquals(1, d.getNumber("one").intValue());
                    Assert.assertEquals(-1, d.getNumber("minus_one").intValue());
                    Assert.assertEquals(1.1, d.getNumber("one_dot_one"));
                    Assert.assertNull(d.getNumber("date"));
                    Assert.assertNull(d.getNumber("dict"));
                    Assert.assertNull(d.getNumber("array"));
                    Assert.assertNull(d.getNumber("blob"));
                    Assert.assertNull(d.getNumber("non_existing_key"));
                });
        }
    }

    @Test
    public void testGetInteger() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertEquals(0, d.getInt("null"));
                    Assert.assertEquals(1, d.getInt("true"));
                    Assert.assertEquals(0, d.getInt("false"));
                    Assert.assertEquals(0, d.getInt("string"));
                    Assert.assertEquals(0, d.getInt("zero"));
                    Assert.assertEquals(1, d.getInt("one"));
                    Assert.assertEquals(-1, d.getInt("minus_one"));
                    Assert.assertEquals(1, d.getInt("one_dot_one"));
                    Assert.assertEquals(0, d.getInt("date"));
                    Assert.assertEquals(0, d.getInt("dict"));
                    Assert.assertEquals(0, d.getInt("array"));
                    Assert.assertEquals(0, d.getInt("blob"));
                    Assert.assertEquals(0, d.getInt("non_existing_key"));
                });
        }
    }

    @Test
    public void testGetLong() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertEquals(0, d.getLong("null"));
                    Assert.assertEquals(1, d.getLong("true"));
                    Assert.assertEquals(0, d.getLong("false"));
                    Assert.assertEquals(0, d.getLong("string"));
                    Assert.assertEquals(0, d.getLong("zero"));
                    Assert.assertEquals(1, d.getLong("one"));
                    Assert.assertEquals(-1, d.getLong("minus_one"));
                    Assert.assertEquals(1, d.getLong("one_dot_one"));
                    Assert.assertEquals(0, d.getLong("date"));
                    Assert.assertEquals(0, d.getLong("dict"));
                    Assert.assertEquals(0, d.getLong("array"));
                    Assert.assertEquals(0, d.getLong("blob"));
                    Assert.assertEquals(0, d.getLong("non_existing_key"));
                });
        }
    }

    @Test
    public void testGetFloat() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertEquals(0.0f, d.getFloat("null"), 0.0f);
                    Assert.assertEquals(1.0f, d.getFloat("true"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("false"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("string"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("zero"), 0.0f);
                    Assert.assertEquals(1.0f, d.getFloat("one"), 0.0f);
                    Assert.assertEquals(-1.0f, d.getFloat("minus_one"), 0.0f);
                    Assert.assertEquals(1.1f, d.getFloat("one_dot_one"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("date"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("dict"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("array"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("blob"), 0.0f);
                    Assert.assertEquals(0.0f, d.getFloat("non_existing_key"), 0.0f);
                });
        }
    }

    @Test
    public void testGetDouble() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertEquals(0.0, d.getDouble("null"), 0.0);
                    Assert.assertEquals(1.0, d.getDouble("true"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("false"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("string"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("zero"), 0.0);
                    Assert.assertEquals(1.0, d.getDouble("one"), 0.0);
                    Assert.assertEquals(-1.0, d.getDouble("minus_one"), 0.0);
                    Assert.assertEquals(1.1, d.getDouble("one_dot_one"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("date"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("dict"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("array"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("blob"), 0.0);
                    Assert.assertEquals(0.0, d.getDouble("non_existing_key"), 0.0);
                });
        }
    }

    @Test
    public void testSetGetMinMaxNumbers() {
        DocValidator validator = doc -> {
            Assert.assertEquals(Integer.MIN_VALUE, doc.getNumber("min_int").intValue());
            Assert.assertEquals(Integer.MAX_VALUE, doc.getNumber("max_int").intValue());
            Assert.assertEquals(Integer.MIN_VALUE, ((Number) doc.getValue("min_int")).intValue());
            Assert.assertEquals(Integer.MAX_VALUE, ((Number) doc.getValue("max_int")).intValue());
            Assert.assertEquals(Integer.MIN_VALUE, doc.getInt("min_int"));
            Assert.assertEquals(Integer.MAX_VALUE, doc.getInt("max_int"));

            Assert.assertEquals(Long.MIN_VALUE, doc.getNumber("min_long"));
            Assert.assertEquals(Long.MAX_VALUE, doc.getNumber("max_long"));
            Assert.assertEquals(Long.MIN_VALUE, doc.getValue("min_long"));
            Assert.assertEquals(Long.MAX_VALUE, doc.getValue("max_long"));
            Assert.assertEquals(Long.MIN_VALUE, doc.getLong("min_long"));
            Assert.assertEquals(Long.MAX_VALUE, doc.getLong("max_long"));

            Assert.assertEquals(Float.MIN_VALUE, doc.getNumber("min_float"));
            Assert.assertEquals(Float.MAX_VALUE, doc.getNumber("max_float"));
            Assert.assertEquals(Float.MIN_VALUE, doc.getValue("min_float"));
            Assert.assertEquals(Float.MAX_VALUE, doc.getValue("max_float"));
            Assert.assertEquals(Float.MIN_VALUE, doc.getFloat("min_float"), 0.0f);
            Assert.assertEquals(Float.MAX_VALUE, doc.getFloat("max_float"), 0.0f);

            Assert.assertEquals(Double.MIN_VALUE, doc.getNumber("min_double"));
            Assert.assertEquals(Double.MAX_VALUE, doc.getNumber("max_double"));
            Assert.assertEquals(Double.MIN_VALUE, doc.getValue("min_double"));
            Assert.assertEquals(Double.MAX_VALUE, doc.getValue("max_double"));
            Assert.assertEquals(Double.MIN_VALUE, doc.getDouble("min_double"), 0.0);
            Assert.assertEquals(Double.MAX_VALUE, doc.getDouble("max_double"), 0.0);
        };

        // -- setValue
        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("min_int", Integer.MIN_VALUE);
        doc.setValue("max_int", Integer.MAX_VALUE);
        doc.setValue("min_long", Long.MIN_VALUE);
        doc.setValue("max_long", Long.MAX_VALUE);
        doc.setValue("min_float", Float.MIN_VALUE);
        doc.setValue("max_float", Float.MAX_VALUE);
        doc.setValue("min_double", Double.MIN_VALUE);
        doc.setValue("max_double", Double.MAX_VALUE);
        validateAndSaveDocInTestCollection(doc, validator);

        // -- setInt, setLong, setFloat, setDouble
        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setInt("min_int", Integer.MIN_VALUE);
        doc2.setInt("max_int", Integer.MAX_VALUE);
        doc2.setLong("min_long", Long.MIN_VALUE);
        doc2.setLong("max_long", Long.MAX_VALUE);
        doc2.setFloat("min_float", Float.MIN_VALUE);
        doc2.setFloat("max_float", Float.MAX_VALUE);
        doc2.setDouble("min_double", Double.MIN_VALUE);
        doc2.setDouble("max_double", Double.MAX_VALUE);
        validateAndSaveDocInTestCollection(doc2, validator);
    }


    @Test
    public void testSetGetFloatNumbers() {
        DocValidator validator = doc -> {
            Assert.assertEquals(1.00, ((Number) doc.getValue("number1")).doubleValue(), 0.00001);
            Assert.assertEquals(1.00, doc.getNumber("number1").doubleValue(), 0.00001);
            Assert.assertEquals(1, doc.getInt("number1"));
            Assert.assertEquals(1L, doc.getLong("number1"));
            Assert.assertEquals(1.00F, doc.getFloat("number1"), 0.00001F);
            Assert.assertEquals(1.00, doc.getDouble("number1"), 0.00001);

            Assert.assertEquals(1.49, ((Number) doc.getValue("number2")).doubleValue(), 0.00001);
            Assert.assertEquals(1.49, doc.getNumber("number2").doubleValue(), 0.00001);
            Assert.assertEquals(1, doc.getInt("number2"));
            Assert.assertEquals(1L, doc.getLong("number2"));
            Assert.assertEquals(1.49F, doc.getFloat("number2"), 0.00001F);
            Assert.assertEquals(1.49, doc.getDouble("number2"), 0.00001);

            Assert.assertEquals(1.50, ((Number) doc.getValue("number3")).doubleValue(), 0.00001);
            Assert.assertEquals(1.50, doc.getNumber("number3").doubleValue(), 0.00001);
            Assert.assertEquals(1, doc.getInt("number3"));
            Assert.assertEquals(1L, doc.getLong("number3"));
            Assert.assertEquals(1.50F, doc.getFloat("number3"), 0.00001F);
            Assert.assertEquals(1.50, doc.getDouble("number3"), 0.00001);

            Assert.assertEquals(1.51, ((Number) doc.getValue("number4")).doubleValue(), 0.00001);
            Assert.assertEquals(1.51, doc.getNumber("number4").doubleValue(), 0.00001);
            Assert.assertEquals(1, doc.getInt("number4"));
            Assert.assertEquals(1L, doc.getLong("number4"));
            Assert.assertEquals(1.51F, doc.getFloat("number4"), 0.00001F);
            Assert.assertEquals(1.51, doc.getDouble("number4"), 0.00001);

            Assert.assertEquals(1.99, ((Number) doc.getValue("number5")).doubleValue(), 0.00001);// return 1
            Assert.assertEquals(1.99, doc.getNumber("number5").doubleValue(), 0.00001);  // return 1
            Assert.assertEquals(1, doc.getInt("number5"));
            Assert.assertEquals(1L, doc.getLong("number5"));
            Assert.assertEquals(1.99F, doc.getFloat("number5"), 0.00001F);
            Assert.assertEquals(1.99, doc.getDouble("number5"), 0.00001);
        };


        // -- setValue
        MutableDocument doc = new MutableDocument("doc1");

        doc.setValue("number1", 1.00);
        doc.setValue("number2", 1.49);
        doc.setValue("number3", 1.50);
        doc.setValue("number4", 1.51);
        doc.setValue("number5", 1.99);
        validateAndSaveDocInTestCollection(doc, validator);

        // -- setFloat
        MutableDocument doc2 = new MutableDocument("doc2");
        doc2.setFloat("number1", 1.00f);
        doc2.setFloat("number2", 1.49f);
        doc2.setFloat("number3", 1.50f);
        doc2.setFloat("number4", 1.51f);
        doc2.setFloat("number5", 1.99f);
        validateAndSaveDocInTestCollection(doc2, validator);

        // -- setDouble
        MutableDocument doc3 = new MutableDocument("doc3");
        doc3.setDouble("number1", 1.00);
        doc3.setDouble("number2", 1.49);
        doc3.setDouble("number3", 1.50);
        doc3.setDouble("number4", 1.51);
        doc3.setDouble("number5", 1.99);
        validateAndSaveDocInTestCollection(doc3, validator);
    }

    @Test
    public void testSetBoolean() {
        DocValidator validator4Save = d -> {
            Assert.assertEquals(true, d.getValue("boolean1"));
            Assert.assertEquals(false, d.getValue("boolean2"));
            Assert.assertTrue(d.getBoolean("boolean1"));
            Assert.assertFalse(d.getBoolean("boolean2"));
        };
        DocValidator validator4Update = d -> {
            Assert.assertEquals(false, d.getValue("boolean1"));
            Assert.assertEquals(true, d.getValue("boolean2"));
            Assert.assertFalse(d.getBoolean("boolean1"));
            Assert.assertTrue(d.getBoolean("boolean2"));
        };

        // -- setValue
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("boolean1", true);
        mDoc.setValue("boolean2", false);
        Document doc = validateAndSaveDocInTestCollection(mDoc, validator4Save);

        // Update:
        mDoc = doc.toMutable();
        mDoc.setValue("boolean1", false);
        mDoc.setValue("boolean2", true);
        validateAndSaveDocInTestCollection(mDoc, validator4Update);

        // -- setBoolean
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setValue("boolean1", true);
        mDoc2.setValue("boolean2", false);
        Document doc2 = validateAndSaveDocInTestCollection(mDoc2, validator4Save);

        // Update:
        mDoc2 = doc2.toMutable();
        mDoc2.setValue("boolean1", false);
        mDoc2.setValue("boolean2", true);
        validateAndSaveDocInTestCollection(mDoc2, validator4Update);
    }

    @Test
    public void testGetBoolean() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertFalse(d.getBoolean("null"));
                    Assert.assertTrue(d.getBoolean("true"));
                    Assert.assertFalse(d.getBoolean("false"));
                    Assert.assertTrue(d.getBoolean("string"));
                    Assert.assertFalse(d.getBoolean("zero"));
                    Assert.assertTrue(d.getBoolean("one"));
                    Assert.assertTrue(d.getBoolean("minus_one"));
                    Assert.assertTrue(d.getBoolean("one_dot_one"));
                    Assert.assertTrue(d.getBoolean("date"));
                    Assert.assertTrue(d.getBoolean("dict"));
                    Assert.assertTrue(d.getBoolean("array"));
                    Assert.assertTrue(d.getBoolean("blob"));
                    Assert.assertFalse(d.getBoolean("non_existing_key"));
                });
        }
    }

    @Test
    public void testSetDate() {
        MutableDocument mDoc = new MutableDocument("doc1");

        Date date = new Date();
        final String dateStr = JSONUtils.toJSONString(date);
        Assert.assertFalse(dateStr.isEmpty());
        mDoc.setValue("date", date);

        Document doc = validateAndSaveDocInTestCollection(
            mDoc, d -> {
                Assert.assertEquals(dateStr, d.getValue("date"));
                Assert.assertEquals(dateStr, d.getString("date"));
                Assert.assertEquals(dateStr, JSONUtils.toJSONString(d.getDate("date")));
            });

        // Update:
        mDoc = doc.toMutable();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, 60);
        Date nuDate = cal.getTime();
        final String nuDateStr = JSONUtils.toJSONString(nuDate);
        mDoc.setValue("date", nuDate);

        validateAndSaveDocInTestCollection(
            mDoc, d -> {
                Assert.assertEquals(nuDateStr, d.getValue("date"));
                Assert.assertEquals(nuDateStr, d.getString("date"));
                Assert.assertEquals(nuDateStr, JSONUtils.toJSONString(d.getDate("date")));
            });
    }

    @Test
    public void testGetDate() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertNull(d.getDate("null"));
                    Assert.assertNull(d.getDate("true"));
                    Assert.assertNull(d.getDate("false"));
                    Assert.assertNull(d.getDate("string"));
                    Assert.assertNull(d.getDate("zero"));
                    Assert.assertNull(d.getDate("one"));
                    Assert.assertNull(d.getDate("minus_one"));
                    Assert.assertNull(d.getDate("one_dot_one"));
                    Assert.assertEquals(TEST_DATE, JSONUtils.toJSONString(d.getDate("date")));
                    Assert.assertNull(d.getDate("dict"));
                    Assert.assertNull(d.getDate("array"));
                    Assert.assertNull(d.getDate("blob"));
                    Assert.assertNull(d.getDate("non_existing_key"));
                });
        }
    }

    @Test
    public void testSetBlob() {
        final String newBlobContent = StringUtils.randomString(100);
        final Blob newBlob = new Blob("text/plain", newBlobContent.getBytes(StandardCharsets.UTF_8));
        final Blob blob = new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8));

        DocValidator validator4Save = d -> {
            Assert.assertEquals(blob.getProperties().get("length"), d.getBlob("blob").getProperties().get("length"));
            Assert.assertEquals(
                blob.getProperties().get("content-type"),
                d.getBlob("blob").getProperties().get("content-type"));
            Assert.assertEquals(blob.getProperties().get("digest"), d.getBlob("blob").getProperties().get("digest"));
            Assert.assertEquals(
                blob.getProperties().get("length"),
                ((Blob) d.getValue("blob")).getProperties().get("length"));
            Assert.assertEquals(
                blob.getProperties().get("content-type"),
                ((Blob) d.getValue("blob")).getProperties().get("content-type"));
            Assert.assertEquals(
                blob.getProperties().get("digest"),
                ((Blob) d.getValue("blob")).getProperties().get("digest"));
            Assert.assertEquals(BLOB_CONTENT, new String(d.getBlob("blob").getContent()));
            Assert.assertArrayEquals(
                BLOB_CONTENT.getBytes(StandardCharsets.UTF_8),
                d.getBlob("blob").getContent());
        };

        DocValidator validator4Update = d -> {
            Assert.assertEquals(newBlob.getProperties().get("length"), d.getBlob("blob").getProperties().get("length"));
            Assert.assertEquals(
                newBlob.getProperties().get("content-type"),
                d.getBlob("blob").getProperties().get("content-type"));
            Assert.assertEquals(newBlob.getProperties().get("digest"), d.getBlob("blob").getProperties().get("digest"));
            Assert.assertEquals(
                newBlob.getProperties().get("length"),
                ((Blob) d.getValue("blob")).getProperties().get("length"));
            Assert.assertEquals(
                newBlob.getProperties().get("content-type"),
                ((Blob) d.getValue("blob")).getProperties().get("content-type"));
            Assert.assertEquals(
                newBlob.getProperties().get("digest"),
                ((Blob) d.getValue("blob")).getProperties().get("digest"));
            Assert.assertEquals(newBlobContent, new String(d.getBlob("blob").getContent()));
            Assert.assertArrayEquals(newBlobContent.getBytes(StandardCharsets.UTF_8), d.getBlob("blob").getContent());
        };

        // --setValue
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("blob", blob);
        Document doc = validateAndSaveDocInTestCollection(mDoc, validator4Save);

        // Update:
        mDoc = doc.toMutable();
        mDoc.setValue("blob", newBlob);
        validateAndSaveDocInTestCollection(mDoc, validator4Update);

        // --setBlob
        MutableDocument mDoc2 = new MutableDocument("doc2");
        mDoc2.setBlob("blob", blob);
        Document doc2 = validateAndSaveDocInTestCollection(mDoc2, validator4Save);

        // Update:
        mDoc2 = doc2.toMutable();
        mDoc2.setBlob("blob", newBlob);
        validateAndSaveDocInTestCollection(mDoc2, validator4Update);
    }

    @Test
    public void testGetBlob() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertNull(d.getBlob("null"));
                    Assert.assertNull(d.getBlob("true"));
                    Assert.assertNull(d.getBlob("false"));
                    Assert.assertNull(d.getBlob("string"));
                    Assert.assertNull(d.getBlob("zero"));
                    Assert.assertNull(d.getBlob("one"));
                    Assert.assertNull(d.getBlob("minus_one"));
                    Assert.assertNull(d.getBlob("one_dot_one"));
                    Assert.assertNull(d.getBlob("date"));
                    Assert.assertNull(d.getBlob("dict"));
                    Assert.assertNull(d.getBlob("array"));
                    Assert.assertEquals(BLOB_CONTENT, new String(d.getBlob("blob").getContent()));
                    Assert.assertArrayEquals(
                        BLOB_CONTENT.getBytes(StandardCharsets.UTF_8),
                        d.getBlob("blob").getContent());
                    Assert.assertNull(d.getBlob("non_existing_key"));
                });
        }
    }

    @Test
    public void testSetDictionary() {
        for (int i = 1; i <= 2; i++) {
            // -- setValue
            MutableDocument mDoc = new MutableDocument();
            MutableDictionary mDict = new MutableDictionary();
            mDict.setValue("street", "1 Main street");
            if (i % 2 == 1) { mDoc.setValue("dict", mDict); }
            else { mDoc.setDictionary("dict", mDict); }
            Assert.assertEquals(mDict, mDoc.getValue("dict"));
            Assert.assertEquals(mDict.toMap(), ((MutableDictionary) mDoc.getValue("dict")).toMap());

            Document doc = saveDocInTestCollection(mDoc);

            Assert.assertNotSame(mDict, doc.getValue("dict"));
            Assert.assertEquals(doc.getValue("dict"), doc.getDictionary("dict"));

            Dictionary dict = (Dictionary) doc.getValue("dict");
            dict = dict instanceof MutableDictionary ? dict : dict.toMutable();
            Assert.assertEquals(mDict.toMap(), dict.toMap());

            // Update:
            mDoc = doc.toMutable();
            mDict = mDoc.getDictionary("dict");
            mDict.setValue("city", "Mountain View");
            Assert.assertEquals(doc.getValue("dict"), doc.getDictionary("dict"));
            Map<String, Object> map = new HashMap<>();
            map.put("street", "1 Main street");
            map.put("city", "Mountain View");
            Assert.assertEquals(map, mDoc.getDictionary("dict").toMap());

            doc = saveDocInTestCollection(mDoc);

            Assert.assertNotSame(mDict, doc.getValue("dict"));
            Assert.assertEquals(doc.getValue("dict"), doc.getDictionary("dict"));
            Assert.assertEquals(map, doc.getDictionary("dict").toMap());
        }
    }

    @Test
    public void testGetDictionary() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertNull(d.getDictionary("null"));
                    Assert.assertNull(d.getDictionary("true"));
                    Assert.assertNull(d.getDictionary("false"));
                    Assert.assertNull(d.getDictionary("string"));
                    Assert.assertNull(d.getDictionary("zero"));
                    Assert.assertNull(d.getDictionary("one"));
                    Assert.assertNull(d.getDictionary("minus_one"));
                    Assert.assertNull(d.getDictionary("one_dot_one"));
                    Assert.assertNull(d.getDictionary("date"));
                    Assert.assertNotNull(d.getDictionary("dict"));
                    Map<String, Object> dict = new HashMap<>();
                    dict.put("street", "1 Main street");
                    dict.put("city", "Mountain View");
                    dict.put("state", "CA");
                    Assert.assertEquals(dict, d.getDictionary("dict").toMap());
                    Assert.assertNull(d.getDictionary("array"));
                    Assert.assertNull(d.getDictionary("blob"));
                    Assert.assertNull(d.getDictionary("non_existing_key"));
                });
        }
    }

    @Test
    public void testSetArray() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument mDoc = new MutableDocument();
            MutableArray array = new MutableArray();
            array.addValue("item1");
            array.addValue("item2");
            array.addValue("item3");
            if (i % 2 == 1) { mDoc.setValue("array", array); }
            else { mDoc.setArray("array", array); }
            Assert.assertEquals(array, mDoc.getValue("array"));
            Assert.assertEquals(array.toList(), ((MutableArray) mDoc.getValue("array")).toList());

            Document doc = saveDocInTestCollection(mDoc);
            Assert.assertNotSame(array, doc.getValue("array"));
            Assert.assertEquals(doc.getValue("array"), doc.getArray("array"));

            Array mArray = (Array) doc.getValue("array");
            mArray = mArray instanceof MutableArray ? mArray : mArray.toMutable();
            Assert.assertEquals(array.toList(), mArray.toList());

            // Update:
            mDoc = doc.toMutable();
            array = mDoc.getArray("array");
            array.addValue("item4");
            array.addValue("item5");
            doc = saveDocInTestCollection(mDoc);
            Assert.assertNotSame(array, doc.getValue("array"));
            Assert.assertEquals(doc.getValue("array"), doc.getArray("array"));
            List<String> list = Arrays.asList("item1", "item2", "item3", "item4", "item5");
            Assert.assertEquals(list, doc.getArray("array").toList());
        }
    }

    @Test
    public void testGetArray() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }
            validateAndSaveDocInTestCollection(
                doc, d -> {
                    Assert.assertNull(d.getArray("null"));
                    Assert.assertNull(d.getArray("true"));
                    Assert.assertNull(d.getArray("false"));
                    Assert.assertNull(d.getArray("string"));
                    Assert.assertNull(d.getArray("zero"));
                    Assert.assertNull(d.getArray("one"));
                    Assert.assertNull(d.getArray("minus_one"));
                    Assert.assertNull(d.getArray("one_dot_one"));
                    Assert.assertNull(d.getArray("date"));
                    Assert.assertNull(d.getArray("dict"));
                    Assert.assertNotNull(d.getArray("array"));
                    List<Object> list = Arrays.asList("650-123-0001", "650-123-0002");
                    Assert.assertEquals(list, d.getArray("array").toList());
                    Assert.assertNull(d.getArray("blob"));
                    Assert.assertNull(d.getArray("non_existing_key"));
                });
        }
    }

    @Test
    public void testSetNull() {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("obj-null", null);
        mDoc.setString("string-null", null);
        mDoc.setNumber("number-null", null);
        mDoc.setDate("date-null", null);
        mDoc.setArray("array-null", null);
        mDoc.setDictionary("dict-null", null);
        // TODO: NOTE: Current implementation follows iOS way. So set null remove it!!
        validateAndSaveDocInTestCollection(
            mDoc, d -> {
                Assert.assertEquals(6, d.count());
                Assert.assertTrue(d.contains("obj-null"));
                Assert.assertTrue(d.contains("string-null"));
                Assert.assertTrue(d.contains("number-null"));
                Assert.assertTrue(d.contains("date-null"));
                Assert.assertTrue(d.contains("array-null"));
                Assert.assertTrue(d.contains("dict-null"));
                Assert.assertNull(d.getValue("obj-null"));
                Assert.assertNull(d.getValue("string-null"));
                Assert.assertNull(d.getValue("number-null"));
                Assert.assertNull(d.getValue("date-null"));
                Assert.assertNull(d.getValue("array-null"));
                Assert.assertNull(d.getValue("dict-null"));
            });
    }

    @Test
    public void testSetMap() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("address", dict);

        MutableDictionary address = doc.getDictionary("address");
        Assert.assertNotNull(address);
        Assert.assertEquals(address, doc.getValue("address"));
        Assert.assertEquals("1 Main street", address.getString("street"));
        Assert.assertEquals("Mountain View", address.getString("city"));
        Assert.assertEquals("CA", address.getString("state"));
        Assert.assertEquals(dict, address.toMap());

        // Update with a new dictionary:
        Map<String, Object> nuDict = new HashMap<>();
        nuDict.put("street", "1 Second street");
        nuDict.put("city", "Palo Alto");
        nuDict.put("state", "CA");
        doc.setValue("address", nuDict);

        // Check whether the old address dictionary is still accessible:
        Assert.assertNotSame(address, doc.getDictionary("address"));
        Assert.assertEquals("1 Main street", address.getString("street"));
        Assert.assertEquals("Mountain View", address.getString("city"));
        Assert.assertEquals("CA", address.getString("state"));
        Assert.assertEquals(dict, address.toMap());

        // The old address dictionary should be detached:
        MutableDictionary nuAddress = doc.getDictionary("address");
        Assert.assertNotSame(address, nuAddress);

        // Update nuAddress:
        nuAddress.setValue("zip", "94302");
        Assert.assertEquals("94302", nuAddress.getString("zip"));
        Assert.assertNull(address.getString("zip"));

        // Save:
        Document savedDoc = saveDocInTestCollection(doc);

        nuDict.put("zip", "94302");
        Map<String, Object> expected = new HashMap<>();
        expected.put("address", nuDict);
        Assert.assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testSetList() {
        List<String> array = Arrays.asList("a", "b", "c");

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("members", array);

        MutableArray members = doc.getArray("members");
        Assert.assertNotNull(members);
        Assert.assertEquals(members, doc.getValue("members"));

        Assert.assertEquals(3, members.count());
        Assert.assertEquals("a", members.getValue(0));
        Assert.assertEquals("b", members.getValue(1));
        Assert.assertEquals("c", members.getValue(2));
        Assert.assertEquals(array, members.toList());

        // Update with a new array:
        List<String> nuArray = Arrays.asList("d", "e", "f");
        doc.setValue("members", nuArray);

        // Check whether the old members array is still accessible:
        Assert.assertEquals(3, members.count());
        Assert.assertEquals("a", members.getValue(0));
        Assert.assertEquals("b", members.getValue(1));
        Assert.assertEquals("c", members.getValue(2));
        Assert.assertEquals(array, members.toList());

        // The old members array should be detached:
        MutableArray nuMembers = doc.getArray("members");
        Assert.assertNotSame(members, nuMembers);

        // Update nuMembers:
        nuMembers.addValue("g");
        Assert.assertEquals(4, nuMembers.count());
        Assert.assertEquals("g", nuMembers.getValue(3));
        Assert.assertEquals(3, members.count());

        // Save
        Document savedDoc = saveDocInTestCollection(doc);

        Map<String, Object> expected = new HashMap<>();
        expected.put("members", Arrays.asList("d", "e", "f", "g"));
        Assert.assertEquals(expected, savedDoc.toMap());
    }

    @Test
    public void testUpdateNestedDictionary() {
        MutableDocument doc = new MutableDocument("doc1");
        MutableDictionary addresses = new MutableDictionary();
        doc.setValue("addresses", addresses);

        MutableDictionary shipping = new MutableDictionary();
        shipping.setValue("street", "1 Main street");
        shipping.setValue("city", "Mountain View");
        shipping.setValue("state", "CA");
        addresses.setValue("shipping", shipping);

        doc = saveDocInTestCollection(doc).toMutable();

        shipping = doc.getDictionary("addresses").getDictionary("shipping");
        shipping.setValue("zip", "94042");

        doc = saveDocInTestCollection(doc).toMutable();

        Map<String, Object> mapShipping = new HashMap<>();
        mapShipping.put("street", "1 Main street");
        mapShipping.put("city", "Mountain View");
        mapShipping.put("state", "CA");
        mapShipping.put("zip", "94042");
        Map<String, Object> mapAddresses = new HashMap<>();
        mapAddresses.put("shipping", mapShipping);
        Map<String, Object> expected = new HashMap<>();
        expected.put("addresses", mapAddresses);

        Assert.assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateDictionaryInArray() {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray addresses = new MutableArray();
        doc.setValue("addresses", addresses);

        MutableDictionary address1 = new MutableDictionary();
        address1.setValue("street", "1 Main street");
        address1.setValue("city", "Mountain View");
        address1.setValue("state", "CA");
        addresses.addValue(address1);

        MutableDictionary address2 = new MutableDictionary();
        address2.setValue("street", "1 Second street");
        address2.setValue("city", "Palo Alto");
        address2.setValue("state", "CA");
        addresses.addValue(address2);

        doc = saveDocInTestCollection(doc).toMutable();

        address1 = doc.getArray("addresses").getDictionary(0);
        address1.setValue("street", "2 Main street");
        address1.setValue("zip", "94042");

        address2 = doc.getArray("addresses").getDictionary(1);
        address2.setValue("street", "2 Second street");
        address2.setValue("zip", "94302");

        doc = saveDocInTestCollection(doc).toMutable();

        Map<String, Object> mapAddress1 = new HashMap<>();
        mapAddress1.put("street", "2 Main street");
        mapAddress1.put("city", "Mountain View");
        mapAddress1.put("state", "CA");
        mapAddress1.put("zip", "94042");

        Map<String, Object> mapAddress2 = new HashMap<>();
        mapAddress2.put("street", "2 Second street");
        mapAddress2.put("city", "Palo Alto");
        mapAddress2.put("state", "CA");
        mapAddress2.put("zip", "94302");

        Map<String, Object> expected = new HashMap<>();
        expected.put("addresses", Arrays.asList(mapAddress1, mapAddress2));

        Assert.assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateNestedArray() {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray groups = new MutableArray();
        doc.setValue("groups", groups);

        MutableArray group1 = new MutableArray();
        group1.addValue("a");
        group1.addValue("b");
        group1.addValue("c");
        groups.addValue(group1);

        MutableArray group2 = new MutableArray();
        group2.addValue(1);
        group2.addValue(2);
        group2.addValue(3);
        groups.addValue(group2);

        doc = saveDocInTestCollection(doc).toMutable();

        group1 = doc.getArray("groups").getArray(0);
        group1.setValue(0, "d");
        group1.setValue(1, "e");
        group1.setValue(2, "f");

        group2 = doc.getArray("groups").getArray(1);
        group2.setValue(0, 4);
        group2.setValue(1, 5);
        group2.setValue(2, 6);

        doc = saveDocInTestCollection(doc).toMutable();

        Map<String, Object> expected = new HashMap<>();
        expected.put(
            "groups", Arrays.asList(
                Arrays.asList("d", "e", "f"),
                Arrays.asList(4L, 5L, 6L)));
        Assert.assertEquals(expected, doc.toMap());
    }

    @Test
    public void testUpdateArrayInDictionary() {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary group1 = new MutableDictionary();
        MutableArray member1 = new MutableArray();
        member1.addValue("a");
        member1.addValue("b");
        member1.addValue("c");
        group1.setValue("member", member1);
        doc.setValue("group1", group1);

        MutableDictionary group2 = new MutableDictionary();
        MutableArray member2 = new MutableArray();
        member2.addValue(1);
        member2.addValue(2);
        member2.addValue(3);
        group2.setValue("member", member2);
        doc.setValue("group2", group2);

        doc = saveDocInTestCollection(doc).toMutable();

        member1 = doc.getDictionary("group1").getArray("member");
        member1.setValue(0, "d");
        member1.setValue(1, "e");
        member1.setValue(2, "f");

        member2 = doc.getDictionary("group2").getArray("member");
        member2.setValue(0, 4);
        member2.setValue(1, 5);
        member2.setValue(2, 6);

        doc = saveDocInTestCollection(doc).toMutable();

        Map<String, Object> expected = new HashMap<>();
        Map<String, Object> mapGroup1 = new HashMap<>();
        mapGroup1.put("member", Arrays.asList("d", "e", "f"));
        Map<String, Object> mapGroup2 = new HashMap<>();
        mapGroup2.put("member", Arrays.asList(4L, 5L, 6L));
        expected.put("group1", mapGroup1);
        expected.put("group2", mapGroup2);
        Assert.assertEquals(expected, doc.toMap());
    }

    @Test
    public void testSetDictionaryToMultipleKeys() {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary address = new MutableDictionary();
        address.setValue("street", "1 Main street");
        address.setValue("city", "Mountain View");
        address.setValue("state", "CA");
        doc.setValue("shipping", address);
        doc.setValue("billing", address);

        // Update address: both shipping and billing should get the update.
        address.setValue("zip", "94042");
        Assert.assertEquals("94042", doc.getDictionary("shipping").getString("zip"));
        Assert.assertEquals("94042", doc.getDictionary("billing").getString("zip"));

        doc = saveDocInTestCollection(doc).toMutable();

        MutableDictionary shipping = doc.getDictionary("shipping");
        MutableDictionary billing = doc.getDictionary("billing");

        // After save: both shipping and billing address are now independent to each other
        Assert.assertNotSame(shipping, address);
        Assert.assertNotSame(billing, address);
        Assert.assertNotSame(shipping, billing);

        shipping.setValue("street", "2 Main street");
        billing.setValue("street", "3 Main street");

        // Save update:
        doc = saveDocInTestCollection(doc).toMutable();
        Assert.assertEquals("2 Main street", doc.getDictionary("shipping").getString("street"));
        Assert.assertEquals("3 Main street", doc.getDictionary("billing").getString("street"));
    }

    @Test
    public void testSetArrayToMultipleKeys() {
        MutableDocument doc = new MutableDocument("doc1");

        MutableArray phones = new MutableArray();
        phones.addValue("650-000-0001");
        phones.addValue("650-000-0002");

        doc.setValue("mobile", phones);
        doc.setValue("home", phones);

        Assert.assertEquals(phones, doc.getValue("mobile"));
        Assert.assertEquals(phones, doc.getValue("home"));

        // Update phones: both mobile and home should get the update
        phones.addValue("650-000-0003");

        Assert.assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003"),
            doc.getArray("mobile").toList());
        Assert.assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003"),
            doc.getArray("home").toList());

        doc = saveDocInTestCollection(doc).toMutable();

        // After save: both mobile and home are not independent to each other
        MutableArray mobile = doc.getArray("mobile");
        MutableArray home = doc.getArray("home");
        Assert.assertNotSame(mobile, phones);
        Assert.assertNotSame(home, phones);
        Assert.assertNotSame(mobile, home);

        // Update mobile and home:
        mobile.addValue("650-000-1234");
        home.addValue("650-000-5678");

        // Save update:
        doc = saveDocInTestCollection(doc).toMutable();

        Assert.assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003", "650-000-1234"),
            doc.getArray("mobile").toList());
        Assert.assertEquals(
            Arrays.asList("650-000-0001", "650-000-0002", "650-000-0003", "650-000-5678"),
            doc.getArray("home").toList());
    }

    @Test
    public void testToDictionary() {
        MutableDocument doc1 = new MutableDocument("doc1");
        populateData(doc1);

        Map<String, Object> expected = new HashMap<>();
        expected.put("true", true);
        expected.put("false", false);
        expected.put("string", "string");
        expected.put("zero", 0);
        expected.put("one", 1);
        expected.put("minus_one", -1);
        expected.put("one_dot_one", 1.1);
        expected.put("date", TEST_DATE); // expect the stringified date
        expected.put("null", null);

        // Dictionary:
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");
        expected.put("dict", dict);

        // Array:
        List<Object> array = new ArrayList<>();
        array.add("650-123-0001");
        array.add("650-123-0002");
        expected.put("array", array);

        // Blob:
        expected.put("blob", makeBlob());

        Assert.assertEquals(expected, doc1.toMap());
    }

    @Test
    public void testCount() {
        for (int i = 1; i <= 2; i++) {
            MutableDocument doc = new MutableDocument();
            if (i % 2 == 1) { populateData(doc); }
            else { populateDataByTypedSetter(doc); }

            Assert.assertEquals(12, doc.count());
            Assert.assertEquals(12, doc.toMap().size());

            doc = saveDocInTestCollection(doc).toMutable();

            Assert.assertEquals(12, doc.count());
            Assert.assertEquals(12, doc.toMap().size());
        }
    }

    @Test
    public void testRemoveKeys() {
        MutableDocument doc = new MutableDocument("doc1");
        Map<String, Object> mapAddress = new HashMap<>();
        mapAddress.put("street", "1 milky way.");
        mapAddress.put("city", "galaxy city");
        mapAddress.put("zip", 12345);
        Map<String, Object> profile = new HashMap<>();
        profile.put("type", "profile");
        profile.put("name", "Jason");
        profile.put("weight", 130.5);
        profile.put("active", true);
        profile.put("age", 30);
        profile.put("address", mapAddress);
        doc.setData(profile);

        saveDocInTestCollection(doc);

        doc.remove("name");
        doc.remove("weight");
        doc.remove("age");
        doc.remove("active");
        doc.getDictionary("address").remove("city");

        Assert.assertNull(doc.getString("name"));
        Assert.assertEquals(0.0F, doc.getFloat("weight"), 0.0F);
        Assert.assertEquals(0.0, doc.getDouble("weight"), 0.0);
        Assert.assertEquals(0, doc.getInt("age"));
        Assert.assertFalse(doc.getBoolean("active"));

        Assert.assertNull(doc.getValue("name"));
        Assert.assertNull(doc.getValue("weight"));
        Assert.assertNull(doc.getValue("age"));
        Assert.assertNull(doc.getValue("active"));
        Assert.assertNull(doc.getDictionary("address").getValue("city"));

        MutableDictionary address = doc.getDictionary("address");
        Map<String, Object> addr = new HashMap<>();
        addr.put("street", "1 milky way.");
        addr.put("zip", 12345);
        Assert.assertEquals(addr, address.toMap());
        Map<String, Object> expected = new HashMap<>();
        expected.put("type", "profile");
        expected.put("address", addr);
        Assert.assertEquals(expected, doc.toMap());

        doc.remove("type");
        doc.remove("address");
        Assert.assertNull(doc.getValue("type"));
        Assert.assertNull(doc.getValue("address"));
        Assert.assertEquals(new HashMap<String, Object>(), doc.toMap());
    }

    @Test
    public void testRemoveKeysBySettingDictionary() throws CouchbaseLiteException {
        Map<String, Object> props = new HashMap<>();
        props.put("PropName1", "Val1");
        props.put("PropName2", 42);

        MutableDocument mDoc = new MutableDocument("docName", props);
        saveDocInTestCollection(mDoc);

        Map<String, Object> newProps = new HashMap<>();
        newProps.put("PropName3", "Val3");
        newProps.put("PropName4", 84);

        MutableDocument existingDoc = getTestCollection().getDocument("docName").toMutable();
        existingDoc.setData(newProps);
        saveDocInTestCollection(existingDoc);

        Assert.assertEquals(newProps, existingDoc.toMap());
    }

    @Test
    public void testContainsKey() {
        MutableDocument doc = new MutableDocument("doc1");
        Map<String, Object> mapAddress = new HashMap<>();
        mapAddress.put("street", "1 milky way.");
        Map<String, Object> profile = new HashMap<>();
        profile.put("type", "profile");
        profile.put("name", "Jason");
        profile.put("age", 30);
        profile.put("address", mapAddress);
        doc.setData(profile);

        Assert.assertTrue(doc.contains("type"));
        Assert.assertTrue(doc.contains("name"));
        Assert.assertTrue(doc.contains("age"));
        Assert.assertTrue(doc.contains("address"));
        Assert.assertFalse(doc.contains("weight"));
    }

    @Test
    public void testDeleteNewDocument() {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setString("name", "Scott Tiger");

        assertThrowsCBLException(
            CBLError.Domain.CBLITE,
            CBLError.Code.NOT_FOUND,
            () -> getTestCollection().delete(mDoc));

        Assert.assertEquals("Scott Tiger", mDoc.getString("name"));
    }

    @Test
    public void testDeleteDocument() throws CouchbaseLiteException {
        String docID = "doc1";
        MutableDocument mDoc = new MutableDocument(docID);
        mDoc.setValue("name", "Scott Tiger");

        // Save:
        Document doc = saveDocInTestCollection(mDoc);

        // Delete:
        getTestCollection().delete(doc);

        Assert.assertNull(getTestCollection().getDocument(docID));

        // NOTE: doc is reserved.
        Object v = doc.getValue("name");
        Assert.assertEquals("Scott Tiger", v);
        Map<String, Object> expected = new HashMap<>();
        expected.put("name", "Scott Tiger");
        Assert.assertEquals(expected, doc.toMap());
    }

    @Test
    public void testDictionaryAfterDeleteDocument() throws CouchbaseLiteException {
        Map<String, Object> addr = new HashMap<>();
        addr.put("street", "1 Main street");
        addr.put("city", "Mountain View");
        addr.put("state", "CA");
        Map<String, Object> dict = new HashMap<>();
        dict.put("address", addr);

        MutableDocument mDoc = new MutableDocument("doc1", dict);
        Document doc = saveDocInTestCollection(mDoc);

        Dictionary address = doc.getDictionary("address");
        Assert.assertEquals("1 Main street", address.getValue("street"));
        Assert.assertEquals("Mountain View", address.getValue("city"));
        Assert.assertEquals("CA", address.getValue("state"));

        getTestCollection().delete(doc);

        // The dictionary still has data but is detached:
        Assert.assertEquals("1 Main street", address.getValue("street"));
        Assert.assertEquals("Mountain View", address.getValue("city"));
        Assert.assertEquals("CA", address.getValue("state"));
    }

    @Test
    public void testArrayAfterDeleteDocument() throws CouchbaseLiteException {
        Map<String, Object> dict = new HashMap<>();
        dict.put("members", Arrays.asList("a", "b", "c"));

        MutableDocument mDoc = new MutableDocument("doc1", dict);
        Document doc = saveDocInTestCollection(mDoc);

        Array members = doc.getArray("members");
        Assert.assertEquals(3, members.count());
        Assert.assertEquals("a", members.getValue(0));
        Assert.assertEquals("b", members.getValue(1));
        Assert.assertEquals("c", members.getValue(2));

        getTestCollection().delete(doc);

        // The array still has data but is detached:
        Assert.assertEquals(3, members.count());
        Assert.assertEquals("a", members.getValue(0));
        Assert.assertEquals("b", members.getValue(1));
        Assert.assertEquals("c", members.getValue(2));
    }

    @Test
    public void testDocumentChangeOnDocumentPurged() throws CouchbaseLiteException, InterruptedException {
        getTestCollection().save(new MutableDocument("doc1").setValue("theanswer", 18));

        final CountDownLatch latch = new CountDownLatch(1);
        try (ListenerToken ignore = getTestCollection().addDocumentChangeListener(
            "doc1",
            change -> {
                try {
                    Assert.assertNotNull(change);
                    Assert.assertEquals("doc1", change.getDocumentID());
                }
                finally { latch.countDown(); }
            })
        ) {
            getTestCollection().setDocumentExpiration("doc1", new Date(System.currentTimeMillis() + 100));
            Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testPurgeDocument() throws CouchbaseLiteException {
        final String docID = "doc1";
        final MutableDocument doc = new MutableDocument(docID);
        doc.setValue("type", "profile");
        doc.setValue("name", "Scott");

        // Purge before save:
        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND, () -> getTestCollection().purge(doc));

        Assert.assertEquals("profile", doc.getValue("type"));
        Assert.assertEquals("Scott", doc.getValue("name"));

        //Save
        Document savedDoc = saveDocInTestCollection(doc);

        // purge
        getTestCollection().purge(savedDoc);
        Assert.assertNull(getTestCollection().getDocument(docID));
    }

    @Test
    public void testPurgeDocumentById() throws CouchbaseLiteException {
        final String docID = "doc1";
        final MutableDocument doc = new MutableDocument(docID);
        doc.setValue("type", "profile");
        doc.setValue("name", "Scott");

        // Purge before save:
        assertThrowsCBLException(
            CBLError.Domain.CBLITE,
            CBLError.Code.NOT_FOUND,
            () -> getTestCollection().purge(docID));

        Assert.assertEquals("profile", doc.getValue("type"));
        Assert.assertEquals("Scott", doc.getValue("name"));

        //Save
        saveDocInTestCollection(doc);

        // purge
        getTestCollection().purge(docID);
        Assert.assertNull(getTestCollection().getDocument(docID));
    }

    @Test
    public void testSetAndGetExpirationFromDoc() throws CouchbaseLiteException {
        Date dto30 = new Date(System.currentTimeMillis() + 30000L);

        MutableDocument doc1a = new MutableDocument("doc1");
        MutableDocument doc1b = new MutableDocument("doc2");
        MutableDocument doc1c = new MutableDocument("doc3");
        doc1a.setInt("answer", 12);
        doc1a.setValue("question", "What is six plus six?");
        saveDocInTestCollection(doc1a);

        doc1b.setInt("answer", 22);
        doc1b.setValue("question", "What is eleven plus eleven?");
        saveDocInTestCollection(doc1b);

        doc1c.setInt("answer", 32);
        doc1c.setValue("question", "What is twenty plus twelve?");
        saveDocInTestCollection(doc1c);

        getTestCollection().setDocumentExpiration("doc1", dto30);
        getTestCollection().setDocumentExpiration("doc3", dto30);

        getTestCollection().setDocumentExpiration("doc3", null);
        Date exp = getTestCollection().getDocumentExpiration("doc1");
        Assert.assertEquals(exp, dto30);
        Assert.assertNull(getTestCollection().getDocumentExpiration("doc2"));
        Assert.assertNull(getTestCollection().getDocumentExpiration("doc3"));
    }

    @Test
    public void testSetExpirationOnDoc() throws CouchbaseLiteException {
        final long now = System.currentTimeMillis();
        final Collection testCollection = getTestCollection();

        MutableDocument doc1 = new MutableDocument();
        doc1.setInt("answer", 12);
        doc1.setValue("question", "What is six plus six?");
        saveDocInTestCollection(doc1);
        Assert.assertEquals(1, testCollection.getCount());

        MutableDocument doc2 = new MutableDocument();
        doc2.setInt("answer", 12);
        doc2.setValue("question", "What is six plus six?");
        saveDocInTestCollection(doc2);
        Assert.assertEquals(2, testCollection.getCount());

        testCollection.setDocumentExpiration(doc1.getId(), new Date(now + 100));
        testCollection.setDocumentExpiration(doc2.getId(), new Date(now + LONG_TIMEOUT_MS));

        Assert.assertEquals(2, testCollection.getCount());
        waitUntil(STD_TIMEOUT_MS, () -> 1 == testCollection.getCount());
    }

    @Test
    public void testSetExpirationOnDeletedDoc() throws CouchbaseLiteException {
        final Collection testCollection = getTestCollection();

        MutableDocument doc1a = new MutableDocument();
        doc1a.setInt("answer", 12);
        doc1a.setValue("question", "What is six plus six?");
        testCollection.save(doc1a);
        Assert.assertEquals(1, testCollection.getCount());

        final Query queryDeleted = QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .where(Meta.deleted);

        int n;
        try (ResultSet rs = queryDeleted.execute()) { n = rs.allResults().size(); }
        Assert.assertEquals(0, n);

        testCollection.delete(doc1a);
        Assert.assertEquals(0, testCollection.getCount());
        try (ResultSet rs = queryDeleted.execute()) { n = rs.allResults().size(); }
        Assert.assertEquals(1, n);

        testCollection.setDocumentExpiration(doc1a.getId(), new Date(System.currentTimeMillis() + 100L));

        waitUntil(
            STD_TIMEOUT_MS,
            () -> {
                if (0 != testCollection.getCount()) { return false; }
                try (ResultSet rs = queryDeleted.execute()) { return rs.allResults().isEmpty(); }
                catch (CouchbaseLiteException e) { Report.log(e, "Unexpected exception"); }
                return false;
            });
    }

    @Test
    public void testGetExpirationFromDeletedDoc() throws CouchbaseLiteException {
        MutableDocument doc1a = new MutableDocument("deleted_doc");
        doc1a.setInt("answer", 12);
        doc1a.setValue("question", "What is six plus six?");
        saveDocInTestCollection(doc1a);

        getTestCollection().delete(doc1a);

        Assert.assertNull(getTestCollection().getDocumentExpiration("deleted_doc"));
    }

    @Test
    public void testSetExpirationOnNoneExistDoc() {
        try {
            getTestCollection().setDocumentExpiration("not_exist", new Date(System.currentTimeMillis() + 30000L));
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) { Assert.assertEquals(CBLError.Code.NOT_FOUND, e.getCode()); }
    }

    @Test
    public void testGetExpirationFromNoneExistDoc() throws CouchbaseLiteException {
        Assert.assertNull(getTestCollection().getDocumentExpiration("not_exist"));
    }

    @Test
    public void testSetExpirationOnDocInDeletedCollection() {
        // add doc in collection
        String id = "test_doc";
        saveDocInTestCollection(new MutableDocument(id));

        BaseDbTestKt.delete(getTestCollection());

        try {
            getTestCollection().setDocumentExpiration(id, new Date(System.currentTimeMillis() + 30000L));
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) { Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode()); }
    }

    @Test
    public void testGetExpirationOnDocInDeletedCollection() throws CouchbaseLiteException {
        String id = "test_doc";
        saveDocInTestCollection(new MutableDocument(id));

        getTestCollection().setDocumentExpiration(id, new Date(System.currentTimeMillis() + 30000L));

        getTestCollection().getDatabase().deleteCollection(
            getTestCollection().getName(),
            getTestCollection().getScope().getName());

        try {
            getTestCollection().getDocumentExpiration(id);
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) { Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode()); }
    }

    @Test
    public void testSetExpirationDocInCollectionDeletedInDifferentDBInstance() {
        // add doc in collection
        String id = "test_doc";
        saveDocInTestCollection(new MutableDocument(id));

        try (Database otherDb = duplicateDb(getTestDatabase())) {
            otherDb.deleteCollection(getTestCollection().getName(), getTestCollection().getScope().getName());
            getTestCollection().setDocumentExpiration(id, new Date(System.currentTimeMillis() + 30000L));
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) { Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode()); }
    }

    @Test
    public void testGetExpirationOnDocInCollectionDeletedInDifferentDBInstance() throws CouchbaseLiteException {
        Date expiration = new Date(System.currentTimeMillis() + 30000L);
        // add doc in collection
        String id = "test_doc";
        MutableDocument document = new MutableDocument(id);
        saveDocInTestCollection(document);
        getTestCollection().setDocumentExpiration(id, expiration);

        try (Database otherDb = duplicateDb(getTestDatabase())) {
            otherDb.deleteCollection(getTestCollection().getName(), getTestCollection().getScope().getName());
            getTestCollection().getDocumentExpiration(id);
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) { Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode()); }
    }

    // Test setting expiration on doc in a collection of closed database throws CBLException
    @Test
    public void testSetExpirationOnDocInCollectionOfClosedDB() {
        Date expiration = new Date(System.currentTimeMillis() + 30000L);
        try {
            closeDb(getTestDatabase());
            getTestCollection().setDocumentExpiration("doc_id", expiration);
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) {
            Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode());
        }
    }

    // Test getting expiration on doc in a collection of closed database throws CBLException
    @Test
    public void testGetExpirationOnDocInACollectionOfClosedDatabase() throws CouchbaseLiteException {
        Date expiration = new Date(System.currentTimeMillis() + 30000L);
        // add doc in collection
        String id = "test_doc";
        MutableDocument document = new MutableDocument(id);
        saveDocInTestCollection(document);
        getTestCollection().setDocumentExpiration(id, expiration);
        getTestDatabase().deleteCollection(getTestCollection().getName(), getTestCollection().getScope().getName());

        try {
            getTestCollection().getDocumentExpiration(id);
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) {
            Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode());
        }
    }

    // Test setting expiration on doc in a collection of deleted database throws CBLException
    @Test
    public void testSetExpirationOnDocInCollectionOfDeletedDB() {
        Date expiration = new Date(System.currentTimeMillis() + 30000L);
        String id = "doc_id";
        MutableDocument document = new MutableDocument(id);
        saveDocInTestCollection(document);
        deleteDb(getTestDatabase());
        try {
            getTestCollection().setDocumentExpiration(id, expiration);
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) {
            Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode());
        }
    }

    // Test getting expiration on doc in a collection of deleted database throws CBLException
    @Test
    public void testGetExpirationOnDocInACollectionOfDeletedDatabase() throws CouchbaseLiteException {
        Date expiration = new Date(System.currentTimeMillis() + 30000L);
        // add doc in collection
        String id = "test_doc";
        MutableDocument document = new MutableDocument(id);
        saveDocInTestCollection(document);
        getTestCollection().setDocumentExpiration(id, expiration);
        deleteDb(getTestDatabase());
        try {
            getTestCollection().getDocumentExpiration(id);
            Assert.fail("Expect CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) {
            Assert.assertEquals(CBLError.Code.NOT_OPEN, e.getCode());
        }
    }

    @Test
    public void testLongExpiration() throws Exception {
        Date now = new Date(System.currentTimeMillis());
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.DATE, 60);
        Date d60Days = c.getTime();

        MutableDocument doc = new MutableDocument("doc");
        doc.setInt("answer", 42);
        doc.setValue("question", "What is twenty-one times two?");
        saveDocInTestCollection(doc);

        Assert.assertNull(getTestCollection().getDocumentExpiration("doc"));
        getTestCollection().setDocumentExpiration("doc", d60Days);

        Date exp = getTestCollection().getDocumentExpiration("doc");
        Assert.assertNotNull(exp);
        long diff = exp.getTime() - now.getTime();
        Assert.assertTrue(Math.abs(TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) - 60.0) <= 1.0);
    }

    @Test
    public void testReopenDB() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("string", "str");
        saveDocInTestCollection(mDoc);

        reopenTestDb();

        Document doc = getTestCollection().getDocument("doc1");
        Assert.assertEquals("str", doc.getString("string"));
        Map<String, Object> expected = new HashMap<>();
        expected.put("string", "str");
        Assert.assertEquals(expected, doc.toMap());
    }

    @Test
    public void testBlob() throws IOException {
        byte[] content = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);

        // store blob
        Blob data = new Blob("text/plain", content);
        Assert.assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("name", "Jim");
        doc.setValue("data", data);

        doc = saveDocInTestCollection(doc).toMutable();

        Assert.assertEquals("Jim", doc.getValue("name"));
        Assert.assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        Assert.assertEquals(BLOB_CONTENT.length(), data.length());
        Assert.assertArrayEquals(content, data.getContent());
        try (InputStream is = data.getContentStream()) {
            Assert.assertNotNull(is);
            byte[] buffer = new byte[content.length + 37];
            int bytesRead = is.read(buffer);
            Assert.assertEquals(content.length, bytesRead);
        }
    }

    @Test
    public void testEmptyBlob() throws IOException {
        byte[] content = "".getBytes(StandardCharsets.UTF_8);
        Blob data = new Blob("text/plain", content);
        Assert.assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("data", data);

        doc = saveDocInTestCollection(doc).toMutable();

        Assert.assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        Assert.assertEquals(0, data.length());
        Assert.assertArrayEquals(content, data.getContent());
        try (InputStream is = data.getContentStream()) {
            Assert.assertNotNull(is);
            byte[] buffer = new byte[37];
            int bytesRead = is.read(buffer);
            Assert.assertEquals(-1, bytesRead);
        }
    }

    @Test
    public void testBlobWithEmptyStream() throws IOException {
        MutableDocument doc = new MutableDocument("doc1");
        byte[] content = "".getBytes(StandardCharsets.UTF_8);
        try (InputStream stream = new ByteArrayInputStream(content)) {
            Blob data = new Blob("text/plain", stream);
            Assert.assertNotNull(data);
            doc.setValue("data", data);
            doc = saveDocInTestCollection(doc).toMutable();
        }

        Assert.assertTrue(doc.getValue("data") instanceof Blob);
        Blob data = (Blob) doc.getValue("data");
        Assert.assertEquals(0, data.length());
        Assert.assertArrayEquals(content, data.getContent());
        try (InputStream is = data.getContentStream()) {
            Assert.assertNotNull(is);
            byte[] buffer = new byte[37];
            int bytesRead = is.read(buffer);
            Assert.assertEquals(-1, bytesRead);
        }
    }

    @Test
    public void testMultipleBlobRead() throws IOException {
        byte[] content = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        Blob data = new Blob("text/plain", content);
        Assert.assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("data", data);

        data = (Blob) doc.getValue("data");
        for (int i = 0; i < 5; i++) {
            Assert.assertArrayEquals(content, data.getContent());
            try (InputStream is = data.getContentStream()) {
                Assert.assertNotNull(is);
                byte[] buffer = new byte[content.length + 37];
                int bytesRead = is.read(buffer);
                Assert.assertEquals(content.length, bytesRead);
            }
        }

        doc = saveDocInTestCollection(doc).toMutable();

        Assert.assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        for (int i = 0; i < 5; i++) {
            Assert.assertArrayEquals(content, data.getContent());
            try (InputStream is = data.getContentStream()) {
                Assert.assertNotNull(is);
                byte[] buffer = new byte[content.length + 37];
                int bytesRead = is.read(buffer);
                Assert.assertEquals(content.length, bytesRead);
            }
        }
    }

    @Test
    public void testReadExistingBlob() throws CouchbaseLiteException {
        byte[] content = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        Blob data = new Blob("text/plain", content);
        Assert.assertNotNull(data);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("data", data);
        doc.setValue("name", "Jim");
        doc = saveDocInTestCollection(doc).toMutable();

        Object obj = doc.getValue("data");
        Assert.assertTrue(obj instanceof Blob);
        data = (Blob) obj;
        Assert.assertArrayEquals(content, data.getContent());

        reopenTestDb();

        doc = getTestCollection().getDocument("doc1").toMutable();
        doc.setValue("foo", "bar");
        doc = saveDocInTestCollection(doc).toMutable();

        Assert.assertTrue(doc.getValue("data") instanceof Blob);
        data = (Blob) doc.getValue("data");
        Assert.assertArrayEquals(content, data.getContent());
    }

    @Test
    public void testEnumeratingKeys() {
        MutableDocument doc = new MutableDocument("doc1");
        for (long i = 0; i < 20; i++) { doc.setLong(TEST_DOC_TAG_KEY + i, i); }
        Map<String, Object> content = doc.toMap();
        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (String key: doc) {
            result.put(key, doc.getValue(key));
            count++;
        }
        Assert.assertEquals(content, result);
        Assert.assertEquals(content.size(), count);

        doc.remove("key2");
        doc.setLong("key20", 20L);
        doc.setLong("key21", 21L);
        final Map<String, Object> content2 = doc.toMap();
        validateAndSaveDocInTestCollection(
            doc, doc1 -> {
                Map<String, Object> content1 = doc1.toMap();
                Map<String, Object> result1 = new HashMap<>();
                int count1 = 0;
                for (String key: doc1) {
                    result1.put(key, doc1.getValue(key));
                    count1++;
                }
                Assert.assertEquals(content1.size(), count1);
                Assert.assertEquals(content1, result1);
                Assert.assertEquals(content1, content2);
            });
    }

    @Test
    public void testToMutable() {
        byte[] content = BLOB_CONTENT.getBytes(StandardCharsets.UTF_8);
        Blob data = new Blob("text/plain", content);
        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setBlob("data", data);
        mDoc1.setString("name", "Jim");
        mDoc1.setInt("score", 10);

        MutableDocument mDoc2 = mDoc1.toMutable();

        // https://forums.couchbase.com/t/bug-in-document-tomutable-in-db21/15441
        Assert.assertEquals(3, mDoc2.getKeys().size());
        Assert.assertEquals(3, mDoc2.count());

        Assert.assertNotSame(mDoc1, mDoc2);
        Assert.assertEquals(mDoc2, mDoc1);
        Assert.assertEquals(mDoc1, mDoc2);
        Assert.assertEquals(mDoc1.getBlob("data"), mDoc2.getBlob("data"));
        Assert.assertEquals(mDoc1.getString("name"), mDoc2.getString("name"));
        Assert.assertEquals(mDoc1.getInt("score"), mDoc2.getInt("score"));

        Document doc1 = saveDocInTestCollection(mDoc1);
        MutableDocument mDoc3 = doc1.toMutable();

        // https://forums.couchbase.com/t/bug-in-document-tomutable-in-db21/15441
        Assert.assertEquals(3, mDoc3.getKeys().size());
        Assert.assertEquals(3, mDoc3.count());

        Assert.assertEquals(doc1.getBlob("data"), mDoc3.getBlob("data"));
        Assert.assertEquals(doc1.getString("name"), mDoc3.getString("name"));
        Assert.assertEquals(doc1.getInt("score"), mDoc3.getInt("score"));
    }

    @Test
    public void testEquality() {
        byte[] data1 = "data1".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "data2".getBytes(StandardCharsets.UTF_8);

        MutableDocument doc1a = new MutableDocument("doc1");
        MutableDocument doc1b = new MutableDocument("doc1");
        MutableDocument doc1c = new MutableDocument("doc1");

        doc1a.setInt("answer", 42);
        doc1a.setValue("options", "1,2,3");
        doc1a.setBlob("attachment", new Blob("text/plain", data1));

        doc1b.setInt("answer", 42);
        doc1b.setValue("options", "1,2,3");
        doc1b.setBlob("attachment", new Blob("text/plain", data1));

        doc1c.setInt("answer", 41);
        doc1c.setValue("options", "1,2");
        doc1c.setBlob("attachment", new Blob("text/plain", data2));
        doc1c.setString("comment", "This is a comment");

        Assert.assertEquals(doc1a, doc1a);
        Assert.assertEquals(doc1a, doc1b);
        Assert.assertNotEquals(doc1a, doc1c);

        Assert.assertEquals(doc1b, doc1a);
        Assert.assertEquals(doc1b, doc1b);
        Assert.assertNotEquals(doc1b, doc1c);

        Assert.assertNotEquals(doc1c, doc1a);
        Assert.assertNotEquals(doc1c, doc1b);
        Assert.assertEquals(doc1c, doc1c);

        Document savedDoc = saveDocInTestCollection(doc1c);
        MutableDocument mDoc = savedDoc.toMutable();
        Assert.assertEquals(savedDoc, mDoc);
        Assert.assertEquals(mDoc, savedDoc);
        mDoc.setInt("answer", 50);
        Assert.assertNotEquals(savedDoc, mDoc);
        Assert.assertNotEquals(mDoc, savedDoc);
    }

    @Test
    public void testEqualityDifferentDocID() {
        MutableDocument doc1 = new MutableDocument("doc1");
        MutableDocument doc2 = new MutableDocument("doc2");
        doc1.setLong("answer", 42L); // TODO: Integer cause inequality with saved doc
        doc2.setLong("answer", 42L); // TODO: Integer cause inequality with saved doc
        Document sDoc1 = saveDocInTestCollection(doc1);
        Document sDoc2 = saveDocInTestCollection(doc2);

        Assert.assertEquals(doc1, doc1);
        Assert.assertEquals(sDoc1, sDoc1);
        Assert.assertEquals(doc1, sDoc1);
        Assert.assertEquals(sDoc1, doc1);

        Assert.assertEquals(doc2, doc2);
        Assert.assertEquals(sDoc2, sDoc2);
        Assert.assertEquals(doc2, sDoc2);
        Assert.assertEquals(sDoc2, doc2);

        Assert.assertNotEquals(doc1, doc2);
        Assert.assertNotEquals(doc2, doc1);
        Assert.assertNotEquals(sDoc1, sDoc2);
        Assert.assertNotEquals(sDoc2, sDoc1);
    }

    @VerySlowTest
    @Test
    public void testEqualityDifferentDB() throws CouchbaseLiteException {
        Database dupDb = null;
        Database otherDB = createDb("equ-diff-db");
        Collection otherCollection = BaseDbTestKt.createSimilarCollection(otherDB, getTestCollection());
        try {
            MutableDocument doc1a = new MutableDocument("doc1");
            MutableDocument doc1b = new MutableDocument("doc1");
            doc1a.setLong("answer", 42L);
            doc1b.setLong("answer", 42L);
            Assert.assertEquals(doc1a, doc1b);
            Assert.assertEquals(doc1b, doc1a);
            Document sDoc1a = saveDocInTestCollection(doc1a);

            otherCollection.save(doc1b);

            Document sDoc1b = otherCollection.getDocument(doc1b.getId());
            Assert.assertEquals(doc1a, sDoc1a);
            Assert.assertEquals(sDoc1a, doc1a);
            Assert.assertEquals(doc1b, sDoc1b);
            Assert.assertEquals(sDoc1b, doc1b);
            Assert.assertNotEquals(sDoc1a, sDoc1b);
            Assert.assertNotEquals(sDoc1b, sDoc1a);

            sDoc1a = getTestCollection().getDocument("doc1");
            sDoc1b = otherCollection.getDocument("doc1");
            Assert.assertNotEquals(sDoc1b, sDoc1a);

            dupDb = duplicateDb(getTestDatabase());
            Collection sameCollection = BaseDbTestKt.getSimilarCollection(dupDb, getTestCollection());

            Document anotherDoc1a = sameCollection.getDocument("doc1");
            Assert.assertEquals(anotherDoc1a, sDoc1a);
            Assert.assertEquals(sDoc1a, anotherDoc1a);
        }
        finally {
            if (dupDb != null) { dupDb.close(); }
            eraseDb(otherDB);
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1449
    @Test
    public void testDeleteDocAndGetDoc() throws CouchbaseLiteException {
        String docID = "doc-1";

        Document doc = getTestCollection().getDocument(docID);
        Assert.assertNull(doc);

        MutableDocument mDoc = new MutableDocument(docID);
        mDoc.setValue(TEST_DOC_TAG_KEY, "value");
        doc = saveDocInTestCollection(mDoc);
        Assert.assertNotNull(doc);
        Assert.assertEquals(1, getTestCollection().getCount());

        doc = getTestCollection().getDocument(docID);
        Assert.assertNotNull(doc);
        Assert.assertEquals("value", doc.getString(TEST_DOC_TAG_KEY));

        getTestCollection().delete(doc);
        Assert.assertEquals(0, getTestCollection().getCount());
        doc = getTestCollection().getDocument(docID);
        Assert.assertNull(doc);
    }

    @Test
    public void testEquals() {

        // mDoc1 and mDoc2 have exactly same data
        // mDoc3 is different
        // mDoc4 is different

        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setValue("key1", 1L);
        mDoc1.setValue("key2", "Hello");
        mDoc1.setValue("key3", null);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setValue("key1", 1L);
        mDoc2.setValue("key2", "Hello");
        mDoc2.setValue("key3", null);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setValue("key1", 100L);
        mDoc3.setValue("key3", true);

        MutableDocument mDoc4 = new MutableDocument();
        mDoc4.setValue("key1", 100L);

        MutableDocument mDoc5 = new MutableDocument();
        mDoc4.setValue("key1", 100L);
        mDoc3.setValue("key3", false);

        MutableDocument mDoc6 = new MutableDocument();
        mDoc6.setValue("key1", 100L);

        MutableDocument mDoc7 = new MutableDocument();
        mDoc7.setValue("key1", 100L);
        mDoc7.setValue("key3", false);

        MutableDocument mDoc8 = new MutableDocument("sameDocID");
        mDoc8.setValue("key1", 100L);

        MutableDocument mDoc9 = new MutableDocument("sameDocID");
        mDoc9.setValue("key1", 100L);
        mDoc9.setValue("key3", false);

        Document doc1 = saveDocInTestCollection(mDoc1);
        Document doc2 = saveDocInTestCollection(mDoc2);
        Document doc3 = saveDocInTestCollection(mDoc3);
        Document doc4 = saveDocInTestCollection(mDoc4);
        Document doc5 = saveDocInTestCollection(mDoc5);

        // compare doc1, doc2, mdoc1, and mdoc2
        Assert.assertEquals(doc1, doc1);
        Assert.assertEquals(doc2, doc2);
        Assert.assertNotEquals(doc1, doc2);
        Assert.assertNotEquals(doc2, doc1);
        Assert.assertEquals(doc1, doc1.toMutable());
        Assert.assertNotEquals(doc1, doc2.toMutable());
        Assert.assertEquals(doc1.toMutable(), doc1);
        Assert.assertNotEquals(doc2.toMutable(), doc1);
        Assert.assertEquals(doc1, mDoc1); // mDoc's ID is updated
        Assert.assertNotEquals(doc1, mDoc2);
        Assert.assertNotEquals(doc2, mDoc1);
        Assert.assertEquals(doc2, mDoc2);
        Assert.assertEquals(mDoc1, doc1);
        Assert.assertNotEquals(mDoc2, doc1);
        Assert.assertNotEquals(mDoc1, doc2);
        Assert.assertEquals(mDoc2, doc2);
        Assert.assertEquals(mDoc1, mDoc1);
        Assert.assertEquals(mDoc2, mDoc2);
        Assert.assertEquals(mDoc1, mDoc1);
        Assert.assertEquals(mDoc2, mDoc2);

        // compare doc1, doc3, mdoc1, and mdoc3
        Assert.assertEquals(doc3, doc3);
        Assert.assertNotEquals(doc1, doc3);
        Assert.assertNotEquals(doc3, doc1);
        Assert.assertNotEquals(doc1, doc3.toMutable());
        Assert.assertNotEquals(doc3.toMutable(), doc1);
        Assert.assertNotEquals(doc1, mDoc3);
        Assert.assertNotEquals(doc3, mDoc1);
        Assert.assertEquals(doc3, mDoc3);
        Assert.assertNotEquals(mDoc3, doc1);
        Assert.assertNotEquals(mDoc1, doc3);
        Assert.assertEquals(mDoc3, doc3);
        Assert.assertEquals(mDoc3, mDoc3);

        // compare doc1, doc4, mdoc1, and mdoc4
        Assert.assertEquals(doc4, doc4);
        Assert.assertNotEquals(doc1, doc4);
        Assert.assertNotEquals(doc4, doc1);
        Assert.assertNotEquals(doc1, doc4.toMutable());
        Assert.assertNotEquals(doc4.toMutable(), doc1);
        Assert.assertNotEquals(doc1, mDoc4);
        Assert.assertNotEquals(doc4, mDoc1);
        Assert.assertEquals(doc4, mDoc4);
        Assert.assertNotEquals(mDoc4, doc1);
        Assert.assertNotEquals(mDoc1, doc4);
        Assert.assertEquals(mDoc4, doc4);
        Assert.assertEquals(mDoc4, mDoc4);

        // compare doc3, doc4, mdoc3, and mdoc4
        Assert.assertNotEquals(doc3, doc4);
        Assert.assertNotEquals(doc4, doc3);
        Assert.assertNotEquals(doc3, doc4.toMutable());
        Assert.assertNotEquals(doc4.toMutable(), doc3);
        Assert.assertNotEquals(doc3, mDoc4);
        Assert.assertNotEquals(doc4, mDoc3);
        Assert.assertNotEquals(mDoc4, doc3);
        Assert.assertNotEquals(mDoc3, doc4);

        // compare doc3, doc5, mdoc3, and mdoc5
        Assert.assertNotEquals(doc3, doc5);
        Assert.assertNotEquals(doc5, doc3);
        Assert.assertNotEquals(doc3, doc5.toMutable());
        Assert.assertNotEquals(doc5.toMutable(), doc3);
        Assert.assertNotEquals(doc3, mDoc5);
        Assert.assertNotEquals(doc5, mDoc3);
        Assert.assertNotEquals(mDoc5, doc3);
        Assert.assertNotEquals(mDoc3, doc5);

        // compare doc5, doc4, mDoc5, and mdoc4
        Assert.assertNotEquals(doc5, doc4);
        Assert.assertNotEquals(doc4, doc5);
        Assert.assertNotEquals(doc5, doc4.toMutable());
        Assert.assertNotEquals(doc4.toMutable(), doc5);
        Assert.assertNotEquals(doc5, mDoc4);
        Assert.assertNotEquals(doc4, mDoc5);
        Assert.assertNotEquals(mDoc4, doc5);
        Assert.assertNotEquals(mDoc5, doc4);

        // compare doc1, mDoc1, and mdoc6
        Assert.assertNotEquals(doc1, mDoc6);
        Assert.assertNotEquals(mDoc6, doc1);
        Assert.assertNotEquals(mDoc6, doc1.toMutable());
        Assert.assertNotEquals(mDoc1, mDoc6);
        Assert.assertNotEquals(mDoc6, mDoc1);

        // compare doc4, mDoc4, and mdoc6
        Assert.assertEquals(mDoc6, mDoc6);
        Assert.assertNotEquals(doc4, mDoc6);
        Assert.assertNotEquals(mDoc6, doc4);
        Assert.assertNotEquals(mDoc6, doc4.toMutable());
        Assert.assertNotEquals(mDoc4, mDoc6);
        Assert.assertNotEquals(mDoc6, mDoc4);

        // compare doc5, mDoc5, and mdoc7
        Assert.assertEquals(mDoc7, mDoc7);
        Assert.assertNotEquals(doc5, mDoc7);
        Assert.assertNotEquals(mDoc7, doc5);
        Assert.assertNotEquals(mDoc7, doc5.toMutable());
        Assert.assertNotEquals(mDoc5, mDoc7);
        Assert.assertNotEquals(mDoc7, mDoc5);

        // compare mDoc6 and mDoc7
        Assert.assertEquals(mDoc6, mDoc6);
        Assert.assertNotEquals(mDoc6, mDoc7);
        Assert.assertNotEquals(mDoc6, mDoc8);
        Assert.assertNotEquals(mDoc6, mDoc9);
        Assert.assertNotEquals(mDoc7, mDoc6);
        Assert.assertEquals(mDoc7, mDoc7);
        Assert.assertNotEquals(mDoc7, mDoc8);
        Assert.assertNotEquals(mDoc7, mDoc9);

        // compare mDoc8 and mDoc9
        Assert.assertEquals(mDoc8, mDoc8);
        Assert.assertNotEquals(mDoc8, mDoc9);
        Assert.assertNotEquals(mDoc9, mDoc8);
        Assert.assertEquals(mDoc9, mDoc9);

        Assert.assertNotNull(doc3);
    }

    @Test
    public void testHashCode() {
        // mDoc1 and mDoc2 have exactly same data
        // mDoc3 is different
        // mDoc4 is different

        MutableDocument mDoc1 = new MutableDocument();
        mDoc1.setValue("key1", 1L);
        mDoc1.setValue("key2", "Hello");
        mDoc1.setValue("key3", null);

        MutableDocument mDoc2 = new MutableDocument();
        mDoc2.setValue("key1", 1L);
        mDoc2.setValue("key2", "Hello");
        mDoc2.setValue("key3", null);

        MutableDocument mDoc3 = new MutableDocument();
        mDoc3.setValue("key1", 100L);
        mDoc3.setValue("key3", true);

        MutableDocument mDoc4 = new MutableDocument();
        mDoc4.setValue("key1", 100L);

        MutableDocument mDoc5 = new MutableDocument();
        mDoc4.setValue("key1", 100L);
        mDoc3.setValue("key3", false);

        MutableDocument mDoc6 = new MutableDocument();
        mDoc6.setValue("key1", 100L);

        MutableDocument mDoc7 = new MutableDocument();
        mDoc7.setValue("key1", 100L);
        mDoc7.setValue("key3", false);

        Document doc1 = saveDocInTestCollection(mDoc1);
        Document doc2 = saveDocInTestCollection(mDoc2);
        Document doc3 = saveDocInTestCollection(mDoc3);
        Document doc4 = saveDocInTestCollection(mDoc4);
        Document doc5 = saveDocInTestCollection(mDoc5);

        Assert.assertEquals(doc1.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(doc1.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(doc2.hashCode(), doc1.hashCode());
        Assert.assertEquals(doc1.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(doc1.hashCode(), doc2.toMutable().hashCode());
        Assert.assertEquals(doc1.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(doc1.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(doc2.hashCode(), mDoc1.hashCode());
        Assert.assertEquals(doc2.hashCode(), mDoc2.hashCode());

        Assert.assertNotEquals(doc3.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc2.hashCode());

        Assert.assertNotEquals(0, doc3.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new Object().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), Integer.valueOf(1).hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new HashMap<>().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new MutableDictionary().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), new MutableArray().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc2.hashCode());

        Assert.assertNotEquals(mDoc6.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc3.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc3.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc3.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc4.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc4.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc4.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc5.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), doc5.toMutable().hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc5.hashCode());
        Assert.assertEquals(mDoc6.hashCode(), mDoc6.hashCode());
        Assert.assertNotEquals(mDoc6.hashCode(), mDoc7.hashCode());

        Assert.assertNotEquals(mDoc7.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc3.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc3.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc3.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc4.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc4.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc4.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc5.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), doc5.toMutable().hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc5.hashCode());
        Assert.assertNotEquals(mDoc7.hashCode(), mDoc6.hashCode());
        Assert.assertEquals(mDoc7.hashCode(), mDoc7.hashCode());

        Assert.assertNotEquals(doc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(doc3.hashCode(), mDoc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc1.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), doc2.toMutable().hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc1.hashCode());
        Assert.assertNotEquals(mDoc3.hashCode(), mDoc2.hashCode());
    }

    @Test
    public void testRevisionIDNewDoc() throws CouchbaseLiteException {
        MutableDocument doc = new MutableDocument();
        Assert.assertNull(doc.getRevisionID());
        getTestCollection().save(doc);
        Assert.assertNotNull(doc.getRevisionID());
    }

    @Test
    public void testRevisionIDExistingDoc() throws CouchbaseLiteException {
        MutableDocument mdoc = new MutableDocument("doc1");
        getTestCollection().save(mdoc);
        String docRevID = mdoc.getRevisionID();

        Document doc = getTestCollection().getDocument("doc1");
        Assert.assertEquals(docRevID, doc.getRevisionID());
        Assert.assertEquals(docRevID, mdoc.getRevisionID());

        mdoc = doc.toMutable();
        Assert.assertEquals(docRevID, doc.getRevisionID());
        Assert.assertEquals(docRevID, mdoc.getRevisionID());

        mdoc.setInt("int", 88);
        Assert.assertEquals(docRevID, doc.getRevisionID());
        Assert.assertEquals(docRevID, mdoc.getRevisionID());

        getTestCollection().save(mdoc);
        Assert.assertEquals(docRevID, doc.getRevisionID());
        Assert.assertNotEquals(docRevID, mdoc.getRevisionID());
    }

    /// ////////////  JSON tests

    // JSON 3.2
    @Test
    public void testDocToJSON() throws JSONException, CouchbaseLiteException {
        final MutableDocument mDoc = makeDocument();
        saveDocInTestCollection(mDoc);
        verifyDocument(new JSONObject(getTestCollection().getDocument(mDoc.getId()).toJSON()));
    }

    // JSON 3.5.?
    @Test
    public void testMutableDocToJSONBeforeSave() {
        Assert.assertThrows(CouchbaseLiteError.class, () -> new MutableDocument().toJSON());
    }

    // JSON 3.5.a
    // Java does not have MutableDocument(String json) because it collides with MutableDocument(String id)

    // JSON 3.5.b-c
    @Test
    public void testDocFromJSON() throws JSONException, CouchbaseLiteException {
        Document dbDoc = saveDocInTestCollection(
            new MutableDocument("fromJSON", BaseDbTestKt.readJSONResource("document.json")));
        getTestDatabase().saveBlob(makeBlob()); // be sure the blob is there...
        verifyDocument(dbDoc, true);
        verifyDocument(new JSONObject(dbDoc.toJSON()));
    }

    // JSON 3.5.d.1
    @Test
    public void testDocFromBadJSON1() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new MutableDocument("fromJSON", "{"));
    }

    // JSON 3.5.d.2
    @Test
    public void testDocFromBadJSON2() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new MutableDocument("fromJSON", "{ab cd: \"xyz\"}"));
    }

    // JSON 3.5.d.3
    @Test
    public void testDocFromBadJSON3() {
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> new MutableDocument("fromJSON", "{ab: \"xyz\" cd: \"xyz\"}"));
    }

    // JSON 3.5.e
    @Test
    public void testMutableFromArray() {
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> new MutableDocument("fromJSON", BaseDbTestKt.readJSONResource("array.json")));
    }

    // Unsupported revision history
    @Test
    public void testGetRevisionHistory() throws CouchbaseLiteException {
        MutableDocument mdoc = new MutableDocument("doc1");
        Collection coll = getTestCollection();
        coll.save(mdoc);
        Document doc = coll.getDocument(mdoc.getId());
        Assert.assertFalse(StringUtils.isEmpty(doc.getRevisionHistory()));
    }

    // !!! Replace with BaseDbTest.makeDocument
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

    // !!! Replace with BaseDbTest.makeDocument
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

    // Kotlin shim functions

    private Document saveDocInTestCollection(MutableDocument mDoc) {
        return validateAndSaveDocInTestCollection(
            mDoc,
            null);
    }

    private Document validateAndSaveDocInTestCollection(MutableDocument mDoc, DocValidator validator) {
        try {
            if (validator != null) { validator.accept(mDoc); }
            Document doc = saveDocInCollection(mDoc, getTestCollection());
            if (validator != null) { validator.accept(doc); }
            return doc;
        }
        catch (Exception e) {
            throw new AssertionError("Failed saving document in test collection: " + mDoc, e);
        }
    }
}
