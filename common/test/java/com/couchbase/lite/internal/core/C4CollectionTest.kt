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

import org.junit.Assert
import org.junit.Test


class C4CollectionTest : C4BaseTest() {
    @Test
    fun setUpC4CollectionTest() {
        Assert.assertNotNull(C4Collection.getDefault(c4Database))
        // Would like to be able to verify that this is the default collection...
    }

    @Test
    fun testCreateCollection() {
        C4Collection.create(c4Database, "Tele", "Chintz").use { coll -> Assert.assertNotNull(coll) }
    }

    @Test
    fun testGetCollection() {
        C4Collection.create(c4Database, "Tele", "Chintz").use { coll -> Assert.assertNotNull(coll) }
        C4Collection.get(c4Database, "Tele", "Chintz").use { coll -> Assert.assertNotNull(coll) }
    }

    @Test
    fun testCollectionIsValidAfterClose() {
        C4Collection.create(c4Database, "Micro", "PezDispensers").use { coll ->
            Assert.assertTrue(coll.isValid)
            c4Database.closeDb()
            c4Database = null
            Assert.assertFalse(coll.isValid)
        }
    }

    @Test
    fun testGetNonExistentDoc() {
        C4Collection.create(c4Database, "Kaleido", "BeanieBabies").use { coll ->
            Assert.assertEquals(0, coll.documentCount)
            Assert.assertNull(coll.getDocument("nexistpas"))
        }
    }

    @Test
    fun testCreateDocWithNullBody() {
        C4Collection.create(c4Database, "Tachisto", "Stamps").use { coll ->
            Assert.assertEquals(0, coll.documentCount)

            assertThrowsLiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.NOT_IN_TRANSACTION
            ) {
                coll.createDocument("yep", null, 0)
            }

            Assert.assertNull(coll.getDocument("yep"))
            Assert.assertEquals(0, coll.documentCount)
        }
    }
}
