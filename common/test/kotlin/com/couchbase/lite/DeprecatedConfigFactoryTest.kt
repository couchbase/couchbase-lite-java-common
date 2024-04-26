//
// Copyright (c) 2020 Couchbase. All rights reserved.
// COUCHBASE CONFIDENTIAL - part of Couchbase Lite Enterprise Edition
//
@file:Suppress("DEPRECATION")

package com.couchbase.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI


// The suite of tests that verifies behavior
// with a deleted default collection are in
// cbl-java-common @ a2de0d43d09ce64fd3a1301dc35
class DeprecatedConfigFactoryTest : BaseDbTest() {
    private val testEndpoint = URLEndpoint(URI("ws://foo.couchbase.com/db"))

    ///// Test ReplicatorConfiguration Factory

    @Test
    fun testReplicatorConfigNoArgs() {
        assertThrows(IllegalArgumentException::class.java) { ReplicatorConfigurationFactory.create() }
    }

    // Create on factory with no db should fail
    @Test
    fun testReplicatorConfigNoDb() {
        assertThrows(IllegalArgumentException::class.java) {
            ReplicatorConfigurationFactory.create(target = testEndpoint, type = ReplicatorType.PULL)
        }
    }

    // Create on factory with no target should fail
    @Test
    fun testReplicatorConfigNoProtocol() {
        assertThrows(IllegalArgumentException::class.java) {
            ReplicatorConfigurationFactory.create(testDatabase, type = ReplicatorType.PULL)
        }
    }

    // Create with db and endpoint should succeed
    @Test
    fun testReplicatorConfigWithGoodArgs() {
        val config = ReplicatorConfigurationFactory.create(testDatabase, testEndpoint)
        assertEquals(testDatabase, config.database)
        assertEquals(testEndpoint, config.target)
    }

    // Create should copy source
    @Test
    fun testReplicatorConfigCopy() {
        val config1 = ReplicatorConfigurationFactory.create(testDatabase, testEndpoint, type = ReplicatorType.PULL)
        val config2 = config1.create()
        assertNotSame(config1, config2)
        assertEquals(config1.database, config2.database)
        assertEquals(config1.target, config2.target)
        assertEquals(config1.type, config2.type)
    }

    // Create should replace source
    @Test
    fun testReplicatorConfigReplace() {
        val config1 = ReplicatorConfigurationFactory.create(testDatabase, testEndpoint, type = ReplicatorType.PULL)
        val config2 = config1.create(type = ReplicatorType.PUSH)
        assertNotSame(config1, config2)
        assertEquals(config1.database, config2.database)
        assertEquals(config1.target, config2.target)
        assertEquals(ReplicatorType.PUSH, config2.type)
    }

    // Create from a source explicitly specifying a default collection
    @Test
    fun testReplicatorConfigFromCollectionWithDefault() {
        val config1 = ReplicatorConfigurationFactory
            .newConfig(testEndpoint, mapOf(listOf(testDatabase.defaultCollection) to CollectionConfiguration()))
        val config2 = config1.create()
        assertNotSame(config1, config2)
        assertEquals(config1.database, config2.database)
        assertEquals(setOf(testCollection.database.defaultCollection), config2.collections)
    }

    // Create from a source with default collection, explicitly specifying a non-default collection
    @Test
    fun testReplicatorConfigFromCollectionWithDefaultAndOther() {
        val config1 = ReplicatorConfigurationFactory
            .newConfig(testEndpoint, mapOf(listOf(testCollection) to CollectionConfiguration()))
        val filter = ReplicationFilter { _, _ -> true }

        // Information gets lost here (the configuration of testCollection): should be a log message
        val config2 = config1.create(pushFilter = filter)

        assertNotSame(config1, config2)
        assertEquals(config1.database, config2.database)

        val db = config1.database
        val defaultCollection = db.defaultCollection

        assertEquals(setOf(defaultCollection), config2.collections)
        assertEquals(filter, config2.getCollectionConfiguration(defaultCollection)?.pushFilter)
    }

    // Create with one of the parameters that has migrated to the collection configuration
    @Test
    fun testReplicatorFromCollectionWithLegacyParameter() {
        val config = ReplicatorConfigurationFactory.create(testDatabase, testEndpoint, channels = listOf("boop"))
        assertEquals(testDatabase, config.database)
        assertEquals(testEndpoint, config.target)
        assertEquals(listOf("boop"), config.getCollectionConfiguration(testDatabase.defaultCollection)!!.channels)
    }

    // Create a collection style config from one built with the legacy call
    @Test
    fun testReplicatorConfigFromLegacy() {
        val config1 = ReplicatorConfigurationFactory.create(testDatabase, testEndpoint, channels = listOf("boop"))
        val config2 = config1.newConfig(continuous = true)
        assertEquals(testDatabase, config2.database)
        assertEquals(testEndpoint, config2.target)
        val colls = config2.collections
        assertEquals(1, colls.size)
        val defaultCollection = testDatabase.defaultCollection
        assertTrue(colls.contains(defaultCollection))
        assertEquals(listOf("boop"), config2.getCollectionConfiguration(defaultCollection)!!.channels)
    }
}
