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
package com.couchbase.lite

import com.couchbase.lite.internal.utils.LoadTest
import com.couchbase.lite.internal.utils.Report
import com.couchbase.lite.internal.utils.SlowTest
import com.couchbase.lite.internal.utils.VerySlowTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*


private const val ITERATIONS = 2000


class LoadTest : BaseDbTest() {
    companion object {
        val DEVICE_SPEED_MULTIPLIER = mapOf(
            "JVM" to 33,
            "r8quex" to 100,
            "a12uue" to 150,
            "starqlteue" to 150,
            "dandelion_global" to 100,
            "cheetah" to 100,
            "sunfish" to 55,
            "taimen" to 55,
            "shamu" to 130,
            "hammerhead" to 130,
            "occam" to 200,
        )
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1447
    @SlowTest
    @LoadTest
    @Test
    fun testCreateUnbatched() {
        val tag = getUniqueName("create#1")
        val docs = createComplexTestDocs(ITERATIONS, tag)

        assertEquals(0, testCollection.count)
        timeTest("testCreateUnbatched", 74) {
            for (doc in docs) {
                testCollection.save(doc)
            }
        }
        assertEquals(ITERATIONS.toLong(), testCollection.count)
        verifyByTag(tag, ITERATIONS)
    }

    @SlowTest
    @LoadTest
    @Test
    fun testCreateBatched() {
        val tag = getUniqueName("create#2")
        val docs = createComplexTestDocs(ITERATIONS, tag)

        assertEquals(0, testCollection.count)
        timeTest("testCreateBatched", 30) {
            testDatabase.inBatch<CouchbaseLiteException> {
                for (doc in docs) {
                    testCollection.save(doc)
                }
            }
        }
        assertEquals(ITERATIONS.toLong(), testCollection.count)
        verifyByTag(tag, ITERATIONS)
    }

    @Test
    @LoadTest
    fun testRead() {
        val tag = getUniqueName("read")
        val ids = saveDocsInCollection(createComplexTestDocs(ITERATIONS, tag)).map { it.id }

        assertEquals(ITERATIONS.toLong(), testCollection.count)
        timeTest("testRead", 12) {
            for (id in ids) {
                val doc = testCollection.getDocument(id)
                assertNotNull(doc)

                assertEquals(tag, doc!!.getString(TEST_DOC_TAG_KEY))

                val address = doc.getDictionary("address")
                assertNotNull(address)
                assertEquals("Mountain View", address!!.getString("city"))

                val phones = doc.getArray("phones")
                assertNotNull(phones)
                assertEquals("650-123-0002", phones!!.getString(1))
            }
        }
    }

    @SlowTest
    @LoadTest
    @Test
    fun testUpdate1() {
        val newTag = getUniqueName("update")
        val ids = saveDocsInCollection(createComplexTestDocs(ITERATIONS, getUniqueName("update"))).map { it.id }

        assertEquals(ITERATIONS.toLong(), testCollection.count)
        timeTest("testUpdate1", 100) {
            var i = 0
            for (id in ids) {
                i++

                val mDoc = testCollection.getDocument(id)!!.toMutable()

                mDoc.setValue("updated", Date())
                mDoc.setValue("update", i)

                mDoc.setValue(TEST_DOC_TAG_KEY, newTag)

                val address = mDoc.getDictionary("address")
                assertNotNull(address)
                address!!.setValue("street", "${i} street")
                mDoc.setDictionary("address", address)

                val phones = mDoc.getArray("phones")
                assertNotNull(phones)
                phones!!.setValue(0, "650-000-${i}")
                mDoc.setArray("phones", phones)

                testCollection.save(mDoc)
            }
        }
        assertEquals(ITERATIONS.toLong(), testCollection.count)
        verifyByTag(newTag, ITERATIONS)
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1610
    @SlowTest
    @LoadTest
    @Test
    fun testUpdate2() {
        var mDoc = createTestDoc()
        mDoc.setValue("map", mapOf("idx" to 0, "long" to 0L, TEST_DOC_TAG_KEY to getUniqueName("tag")))
        testCollection.save(mDoc)

        assertEquals(1L, testCollection.count)
        timeTest("testUpdate2", 70) {
            for (i in 0..ITERATIONS) {
                mDoc = testCollection.getDocument(mDoc.id)!!.toMutable()
                mDoc.setValue("map", mapOf("idx" to i, "long" to i.toLong(), TEST_DOC_TAG_KEY to getUniqueName("tag")))
                testCollection.save(mDoc)
            }
        }

        val doc = testCollection.getDocument(mDoc.id)
        assertNotNull(doc)
        val map = doc!!.getDictionary("map")
        assertNotNull(map)
        assertEquals(ITERATIONS, map!!.getInt("idx"))
        assertEquals(ITERATIONS.toLong(), map.getLong("long"))
        assertEquals(mDoc.getString(TEST_DOC_TAG_KEY), doc.getString(TEST_DOC_TAG_KEY))
    }

    @VerySlowTest
    @LoadTest
    @Test
    fun testDelete() {
        val docs = saveDocsInCollection(createComplexTestDocs(ITERATIONS, getUniqueName("delete")))

        assertEquals(ITERATIONS.toLong(), testCollection.count)
        timeTest("testDelete", 80) {
            for (doc in docs) {
                testCollection.delete(doc)
            }
        }
        assertEquals(0, testCollection.count)
    }

    @SlowTest
    @LoadTest
    @Test
    fun testSaveRevisions1() {
        var mDoc = MutableDocument()
        timeTest("testSaveRevisions1", 42) {
            testDatabase.inBatch<CouchbaseLiteException> {
                for (i in 0 until ITERATIONS) {
                    mDoc.setValue("count", i)
                    testCollection.save(mDoc)
                    mDoc = testCollection.getDocument(mDoc.id)!!.toMutable()
                }
            }
        }
        assertEquals((ITERATIONS - 1), testCollection.getDocument(mDoc.id)!!.getInt("count"))
    }

    @SlowTest
    @LoadTest
    @Test
    fun testSaveRevisions2() {
        val mDoc = MutableDocument()
        timeTest("testSaveRevisions2", 22) {
            testDatabase.inBatch<CouchbaseLiteException> {
                for (i in 0 until ITERATIONS) {
                    mDoc.setValue("count", i)
                    testCollection.save(mDoc)
                }
            }
        }
        assertEquals((ITERATIONS - 1), testCollection.getDocument(mDoc.id)!!.getInt("count"))
    }

    // Utility methods

    private fun verifyByTag(tag: String, count: Int) {
        assertEquals(
            count,
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .where(Expression.property(TEST_DOC_TAG_KEY).equalTo(Expression.string(tag)))
                .execute().allResults().size
        )
    }

    private fun timeTest(testName: String, testTime: Long, test: Runnable) {
        val maxTimeMs = testTime * (DEVICE_SPEED_MULTIPLIER[device] ?: 200)
        val t0 = System.currentTimeMillis()
        test.run()
        val elapsedTime = System.currentTimeMillis() - t0
        Report.log("Load test ${testName} completed in ${elapsedTime} ms (${maxTimeMs})")
        assertTrue(
            "Load test ${testName} over time: ${elapsedTime} > ${maxTimeMs}",
            elapsedTime < maxTimeMs
        )
    }
}
