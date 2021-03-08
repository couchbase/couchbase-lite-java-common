//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.utils

import com.couchbase.lite.BaseTest
import com.couchbase.lite.internal.utils.JsonUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class JsonUtilsTest : BaseTest() {
    @Test
    fun testMarshaller() {
        val map = HashMap<String, Any?>()
        map.put("x1", null)
        map.put("b1", true)
        map.put("n1", 47)
        map.put("s1", "one")
        map.put("l1", listOf(null, false, 48.48F, "two", listOf<Any?>("i1"), mapOf("k1" to "v1")))
        map.put(
            "m1",
            mapOf(
                "x2" to null,
                "b2" to true,
                "n2" to 49.49,
                "s2" to "three",
                "l2" to listOf<Any?>("i2"),
                "m2" to mapOf("k2" to "v2")
            )
        )

        val json = JSONObject(JsonUtils.Marshaller().writeValue(map).toString())
        assertEquals(JSONObject.NULL, json.get("x1"))
        assertTrue(json.optBoolean("b1"))
        assertEquals(47, json.optInt("n1"))
        assertEquals("one", json.optString("s1"))

        val array = json.getJSONArray("l1")
        assertEquals(JSONObject.NULL, array.get(0))
        assertFalse(array.optBoolean(1))
        assertEquals(48.48, array.optDouble(2), 0.01)
        assertEquals("two", array.optString(3))
        assertEquals("i1", array.optJSONArray(4).opt(0))
        assertEquals("v1", array.optJSONObject(5).optString("k1"))

        val obj = json.getJSONObject("m1")
        assertEquals(JSONObject.NULL, obj.get("x2"))
        assertTrue(obj.optBoolean("b2"))
        assertEquals(49.49, obj.optDouble("n2"), 0.01)
        assertEquals("three", obj.optString("s2"))
        assertEquals("i2", obj.optJSONArray("l2")!!.opt(0))
        assertEquals("v2", obj.optJSONObject("m2")!!.optString("k2"))
    }
}