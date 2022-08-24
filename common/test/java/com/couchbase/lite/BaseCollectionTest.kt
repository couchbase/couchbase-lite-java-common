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

import com.couchbase.lite.internal.utils.JSONUtils
import com.couchbase.lite.internal.utils.PlatformUtils
import com.couchbase.lite.internal.utils.Report
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

open class BaseCollectionTest : BaseDbTest() {
    protected lateinit var testCollection: Collection

    protected val Scope.collectionCount
        get() = this.collections.size

    @Before
    fun setUpBaseCollectionTest() {
        testCollection = baseTestDb.createCollection(getUniqueName("test_collection"), getUniqueName("test_scope"))
        Report.log("Created base test Collection: $testCollection")
    }

    protected fun saveDocInCollection(doc: MutableDocument, collection: Collection): Document {
        collection.save(doc)
        val savedDoc = collection.getDocument(doc.id)
        assertNotNull(savedDoc)
        assertEquals(doc.id, savedDoc!!.id)
        return savedDoc
    }

    @Throws(CouchbaseLiteException::class)
    protected fun createSingleDocInTestCollectionWithId(docID: String?): Document {
        val n = testCollection.count
        val doc = MutableDocument(docID)
        doc.setValue("key", 1)
        val savedDoc = saveDocInTestCollection(doc)
        assertEquals(n + 1, testCollection.count)
        assertEquals(1, savedDoc.sequence)
        return savedDoc
    }

    @Throws(CouchbaseLiteException::class)
    protected fun saveDocInTestCollection(doc: MutableDocument): Document {
        testCollection.save(doc)
        val savedDoc = testCollection.getDocument(doc.id)
        assertNotNull(savedDoc)
        assertEquals(doc.id, savedDoc!!.id)
        return savedDoc
    }

    @Throws(CouchbaseLiteException::class)
    protected fun createDocsInTestCollection(n: Int) {
        for (i in 0 until n) {
            val doc = MutableDocument(String.format(Locale.US, "doc_%03d", i))
            doc.setValue("key", i)
            saveDocInTestCollection(doc)
        }
        assertEquals(n.toLong(), testCollection.count)
    }

    protected fun createDocsInCustomizedCollection(n: Int, col: Collection) {
        for (i in 0 until n) {
            val doc = MutableDocument(String.format(Locale.US, "%s_%s_doc_%03d", col.database.name, col.name, i))
            doc.setValue("key", i)
            saveDocInCollection(doc, col)
        }
        assertEquals(n.toLong(), col.count)
    }

    @Throws(IOException::class, JSONException::class)
    protected fun loadJSONResourceIntoCollection(name: String, collection: Collection) {
        BufferedReader(InputStreamReader(PlatformUtils.getAsset(name))).use {
            var n = 1
            it.lineSequence().forEach { l ->
                if (l.trim().isEmpty()) {
                    return
                }
                val doc = MutableDocument(String.format(Locale.ENGLISH, "doc-%03d", n++))
                doc.setData(JSONUtils.fromJSON(JSONObject(l)))

                saveDocInCollection(doc, collection)
            }
        }
    }
}

