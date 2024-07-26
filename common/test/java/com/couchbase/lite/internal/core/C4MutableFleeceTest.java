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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.MutableArray;
import com.couchbase.lite.MutableDictionary;
import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.fleece.MValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class C4MutableFleeceTest extends C4BaseTest {
    static class TestContext extends DbContext {
        @NonNull
        private final FLSliceResult data;

        public TestContext(@NonNull FLSliceResult data) {
            super(null);
            this.data = data;
        }

        @NonNull
        public FLSliceResult getData() { return data; }
    }

    // TEST_CASE("MValue", "[Mutable]")
    @Test
    public void testMValue() {
        MValue val = new MValue("hi");
        assertEquals("hi", val.toJava(null));
        assertNull(val.getFLValue());
    }

    @Test
    public void testMRoot() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32L);
        subMap.put("boil", 212L);

        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);

        try (FLSliceResult data = encodeObj(map)) {
            MRoot root = new MRoot(new TestContext(data), FLValue.fromSliceResult(data), true);

            assertFalse(root.isMutated());
            Object dict = root.toJava();
            assertNotNull(dict);
            assertTrue(dict instanceof MutableDictionary);
            MutableDictionary mDict = (MutableDictionary) dict;

            assertEquals("hi", mDict.getString("greeting"));
            assertEquals(subMap, mDict.getDictionary("dict").toMap());

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,melt:32},greeting:\"hi\"}",
                    fleece2JSON(encodedRoot));
            }

            MutableArray mArray = mDict.getArray("array");
            assertEquals(Arrays.asList("boo", false), mArray.toList());
            mDict.setArray("new", mArray);
            mArray.addBoolean(true);
            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals(
                    "{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}",
                    fleece2JSON(encodedRoot));
            }
        }
    }

    @Test
    public void testMRoot2() throws LiteCoreException {
        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");

        try (FLSliceResult data = encodeObj(map)) {
            MRoot root = new MRoot(new TestContext(data), FLValue.fromSliceResult(data), true);

            assertFalse(root.isMutated());
            Object dict = root.toJava();
            assertNotNull(dict);
            assertTrue(dict instanceof MutableDictionary);
            MutableDictionary mDict = (MutableDictionary) dict;

            assertEquals("hi", mDict.getString("greeting"));

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals("{greeting:\"hi\"}", fleece2JSON(encodedRoot));
            }

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals("{greeting:\"hi\"}", fleece2JSON(encodedRoot));
            }

            mDict.setString("hello", "world");
            assertEquals("hi", mDict.getString("greeting"));
            assertEquals("world", mDict.getString("hello"));

            String expected = "{greeting:\"hi\",hello:\"world\"}";
            assertEquals(expected, fleece2JSON(encodeObj(dict)));
            assertEquals(expected, fleece2JSON(encodeObj(root.toJava())));
            assertEquals(expected, fleece2JSON(encodeCollection(root)));
            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals(expected, fleece2JSON(encodedRoot));
            }
        }
    }

    // TEST_CASE("MDict", "[Mutable]")
    @Test
    public void testMDict() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32L);
        subMap.put("boil", 212L);

        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);

        try (FLSliceResult data = encodeObj(map)) {
            MRoot root = new MRoot(new TestContext(data), FLValue.fromSliceResult(data), true);

            Object dict = root.toJava();
            assertNotNull(dict);
            assertTrue(dict instanceof MutableDictionary);
            MutableDictionary mDict = (MutableDictionary) dict;

            assertEquals(3, mDict.count());
            assertTrue(mDict.contains("greeting"));
            assertFalse(mDict.contains("x"));
            assertEquals(Arrays.asList("array", "dict", "greeting"), sortedKeys(mDict));
            assertEquals("hi", mDict.getString("greeting"));
            assertNull(mDict.getValue("x"));

            verifyDictIterator(mDict);

            MutableDictionary mSubDict = mDict.getDictionary("dict");
            assertNotNull(mSubDict);
            assertEquals(Arrays.asList("boil", "melt"), sortedKeys(mSubDict));
            assertEquals(32L, mSubDict.getNumber("melt"));
            assertEquals(212L, mSubDict.getNumber("boil"));
            assertEquals(subMap, mSubDict.toMap());

            assertNull(mSubDict.getNumber("freeze"));

            verifyDictIterator(mSubDict);

            assertFalse(root.isMutated());
            assertFalse(mDictHasChanged(mDict));
            assertFalse(mDictHasChanged(mSubDict));

            MutableArray mArray = new MutableArray();
            mArray.addLong(32L);
            mArray.addString("Fahrenheit");
            mSubDict.setArray("freeze", mArray);
            assertTrue(root.isMutated());

            assertEquals(32L, mSubDict.getNumber("melt"));
            mSubDict.remove("melt");
            assertNull(mSubDict.getValue("melt"));

            Map<String, Object> expected = new HashMap<>();
            expected.put("freeze", Arrays.asList(32L, "Fahrenheit"));
            expected.put("boil", 212L);
            assertEquals(expected, mSubDict.toMap());

            verifyDictIterator(mDict);
            verifyDictIterator(mSubDict);

            try (FLSliceResult encodedDict = encodeObj(dict)) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                    fleece2JSON(encodedDict));
            }

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                    fleece2JSON(encodedRoot));
            }
        }
    }

    // TEST_CASE("MArray", "[Mutable]")
    @Test
    public void testMArray() throws LiteCoreException {
        List<Object> expected = Arrays.asList("hi", Arrays.asList("boo", false), 42);

        try (FLSliceResult data = encodeObj(expected)) {
            MRoot root = new MRoot(new TestContext(data), FLValue.fromSliceResult(data), true);

            assertFalse(root.isMutated());
            Object array = root.toJava();
            assertNotNull(array);
            assertTrue(array instanceof MutableArray);
            MutableArray mArray = (MutableArray) array;

            assertEquals(3, mArray.count());
            assertEquals("hi", mArray.getString(0));
            assertEquals(42L, mArray.getNumber(2));

            MutableArray subArray = mArray.getArray(1);
            assertNotNull(subArray);
            assertEquals(Arrays.asList("boo", false), subArray.toList());

            MutableArray subArray2 = new MutableArray();
            subArray2.addDouble(3.14);
            subArray2.addDouble(2.17);

            mArray.setArray(0, subArray2);
            mArray.insertString(2, "NEW");
            assertEquals(Arrays.asList(3.14D, 2.17D), mArray.getArray(0).toList());
            assertEquals(Arrays.asList("boo", false), mArray.getArray(1).toList());
            assertEquals("NEW", mArray.getString(2));
            assertEquals(42L, mArray.getNumber(3));
            assertEquals(4, mArray.count());

            expected = Arrays.asList(Arrays.asList(3.14, 2.17), Arrays.asList("boo", false), "NEW", 42L);
            assertEquals(expected, mArray.toList());

            subArray = mArray.getArray(1);
            assertNotNull(subArray);
            subArray.setBoolean(1, true);

            try (FLSliceResult encodedArray = encodeObj(array)) {
                assertEquals("[[3.14,2.17],[\"boo\",true],\"NEW\",42]", fleece2JSON(encodedArray));
            }

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals("[[3.14,2.17],[\"boo\",true],\"NEW\",42]", fleece2JSON(encodedRoot));
            }
        }
    }

    // TEST_CASE("Adding mutable collections", "[Mutable]")
    @Test
    public void testAddingMutableCollections() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32);
        subMap.put("boil", 212);

        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);

        try (FLSliceResult data = encodeObj(map)) {
            MRoot root = new MRoot(new TestContext(data), FLValue.fromSliceResult(data), true);

            assertFalse(root.isMutated());
            Object dict = root.toJava();
            assertNotNull(dict);
            assertTrue(dict instanceof MutableDictionary);
            MutableDictionary mDict = (MutableDictionary) dict;

            MutableArray mArray = mDict.getArray("array");
            mDict.setArray("new", mArray);
            mArray.addBoolean(true);

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals(
                    "{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}",
                    fleece2JSON(encodedRoot));
            }

            try (FLSliceResult encodedRoot = encodeCollection(root)) {
                assertEquals(
                    "{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}",
                    fleece2JSON(encodedRoot));
            }
        }
    }

    // TEST_CASE("MArray iteration", "[Mutable]")
    @Test
    public void testMArrayIteration() throws LiteCoreException {
        List<Object> expected = new ArrayList<>();
        for (int i = 0; i < 100; i++) { expected.add("This is item number " + i); }

        try (FLSliceResult data = encodeObj(expected)) {
            MRoot root = new MRoot(new TestContext(data), FLValue.fromSliceResult(data), true);

            Object array = root.toJava();
            assertNotNull(array);
            assertTrue(array instanceof MutableArray);

            MutableArray mArray = (MutableArray) array;
            int i = 0;
            for (Object o: mArray.toList()) {
                assertEquals(expected.get(i), o);
                i++;
            }

            assertEquals(expected.size(), i);
        }
    }

    @NonNull
    private FLSliceResult encodeObj(Object obj) throws LiteCoreException {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.writeValue(obj);
            return enc.finish2();
        }
    }

    private FLSliceResult encodeCollection(MCollection collection) throws LiteCoreException {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            collection.encodeTo(enc);
            return enc.finish2();
        }
    }

    private List<String> sortedKeys(MutableDictionary dict) {
        List<String> keys = dict.getKeys();
        Collections.sort(keys);
        return keys;
    }

    private void verifyDictIterator(MutableDictionary dict) {
        int count = 0;
        Set<String> keys = new HashSet<>();
        for (String key: dict.getKeys()) {
            count++;
            assertNotNull(key);
            keys.add(key);
        }
        assertEquals(dict.count(), keys.size());
        assertEquals(dict.count(), count);
    }

    private String fleece2JSON(FLSliceResult fleece) {
        FLValue v = FLValue.fromSliceResult(fleece);
        if (v == null) { return "INVALID_FLEECE"; }
        return v.toJSON5();
    }
}
