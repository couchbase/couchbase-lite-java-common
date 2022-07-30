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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplicatorConfigurationTest : BaseReplicatorTest() {

    //     1: Create a config object with ReplicatorConfiguration.init(database, endpoint).
    //     2: Access collections property. It mush have one collection which is the default collection.
    //     6: ReplicatorConfiguration.database should be the database with which the configuration was created
    @Test
    fun TestCreateConfigWithDatabase_1() {
        val replConfig = ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
        val collections = replConfig.collections;
        assertEquals(1, collections?.size)
        assertTrue(collections?.contains(baseTestDb.defaultCollection) ?: false)
        assertEquals(baseTestDb, replConfig.database)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database, endpoint).
    //     3: Calling getCollectionConfig() with the default collection should produce a CollectionConfiguration
    //     4: CollectionConfiguration.collection should be the default collection.
    //     5: CollectionConfiguration.conflictResolver, pushFilter, pullFilter, channels, and documentIDs
    //        should be null.
    @Test
    fun TestCreateConfigWithDatabase_2() {
        val collectionConfig = ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .getCollectionConfiguration(baseTestDb.defaultCollection!!)
        assertNotNull(collectionConfig)
        assertNull(collectionConfig!!.conflictResolver)
        assertNull(collectionConfig.pushFilter)
        assertNull(collectionConfig.pullFilter)
        assertNull(collectionConfig.channels)
        assertNull(collectionConfig.documentIDs)
        assertNull(collectionConfig.documentIDs)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //     3: Calling getCollectionConfig() with the default collection should produce a CollectionConfiguration
    //     4: CollectionConfiguration.conflictResolver should be the same as ReplicatorConfiguration.conflictResolver.
    @Test
    fun TestCreateConfigWithDatabaseAndConflictResolver() {
        val resolver = ConflictResolver { conflict -> conflict.localDocument }
        val replConfig = ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setConflictResolver(resolver)
        assertEquals(resolver, replConfig?.conflictResolver)
        val collectionConfig = replConfig.getCollectionConfiguration(baseTestDb.defaultCollection!!)
        assertNotNull(collectionConfig)
        assertEquals(resolver, collectionConfig?.conflictResolver)
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set ReplicatorConfiguration.conflictResolver with a conflict resolver.
    //     3: Verify that CollectionConfiguration.conflictResolver is the same as ReplicatorConfiguration.conflictResolver.
    //     4: Update ReplicatorConfiguration.conflictResolver with a new conflict resolver.
    //     5-7: Verify that CollectionConfiguration.conflictResolver is still the same as ReplicatorConfiguration.conflictResolver..
    @Test
    fun TestUpdateConflictResolverForDefaultCollection() {
        val resolver = ConflictResolver { conflict -> conflict.localDocument }
        val replConfig = ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setConflictResolver(resolver)
        assertEquals(
            replConfig?.conflictResolver,
            replConfig.getCollectionConfiguration(baseTestDb.defaultCollection!!)?.conflictResolver
        )
        val resolver2 = ConflictResolver { conflict -> conflict.localDocument }
        assertEquals(
            replConfig?.conflictResolver,
            replConfig.getCollectionConfiguration(baseTestDb.defaultCollection!!)?.conflictResolver
        )
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs
    //     3: Call getCollectionConfig() method with the default collection.
    //       A CollectionConfiguration object should be returned and the properties in the config should
    //       be the same as the corresponding properties in ReplicatorConfiguration
    @Test
    fun TestCreateConfigWithDatabaseAndFilters() {
        val pushFilter = ReplicationFilter {doc, flags ->  true}
        val pullFilter = ReplicationFilter {doc, flags ->  true}
        val replConfig = ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setPushFilter(pushFilter)
            .setPullFilter(pullFilter)
            .setChannels(listOf("CNBC", "ABC"))
            .setDocumentIDs(listOf("doc1", "doc2"))
        assertEquals(pushFilter, replConfig!!.pushFilter)
        assertEquals(pullFilter, replConfig.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), replConfig.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), replConfig.documentIDs?.toTypedArray())

        val collectionConfig = replConfig.getCollectionConfiguration(baseTestDb.defaultCollection!!)
        assertNotNull(collectionConfig)
        assertEquals(pushFilter, collectionConfig!!.pushFilter)
        assertEquals(pullFilter, collectionConfig.pullFilter)
        assertArrayEquals(arrayOf("CNBC", "ABC"), collectionConfig.channels?.toTypedArray())
        assertArrayEquals(arrayOf("doc1", "doc2"), collectionConfig.documentIDs?.toTypedArray())
    }

    //     1: Create a config object with ReplicatorConfiguration.init(database: database, endpoint: endpoint).
    //     2: Set values to ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs.
    //     3: Call getCollectionConfig() method with the default collection. A CollectionConfiguration object should be returned. The filters in the config should be the same ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs.
    //     4: Update ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs with new values.
    //     5: Call getCollectionConfig() method with the default collection object getting from the database. A CollectionConfiguration object should be returned. The filters in the config be updated accordingly.
    //     6: Update CollectionConfiguration.pushFilter, pullFilters, channels, and documentIDs with new values. Use addCollection() method to add the default collection with the updated config.
    //     7: Check ReplicatorConfiguration.pushFilter, pullFilters, channels, and documentIDs. The filters should be updated accordingly.
    @Test
    fun TestUpdateFiltersForDefaultCollection() {

    }

    //     1: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     2: Access collections property and an empty collection list should be returned.
    //     3: Access database property and Illegal State Exception will be thrown.
    @Test
    fun TestCreateConfigWithEndpointOnly() {

    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Use addCollections() to add both colA and colB to the config without specifying a collection config.
    //     4: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     5: Use getCollectionConfig() to get the collection config for colA and colB. Check the returned configs of both collections. The returned configs should be different instances. The conflict resolver and filters of both configs should be all NULL.
    @Test
    fun TestAddCollectionsWithoutCollectionConfig() {

    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Create a CollectionConfiguration object, and set a conflictResolver and all filters.
    //     4: Use addCollections() to add both colA and colB to the config created from the previous step.
    //     5: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     6: Use getCollectionConfig() to get the collection config for colA and colB. The returned configs of both collections should be different instances. The conflict resolver and filters of both configs should be the same as what was specified when calling addCollections().
    @Test
    fun TestAddCollectionsWithCollectionConfig() {

    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Use addCollection() to add the colA without specifying a collection config.
    //     4: Create a CollectionConfiguration object, and set a conflictResolver and all filters.
    //     5: Use addCollection() to add the colB with the collection config created from the previous step.
    //     6: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     7: Use getCollectionConfig() to get the collection config for colA and colB. The returned config of the colA should contain all NULL values. The returned config of the colB should contain the values according to the config used when adding the collection.
    @Test
    fun TestAddCollection() {

    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Create a CollectionConfiguration object, and set a conflictResolver and all filters.
    //     4: Use addCollection() to add the colA and colB with the collection config created from the previous step.
    //     5: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     6: Use getCollectionConfig() to get the collection config for colA and colB. Check the returned configs of both collections and ensure that both configs contain the values correctly.
    //     7: Use addCollection() to add colA again without specifying collection config.
    //     8: Create a new CollectionConfiguration object, and set a conflictResolver and all filters.
    //     9: Use addCollection() to add colB again with the updated collection config created from the previous step.
    //     10: Use getCollectionConfig() to get the collection config for colA and colB. Check the returned configs of both collections and ensure that both configs contain the updated values correctly.
    @Test
    fun TestUpdateCollectionConfig() {

    }

    //     1: Create Collection "colA" and "colB" in the scope "scopeA".
    //     2: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     3: Create a CollectionConfiguration object, and set a conflictResolvers and all filters.
    //     4: Use addCollections() to add both colA and colB to the config with the CollectionConfiguration created from the previous step.
    //     5: Check  ReplicatorConfiguration.collections. The collections should have colA and colB.
    //     6: Use getCollectionConfig() to get the collection config for colA and colB. Check the returned config of both collections and ensure that both configs contain the values correctly.
    //     7: Remove "colB" by calling removeCollection().
    //     8: Check  ReplicatorConfiguration.collections. The collections should have only colA.
    //     9: Use getCollectionConfig() to get the collection config for colA and colB. The returned config for the colB should be NULL.
    @Test
    fun TestRemoveCollection() {

    }

    //     1: Create collection "colA" in the scope "scopeA" using database instance A.
    //     2: Create collection "colB" in the scope "scopeA" using database instance B.
    //     3: Create a config object with ReplicatorConfiguration.init(endpoint: endpoint).
    //     4: Use addCollections() to add both colA and colB. An invalid argument exception should be thrown as the collections are from different database instances.
    //     5: Use addCollection() to add colA. Ensure that the colA has been added correctly.
    //     6: Use addCollection() to add colB. An invalid argument exception should be thrown as the collections are from different database instances.
    @Test
    fun TestAddCollectionsFromDifferentDatabaseInstances() {

    }
}