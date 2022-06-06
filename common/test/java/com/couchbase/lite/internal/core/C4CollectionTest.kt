//
// Copyright (c) 2020 Couchbase, Inc.
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
package com.couchbase.lite.internal.core

import com.couchbase.lite.LiteCoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class C4CollectionTest : C4BaseTest() {
    @Test
    fun setUpC4CollectionTest() {
        assertNotNull(C4Collection.getDefault(c4Database))
    }

    @Test
    fun testCreateCollection() {
        assertNotNull(C4Collection.create(c4Database, "Tele", "Chintz"))
    }

    @Test
    fun testGetCollection() {
        assertNotNull(C4Collection.create(c4Database, "Tele", "Chintz"))
        assertNotNull(C4Collection.get(c4Database, "Tele", "Chintz"))
    }

    @Test
    fun testDeleteCollection() {
        val collection = C4Collection.create(c4Database, "Micro", "PezDispensers")
        assertTrue(collection.isValid)
        c4Database.deleteCollection("Micro", "PezDispensers")
        assertTrue(collection.isValid)
    }

    @Test(expected = LiteCoreException::class)
    fun testGetNonExistantDoc() {
        val collection = C4Collection.create(c4Database, "Kaleido", "BeanieBabies")
        assertEquals(0, collection.documentCount)
        collection.getDocument("nexistpas")
    }

    @Test(expected = LiteCoreException::class)
    fun testCreateDocWithNullBody() {
        val collection = C4Collection.create(c4Database, "Tachisto", "Stamps")
        assertEquals(0, collection.documentCount)
        assertNotNull(collection.createDocument("yep", null, 0))
        assertEquals(1, collection.documentCount)
        assertNotNull(collection.getDocument("yep"))
    }
}

