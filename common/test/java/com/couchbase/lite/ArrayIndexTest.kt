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

import com.couchbase.lite.internal.core.C4TestUtils
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

// Implements test spec version 1.0.2
class ArrayIndexTest : BaseDbTest() {
    @Test
    fun testArrayIndexConfigValidExpressions1() = Assert.assertEquals("", ArrayIndexConfiguration("foo").indexSpec)

    @Test
    fun testArrayIndexConfigValidExpressions3() =
        Assert.assertEquals("", ArrayIndexConfiguration("foo", null).indexSpec)

    @Test
    fun testArrayIndexConfigValidExpressions4() =
        Assert.assertEquals("bar", ArrayIndexConfiguration("foo", "bar").indexSpec)

    @Test
    fun testArrayIndexConfigValidExpressions5() =
        Assert.assertEquals("bar,baz", ArrayIndexConfiguration("foo", "bar", "baz").indexSpec)

    @Test
    fun testArrayIndexConfigValidExpressions7() =
        Assert.assertEquals("bar,baz", ArrayIndexConfiguration("foo", listOf("bar", "baz")).indexSpec)

    /**
     * 1. TestArrayIndexConfigInvalidExpressions
     *
     * Description
     *     Test that creating an ArrayIndexConfiguration with invalid
     *     expressions which are an empty expressions or contain null.
     *
     * Steps
     * 1. Create a ArrayIndexConfiguration object.
     *     - path: "contacts"
     *     - expressions: []
     * 2. Check that an invalid argument exception is thrown.
     * 3. Create a ArrayIndexConfiguration object.
     *     - path: "contacts"
     *     - expressions: [""]
     * 4. Check that an invalid argument exception is thrown.
     * 5. Create a ArrayIndexConfiguration object. This case can be ignore if the platform doesn't allow null.
     *     - path: "contacts"
     *     - expressions: ["address.state", null, "address.city"]
     * 6. Check that an invalid argument exception is thrown.
     */
    @Test
    fun testArrayIndexConfigInvalidExpressions1() = assertThrows(IllegalArgumentException::class.java) {
        ArrayIndexConfiguration("contacts", emptyList())
    }

    @Test
    fun testArrayIndexConfigInvalidExpressions3a() = assertThrows(IllegalArgumentException::class.java) {
        ArrayIndexConfiguration("contacts", listOf(""))
    }

    @Test
    fun testArrayIndexConfigInvalidExpressions3b() = assertThrows(IllegalArgumentException::class.java) {
        ArrayIndexConfiguration("contacts", "")
    }

    @Test
    fun testArrayIndexConfigInvalidExpressions5() = assertThrows(IllegalArgumentException::class.java) {
        ArrayIndexConfiguration("contacts", listOf("address.state", null, "address.city"))
    }

    /**
     * 2. TestCreateArrayIndexWithPath
     *
     * Description
     *     Test that creating an array index with only path works as expected.
     *
     * Steps
     *     1. Load profiles.json into the collection named "_default.profiles".
     *     2. Create a ArrayIndexConfiguration object.
     *         - path: "contacts"
     *         - expressions: null
     *     3. Create an array index named "contacts" in the profiles collection.
     *     4. Get index names from the profiles collection and check that the index named "contacts" exists.
     *     5. Get info of the index named "contacts" using an internal API and check that the
     *        index has path and expressions as configured.
     */
    @Ignore("Awaiting merge of Array Index feature")
    @Test
    fun testCreateArrayIndexWithPath() {
        val profilesCollection = testDatabase.createCollection("profiles")
        loadJSONResourceIntoCollection("profiles_100.json", collection = profilesCollection)

        profilesCollection.createIndex("contacts", ArrayIndexConfiguration("contacts"))

        val idx = profilesCollection.getIndexExpressions("contacts")
        Assert.assertNotNull(idx)
        Assert.assertEquals(1, idx.size)
        Assert.assertEquals("", idx[0])

        Assert.assertEquals("contacts", profilesCollection.getPathForIndex("contacts"))
    }

    /**
     * 3. TestCreateArrayIndexWithPathAndExpressions
     *
     * Description
     *     Test that creating an array index with path and expressions works as expected.
     *
     * Steps
     *     1. Load profiles.json into the collection named "_default.profiles".
     *     2. Create a ArrayIndexConfiguration object.
     *         - path: "contacts"
     *         - expressions: ["address.city", "address.state"]
     *     3. Create an array index named "contacts" in the profiles collection.
     *     4. Get index names from the profiles collection and check that the index named "contacts" exists.
     *     5. Get info of the index named "contacts" using an internal API and check that the
     *        index has path and expressions as configured.
     */
    @Ignore("Awaiting merge of Array Index feature")
    @Test
    fun testCreateArrayIndexWithPathAndExpressions() {
        val profilesCollection = testDatabase.createCollection("profiles")
        loadJSONResourceIntoCollection("profiles_100.json", collection = profilesCollection)

        val exprs = listOf("address.city", "address.state")

        profilesCollection.createIndex("contacts", ArrayIndexConfiguration("contacts", exprs))

        Assert.assertEquals("contacts", profilesCollection.getPathForIndex("contacts"))

        val idx = profilesCollection.getIndexExpressions("contacts")
        Assert.assertNotNull(idx)
        Assert.assertEquals(2, idx.size)
        Assert.assertTrue(idx.contains(exprs[0]))
        Assert.assertTrue(idx.contains(exprs[0]))
    }

    private fun Collection.getIndexExpressions(indexName: String) =
        this.getIndexInfo()
            .firstOrNull { it[Collection.INDEX_KEY_NAME] == indexName }
            ?.let { (it[Collection.INDEX_KEY_EXPR] as? String)?.split(",") } ?: emptyList()

    private fun Collection.getPathForIndex(indexName: String) =
        C4TestUtils.getIndexOptions(assertNonNull(this.getC4Index(indexName))).unnestPath
}

