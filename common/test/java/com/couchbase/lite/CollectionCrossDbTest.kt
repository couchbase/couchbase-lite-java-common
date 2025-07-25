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

import com.couchbase.lite.internal.utils.VerySlowTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class CollectionCrossDbTest : BaseTest() {
    private lateinit var dbA: Database
    private lateinit var dbB: Database

    @Before
    fun setUpBaseReplicatorTest() {
        val dbName = getUniqueName("collection_db")
        dbA = Database(dbName)
        dbB = Database(dbName)
    }

    @After
    fun tearDownBaseReplicatorTest() {
        dbB.close()
        eraseDb(dbA)
    }

    // 8.3.1 Test that creating a collection from a database instance is visible to the other database instance.
    // Create Database instance A and B.
    // Create a collection in a scope from the database instance A.
    // Ensure that the created collection is visible to the database instance B by using
    //   database.getCollection(name: "colA", scope: "scopeA") and database.getCollections(scope: "scopeA") API.
    @VerySlowTest
    @Test
    fun testCreateThenGetCollectionFromDifferentDatabaseInstance() {
        dbA.createCollection("autographs", "polari")

        Assert.assertNotNull(dbA.getCollection("autographs", "polari"))
        Assert.assertNotNull(dbB.getCollection("autographs", "polari"))
    }

    // 8.3.2 Test that deleting a collection from a database instance is visible to the other database instance.
    // Create Database instance A and B.
    // Create a collection in a scope from the database instance A.
    // Add some documents to the created collection.
    // Ensure that the created collection is visible to the database instance B by using
    //   database.getCollection(name: "colA", scope: "scopeA") and database.getCollections(scope: "scopeA") API.
    // Ensure that the collection from the database instance B has the correct number of document counts.
    // Delete the collection from the database instance A.
    // Get the document count from the collection getting from the database instance B.
    //    Ensure that the document count is 0.
    // Ensure that the collection is null when getting the collection from the database instance B again
    //    by using database.getCollection(name: "colA", scope: "scopeA").
    // Ensure that the collection is not included when getting all collections from the database instance B
    //    again by using database.getCollections(scope: "scopeA").
    @Test
    fun testDeleteThenGetCollectionFromDifferentDatabaseInstance() {
        val collectionA = dbA.createCollection("poster", "sigmoido")

        for (i in 0..9) {
            collectionA.save(MutableDocument("doc_$i"))
        }

        Assert.assertNotNull(dbB.getCollections("sigmoido").firstOrNull { c ->
            (c.name == "poster") && (c.scope.name == "sigmoido")
        })
        val collectionB = dbB.getCollection("poster", "sigmoido")
        Assert.assertNotNull(collectionB)
        Assert.assertEquals(10, collectionB!!.count)

        dbA.deleteCollection("poster", "sigmoido")

        Assert.assertEquals(0, collectionB.count)
        Assert.assertNull(dbB.getCollection("poster", "sigmoido"))
        Assert.assertNull(dbB.getCollections("sigmoido").firstOrNull { c -> c.name == "poster" })
    }

    // 8.3.3 Test that deleting a collection then recreating the collection from a database instance
    //    is visible to the other database instance.
    // Create Database instance A and B.
    // Create a collection in a scope from the database instance A.
    // Add some documents to the created collection.
    // Ensure that the created collection is visible to the database instance B by using
    //    database.getCollection(name: "colA", scope: "scopeA") and database.getCollections(scope: "scopeA") API.
    // Ensure that the collection from the database instance B has the correct number of document counts.
    // Delete the collection from the database instance A and recreate the collection using the database instance A.
    // Get the document count from the collection getting from the database instance B.
    //    Ensure that the document count is 0.
    // Ensure that the collection is not null and is different from the instance gotten before
    //    from the instanceB when getting the collection from the database instance B
    //    by using database.getCollection(name: "colA", scope: "scopeA").
    // Ensure that the collection is included when getting all collections from the database instance B
    //    by using database.getCollections(scope: "scopeA").
    @Test
    fun testDeleteAndRecreateThenGetCollectionFromDifferentDatabaseInstance() {
        val collectionA1 = dbA.createCollection("45s", "laryngo")

        for (i in 0..9) {
            collectionA1.save(MutableDocument("doc_$i"))
        }

        val collectionB1 = dbB.getCollection("45s", "laryngo")
        Assert.assertEquals(10, collectionB1?.count ?: 0)

        dbA.deleteCollection("45s", "laryngo")
        val collectionA2 = dbA.createCollection("45s", "laryngo")
        Assert.assertNotSame(collectionA1, collectionA2)

        Assert.assertEquals(0, collectionB1?.count ?: -1)
        val collectionB2 = dbB.getCollection("45s", "laryngo")
        Assert.assertNotNull(collectionB2)
        Assert.assertNotSame(collectionB1, collectionB2)
        Assert.assertNotNull(dbB.getCollections("laryngo").firstOrNull { c ->
            (c.name == "45s") && (c.scope.name == "laryngo")
        })
    }
}