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
    protected lateinit var testCollection: Collection
    protected lateinit var testColName : String
    protected lateinit var testScopeName : String

    protected val Scope.collectionCount
        get() = this.collections.size

    @Before
    fun setUpBaseCollectionTest() {
        testColName = getUniqueName("test_collection")
        testScopeName = getUniqueName("test_scope")
        testCollection = baseTestDb.createCollection(testColName, testScopeName)
        Report.log(LogLevel.INFO, "Created base test Collection: $testCollection")
    }

    @After
    fun tearDownBaseCollectionTest() {
        val collectionName = testCollection.name
        // don't delete the default collection
        if (Collection.DEFAULT_NAME != collectionName) {
            baseTestDb.deleteCollection(collectionName)
            Report.log(LogLevel.INFO, "Deleted testCollection: $testCollection")
        }
    }

    @Throws(CouchbaseLiteException::class)
    protected fun createSingleDocInCollectionWithId(docID: String?): Document {
        val n = testCollection.count
        val doc = MutableDocument(docID)
        doc.setValue("key", 1)
        val savedDoc = saveDocInBaseCollectionTest(doc)
        Assert.assertEquals(n + 1, testCollection.count)
        Assert.assertEquals(1, savedDoc.sequence)
        return savedDoc
    }

    @Throws(CouchbaseLiteException::class)
    protected fun saveDocInBaseCollectionTest(doc: MutableDocument): Document {
        testCollection.save(doc)
        val savedDoc = testCollection.getDocument(doc.id)
        Assert.assertNotNull(savedDoc)
        Assert.assertEquals(doc.id, savedDoc!!.id)
        return savedDoc
    }

    @Throws(CouchbaseLiteException::class)
    protected fun createDocsInCollectionTest(n: Int) {
        for (i in 0 until n) {
            val doc = MutableDocument(String.format(Locale.US, "doc_%03d", i))
            doc.setValue("key", i)
            saveDocInBaseCollectionTest(doc)
        }
        Assert.assertEquals(n.toLong(), testCollection.count)
    }
}

