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
package com.couchbase.lite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class ReplicatorConfigurationTest : BaseReplicatorTest() {

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun testIllegalMaxAttempts() {
        makeReplicatorConfig().maxAttempts = -1
    }

    @Test
    fun testMaxAttemptsZero() {
        makeReplicatorConfig().maxAttempts = 0
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun testIllegalAttemptsWaitTime() {
        makeReplicatorConfig().maxAttemptWaitTime = -1
    }

    @Test
    fun testMaxAttemptsWaitTimeZero() {
        makeReplicatorConfig().maxAttemptWaitTime = 0
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun testIllegalHeartbeatMin() {
        makeReplicatorConfig().heartbeat = -1
    }

    @Test
    fun testHeartbeatZero() {
        makeReplicatorConfig().heartbeat = 0
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun testIllegalHeartbeatMax() {
        makeReplicatorConfig().heartbeat = 2147484
    }

    @Test
    fun testConfigDefaults() {
        val config = CollectionConfiguration()
        assertNull(config.channels)
        assertNull(config.documentIDs)
        assertNull(config.pushFilter)
        assertNull(config.pullFilter)
        assertNull(config.conflictResolver)

        // this does not actually verify that setting null
        // causes Replication to use the default resolver.
        // It just tests that null is a legal value and that it sticks.
        config.conflictResolver = ConflictResolver { conflict -> null }
        assertNotNull(config.conflictResolver)
        config.conflictResolver = null
        assertNull(config.conflictResolver)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database, endpoint).
    //     2: Access collections property. It mush have one collection which is the default collection.
    //     6: ReplicatorConfiguration.database should be the database with which the configuration was created
    @Suppress("DEPRECATION")
    @Test
    fun testCreateConfigWithDatabase1() {
        val replConfig = ReplicatorConfiguration(testDatabase, mockURLEndpoint)
        val collections = replConfig.collections
        assertEquals(1, collections.size)
        assertTrue(collections.contains(testDatabase.defaultCollection))
        assertEquals(testDatabase, replConfig.database)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database, endpoint).
    //     3: Calling getCollectionConfig() with the default collection should produce a CollectionConfiguration
    //     4: CollectionConfiguration.collection should be the default collection.
    //     5: CollectionConfiguration.conflictResolver, pushFilter, pullFilter, channels, and documentIDs
    //        should be null.
    @Suppress("DEPRECATION")
    @Test
    fun testCreateConfigWithDatabase2() {
        val collectionConfig = ReplicatorConfiguration(testDatabase, mockURLEndpoint)
            .getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig)
        assertNull(collectionConfig!!.conflictResolver)
        assertNull(collectionConfig.pushFilter)
        assertNull(collectionConfig.pullFilter)
        assertNull(collectionConfig.channels)
        assertNull(collectionConfig.documentIDs)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //     3: Calling getCollectionConfig() with the default collection should produce a CollectionConfiguration
    //     4: CollectionConfiguration.conflictResolver should be the same as ReplicatorConfiguration.conflictResolver.
    @Suppress("DEPRECATION")
    @Test
    fun testCreateConfigWithDatabaseAndConflictResolver() {
        val resolver = ConflictResolver { conflict -> conflict.localDocument }
        val replConfig = ReplicatorConfiguration(testDatabase, mockURLEndpoint).setConflictResolver(resolver)
        assertEquals(resolver, replConfig.conflictResolver)
        val collectionConfig = replConfig.getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig)
        assertEquals(resolver, collectionConfig?.conflictResolver)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //     3: Verify that CollectionConfiguration.conflictResolver and ReplicatorConfiguration.conflictResolver
    //        are the same resolver..
    //     4: Update ReplicatorConfiguration.conflictResolver with a new conflict resolver.
    //     5-7: Verify that CollectionConfiguration.conflictResolver is still the same
    //          as ReplicatorConfiguration.conflictResolver..
    @Suppress("DEPRECATION")
    @Test
    fun testUpdateConflictResolverForDefaultCollection() {
        val resolver = ConflictResolver { conflict -> conflict.localDocument }
        val replConfig = ReplicatorConfiguration(testDatabase, mockURLEndpoint).setConflictResolver(resolver)
        assertEquals(
            replConfig.conflictResolver,
            replConfig.getCollectionConfiguration(testDatabase.defaultCollection!!)?.conflictResolver
        )
        val resolver2 = ConflictResolver { conflict -> conflict.localDocument }
        replConfig.conflictResolver = resolver2
        assertEquals(
            resolver2,
            replConfig.getCollectionConfiguration(testDatabase.defaultCollection!!)?.conflictResolver
        )
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs
    //     3: Call getCollectionConfig() method with the default collection.
    //       A CollectionConfiguration object should be returned and the properties in the config should
    //       be the same as the corresponding properties in ReplicatorConfiguration
    @Suppress("DEPRECATION")
    @Test
    fun testCreateConfigWithDatabaseAndFilters() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val replConfig1 = ReplicatorConfiguration(testDatabase, mockURLEndpoint)
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setChannels(listOf("CNBC", "ABC"))
            .setDocumentIDs(listOf("doc1", "doc2"))
        assertEquals(pushFilter1, replConfig1.pushFilter)
        assertEquals(pullFilter1, replConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), replConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), replConfig1.documentIDs?.toTypedArray())

        val collectionConfig1 = replConfig1.getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig1)
        assertEquals(pushFilter1, collectionConfig1!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), collectionConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), collectionConfig1.documentIDs?.toTypedArray())
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs.
    //     3: Call getCollectionConfig() method with the default collection.
    //       A CollectionConfiguration object should be returned and the properties in the config should
    //       be the same as the corresponding properties in ReplicatorConfiguration
    //     4: Update ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs with new values.
    //     5: Repeat #3. The previously obtains ReplicatorConfiguration should not change.
    //        The new one should have the new values.
    //     6: Update CollectionConfiguration.pushFilter, pullFilters, channels, and documentIDs with new values.
    //        Use addCollection() method to add the default collection with the updated config.
    //     7: Check ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs.
    //        The filters should be updated accordingly.
    @Suppress("DEPRECATION")
    @Test
    fun testUpdateFiltersForDefaultCollection1() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val replConfig1 = ReplicatorConfiguration(testDatabase, mockURLEndpoint)
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setChannels(listOf("CNBC", "ABC"))
            .setDocumentIDs(listOf("doc1", "doc2"))
        assertEquals(pushFilter1, replConfig1.pushFilter)
        assertEquals(pullFilter1, replConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), replConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), replConfig1.documentIDs?.toTypedArray())

        val collectionConfig1 = replConfig1.getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig1)
        assertEquals(pushFilter1, collectionConfig1!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), collectionConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), collectionConfig1.documentIDs?.toTypedArray())

        val pushFilter2 = ReplicationFilter { _, _ -> true }
        val pullFilter2 = ReplicationFilter { _, _ -> true }
        replConfig1
            .setPushFilter(pushFilter2)
            .setPullFilter(pullFilter2)
            .setChannels(listOf("Peacock", "History")).documentIDs = listOf("doc3")

        assertEquals(pushFilter1, collectionConfig1.pushFilter)
        assertEquals(pullFilter1, collectionConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), collectionConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), collectionConfig1.documentIDs?.toTypedArray())

        val collectionConfig2 = replConfig1.getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig2)
        assertEquals(pushFilter2, collectionConfig2!!.pushFilter)
        assertEquals(pullFilter2, collectionConfig2.pullFilter)
        assertArrayEquals(arrayOf("Peacock", "History"), collectionConfig2.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc3"), collectionConfig2.documentIDs?.toTypedArray())
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs.
    //     3: Call getCollectionConfig() method with the default collection.
    //       A CollectionConfiguration object should be returned and the properties in the config should
    //       be the same as the corresponding properties in ReplicatorConfiguration
    //     6: Update CollectionConfiguration.pushFilter, pullFilters, channels, and documentIDs with new values.
    //        Use addCollection() method to add the default collection with the updated config.
    //     7: Check ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs.
    //        The filters should be updated accordingly.
    @Suppress("DEPRECATION")
    @Test
    fun testUpdateFiltersForDefaultCollection2() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val replConfig1 = ReplicatorConfiguration(testDatabase, mockURLEndpoint)
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setChannels(listOf("CNBC", "ABC"))
            .setDocumentIDs(listOf("doc1", "doc2"))
        assertEquals(pushFilter1, replConfig1.pushFilter)
        assertEquals(pullFilter1, replConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), replConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), replConfig1.documentIDs?.toTypedArray())

        val collectionConfig1 = replConfig1.getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig1)
        assertEquals(pushFilter1, collectionConfig1!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig1.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), collectionConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), collectionConfig1.documentIDs?.toTypedArray())

        val pushFilter2 = ReplicationFilter { _, _ -> true }
        val pullFilter2 = ReplicationFilter { _, _ -> true }
        val collectionConfig2 = CollectionConfiguration()
            .setPushFilter(pushFilter2)
            .setPullFilter(pullFilter2)
            .setChannels(listOf("Peacock", "History"))
            .setDocumentIDs(listOf("doc3"))
        replConfig1.addCollection(testDatabase.defaultCollection!!, collectionConfig2)

        assertEquals(pushFilter2, replConfig1.pushFilter)
        assertEquals(pullFilter2, replConfig1.pullFilter)
        assertArrayEquals(arrayOf("Peacock", "History"), replConfig1.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc3"), replConfig1.documentIDs?.toTypedArray())

        val collectionConfig3 = replConfig1.getCollectionConfiguration(testDatabase.defaultCollection!!)
        assertNotNull(collectionConfig3)
        assertEquals(pushFilter2, collectionConfig3!!.pushFilter)
        assertEquals(pullFilter2, collectionConfig3.pullFilter)
        assertArrayEquals(arrayOf("Peacock", "History"), collectionConfig3.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc3"), collectionConfig3.documentIDs?.toTypedArray())
    }

    //     1: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     2: Access collections property and an empty collection list should be returned.
    @Test
    fun testCreateConfigWithEndpointOnly1() {
        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        val collections = replConfig1.collections
        assertNotNull(collections)
        assertTrue(collections.isEmpty())
    }

    //     1: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Access database property and Illegal State Exception will be thrown.
    @Suppress("DEPRECATION")
    @Test(expected = IllegalStateException::class)
    fun testCreateConfigWithEndpointOnly2() {
        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)
        replConfig1.database
    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Use addCollections() to add both colA and colB to the config without specifying a collection config.
    //     4: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     5: Use getCollectionConfig() to get the collection config for colA and colB.
    //        Both should be non-null, they should be different instances and he conflict resolver and filters be null.
    @Test
    fun testAddCollectionsWithoutCollectionConfig() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)
        replConfig1.addCollections(setOf(collectionA, collectionB), null)

        val collectionConfig1 = replConfig1.getCollectionConfiguration(collectionA)
        assertNotNull(collectionConfig1)
        assertNull(collectionConfig1!!.conflictResolver)
        assertNull(collectionConfig1.pushFilter)
        assertNull(collectionConfig1.pullFilter)
        assertNull(collectionConfig1.channels)
        assertNull(collectionConfig1.documentIDs)

        val collectionConfig2 = replConfig1.getCollectionConfiguration(collectionB)
        assertNotNull(collectionConfig2)
        assertNull(collectionConfig1.conflictResolver)
        assertNull(collectionConfig1.pushFilter)
        assertNull(collectionConfig1.pullFilter)
        assertNull(collectionConfig1.channels)
        assertNull(collectionConfig1.documentIDs)

        assertNotSame(collectionConfig1, collectionConfig2)
    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Create a CollectionConfiguration object, and set a conflictResolver and all filters.
    //     4: Use addCollections() to add both colA and colB to the config created in the previous step.
    //     5: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     6: Use getCollectionConfig() to get the collection config for colA and colB.
    //        Both should be non-null, they should be different instances and he conflict resolver and filters
    //        should be as assigned.
    @Test
    fun testAddCollectionsWithCollectionConfig() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val resolver = ConflictResolver { conflict -> conflict.localDocument }
        val collectionConfig0 = CollectionConfiguration()
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setConflictResolver(resolver)
        replConfig1.addCollections(setOf(collectionA, collectionB), collectionConfig0)

        val collectionConfig1 = replConfig1.getCollectionConfiguration(collectionA)
        assertNotNull(collectionConfig1)
        assertEquals(pushFilter1, collectionConfig1!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig1.pullFilter)
        assertEquals(resolver, collectionConfig1.conflictResolver)

        val collectionConfig2 = replConfig1.getCollectionConfiguration(collectionB)
        assertNotNull(collectionConfig2)
        assertEquals(pushFilter1, collectionConfig2!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig2.pullFilter)
        assertEquals(resolver, collectionConfig2.conflictResolver)

        assertNotSame(collectionConfig1, collectionConfig2)
    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Use addCollection() to add the colA without specifying a collection config.
    //     4: Create a CollectionConfiguration object, and set a conflictResolver and all filters.
    //     5: Use addCollection() to add the colB with the collection config created from the previous step.
    //     6: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     7: Use getCollectionConfig() to get the collection config for colA and colB.
    //        All of the properties for colA's config should be null. The properties for colB should
    //        be be those passed in the configuration used to add it..
    @Test
    fun testAddCollection() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        replConfig1.addCollection(collectionA, null)

        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val resolver = ConflictResolver { conflict -> conflict.localDocument }
        val collectionConfig0 = CollectionConfiguration()
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setConflictResolver(resolver)
        replConfig1.addCollection(collectionB, collectionConfig0)

        assertTrue(replConfig1.collections.contains(collectionA))
        assertTrue(replConfig1.collections.contains(collectionB))

        val collectionConfig1 = replConfig1.getCollectionConfiguration(collectionA)
        assertNotNull(collectionConfig1)
        assertNull(collectionConfig1!!.pushFilter)
        assertNull(collectionConfig1.pullFilter)
        assertNull(collectionConfig1.conflictResolver)

        val collectionConfig2 = replConfig1.getCollectionConfiguration(collectionB)
        assertNotNull(collectionConfig2)
        assertEquals(pushFilter1, collectionConfig2!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig2.pullFilter)
        assertEquals(resolver, collectionConfig2.conflictResolver)
    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Create a CollectionConfiguration object, and set a conflictResolver and all filters.
    //     4: Use addCollection() to add the colA and colB with the collection config created from the previous step.
    //     5: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     6: Use getCollectionConfig() to get the collection config for colA and colB. Check the returned configs
    //        for both collections and ensure that both configs contain the correct values.
    //     7: Use addCollection() to add colA again without specifying collection config.
    //     8: Create a new CollectionConfiguration object, and set a conflictResolver and all filters.
    //     9: Use addCollection() to add colB again with the updated collection config created from the previous step.
    //     10: Use getCollectionConfig() to get the collection config for colA and colB.
    //         Check the configs for both collections and ensure that they contain the updated values.
    @Test
    fun testUpdateCollectionConfig() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val resolver1 = ConflictResolver { conflict -> conflict.localDocument }
        val collectionConfig0 = CollectionConfiguration()
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setConflictResolver(resolver1)

        replConfig1.addCollection(collectionA, collectionConfig0)
        replConfig1.addCollection(collectionB, collectionConfig0)


        assertTrue(replConfig1.collections.contains(collectionA))
        assertTrue(replConfig1.collections.contains(collectionB))

        val collectionConfig1 = replConfig1.getCollectionConfiguration(collectionA)
        assertNotNull(collectionConfig1)
        assertEquals(pushFilter1, collectionConfig1!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig1.pullFilter)
        assertEquals(resolver1, collectionConfig1.conflictResolver)

        val collectionConfig2 = replConfig1.getCollectionConfiguration(collectionB)
        assertNotNull(collectionConfig2)
        assertEquals(pushFilter1, collectionConfig2!!.pushFilter)
        assertEquals(pullFilter1, collectionConfig2.pullFilter)
        assertEquals(resolver1, collectionConfig2.conflictResolver)

        val pushFilter2 = ReplicationFilter { _, _ -> true }
        val pullFilter2 = ReplicationFilter { _, _ -> true }
        val resolver2 = ConflictResolver { conflict -> conflict.localDocument }
        val collectionConfig3 = CollectionConfiguration()
            .setPushFilter(pushFilter2)
            .setPullFilter(pullFilter2)
            .setConflictResolver(resolver2)

        replConfig1.addCollection(collectionA, null)
        replConfig1.addCollection(collectionB, collectionConfig3)

        val collectionConfig4 = replConfig1.getCollectionConfiguration(collectionA)
        assertNotNull(collectionConfig3)
        assertNull(collectionConfig4!!.pushFilter)
        assertNull(collectionConfig4.pullFilter)
        assertNull(collectionConfig4.conflictResolver)

        val collectionConfig5 = replConfig1.getCollectionConfiguration(collectionB)
        assertNotNull(collectionConfig5)
        assertEquals(pushFilter2, collectionConfig5!!.pushFilter)
        assertEquals(pullFilter2, collectionConfig5.pullFilter)
        assertEquals(resolver2, collectionConfig5.conflictResolver)
    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Create a CollectionConfiguration object, and set a conflictResolvers and all filters.
    //     4: Use addCollections() to add both colA and colB to the config with the CollectionConfiguration.
    //     5: Check ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     6: ...
    //     7: Remove "colB" by calling removeCollection().
    //     8: Check  ReplicatorConfiguration.collections. The collections should have only colA.
    //     9: Use getCollectionConfig() to get the collection config for colA and colB.
    //        All of colB's properties should be null.
    @Test
    fun testRemoveCollection() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val resolver1 = ConflictResolver { conflict -> conflict.localDocument }
        val collectionConfig0 = CollectionConfiguration()
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setConflictResolver(resolver1)

        replConfig1.addCollection(collectionA, collectionConfig0)
        replConfig1.addCollection(collectionB, collectionConfig0)

        assertTrue(replConfig1.collections.contains(collectionA))
        assertTrue(replConfig1.collections.contains(collectionB))

        replConfig1.removeCollection(collectionB)
        assertTrue(replConfig1.collections.contains(collectionA))
        assertFalse(replConfig1.collections.contains(collectionB))

        assertNotNull(replConfig1.getCollectionConfiguration(collectionA))
        assertNull(replConfig1.getCollectionConfiguration(collectionB))
    }

    //     1: Create collection "colA" in the scope "scopeA" using database instance A.
    //     2: Create collection "colB" in the scope "scopeA" using database instance B.
    //     3: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     4: Use addCollections() to add both colA and colB. This should cause an InvalidArgumentException.
    @Test(expected = IllegalArgumentException::class)
    fun testAddCollectionsFromDifferentDatabaseInstances1() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = targetDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        replConfig1.addCollections(setOf(collectionA, collectionB), null)
    }

    //     1: Create collection "colA" in the scope "scopeA" using database instance A.
    //     2: Create collection "colB" in the scope "scopeA" using database instance B.
    //     3: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     4: Use addCollections() to add both colA and colB. This should cause an InvalidArgumentException.
    //     5: Use addCollection() to add colA. Ensure that the colA has been added correctly.
    //     6: Use addCollection() to add colB. This should cause an InvalidArgumentException.
    @Test(expected = IllegalArgumentException::class)
    fun testAddCollectionsFromDifferentDatabaseInstances2() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = targetDatabase.createCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        replConfig1.addCollection(collectionA, null)
        replConfig1.addCollection(collectionB, null)
    }

    //     1: Create collection "colA" in the scope "scopeA" using database instance A.
    //     2: Create collection "colB" in the scope "scopeA" using database instance B.
    //     3: Delete collection colB
    //     4: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     5: Use addCollections() to add both colA and colB. This should cause an InvalidArgumentException.
    @Test(expected = IllegalArgumentException::class)
    fun testAddDeletedCollections1() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = targetDatabase.createCollection("colB", "scopeA")

        testDatabase.deleteCollection("colB", "scopeA")

        ReplicatorConfiguration(mockURLEndpoint)
            .addCollections(setOf(collectionA, collectionB), null)
    }

    //     1: Create collection "colA" in the scope "scopeA" using database instance A.
    //     2: Create collection "colB" in the scope "scopeA" using database instance B.
    //     3: Delete collection colB
    //     4: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     6: Use addCollection() to add colA. Ensure that the colA has been added correctly.
    @Test
    fun testAddDeletedCollections2() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        targetDatabase.createCollection("colB", "scopeA")

        testDatabase.deleteCollection("colB", "scopeA")

        val replConfig1 = ReplicatorConfiguration(mockURLEndpoint)

        replConfig1.addCollection(collectionA, null)

        val collections = replConfig1.collections
        assertEquals(1, collections.size)
        assertTrue(collections.contains(collectionA))
    }

    //     1: Create collection "colA" in the scope "scopeA" using database instance A.
    //     2: Create collection "colB" in the scope "scopeA" using database instance B.
    //     3: Delete collection colB
    //     4: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     7: Use addCollection() to add colB. This should cause an InvalidArgumentException.
    @Test(expected = IllegalArgumentException::class)
    fun testAddDeletedCollections3() {
        testDatabase.createCollection("colA", "scopeA")
        val collectionB = targetDatabase.createCollection("colB", "scopeA")

        targetDatabase.deleteCollection("colB", "scopeA")

        ReplicatorConfiguration(mockURLEndpoint)
            .addCollection(collectionB, null)
    }

    // CBL-3736
    // Attempting to configure a replicator with no collection
    // should throw an illegal argument exception.
    @Test(expected = IllegalArgumentException::class)
    fun testCreateReplicatorWithNoCollections() {
        Replicator(ReplicatorConfiguration(mockURLEndpoint))
    }

    // CBL-3736
    // After the last collection from a scope is deleted,
    // an attempt to get the scope should return null
    @Test
    fun testUseScopeAfterScopeDeleted() {
        assertNotNull(testDatabase.createCollection("colA", "scopeA"))

        testDatabase.deleteCollection("colA", "scopeA")

        assertNull(testDatabase.getScope("scopeA"))
    }

    // CBL-3736
    // An attempt to get a collection from a closed database
    // should throw a CouchbaseLiteException
    @Test(expected = CouchbaseLiteException::class)
    fun testUseScopeAfterDBClosed() {
        assertNotNull(testDatabase.createCollection("colA", "scopeA"))

        testDatabase.close()

        testDatabase.getCollection("colA", "scopeA")
    }
}