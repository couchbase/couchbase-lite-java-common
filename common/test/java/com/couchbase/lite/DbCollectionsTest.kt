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

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test


class DbCollectionsTest : BaseCollectionTest() {
    @Test
    fun testGetDefaultScope() {
        val scope = baseTestDb.defaultScope
        assertEquals(Scope.DEFAULT_NAME, scope.name)
        assertEquals(1, scope.collectionCount)
        assertNotNull(scope.getCollection(Collection.DEFAULT_NAME))
    }

    @Ignore("CouchbaseLiteException: duplicate column when creating collection Chintz")
    @Test
    //create valid collections
    fun testCreateCollectionInDefaultScope() {
        //name with valid characters
        baseTestDb.createCollection("chintz")
        baseTestDb.createCollection("Chintz") //collection is case sensitive, this collection should be created independently from chintz
        baseTestDb.createCollection("6hintz")
        baseTestDb.createCollection("-Ch1ntz")

        val scope = baseTestDb.defaultScope
        assertEquals(5, scope.collectionCount)
        assertNotNull(scope.getCollection("chintz"))
        assertNotNull(scope.getCollection("Chintz"))
        assertNotNull(scope.getCollection("6hintz"))
        assertNotNull(scope.getCollection("-Ch1ntz"))
    }


    @Test(expected = CouchbaseLiteException::class)
    fun testCollectionNameStartWithIllegalChars1() {
        baseTestDb.createCollection("_notvalid")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun tesCollectionNameStartWithIllegalChars2() {
        baseTestDb.createCollection("%notvalid")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testCollectionNameContainingIllegalChars() {
        baseTestDb.createCollection("notval!d")
    }

    @Test
    fun testCreateCollectionInNamedScope() {
        baseTestDb.createCollection("chintz", "micro")
        baseTestDb.createCollection("chintz", "3icro")
        baseTestDb.createCollection("chintz", "-micro")

        var scope: Scope? = baseTestDb.defaultScope
        assertEquals(1, scope?.collectionCount)
        assertNull(scope?.getCollection("chintz"))

        scope = baseTestDb.getScope("micro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))

        scope = baseTestDb.getScope("3icro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))


        scope = baseTestDb.getScope("-micro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))

    }

    @Test(expected = CouchbaseLiteException::class)
    fun testScopeNameWithIllegalChar1() {
        baseTestDb.createCollection("chintz", "_micro")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testScopeNameWithIllegalChar2() {
        baseTestDb.createCollection("chintz", "%micro")
    }

    @Test
    fun testScopeNameCaseSensitive() {
        baseTestDb.createCollection("coll1", "scope1")
        val scope1 = baseTestDb.getScope("scope1")

        baseTestDb.createCollection("coll2", "Scope1")
        val scope2 = baseTestDb.getScope("Scope1")

        assertNotNull(scope1)
        assertNotNull(scope2)
        assertEquals(scope1, baseTestDb.getScope("scope1"))
        assertNotSame(scope1, scope2)
    }

    @Test
    fun testGetScopes() {
        baseTestDb.createCollection("pezDispenser", "tele")

        val scopes = baseTestDb.scopes
        assertEquals(2, scopes.size)

        var scope = scopes.first { it.name == Scope.DEFAULT_NAME }
        assertNotNull(scope.getCollection(Scope.DEFAULT_NAME))

        scope = scopes.first { it.name == "tele" }
        assertNotNull(scope.getCollection("pezDispenser"))
    }

    @Test
    fun testDeleteCollectionFromNamedScope() {
        baseTestDb.createCollection("pezDispenser", "tele")

        var scopes = baseTestDb.scopes
        assertEquals(2, scopes.size)

        baseTestDb.deleteCollection("pezDispenser", "tele")

        scopes = baseTestDb.scopes
        assertEquals(1, scopes.size)
    }


// !!! TESTS BELOW NEED TO BE MOVED TO CollectionTest

    @Test
    fun testDeleteDefaultCollection() {
        var scopes = baseTestDb.scopes
        assertEquals(1, scopes.size)

        var scope = baseTestDb.defaultScope
        assertEquals(1, scope.collectionCount)

        baseTestDb.deleteCollection(Collection.DEFAULT_NAME)

        // The default collection should not go away when it is empty
        scopes = baseTestDb.scopes
        assertEquals(1, scopes.size)

        scope = baseTestDb.defaultScope
        assertEquals(0, scope.collectionCount)
    }

    /**
     * Collections and Cross Database instance
     */

    @Test
    fun testCreateThenGetCollectionFromDifferentDatabaseInstance() {
        val otherDb = duplicateDb(baseTestDb)
        baseTestDb.createCollection("testColl")
        val collection = otherDb.getCollection("testColl")
        assertNotNull(collection)

        //delete coll from a db
        baseTestDb.deleteCollection("testColl")
        assertNull(baseTestDb.getCollection("testColl"))
        assertNull(otherDb.getCollection("testColl"))

        //recreate collection
        baseTestDb.createCollection("testColl")
        val collectionRecreated = otherDb.getCollection("testColl")
        assertNotSame(collectionRecreated, collection)

    }

    @Test
    fun testCreateCollectionFromDifferentDatabase() {
        //open a new db
        val newDB = openDatabase()
        try {
            assertNull(newDB.getCollection(testColName,testScopeName))
        } finally {
            // delete otherDb
            deleteDb(newDB);
        }
    }
}

