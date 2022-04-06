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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.TestDictionary;
import com.couchbase.lite.internal.fleece.MContext;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.fleece.TestMValueDelegate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


class FLContext extends MContext {
    @NonNull
    private final FLSliceResult data;

    public FLContext(@NonNull FLSliceResult data) { this.data = data; }

    @NonNull
    public FLSliceResult getData() { return data; }
}


@SuppressWarnings("unchecked")
public class C4MutableFleeceTest extends C4BaseTest {
    private MValue.Delegate delegate;

    @Before
    public final void setUpC4MutableFleeceTest() {
        delegate = MValue.getRegisteredDelegate();
        MValue.registerDelegate(new TestMValueDelegate());
    }

    @After
    public final void tearDownC4MutableFleeceTest() {
        if (delegate != null) { MValue.registerDelegate(delegate); }
    }

    // TEST_CASE("MValue", "[Mutable]")
    @Test
    public void testMValue() {
        MValue val = new MValue("hi");
        assertEquals("hi", val.asNative(null));
        assertNull(val.getValue());
    }

    // TEST_CASE("MDict", "[Mutable]")
    @Test
    public void testMDict() throws LiteCoreException {
        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32);
        subMap.put("boil", 212);
        map.put("dict", subMap);


        try (FLSliceResult data = encode(map)) {
            MRoot root = getMRoot(data);
            assertFalse(root.isMutated());
            Object obj = root.asNative();
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> dict = (Map<String, Object>) obj;
            assertNotNull(dict);
            assertEquals(3, dict.size());
            assertTrue(dict.containsKey("greeting"));
            assertFalse(dict.containsKey("x"));
            assertEquals(Arrays.asList("array", "dict", "greeting"), sortedKeys(dict));
            assertEquals("hi", dict.get("greeting"));
            assertNull(dict.get("x"));

            obj = dict.get("dict");
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> nested = (Map<String, Object>) obj;
            assertEquals(sortedKeys(nested), Arrays.asList("boil", "melt"));
            Map<String, Object> expected = new HashMap<>();
            expected.put("melt", 32L);
            expected.put("boil", 212L);
            assertEquals(expected, nested);
            assertEquals(32L, nested.get("melt"));
            assertEquals(212L, nested.get("boil"));
            assertNull(nested.get("freeze"));
            assertFalse(root.isMutated());

            verifyDictIterator(dict);

            nested.put("freeze", Arrays.asList(32L, "Fahrenheit"));
            assertTrue(root.isMutated());
            assertEquals(32L, nested.remove("melt"));
            expected.clear();
            expected.put("freeze", Arrays.asList(32L, "Fahrenheit"));
            expected.put("boil", 212L);
            assertEquals(expected, nested);

            verifyDictIterator(dict);

            try (FLSliceResult encodedDict = encode(dict)) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                    fleece2JSON(encodedDict));
            }
            try (FLSliceResult encodedRoot = encode(root)) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                    fleece2JSON(encodedRoot));
            }
        }
    }

    // TEST_CASE("MArray", "[Mutable]")
    @Test
    public void testMArray() throws LiteCoreException {
        List<Object> list = Arrays.asList("hi", Arrays.asList("boo", false), 42);

        try (FLSliceResult data = encode(list)) {
            MRoot root = getMRoot(data);
            assertFalse(root.isMutated());
            Object obj = root.asNative();
            assertNotNull(obj);
            assertTrue(obj instanceof List);
            List<Object> array = (List<Object>) obj;
            assertNotNull(array);
            assertEquals(3, array.size());
            assertEquals("hi", array.get(0));
            assertEquals(42L, array.get(2));
            assertNotNull(array.get(1));
            obj = array.get(1);
            assertTrue(obj instanceof List);
            assertEquals(Arrays.asList("boo", false), obj);
            array.set(0, Arrays.asList(3.14, 2.17));
            array.add(2, "NEW");
            assertEquals(Arrays.asList(3.14, 2.17), array.get(0));
            assertEquals(Arrays.asList("boo", false), array.get(1));
            assertEquals("NEW", array.get(2));
            assertEquals(42L, array.get(3));
            assertEquals(4, array.size());

            List<Object> expected = new ArrayList<>();
            expected.add(Arrays.asList(3.14, 2.17));
            expected.add(Arrays.asList("boo", false));
            expected.add("NEW");
            expected.add(42L);
            assertEquals(expected, array);

            obj = array.get(1);
            assertNotNull(obj);
            List<Object> nested = (List<Object>) obj;
            nested.set(1, true);

            try (FLSliceResult encodedArray = encode(array)) {
                assertEquals(
                    "[[3.14,2.17],[\"boo\",true],\"NEW\",42]",
                    fleece2JSON(encodedArray));
            }
            try (FLSliceResult encodedRoot = encode(root)) {
                assertEquals(
                    "[[3.14,2.17],[\"boo\",true],\"NEW\",42]",
                    fleece2JSON(encodedRoot));
            }
        }
    }

    // TEST_CASE("MArray iteration", "[Mutable]")
    @Test
    public void testMArrayIteration() throws LiteCoreException {
        List<Object> orig = new ArrayList<>();
        for (int i = 0; i < 100; i++) { orig.add(String.format(Locale.ENGLISH, "This is item number %d", i)); }

        try (FLSliceResult data = encode(orig)) {
            MRoot root = getMRoot(data);
            List<Object> array = (List<Object>) root.asNative();
            int i = 0;
            for (Object o: array) {
                assertEquals(orig.get(i), o);
                i++;
            }
        }
    }

    // TEST_CASE("MDict no root", "[Mutable]")
    @Test
    public void testMDictNoRoot() throws LiteCoreException {
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("melt", 32);
        subMap.put("boil", 212);

        Map<String, Object> map = new HashMap<>();
        map.put("greeting", "hi");
        map.put("array", Arrays.asList("boo", false));
        map.put("dict", subMap);


        try (FLSliceResult data = encode(map)) {
            Object obj = getMRoot(data).asNative();
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> dict = (Map<String, Object>) obj;
            assertFalse(((TestDictionary) dict).isMutated());
            assertEquals(Arrays.asList("array", "dict", "greeting"), sortedKeys(dict));
            assertEquals("hi", dict.get("greeting"));
            assertNull(dict.get("x"));
            verifyDictIterator(dict);

            obj = dict.get("dict");
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> nested = (Map<String, Object>) obj;
            assertEquals(sortedKeys(nested), Arrays.asList("boil", "melt"));
            Map<String, Object> expected = new HashMap<>();
            expected.put("melt", 32L);
            expected.put("boil", 212L);
            assertEquals(expected, nested);
            assertEquals(32L, nested.get("melt"));
            assertEquals(212L, nested.get("boil"));
            assertNull(nested.get("freeze"));
            verifyDictIterator(nested);
            assertFalse(((TestDictionary) nested).isMutated());
            assertFalse(((TestDictionary) dict).isMutated());

            nested.put("freeze", Arrays.asList(32L, "Fahrenheit"));
            assertTrue(((TestDictionary) nested).isMutated());
            assertTrue(((TestDictionary) dict).isMutated());
            assertEquals(32L, nested.remove("melt"));
            expected.clear();
            expected.put("freeze", Arrays.asList(32L, "Fahrenheit"));
            expected.put("boil", 212L);
            assertEquals(expected, nested);

            verifyDictIterator(nested);
            verifyDictIterator(dict);

            try (FLSliceResult encodedDict = encode(dict)) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,freeze:[32,\"Fahrenheit\"]},greeting:\"hi\"}",
                    fleece2JSON(encodedDict));
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


        try (FLSliceResult data = encode(map)) {
            MRoot root = getMRoot(data);
            assertFalse(root.isMutated());
            Object obj = root.asNative();
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> dict = (Map<String, Object>) obj;

            obj = dict.get("array");
            assertTrue(obj instanceof List);
            List<Object> array = (List<Object>) obj;
            dict.put("new", array);
            array.add(true);

            try (FLSliceResult encodedRoot = encode(root)) {
                assertEquals(
                    "{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}",
                    fleece2JSON(encodedRoot));
            }

            try (FLSliceResult encodedRoot = root.encode()) {
                assertEquals(
                    "{array:[\"boo\",false,true],dict:{boil:212,melt:32},greeting:\"hi\",new:[\"boo\",false,true]}",
                    fleece2JSON(encodedRoot));
            }
        }
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

        try (FLSliceResult data = encode(map)) {
            MRoot root = getMRoot(data);
            assertFalse(root.isMutated());
            Object obj = root.asNative();
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> dict = (Map<String, Object>) obj;
            assertEquals("hi", dict.get("greeting"));
            assertEquals(Arrays.asList("boo", false), dict.get("array"));
            assertEquals(subMap, dict.get("dict"));

            try (FLSliceResult encodedRoot = root.encode()) {
                assertEquals(
                    "{array:[\"boo\",false],dict:{boil:212,melt:32},greeting:\"hi\"}",
                    fleece2JSON(encodedRoot));
            }

            List<Object> array = (List<Object>) dict.get("array");
            dict.put("new", array);
            array.add(true);

            try (FLSliceResult encodedRoot = root.encode()) {
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

        try (FLSliceResult data = encode(map)) {
            MRoot root = getMRoot(data);
            assertFalse(root.isMutated());
            Object obj = root.asNative();
            assertNotNull(obj);
            assertTrue(obj instanceof Map);
            Map<String, Object> dict = (Map<String, Object>) obj;
            assertEquals("hi", dict.get("greeting"));

            try (FLSliceResult encodedRoot = root.encode()) {
                assertEquals("{greeting:\"hi\"}", fleece2JSON(encodedRoot));
            }
            try (FLSliceResult encodedRoot = encode(root)) {
                assertEquals("{greeting:\"hi\"}", fleece2JSON(encodedRoot));
            }

            dict.put("hello", "world");
            assertEquals("hi", dict.get("greeting"));
            assertEquals("world", dict.get("hello"));

            try (FLSliceResult encodedDict = encode(dict)) {
                assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encodedDict));
            }

            try (FLSliceResult encodedNative = encode(root.asNative())) {
                assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encodedNative));
            }
            try (FLSliceResult encodedRoot = root.encode()) {
                assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encodedRoot));
            }
            try (FLSliceResult encodedRoot = encode(root)) {
                assertEquals("{greeting:\"hi\",hello:\"world\"}", fleece2JSON(encodedRoot));
            }
        }
    }

    private FLSliceResult encode(Object obj) throws LiteCoreException {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.writeValue(obj);
            return enc.finish2();
        }
    }

    private FLSliceResult encode(MRoot root) throws LiteCoreException {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            root.encodeTo(enc);
            return enc.finish2();
        }
    }

    private List<String> sortedKeys(Map<String, Object> dict) {
        Set<String> keys = dict.keySet();
        ArrayList<String> list = new ArrayList<>(keys);
        Collections.sort(list);
        return list;
    }

    private void verifyDictIterator(Map<String, Object> dict) {
        int count = 0;
        Set<String> keys = new HashSet<>();
        for (String key: dict.keySet()) {
            count++;
            assertNotNull(key);
            keys.add(key);
        }
        assertEquals(dict.size(), keys.size());
        assertEquals(dict.size(), count);
    }

    private String fleece2JSON(FLSliceResult fleece) {
        try {
            FLValue v = FLValue.fromData(fleece);
            if (v == null) { return "INVALID_FLEECE"; }
            return v.toJSON5();
        }
        finally {
            if (fleece != null) { fleece.close(); }
        }
    }

    private MRoot getMRoot(FLSliceResult data) {
        return new MRoot(new FLContext(data), FLValue.fromData(data), true);
    }
}
