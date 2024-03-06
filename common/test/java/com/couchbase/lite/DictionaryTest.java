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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


// Tests for the Dictionary Iterator tests are in IteratorTest
@SuppressWarnings("ConstantConditions")
public class DictionaryTest extends BaseDbTest {
    @Test
    public void testCreateDictionary() {
        MutableDictionary address = new MutableDictionary();
        assertEquals(0, address.count());
        assertEquals(new HashMap<String, Object>(), address.toMap());

        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("address", address);
        assertEquals(address, mDoc.getDictionary("address"));

        Document doc = saveDocInTestCollection(mDoc);
        assertEquals(new HashMap<String, Object>(), doc.getDictionary("address").toMap());
    }

    @Test
    public void testRecursiveDictionary() {
        MutableDictionary dict = new MutableDictionary();
        assertThrows(IllegalArgumentException.class, () -> dict.setDictionary("k1", dict));
    }

    @Test
    public void testCreateDictionaryWithMap() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("street", "1 Main street");
        dict.put("city", "Mountain View");
        dict.put("state", "CA");
        MutableDictionary address = new MutableDictionary(dict);
        assertEquals(3, address.count());
        assertEquals("1 Main street", address.getValue("street"));
        assertEquals("Mountain View", address.getValue("city"));
        assertEquals("CA", address.getValue("state"));
        assertEquals(dict, address.toMap());

        MutableDocument mDoc1 = new MutableDocument("doc1");
        mDoc1.setValue("address", address);
        assertEquals(address, mDoc1.getDictionary("address"));

        Document doc1 = saveDocInTestCollection(mDoc1);
        assertEquals(dict, doc1.getDictionary("address").toMap());
    }

    @Test
    public void testGetValueFromNewEmptyDictionary() {
        MutableDictionary mDict = new MutableDictionary();

        assertEquals(0, mDict.getInt(TEST_DOC_TAG_KEY));
        assertEquals(0.0f, mDict.getFloat(TEST_DOC_TAG_KEY), 0.0f);
        assertEquals(0.0, mDict.getDouble(TEST_DOC_TAG_KEY), 0.0);
        assertFalse(mDict.getBoolean(TEST_DOC_TAG_KEY));
        assertNull(mDict.getBlob(TEST_DOC_TAG_KEY));
        assertNull(mDict.getDate(TEST_DOC_TAG_KEY));
        assertNull(mDict.getNumber(TEST_DOC_TAG_KEY));
        assertNull(mDict.getValue(TEST_DOC_TAG_KEY));
        assertNull(mDict.getString(TEST_DOC_TAG_KEY));
        assertNull(mDict.getDictionary(TEST_DOC_TAG_KEY));
        assertNull(mDict.getArray(TEST_DOC_TAG_KEY));
        assertEquals(new HashMap<String, Object>(), mDict.toMap());

        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("dict", mDict);

        Document doc = saveDocInTestCollection(mDoc);

        Dictionary dict = doc.getDictionary("dict");

        assertEquals(0, dict.getInt(TEST_DOC_TAG_KEY));
        assertEquals(0.0f, dict.getFloat(TEST_DOC_TAG_KEY), 0.0f);
        assertEquals(0.0, dict.getDouble(TEST_DOC_TAG_KEY), 0.0);
        assertFalse(dict.getBoolean(TEST_DOC_TAG_KEY));
        assertNull(dict.getBlob(TEST_DOC_TAG_KEY));
        assertNull(dict.getDate(TEST_DOC_TAG_KEY));
        assertNull(dict.getNumber(TEST_DOC_TAG_KEY));
        assertNull(dict.getValue(TEST_DOC_TAG_KEY));
        assertNull(dict.getString(TEST_DOC_TAG_KEY));
        assertNull(dict.getDictionary(TEST_DOC_TAG_KEY));
        assertNull(dict.getArray(TEST_DOC_TAG_KEY));
        assertEquals(new HashMap<String, Object>(), dict.toMap());
    }

    @Test
    public void testSetNestedDictionaries() {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary level1 = new MutableDictionary();
        level1.setValue("name", "n1");
        doc.setValue("level1", level1);

        MutableDictionary level2 = new MutableDictionary();
        level2.setValue("name", "n2");
        doc.setValue("level2", level2);

        MutableDictionary level3 = new MutableDictionary();
        level3.setValue("name", "n3");
        doc.setValue("level3", level3);

        assertEquals(level1, doc.getDictionary("level1"));
        assertEquals(level2, doc.getDictionary("level2"));
        assertEquals(level3, doc.getDictionary("level3"));

        Map<String, Object> dict = new HashMap<>();
        Map<String, Object> l1 = new HashMap<>();
        l1.put("name", "n1");
        dict.put("level1", l1);
        Map<String, Object> l2 = new HashMap<>();
        l2.put("name", "n2");
        dict.put("level2", l2);
        Map<String, Object> l3 = new HashMap<>();
        l3.put("name", "n3");
        dict.put("level3", l3);
        assertEquals(dict, doc.toMap());

        Document savedDoc = saveDocInTestCollection(doc);

        assertNotSame(level1, savedDoc.getDictionary("level1"));
        assertEquals(dict, savedDoc.toMap());
    }

    @Test
    public void testDictionaryArray() {
        MutableDocument mDoc = new MutableDocument("doc1");

        List<Object> data = new ArrayList<>();

        Map<String, Object> d1 = new HashMap<>();
        d1.put("name", "1");
        data.add(d1);
        Map<String, Object> d2 = new HashMap<>();
        d2.put("name", "2");
        data.add(d2);
        Map<String, Object> d3 = new HashMap<>();
        d3.put("name", "3");
        data.add(d3);
        Map<String, Object> d4 = new HashMap<>();
        d4.put("name", "4");
        data.add(d4);
        assertEquals(4, data.size());

        mDoc.setValue("array", data);

        MutableArray mArray = mDoc.getArray("array");
        assertEquals(4, mArray.count());

        MutableDictionary mDict1 = mArray.getDictionary(0);
        MutableDictionary mDict2 = mArray.getDictionary(1);
        MutableDictionary mDict3 = mArray.getDictionary(2);
        MutableDictionary mDict4 = mArray.getDictionary(3);

        assertEquals("1", mDict1.getString("name"));
        assertEquals("2", mDict2.getString("name"));
        assertEquals("3", mDict3.getString("name"));
        assertEquals("4", mDict4.getString("name"));

        // after save
        Document doc = saveDocInTestCollection(mDoc);

        Array array = doc.getArray("array");
        assertEquals(4, array.count());

        Dictionary dict1 = array.getDictionary(0);
        Dictionary dict2 = array.getDictionary(1);
        Dictionary dict3 = array.getDictionary(2);
        Dictionary dict4 = array.getDictionary(3);

        assertEquals("1", dict1.getString("name"));
        assertEquals("2", dict2.getString("name"));
        assertEquals("3", dict3.getString("name"));
        assertEquals("4", dict4.getString("name"));
    }

    @Test
    public void testReplaceDictionary() {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary profile1 = new MutableDictionary();
        profile1.setValue("name", "Scott Tiger");
        doc.setValue("profile", profile1);
        assertEquals(profile1, doc.getDictionary("profile"));

        MutableDictionary profile2 = new MutableDictionary();
        profile2.setValue("name", "Daniel Tiger");
        doc.setValue("profile", profile2);
        assertEquals(profile2, doc.getDictionary("profile"));

        // Profile1 should be now detached:
        profile1.setValue("age", 20);
        assertEquals("Scott Tiger", profile1.getValue("name"));
        assertEquals(20, profile1.getValue("age"));

        // Check profile2:
        assertEquals("Daniel Tiger", profile2.getValue("name"));
        assertNull(profile2.getValue("age"));

        // Save:
        Document savedDoc = saveDocInTestCollection(doc);

        assertNotSame(profile2, savedDoc.getDictionary("profile"));
        Dictionary savedDict = savedDoc.getDictionary("profile");
        assertEquals("Daniel Tiger", savedDict.getValue("name"));
    }

    @Test
    public void testReplaceDictionaryDifferentType() {
        MutableDocument doc = new MutableDocument("doc1");

        MutableDictionary profile1 = new MutableDictionary();
        profile1.setValue("name", "Scott Tiger");
        doc.setValue("profile", profile1);
        assertEquals(profile1, doc.getDictionary("profile"));

        // Set string value to profile:
        doc.setValue("profile", "Daniel Tiger");
        assertEquals("Daniel Tiger", doc.getValue("profile"));

        // Profile1 should be now detached:
        profile1.setValue("age", 20);
        assertEquals("Scott Tiger", profile1.getValue("name"));
        assertEquals(20, profile1.getValue("age"));

        // Check whether the profile value has no change:
        assertEquals("Daniel Tiger", doc.getValue("profile"));

        // Save
        Document savedDoc = saveDocInTestCollection(doc);
        assertEquals("Daniel Tiger", savedDoc.getValue("profile"));
    }

    @Test
    public void testRemoveDictionary() {
        MutableDocument doc = new MutableDocument("doc1");
        MutableDictionary profile1 = new MutableDictionary();
        profile1.setValue("name", "Scott Tiger");
        doc.setValue("profile", profile1);
        assertEquals(profile1.toMap(), doc.getDictionary("profile").toMap());
        assertTrue(doc.contains("profile"));

        // Remove profile
        doc.remove("profile");
        assertNull(doc.getValue("profile"));
        assertFalse(doc.contains("profile"));

        // Profile1 should be now detached:
        profile1.setValue("age", 20);
        assertEquals("Scott Tiger", profile1.getValue("name"));
        assertEquals(20, profile1.getValue("age"));

        // Check whether the profile value has no change:
        assertNull(doc.getValue("profile"));

        // Save:
        doc = saveDocInTestCollection(doc).toMutable();

        assertNull(doc.getValue("profile"));
        assertFalse(doc.contains("profile"));
    }

    @Test
    public void testEnumeratingKeys() {
        final MutableDictionary dict = new MutableDictionary();
        for (int i = 0; i < 20; i++) { dict.setValue(String.format(Locale.ENGLISH, "key%d", i), i); }
        Map<String, Object> content = dict.toMap();

        Map<String, Object> result = new HashMap<>();
        int count = 0;
        for (String key: dict) {
            result.put(key, dict.getValue(key));
            count++;
        }
        assertEquals(content.size(), count);
        assertEquals(content, result);

        // Update:
        dict.remove("key2");
        dict.setValue("key20", 20);
        dict.setValue("key21", 21);
        content = dict.toMap();

        result = new HashMap<>();
        count = 0;
        for (String key: dict) {
            result.put(key, dict.getValue(key));
            count++;
        }
        assertEquals(content.size(), count);
        assertEquals(content, result);

        final Map<String, Object> finalContent = content;

        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("dict", dict);
        Document doc = saveDocInTestCollection(mDoc);

        count = 0;
        result = new HashMap<>();
        Dictionary dictObj = doc.getDictionary("dict");
        for (String key: dictObj) {
            result.put(key, dict.getValue(key));
            count++;
        }
        assertEquals(finalContent.size(), count);
        assertEquals(finalContent, result);
    }

    // https://github.com/couchbase/couchbase-lite-core/issues/230
    @Test
    public void testLargeLongValue() {
        MutableDocument doc = new MutableDocument("test");
        long num1 = 1234567L;
        long num2 = 12345678L;
        long num3 = 123456789L;
        doc.setValue("num1", num1);
        doc.setValue("num2", num2);
        doc.setValue("num3", num3);
        doc = saveDocInTestCollection(doc).toMutable();
        assertEquals(num1, doc.getLong("num1"));
        assertEquals(num2, doc.getLong("num2"));
        assertEquals(num3, doc.getLong("num3"));
    }

    //https://forums.couchbase.com/t/long-value-on-document-changed-after-saved-to-db/14259/
    @Test
    public void testLargeLongValue2() {
        MutableDocument doc = new MutableDocument("test");
        long num1 = 11989091L;
        long num2 = 231548688L;
        doc.setValue("num1", num1);
        doc.setValue("num2", num2);
        doc = saveDocInTestCollection(doc).toMutable();
        assertEquals(num1, doc.getLong("num1"));
        assertEquals(num2, doc.getLong("num2"));
    }

    @Test
    public void testSetNull() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableDictionary mDict = new MutableDictionary();
        mDict.setValue("obj-null", null);
        mDict.setString("string-null", null);
        mDict.setNumber("number-null", null);
        mDict.setDate("date-null", null);
        mDict.setArray("array-null", null);
        mDict.setDictionary("dict-null", null);
        mDoc.setDictionary("dict", mDict);
        Document doc = saveDocInTestCollection(mDoc);

        assertEquals(1, doc.count());
        assertTrue(doc.contains("dict"));
        Dictionary d = doc.getDictionary("dict");
        assertNotNull(d);
        assertEquals(6, d.count());
        assertTrue(d.contains("obj-null"));
        assertTrue(d.contains("string-null"));
        assertTrue(d.contains("number-null"));
        assertTrue(d.contains("date-null"));
        assertTrue(d.contains("array-null"));
        assertTrue(d.contains("dict-null"));
        assertNull(d.getValue("obj-null"));
        assertNull(d.getValue("string-null"));
        assertNull(d.getValue("number-null"));
        assertNull(d.getValue("date-null"));
        assertNull(d.getValue("array-null"));
        assertNull(d.getValue("dict-null"));
    }

    @Test
    public void testEquals() {

        // mDict1 and mDict2 have exactly same data
        // mDict3 is different
        // mDict4 is different

        MutableDictionary mDict1 = new MutableDictionary();
        mDict1.setValue("key1", 1L);
        mDict1.setValue("key2", "Hello");
        mDict1.setValue("key3", null);

        MutableDictionary mDict2 = new MutableDictionary();
        mDict2.setValue("key1", 1L);
        mDict2.setValue("key2", "Hello");
        mDict2.setValue("key3", null);

        MutableDictionary mDict3 = new MutableDictionary();
        mDict3.setValue("key1", 100L);
        mDict3.setValue("key3", true);

        MutableDictionary mDict4 = new MutableDictionary();
        mDict4.setValue("key1", 100L);

        MutableDictionary mDict5 = new MutableDictionary();
        mDict4.setValue("key1", 100L);
        mDict3.setValue("key3", false);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setDictionary("dict1", mDict1);
        mDoc.setDictionary("dict2", mDict2);
        mDoc.setDictionary("dict3", mDict3);
        mDoc.setDictionary("dict4", mDict4);
        mDoc.setDictionary("dict5", mDict5);

        Document doc = saveDocInTestCollection(mDoc);
        Dictionary dict1 = doc.getDictionary("dict1");
        Dictionary dict2 = doc.getDictionary("dict2");
        Dictionary dict3 = doc.getDictionary("dict3");
        Dictionary dict4 = doc.getDictionary("dict4");
        Dictionary dict5 = doc.getDictionary("dict5");

        // compare dict1, dict2, mdict1, and mdict2
        assertEquals(dict1, dict1);
        assertEquals(dict2, dict2);
        assertEquals(dict1, dict2);
        assertEquals(dict2, dict1);
        assertEquals(dict1, dict1.toMutable());
        assertEquals(dict1, dict2.toMutable());
        assertEquals(dict1.toMutable(), dict1);
        assertEquals(dict2.toMutable(), dict1);
        assertEquals(dict1, mDict1);
        assertEquals(dict1, mDict2);
        assertEquals(dict2, mDict1);
        assertEquals(dict2, mDict2);
        assertEquals(mDict1, dict1);
        assertEquals(mDict2, dict1);
        assertEquals(mDict1, dict2);
        assertEquals(mDict2, dict2);
        assertEquals(mDict1, mDict1);
        assertEquals(mDict2, mDict2);
        assertEquals(mDict1, mDict1);
        assertEquals(mDict2, mDict2);

        // compare dict1, dict3, mdict1, and mdict3
        assertEquals(dict3, dict3);
        assertNotEquals(dict1, dict3);
        assertNotEquals(dict3, dict1);
        assertNotEquals(dict1, dict3.toMutable());
        assertNotEquals(dict3.toMutable(), dict1);
        assertNotEquals(dict1, mDict3);
        assertNotEquals(dict3, mDict1);
        assertEquals(dict3, mDict3);
        assertNotEquals(mDict3, dict1);
        assertNotEquals(mDict1, dict3);
        assertEquals(mDict3, dict3);
        assertEquals(mDict3, mDict3);
        assertEquals(mDict3, mDict3);

        // compare dict1, dict4, mdict1, and mdict4
        assertEquals(dict4, dict4);
        assertNotEquals(dict1, dict4);
        assertNotEquals(dict4, dict1);
        assertNotEquals(dict1, dict4.toMutable());
        assertNotEquals(dict4.toMutable(), dict1);
        assertNotEquals(dict1, mDict4);
        assertNotEquals(dict4, mDict1);
        assertEquals(dict4, mDict4);
        assertNotEquals(mDict4, dict1);
        assertNotEquals(mDict1, dict4);
        assertEquals(mDict4, dict4);
        assertEquals(mDict4, mDict4);
        assertEquals(mDict4, mDict4);

        // compare dict3, dict4, mdict3, and mdict4
        assertNotEquals(dict3, dict4);
        assertNotEquals(dict4, dict3);
        assertNotEquals(dict3, dict4.toMutable());
        assertNotEquals(dict4.toMutable(), dict3);
        assertNotEquals(dict3, mDict4);
        assertNotEquals(dict4, mDict3);
        assertNotEquals(mDict4, dict3);
        assertNotEquals(mDict3, dict4);

        // compare dict3, dict5, mdict3, and mdict5
        assertNotEquals(dict3, dict5);
        assertNotEquals(dict5, dict3);
        assertNotEquals(dict3, dict5.toMutable());
        assertNotEquals(dict5.toMutable(), dict3);
        assertNotEquals(dict3, mDict5);
        assertNotEquals(dict5, mDict3);
        assertNotEquals(mDict5, dict3);
        assertNotEquals(mDict3, dict5);

        // compare dict5, dict4, mDict5, and mdict4
        assertNotEquals(dict5, dict4);
        assertNotEquals(dict4, dict5);
        assertNotEquals(dict5, dict4.toMutable());
        assertNotEquals(dict4.toMutable(), dict5);
        assertNotEquals(dict5, mDict4);
        assertNotEquals(dict4, mDict5);
        assertNotEquals(mDict4, dict5);
        assertNotEquals(mDict5, dict4);

        assertNotNull(dict3);
    }

    @Test
    public void testHashCode() {

        // mDict1 and mDict2 have exactly same data
        // mDict3 is different
        // mDict4 is different

        MutableDictionary mDict1 = new MutableDictionary();
        mDict1.setValue("key1", 1L);
        mDict1.setValue("key2", "Hello");
        mDict1.setValue("key3", null);

        MutableDictionary mDict2 = new MutableDictionary();
        mDict2.setValue("key1", 1L);
        mDict2.setValue("key2", "Hello");
        mDict2.setValue("key3", null);

        MutableDictionary mDict3 = new MutableDictionary();
        mDict3.setValue("key1", 100L);
        mDict3.setValue("key3", true);

        MutableDictionary mDict4 = new MutableDictionary();
        mDict4.setValue("key1", 100L);

        MutableDictionary mDict5 = new MutableDictionary();
        mDict4.setValue("key1", 100L);
        mDict3.setValue("key3", false);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setDictionary("dict1", mDict1);
        mDoc.setDictionary("dict2", mDict2);
        mDoc.setDictionary("dict3", mDict3);
        mDoc.setDictionary("dict4", mDict4);
        mDoc.setDictionary("dict5", mDict5);

        Document doc = saveDocInTestCollection(mDoc);
        Dictionary dict1 = doc.getDictionary("dict1");
        Dictionary dict2 = doc.getDictionary("dict2");
        Dictionary dict3 = doc.getDictionary("dict3");

        assertEquals(dict1.hashCode(), dict1.hashCode());
        assertEquals(dict1.hashCode(), dict2.hashCode());
        assertEquals(dict2.hashCode(), dict1.hashCode());
        assertEquals(dict1.hashCode(), dict1.toMutable().hashCode());
        assertEquals(dict1.hashCode(), dict2.toMutable().hashCode());
        assertEquals(dict1.hashCode(), mDict1.hashCode());
        assertEquals(dict1.hashCode(), mDict2.hashCode());
        assertEquals(dict2.hashCode(), mDict1.hashCode());
        assertEquals(dict2.hashCode(), mDict2.hashCode());

        assertNotEquals(dict3.hashCode(), dict1.hashCode());
        assertNotEquals(dict3.hashCode(), dict2.hashCode());
        assertNotEquals(dict3.hashCode(), dict1.toMutable().hashCode());
        assertNotEquals(dict3.hashCode(), dict2.toMutable().hashCode());
        assertNotEquals(dict3.hashCode(), mDict1.hashCode());
        assertNotEquals(dict3.hashCode(), mDict2.hashCode());
        assertNotEquals(mDict3.hashCode(), dict1.hashCode());
        assertNotEquals(mDict3.hashCode(), dict2.hashCode());
        assertNotEquals(mDict3.hashCode(), dict1.toMutable().hashCode());
        assertNotEquals(mDict3.hashCode(), dict2.toMutable().hashCode());
        assertNotEquals(mDict3.hashCode(), mDict1.hashCode());
        assertNotEquals(mDict3.hashCode(), mDict2.hashCode());

        assertNotEquals(0, dict3.hashCode());
        assertNotEquals(dict3.hashCode(), new Object().hashCode());
        assertNotEquals(dict3.hashCode(), Integer.valueOf(1).hashCode());
        assertNotEquals(dict3.hashCode(), new HashMap<>().hashCode());
        assertNotEquals(dict3.hashCode(), new MutableDictionary().hashCode());
        assertNotEquals(dict3.hashCode(), new MutableArray().hashCode());
        assertNotEquals(mDict3.hashCode(), doc.hashCode());
        assertNotEquals(mDict3.hashCode(), mDoc.hashCode());
        assertNotEquals(mDict3.hashCode(), dict1.toMutable().hashCode());
        assertNotEquals(mDict3.hashCode(), dict2.toMutable().hashCode());
        assertNotEquals(mDict3.hashCode(), mDict1.hashCode());
        assertNotEquals(mDict3.hashCode(), mDict2.hashCode());
    }

    @Test
    public void testGetDictionary() {
        MutableDictionary mNestedDict = new MutableDictionary();
        mNestedDict.setValue("key1", 1L);
        mNestedDict.setValue("key2", "Hello");
        mNestedDict.setValue("key3", null);

        MutableDictionary mDict = new MutableDictionary();
        mDict.setValue("key1", 1L);
        mDict.setValue("key2", "Hello");
        mDict.setValue("key3", null);
        mDict.setValue("nestedDict", mNestedDict);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setDictionary("dict", mDict);

        Dictionary dict = saveDocInTestCollection(mDoc).getDictionary("dict");

        assertNotNull(dict);
        assertNull(dict.getDictionary("not-exists"));
        assertNotNull(dict.getDictionary("nestedDict"));

        Dictionary nestedDict = dict.getDictionary("nestedDict");
        assertEquals(nestedDict, mNestedDict);
        assertEquals(dict, mDict);
    }

    @Test
    public void testGetArray() {
        MutableArray mNestedArray = new MutableArray();
        mNestedArray.addValue(1L);
        mNestedArray.addValue("Hello");
        mNestedArray.addValue(null);

        MutableArray mArray = new MutableArray();
        mArray.addValue(1L);
        mArray.addValue("Hello");
        mArray.addValue(null);
        mArray.addValue(mNestedArray);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setValue("array", mArray);

        Array array = saveDocInTestCollection(mDoc).getArray("array");

        assertNotNull(array);
        assertNull(array.getArray(0));
        assertNull(array.getArray(1));
        assertNull(array.getArray(2));
        assertNotNull(array.getArray(3));

        assertThrows(IndexOutOfBoundsException.class, () -> assertNull(array.getArray(4)));

        Array nestedArray = array.getArray(3);
        assertEquals(nestedArray, mNestedArray);
        assertEquals(array, mArray);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1518
    @Test
    public void testSetValueWithDictionary() {
        MutableDictionary mDict = new MutableDictionary();
        mDict.setString("hello", "world");

        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("dict", mDict);
        Document doc = saveDocInTestCollection(mDoc);

        Dictionary dict = doc.getDictionary("dict");

        mDoc = doc.toMutable();
        mDoc.setValue("dict2", dict);

        dict = saveDocInTestCollection(mDoc).getDictionary("dict2");
        assertEquals(1, dict.count());
        assertEquals("world", dict.getString("hello"));
    }

    @Test
    public void testSetValueWithArray() {
        MutableArray mArray = new MutableArray();
        mArray.addString("hello");
        mArray.addString("world");

        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);

        Array array = doc.getArray("array");

        mDoc = doc.toMutable();
        mDoc.setValue("array2", array);

        array = saveDocInTestCollection(mDoc).getArray("array2");
        assertEquals(2, array.count());
        assertEquals("hello", array.getString(0));
        assertEquals("world", array.getString(1));
    }


    ///////////////  JSON tests
    // https://docs.google.com/document/d/1H0mnutn-XXIADvGT_EjINAOVwt0Ea8vwW70v0i_PO54

    // JSON 3.3
    @Test
    public void testDictToJSON() throws JSONException {
        MutableDocument mDoc = new MutableDocument().setDictionary("dict", makeDict());
        verifyDict(new JSONObject(saveDocInTestCollection(mDoc).getDictionary("dict").toJSON()));
    }

    // JSON 3.6.?
    @Test
    public void testDictToJSONBeforeSave() {
        assertThrows(CouchbaseLiteError.class, () -> new MutableDictionary().toJSON());
    }

    // JSON 3.5.a-b
    @Test
    public void testDictFromJSON() throws JSONException {
        MutableDictionary mDict = new MutableDictionary(BaseDbTestKt.readJSONResource("dictionary.json"));
        MutableDocument mDoc = new MutableDocument().setDictionary("dict", mDict);
        Dictionary dbDict = saveDocInTestCollection(mDoc).getDictionary("dict");
        verifyDict(dbDict, true);
        verifyDict(new JSONObject(dbDict.toJSON()));
    }

    // JSON 3.6.c.1
    @Test
    public void testDictFromBadJSON1() {
        assertThrows(IllegalArgumentException.class, () -> new MutableDictionary("{"));
    }

    // JSON 3.6.c.2
    @Test
    public void testDictFromBadJSON2() {
        assertThrows(IllegalArgumentException.class, () -> new MutableDictionary("{ab cd: \"xyz\"}"));
    }

    // JSON 3.6.c.3
    @Test
    public void testDictFromBadJSON3() {
        assertThrows(IllegalArgumentException.class, () -> new MutableDictionary("{ab: \"xyz\" cd: \"xyz\"}"));
    }

    // JSON 3.6.d
    @Test
    public void testDictFromArray() {
        assertThrows(IllegalArgumentException.class, () -> new MutableDictionary("[1, a, 1.0]"));
    }


    // Kotlin shim functions

    private Document saveDocInTestCollection(MutableDocument mDoc) {
        return saveDocInTestCollection(mDoc, getTestCollection());
    }

    private Document saveDocInTestCollection(MutableDocument mDoc, Collection collection) {
        return saveDocInCollection(mDoc, collection);
    }
}
