//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite

import com.couchbase.lite.internal.utils.JSONUtils
import org.junit.Assert
import org.junit.Test

class ParameterTest : BaseDbTest() {
    @Test
    fun testCreateParams() {
        val params = Parameters()
        params.setString("param", "value")

        val query = testDatabase.createQuery(
            "SELECT  meta().id"
                    + " FROM _default._default"
                    + " WHERE test = \$param"
        )

        query.parameters = params

        Assert.assertEquals("value", query.parameters?.getValue("param"))
    }

    @Test
    fun testMutateImmutableParams() {
        val params = Parameters()
        params.setString("param", "value")

        val query = testDatabase.createQuery(
            "SELECT  meta().id"
                    + " FROM _default._default"
                    + " WHERE test = \$param"
        )

        query.parameters = params

        Assert.assertThrows(CouchbaseLiteError::class.java) {
            query.parameters?.setString("param", "value2")
        }
    }

    @Test
    fun testParamContents() {
        val params = makeParams()

        val query = testDatabase.createQuery(
            "SELECT  meta().id"
                    + " FROM _default._default"
                    + " WHERE test = \$param"
        )

        query.parameters = params
        verifyParams(query.parameters)
    }

    private fun makeParams(): Parameters {
        // A small array
        val simpleArray = MutableArray()
        simpleArray.addInt(54)
        simpleArray.addString("Joplin")

        // A small dictionary
        val simpleDict = MutableDictionary()
        simpleDict.setInt("sparam.1", 58)
        simpleDict.setString("sparam.2", "Winehouse")

        // Parameters:
        val params = Parameters()
        params.setValue("param-1", null)
        params.setBoolean("param-2", true)
        params.setBoolean("param-3", false)
        params.setInt("param-4", 0)
        params.setInt("param-5", Int.MIN_VALUE)
        params.setInt("param-6", Int.MAX_VALUE)
        params.setLong("param-7", 0L)
        params.setLong("param-8", Long.MIN_VALUE)
        params.setLong("param-9", Long.MAX_VALUE)
        params.setFloat("param-10", 0.0f)
        params.setFloat("param-11", Float.MIN_VALUE)
        params.setFloat("param-12", Float.MAX_VALUE)
        params.setDouble("param-13", 0.0)
        params.setDouble("param-14", Double.MIN_VALUE)
        params.setDouble("param-15", Double.MAX_VALUE)
        params.setNumber("param-16", null)
        params.setNumber("param-17", 0)
        params.setNumber("param-18", Float.MIN_VALUE)
        params.setNumber("param-19", Long.MIN_VALUE)
        params.setString("param-20", null)
        params.setString("param-21", "Quatro")
        params.setDate("param-22", null)
        params.setDate("param-23", JSONUtils.toDate(TEST_DATE))
        params.setArray("param-24", null)
        params.setArray("param-25", simpleArray)
        params.setDictionary("param-26", null)
        params.setDictionary("param-27", simpleDict)
        return params
    }

    private fun verifyParams(params: Parameters?) {
        Assert.assertNotNull(params)
        params!!

        //#0 param.setValue(null);
        Assert.assertNull(params.getValue("param-1"))

        //#1 param.setBoolean(true);
        Assert.assertEquals(true, params.getValue("param-2"))

        //#2 param.setBoolean(false);
        Assert.assertEquals(false, params.getValue("param-3"))

        //#3 param.setInt(0);
        Assert.assertEquals(0, params.getValue("param-4"))

        //#4 param.setInt(Integer.MIN_VALUE);
        Assert.assertEquals(Int.MIN_VALUE, params.getValue("param-5"))

        //#5 param.setInt(Integer.MAX_VALUE);
        Assert.assertEquals(Int.MAX_VALUE, params.getValue("param-6"))

        //#6 param.setLong(0L);
        Assert.assertEquals(0L, params.getValue("param-7"))

        //#7 param.setLong(Long.MIN_VALUE);
        Assert.assertEquals(Long.MIN_VALUE, params.getValue("param-8"))

        //#8 param.setLong(Long.MAX_VALUE);
        Assert.assertEquals(Long.MAX_VALUE, params.getValue("param-9"))

        //#9 param.setFloat(0.0F);
        Assert.assertEquals(0.0f, params.getValue("param-10"))

        //#10 param.setFloat(Float.MIN_VALUE);
        Assert.assertEquals(Float.MIN_VALUE, params.getValue("param-11"))

        //#11 param.setFloat(Float.MAX_VALUE);
        Assert.assertEquals(Float.MAX_VALUE, params.getValue("param-12"))

        //#12 param.setDouble(0.0);
        Assert.assertEquals(0.0, params.getValue("param-13") as Double, 0.001)

        //#13 param.setDouble(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, params.getValue("param-14") as Double, 0.001)

        //#14 param.setDouble(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, params.getValue("param-15") as Double, 0.001)

        //#15 param.setNumber(null);
        Assert.assertNull(params.getValue("param-16"))

        //#16 param.setNumber(0);
        Assert.assertEquals(0, params.getValue("param-17"))

        //#17 param.setNumber(Float.MIN_VALUE);
        Assert.assertEquals(Float.MIN_VALUE, params.getValue("param-18"))

        //#18 param.setNumber(Long.MIN_VALUE);
        Assert.assertEquals(Long.MIN_VALUE, params.getValue("param-19"))

        //#19 param.setString(null);
        Assert.assertNull(params.getValue("param-20"))

        //#20 param.setString("Quatro");
        Assert.assertEquals("Quatro", params.getValue("param-21"))

        //#21 param.setDate(null);
        Assert.assertNull(params.getValue("param-22"))

        //#22 param.setDate(JSONUtils.toDate(TEST_DATE));
        Assert.assertEquals(TEST_DATE, params.getValue("param-23"))

        //#23 param.setArray(null);
        Assert.assertNull(params.getValue("param-24"))

        //#24 param.setArray(simpleArray);
        val a = params.getValue("param-25")
        Assert.assertTrue(a is Array)
        val array = a as Array
        Assert.assertEquals(54, array.getInt(0))
        Assert.assertEquals("Joplin", array.getString(1))

        //#25 param.setDictionary(null);
        Assert.assertNull(params.getValue("param-26"))

        //#26 param.setDictionary(simpleDict);
        val d = params.getValue("param-27")
        Assert.assertTrue(d is Dictionary)
        val dict = d as Dictionary
        Assert.assertEquals(58, dict.getInt("sparam.1"))
        Assert.assertEquals("Winehouse", dict.getString("sparam.2"))
    }
}