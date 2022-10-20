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
@file:Suppress("DEPRECATION")

package com.couchbase.lite

import com.couchbase.lite.internal.utils.LoadTest
import com.couchbase.lite.internal.utils.Report
import com.couchbase.lite.internal.utils.SlowTest
import com.couchbase.lite.internal.utils.VerySlowTest
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
    @LoadTest
    @Test
    fun testAddRevisions() {
        timeTest("testAddRevisions", 45 * 1000L) {
            addRevisions(1000, false)
            addRevisions(1000, true)
        }
    }

    @SlowTest
    @LoadTest
    @Test
    fun testCreate() {
        timeTest("testCreate", 10 * 1000L) {
            createAndSaveDocuments("Create", ITERATIONS)
            verifyByTag("Create", ITERATIONS)
            Assert.assertEquals(ITERATIONS.toLong(), baseTestDb.count)
        }
    }

    // This test reliably drove a bug that caused C4NativePeer
    // to finalize what appears to have been an incompletely initialize
    // instance of C4Document.  It is, otherwise, not relevant.
    @Ignore("Same test as testCreate")
    @LoadTest
    @Test
    fun testCreateMany() {
        timeTest("testCreateMany", 35 * 1000L) {
            for (i in 0..3) {
                createAndSaveDocuments("Create", ITERATIONS)
                verifyByTag("Create", ITERATIONS)
                Assert.assertEquals(ITERATIONS.toLong(), baseTestDb.count)
            }
        }
    }

    @VerySlowTest
    @LoadTest
    @Test
    fun testDelete() {
        timeTest("testDelete", 20 * 1000L) {
            // create & delete doc ITERATIONS times
            for (i in 0 until ITERATIONS) {
                val docID = "doc-${i}"
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
    @LoadTest
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
    @LoadTest
    @Test
    fun testSaveManyDocs() {
        timeTest("testSaveManyDocs", 20 * 1000L) {
            // Without Batch
            for (i in 0 until ITERATIONS) {
                val doc = MutableDocument("doc-${i}")
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
    @LoadTest
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
            for (i in 1..ITERATIONS) {
                val mDoc = baseTestDb.getDocument("doc1")!!.toMutable()
                mDoc.setValue("update", i)
                mDoc.setValue("tag", "Update")
                val address = mDoc.getDictionary("address")
                Assert.assertNotNull(address)
                address!!.setValue("street", "${i} street")
                mDoc.setDictionary("address", address)
                val phones = mDoc.getArray("phones")
                Assert.assertNotNull(phones)
                Assert.assertEquals(2, phones!!.count())
                phones.setValue(0, "650-000-${i}")
                mDoc.setArray("phones", phones)
                mDoc.setValue("updated", Date())
                saveDocInBaseTestDb(mDoc)
            }
            // check document
            doc = baseTestDb.getDocument("doc1")
            Assert.assertNotNull(doc)
            Assert.assertEquals("doc1", doc!!.id)
            Assert.assertEquals("Update", doc.getString("tag"))
            Assert.assertEquals(ITERATIONS, doc.getInt("update"))
            Assert.assertEquals("${ITERATIONS} street", doc.getDictionary("address")!!.getString("street"))
            Assert.assertEquals("650-000-${ITERATIONS}", doc.getArray("phones")!!.getString(0))
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1610
    @SlowTest
    @LoadTest
    @Test
    fun testUpdate2() {
        timeTest("testUpdate2", 25 * 1000L) {
            val mDoc = MutableDocument("doc1")
            val map: MutableMap<String, Any> = HashMap()
            map["ID"] = "doc1"
            mDoc.setValue("map", map)
            saveDocInBaseTestDb(mDoc)
            for (i in 0..1999) {
                map["index"] = i
                val doc = baseTestDb.getDocument(map["ID"].toString()) ?: throw AssertionError("Failed fetching doc")
                val newDoc = doc.toMutable()
                newDoc.setValue("map", map)
                newDoc.setInt("int", i)
                newDoc.setLong("long", i.toLong())
                baseTestDb.save(newDoc)
            }
        }
    }


    /// Utility methods

    private fun createAndSaveDocument(id: String, tag: String) {
        val doc = createDocumentWithTag(id, tag)
        baseTestDb.save(doc)
    }

    private fun createAndSaveDocuments(tag: String, nDocs: Int) {
        for (i in 0 until nDocs) {
            createAndSaveDocument("doc-${i}", tag)
        }
    }

    private fun addRevisions(revisions: Int, retrieveNewDoc: Boolean) {
        baseTestDb.inBatch<CouchbaseLiteException> {
            var mDoc = MutableDocument("doc")
            for (i in 0 until revisions) {
                mDoc.setValue("count", i)
                baseTestDb.save(mDoc)
                if (!retrieveNewDoc) {
                    System.gc()
                } else {
                    mDoc = baseTestDb.getDocument("doc")!!.toMutable()
                }
            }
        }
        val doc = baseTestDb.getDocument("doc")
        Assert.assertEquals((revisions - 1), doc!!.getInt("count")) // start from 0.
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

    private fun verifyByTag(tag: String, nRows: Int) {
        val count = AtomicInteger(0)
        verifyByTag(tag) { _, _ -> count.incrementAndGet() }
        Assert.assertEquals(nRows.toLong(), count.toInt().toLong())
    }

    private fun timeTest(testName: String, maxTimeMs: Long, test: Runnable) {
        val t0 = System.currentTimeMillis()
        test.run()
        val elapsedTime = System.currentTimeMillis() - t0
        Report.log("Test ${testName} time: " + elapsedTime)
        assertTrue(elapsedTime < maxTimeMs)
    }
}
