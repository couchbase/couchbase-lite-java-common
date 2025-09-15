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

    // 8.13.2 Create a config object with ReplicatorConfiguration(java.util.Collection&lt;CollectionConfiguration&gt;, Endpoint).
    //
    // Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //
    // Check CollectionConfiguration.conflictResolver. The returned conflict resolver
    // should be the same as ReplicatorConfiguration.conflictResolver.
    @Test
    fun testCreateWithCollectionAndConflictResolver() {
        val resolver = localResolver
        val collectConfigs = ReplicatorConfiguration(
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection)),
            mockURLEndpoint
        ).collections

        collectConfigs.forEach { it.conflictResolver = resolver }

        collectConfigs.forEach { it ->
            Assert.assertNotNull(it)
            Assert.assertEquals(resolver, it.conflictResolver)
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
    fun testCreateWithCollectionAndReplicationFilters() {
        val pushFilter1 = ReplicationFilter { _, _ -> true }
        val pullFilter1 = ReplicationFilter { _, _ -> true }
        val collectConfigs =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        val replConfig1 = ReplicatorConfiguration(collectConfigs, mockURLEndpoint)
        replConfig1.collections.forEach { collecConfig ->
            collecConfig
                .setPushFilter(pushFilter1)
                .setPullFilter(pullFilter1)
                .setChannels(listOf("CNBC", "ABC"))
                .setDocumentIDs(listOf("doc1", "doc2"))
        }

        replConfig1.collections.forEach { collecConfig ->
            Assert.assertEquals(pushFilter1, collecConfig.pushFilter)
            Assert.assertEquals(pullFilter1, collecConfig.pullFilter)
            Assert.assertArrayEquals(arrayOf("CNBC", "ABC"), collecConfig.channels?.toTypedArray())
            Assert.assertArrayEquals(arrayOf("doc1", "doc2"), collecConfig.documentIDs?.toTypedArray())

        }
    }

    // 8.13.12b Create collection "colA" in the scope "scopeA" using database instance A.
    //
    // Create collection "colB" in the scope "scopeA" using database instance B.
    //
    // Create a config object with ReplicatorConfiguration(collection: Set<CollectionConfiguration>, endpoint: endpoint).
    // An invalid argument exception should be thrown as the collections are from different database instances.
    @Test
    fun testUseCollectionsFromDifferentDatabaseInstancesB() {
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
    fun testUseDeletedCollectionsB() {
        val collectionA = testDatabase.createCollection("colA", "scopeA")
        targetDatabase.createCollection("colB", "scopeA")

        testDatabase.deleteCollection("colB", "scopeA")

        val collectConfigA = CollectionConfiguration(collectionA)
        val replConfig1 = ReplicatorConfiguration(setOf(collectConfigA), mockURLEndpoint)

        val collectionConfigs = replConfig1.collections
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
    fun testUseDeletedCollectionsC() {
        testDatabase.createCollection("colA", "scopeA")
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
