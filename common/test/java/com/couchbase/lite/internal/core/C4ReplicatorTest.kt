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
package com.couchbase.lite.internal.core

import com.couchbase.lite.BaseReplicatorTest
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.ReplicatorConfiguration
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.URLEndpoint
import com.couchbase.lite.getC4Db
import com.couchbase.lite.internal.SocketFactory
import com.couchbase.lite.internal.boundCollectionCount
import com.couchbase.lite.internal.clearBoundCollections
import com.couchbase.lite.internal.replicator.CBLCookieStore
import com.couchbase.lite.internal.sockets.MessageFraming
import com.couchbase.lite.mock.MockNativeReplicator
import com.couchbase.lite.mock.MockNativeSocket
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URI


class C4ReplicatorTest : BaseReplicatorTest() {

    @Before
    fun setUpC4ReplicatorTest() {
        C4Replicator.BOUND_REPLICATORS.clear()
        clearBoundCollections()
    }

    @After
    fun tearDownC4ReplicatorTest() {
        clearBoundCollections()
        C4Replicator.BOUND_REPLICATORS.clear()
    }

    @Test
    fun testCreateRemoteReplicator() {
        var calls = 0

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(0, boundCollectionCount())

        val config = ReplicatorConfiguration(URLEndpoint(URI("wss://foo")))
        config.addCollection(testCollection, CollectionConfiguration())

        val c4Repl = C4Replicator.createRemoteReplicator(
            object : MockNativeReplicator() {
                override fun nStop(peer: Long) {
                    calls++
                }

                override fun nFree(peer: Long) {
                    calls++
                }
            },
            mapOf(testCollection to CollectionConfiguration()),
            C4BaseTest.MOCK_PEER,
            null,
            null,
            0,
            null,
            null,
            MessageFraming.NO_FRAMING,
            ReplicatorType.PUSH_AND_PULL,
            false,
            null,
            { _, _ -> },
            { _, _ -> },
            config.testReplicator(),
            SocketFactory(
                config,
                object : CBLCookieStore {
                    override fun setCookies(uri: URI, cookies: List<String>, acceptParents: Boolean) = Unit
                    override fun getCookies(uri: URI): String? = null
                })
            { }
        )

        Assert.assertEquals(1, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(c4Repl, C4Replicator.BOUND_REPLICATORS.getBinding(c4Repl.token))

        Assert.assertEquals(1, c4Repl.colls.size)
        Assert.assertEquals(1, boundCollectionCount())

        c4Repl.close()
        Assert.assertEquals(2, calls)

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(0, boundCollectionCount())
    }

    @Test
    fun testCreateLocalReplicator() {
        var calls = 0

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(0, boundCollectionCount())

        val config = ReplicatorConfiguration(URLEndpoint(URI("wss://foo")))
        config.addCollection(testCollection, CollectionConfiguration())

        val c4Repl = C4Replicator.createLocalReplicator(
            object : MockNativeReplicator() {
                override fun nStop(peer: Long) {
                    calls++
                }

                override fun nFree(peer: Long) {
                    calls++
                }
            },
            mapOf(testCollection to CollectionConfiguration()),
            C4BaseTest.MOCK_PEER,
            testDatabase.getC4Db,
            ReplicatorType.PUSH_AND_PULL,
            false,
            null,
            { _, _ -> },
            { _, _ -> },
            config.testReplicator()
        )

        Assert.assertEquals(1, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(c4Repl, C4Replicator.BOUND_REPLICATORS.getBinding(c4Repl.token))

        Assert.assertEquals(1, c4Repl.colls.size)
        Assert.assertEquals(1, boundCollectionCount())

        c4Repl.close()
        Assert.assertEquals(2, calls)

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(0, boundCollectionCount())
    }

    @Test
    fun testCreateMessageEndpointReplicator() {
        var calls = 0

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())
        val c4Repl = C4Replicator.createMessageEndpointReplicator(
            object : MockNativeReplicator() {
                override fun nStop(peer: Long) {
                    calls++
                }

                override fun nFree(peer: Long) {
                    calls++
                }
            },
            setOf(testCollection),
            C4BaseTest.MOCK_PEER,
            C4Socket.createSocket(MockNativeSocket(), C4BaseTest.MOCK_PEER),
            null
        )
        { _, _ -> }

        Assert.assertEquals(1, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(c4Repl, C4Replicator.BOUND_REPLICATORS.getBinding(c4Repl.token))

        Assert.assertEquals(1, c4Repl.colls.size)
        Assert.assertEquals(1, boundCollectionCount())

        c4Repl.close()
        Assert.assertEquals(2, calls)

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())
        Assert.assertEquals(0, boundCollectionCount())
    }

    @Test
    fun testStatusChangedCallback() {
        var calls = 0

        Assert.assertEquals(0, C4Replicator.BOUND_REPLICATORS.size())

        val c4Repl = C4Replicator.createMessageEndpointReplicator(
            MockNativeReplicator(),
            setOf(testCollection),
            C4BaseTest.MOCK_PEER,
            C4Socket.createSocket(MockNativeSocket(), C4BaseTest.MOCK_PEER),
            null
        )
        { _, _ -> calls++ }

        C4Replicator.statusChangedCallback(
            c4Repl.token,
            C4ReplicatorStatus(C4ReplicatorStatus.ActivityLevel.BUSY, 0, 0)
        )
        Assert.assertEquals(1, calls)

        c4Repl.close()

        C4Replicator.statusChangedCallback(
            c4Repl.token,
            C4ReplicatorStatus(C4ReplicatorStatus.ActivityLevel.BUSY, 0, 0)
        )
        Assert.assertEquals(1, calls)
    }

    @Test
    fun testDocumentEndedCallback() {
        var calls = 0

        val config = ReplicatorConfiguration(URLEndpoint(URI("wss://foo")))
        config.addCollection(testCollection, CollectionConfiguration())

        val c4Repl = C4Replicator.createLocalReplicator(
            MockNativeReplicator(),
            mapOf(testCollection to CollectionConfiguration()),
            C4BaseTest.MOCK_PEER,
            testDatabase.getC4Db,
            ReplicatorType.PUSH_AND_PULL,
            false,
            null,
            { _, _ -> },
            { _, _ -> calls++ },
            config.testReplicator()
        )

        val docEnd = C4DocumentEnded(C4BaseTest.MOCK_TOKEN, "micro", "WWI sabres", "doc-1", "#57", 0, 0L, 0, 0, 0, true)

        C4Replicator.documentEndedCallback(c4Repl.token, true, docEnd)
        Assert.assertEquals(1, calls)

        c4Repl.close()

        C4Replicator.documentEndedCallback(c4Repl.token, true, docEnd)

        Assert.assertEquals(1, calls)
    }
}