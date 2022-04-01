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

import com.couchbase.lite.internal.utils.LoadIntegrationTest
import com.couchbase.lite.internal.utils.Report
import com.couchbase.lite.internal.utils.SlowTest
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


private const val ITERATIONS = 2000

private fun interface Verifier {
    fun verify(n: Int, result: Result?)
}

// Timings were chosen to allow a Nexus 6 running Android 7.0 to pass.
class LoadTest : BaseDbTest() {
    @SlowTest
    @LoadIntegrationTest
    @Test
    fun testAddRevisions() {
        timeTest("testAddRevisions", 35 * 1000L) {
            addRevisions(1000, false)
            addRevisions(1000, true)
        }
    }

    @LoadIntegrationTest
    @Test
    fun testCreate() {
        timeTest("testCreate", 10 * 1000L) {
            createAndSaveDocument("Create", ITERATIONS)
            verifyByTag("Create", ITERATIONS)
            Assert.assertEquals(ITERATIONS.toLong(), baseTestDb.count)
        }
    }

    // This test reliably drove a bug that caused C4NativePeer
    // to finalize what appears to have been an incompletely initialize
    // instance of C4Document.  It is, otherwise, not relevant.
    @Ignore("Same test as testCreate")
    @LoadIntegrationTest
    @Test
    fun testCreateMany() {
        timeTest("testCreateMany", 35 * 1000L) {
            for (i in 0..3) {
                createAndSaveDocument("Create", ITERATIONS)
                verifyByTag("Create", ITERATIONS)
                Assert.assertEquals(ITERATIONS.toLong(), baseTestDb.count)
            }
        }
    }

    @SlowTest
    @LoadIntegrationTest
    @Test
    fun testDelete() {
        timeTest("testDelete", 15 * 1000L) {
            // create & delete doc ITERATIONS times
            for (i in 0 until ITERATIONS) {
                val docID = String.format(Locale.ENGLISH, "doc-%010d", i)
                createAndSaveDocument(docID, "Delete")
                Assert.assertEquals(1, baseTestDb.count)
                val doc = baseTestDb.getDocument(docID)
                Assert.assertNotNull(doc)
                Assert.assertEquals("Delete", doc!!.getString("tag"))
                baseTestDb.delete(doc)
                Assert.assertEquals(0, baseTestDb.count)
            }
        }
    }

    @Test
    @LoadIntegrationTest
    fun testRead() {
        timeTest("testRead", 5 * 1000L) {
            // create 1 doc
            createAndSaveDocument("doc1", "Read")
            // read the doc n times
            for (i in 0 until ITERATIONS) {
                val doc = baseTestDb.getDocument("doc1")
                Assert.assertNotNull(doc)
                Assert.assertEquals("doc1", doc!!.id)
                Assert.assertEquals("Read", doc.getString("tag"))
            }
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1447
    @SlowTest
    @LoadIntegrationTest
    @Test
    fun testSaveManyDocs() {
        timeTest("testSaveManyDocs", 15 * 1000L) {
            // Without Batch
            for (i in 0 until ITERATIONS) {
                val doc = MutableDocument(String.format(Locale.ENGLISH, "doc-%05d", i))
                for (j in 0 until 100) {
                    doc.setInt(j.toString(), j); }
                try {
                    baseTestDb.save(doc)
                } catch (e: CouchbaseLiteException) {
                    Report.log(LogLevel.ERROR, "Failed to save", e)
                }
            }
            Assert.assertEquals(ITERATIONS.toLong(), baseTestDb.count)
        }
    }

    @SlowTest
    @LoadIntegrationTest
    @Test
    fun testUpdate() {
        timeTest("testUpdate", 25 * 1000L) {
            // create doc
            createAndSaveDocument("doc1", "Create")
            var doc = baseTestDb.getDocument("doc1")
            Assert.assertNotNull(doc)
            Assert.assertEquals("doc1", doc!!.id)
            Assert.assertEquals("Create", doc.getString("tag"))

            // update doc n times
            updateDoc(doc, ITERATIONS, "Update")

            // check document
            doc = baseTestDb.getDocument("doc1")
            Assert.assertNotNull(doc)
            Assert.assertEquals("doc1", doc!!.id)
            Assert.assertEquals("Update", doc.getString("tag"))
            Assert.assertEquals(ITERATIONS.toLong(), doc.getInt("update").toLong())
            val street = String.format(Locale.ENGLISH, "%d street.", ITERATIONS)
            val phone = String.format(Locale.ENGLISH, "650-000-%04d", ITERATIONS)
            Assert.assertEquals(street, doc.getDictionary("address")!!.getString("street"))
            Assert.assertEquals(phone, doc.getArray("phones")!!.getString(0))
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1610
    @SlowTest
    @LoadIntegrationTest
    @Test
    fun testUpdate2() {
        timeTest("testUpdate2", 20 * 1000L) {
            val mDoc = MutableDocument("doc1")
            val map: MutableMap<String, Any> = HashMap()
            map["ID"] = "doc1"
            mDoc.setValue("map", map)
            saveDocInBaseTestDb(mDoc)
            for (i in 0..1999) {
                map["index"] = i
                Assert.assertTrue(updateMap(map, i, i.toLong()))
            }
        }
    }

    /// Utility methods
    private fun updateMap(map: Map<String, *>, i: Int, l: Long): Boolean {
        val doc = baseTestDb.getDocument(map["ID"].toString()) ?: return false
        val newDoc = doc.toMutable()
        newDoc.setValue("map", map)
        newDoc.setInt("int", i)
        newDoc.setLong("long", l)
        try {
            baseTestDb.save(newDoc)
        } catch (e: CouchbaseLiteException) {
            Report.log(LogLevel.ERROR, "DB is not responding", e)
            return false
        }
        return true
    }

    private fun addRevisions(revisions: Int, retrieveNewDoc: Boolean) {
        baseTestDb.inBatch<CouchbaseLiteException> {
            val mDoc = MutableDocument("doc")
            if (retrieveNewDoc) {
                updateDocWithGetDocument(mDoc, revisions)
            } else {
                updateDoc(mDoc, revisions)
            }
        }
        val doc = baseTestDb.getDocument("doc")
        Assert.assertEquals(
            (revisions - 1).toLong(),
            doc!!.getInt("count").toLong()
        ) // start from 0.
    }

    private fun updateDoc(doc: MutableDocument, revisions: Int) {
        for (i in 0 until revisions) {
            doc.setValue("count", i)
            baseTestDb.save(doc)
            System.gc()
        }
    }

    private fun updateDocWithGetDocument(document: MutableDocument, revisions: Int) {
        var doc = document
        for (i in 0 until revisions) {
            doc.setValue("count", i)
            baseTestDb.save(doc)
            doc = baseTestDb.getDocument("doc")!!.toMutable()
        }
    }

    private fun createDocumentWithTag(id: String?, tag: String): MutableDocument {
        val doc = id?.let { MutableDocument(it) } ?: MutableDocument()

        // Tag
        doc.setValue("tag", tag)

        // String
        doc.setValue("firstName", "Daniel")
        doc.setValue("lastName", "Tiger")

        // Dictionary:
        val address = MutableDictionary()
        address.setValue("street", "1 Main street")
        address.setValue("city", "Mountain View")
        address.setValue("state", "CA")
        doc.setValue("address", address)

        // Array:
        val phones = MutableArray()
        phones.addValue("650-123-0001")
        phones.addValue("650-123-0002")
        doc.setValue("phones", phones)

        // Date:
        doc.setValue("updated", Date())
        return doc
    }

    @Throws(CouchbaseLiteException::class)
    private fun createAndSaveDocument(id: String, tag: String) {
        val doc = createDocumentWithTag(id, tag)
        baseTestDb.save(doc)
    }

    @Throws(CouchbaseLiteException::class)
    private fun createAndSaveDocument(tag: String, nDocs: Int) {
        for (i in 0 until nDocs) {
            val docID = String.format(Locale.ENGLISH, "doc-%010d", i)
            createAndSaveDocument(docID, tag)
        }
    }

    @Throws(CouchbaseLiteException::class)
    private fun updateDoc(document: Document?, rounds: Int, tag: String) {
        var doc = document
        for (i in 1..rounds) {
            val mDoc = doc!!.toMutable()
            mDoc.setValue("update", i)
            mDoc.setValue("tag", tag)
            val address = mDoc.getDictionary("address")
            Assert.assertNotNull(address)
            val street = String.format(Locale.ENGLISH, "%d street.", i)
            address!!.setValue("street", street)
            mDoc.setDictionary("address", address)
            val phones = mDoc.getArray("phones")
            Assert.assertNotNull(phones)
            Assert.assertEquals(2, phones!!.count().toLong())
            val phone = String.format(Locale.ENGLISH, "650-000-%04d", i)
            phones.setValue(0, phone)
            mDoc.setArray("phones", phones)
            mDoc.setValue("updated", Date())
            doc = saveDocInBaseTestDb(mDoc)
        }
    }

    @Throws(CouchbaseLiteException::class)
    private fun verifyByTag(tag: String, verifier: Verifier) {
        var n = 0
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property("tag").equalTo(Expression.string(tag)))
            .execute().use { rs ->
                for (row in rs) {
                    verifier.verify(++n, row)
                }
            }
    }

    @Throws(CouchbaseLiteException::class)
    private fun verifyByTag(tag: String, nRows: Int) {
        val count = AtomicInteger(0)
        verifyByTag(tag) { _, _ -> count.incrementAndGet() }
        Assert.assertEquals(nRows.toLong(), count.toInt().toLong())
    }

    private fun timeTest(testName: String, maxTimeMs: Long, test: Runnable) {
        val t0 = System.currentTimeMillis()
        test.run()
        val elapsedTime = System.currentTimeMillis() - t0
        Report.log("Test " + testName + " time: " + elapsedTime)
        assertTrue(elapsedTime < maxTimeMs)
    }

}
