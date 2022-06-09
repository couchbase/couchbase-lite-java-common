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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun testCreateCollectionInDefaultScope() {
        baseTestDb.createCollection("chintz")
        val scope = baseTestDb.defaultScope
        assertEquals(2, scope.collectionCount)
        assertNotNull(scope.getCollection("chintz"))
    }

    @Test
    fun testCreateCollectionInNamedScope() {
        baseTestDb.createCollection("chintz", "micro")

        var scope: Scope? = baseTestDb.defaultScope
        assertEquals(1, scope?.collectionCount)
        assertNull(scope?.getCollection("chintz"))

        scope = baseTestDb.getScope("micro")
        assertEquals(1, scope?.collectionCount)
        assertNotNull(scope?.getCollection("chintz"))
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

    @Ignore("CBL-3257: getScopeNames does not return default scope when it is empty")
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
}