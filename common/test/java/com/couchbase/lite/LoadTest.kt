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
import org.junit.Assert
import org.junit.Test
import java.util.Date


private const val ITERATIONS = 2000


// These tests are flaky.  Whichever test get the GC run
// is probably going to exceed its time limit.
class LoadTest : BaseDbTest() {
    companion object {
        private val DEVICE_SPEED_MULTIPLIER = mapOf(
            // java devices
            "lin" to 50,
            "mac" to 100,
            "win" to 120,

            // android on jenkins
            "bluejay" to 100,
            "starqlteue" to 150,
            "lynx" to 100,
            "a12usq" to 230,
            "r8quex" to 100,

            // android local
            "shiba" to 80,
            "sunfish" to 140,
            "taimen" to 90,
            "occam" to 300,
        )
    }

    private val speedMultiplier: Int
        get() = DEVICE_SPEED_MULTIPLIER[device] ?: 100


    // https://github.com/couchbase/couchbase-lite-android/issues/1447
    @SlowTest
    @LoadTest
    @Test
    fun testCreateUnbatched() {
        val tag = getUniqueName("create#1")
        val docs = createComplexTestDocs(ITERATIONS, tag)

        Assert.assertEquals(0, testCollection.count)
        timeTest("testCreateUnbatched", 170) {
            for (doc in docs) {
                testCollection.save(doc)
            }
        }
        Assert.assertEquals(ITERATIONS.toLong(), testCollection.count)
        verifyByTag(tag, ITERATIONS)
    }

    @SlowTest
    @LoadTest
    @Test
    fun testCreateBatched() {
        val tag = getUniqueName("create#2")
        val docs = createComplexTestDocs(ITERATIONS, tag)

        Assert.assertEquals(0, testCollection.count)
        timeTest("testCreateBatched", 45) {
            testDatabase.inBatch<CouchbaseLiteException> {
                for (doc in docs) {
                    testCollection.save(doc)
                }
            }
        }
        Assert.assertEquals(ITERATIONS.toLong(), testCollection.count)
        verifyByTag(tag, ITERATIONS)
    }

    @Test
    @LoadTest
    fun testRead() {
        val tag = getUniqueName("read")
        val ids = saveDocsInCollection(createComplexTestDocs(ITERATIONS, tag)).map { it.id }

        Assert.assertEquals(ITERATIONS.toLong(), testCollection.count)
        timeTest("testRead", 60) {
            for (id in ids) {
                val doc = testCollection.getDocument(id)
                Assert.assertNotNull(doc)

                Assert.assertEquals(tag, doc!!.getString(TEST_DOC_TAG_KEY))

                val address = doc.getDictionary("address")
                Assert.assertNotNull(address)
                Assert.assertEquals("Mountain View", address!!.getString("city"))

                val phones = doc.getArray("phones")
                Assert.assertNotNull(phones)
                Assert.assertEquals("650-123-0002", phones!!.getString(1))
            }
        }
    }

    @SlowTest
    @LoadTest
    @Test
    fun testUpdate1() {
        val newTag = getUniqueName("update")
        val ids = saveDocsInCollection(createComplexTestDocs(ITERATIONS, getUniqueName("update"))).map { it.id }

        Assert.assertEquals(ITERATIONS.toLong(), testCollection.count)
        timeTest("testUpdate1", 130) {
            var i = 0
            for (id in ids) {
                i++

                val mDoc = testCollection.getDocument(id)!!.toMutable()

                mDoc.setValue("updated", Date())
                mDoc.setValue("update", i)

                mDoc.setValue(TEST_DOC_TAG_KEY, newTag)

                val address = mDoc.getDictionary("address")
                Assert.assertNotNull(address)
                address!!.setValue("street", "${i} street")
                mDoc.setDictionary("address", address)

                val phones = mDoc.getArray("phones")
                Assert.assertNotNull(phones)
                phones!!.setValue(0, "650-000-${i}")
                mDoc.setArray("phones", phones)

                testCollection.save(mDoc)
            }
        }
        Assert.assertEquals(ITERATIONS.toLong(), testCollection.count)
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

        Assert.assertEquals(1L, testCollection.count)
        timeTest("testUpdate2", 110) {
            for (i in 0..ITERATIONS) {
                mDoc = testCollection.getDocument(mDoc.id)!!.toMutable()
                mDoc.setValue("map", mapOf("idx" to i, "long" to i.toLong(), TEST_DOC_TAG_KEY to getUniqueName("tag")))
                testCollection.save(mDoc)
            }
        }

        val doc = testCollection.getDocument(mDoc.id)
        Assert.assertNotNull(doc)
        val map = doc!!.getDictionary("map")
        Assert.assertNotNull(map)
        Assert.assertEquals(ITERATIONS, map!!.getInt("idx"))
        Assert.assertEquals(ITERATIONS.toLong(), map.getLong("long"))
        Assert.assertEquals(mDoc.getString(TEST_DOC_TAG_KEY), doc.getString(TEST_DOC_TAG_KEY))
    }

    @VerySlowTest
    @LoadTest
    @Test
    fun testDelete() {
        val docs = saveDocsInCollection(createComplexTestDocs(ITERATIONS, getUniqueName("delete")))

        Assert.assertEquals(ITERATIONS.toLong(), testCollection.count)
        timeTest("testDelete", 100) {
            for (doc in docs) {
                testCollection.delete(doc)
            }
        }
        Assert.assertEquals(0, testCollection.count)
    }

    @SlowTest
    @LoadTest
    @Test
    fun testSaveRevisions1() {
        var mDoc = MutableDocument()
        timeTest("testSaveRevisions1", 50) {
            testDatabase.inBatch<CouchbaseLiteException> {
                for (i in 0 until ITERATIONS) {
                    mDoc.setValue("count", i)
                    testCollection.save(mDoc)
                    mDoc = testCollection.getDocument(mDoc.id)!!.toMutable()
                }
            }
        }
        Assert.assertEquals((ITERATIONS - 1), testCollection.getDocument(mDoc.id)!!.getInt("count"))
    }

    @SlowTest
    @LoadTest
    @Test
    fun testSaveRevisions2() {
        val mDoc = MutableDocument()
        timeTest("testSaveRevisions2", 40) {
            testDatabase.inBatch<CouchbaseLiteException> {
                for (i in 0 until ITERATIONS) {
                    mDoc.setValue("count", i)
                    testCollection.save(mDoc)
                }
            }
        }
        Assert.assertEquals((ITERATIONS - 1), testCollection.getDocument(mDoc.id)!!.getInt("count"))
    }

    // Utility methods

    private fun verifyByTag(tag: String, count: Int) {
        Assert.assertEquals(
            count,
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .where(Expression.property(TEST_DOC_TAG_KEY).equalTo(Expression.string(tag)))
                .execute().allResults().size
        )
    }

    private fun timeTest(testName: String, testTime: Long, test: Runnable) {
        System.gc() // try to avoid an unnecessary gc during the test
        val maxTimeMs = testTime * speedMultiplier
        val t0 = System.currentTimeMillis()
        test.run()
        val elapsedTime = System.currentTimeMillis() - t0
        Report.log("Load test ${testName} completed in ${elapsedTime}ms (${maxTimeMs}) on ${device}")
        Assert.assertTrue(
            "Load test ${testName} over time: ${elapsedTime} > ${maxTimeMs} on ${device}",
            elapsedTime < maxTimeMs
        )
    }
}
