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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;

import static com.couchbase.lite.internal.fleece.FLValue.ARRAY;
import static com.couchbase.lite.internal.fleece.FLValue.DATA;
import static com.couchbase.lite.internal.fleece.FLValue.DICT;
import static com.couchbase.lite.internal.fleece.FLValue.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class C4FleeceTest extends C4BaseTest {

    @Test
    public void testEncodeChars() throws LiteCoreException {
        String str = "Hello World!";

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.writeString(str.toCharArray());
            byte[] encoded = enc.finish();
            assertNotNull(encoded);

            FLValue value = FLValue.fromData(encoded);
            assertNotNull(value);
            assertEquals(STRING, value.getType());

            String decoded = value.asString();
            assertNotNull(decoded);
            Assert.assertEquals(str, decoded);
        }
    }

    @Test
    public void testEncodeBytes() throws LiteCoreException {
        byte[] data = "Hello World!".getBytes(StandardCharsets.UTF_8);

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.writeData(data);
            byte[] encoded = enc.finish();
            assertNotNull(encoded);

            FLValue value = FLValue.fromData(encoded);
            assertNotNull(value);
            assertEquals(DATA, value.getType());

            byte[] decoded = value.asByteArray();
            assertNotNull(decoded);
            Assert.assertArrayEquals(data, decoded);
        }
    }

    @Test
    public void testEncodeArrayWithBytes() throws LiteCoreException {
        byte[] data = "Hello World!".getBytes(StandardCharsets.UTF_8);
        List<Object> array = new ArrayList<>();
        array.add(data);

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(array);
            byte[] encoded = enc.finish();
            assertNotNull(encoded);

            FLValue value = FLValue.fromData(encoded);
            assertNotNull(value);
            assertEquals(ARRAY, value.getType());

            List<Object> decoded = value.asList(Object.class);
            assertNotNull(decoded);
            assertEquals(1, decoded.size());

            byte[] decodedData = (byte[]) decoded.get(0);
            assertNotNull(decodedData);
            Assert.assertArrayEquals(data, decodedData);
        }
    }

    @Test
    public void testEncodeMapWithBytes() throws LiteCoreException {
        byte[] data = "Hello World!".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> map = new HashMap<>();
        map.put("bytes", data);

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(map);
            byte[] encoded = enc.finish();
            assertNotNull(encoded);

            FLValue value = FLValue.fromData(encoded);
            assertNotNull(value);
            assertEquals(DICT, value.getType());

            Map<String, Object> decoded = value.asMap(String.class, Object.class);

            assertNotNull(decoded);
            assertTrue(decoded.containsKey("bytes"));

            byte[] decodedData = (byte[]) decoded.get("bytes");
            assertNotNull(decodedData);
            Assert.assertArrayEquals(data, decodedData);
        }
    }

    @Test
    public void testEncodeNull() throws LiteCoreException {
        try (FLEncoder.JSONEncoder enc = FLEncoder.getJSONEncoder()) {
            enc.beginDict(6);
            enc.writeKey("foo");
            enc.writeValue("foo");
            enc.writeKey(null);
            enc.writeValue("foo");
            enc.writeKey("foo");
            enc.writeValue(null);
            enc.writeKey(null);
            enc.writeValue(null);
            enc.writeKey("foo");
            enc.writeValue("bar");
            enc.writeKey("bar");
            enc.writeValue("bar");
            enc.endDict();
            enc.finishJSON();
        }
    }
}
