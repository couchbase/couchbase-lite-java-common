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

import com.couchbase.lite.internal.utils.Report
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.util.*

open class BaseCollectionTest : BaseDbTest() {
    private var testScope: Scope? = null
    private var testCollection: Collection? = null

    protected val Scope.collectionCount
        get() = this.collections.size

    @Before
    fun setUpBaseCollectionTest() {
        testScope = baseTestDb.defaultScope
        testCollection = testScope!!.getCollection(Collection.DEFAULT_NAME)
        Report.log(LogLevel.INFO, "Created base test Collection: $testCollection")
    }

    @After
    fun tearDownBaseCollectionTest() {
        val collectionName = if (testCollection == null) Collection.DEFAULT_NAME else testCollection!!.name
        // don't delete the default collection
        if (Collection.DEFAULT_NAME != collectionName) {
            baseTestDb.deleteCollection(collectionName)
            Report.log(LogLevel.INFO, "Deleted testCollection: $testCollection")
        }
    }

    @Throws(CouchbaseLiteException::class)
    protected fun createSingleDocInCollectionWithId(docID: String?): Document {
        val n = testCollection!!.count
        val doc = MutableDocument(docID)
        doc.setValue("key", 1)
        val savedDoc = saveDocInBaseCollectionTest(doc)
        Assert.assertEquals(n + 1, testCollection!!.count)
        Assert.assertEquals(1, savedDoc.sequence)
        return savedDoc
    }

    @Throws(CouchbaseLiteException::class)
    protected fun saveDocInBaseCollectionTest(doc: MutableDocument): Document {
        testCollection!!.save(doc)
        val savedDoc = testCollection!!.getDocument(doc.id)
        Assert.assertNotNull(savedDoc)
        Assert.assertEquals(doc.id, savedDoc!!.id)
        return savedDoc
    }
}
