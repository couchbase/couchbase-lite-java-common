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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DbCollectionsTest : BaseCollectionTest() {
    private val invalidChars = charArrayOf(
        '!', '@', '#', '$', '^', '&', '*', '(', ')', '+', '.', '<', '>', '?', '[', ']', '{', '}', '=', '“', '‘', '|', '\\', '/', '`', '~'
    )

    @Test
    fun testGetDefaultScope() {
        val scope = baseTestDb.defaultScope
        assertNotNull(scope)
        assertTrue(baseTestDb.scopes.contains(scope))
        assertEquals(Scope.DEFAULT_NAME, scope.name)
        assertEquals(1, scope.collectionCount)
        assertNotNull(scope.getCollection(Collection.DEFAULT_NAME))
    }

    @Test
    fun testGetDefaultCollection() {
        val col = baseTestDb.defaultCollection
        assertNotNull(col)
        assertEquals(Collection.DEFAULT_NAME, col!!.name)
        assertEquals(col, baseTestDb.getCollection(Collection.DEFAULT_NAME))
        assertTrue(baseTestDb.collections.contains(col))
        assertNotNull(col.scope)
        assertEquals(Scope.DEFAULT_NAME, col.scope.name)
        assertEquals(0, col.count)
    }

    // Test that collections can be created and accessed from the default scope
    @Test
    fun testCreateCollectionInDefaultScope() {
        //name with valid characters
        baseTestDb.createCollection("chintz")
        // collection names should be case sensitive
        baseTestDb.createCollection("Chintz")
        baseTestDb.createCollection("6hintz")
        baseTestDb.createCollection("-Ch1ntz")

        val scope = baseTestDb.defaultScope
        assertEquals(5, scope.collectionCount)
        assertNotNull(scope.getCollection("chintz"))
        assertNotNull(scope.getCollection("Chintz"))
        assertNotNull(scope.getCollection("6hintz"))
        assertNotNull(scope.getCollection("-Ch1ntz"))

        // collections exists when calling from database
        assertNotNull(baseTestDb.getCollection("chintz"))
        assertNotNull(baseTestDb.getCollection("Chintz"))
        assertNotNull(baseTestDb.getCollection("6hintz"))
        assertNotNull(baseTestDb.getCollection("-Ch1ntz"))
    }


    @Test(expected = CouchbaseLiteException::class)
    fun testCollectionNameStartsWithIllegalChars1() {
        baseTestDb.createCollection("_notvalid")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testCollectionNameStartsWithIllegalChars2() {
        baseTestDb.createCollection("%notvalid")
    }

    @Test
    fun testCollectionNameContainingIllegalChars() {
        for (c in invalidChars) {
            val colName = "notval" + c + "d"
            try {
                baseTestDb.createCollection(colName)
                fail("Expect CBL Exception for collection : $colName")
            } catch (ignore: CouchbaseLiteException) {
            }
        }
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testCreateCollectionNameLength252() {
        val name =
            "fhndlbjgjyggvvnreutzuzyzszqiqmbqbegudyvdzvenpybjuayxssmipnpjysyfldhjmyyjmzxhegjjqwfrgzkwbiepqbvwbijcifvqamanpmiqydqpcqgubyputmrjiulrjxbayzpxqbxsaszkdxdobhreeqorlmfeoukbspfocymiucffsvioqmvqpqnpvdhpbnenkppfogruvdrrhiaalcfijifapsjqpjuwmlkkrxohvgxoqumkktipsqpsgrqidtcdeadnanxlhbivyvqkdxprsjybvuhjolkpaswlkgtiz"
        baseTestDb.createCollection(name)
    }

    @Test
    fun testCreateCollectionInNamedScope() {
        baseTestDb.createCollection("chintz", "micro")
        baseTestDb.createCollection("chintz", "3icro")
        baseTestDb.createCollection("chintz", "-micro")

        var scope: Scope? = baseTestDb.defaultScope
        assertEquals(1, scope?.collectionCount)

        // get non-existing collection returns null
        assertNull(scope?.getCollection("chintz"))
        assertNull(baseTestDb.getCollection("chintz"))

        scope = baseTestDb.getScope("micro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))

        scope = baseTestDb.getScope("3icro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))


        scope = baseTestDb.getScope("-micro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))


        // collections exists when calling from database
        assertNotNull(baseTestDb.getCollection("chintz", "micro"))
        assertNotNull(baseTestDb.getCollection("chintz", "3icro"))
        assertNotNull(baseTestDb.getCollection("chintz", "-micro"))
    }

    //Test that creating an existing collection returns an existing collection
    @Test
    fun testCreateAnExistingCollection() {
        val docId = "doc1"

        //save doc in testCollection
        createSingleDocInTestCollectionWithId(docId)

        val col = baseTestDb.createCollection(testCollection.name, testCollection.scope.name)

        // the copy collection has the same content as testCollection
        assertEquals(col, testCollection)
        assertNotNull(col.getDocument(docId))

        // updating the copy col also update the original one
        col.save(MutableDocument("doc2"))
        assertNotNull(testCollection.getDocument("doc2"))
    }


    @Test(expected = CouchbaseLiteException::class)
    fun testScopeNameStartsWithIllegalChar1() {
        baseTestDb.createCollection("chintz", "_micro")
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testScopeNameStartsWithIllegalChar2() {
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
        val scopes = baseTestDb.scopes
        assertEquals(2, scopes.size)

        var scope = scopes.first { it.name == Scope.DEFAULT_NAME }
        assertNotNull(scope.getCollection(Scope.DEFAULT_NAME))

        scope = scopes.first { it.name == testCollection.scope.name }
        assertNotNull(scope.getCollection(testCollection.name))
    }

    @Test
    fun testDeleteCollectionFromNamedScope() {
        var scopes = baseTestDb.scopes
        assertEquals(2, scopes.size)

        baseTestDb.deleteCollection(testCollection.name, testCollection.scope.name)

        scopes = baseTestDb.scopes
        assertEquals(1, scopes.size)

        val recreateCol = baseTestDb.createCollection(testCollection.name, testCollection.scope.name)
        assertNotNull(recreateCol)
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testDeleteDefaultCollection() {
        var scopes = baseTestDb.scopes

        // scopes should have a default scope and a non default test scope created in BaseCollection
        assertEquals(2, scopes.size)

        var scope = baseTestDb.defaultScope
        assertEquals(1, scope.collectionCount)

        baseTestDb.deleteCollection(Collection.DEFAULT_NAME)

        // The default scope should not go away when it is empty
        scopes = baseTestDb.scopes
        assertEquals(2, scopes.size)
        assertNotNull(baseTestDb.defaultScope)

        scope = baseTestDb.defaultScope
        assertEquals(0, scope.collectionCount)

        // default collection cannot be recreated
        baseTestDb.createCollection(Collection.DEFAULT_NAME)

    }

    // When deleting all collections in non-default scope, the scope will be deleted
    @Test
    fun testDeleteAllCollectionsInNamedScope() {
        baseTestDb.deleteCollection(testCollection.name, testCollection.scope.name)
        assertNull(baseTestDb.getScope(testCollection.name))
        assertEquals(setOf(baseTestDb.defaultScope), baseTestDb.scopes)
    }

    @Test
    fun testScopeNameContainingIllegalChars() {
        for (c in invalidChars) {
            val scopeName = "notval" + c + "d"
            try {
                baseTestDb.createCollection("col", scopeName)
                fail("Expect CBL Exception for scope : $scopeName")
            } catch (e: CouchbaseLiteException) {
            }
        }
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testCreateScopeNameLength252() {
        val name =
            "fhndlbjgjyggvvnreutzuzyzszqiqmbqbegudyvdzvenpybjuayxssmipnpjysyfldhjmyyjmzxhegjjqwfrgzkwbiepqbvwbijcifvqamanpmiqydqpcqgubyputmrjiulrjxbayzpxqbxsaszkdxdobhreeqorlmfeoukbspfocymiucffsvioqmvqpqnpvdhpbnenkppfogruvdrrhiaalcfijifapsjqpjuwmlkkrxohvgxoqumkktipsqpsgrqidtcdeadnanxlhbivyvqkdxprsjybvuhjolkpaswlkgtiz"
        baseTestDb.createCollection("col", name)
    }

    /**
     * Collections and Cross Database instance
     */

    @VerySlowTest
    @Test
    fun testCreateThenGetCollectionFromDifferentDatabaseInstance() {
        val otherDb = duplicateDb(baseTestDb)
        try {
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
        } finally {
            eraseDb(otherDb)
        }
    }

    @Test
    fun testCreateCollectionFromDifferentDatabase() {
        //open a new db
        val newDB = openDatabase()
        try {
            assertNull(newDB.getCollection(testCollection.name, testCollection.scope.name))
        } finally {
            // delete otherDb
            eraseDb(newDB)
        }
    }

    /* Use APIs on Collection when collection is deleted */
    @Test
    fun testGetScopeFromDeletedCollection() {
        val scopeName = testCollection.scope.name
        baseTestDb.deleteCollection(testCollection.name, scopeName)
        assertEquals(scopeName, testCollection.scope.name)
    }

    @Test
    fun testGetColNameFromDeletedCollection() {
        val collectionName = testCollection.name
        baseTestDb.deleteCollection(collectionName, testCollection.scope.name)
        assertEquals(collectionName, testCollection.name)
    }

    // Test get scope from a collection that is deleted from a different database instance
    @Test
    fun testGetScopeAndNameFromCollectionFromDifferentDBInstance() {
        val collectionName = testCollection.name
        val otherDb = duplicateDb(baseTestDb)
        otherDb.use {
            val collection = otherDb.getCollection(collectionName, testCollection.scope.name)
            assertNotNull(collection)

            otherDb.deleteCollection(testCollection.name, testCollection.scope.name)
            assertNull(otherDb.getCollection(testCollection.name, testCollection.scope.name))

            //get from original collection
            assertNotNull(testCollection.scope)
            assertEquals(collectionName, testCollection.name)
        }
    }

    // Test getting scope, and collection name from a collection when database is closed returns the scope and name
    @Test
    fun testGetScopeAndCollectionNameFromAClosedDatabase() {
        val collectionName = testCollection.name
        discardDb(baseTestDb)
        assertNotNull(testCollection.scope)
        assertEquals(collectionName, testCollection.name)
    }

    // Test getting scope, and collection name from a collection when database is deleted returns the scope and name
    @Test
    fun testGetScopeAndCollectionNameFromADeletedDatabase() {
        val collectionName = testCollection.name
        baseTestDb.delete()
        assertNotNull(testCollection.scope)
        assertEquals(collectionName, testCollection.name)
    }
}

