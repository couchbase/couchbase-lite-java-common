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

import com.couchbase.lite.internal.ImmutableReplicatorConfiguration
import com.couchbase.lite.internal.core.C4Replicator
import org.junit.Assert
import org.junit.Test


class ReplicatorConfigurationTest : BaseReplicatorTest() {

    @Test
    fun testIllegalMaxAttempts() {
        Assert.assertThrows(IllegalArgumentException::class.java) { makeSimpleReplConfig(maxAttempts = -1) }
    }

    @Test
    fun testMaxAttemptsZero() {
        makeSimpleReplConfig(maxAttempts = 0)
    }

    @Test
    fun testIllegalAttemptsWaitTime() {
        Assert.assertThrows(IllegalArgumentException::class.java) { makeSimpleReplConfig(maxAttemptWaitTime = -1) }
    }

    @Test
    fun testMaxAttemptsWaitTimeZero() {
        makeSimpleReplConfig(maxAttemptWaitTime = 0)
    }

    @Test
    fun testIllegalHeartbeatMin() {
        Assert.assertThrows(IllegalArgumentException::class.java) { makeSimpleReplConfig().heartbeat = -1 }
    }

    @Test
    fun testHeartbeatZero() {
        makeSimpleReplConfig().heartbeat = 0
    }

    @Test
    fun testIllegalHeartbeatMax() {
        Assert.assertThrows(IllegalArgumentException::class.java) { makeSimpleReplConfig().heartbeat = 2147484 }
    }

    // Can't test the EE parameter (self-signed only) here
    @Test
    fun testCreateConfigDefaults() {
        val collectionConfigs = CollectionConfiguration.fromCollections(setOf(testCollection))
        val config = ReplicatorConfiguration(collectionConfigs, mockURLEndpoint)

        val immutableConfig = ImmutableReplicatorConfiguration(config)
        Assert.assertEquals(Defaults.Replicator.TYPE, immutableConfig.type)
        Assert.assertEquals(Defaults.Replicator.CONTINUOUS, immutableConfig.isContinuous)

        val opts = immutableConfig.connectionOptions
        Assert.assertEquals(Defaults.Replicator.HEARTBEAT, opts[C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL])
        Assert.assertEquals(
            Defaults.Replicator.MAX_ATTEMPTS_SINGLE_SHOT - 1,
            opts[C4Replicator.REPLICATOR_OPTION_MAX_RETRIES]
        )
        Assert.assertEquals(
            Defaults.Replicator.MAX_ATTEMPTS_WAIT_TIME,
            opts[C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL]
        )
        Assert.assertEquals(
            Defaults.Replicator.ENABLE_AUTO_PURGE,
            opts[C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE]
        )
        Assert.assertEquals(
            Defaults.Replicator.ACCEPT_PARENT_COOKIES,
            opts[C4Replicator.REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES]
        )
    }

    // Can't test the EE parameter (self-signed only) here
    @Test
    fun testCreateConfigCompatibility() {
        val collectionConfigs = CollectionConfiguration.fromCollections(setOf(testCollection))
        val config = ReplicatorConfiguration(collectionConfigs, mockURLEndpoint)

        config.heartbeat = 6
        config.maxAttempts = 6
        config.maxAttemptWaitTime = 6

        val opts1 = ImmutableReplicatorConfiguration(config).connectionOptions
        Assert.assertEquals(6, opts1[C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL])
        Assert.assertEquals(6, opts1[C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL])
        Assert.assertEquals(6 - 1, opts1[C4Replicator.REPLICATOR_OPTION_MAX_RETRIES])

        config.heartbeat = 0
        config.maxAttempts = 0
        config.maxAttemptWaitTime = 0

        val opts2 = ImmutableReplicatorConfiguration(config).connectionOptions
        Assert.assertEquals(Defaults.Replicator.HEARTBEAT, opts2[C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL])
        Assert.assertEquals(
            Defaults.Replicator.MAX_ATTEMPTS_WAIT_TIME,
            opts2[C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL]
        )
        Assert.assertEquals(
            Defaults.Replicator.MAX_ATTEMPTS_SINGLE_SHOT - 1,
            opts2[C4Replicator.REPLICATOR_OPTION_MAX_RETRIES]
        )
    }

    /****************** Scopes and Collections Section 8.13 ****************/


    // 8.13.1a Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Access collections property. The returned collections will have one collection
    // which is the default collection.
    //
    // Access database property, and the database object from the init should be
    // returned.
    @Test
    fun testCreateConfigWithDatabaseA() {
        val collectionConfigs = CollectionConfiguration.fromCollections(testDatabase.collections)
        val replConfig = ReplicatorConfiguration(collectionConfigs, mockURLEndpoint)
        val collections = replConfig.collectionConfigs.map { it -> it.collection }
        Assert.assertEquals(1, collections.size)
        Assert.assertTrue(collections.contains(testDatabase.defaultCollection))
    }

    // 8.13.1b Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Access collections property. The returned collections will have one collection
    // which is the default collection.
    //
    // Call getCollectionConfig() method with the default collection. A
    // CollectionConfiguration object should be returned.
    //
    // Check CollectionConfiguration.conflictResolver, .pushFilter, pullFilters,
    // channels, and documentIDs. The return object of those properties should be NULL.
    @Test
    fun testCreateConfigWithDatabaseB() {
        val collectionConfigs = CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        val collectionConfig = ReplicatorConfiguration(collectionConfigs, mockURLEndpoint).collectionConfigs

        collectionConfig.forEach { it ->
            Assert.assertNotNull(it)
            Assert.assertNull(it.conflictResolver)
            Assert.assertNull(it.pushFilter)
            Assert.assertNull(it.pullFilter)
            Assert.assertNull(it.channels)
            Assert.assertNull(it.documentIDs)
        }

    }

    // 8.13.2 Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //
    // Check CollectionConfiguration.conflictResolver. The returned conflict resolver
    // should be the same as ReplicatorConfiguration.conflictResolver.
    @Test
    fun testCreateConfigWithDatabaseAndConflictResolver() {
        val resolver = localResolver
        val collectConfigs = ReplicatorConfiguration(
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection)),
            mockURLEndpoint
        ).collectionConfigs

        collectConfigs.forEach { it.conflictResolver = resolver }

        collectConfigs.forEach { it ->
            Assert.assertNotNull(it)
            Assert.assertEquals(resolver, it.conflictResolver)
        }
    }

    // 8.13.3Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //
    // Check CollectionConfiguration.conflictResolver. The conflict resolver should be the
    // same as ReplicatorConfiguration.conflictResolver.
    //
    // Update ReplicatorConfiguration.conflictResolver with a new conflict resolver.
    //
    // Call getCollectionConfig() method with the default collection. Check
    // CollectionConfiguration.conflictResolver. The conflict resolver should be
    // updated accordingly.
    //
    // Update CollectionConfiguration.conflictResolver with a new conflict resolver.
    //
    // Check ReplicatorConfiguration.conflictResolver. The conflict resolver should be
    // updated accordingly.
    @Test
    fun testUpdateConflictResolverForDefaultCollection() {
        val resolver = localResolver
        val collectConfigs = ReplicatorConfiguration(
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection)),
            mockURLEndpoint
        ).collectionConfigs

        collectConfigs.forEach { it.conflictResolver = resolver }

        collectConfigs.forEach { it ->
            Assert.assertEquals(
                it.conflictResolver,
                resolver
            )
        }
        val resolver2 = localResolver
        collectConfigs.forEach { it.conflictResolver = resolver2 }

        collectConfigs.forEach { it->
            Assert.assertEquals(
                resolver2,
                it.conflictResolver
            )
        }

    }

    // 8.13.4 Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs
    //
    // Call getCollectionConfig() method with the default collection. A
    // CollectionConfiguration object should be returned. The filters in the config
    // should be the same ReplicatorConfiguration.pushFilter, pullFilters, channels,
    // and documentIDs.
    @Test
    fun testCreateConfigWithDatabaseAndFilters() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val collecConfigs =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        val replConfig1 = ReplicatorConfiguration(collecConfigs, mockURLEndpoint)
        replConfig1.collectionConfigs.forEach { collecConfig ->
            collecConfig
                .setPushFilter(pushFilter1)
                .setPullFilter(pullFilter1)
                .setChannels(listOf("CNBC", "ABC"))
                .setDocumentIDs(listOf("doc1", "doc2"))
        }

        replConfig1.collectionConfigs.forEach { collecConfig ->
            Assert.assertEquals(pushFilter1, collecConfig.pushFilter)
            Assert.assertEquals(pullFilter1, collecConfig.pullFilter)
            Assert.assertArrayEquals(arrayOf("CNBC", "ABC"), collecConfig.channels?.toTypedArray())
            Assert.assertArrayEquals(arrayOf("doc1", "doc2"), collecConfig.documentIDs?.toTypedArray())

        }
    }

    // 8.13.5a Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs.
    //
    // Call getCollectionConfig() method with the default collection. A
    // CollectionConfiguration object should be returned. The filters in the config
    // should be the same ReplicatorConfiguration.pushFilter, pullFilters, channels,
    // and documentIDs.
    //
    // Update ReplicatorConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs with new values.
    //
    // Call getCollectionConfig() method with the default collection object getting
    // from the database. A CollectionConfiguration object should be returned. The
    // filters in the config be updated accordingly.
    //
    // Update CollectionConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs with new values.
    //
    // Check ReplicatorConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs. The filters should be updated accordingly.
    @Test
    fun testUpdateFiltersForDefaultCollectionA() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }

        val collectConfigs =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))

        val replConfig1 = ReplicatorConfiguration(collectConfigs, mockURLEndpoint)
        replConfig1.collectionConfigs.forEach { collectConfig ->
            collectConfig
                .setPushFilter(pushFilter1)
                .setPullFilter(pullFilter1)
                .setChannels(listOf("CNBC", "ABC"))
                .setDocumentIDs(listOf("doc1", "doc2"))
        }
        val collectionConfig1 = replConfig1.collectionConfigs

        collectionConfig1.forEach { collectConfig ->
            Assert.assertEquals(pushFilter1, collectConfig.pushFilter)
            Assert.assertEquals(pullFilter1, collectConfig.pullFilter)
            Assert.assertArrayEquals(arrayOf("CNBC", "ABC"), collectConfig.channels?.toTypedArray())
            Assert.assertArrayEquals(
                arrayOf("doc1", "doc2"),
                collectConfig.documentIDs?.toTypedArray()
            )
        }

        val pushFilter2 = ReplicationFilter { _, _ -> true }
        val pullFilter2 = ReplicationFilter { _, _ -> true }
        replConfig1.collectionConfigs.map { collectionConfiguration ->
            collectionConfiguration
                .setPushFilter(pushFilter2)
                .setPullFilter(pullFilter2)
                .setChannels(listOf("Peacock", "History")).documentIDs = listOf("doc3")
        }

        replConfig1.collectionConfigs.forEach { collectionConfig2 ->
            Assert.assertNotNull(collectionConfig2)
            Assert.assertEquals(pushFilter2, collectionConfig2.pushFilter)
            Assert.assertEquals(pullFilter2, collectionConfig2.pullFilter)
            Assert.assertArrayEquals(arrayOf("Peacock", "History"), collectionConfig2.channels?.toTypedArray())
            Assert.assertArrayEquals(arrayOf("doc3"), collectionConfig2.documentIDs?.toTypedArray())
        }
    }


    // 8.13.5b Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs.
    //
    // Call getCollectionConfig() method with the default collection. A
    // CollectionConfiguration object should be returned. The filters in the config
    // should be the same ReplicatorConfiguration.pushFilter, pullFilters, channels,
    // and documentIDs.
    //
    // Call getCollectionConfig() method with the default collection object getting
    // from the database. A CollectionConfiguration object should be returned. The
    // filters in the config be updated accordingly.
    //
    // Update CollectionConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs with new values. Use addCollection() method to add the default
    // collection with the updated config.
    //
    // Check ReplicatorConfiguration.pushFilter, pullFilters, channels, and
    // documentIDs. The filters should be updated accordingly.
    @Test
    fun testUpdateFiltersForDefaultCollectionB() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val collectionConfigs =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        var replConfig1 = ReplicatorConfiguration(collectionConfigs, mockURLEndpoint)
        replConfig1
            .collectionConfigs.forEach { it ->
                it
                    .setPushFilter(pushFilter1)
                    .setPullFilter(pullFilter1)
                    .setChannels(listOf("CNBC", "ABC"))
                    .setDocumentIDs(listOf("doc1", "doc2"))
            }

        replConfig1.collectionConfigs.forEach { collectionConfig ->
            Assert.assertEquals(pushFilter1, collectionConfig.pushFilter)
            Assert.assertEquals(pullFilter1, collectionConfig.pullFilter)
            Assert.assertArrayEquals(
                arrayOf("CNBC", "ABC"),
                collectionConfig.channels?.toTypedArray()
            )
            Assert.assertArrayEquals(
                arrayOf("doc1", "doc2"),
                collectionConfig.documentIDs?.toTypedArray()
            )
        }

        val pushFilter2 = ReplicationFilter { _, _ -> true }
        val pullFilter2 = ReplicationFilter { _, _ -> true }
        val collectionConfig2 = CollectionConfiguration(testDatabase.defaultCollection)
            .setPushFilter(pushFilter2)
            .setPullFilter(pullFilter2)
            .setChannels(listOf("Peacock", "History"))
            .setDocumentIDs(listOf("doc3"))

        replConfig1 = ReplicatorConfiguration(setOf(collectionConfig2), mockURLEndpoint)

        replConfig1.collectionConfigs.forEach { collectionConfig2 ->
            Assert.assertEquals(pushFilter2, collectionConfig2.pushFilter)
            Assert.assertEquals(pullFilter2, collectionConfig2.pullFilter)
            Assert.assertArrayEquals(
                arrayOf("Peacock", "History"),
                collectionConfig2.channels?.toTypedArray()
            )
            Assert.assertArrayEquals(arrayOf("doc3"), collectionConfig2.documentIDs?.toTypedArray())
        }
    }

    // 8.13.7 Create Collection "colA" and "colB" in the scope "scopeA".
    //
    // Create a config object with ReplicatorConfiguration(Collection<CollectionConfiguration>, Endpoint).
    //
    // Use addCollections() to add both colA and colB to the config without specifying
    // a collection config.
    //
    // Check  ReplicatorConfiguration.collections. The collections should have colA and
    // colB.
    //
    // Use getCollectionConfig() to get the collection config for colA and colB. Check
    // the returned configs of both collections. The returned configs should be
    // different instances. The conflict resolver and filters of both configs should be
    // all NULL.
    @Test
    fun testAddCollectionsWithoutCollectionConfig() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val collectConfigs = CollectionConfiguration.fromCollections(setOf(collectionA, collectionB))
        val replConfig1 = ReplicatorConfiguration(collectConfigs, mockURLEndpoint)

        replConfig1.collectionConfigs.forEach { collectConfig ->
            Assert.assertNotNull(collectConfig)
            Assert.assertNull(collectConfig.conflictResolver)
            Assert.assertNull(collectConfig.pushFilter)
            Assert.assertNull(collectConfig.pullFilter)
            Assert.assertNull(collectConfig.channels)
            Assert.assertNull(collectConfig.documentIDs)
        }

        val collectConfigsArray = replConfig1.collectionConfigs.toList()
        Assert.assertNotSame(collectConfigsArray[0], collectConfigsArray[1])
    }

    // 8.13.10a Create Collection "colA" and "colB" in the scope "scopeA".
    //
    // Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //
    // Create a CollectionConfiguration object, and set a conflictResolver and all
    // filters.
    //
    // Use addCollection() to add the colA and colB with the collection config created
    // from the previous step.
    //
    // Check  ReplicatorConfiguration.collections. The collections should have colA and
    // colB.
    //
    // Use getCollectionConfig() to get the collection config for colA and colB. Check
    // the returned configs of both collections and ensure that both configs contain
    // the values correctly.
    //
    // Use addCollection() to add colA again without specifying collection config.
    //
    // Create a new CollectionConfiguration object, and set a conflictResolver and all
    // filters.
    //
    // Use addCollection() to add colB again with the updated collection config created
    // from the previous step.
    //
    // Use getCollectionConfig() to get the collection config for colA and colB. Check
    // the returned configs of both collections and ensure that both configs contain
    // the updated values correctly.
    @Test
    fun testUpdateCollectionConfigA() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = testDatabase.createCollection("colB", "scopeA")

        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val resolver1 = localResolver
        val collectionConfigA0 = CollectionConfiguration(collectionA)
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setConflictResolver(resolver1)

        val collectionConfigB0 = CollectionConfiguration(collectionB)
            .setPushFilter(pushFilter1)
            .setPullFilter(pullFilter1)
            .setConflictResolver(resolver1)

        val replConfig1 = ReplicatorConfiguration(setOf(collectionConfigA0, collectionConfigB0), mockURLEndpoint)

        Assert.assertTrue(replConfig1.collectionConfigs.contains(collectionConfigA0))
        Assert.assertTrue(replConfig1.collectionConfigs.contains(collectionConfigB0))

        Assert.assertNotNull(collectionConfigA0)
        Assert.assertEquals(pushFilter1, collectionConfigA0.pushFilter)
        Assert.assertEquals(pullFilter1, collectionConfigA0.pullFilter)
        Assert.assertEquals(resolver1, collectionConfigA0.conflictResolver)

        Assert.assertNotNull(collectionConfigB0)
        Assert.assertEquals(pushFilter1, collectionConfigB0.pushFilter)
        Assert.assertEquals(pullFilter1, collectionConfigB0.pullFilter)
        Assert.assertEquals(resolver1, collectionConfigB0.conflictResolver)

        val pushFilter2 = ReplicationFilter { _, _ -> true }
        val pullFilter2 = ReplicationFilter { _, _ -> true }
        val resolver2 = localResolver
        val collectionConfigA1 = CollectionConfiguration(collectionA)
        val collectionConfigB1 = CollectionConfiguration(collectionB)
            .setPushFilter(pushFilter2)
            .setPullFilter(pullFilter2)
            .setConflictResolver(resolver2)

        ReplicatorConfiguration(setOf(collectionConfigA1, collectionConfigB1), mockURLEndpoint)

        Assert.assertNotNull(collectionConfigA1)
        Assert.assertNull(collectionConfigA1.pushFilter)
        Assert.assertNull(collectionConfigA1.pullFilter)
        Assert.assertNull(collectionConfigA1.conflictResolver)

        Assert.assertNotNull(collectionConfigB1)
        Assert.assertEquals(pushFilter2, collectionConfigB1.pushFilter)
        Assert.assertEquals(pullFilter2, collectionConfigB1.pullFilter)
        Assert.assertEquals(resolver2, collectionConfigB1.conflictResolver)
    }

    // 8.13.12b Create collection "colA" in the scope "scopeA" using database instance A.
    //
    // Create collection "colB" in the scope "scopeA" using database instance B.
    //
    // Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //
    // Use addCollections() to add both colA and colB. An invalid argument exception
    // should be thrown as the collections are from different database instances.
    //
    // Use addCollection() to add colA. Ensure that the colA has been added correctly.
    //
    // Use addCollection() to add colB. An invalid argument exception should be thrown
    // as the collections are from different database instances.
    @Test
    fun testAddCollectionsFromDifferentDatabaseInstancesB() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        val collectionB = targetDatabase.createCollection("colB", "scopeA")

        val collectConfig = CollectionConfiguration.fromCollections(setOf(collectionA, collectionB))

        Assert.assertThrows(IllegalArgumentException::class.java) {
            ReplicatorConfiguration(
                collectConfig,
                mockURLEndpoint
            )
        }
    }

    // 8.13.13a Create collection "colA" in the scope "scopeA" using database instance A.
    //
    // Create collection "colB" in the scope "scopeA" using database instance B.
    //
    // Delete collection colB.
    //
    // Create a config object with ReplicatorConfiguration(Collection<CollectionConfiguration>, Endpoint).
    //
    // Check Collection Config size and whether config contains config of collection A.
    @Test
    fun testAddDeletedCollectionsB() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        targetDatabase.createCollection("colB", "scopeA")

        testDatabase.deleteCollection("colB", "scopeA")

        val collectConfigA = CollectionConfiguration(collectionA)
        val replConfig1 = ReplicatorConfiguration(setOf(collectConfigA), mockURLEndpoint)

        val collectionConfigs = replConfig1.collectionConfigs
        Assert.assertEquals(1, collectionConfigs.size)
        Assert.assertTrue(collectionConfigs.contains(collectConfigA))
    }


    // 8.13.13c Create collection "colA" in the scope "scopeA" using database instance A.
    //
    // Create collection "colB" in the scope "scopeA" using database instance B.
    //
    // Delete collection colB.
    //
    // Create a config object with ReplicatorConfiguration(Collection<CollectionConfiguration>, Endpoint).
    //
    // Create replicator Configuration with deleted colB
    @Test
    fun testAddDeletedCollectionsC() {
        testDatabase.createCollection("colA", "scopeA")
        val collectionB = targetDatabase.createCollection("colB", "scopeA")

        targetDatabase.deleteCollection("colB", "scopeA")

        Assert.assertThrows(IllegalArgumentException::class.java) {
            ReplicatorConfiguration(
                CollectionConfiguration.fromCollections(
                    setOf(
                        testDatabase.getCollection(
                            "colB",
                            "scopeA"
                        )
                    )
                ), mockURLEndpoint
            )
        }
    }

    // CBL-3736
    // Attempting to configure a replicator with no collection
    // should throw an illegal argument exception.
    @Test
    fun testCreateReplicatorWithNoCollections() {
        Assert.assertThrows(IllegalArgumentException::class.java) { Replicator(ReplicatorConfiguration(setOf(), mockURLEndpoint)) }
    }

    // CBL-3736
    // After the last collection from a scope is deleted,
    // an attempt to get the scope should return null
    @Test
    fun testUseScopeAfterScopeDeleted() {
        Assert.assertNotNull(testDatabase.createCollection("colA", "scopeA"))

        testDatabase.deleteCollection("colA", "scopeA")

        Assert.assertNull(testDatabase.getScope("scopeA"))
    }

    // CBL-3736
    // An attempt to get a collection from a closed database
    // should throw a CouchbaseLiteException
    @Test
    fun testUseScopeAfterDBClosed() {
        Assert.assertNotNull(testDatabase.createCollection("colA", "scopeA"))

        testDatabase.close()

        assertThrowsCBLException(CBLError.Domain.CBLITE, CBLError.Code.NOT_OPEN) {
            testDatabase.getCollection("colA", "scopeA")
        }
    }
}
