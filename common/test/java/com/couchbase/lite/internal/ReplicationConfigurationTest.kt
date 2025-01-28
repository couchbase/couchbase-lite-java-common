//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal

import com.couchbase.lite.BaseDbTest
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.ReplicationFilter
import com.couchbase.lite.Scope
import com.couchbase.lite.internal.core.C4Replicator
import com.couchbase.lite.internal.fleece.FLEncoder
import com.couchbase.lite.internal.fleece.FLValue
import com.couchbase.lite.internal.fleece.withContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// These two functions have to be here, to expose package private state to other tests

fun boundCollectionCount() = ReplicationCollection.BOUND_COLLECTIONS.size()

fun clearBoundCollections() = ReplicationCollection.BOUND_COLLECTIONS.clear()


class ReplicationConfigurationTest : BaseDbTest() {
    @Before
    fun setUpC4ReplicatorTest() = clearBoundCollections()

    @After
    fun tearDownC4ReplicatorTest() = clearBoundCollections()

    @Test
    fun testCreateAndCloseReplicationCollection() {
        assertEquals(0, boundCollectionCount())
        val replColl = ReplicationCollection.create(testDatabase.defaultCollection, null, null, null, null)
        assertEquals(1, boundCollectionCount())
        replColl.close()
        assertEquals(0, boundCollectionCount())
    }

    @Test
    fun testCreateAllReplicationCollection() {
        val colls = mapOf(
            testDatabase.defaultCollection to CollectionConfiguration(),
            testDatabase.createCollection("antique_clocks") to CollectionConfiguration()
        )

        assertEquals(0, boundCollectionCount())
        val replColls = ReplicationCollection.createAll(colls)
        assertEquals(2, replColls.size)
        assertEquals(2, boundCollectionCount())

        val replColl = replColls.firstOrNull { it.name == "antique_clocks" }
        assertNotNull(replColl)
        assertEquals(replColl, ReplicationCollection.BOUND_COLLECTIONS.getBinding(replColl!!.token))
        assertEquals(Scope.DEFAULT_NAME, replColl.scope)
        assertNull(replColl.options)
        assertNull(replColl.c4PushFilter)
        assertNull(replColl.c4PullFilter)
    }

    @Test
    fun testReplicationCollectionChannels() {
        val config = CollectionConfiguration()
        config.channels = listOf("x", "y", "z")
        val colls = mapOf(testDatabase.createCollection("rockwell_plates") to config)
        val opts = FLValue.fromData(ReplicationCollection.createAll(colls)[0].options!!)
            .asMap(String::class.java, Object::class.java)
        assertEquals(1, opts.size)
        assertEquals(listOf("x", "y", "z"), opts[C4Replicator.REPLICATOR_OPTION_CHANNELS])
    }

    @Test
    fun testReplicationCollectionDocIds() {
        val config = CollectionConfiguration()
        config.documentIDs = listOf("x", "y", "z")
        val colls = mapOf(testDatabase.createCollection("porcelain_dolls") to config)
        val opts = FLValue.fromData(ReplicationCollection.createAll(colls)[0].options!!)
            .asMap(String::class.java, Object::class.java)
        assertEquals(1, opts.size)
        assertEquals(listOf("x", "y", "z"), opts[C4Replicator.REPLICATOR_OPTION_DOC_IDS])
    }

    @Test
    fun testReplicationCollectionPullFilter() {
        var calls = 0

        val coll = testDatabase.createCollection("pogs")
        val doc = MutableDocument("BorisTheSpider")
        coll.save(doc)

        val body = FLValue.fromData(FLEncoder.encodeMap(mapOf("Haight" to "Ashbury"))!!)

        val config = CollectionConfiguration()
        config.pullFilter = ReplicationFilter { _, _ -> calls++; true }
        val colls = mapOf(testDatabase.createCollection("pogs") to config)
        val token = ReplicationCollection.createAll(colls)[0].token

        withContent(body) {
            ReplicationCollection.filterCallback(token, null, null, "doc-1", "99", 0, it, false)
            ReplicationCollection.filterCallback(token, null, null, "doc-2", "88", 0, it, true)
        }

        assertEquals(1, calls)
    }

    @Test
    fun testReplicationCollectionPushFilter() {
        var calls = 0

        val coll = testDatabase.createCollection("pogs")
        val doc = MutableDocument("BorisTheSpider")
        coll.save(doc)

        val body = FLValue.fromData(FLEncoder.encodeMap(mapOf("Haight" to "Ashbury"))!!)

        val config = CollectionConfiguration()
        config.pushFilter = ReplicationFilter { _, _ -> calls++; true }
        val colls = mapOf(testDatabase.createCollection("pogs") to config)
        val token = ReplicationCollection.createAll(colls)[0].token

        withContent(body) {
            ReplicationCollection.filterCallback(token, null, null, "doc-1", "99", 0, it, false)
            ReplicationCollection.filterCallback(token, null, null, "doc-2", "88", 0, it, true)
        }

        assertEquals(1, calls)
    }

    @Test
    fun testReplicationCollectionPushPullFilter() {
        var calls = 0

        val coll = testDatabase.createCollection("pogs")
        val doc = MutableDocument("BorisTheSpider")
        coll.save(doc)

        val body = FLValue.fromData(FLEncoder.encodeMap(mapOf("Haight" to "Ashbury"))!!)

        val config = CollectionConfiguration()
        config.pushFilter = ReplicationFilter { _, _ -> calls++; true }
        config.pullFilter = ReplicationFilter { _, _ -> calls++; true }
        val colls = mapOf(testDatabase.createCollection("pogs") to config)
        val token = ReplicationCollection.createAll(colls)[0].token

        withContent(body) {
            ReplicationCollection.filterCallback(token, null, null, "doc-1", "99", 0, it, false)
            ReplicationCollection.filterCallback(token, null, null, "doc-2", "88", 0, it, true)
        }

        assertEquals(2, calls)
    }

    @Test
    fun testReplicationCollectionPushPullFilterNoCallback() {
        var calls = 0

        val coll = testDatabase.createCollection("pogs")
        val doc = MutableDocument("BorisTheSpider")
        coll.save(doc)

        val body = FLValue.fromData(FLEncoder.encodeMap(mapOf("Haight" to "Ashbury"))!!)

        val config = CollectionConfiguration()
        config.pushFilter = ReplicationFilter { _, _ -> calls++; true }
        config.pullFilter = ReplicationFilter { _, _ -> calls++; true }
        val colls = mapOf(testDatabase.createCollection("pogs") to config)

        // this token should be ignored.
        val token = ReplicationCollection.createAll(colls)[0].token + 1

        withContent(body) {
            ReplicationCollection.filterCallback(token, null, null, "doc-1", "99", 0, it, false)
            ReplicationCollection.filterCallback(token, null, null, "doc-2", "88", 0, it, true)
        }

        assertEquals(0, calls)
    }
}