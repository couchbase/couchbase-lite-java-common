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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test


class C4CollectionTest : C4BaseTest() {
    @Test
    fun setUpC4CollectionTest() {
        assertNotNull(C4Collection.getDefault(c4Database))
        // Would like to be able to verify that this is the default collection...
    }

    @Test
    fun testCreateCollection() {
        C4Collection.create(c4Database, "Tele", "Chintz").use { coll -> assertNotNull(coll) }
    }

    @Test
    fun testGetCollection() {
        C4Collection.create(c4Database, "Tele", "Chintz").use { coll -> assertNotNull(coll) }
        C4Collection.get(c4Database, "Tele", "Chintz").use { coll -> assertNotNull(coll) }
    }

    @Test
    fun testCollectionIsValidAfterClose() {
        C4Collection.create(c4Database, "Micro", "PezDispensers").use { coll ->
            assertTrue(coll.isValid)
            c4Database.closeDb()
            c4Database = null
            assertFalse(coll.isValid)
        }
    }

    @Test(expected = LiteCoreException::class)
    fun testGetNonExistentDoc() {
        C4Collection.create(c4Database, "Kaleido", "BeanieBabies").use { coll ->
            assertEquals(0, coll.documentCount)
            coll.getDocument("nexistpas")
        }
    }

    @Test(expected = LiteCoreException::class)
    fun testCreateDocWithNullBody() {
        C4Collection.create(c4Database, "Tachisto", "Stamps").use { coll ->
            assertEquals(0, coll.documentCount)
            assertNotNull(coll.createDocument("yep", null, 0))
            assertEquals(1, coll.documentCount)
            assertNotNull(coll.getDocument("yep"))
        }
    }
}
