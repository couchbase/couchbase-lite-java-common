//
// Copyright (c) 2020 Couchbase. All rights reserved.
// COUCHBASE CONFIDENTIAL - part of Couchbase Lite Enterprise Edition
//
@file:Suppress("DEPRECATION")

package com.couchbase.lite

import org.junit.Assert
import org.junit.Test
import java.net.URI


// The suite of tests that verifies behavior
// with a deleted default collection are in
// cbl-java-common @ a2de0d43d09ce64fd3a1301dc35
class DeprecatedConfigFactoryTest : BaseDbTest() {
    private val testEndpoint = URLEndpoint(URI("ws://foo.couchbase.com/db"))


    // Create with db and endpoint should succeed
    @Test
    fun testReplicatorConfigWithGoodArgs() {
        val collectConfig = CollectionConfiguration.fromCollections(setOf(testCollection))
        val config = ReplicatorConfigurationFactory.newConfig(
            testEndpoint,
            collectConfig
        )
        config.collectionConfigs.map { Assert.assertEquals(testCollection, it.collection) }
        Assert.assertEquals(testEndpoint, config.target)
    }

    // Create should copy source
    @Test
    fun testReplicatorConfigCopy() {
        val collectConfig = CollectionConfiguration.fromCollections(setOf(testCollection))
        val config1 = ReplicatorConfigurationFactory.newConfig(
            testEndpoint,
            collectConfig,
            type = ReplicatorType.PULL
        )
        val config2 = ReplicatorConfiguration(config1)
        Assert.assertNotSame(config1, config2)
        Assert.assertEquals(config1.collectionConfigs, config2.collectionConfigs)
        Assert.assertEquals(config1.target, config2.target)
        Assert.assertEquals(config1.type, config2.type)
    }

    // Create should replace source
    @Test
    fun testReplicatorConfigReplace() {
        val collectConfig = CollectionConfiguration.fromCollections(setOf(testCollection))
        val config1 = ReplicatorConfigurationFactory.newConfig(
            testEndpoint,
            collectConfig,
            type = ReplicatorType.PULL
        )
        val config2 = config1.newConfig(testEndpoint).setType(ReplicatorType.PUSH)
        Assert.assertNotSame(config1, config2)
        Assert.assertEquals(
            config1.collectionConfigs.map { it.collection?.database },
            config2.collectionConfigs.map { it.collection?.database })
        Assert.assertEquals(config1.target, config2.target)
        Assert.assertEquals(ReplicatorType.PUSH, config2.type)
    }

    // Create from a source explicitly specifying a default collection
    @Test
    fun testReplicatorConfigFromCollectionWithDefault() {
        val collectConfig =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        val config1 = ReplicatorConfigurationFactory
            .newConfig(testEndpoint, collectConfig)
        val config2 = config1.newConfig(testEndpoint)
        Assert.assertNotSame(config1, config2)
        Assert.assertEquals(config1.collectionConfigs, config2.collectionConfigs)
        config2.collectionConfigs.map { it ->
            Assert.assertEquals(
                testCollection.database.defaultCollection,
                it.collection
            )
        }
    }

    // Create from a source with default collection, explicitly specifying a non-default collection
    @Test
    fun testReplicatorConfigFromCollectionWithDefaultAndOther() {
        val collectConfig =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        val config1 = ReplicatorConfigurationFactory
            .newConfig(testEndpoint, collectConfig)
        val filter = ReplicationFilter { _, _ -> true }

        // Information gets lost here (the configuration of testCollection): should be a log message
        collectConfig.map { it.pushFilter = filter }
        val config2 = config1.newConfig(testEndpoint, collectConfig)

        Assert.assertNotSame(config1, config2)
        val db1 = config1.collectionConfigs.map { it.collection?.database }.first()
        val db2 = config2.collectionConfigs.map { it.collection?.database }.first()
        Assert.assertEquals(db1, db2)

        val defaultCollection = db1?.defaultCollection

        Assert.assertEquals(
            setOf(defaultCollection),
            config2.collectionConfigs.map { it.collection }.toSet()
        )
        Assert.assertEquals(
            filter,
            config2.collectionConfigs.first { it.collection == defaultCollection }.pushFilter
        )
    }

    // Create with one of the parameters that has migrated to the collection configuration
    @Test
    fun testReplicatorFromCollectionWithLegacyParameter() {
        val collectConfig =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        collectConfig.map { it.channels = listOf("boop") }
        val config = ReplicatorConfigurationFactory.newConfig(testEndpoint, collectConfig)
        val collections: Set<Collection> = config.collectionConfigs.map { it.collection!! }.toSet()
        Assert.assertEquals(testDatabase.collections, collections)
        Assert.assertEquals(testEndpoint, config.target)
        Assert.assertEquals(
            listOf("boop"),
            config.collectionConfigs.first { it.collection == testDatabase.defaultCollection }.channels
        )
    }

    // Create a collection style config from one built with the legacy call
    @Test
    fun testReplicatorConfigFromLegacy() {
        val collectConfig =
            CollectionConfiguration.fromCollections(setOf(testDatabase.defaultCollection))
        collectConfig.map { it.channels = listOf("boop") }
        val config1 = ReplicatorConfigurationFactory.newConfig(testEndpoint, collectConfig)
        val config2 = config1.setContinuous(true)
        val collections: Set<Collection> = config2.collectionConfigs.map { it.collection!! }.toSet()
        Assert.assertEquals(testDatabase.collections, collections)
        Assert.assertEquals(testEndpoint, config2.target)
        val colls = config2.collectionConfigs.map { it.collection }
        Assert.assertEquals(1, colls.size)
        val defaultCollection = testDatabase.defaultCollection
        Assert.assertTrue(colls.contains(defaultCollection))
        Assert.assertEquals(
            listOf("boop"),
            config2.collectionConfigs.first { it.collection == testDatabase.defaultCollection }.channels
        )
    }
}
