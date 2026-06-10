//
// Copyright (c) 2026 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http:// www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.couchbase.lite

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.junit.Assert
import org.junit.Test


/** Tests serialization-based Collection accessors implemented in CollectionExtensions.kt. */
class CollectionSerializationTest : BaseDbTest() {
    //---------------------------------------------
    //  Model-based (serialization) API
    //---------------------------------------------

    @Test
    fun saveNewDocInCollectionFromModel() {
        val id = getUniqueName("test_doc")

        // Create a new model and save it:
        val model = TestModel("Nigel", 12)
        testCollection.save(model, id)
        Assert.assertEquals(testCollection, model.documentMeta?.collection)
        Assert.assertEquals(id, model.documentMeta?.id)

        // Read it back:
        Assert.assertEquals(1, testCollection.count)
        val gotModel = testCollection.getDocumentAs<TestModel>(id)!!
        Assert.assertEquals(model, gotModel)

        // Modify and save again:
        gotModel.favorites = listOf("XTC", "Elvis Costello")
        Assert.assertTrue(testCollection.save(gotModel))
        Assert.assertNotEquals(model.documentMeta?.revisionID, gotModel.documentMeta?.revisionID)

        // Get it as a regular Document and verify the contents:
        val doc = testCollection.getDocument(id)!!
        Assert.assertEquals(gotModel.documentMeta?.revisionID, doc.revisionID)
        Assert.assertEquals("Nigel", doc.getString("name"))
        Assert.assertEquals(12, doc.getInt("age"))
        val faves = doc.getArray("favorites")!!
        Assert.assertEquals(2, faves.count())
        Assert.assertEquals("XTC", faves.getString(0))
        Assert.assertEquals("Elvis Costello", faves.getString(1))

        // Delete the model. `model` is out of date, so it will fail, but `gotModel` is OK:
        Assert.assertFalse(testCollection.delete(model, ConcurrencyControl.FAIL_ON_CONFLICT))
        Assert.assertTrue(testCollection.delete(gotModel, ConcurrencyControl.FAIL_ON_CONFLICT))
    }
}


// Simple Model class for tests
@Serializable
data class TestModel(var name: String,
                     var age: Int,
                     var favorites: List<String>? = null): DocumentModel {
    @Transient override var documentMeta: DocumentMeta? = null
}
