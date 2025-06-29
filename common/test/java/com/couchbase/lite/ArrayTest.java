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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.JSONUtils;

import org.junit.Assert;


// Tests for the Array Iterator tests are in IteratorTest
@SuppressWarnings({"ConstantConditions", "SameParameterValue"})
public class ArrayTest extends BaseDbTest {

    @Test
    public void testCreate() {
        MutableArray array = new MutableArray();
        Assert.assertEquals(0, array.count());
        Assert.assertEquals(new ArrayList<>(), array.toList());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        Assert.assertEquals(array, doc.getArray("array"));

        Document updatedDoc = saveDocInTestCollection(doc);
        Assert.assertEquals(new ArrayList<>(), updatedDoc.getArray("array").toList());
    }

    @Test
    public void testCreateWithList() {
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        MutableArray array = new MutableArray(data);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(data, array.toList());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        Assert.assertEquals(array, doc.getArray("array"));

        Document savedDoc = saveDocInTestCollection(doc);
        Assert.assertEquals(data, savedDoc.getArray("array").toList());
    }

    @Test
    public void testRecursiveArray() {
        MutableArray array = new MutableArray();
        array.addArray(array);
        Assert.assertNotSame(array, array.getArray(0));
    }

    @Test
    public void testSetList() {
        List<Object> data = new ArrayList<>();
        data.add("1");
        data.add("2");
        data.add("3");
        MutableArray array = new MutableArray();
        array.setData(data);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(data, array.toList());

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);
        Assert.assertEquals(array, doc.getArray("array"));

        // save
        Document savedDoc = saveDocInTestCollection(doc);
        Assert.assertEquals(data, savedDoc.getArray("array").toList());

        // update
        array = savedDoc.getArray("array").toMutable();
        data = new ArrayList<>();
        data.add("4");
        data.add("5");
        data.add("6");
        array.setData(data);

        Assert.assertEquals(data.size(), array.count());
        Assert.assertEquals(data, array.toList());
    }

    @Test
    public void testAddNull() {
        MutableArray array = new MutableArray();
        array.addValue(null);
        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, a -> {
            Assert.assertEquals(1, a.count());
            Assert.assertNull(a.getValue(0));
        });
    }

    @Test
    public void testAddObjects() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();

            // Add objects of all types:
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }

            MutableDocument doc = new MutableDocument("doc1");
            save(doc, "array", array, a -> {
                Assert.assertEquals(12, a.count());

                Assert.assertEquals(true, a.getValue(0));
                Assert.assertEquals(false, a.getValue(1));
                Assert.assertEquals("string", a.getValue(2));
                Assert.assertEquals(0, ((Number) a.getValue(3)).intValue());
                Assert.assertEquals(1, ((Number) a.getValue(4)).intValue());
                Assert.assertEquals(-1, ((Number) a.getValue(5)).intValue());
                Assert.assertEquals(1.1, a.getValue(6));
                Assert.assertEquals(TEST_DATE, a.getValue(7));
                Assert.assertNull(a.getValue(8));

                // dictionary
                Dictionary dict = (Dictionary) a.getValue(9);
                MutableDictionary subdict = (dict instanceof MutableDictionary)
                    ? (MutableDictionary) dict
                    : dict.toMutable();

                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                Assert.assertEquals(expectedMap, subdict.toMap());

                // array
                Array array1 = (Array) a.getValue(10);
                MutableArray subarray = array1 instanceof MutableArray
                    ? (MutableArray) array1
                    : array1.toMutable();

                List<Object> expected = new ArrayList<>();
                expected.add("a");
                expected.add("b");
                expected.add("c");
                Assert.assertEquals(expected, subarray.toList());

                // blob
                verifyBlob(a.getValue(11));
            });
        }
    }

    @Test
    public void testAddObjectsToExistingArray() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }

            // Save
            MutableDocument doc = new MutableDocument(docId(i));
            doc.setValue("array", array);
            doc = saveDocInTestCollection(doc).toMutable();

            // Get an existing array:
            array = doc.getArray("array");
            Assert.assertNotNull(array);
            Assert.assertEquals(12, array.count());

            // Update:
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(24, array.count());

            save(doc, "array", array, a -> {
                Assert.assertEquals(24, a.count());

                Assert.assertEquals(true, a.getValue(12));
                Assert.assertEquals(false, a.getValue(12 + 1));
                Assert.assertEquals("string", a.getValue(12 + 2));
                Assert.assertEquals(0, ((Number) a.getValue(12 + 3)).intValue());
                Assert.assertEquals(1, ((Number) a.getValue(12 + 4)).intValue());
                Assert.assertEquals(-1, ((Number) a.getValue(12 + 5)).intValue());
                Assert.assertEquals(1.1, a.getValue(12 + 6));
                Assert.assertEquals(TEST_DATE, a.getValue(12 + 7));
                Assert.assertNull(a.getValue(12 + 8));

                // dictionary
                Dictionary dict = (Dictionary) a.getValue(12 + 9);
                MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                    (MutableDictionary) dict : dict.toMutable();
                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                Assert.assertEquals(expectedMap, subdict.toMap());

                // array
                Array array1 = (Array) a.getValue(12 + 10);
                MutableArray subarray = array1 instanceof MutableArray ?
                    (MutableArray) array1 : array1.toMutable();
                List<Object> expected = new ArrayList<>();
                expected.add("a");
                expected.add("b");
                expected.add("c");
                Assert.assertEquals(expected, subarray.toList());

                // blob
                verifyBlob(a.getValue(12 + 11));
            });
        }
    }

    @Test
    public void testSetObject() {
        List<Object> data = arrayOfAllTypes();

        // Prepare CBLArray with NSNull placeholders:
        MutableArray array = new MutableArray();
        for (int i = 0; i < data.size(); i++) { array.addValue(null); }

        // Set object at index:
        for (int i = 0; i < data.size(); i++) { array.setValue(i, data.get(i)); }

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, a -> {
            Assert.assertEquals(12, a.count());

            Assert.assertEquals(true, a.getValue(0));
            Assert.assertEquals(false, a.getValue(1));
            Assert.assertEquals("string", a.getValue(2));
            Assert.assertEquals(0, ((Number) a.getValue(3)).intValue());
            Assert.assertEquals(1, ((Number) a.getValue(4)).intValue());
            Assert.assertEquals(-1, ((Number) a.getValue(5)).intValue());
            Assert.assertEquals(1.1, a.getValue(6));
            Assert.assertEquals(TEST_DATE, a.getValue(7));
            Assert.assertNull(a.getValue(8));

            // dictionary
            Dictionary dict = (Dictionary) a.getValue(9);
            MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                (MutableDictionary) dict : dict.toMutable();
            Map<String, Object> expectedMap = new HashMap<>();
            expectedMap.put("name", "Scott Tiger");
            Assert.assertEquals(expectedMap, subdict.toMap());

            // array
            Array array1 = (Array) a.getValue(10);
            MutableArray subarray = array1 instanceof MutableArray ?
                (MutableArray) array1 : array1.toMutable();
            List<Object> expected = new ArrayList<>();
            expected.add("a");
            expected.add("b");
            expected.add("c");
            Assert.assertEquals(expected, subarray.toList());

            // blob
            verifyBlob(a.getValue(11));
        });
    }

    @Test
    public void testSetObjectToExistingArray() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }

            // Save
            MutableDocument doc = new MutableDocument(docId(i));
            doc.setArray("array", array);
            doc = saveDocInTestCollection(doc).toMutable();
            MutableArray gotArray = doc.getArray("array");

            List<Object> data = arrayOfAllTypes();
            Assert.assertEquals(data.size(), gotArray.count());

            // reverse the array
            for (int j = 0; j < data.size(); j++) { gotArray.setValue(j, data.get(data.size() - j - 1)); }

            save(doc, "array", gotArray, a -> {
                Assert.assertEquals(12, a.count());

                Assert.assertEquals(true, a.getValue(11));
                Assert.assertEquals(false, a.getValue(10));
                Assert.assertEquals("string", a.getValue(9));
                Assert.assertEquals(0, ((Number) a.getValue(8)).intValue());
                Assert.assertEquals(1, ((Number) a.getValue(7)).intValue());
                Assert.assertEquals(-1, ((Number) a.getValue(6)).intValue());
                Assert.assertEquals(1.1, a.getValue(5));
                Assert.assertEquals(TEST_DATE, a.getValue(4));
                Assert.assertNull(a.getValue(3));

                // dictionary
                Dictionary dict = (Dictionary) a.getValue(2);
                MutableDictionary subdict = (dict instanceof MutableDictionary) ?
                    (MutableDictionary) dict : dict.toMutable();

                Map<String, Object> expectedMap = new HashMap<>();
                expectedMap.put("name", "Scott Tiger");
                Assert.assertEquals(expectedMap, subdict.toMap());

                // array
                Array array1 = (Array) a.getValue(1);
                MutableArray subarray = (array1 instanceof MutableArray) ?
                    (MutableArray) array1 : array1.toMutable();

                List<Object> expected = new ArrayList<>();
                expected.add("a");
                expected.add("b");
                expected.add("c");
                Assert.assertEquals(expected, subarray.toList());

                // blob
                verifyBlob(a.getValue(0));
            });
        }
    }

    @Test
    public void testSetObjectOutOfBound() {
        MutableArray array = new MutableArray();
        array.addValue("a");

        Assert.assertThrows(IndexOutOfBoundsException.class, () -> array.setValue(-1, "b"));

        Assert.assertThrows(IndexOutOfBoundsException.class, () -> array.setValue(1, "b"));
    }

    @Test
    public void testInsertObject() {
        MutableArray array = new MutableArray();

        array.insertValue(0, "a");
        Assert.assertEquals(1, array.count());
        Assert.assertEquals("a", array.getValue(0));

        array.insertValue(0, "c");
        Assert.assertEquals(2, array.count());
        Assert.assertEquals("c", array.getValue(0));
        Assert.assertEquals("a", array.getValue(1));

        array.insertValue(1, "d");
        Assert.assertEquals(3, array.count());
        Assert.assertEquals("c", array.getValue(0));
        Assert.assertEquals("d", array.getValue(1));
        Assert.assertEquals("a", array.getValue(2));

        array.insertValue(2, "e");
        Assert.assertEquals(4, array.count());
        Assert.assertEquals("c", array.getValue(0));
        Assert.assertEquals("d", array.getValue(1));
        Assert.assertEquals("e", array.getValue(2));
        Assert.assertEquals("a", array.getValue(3));

        array.insertValue(4, "f");
        Assert.assertEquals(5, array.count());
        Assert.assertEquals("c", array.getValue(0));
        Assert.assertEquals("d", array.getValue(1));
        Assert.assertEquals("e", array.getValue(2));
        Assert.assertEquals("a", array.getValue(3));
        Assert.assertEquals("f", array.getValue(4));
    }

    @Test
    public void testInsertObjectToExistingArray() {
        MutableDocument mDoc = new MutableDocument("doc1");
        mDoc.setValue("array", new MutableArray());
        Document doc = saveDocInTestCollection(mDoc);
        mDoc = doc.toMutable();

        MutableArray mArray = mDoc.getArray("array");
        Assert.assertNotNull(mArray);
        mArray.insertValue(0, "a");
        doc = save(mDoc, "array", mArray, a -> {
            Assert.assertEquals(1, a.count());
            Assert.assertEquals("a", a.getValue(0));
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        Assert.assertNotNull(mArray);
        mArray.insertValue(0, "c");
        doc = save(mDoc, "array", mArray, a -> {
            Assert.assertEquals(2, a.count());
            Assert.assertEquals("c", a.getValue(0));
            Assert.assertEquals("a", a.getValue(1));
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        Assert.assertNotNull(mArray);
        mArray.insertValue(1, "d");
        doc = save(mDoc, "array", mArray, a -> {
            Assert.assertEquals(3, a.count());
            Assert.assertEquals("c", a.getValue(0));
            Assert.assertEquals("d", a.getValue(1));
            Assert.assertEquals("a", a.getValue(2));
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        Assert.assertNotNull(mArray);
        mArray.insertValue(2, "e");
        doc = save(mDoc, "array", mArray, a -> {
            Assert.assertEquals(4, a.count());
            Assert.assertEquals("c", a.getValue(0));
            Assert.assertEquals("d", a.getValue(1));
            Assert.assertEquals("e", a.getValue(2));
            Assert.assertEquals("a", a.getValue(3));
        });

        mDoc = doc.toMutable();
        mArray = mDoc.getArray("array");
        Assert.assertNotNull(mArray);
        mArray.insertValue(4, "f");
        save(mDoc, "array", mArray, a -> {
            Assert.assertEquals(5, a.count());
            Assert.assertEquals("c", a.getValue(0));
            Assert.assertEquals("d", a.getValue(1));
            Assert.assertEquals("e", a.getValue(2));
            Assert.assertEquals("a", a.getValue(3));
            Assert.assertEquals("f", a.getValue(4));
        });
    }

    @Test
    public void testInsertObjectOutOfBound() {
        MutableArray array = new MutableArray();
        array.addValue("a");

        Assert.assertThrows(IndexOutOfBoundsException.class, () -> array.insertValue(-1, "b"));

        Assert.assertThrows(IndexOutOfBoundsException.class, () -> array.insertValue(2, "b"));
    }

    @Test
    public void testRemove() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }

            for (int j = array.count() - 1; j >= 0; j--) {
                array.remove(j);
            }

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertEquals(0, a.count());
                Assert.assertEquals(new ArrayList<>(), a.toList());
            });
        }
    }

    @Test
    public void testRemoveExistingArray() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }

            MutableDocument doc = new MutableDocument(docId(i));
            doc.setValue("array", array);
            doc = saveDocInTestCollection(doc).toMutable();
            array = doc.getArray("array");

            for (int j = array.count() - 1; j >= 0; j--) {
                array.remove(j);
            }

            save(doc, "array", array, a -> {
                Assert.assertEquals(0, a.count());
                Assert.assertEquals(new ArrayList<>(), a.toList());
            });
        }
    }

    @Test
    public void testRemoveOutOfBound() {
        MutableArray array = new MutableArray();
        array.addValue("a");

        Assert.assertThrows(IndexOutOfBoundsException.class, () -> array.remove(-1));

        Assert.assertThrows(IndexOutOfBoundsException.class, () -> array.remove(1));
    }

    @Test
    public void testCount() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> Assert.assertEquals(12, a.count()));
        }
    }

    @Test
    public void testGetString() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertNull(a.getString(0));
                Assert.assertNull(a.getString(1));
                Assert.assertEquals("string", a.getString(2));
                Assert.assertNull(a.getString(3));
                Assert.assertNull(a.getString(4));
                Assert.assertNull(a.getString(5));
                Assert.assertNull(a.getString(6));
                Assert.assertEquals(TEST_DATE, a.getString(7));
                Assert.assertNull(a.getString(8));
                Assert.assertNull(a.getString(9));
                Assert.assertNull(a.getString(10));
                Assert.assertNull(a.getString(11));
            });
        }
    }

    @Test
    public void testGetNumber() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertEquals(1, a.getNumber(0).intValue());
                Assert.assertEquals(0, a.getNumber(1).intValue());
                Assert.assertNull(a.getNumber(2));
                Assert.assertEquals(0, a.getNumber(3).intValue());
                Assert.assertEquals(1, a.getNumber(4).intValue());
                Assert.assertEquals(-1, a.getNumber(5).intValue());
                Assert.assertEquals(1.1, a.getNumber(6));
                Assert.assertNull(a.getNumber(7));
                Assert.assertNull(a.getNumber(8));
                Assert.assertNull(a.getNumber(9));
                Assert.assertNull(a.getNumber(10));
                Assert.assertNull(a.getNumber(11));
            });
        }
    }

    @Test
    public void testGetInteger() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertEquals(1, a.getInt(0));
                Assert.assertEquals(0, a.getInt(1));
                Assert.assertEquals(0, a.getInt(2));
                Assert.assertEquals(0, a.getInt(3));
                Assert.assertEquals(1, a.getInt(4));
                Assert.assertEquals(-1, a.getInt(5));
                Assert.assertEquals(1, a.getInt(6));
                Assert.assertEquals(0, a.getInt(7));
                Assert.assertEquals(0, a.getInt(8));
                Assert.assertEquals(0, a.getInt(9));
                Assert.assertEquals(0, a.getInt(10));
                Assert.assertEquals(0, a.getInt(11));
            });
        }
    }

    @Test
    public void testGetLong() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertEquals(1, a.getLong(0));
                Assert.assertEquals(0, a.getLong(1));
                Assert.assertEquals(0, a.getLong(2));
                Assert.assertEquals(0, a.getLong(3));
                Assert.assertEquals(1, a.getLong(4));
                Assert.assertEquals(-1, a.getLong(5));
                Assert.assertEquals(1, a.getLong(6));
                Assert.assertEquals(0, a.getLong(7));
                Assert.assertEquals(0, a.getLong(8));
                Assert.assertEquals(0, a.getLong(9));
                Assert.assertEquals(0, a.getLong(10));
                Assert.assertEquals(0, a.getLong(11));
            });
        }
    }

    @Test
    public void testGetFloat() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertEquals(1.0f, a.getFloat(0), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(1), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(2), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(3), 0.0f);
                Assert.assertEquals(1.0f, a.getFloat(4), 0.0f);
                Assert.assertEquals(-1.0f, a.getFloat(5), 0.0f);
                Assert.assertEquals(1.1f, a.getFloat(6), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(7), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(8), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(9), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(10), 0.0f);
                Assert.assertEquals(0.0f, a.getFloat(11), 0.0f);
            });
        }
    }

    @Test
    public void testGetDouble() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertEquals(1.0, a.getDouble(0), 0.0);
                Assert.assertEquals(0.0, a.getDouble(1), 0.0);
                Assert.assertEquals(0.0, a.getDouble(2), 0.0);
                Assert.assertEquals(0.0, a.getDouble(3), 0.0);
                Assert.assertEquals(1.0, a.getDouble(4), 0.0);
                Assert.assertEquals(-1.0, a.getDouble(5), 0.0);
                Assert.assertEquals(1.1, a.getDouble(6), 0.0);
                Assert.assertEquals(0.0, a.getDouble(7), 0.0);
                Assert.assertEquals(0.0, a.getDouble(8), 0.0);
                Assert.assertEquals(0.0, a.getDouble(9), 0.0);
                Assert.assertEquals(0.0, a.getDouble(10), 0.0);
                Assert.assertEquals(0.0, a.getDouble(11), 0.0);
            });
        }
    }

    @Test
    public void testSetGetMinMaxNumbers() {
        MutableArray array = new MutableArray();
        array.addValue(Integer.MIN_VALUE);
        array.addValue(Integer.MAX_VALUE);
        array.addValue(Long.MIN_VALUE);
        array.addValue(Long.MAX_VALUE);
        array.addValue(Float.MIN_VALUE);
        array.addValue(Float.MAX_VALUE);
        array.addValue(Double.MIN_VALUE);
        array.addValue(Double.MAX_VALUE);

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, a -> {
            Assert.assertEquals(Integer.MIN_VALUE, a.getNumber(0).intValue());
            Assert.assertEquals(Integer.MAX_VALUE, a.getNumber(1).intValue());
            Assert.assertEquals(Integer.MIN_VALUE, ((Number) a.getValue(0)).intValue());
            Assert.assertEquals(Integer.MAX_VALUE, ((Number) a.getValue(1)).intValue());
            Assert.assertEquals(Integer.MIN_VALUE, a.getInt(0));
            Assert.assertEquals(Integer.MAX_VALUE, a.getInt(1));

            Assert.assertEquals(Long.MIN_VALUE, a.getNumber(2));
            Assert.assertEquals(Long.MAX_VALUE, a.getNumber(3));
            Assert.assertEquals(Long.MIN_VALUE, a.getValue(2));
            Assert.assertEquals(Long.MAX_VALUE, a.getValue(3));
            Assert.assertEquals(Long.MIN_VALUE, a.getLong(2));
            Assert.assertEquals(Long.MAX_VALUE, a.getLong(3));

            Assert.assertEquals(Float.MIN_VALUE, a.getNumber(4));
            Assert.assertEquals(Float.MAX_VALUE, a.getNumber(5));
            Assert.assertEquals(Float.MIN_VALUE, a.getValue(4));
            Assert.assertEquals(Float.MAX_VALUE, a.getValue(5));
            Assert.assertEquals(Float.MIN_VALUE, a.getFloat(4), 0.0f);
            Assert.assertEquals(Float.MAX_VALUE, a.getFloat(5), 0.0f);

            Assert.assertEquals(Double.MIN_VALUE, a.getNumber(6));
            Assert.assertEquals(Double.MAX_VALUE, a.getNumber(7));
            Assert.assertEquals(Double.MIN_VALUE, a.getValue(6));
            Assert.assertEquals(Double.MAX_VALUE, a.getValue(7));
            Assert.assertEquals(Double.MIN_VALUE, a.getDouble(6), 0.0f);
            Assert.assertEquals(Double.MAX_VALUE, a.getDouble(7), 0.0f);
        });
    }

    @Test
    public void testSetGetFloatNumbers() {
        MutableArray array = new MutableArray();
        array.addValue(1.00);
        array.addValue(1.49);
        array.addValue(1.50);
        array.addValue(1.51);
        array.addValue(1.99);

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array, a -> {
            // NOTE: Number which has no floating part is stored as Integer.
            //       This causes type difference between before and after storing data
            //       into the database.
            Assert.assertEquals(1.00, ((Number) a.getValue(0)).doubleValue(), 0.0);
            Assert.assertEquals(1.00, a.getNumber(0).doubleValue(), 0.0);
            Assert.assertEquals(1, a.getInt(0));
            Assert.assertEquals(1L, a.getLong(0));
            Assert.assertEquals(1.00F, a.getFloat(0), 0.0F);
            Assert.assertEquals(1.00, a.getDouble(0), 0.0);

            Assert.assertEquals(1.49, a.getValue(1));
            Assert.assertEquals(1.49, a.getNumber(1));
            Assert.assertEquals(1, a.getInt(1));
            Assert.assertEquals(1L, a.getLong(1));
            Assert.assertEquals(1.49F, a.getFloat(1), 0.0F);
            Assert.assertEquals(1.49, a.getDouble(1), 0.0);

            Assert.assertEquals(1.50, ((Number) a.getValue(2)).doubleValue(), 0.0);
            Assert.assertEquals(1.50, a.getNumber(2).doubleValue(), 0.0);
            Assert.assertEquals(1, a.getInt(2));
            Assert.assertEquals(1L, a.getLong(2));
            Assert.assertEquals(1.50F, a.getFloat(2), 0.0F);
            Assert.assertEquals(1.50, a.getDouble(2), 0.0);

            Assert.assertEquals(1.51, a.getValue(3));
            Assert.assertEquals(1.51, a.getNumber(3));
            Assert.assertEquals(1, a.getInt(3));
            Assert.assertEquals(1L, a.getLong(3));
            Assert.assertEquals(1.51F, a.getFloat(3), 0.0F);
            Assert.assertEquals(1.51, a.getDouble(3), 0.0);

            Assert.assertEquals(1.99, a.getValue(4));
            Assert.assertEquals(1.99, a.getNumber(4));
            Assert.assertEquals(1, a.getInt(4));
            Assert.assertEquals(1L, a.getLong(4));
            Assert.assertEquals(1.99F, a.getFloat(4), 0.0F);
            Assert.assertEquals(1.99, a.getDouble(4), 0.0);
        });
    }

    @Test
    public void testGetBoolean() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertTrue(a.getBoolean(0));
                Assert.assertFalse(a.getBoolean(1));
                Assert.assertTrue(a.getBoolean(2));
                Assert.assertFalse(a.getBoolean(3));
                Assert.assertTrue(a.getBoolean(4));
                Assert.assertTrue(a.getBoolean(5));
                Assert.assertTrue(a.getBoolean(6));
                Assert.assertTrue(a.getBoolean(7));
                Assert.assertFalse(a.getBoolean(8));
                Assert.assertTrue(a.getBoolean(9));
                Assert.assertTrue(a.getBoolean(10));
                Assert.assertTrue(a.getBoolean(11));
            });
        }
    }

    @Test
    public void testGetDate() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertNull(a.getDate(0));
                Assert.assertNull(a.getDate(1));
                Assert.assertNull(a.getDate(2));
                Assert.assertNull(a.getDate(3));
                Assert.assertNull(a.getDate(4));
                Assert.assertNull(a.getDate(5));
                Assert.assertNull(a.getDate(6));
                Assert.assertEquals(TEST_DATE, JSONUtils.toJSONString(a.getDate(7)));
                Assert.assertNull(a.getDate(8));
                Assert.assertNull(a.getDate(9));
                Assert.assertNull(a.getDate(10));
                Assert.assertNull(a.getDate(11));
            });
        }
    }

    @Test
    public void testGetMap() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertNull(a.getDictionary(0));
                Assert.assertNull(a.getDictionary(1));
                Assert.assertNull(a.getDictionary(2));
                Assert.assertNull(a.getDictionary(3));
                Assert.assertNull(a.getDictionary(4));
                Assert.assertNull(a.getDictionary(5));
                Assert.assertNull(a.getDictionary(6));
                Assert.assertNull(a.getDictionary(7));
                Assert.assertNull(a.getDictionary(8));
                Map<String, Object> map = new HashMap<>();
                map.put("name", "Scott Tiger");
                Assert.assertEquals(map, a.getDictionary(9).toMap());
                Assert.assertNull(a.getDictionary(10));
                Assert.assertNull(a.getDictionary(11));
            });
        }
    }

    @Test
    public void testGetArray() {
        for (int i = 0; i < 2; i++) {
            MutableArray array = new MutableArray();
            if (i % 2 == 0) { populateData(array); }
            else { populateDataByType(array); }
            Assert.assertEquals(12, array.count());

            MutableDocument doc = new MutableDocument(docId(i));
            save(doc, "array", array, a -> {
                Assert.assertNull(a.getArray(0));
                Assert.assertNull(a.getArray(1));
                Assert.assertNull(a.getArray(2));
                Assert.assertNull(a.getArray(3));
                Assert.assertNull(a.getArray(4));
                Assert.assertNull(a.getArray(5));
                Assert.assertNull(a.getArray(6));
                Assert.assertNull(a.getArray(7));
                Assert.assertNull(a.getArray(9));
                Assert.assertEquals(Arrays.asList("a", "b", "c"), a.getArray(10).toList());
                Assert.assertNull(a.getDictionary(11));
            });
        }
    }

    @Test
    public void testSetNestedArray() {
        MutableArray array1 = new MutableArray();
        MutableArray array2 = new MutableArray();
        MutableArray array3 = new MutableArray();

        array1.addValue(array2);
        array2.addValue(array3);
        array3.addValue("a");
        array3.addValue("b");
        array3.addValue("c");

        MutableDocument doc = new MutableDocument("doc1");
        save(doc, "array", array1, a1 -> {
            Assert.assertEquals(1, a1.count());
            Array a2 = a1.getArray(0);
            Assert.assertEquals(1, a2.count());
            Array a3 = a2.getArray(0);
            Assert.assertEquals(3, a3.count());
            Assert.assertEquals("a", a3.getValue(0));
            Assert.assertEquals("b", a3.getValue(1));
            Assert.assertEquals("c", a3.getValue(2));
        });
    }

    @Test
    public void testReplaceArray() {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray array1 = new MutableArray();
        array1.addValue("a");
        array1.addValue("b");
        array1.addValue("c");
        Assert.assertEquals(3, array1.count());
        Assert.assertEquals(Arrays.asList("a", "b", "c"), array1.toList());
        doc.setValue("array", array1);

        MutableArray array2 = new MutableArray();
        array2.addValue("x");
        array2.addValue("y");
        array2.addValue("z");
        Assert.assertEquals(3, array2.count());
        Assert.assertEquals(Arrays.asList("x", "y", "z"), array2.toList());

        // Replace:
        doc.setValue("array", array2);

        // array1 should be now detached:
        array1.addValue("d");
        Assert.assertEquals(4, array1.count());
        Assert.assertEquals(Arrays.asList("a", "b", "c", "d"), array1.toList());

        // Check array2:
        Assert.assertEquals(3, array2.count());
        Assert.assertEquals(Arrays.asList("x", "y", "z"), array2.toList());

        // Save:
        doc = saveDocInTestCollection(doc).toMutable();

        // Check current array:
        Assert.assertNotSame(doc.getArray("array"), array2);
        array2 = doc.getArray("array");
        Assert.assertEquals(3, array2.count());
        Assert.assertEquals(Arrays.asList("x", "y", "z"), array2.toList());
    }

    @Test
    public void testReplaceArrayDifferentType() {
        MutableDocument doc = new MutableDocument("doc1");
        MutableArray array1 = new MutableArray();
        array1.addValue("a");
        array1.addValue("b");
        array1.addValue("c");
        Assert.assertEquals(3, array1.count());
        Assert.assertEquals(Arrays.asList("a", "b", "c"), array1.toList());
        doc.setValue("array", array1);

        // Replace:
        doc.setValue("array", "Daniel Tiger");

        // array1 should be now detached:
        array1.addValue("d");
        Assert.assertEquals(4, array1.count());
        Assert.assertEquals(Arrays.asList("a", "b", "c", "d"), array1.toList());

        // Save:
        doc = saveDocInTestCollection(doc).toMutable();
        Assert.assertEquals("Daniel Tiger", doc.getString("array"));
    }

    @Test
    public void testEnumeratingArray() {
        MutableArray array = new MutableArray();
        for (int i = 0; i < 20; i++) {
            array.addValue(i);
        }
        List<Object> content = array.toList();

        List<Object> result = new ArrayList<>();
        int counter = 0;
        for (Object item: array) {
            Assert.assertNotNull(item);
            result.add(item);
            counter++;
        }
        Assert.assertEquals(content, result);
        Assert.assertEquals(array.count(), counter);

        // Update:
        array.remove(1);
        array.addValue(20);
        array.addValue(21);
        content = array.toList();

        result = new ArrayList<>();
        for (Object item: array) {
            Assert.assertNotNull(item);
            result.add(item);
        }
        Assert.assertEquals(content, result);

        MutableDocument doc = new MutableDocument("doc1");
        doc.setValue("array", array);

        final List<Object> c = content;
        save(doc, "array", array, array1 -> {
            List<Object> r = new ArrayList<>();
            for (Object item: array1) {
                Assert.assertNotNull(item);
                r.add(item);
            }
            Assert.assertEquals(c.toString(), r.toString());
        });
    }

    @Test
    public void testSetNull() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addValue(null);
        mArray.addString(null);
        mArray.addNumber(null);
        mArray.addDate(null);
        mArray.addArray(null);
        mArray.addDictionary(null);
        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(6, array.count());
        Assert.assertNull(array.getValue(0));
        Assert.assertNull(array.getValue(1));
        Assert.assertNull(array.getValue(2));
        Assert.assertNull(array.getValue(3));
        Assert.assertNull(array.getValue(4));
        Assert.assertNull(array.getValue(5));
    }

    @SuppressWarnings("AssertBetweenInconvertibleTypes")
    @Test
    public void testEquals() {

        // mArray1 and mArray2 have exactly same data
        // mArray3 is different
        // mArray4 is different
        // mArray5 is different

        MutableArray mArray1 = new MutableArray();
        mArray1.addValue(1L);
        mArray1.addValue("Hello");
        mArray1.addValue(null);

        MutableArray mArray2 = new MutableArray();
        mArray2.addValue(1L);
        mArray2.addValue("Hello");
        mArray2.addValue(null);

        MutableArray mArray3 = new MutableArray();
        mArray3.addValue(100L);
        mArray3.addValue(true);

        MutableArray mArray4 = new MutableArray();
        mArray4.addValue(100L);

        MutableArray mArray5 = new MutableArray();
        mArray4.addValue(100L);
        mArray3.addValue(false);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setArray("array1", mArray1);
        mDoc.setArray("array2", mArray2);
        mDoc.setArray("array3", mArray3);
        mDoc.setArray("array4", mArray4);
        mDoc.setArray("array5", mArray5);

        Document doc = saveDocInTestCollection(mDoc);
        Array array1 = doc.getArray("array1");
        Array array2 = doc.getArray("array2");
        Array array3 = doc.getArray("array3");
        Array array4 = doc.getArray("array4");
        Array array5 = doc.getArray("array5");

        // compare array1, array2, marray1, and marray2
        Assert.assertEquals(array1, array1);
        Assert.assertEquals(array2, array2);
        Assert.assertEquals(array1, array2);
        Assert.assertEquals(array2, array1);
        Assert.assertEquals(array1, array1.toMutable());
        Assert.assertEquals(array1, array2.toMutable());
        Assert.assertEquals(array1.toMutable(), array1);
        Assert.assertEquals(array2.toMutable(), array1);
        Assert.assertEquals(array1, mArray1);
        Assert.assertEquals(array1, mArray2);
        Assert.assertEquals(array2, mArray1);
        Assert.assertEquals(array2, mArray2);
        Assert.assertEquals(mArray1, array1);
        Assert.assertEquals(mArray2, array1);
        Assert.assertEquals(mArray1, array2);
        Assert.assertEquals(mArray2, array2);
        Assert.assertEquals(mArray1, mArray1);
        Assert.assertEquals(mArray2, mArray2);
        Assert.assertEquals(mArray1, mArray1);
        Assert.assertEquals(mArray2, mArray2);

        // compare array1, array3, marray1, and marray3
        Assert.assertEquals(array3, array3);
        Assert.assertNotEquals(array1, array3);
        Assert.assertNotEquals(array3, array1);
        Assert.assertNotEquals(array1, array3.toMutable());
        Assert.assertNotEquals(array3.toMutable(), array1);
        Assert.assertNotEquals(array1, mArray3);
        Assert.assertNotEquals(array3, mArray1);
        Assert.assertEquals(array3, mArray3);
        Assert.assertNotEquals(mArray3, array1);
        Assert.assertNotEquals(mArray1, array3);
        Assert.assertEquals(mArray3, array3);
        Assert.assertEquals(mArray3, mArray3);
        Assert.assertEquals(mArray3, mArray3);

        // compare array1, array4, marray1, and marray4
        Assert.assertEquals(array4, array4);
        Assert.assertNotEquals(array1, array4);
        Assert.assertNotEquals(array4, array1);
        Assert.assertNotEquals(array1, array4.toMutable());
        Assert.assertNotEquals(array4.toMutable(), array1);
        Assert.assertNotEquals(array1, mArray4);
        Assert.assertNotEquals(array4, mArray1);
        Assert.assertEquals(array4, mArray4);
        Assert.assertNotEquals(mArray4, array1);
        Assert.assertNotEquals(mArray1, array4);
        Assert.assertEquals(mArray4, array4);
        Assert.assertEquals(mArray4, mArray4);
        Assert.assertEquals(mArray4, mArray4);

        // compare array3, array4, marray3, and marray4
        Assert.assertNotEquals(array3, array4);
        Assert.assertNotEquals(array4, array3);
        Assert.assertNotEquals(array3, array4.toMutable());
        Assert.assertNotEquals(array4.toMutable(), array3);
        Assert.assertNotEquals(array3, mArray4);
        Assert.assertNotEquals(array4, mArray3);
        Assert.assertNotEquals(mArray4, array3);
        Assert.assertNotEquals(mArray3, array4);

        // compare array3, array5, marray3, and marray5
        Assert.assertNotEquals(array3, array5);
        Assert.assertNotEquals(array5, array3);
        Assert.assertNotEquals(array3, array5.toMutable());
        Assert.assertNotEquals(array5.toMutable(), array3);
        Assert.assertNotEquals(array3, mArray5);
        Assert.assertNotEquals(array5, mArray3);
        Assert.assertNotEquals(mArray5, array3);
        Assert.assertNotEquals(mArray3, array5);

        // compare array5, array4, mArray5, and marray4
        Assert.assertNotEquals(array5, array4);
        Assert.assertNotEquals(array4, array5);
        Assert.assertNotEquals(array5, array4.toMutable());
        Assert.assertNotEquals(array4.toMutable(), array5);
        Assert.assertNotEquals(array5, mArray4);
        Assert.assertNotEquals(array4, mArray5);
        Assert.assertNotEquals(mArray4, array5);
        Assert.assertNotEquals(mArray5, array4);

        // against other type
        Assert.assertNotEquals(null, array3);
        Assert.assertNotEquals(new Object(), array3);
        Assert.assertNotEquals(1, array3);
        Assert.assertNotEquals(new HashMap<>(), array3);
        Assert.assertNotEquals(new MutableDictionary(), array3);
        Assert.assertNotEquals(new MutableArray(), array3);
        Assert.assertNotEquals(array3, doc);
        Assert.assertNotEquals(array3, mDoc);
    }

    @Test
    public void testHashCode() {
        // mArray1 and mArray2 have exactly same data
        // mArray3 is different
        // mArray4 is different
        // mArray5 is different

        MutableArray mArray1 = new MutableArray();
        mArray1.addValue(1L);
        mArray1.addValue("Hello");
        mArray1.addValue(null);

        MutableArray mArray2 = new MutableArray();
        mArray2.addValue(1L);
        mArray2.addValue("Hello");
        mArray2.addValue(null);

        MutableArray mArray3 = new MutableArray();
        mArray3.addValue(100L);
        mArray3.addValue(true);

        MutableArray mArray4 = new MutableArray();
        mArray4.addValue(100L);

        MutableArray mArray5 = new MutableArray();
        mArray4.addValue(100L);
        mArray3.addValue(false);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setArray("array1", mArray1);
        mDoc.setArray("array2", mArray2);
        mDoc.setArray("array3", mArray3);
        mDoc.setArray("array4", mArray4);
        mDoc.setArray("array5", mArray5);

        Document doc = saveDocInTestCollection(mDoc);
        Array array1 = doc.getArray("array1");
        Array array2 = doc.getArray("array2");
        Array array3 = doc.getArray("array3");
        Array array4 = doc.getArray("array4");
        Array array5 = doc.getArray("array5");

        Assert.assertEquals(array1.hashCode(), array1.hashCode());
        Assert.assertEquals(array1.hashCode(), array2.hashCode());
        Assert.assertEquals(array2.hashCode(), array1.hashCode());
        Assert.assertEquals(array1.hashCode(), array1.toMutable().hashCode());
        Assert.assertEquals(array1.hashCode(), array2.toMutable().hashCode());
        Assert.assertEquals(array1.hashCode(), mArray1.hashCode());
        Assert.assertEquals(array1.hashCode(), mArray2.hashCode());
        Assert.assertEquals(array2.hashCode(), mArray1.hashCode());
        Assert.assertEquals(array2.hashCode(), mArray2.hashCode());

        Assert.assertNotEquals(array3.hashCode(), array1.hashCode());
        Assert.assertNotEquals(array3.hashCode(), array2.hashCode());
        Assert.assertNotEquals(array3.hashCode(), array1.toMutable().hashCode());
        Assert.assertNotEquals(array3.hashCode(), array2.toMutable().hashCode());
        Assert.assertNotEquals(array3.hashCode(), mArray1.hashCode());
        Assert.assertNotEquals(array3.hashCode(), mArray2.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), array1.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), array2.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), array1.toMutable().hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), array2.toMutable().hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), mArray1.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), mArray2.hashCode());

        Assert.assertNotEquals(array1.hashCode(), array4.hashCode());
        Assert.assertNotEquals(array1.hashCode(), array5.hashCode());
        Assert.assertNotEquals(array2.hashCode(), array4.hashCode());
        Assert.assertNotEquals(array2.hashCode(), array5.hashCode());
        Assert.assertNotEquals(array3.hashCode(), array4.hashCode());
        Assert.assertNotEquals(array3.hashCode(), array5.hashCode());

        Assert.assertNotEquals(0, array3.hashCode());
        Assert.assertNotEquals(array3.hashCode(), new Object().hashCode());
        Assert.assertNotEquals(array3.hashCode(), Integer.valueOf(1).hashCode());
        Assert.assertNotEquals(array3.hashCode(), new HashMap<>().hashCode());
        Assert.assertNotEquals(array3.hashCode(), new MutableDictionary().hashCode());
        Assert.assertNotEquals(array3.hashCode(), new MutableArray().hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), doc.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), mDoc.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), array1.toMutable().hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), array2.toMutable().hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), mArray1.hashCode());
        Assert.assertNotEquals(mArray3.hashCode(), mArray2.hashCode());
    }


    @Test
    public void testGetDictionary() {
        MutableDictionary mNestedDict = new MutableDictionary();
        mNestedDict.setValue("key1", 1L);
        mNestedDict.setValue("key2", "Hello");
        mNestedDict.setValue("key3", null);

        MutableArray mArray = new MutableArray();
        mArray.addValue(1L);
        mArray.addValue("Hello");
        mArray.addValue(null);
        mArray.addValue(mNestedDict);

        MutableDocument mDoc = new MutableDocument("test");
        mDoc.setArray("array", mArray);

        Document doc = saveDocInTestCollection(mDoc);
        Array array = doc.getArray("array");

        Assert.assertNotNull(array);
        Assert.assertNull(array.getDictionary(0));
        Assert.assertNull(array.getDictionary(1));
        Assert.assertNull(array.getDictionary(2));
        Assert.assertNotNull(array.getDictionary(3));

        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> Assert.assertNull(array.getDictionary(4)));

        Dictionary nestedDict = array.getDictionary(3);
        Assert.assertEquals(nestedDict, mNestedDict);
        Assert.assertEquals(array, mArray);
    }

    @Test
    public void testGetArray2() {
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

        Document doc = saveDocInTestCollection(mDoc);
        Array array = doc.getArray("array");

        Assert.assertNotNull(array);
        Assert.assertNull(array.getArray(0));
        Assert.assertNull(array.getArray(1));
        Assert.assertNull(array.getArray(2));
        Assert.assertNotNull(array.getArray(3));

        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> Assert.assertNull(array.getArray(4)));

        Array nestedArray = array.getArray(3);
        Assert.assertEquals(nestedArray, mNestedArray);
        Assert.assertEquals(array, mArray);
    }

    @Test
    public void testAddInt() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addInt(0);
        mArray.addInt(Integer.MAX_VALUE);
        mArray.addInt(Integer.MIN_VALUE);
        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(0, array.getInt(0));
        Assert.assertEquals(Integer.MAX_VALUE, array.getInt(1));
        Assert.assertEquals(Integer.MIN_VALUE, array.getInt(2));
    }

    @Test
    public void testSetInt() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addInt(0);
        mArray.addInt(Integer.MAX_VALUE);
        mArray.addInt(Integer.MIN_VALUE);

        mArray.setInt(0, Integer.MAX_VALUE);
        mArray.setInt(1, Integer.MIN_VALUE);
        mArray.setInt(2, 0);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(0, array.getInt(2));
        Assert.assertEquals(Integer.MAX_VALUE, array.getInt(0));
        Assert.assertEquals(Integer.MIN_VALUE, array.getInt(1));
    }

    @Test
    public void testInsertInt() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addInt(10); // will be pushed 3 times.
        mArray.insertInt(0, 0);
        mArray.insertInt(1, Integer.MAX_VALUE);
        mArray.insertInt(2, Integer.MIN_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertEquals(0, array.getInt(0));
        Assert.assertEquals(Integer.MAX_VALUE, array.getInt(1));
        Assert.assertEquals(Integer.MIN_VALUE, array.getInt(2));
        Assert.assertEquals(10, array.getInt(3));
    }

    @Test
    public void testAddLong() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addLong(0);
        mArray.addLong(Long.MAX_VALUE);
        mArray.addLong(Long.MIN_VALUE);
        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(0, array.getLong(0));
        Assert.assertEquals(Long.MAX_VALUE, array.getLong(1));
        Assert.assertEquals(Long.MIN_VALUE, array.getLong(2));
    }

    @Test
    public void testSetLong() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addLong(0);
        mArray.addLong(Long.MAX_VALUE);
        mArray.addLong(Long.MIN_VALUE);
        mArray.setLong(0, Long.MAX_VALUE);
        mArray.setLong(1, Long.MIN_VALUE);
        mArray.setLong(2, 0);
        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(0, array.getLong(2));
        Assert.assertEquals(Long.MAX_VALUE, array.getLong(0));
        Assert.assertEquals(Long.MIN_VALUE, array.getLong(1));
    }

    @Test
    public void testInsertLong() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addLong(10); // will be pushed 3 times.
        mArray.insertLong(0, 0);
        mArray.insertLong(1, Long.MAX_VALUE);
        mArray.insertLong(2, Long.MIN_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertEquals(0, array.getLong(0));
        Assert.assertEquals(Long.MAX_VALUE, array.getLong(1));
        Assert.assertEquals(Long.MIN_VALUE, array.getLong(2));
        Assert.assertEquals(10, array.getLong(3));
    }

    @Test
    public void testAddFloat() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();
        mArray.addFloat(0.0F);
        mArray.addFloat(Float.MAX_VALUE);
        mArray.addFloat(Float.MIN_VALUE);
        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(0.0F, array.getFloat(0), 0.0F);
        Assert.assertEquals(Float.MAX_VALUE, array.getFloat(1), 0.0F);
        Assert.assertEquals(Float.MIN_VALUE, array.getFloat(2), 0.0F);
    }

    @Test
    public void testSetFloat() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addFloat(0);
        mArray.addFloat(Float.MAX_VALUE);
        mArray.addFloat(Float.MIN_VALUE);

        mArray.setFloat(0, Float.MAX_VALUE);
        mArray.setFloat(1, Float.MIN_VALUE);
        mArray.setFloat(2, 0);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());

        Assert.assertEquals(0.0F, array.getLong(2), 0.0F);
        Assert.assertEquals(Float.MAX_VALUE, array.getFloat(0), 0.0F);
        Assert.assertEquals(Float.MIN_VALUE, array.getFloat(1), 0.0f);
    }

    @Test
    public void testInsertFloat() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addFloat(10F); // will be pushed 3 times.
        mArray.insertFloat(0, 0F);
        mArray.insertFloat(1, Float.MAX_VALUE);
        mArray.insertFloat(2, Float.MIN_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertEquals(0F, array.getFloat(0), 0F);
        Assert.assertEquals(Float.MAX_VALUE, array.getFloat(1), 0F);
        Assert.assertEquals(Float.MIN_VALUE, array.getFloat(2), 0F);
        Assert.assertEquals(10F, array.getFloat(3), 0F);
    }

    @Test
    public void testAddDouble() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addDouble(0.0);
        mArray.addDouble(Double.MAX_VALUE);
        mArray.addDouble(Double.MIN_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());
        Assert.assertEquals(0.0, array.getDouble(0), 0.0);
        Assert.assertEquals(Double.MAX_VALUE, array.getDouble(1), 0.0);
        Assert.assertEquals(Double.MIN_VALUE, array.getDouble(2), 0.0);
    }

    @Test
    public void testSetDouble() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addDouble(0);
        mArray.addDouble(Double.MAX_VALUE);
        mArray.addDouble(Double.MIN_VALUE);

        mArray.setDouble(0, Double.MAX_VALUE);
        mArray.setDouble(1, Double.MIN_VALUE);
        mArray.setDouble(2, 0.0);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());

        Assert.assertEquals(0.0, array.getDouble(2), 0.0);
        Assert.assertEquals(Double.MAX_VALUE, array.getDouble(0), 0.0);
        Assert.assertEquals(Double.MIN_VALUE, array.getDouble(1), 0.0);
    }

    @Test
    public void testInsertDouble() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addDouble(10.0); // will be pushed 3 times.
        mArray.insertDouble(0, 0.0);
        mArray.insertDouble(1, Double.MAX_VALUE);
        mArray.insertDouble(2, Double.MIN_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertEquals(0.0, array.getDouble(0), 0.0);
        Assert.assertEquals(Double.MAX_VALUE, array.getDouble(1), 0.0);
        Assert.assertEquals(Double.MIN_VALUE, array.getDouble(2), 0.0);
        Assert.assertEquals(10.0, array.getDouble(3), 0.0);
    }

    @Test
    public void testAddNumber() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addNumber(Integer.MAX_VALUE);
        mArray.addNumber(Long.MAX_VALUE);
        mArray.addNumber(Double.MAX_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());

        Assert.assertEquals(Integer.MAX_VALUE, array.getNumber(0).intValue());
        Assert.assertEquals(Long.MAX_VALUE, array.getNumber(1).longValue());
        Assert.assertEquals(Double.MAX_VALUE, array.getNumber(2).doubleValue(), 0.0);
    }

    @Test
    public void testSetNumber() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addNumber(Integer.MAX_VALUE);
        mArray.addNumber(Long.MAX_VALUE);
        mArray.addNumber(Double.MAX_VALUE);

        mArray.setNumber(0, Long.MAX_VALUE);
        mArray.setNumber(1, Double.MAX_VALUE);
        mArray.setNumber(2, Integer.MAX_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());

        Assert.assertEquals(Integer.MAX_VALUE, array.getNumber(2).intValue());
        Assert.assertEquals(Long.MAX_VALUE, array.getNumber(0).longValue());
        Assert.assertEquals(Double.MAX_VALUE, array.getNumber(1).doubleValue(), 0.0);
    }

    @Test
    public void testInsertNumber() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addNumber(10L); // will be pushed 3 times.
        mArray.insertNumber(0, Integer.MAX_VALUE);
        mArray.insertNumber(1, Long.MAX_VALUE);
        mArray.insertNumber(2, Double.MAX_VALUE);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertEquals(Integer.MAX_VALUE, array.getInt(0));
        Assert.assertEquals(Long.MAX_VALUE, array.getLong(1));
        Assert.assertEquals(Double.MAX_VALUE, array.getDouble(2), 0.0);
        Assert.assertEquals(10L, array.getNumber(3).longValue());
    }

    @Test
    public void testAddString() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addString("");
        mArray.addString("Hello");
        mArray.addString("World");

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());

        Assert.assertEquals("", array.getString(0));
        Assert.assertEquals("Hello", array.getString(1));
        Assert.assertEquals("World", array.getString(2));
    }

    @Test
    public void testSetString() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addString("");
        mArray.addString("Hello");
        mArray.addString("World");

        mArray.setString(0, "Hello");
        mArray.setString(1, "World");
        mArray.setString(2, "");

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.count());

        Assert.assertEquals("", array.getString(2));
        Assert.assertEquals("Hello", array.getString(0));
        Assert.assertEquals("World", array.getString(1));
    }

    @Test
    public void testInsertString() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addString(""); // will be pushed 3 times.
        mArray.insertString(0, "Hello");
        mArray.insertString(1, "World");
        mArray.insertString(2, "!");

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertEquals("Hello", array.getString(0));
        Assert.assertEquals("World", array.getString(1));
        Assert.assertEquals("!", array.getString(2));
        Assert.assertEquals("", array.getString(3));
    }

    @Test
    public void testAddBoolean() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addBoolean(true);
        mArray.addBoolean(false);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(2, array.count());

        Assert.assertTrue(array.getBoolean(0));
        Assert.assertFalse(array.getBoolean(1));
    }

    @Test
    public void testSetBoolean() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addBoolean(true);
        mArray.addBoolean(false);

        mArray.setBoolean(0, false);
        mArray.setBoolean(1, true);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(2, array.count());

        Assert.assertTrue(array.getBoolean(1));
        Assert.assertFalse(array.getBoolean(0));
    }

    @Test
    public void testInsertBoolean() {
        MutableDocument mDoc = new MutableDocument("test");
        MutableArray mArray = new MutableArray();

        mArray.addBoolean(false); // will be pushed 2 times
        mArray.addBoolean(true); // will be pushed 2 times.
        mArray.insertBoolean(0, true);
        mArray.insertBoolean(1, false);

        mDoc.setArray("array", mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Assert.assertEquals(1, doc.count());
        Assert.assertTrue(doc.contains("array"));
        Array array = doc.getArray("array");
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.count());
        Assert.assertTrue(array.getBoolean(0));
        Assert.assertFalse(array.getBoolean(1));
        Assert.assertFalse(array.getBoolean(2));
        Assert.assertTrue(array.getBoolean(3));
    }

    ///////////////  Error Case test

    private static class Unserializable { }

    @Test
    public void testAddValueUnexpectedObject() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new MutableArray().addValue(new Unserializable()));
    }

    @Test
    public void testSetValueUnExpectedObject() {
        MutableArray mArray = new MutableArray();
        mArray.addValue(0);
        Assert.assertThrows(IllegalArgumentException.class, () -> mArray.setValue(0, new Unserializable()));
    }

    ///////////////  JSON tests

    // JSON 3.4
    @Test
    public void testArrayToJSON() throws JSONException, CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument().setArray("array", makeArray());
        verifyArray(new JSONArray(saveDocInTestCollection(mDoc).getArray("array").toJSON()));
    }

    // JSON 3.7.?
    @Test
    public void testArrayToJSONBeforeSave() {
        Assert.assertThrows(CouchbaseLiteError.class, () -> new MutableArray().toJSON());
    }

    // JSON 3.7.a-b
    @Test
    public void testArrayFromJSON() throws JSONException, CouchbaseLiteException {
        MutableArray mArray = new MutableArray(BaseDbTestKt.readJSONResource("array.json"));
        MutableDocument mDoc = new MutableDocument().setArray("array", mArray);
        Array dbArray = saveDocInTestCollection(mDoc).getArray("array");
        verifyArray(dbArray, true);
        verifyArray(new JSONArray(dbArray.toJSON()));
    }

    // JSON 3.7.c.1
    @Test
    public void testArrayFromBadJSON1() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new MutableArray("["));
    }

    // JSON 3.7.c.2
    @Test
    public void testArrayFromBadJSON2() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new MutableArray("[ab cd]"));
    }

    // JSON 3.7.d
    @Test
    public void testDictFromArray() {
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> new MutableArray(BaseDbTestKt.readJSONResource("dictionary.json")));
    }


    ///////////////  Tooling

    private List<Object> arrayOfAllTypes() {
        List<Object> list = new ArrayList<>();
        list.add(true);
        list.add(false);
        list.add("string");
        list.add(0);
        list.add(1);
        list.add(-1);
        list.add(1.1);

        list.add(JSONUtils.toDate(TEST_DATE));
        list.add(null);

        // Dictionary
        MutableDictionary subdict = new MutableDictionary();
        subdict.setValue("name", "Scott Tiger");
        list.add(subdict);

        // Array
        MutableArray subarray = new MutableArray();
        subarray.addValue("a");
        subarray.addValue("b");
        subarray.addValue("c");
        list.add(subarray);

        // Blob
        list.add(new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));

        return list;
    }

    private void populateData(MutableArray array) {
        for (Object o: arrayOfAllTypes()) { array.addValue(o); }
    }

    @SuppressWarnings("UnnecessaryUnboxing")
    private void populateDataByType(MutableArray array) {
        List<Object> data = arrayOfAllTypes();
        for (Object o: data) {
            if (o instanceof Integer) { array.addInt(((Integer) o).intValue()); }
            else if (o instanceof Long) { array.addLong(((Long) o).longValue()); }
            else if (o instanceof Float) { array.addFloat(((Float) o).floatValue()); }
            else if (o instanceof Double) { array.addDouble(((Double) o).doubleValue()); }
            else if (o instanceof Number) { array.addNumber((Number) o); }
            else if (o instanceof String) { array.addString((String) o); }
            else if (o instanceof Boolean) { array.addBoolean(((Boolean) o).booleanValue()); }
            else if (o instanceof Date) { array.addDate((Date) o); }
            else if (o instanceof Blob) { array.addBlob((Blob) o); }
            else if (o instanceof MutableDictionary) { array.addDictionary((MutableDictionary) o); }
            else if (o instanceof MutableArray) { array.addArray((MutableArray) o); }
            else { array.addValue(o); }
        }
    }

    private Document save(MutableDocument mDoc, String key, MutableArray mArray, Fn.Consumer<Array> validator) {
        validator.accept(mArray);
        mDoc.setValue(key, mArray);
        Document doc = saveDocInTestCollection(mDoc);
        Array array = doc.getArray(key);
        validator.accept(array);
        return doc;
    }

    private void verifyBlob(Object obj) {
        Assert.assertTrue(obj instanceof Blob);
        final Blob blob = (Blob) obj;
        Assert.assertNotNull(blob);
        final byte[] contents = blob.getContent();
        Assert.assertNotNull(contents);
        Assert.assertArrayEquals(BLOB_CONTENT.getBytes(StandardCharsets.UTF_8), contents);
        Assert.assertEquals(BLOB_CONTENT, new String(contents));
    }

    private String docId(int i) { return "doc-" + i; }

    // Kotlin shim functions

    private Document saveDocInTestCollection(MutableDocument mDoc) {
        return saveDocumentInCollection(mDoc, getTestCollection());
    }

    private Document saveDocumentInCollection(MutableDocument mDoc, Collection collection) {
        return saveDocInCollection(mDoc, collection);
    }
}
