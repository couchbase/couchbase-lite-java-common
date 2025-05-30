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

import com.couchbase.lite.internal.exec.ExecutionService.CloseableExecutor
import com.couchbase.lite.internal.utils.JSONUtils
import com.couchbase.lite.internal.utils.PlatformUtils
import com.couchbase.lite.internal.utils.Report
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


// Here to make it visible from other test packages
val Database.getC4Db
    get() = this.openC4Database

val Scope.collectionCount
    get() = this.collections.size

fun Database.createTestCollection(name: String = "coll_", scope: String = "scope_"): Collection {
    val uname = BaseTest.getUniqueName(name)
    val uscope = BaseTest.getUniqueName(scope)
    try {
        val coll = this.createCollection(uname, uscope)
        Assert.assertNotNull(coll)
        return coll
    } catch (e: Exception) {
        throw AssertionError("Failed creating collection ${uname}.${uscope} in database ${this}", e)
    }
}

fun Database.createSimilarCollection(collection: Collection): Collection {
    try {
        val coll = this.createCollection(collection.name, collection.scope.name)
        Assert.assertNotNull(coll)
        return coll
    } catch (e: Exception) {
        throw AssertionError("Failed creating collection similar to ${collection} in database ${this}", e)
    }
}

fun Database.getSimilarCollection(collection: Collection): Collection {
    try {
        val coll = this.getCollection(collection.name, collection.scope.name)
        Assert.assertNotNull(coll)
        return coll!!
    } catch (e: Exception) {
        throw AssertionError("Failed getting collection similar to ${collection} in database ${this}", e)
    }
}

fun Collection.getQualifiedName() = "${this.scope.name}.${this.name}"

fun Collection.getNonNullDoc(id: String) = this.getDocument(id) ?: throw AssertionError("document ${id} is null")

fun Collection.delete() {
    val db = this.database
    try {
        db.deleteCollection(this.name, this.scope.name)
    } catch (e: Exception) {
        throw AssertionError("Failed deleting collection ${this} from database ${db}", e)
    }
}

fun Document.delete() {
    val coll = this.collection
    Assert.assertNotNull(coll)
    try {
        coll!!.delete(this)
    } catch (e: CouchbaseLiteException) {
        throw AssertionError("Failed deleting document ${this.id} from collection ${coll}")
    }
}

fun <T : Comparable<T>> assertContents(l1: List<T>, vararg contents: T) {
    Assert.assertEquals(l1.sorted(), listOf(*contents).sorted())
}

fun <T : Comparable<T>> assertContents(l1: Set<T>, vararg contents: T) {
    Assert.assertEquals(l1.sorted(), listOf(*contents).sorted())
}

fun CountDownLatch.stdWait(): Boolean {
    return try {
        await(BaseTest.STD_TIMEOUT_SEC, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        false
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> List<*>.castAs() = if (!all { it is T }) null else this as List<T>

// Comparing documents isn't trivial:
// Fleece will change the types of numeric values
// to suit its internal requirements.
// This comparator also will not work with nested
// complex objects: arrays and dictionaries.
fun assertSameContent(mDoc: MutableDocument, doc: Document?) {
    doc ?: throw AssertionError("doc is null")
    Assert.assertEquals(mDoc.toMap(), doc.toMap())
}

fun readJSONResource(name: String?): String {
    val buf = StringBuilder()
    PlatformUtils.getAsset(name)?.use { assetStream ->
        BufferedReader(InputStreamReader(assetStream)).use { src ->
            while (true) {
                val l = src.readLine() ?: break
                if (l.trim().isNotEmpty()) buf.append(l)
            }
        }
    }
    return buf.toString()
}

abstract class BaseDbTest : BaseTest() {
    protected val testDatabase: Database
        get() = testDb!!
    protected val testCollection: Collection
        get() = testCol!!
    protected val testTag: String
        get() = testTg!!
    protected val testSerialExecutor: Executor
        get() = testExec!!

    private var testDb: Database? = null
    private var testCol: Collection? = null
    private var testTg: String? = null
    private var testExec: CloseableExecutor? = null

    @Before
    fun setUpBaseDbTest() {
        testDb = createDb("base_db")
        Report.log("Created base test DB: $testDatabase")

        Assert.assertNotNull(testDatabase)
        Assert.assertTrue(testDatabase.isOpen)

        testCol = testDatabase.createCollection(getUniqueName("test_collection"), getUniqueName("test_scope"))
        Report.log("Created base test Collection: $testCollection")
        Assert.assertNotNull(testCol)

        testTg = getUniqueName("db_test_tag")

        testExec = object : CloseableExecutor {
            val executor: ThreadPoolExecutor = ThreadPoolExecutor(
                1, 1,
                30, TimeUnit.SECONDS,
                LinkedBlockingQueue(),  // thread factory that gives our threads nice recognizable names
                object : ThreadFactory {
                    private val threadId = AtomicInteger(0)

                    override fun newThread(r: Runnable): Thread {
                        val thread = Thread(r, "TEST-THREAD #" + threadId.incrementAndGet())
                        thread.isDaemon = true
                        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e: Throwable? ->
                            Report.log(
                                e,
                                "Uncaught exception on test thread %s",
                                thread.name
                            )
                        }
                        Report.log("New test thread: %s", thread.name)
                        return thread
                    }
                })

            override fun execute(task: Runnable) {
                Report.log("task enqueued: %s", task)
                executor.execute {
                    Report.log("Test task started: %s", task)
                    try {
                        task.run()
                    } finally {
                        Report.log("Test task finished: %s", task)
                    }
                }
            }

            override fun getPending(): Int {
                return executor.queue.size
            }

            override fun stop(timeout: Long, unit: TimeUnit): Boolean {
                executor.shutdownNow()
                return true
            }
        }
    }

    @After
    fun tearDownBaseDbTest() {
        testCol?.let {
            it.close()
            Report.log("Test collection closed: ${it.fullName}")
        }

        testDb?.let {
            eraseDb(it)
            Report.log("Test db erased: ${it.name}")
        }

        var succeeded = false
        testExec?.let { succeeded = it.stop(2, TimeUnit.SECONDS) }
        Report.log("Executor stopped: %s", succeeded)
    }

    protected fun reopenTestDb() {
        val cScope = testCollection.scope.name
        val cName = testCollection.name
        testCollection.close()

        testDb = reopenDb(testDatabase)

        testCol = testDatabase.getCollection(cName, cScope)
            ?: throw AssertionError("Could not create collection ${cScope}.${cName} in database ${testDatabase.name}")
    }

    protected fun recreateTestDb() {
        val cScope = testCollection.scope.name
        val cName = testCollection.name
        testCollection.close()

        eraseDb(testDatabase)

        testDb = createDb("base_db")

        testCol = testDatabase.getCollection(cName, cScope)
            ?: throw AssertionError("Could not create collection ${cScope}.${cName} in database ${testDatabase.name}")
    }

    protected fun duplicateTestDb(): Pair<Database, Collection> {
        val otherDb = duplicateDb(testDatabase)
        Assert.assertNotNull(otherDb)
        val otherCollection = otherDb.getSimilarCollection(testCollection)
        Assert.assertNotNull(otherCollection)
        return Pair(otherDb, otherCollection)
    }

    protected fun saveDocInCollection(mDoc: MutableDocument, collection: Collection = testCollection): Document {
        collection.save(mDoc)
        val doc = collection.getDocument(mDoc.id)
        Assert.assertNotNull(doc)
        Assert.assertEquals(mDoc.id, doc!!.id)
        return doc
    }

    protected fun saveDocsInCollection(mDocs: List<MutableDocument>, collection: Collection = testCollection)
            : List<Document> {
        var docs: List<Document>? = null
        collection.database.inBatch<CouchbaseLiteException> {
            docs = mDocs.map { saveDocInCollection(it, collection) }
        }
        return docs ?: throw CouchbaseLiteError("doc list is null")
    }

    protected fun createDocInCollection(
        tag: String = testTag,
        collection: Collection = testCollection
    ): MutableDocument {
        val mDoc = createTestDoc(tag)
        val n = collection.count
        saveDocInCollection(mDoc, collection)
        Assert.assertEquals(n + 1, collection.count)
        return mDoc
    }

    protected fun createDocsInCollection(
        count: Int = 1,
        tag: String = testTag,
        collection: Collection = testCollection,
        first: Int = 1
    ): List<MutableDocument> {
        val mDocs = createTestDocs(first, count, tag)
        saveDocsInCollection(mDocs, collection)
        return mDocs
    }

    protected fun verifyDocInCollection(docId: String, tag: String = testTag, coll: Collection = testCollection) {
        val doc = coll.getDocument(docId)
        Assert.assertNotNull(doc)
        Assert.assertEquals(docId, doc?.id ?: Int.MAX_VALUE)
        Assert.assertEquals(tag, doc?.getValue(TEST_DOC_TAG_KEY))
    }

    protected fun verifyDocsInCollection(
        docIds: kotlin.collections.Collection<String>,
        tag: String = testTag,
        coll: Collection = testCollection
    ) {
        docIds.forEach { verifyDocInCollection(it, tag, coll) }
    }

    // file is one JSON object per line
    protected fun loadJSONResourceIntoCollection(
        resName: String,
        idTemplate: String = "doc-%03d",
        collection: Collection = testCollection
    ) {
        PlatformUtils.getAsset(resName)?.use { assetStream ->
            try {
                BufferedReader(InputStreamReader(assetStream)).use { src ->
                    var n = 1
                    while (true) {
                        val l = src.readLine() ?: break
                        val doc = MutableDocument(String.format(Locale.ENGLISH, idTemplate, n++))
                        doc.setData(JSONUtils.fromJSON(JSONObject(l)))
                        saveDocInCollection(doc, collection)
                    }
                }
            } catch (e: java.lang.Exception) {
                throw java.lang.AssertionError(
                    "Failed reading JSON resource ${resName} into collection ${collection}",
                    e
                )
            }
        }
    }

    /// Data objects

    protected fun makeArray(): MutableArray {
        // A small array
        val simpleArray = MutableArray()
        simpleArray.addInt(54)
        simpleArray.addString("Joplin")

        // A small dictionary
        val simpleDict = MutableDictionary()
        simpleDict.setInt("sdict-1", 58)
        simpleDict.setString("sdict-2", "Winehouse")
        val array = MutableArray()
        array.addValue(null)
        array.addBoolean(true)
        array.addBoolean(false)
        array.addInt(0)
        array.addInt(Int.MIN_VALUE)
        array.addInt(Int.MAX_VALUE)
        array.addLong(0L)
        array.addLong(Long.MIN_VALUE)
        array.addLong(Long.MAX_VALUE)
        array.addFloat(0.0f)
        array.addFloat(Float.MIN_VALUE)
        array.addFloat(Float.MAX_VALUE)
        array.addDouble(0.0)
        array.addDouble(Double.MIN_VALUE)
        array.addDouble(Double.MAX_VALUE)
        array.addNumber(null)
        array.addNumber(0)
        array.addNumber(Float.MIN_VALUE)
        array.addNumber(Long.MIN_VALUE)
        array.addString(null)
        array.addString("Harry")
        array.addDate(null)
        array.addDate(JSONUtils.toDate(TEST_DATE))
        array.addArray(null)
        array.addArray(simpleArray)
        array.addDictionary(null)
        array.addDictionary(simpleDict)
        return array
    }

    protected fun verifyArray(jArray: JSONArray) {
        Assert.assertEquals(27, jArray.length().toLong())

        Assert.assertEquals(JSONObject.NULL, jArray[0])

        Assert.assertEquals(true, jArray[1])
        Assert.assertEquals(false, jArray[2])

        Assert.assertEquals(0, jArray[3])
        Assert.assertEquals(Int.MIN_VALUE, jArray[4])
        Assert.assertEquals(Int.MAX_VALUE, jArray[5])

        Assert.assertEquals(0, jArray[6])
        Assert.assertEquals(Long.MIN_VALUE, jArray[7])
        Assert.assertEquals(Long.MAX_VALUE, jArray[8])

        Assert.assertEquals(0.0F, jArray.getDouble(9).toFloat(), 0.001F)
        Assert.assertEquals(Float.MIN_VALUE, jArray.getDouble(10).toFloat(), 0.001F)
        Assert.assertEquals(Float.MAX_VALUE, jArray.getDouble(11).toFloat(), 100.0F)

        Assert.assertEquals(0.0, jArray.getDouble(12), 0.001)
        Assert.assertEquals(Double.MIN_VALUE, jArray.getDouble(13), 0.001)
        Assert.assertEquals(Double.MAX_VALUE, jArray.getDouble(14), 1.0)

        Assert.assertEquals(JSONObject.NULL, jArray[15])
        Assert.assertEquals(0, jArray.getLong(16))
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), jArray.getDouble(17), 0.001)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), jArray.getLong(18).toDouble(), 0.001)

        Assert.assertEquals(JSONObject.NULL, jArray[19])
        Assert.assertEquals("Harry", jArray[20])

        Assert.assertEquals(JSONObject.NULL, jArray[21])
        Assert.assertEquals(TEST_DATE, jArray[22])

        Assert.assertEquals(JSONObject.NULL, jArray[23])
        Assert.assertEquals(JSONArray::class.java, jArray[24].javaClass)

        Assert.assertEquals(JSONObject.NULL, jArray[25])
        Assert.assertEquals(JSONObject::class.java, jArray[26].javaClass)

        Assert.assertEquals(JSONObject.NULL, jArray[25])
        Assert.assertEquals(JSONObject::class.java, jArray[26].javaClass)
    }

    protected fun verifyArray(a: ArrayInterface?, fromJSON: Boolean = false) {
        Assert.assertNotNull(a)
        val array = a!!

        Assert.assertEquals(27, array.count().toLong())

        //#0 array.addValue(null);
        Assert.assertNull(array.getValue(0))
        Assert.assertFalse(array.getBoolean(0))
        Assert.assertEquals(0, array.getInt(0).toLong())
        Assert.assertEquals(0L, array.getLong(0))
        Assert.assertEquals(0.0f, array.getFloat(0), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(0), 0.001)
        Assert.assertNull(array.getNumber(0))
        Assert.assertNull(array.getString(0))
        Assert.assertNull(array.getDate(0))
        Assert.assertNull(array.getBlob(0))
        Assert.assertNull(array.getArray(0))
        Assert.assertNull(array.getDictionary(0))

        //#1 array.addBoolean(true);
        Assert.assertEquals(true, array.getValue(1))
        Assert.assertTrue(array.getBoolean(1))
        Assert.assertEquals(1, array.getInt(1).toLong())
        Assert.assertEquals(1L, array.getLong(1))
        Assert.assertEquals(1.0f, array.getFloat(1), 0.001f)
        Assert.assertEquals(1.0, array.getDouble(1), 0.001)
        Assert.assertEquals(1, array.getNumber(1))
        Assert.assertNull(array.getString(1))
        Assert.assertNull(array.getDate(1))
        Assert.assertNull(array.getBlob(1))
        Assert.assertNull(array.getArray(1))
        Assert.assertNull(array.getDictionary(1))

        //#2 array.addBoolean(false);
        Assert.assertEquals(false, array.getValue(2))
        Assert.assertFalse(array.getBoolean(2))
        Assert.assertEquals(0, array.getInt(2))
        Assert.assertEquals(0L, array.getLong(2))
        Assert.assertEquals(0.0f, array.getFloat(2), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(2), 0.001)
        Assert.assertEquals(0, array.getNumber(2))
        Assert.assertNull(array.getString(2))
        Assert.assertNull(array.getDate(2))
        Assert.assertNull(array.getBlob(2))
        Assert.assertNull(array.getArray(2))
        Assert.assertNull(array.getDictionary(2))

        //#3 array.addInt(0);
        Assert.assertEquals(0L, array.getValue(3))
        Assert.assertFalse(array.getBoolean(3))
        Assert.assertEquals(0, array.getInt(3))
        Assert.assertEquals(0L, array.getLong(3))
        Assert.assertEquals(0.0f, array.getFloat(3), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(3), 0.001)
        Assert.assertEquals(0L, array.getNumber(3))
        Assert.assertNull(array.getString(3))
        Assert.assertNull(array.getDate(3))
        Assert.assertNull(array.getBlob(3))
        Assert.assertNull(array.getArray(3))
        Assert.assertNull(array.getDictionary(3))

        //#4 array.addInt(Integer.MIN_VALUE);
        Assert.assertEquals(Int.MIN_VALUE.toLong(), array.getValue(4))
        Assert.assertTrue(array.getBoolean(4))
        Assert.assertEquals(Int.MIN_VALUE, array.getInt(4))
        Assert.assertEquals(Int.MIN_VALUE.toLong(), array.getLong(4))
        Assert.assertEquals(Int.MIN_VALUE.toFloat(), array.getFloat(4), 0.001f)
        Assert.assertEquals(Int.MIN_VALUE.toDouble(), array.getDouble(4), 0.001)
        Assert.assertEquals(Int.MIN_VALUE.toLong(), array.getNumber(4))
        Assert.assertNull(array.getString(4))
        Assert.assertNull(array.getDate(4))
        Assert.assertNull(array.getBlob(4))
        Assert.assertNull(array.getArray(4))
        Assert.assertNull(array.getDictionary(4))

        //#5 array.addInt(Integer.MAX_VALUE);
        Assert.assertEquals(Int.MAX_VALUE.toLong(), array.getValue(5))
        Assert.assertTrue(array.getBoolean(5))
        Assert.assertEquals(Int.MAX_VALUE, array.getInt(5))
        Assert.assertEquals(Int.MAX_VALUE.toLong(), array.getLong(5))
        Assert.assertEquals(Int.MAX_VALUE.toFloat(), array.getFloat(5), 100.0f)
        Assert.assertEquals(Int.MAX_VALUE.toDouble(), array.getDouble(5), 100.0)
        Assert.assertEquals(Int.MAX_VALUE.toLong(), array.getNumber(5))
        Assert.assertNull(array.getString(5))
        Assert.assertNull(array.getDate(5))
        Assert.assertNull(array.getBlob(5))
        Assert.assertNull(array.getArray(5))
        Assert.assertNull(array.getDictionary(5))

        //#6 array.addLong(0L);
        Assert.assertEquals(0L, array.getValue(6))
        Assert.assertFalse(array.getBoolean(6))
        Assert.assertEquals(0, array.getInt(6))
        Assert.assertEquals(0L, array.getLong(6))
        Assert.assertEquals(0.0f, array.getFloat(6), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(6), 0.001)
        Assert.assertEquals(0L, array.getNumber(6))
        Assert.assertNull(array.getString(6))
        Assert.assertNull(array.getDate(6))
        Assert.assertNull(array.getBlob(6))
        Assert.assertNull(array.getArray(6))
        Assert.assertNull(array.getDictionary(6))

        //#7 array.addLong(Long.MIN_VALUE);
        Assert.assertEquals(Long.MIN_VALUE, array.getValue(7))
        Assert.assertFalse(array.getBoolean(7))
        Assert.assertEquals(Long.MIN_VALUE.toInt(), array.getInt(7))
        Assert.assertEquals(Long.MIN_VALUE, array.getLong(7))
        Assert.assertEquals(Long.MIN_VALUE.toFloat(), array.getFloat(7), 0.001f)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), array.getDouble(7), 0.001)
        Assert.assertEquals(Long.MIN_VALUE, array.getNumber(7))
        Assert.assertNull(array.getString(7))
        Assert.assertNull(array.getDate(7))
        Assert.assertNull(array.getBlob(7))
        Assert.assertNull(array.getArray(7))
        Assert.assertNull(array.getDictionary(7))

        //#8 array.addLong(Long.MAX_VALUE);
        Assert.assertEquals(java.lang.Long.valueOf(Long.MAX_VALUE), array.getValue(8))
        Assert.assertTrue(array.getBoolean(8))
        Assert.assertEquals(0, array.getInt(8))
        Assert.assertEquals(Long.MAX_VALUE, array.getLong(8))
        Assert.assertEquals(Long.MAX_VALUE.toFloat(), array.getFloat(8), 100.0f)
        Assert.assertEquals(Long.MAX_VALUE.toDouble(), array.getDouble(8), 100.0)
        Assert.assertEquals(Long.MAX_VALUE, array.getNumber(8))
        Assert.assertNull(array.getString(8))
        Assert.assertNull(array.getDate(8))
        Assert.assertNull(array.getBlob(8))
        Assert.assertNull(array.getArray(8))
        Assert.assertNull(array.getDictionary(8))

        //#9 array.addFloat(0.0F);
        if (fromJSON) {
            Assert.assertEquals(0.0, array.getValue(9))
        } else {
            Assert.assertEquals(0.0f, array.getValue(9))
        }
        Assert.assertFalse(array.getBoolean(9))
        Assert.assertEquals(0, array.getInt(9).toLong())
        Assert.assertEquals(0L, array.getLong(9))
        Assert.assertEquals(0.0f, array.getFloat(9), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(9), 0.001)
        if (fromJSON) {
            Assert.assertEquals(0.0, array.getNumber(9))
        } else {
            Assert.assertEquals(0.0f, array.getValue(9))
        }
        Assert.assertNull(array.getString(9))
        Assert.assertNull(array.getDate(9))
        Assert.assertNull(array.getBlob(9))
        Assert.assertNull(array.getArray(9))
        Assert.assertNull(array.getDictionary(9))

        //#10 array.addFloat(Float.MIN_VALUE);
        Assert.assertEquals(Float.MIN_VALUE, array.getValue(10)!!.demoteToFloat())
        Assert.assertFalse(array.getBoolean(10))
        Assert.assertEquals(Float.MIN_VALUE, array.getInt(10).toFloat(), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE, array.getLong(10).toFloat(), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE, array.getFloat(10), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), array.getDouble(10), 0.001)
        Assert.assertEquals(Float.MIN_VALUE, array.getNumber(10)!!.demoteToFloat())
        Assert.assertNull(array.getString(10))
        Assert.assertNull(array.getDate(10))
        Assert.assertNull(array.getBlob(10))
        Assert.assertNull(array.getArray(10))
        Assert.assertNull(array.getDictionary(10))

        //#11 array.addFloat(Float.MAX_VALUE);
        Assert.assertEquals(Float.MAX_VALUE, array.getValue(11)!!.demoteToFloat())
        Assert.assertTrue(array.getBoolean(11))
        // ??? Fails: Assert.assertEquals(Float.MAX_VALUE.toInt(), array.getInt(11))
        // ??? Fails in Java: Assert.assertEquals(Float.MAX_VALUE.toLong(), array.getLong(11))
        Assert.assertEquals(Float.MAX_VALUE, array.getFloat(11), 100.0f)
        Assert.assertEquals(Float.MAX_VALUE.toDouble(), array.getDouble(11), 1.0E31)
        Assert.assertEquals(Float.MAX_VALUE, array.getNumber(11)!!.demoteToFloat())
        Assert.assertNull(array.getString(11))
        Assert.assertNull(array.getDate(11))
        Assert.assertNull(array.getBlob(11))
        Assert.assertNull(array.getArray(11))
        Assert.assertNull(array.getDictionary(11))

        //#12 array.addDouble(0.0);
        Assert.assertEquals(0.0, array.getValue(12)!!.promoteToDouble(), 0.001)
        Assert.assertFalse(array.getBoolean(12))
        Assert.assertEquals(0, array.getInt(12))
        Assert.assertEquals(0L, array.getLong(12))
        Assert.assertEquals(0.0f, array.getFloat(12), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(12), 0.001)
        Assert.assertEquals(0.0, array.getNumber(12))
        Assert.assertNull(array.getString(12))
        Assert.assertNull(array.getDate(12))
        Assert.assertNull(array.getBlob(12))
        Assert.assertNull(array.getArray(12))
        Assert.assertNull(array.getDictionary(12))

        //#13 array.addDouble(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, array.getValue(13))
        Assert.assertFalse(array.getBoolean(13))
        Assert.assertEquals(Double.MIN_VALUE.toInt(), array.getInt(13))
        Assert.assertEquals(Double.MIN_VALUE.toLong(), array.getLong(13))
        Assert.assertEquals(Double.MIN_VALUE.toFloat(), array.getFloat(13), 0.001f)
        Assert.assertEquals(Double.MIN_VALUE, array.getDouble(13), 0.001)
        Assert.assertEquals(Double.MIN_VALUE, array.getNumber(13))
        Assert.assertNull(array.getString(13))
        Assert.assertNull(array.getDate(13))
        Assert.assertNull(array.getBlob(13))
        Assert.assertNull(array.getArray(13))
        Assert.assertNull(array.getDictionary(13))

        //#14 array.addDouble(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, array.getValue(14))
        Assert.assertTrue(array.getBoolean(14))
        // ??? Fails: Assert.assertEquals(Double.MAX_VALUE.toInt(), array.getInt(14))
        // ??? Fails in Java: Assert.assertEquals(Double.MAX_VALUE.toLong(), array.getLong(14))
        Assert.assertEquals(Double.MAX_VALUE.toFloat(), array.getFloat(14), 100.0f)
        Assert.assertEquals(Double.MAX_VALUE, array.getDouble(14), 100.0)
        Assert.assertEquals(Double.MAX_VALUE, array.getNumber(14))
        Assert.assertNull(array.getString(14))
        Assert.assertNull(array.getDate(14))
        Assert.assertNull(array.getBlob(14))
        Assert.assertNull(array.getArray(14))
        Assert.assertNull(array.getDictionary(14))

        //#15 array.addNumber(null);
        Assert.assertNull(array.getValue(15))
        Assert.assertFalse(array.getBoolean(15))
        Assert.assertEquals(0, array.getInt(15))
        Assert.assertEquals(0L, array.getLong(15))
        Assert.assertEquals(0.0f, array.getFloat(15), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(15), 0.001)
        Assert.assertNull(array.getNumber(15))
        Assert.assertNull(array.getString(15))
        Assert.assertNull(array.getDate(15))
        Assert.assertNull(array.getBlob(15))
        Assert.assertNull(array.getArray(15))
        Assert.assertNull(array.getDictionary(15))

        //#16 array.addNumber(0);
        Assert.assertEquals(0L, array.getValue(16))
        Assert.assertFalse(array.getBoolean(16))
        Assert.assertEquals(0, array.getInt(16))
        Assert.assertEquals(0L, array.getLong(16))
        Assert.assertEquals(0.0f, array.getFloat(16), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(16), 0.001)
        Assert.assertEquals(0L, array.getNumber(16))
        Assert.assertNull(array.getString(16))
        Assert.assertNull(array.getDate(16))
        Assert.assertNull(array.getBlob(16))
        Assert.assertNull(array.getArray(16))
        Assert.assertNull(array.getDictionary(16))

        //#17 array.addNumber(Float.MIN_VALUE);
        Assert.assertEquals(Float.MIN_VALUE, array.getValue(17)!!.demoteToFloat())
        Assert.assertFalse(array.getBoolean(17))
        Assert.assertEquals(Float.MIN_VALUE.toInt(), array.getInt(17))
        Assert.assertEquals(Float.MIN_VALUE.toLong(), array.getLong(17))
        Assert.assertEquals(Float.MIN_VALUE, array.getFloat(17), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), array.getDouble(17), 0.001)
        Assert.assertEquals(Float.MIN_VALUE, array.getNumber(17)!!.demoteToFloat())
        Assert.assertNull(array.getString(17))
        Assert.assertNull(array.getDate(17))
        Assert.assertNull(array.getBlob(17))
        Assert.assertNull(array.getArray(17))
        Assert.assertNull(array.getDictionary(17))

        //#18 array.addNumber(Long.MIN_VALUE);
        Assert.assertEquals(Long.MIN_VALUE, array.getValue(18))
        Assert.assertFalse(array.getBoolean(18))
        Assert.assertEquals(Long.MIN_VALUE.toInt(), array.getInt(18))
        Assert.assertEquals(Long.MIN_VALUE, array.getLong(18))
        Assert.assertEquals(Long.MIN_VALUE.toFloat(), array.getFloat(18), 0.001f)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), array.getDouble(18), 0.001)
        Assert.assertEquals(Long.MIN_VALUE, array.getNumber(18))
        Assert.assertNull(array.getString(18))
        Assert.assertNull(array.getDate(18))
        Assert.assertNull(array.getBlob(18))
        Assert.assertNull(array.getArray(18))
        Assert.assertNull(array.getDictionary(18))

        //#19 array.addString(null);
        Assert.assertNull(array.getValue(19))
        Assert.assertFalse(array.getBoolean(19))
        Assert.assertEquals(0, array.getInt(19))
        Assert.assertEquals(0L, array.getLong(19))
        Assert.assertEquals(0.0f, array.getFloat(19), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(19), 0.001)
        Assert.assertNull(array.getNumber(19))
        Assert.assertNull(array.getString(19))
        Assert.assertNull(array.getDate(19))
        Assert.assertNull(array.getBlob(19))
        Assert.assertNull(array.getArray(19))
        Assert.assertNull(array.getDictionary(19))

        //#20 array.addString("Quatro");
        Assert.assertEquals("Harry", array.getValue(20))
        Assert.assertTrue(array.getBoolean(20))
        Assert.assertEquals(0, array.getInt(20))
        Assert.assertEquals(0, array.getLong(20))
        Assert.assertEquals(0.0f, array.getFloat(20), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(20), 0.001)
        Assert.assertNull(array.getNumber(20))
        Assert.assertEquals("Harry", array.getString(20))
        Assert.assertNull(array.getDate(20))
        Assert.assertNull(array.getBlob(20))
        Assert.assertNull(array.getArray(20))
        Assert.assertNull(array.getDictionary(20))

        //#21 array.addDate(null);
        Assert.assertNull(array.getValue(21))
        Assert.assertFalse(array.getBoolean(21))
        Assert.assertEquals(0, array.getInt(21))
        Assert.assertEquals(0L, array.getLong(21))
        Assert.assertEquals(0.0f, array.getFloat(21), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(21), 0.001)
        Assert.assertNull(array.getNumber(21))
        Assert.assertNull(array.getString(21))
        Assert.assertNull(array.getDate(21))
        Assert.assertNull(array.getBlob(21))
        Assert.assertNull(array.getArray(21))
        Assert.assertNull(array.getDictionary(21))

        //#22 array.addDate(JSONUtils.toDate(TEST_DATE));
        Assert.assertEquals(TEST_DATE, array.getValue(22))
        Assert.assertTrue(array.getBoolean(22))
        Assert.assertEquals(0, array.getInt(22))
        Assert.assertEquals(0L, array.getLong(22))
        Assert.assertEquals(0.0f, array.getFloat(22), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(22), 0.001)
        Assert.assertNull(array.getNumber(22))
        Assert.assertEquals(TEST_DATE, array.getString(22))
        Assert.assertEquals(JSONUtils.toDate(TEST_DATE), array.getDate(22))
        Assert.assertNull(array.getBlob(22))
        Assert.assertNull(array.getArray(22))
        Assert.assertNull(array.getDictionary(22))

        //#23 array.addArray(null);
        Assert.assertNull(array.getValue(23))
        Assert.assertFalse(array.getBoolean(23))
        Assert.assertEquals(0, array.getInt(23))
        Assert.assertEquals(0L, array.getLong(23))
        Assert.assertEquals(0.0f, array.getFloat(23), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(23), 0.001)
        Assert.assertNull(array.getNumber(23))
        Assert.assertNull(array.getString(23))
        Assert.assertNull(array.getDate(23))
        Assert.assertNull(array.getBlob(23))
        Assert.assertNull(array.getArray(23))
        Assert.assertNull(array.getDictionary(23))

        //#24 array.addArray(simpleArray);
        Assert.assertTrue(array.getValue(24) is Array)
        Assert.assertTrue(array.getBoolean(24))
        Assert.assertEquals(0, array.getInt(24).toLong())
        Assert.assertEquals(0L, array.getLong(24))
        Assert.assertEquals(0.0f, array.getFloat(24), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(24), 0.001)
        Assert.assertNull(array.getNumber(24))
        Assert.assertNull(array.getString(24))
        Assert.assertNull(array.getDate(24))
        Assert.assertNull(array.getBlob(24))
        Assert.assertTrue(array.getArray(24) is Array)
        Assert.assertNull(array.getDictionary(24))

        //#25 array.addDictionary(null);
        Assert.assertNull(array.getValue(25))
        Assert.assertFalse(array.getBoolean(25))
        Assert.assertEquals(0, array.getInt(25))
        Assert.assertEquals(0L, array.getLong(25))
        Assert.assertEquals(0.0f, array.getFloat(25), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(25), 0.001)
        Assert.assertNull(array.getNumber(25))
        Assert.assertNull(array.getString(25))
        Assert.assertNull(array.getDate(25))
        Assert.assertNull(array.getBlob(25))
        Assert.assertNull(array.getArray(25))
        Assert.assertNull(array.getDictionary(25))

        //#26 array.addDictionary(simpleDict);
        Assert.assertTrue(array.getValue(26) is Dictionary)
        Assert.assertTrue(array.getBoolean(26))
        Assert.assertEquals(0, array.getInt(26))
        Assert.assertEquals(0L, array.getLong(26))
        Assert.assertEquals(0.0f, array.getFloat(26), 0.001f)
        Assert.assertEquals(0.0, array.getDouble(26), 0.001)
        Assert.assertNull(array.getNumber(26))
        Assert.assertNull(array.getString(26))
        Assert.assertNull(array.getDate(26))
        Assert.assertNull(array.getBlob(26))
        Assert.assertNull(array.getArray(26))
        Assert.assertTrue(array.getDictionary(26) is Dictionary)
    }

    protected fun makeDict(): MutableDictionary {
        // A small array
        val simpleArray = MutableArray()
        simpleArray.addInt(54)
        simpleArray.addString("Joplin")

        // A small dictionary
        val simpleDict = MutableDictionary()
        simpleDict.setInt("sdict.1", 58)
        simpleDict.setString("sdict.2", "Winehouse")

        // Dictionary:
        val dict = MutableDictionary()
        dict.setValue("dict-1", null)
        dict.setBoolean("dict-2", true)
        dict.setBoolean("dict-3", false)
        dict.setInt("dict-4", 0)
        dict.setInt("dict-5", Int.MIN_VALUE)
        dict.setInt("dict-6", Int.MAX_VALUE)
        dict.setLong("dict-7", 0L)
        dict.setLong("dict-8", Long.MIN_VALUE)
        dict.setLong("dict-9", Long.MAX_VALUE)
        dict.setFloat("dict-10", 0.0f)
        dict.setFloat("dict-11", Float.MIN_VALUE)
        dict.setFloat("dict-12", Float.MAX_VALUE)
        dict.setDouble("dict-13", 0.0)
        dict.setDouble("dict-14", Double.MIN_VALUE)
        dict.setDouble("dict-15", Double.MAX_VALUE)
        dict.setNumber("dict-16", null)
        dict.setNumber("dict-17", 0)
        dict.setNumber("dict-18", Float.MIN_VALUE)
        dict.setNumber("dict-19", Long.MIN_VALUE)
        dict.setString("dict-20", null)
        dict.setString("dict-21", "Quatro")
        dict.setDate("dict-22", null)
        dict.setDate("dict-23", JSONUtils.toDate(TEST_DATE))
        dict.setArray("dict-24", null)
        dict.setArray("dict-25", simpleArray)
        dict.setDictionary("dict-26", null)
        dict.setDictionary("dict-27", simpleDict)
        return dict
    }

    protected fun verifyDict(jObj: JSONObject) {
        Assert.assertEquals(27, jObj.length().toLong())
        Assert.assertEquals(JSONObject.NULL, jObj["dict-1"])
        Assert.assertEquals(true, jObj["dict-2"])
        Assert.assertEquals(false, jObj["dict-3"])
        Assert.assertEquals(0, jObj["dict-4"])
        Assert.assertEquals(Int.MIN_VALUE, jObj["dict-5"])
        Assert.assertEquals(Int.MAX_VALUE, jObj["dict-6"])
        Assert.assertEquals(0, jObj["dict-7"])
        Assert.assertEquals(Long.MIN_VALUE, jObj["dict-8"])
        Assert.assertEquals(Long.MAX_VALUE, jObj["dict-9"])
        Assert.assertEquals(0.0, jObj.getDouble("dict-10"), 0.001)
        Assert.assertEquals(Float.MIN_VALUE, jObj.getDouble("dict-11").toFloat(), 0.001F)
        Assert.assertEquals(Float.MAX_VALUE, jObj.getDouble("dict-12").toFloat(), 100.0f)
        Assert.assertEquals(0.0, jObj.getDouble("dict-13"), 0.001)
        Assert.assertEquals(Double.MIN_VALUE, jObj.getDouble("dict-14"), 0.001)
        Assert.assertEquals(Double.MAX_VALUE, jObj.getDouble("dict-15"), 1.0)
        Assert.assertEquals(JSONObject.NULL, jObj["dict-16"])
        Assert.assertEquals(0, jObj.getLong("dict-17"))
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), jObj.getDouble("dict-18"), 0.001)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), jObj.getLong("dict-19").toDouble(), 0.001)
        Assert.assertEquals(JSONObject.NULL, jObj["dict-20"])
        Assert.assertEquals("Quatro", jObj["dict-21"])
        Assert.assertEquals(JSONObject.NULL, jObj["dict-22"])
        Assert.assertEquals(TEST_DATE, jObj["dict-23"])
        Assert.assertEquals(JSONObject.NULL, jObj["dict-24"])
        Assert.assertEquals(JSONArray::class.java, jObj["dict-25"].javaClass)
        Assert.assertEquals(JSONObject.NULL, jObj["dict-26"])
        Assert.assertEquals(JSONObject::class.java, jObj["dict-27"].javaClass)
    }

    protected fun verifyDict(d: DictionaryInterface?, fromJSON: Boolean = false) {
        Assert.assertNotNull(d)
        val dict = d!!

        Assert.assertEquals(27, dict.count().toLong())

        //#0 dict.setValue(null);
        Assert.assertNull(dict.getValue("dict-1"))
        Assert.assertFalse(dict.getBoolean("dict-1"))
        Assert.assertEquals(0, dict.getInt("dict-1"))
        Assert.assertEquals(0L, dict.getLong("dict-1"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-1"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-1"), 0.001)
        Assert.assertNull(dict.getNumber("dict-1"))
        Assert.assertNull(dict.getString("dict-1"))
        Assert.assertNull(dict.getDate("dict-1"))
        Assert.assertNull(dict.getBlob("dict-1"))
        Assert.assertNull(dict.getArray("dict-1"))
        Assert.assertNull(dict.getDictionary("dict-1"))

        //#1 dict.setBoolean(true);
        Assert.assertEquals(true, dict.getValue("dict-2"))
        Assert.assertTrue(dict.getBoolean("dict-2"))
        Assert.assertEquals(1, dict.getInt("dict-2"))
        Assert.assertEquals(1L, dict.getLong("dict-2"))
        Assert.assertEquals(1.0f, dict.getFloat("dict-2"), 0.001f)
        Assert.assertEquals(1.0, dict.getDouble("dict-2"), 0.001)
        Assert.assertEquals(1, dict.getNumber("dict-2"))
        Assert.assertNull(dict.getString("dict-2"))
        Assert.assertNull(dict.getDate("dict-2"))
        Assert.assertNull(dict.getBlob("dict-2"))
        Assert.assertNull(dict.getArray("dict-2"))
        Assert.assertNull(dict.getDictionary("dict-2"))

        //#2 dict.setBoolean(false);
        Assert.assertEquals(false, dict.getValue("dict-3"))
        Assert.assertFalse(dict.getBoolean("dict-3"))
        Assert.assertEquals(0, dict.getInt("dict-3"))
        Assert.assertEquals(0L, dict.getLong("dict-3"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-3"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-3"), 0.001)
        Assert.assertEquals(0, dict.getNumber("dict-3"))
        Assert.assertNull(dict.getString("dict-3"))
        Assert.assertNull(dict.getDate("dict-3"))
        Assert.assertNull(dict.getBlob("dict-3"))
        Assert.assertNull(dict.getArray("dict-3"))
        Assert.assertNull(dict.getDictionary("dict-3"))

        //#3 dict.setInt(0);
        Assert.assertEquals(0L, dict.getValue("dict-4"))
        Assert.assertFalse(dict.getBoolean("dict-4"))
        Assert.assertEquals(0, dict.getInt("dict-4"))
        Assert.assertEquals(0L, dict.getLong("dict-4"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-4"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-4"), 0.001)
        Assert.assertEquals(0L, dict.getNumber("dict-4"))
        Assert.assertNull(dict.getString("dict-4"))
        Assert.assertNull(dict.getDate("dict-4"))
        Assert.assertNull(dict.getBlob("dict-4"))
        Assert.assertNull(dict.getArray("dict-4"))
        Assert.assertNull(dict.getDictionary("dict-4"))

        //#4 dict.setInt(Integer.MIN_VALUE);
        Assert.assertEquals(Int.MIN_VALUE, dict.getValue("dict-5")!!.demoteToInt())
        Assert.assertTrue(dict.getBoolean("dict-5"))
        Assert.assertEquals(Int.MIN_VALUE, dict.getInt("dict-5"))
        Assert.assertEquals(Int.MIN_VALUE.toLong(), dict.getLong("dict-5"))
        Assert.assertEquals(Int.MIN_VALUE.toFloat(), dict.getFloat("dict-5"), 0.001f)
        Assert.assertEquals(Int.MIN_VALUE.toDouble(), dict.getDouble("dict-5"), 0.001)
        Assert.assertEquals(Int.MIN_VALUE, dict.getNumber("dict-5")!!.demoteToInt())
        Assert.assertNull(dict.getString("dict-5"))
        Assert.assertNull(dict.getDate("dict-5"))
        Assert.assertNull(dict.getBlob("dict-5"))
        Assert.assertNull(dict.getArray("dict-5"))
        Assert.assertNull(dict.getDictionary("dict-5"))

        //#5 dict.setInt(Integer.MAX_VALUE);
        Assert.assertEquals(Int.MAX_VALUE, dict.getValue("dict-6")!!.demoteToInt())
        Assert.assertTrue(dict.getBoolean("dict-6"))
        Assert.assertEquals(Int.MAX_VALUE, dict.getInt("dict-6"))
        Assert.assertEquals(Int.MAX_VALUE.toLong(), dict.getLong("dict-6"))
        Assert.assertEquals(Int.MAX_VALUE.toFloat(), dict.getFloat("dict-6"), 100.0f)
        Assert.assertEquals(Int.MAX_VALUE.toDouble(), dict.getDouble("dict-6"), 100.0)
        Assert.assertEquals(Int.MAX_VALUE, dict.getNumber("dict-6")!!.demoteToInt())
        Assert.assertNull(dict.getString("dict-6"))
        Assert.assertNull(dict.getDate("dict-6"))
        Assert.assertNull(dict.getBlob("dict-6"))
        Assert.assertNull(dict.getArray("dict-6"))
        Assert.assertNull(dict.getDictionary("dict-6"))

        //#6 dict.setLong(0L);
        Assert.assertEquals(0L, dict.getValue("dict-7"))
        Assert.assertFalse(dict.getBoolean("dict-7"))
        Assert.assertEquals(0, dict.getInt("dict-7"))
        Assert.assertEquals(0L, dict.getLong("dict-7"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-7"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-7"), 0.001)
        Assert.assertEquals(0L, dict.getNumber("dict-7"))
        Assert.assertNull(dict.getString("dict-7"))
        Assert.assertNull(dict.getDate("dict-7"))
        Assert.assertNull(dict.getBlob("dict-7"))
        Assert.assertNull(dict.getArray("dict-7"))
        Assert.assertNull(dict.getDictionary("dict-7"))

        //#7 dict.setLong(Long.MIN_VALUE);
        Assert.assertEquals(Long.MIN_VALUE, dict.getValue("dict-8"))
        Assert.assertFalse(dict.getBoolean("dict-8"))
        Assert.assertEquals(Long.MIN_VALUE.toInt(), dict.getInt("dict-8"))
        Assert.assertEquals(Long.MIN_VALUE, dict.getLong("dict-8"))
        Assert.assertEquals(Long.MIN_VALUE.toFloat(), dict.getFloat("dict-8"), 0.001f)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), dict.getDouble("dict-8"), 0.001)
        Assert.assertEquals(Long.MIN_VALUE, dict.getNumber("dict-8"))
        Assert.assertNull(dict.getString("dict-8"))
        Assert.assertNull(dict.getDate("dict-8"))
        Assert.assertNull(dict.getBlob("dict-8"))
        Assert.assertNull(dict.getArray("dict-8"))
        Assert.assertNull(dict.getDictionary("dict-8"))

        //#8 dict.setLong(Long.MAX_VALUE);
        Assert.assertEquals(Long.MAX_VALUE, dict.getValue("dict-9"))
        Assert.assertTrue(dict.getBoolean("dict-9"))
        Assert.assertEquals(0, dict.getInt("dict-9"))
        Assert.assertEquals(Long.MAX_VALUE, dict.getLong("dict-9"))
        Assert.assertEquals(Long.MAX_VALUE.toFloat(), dict.getFloat("dict-9"), 100.0f)
        Assert.assertEquals(Long.MAX_VALUE.toDouble(), dict.getDouble("dict-9"), 100.0)
        Assert.assertEquals(Long.MAX_VALUE, dict.getNumber("dict-9"))
        Assert.assertNull(dict.getString("dict-9"))
        Assert.assertNull(dict.getDate("dict-9"))
        Assert.assertNull(dict.getBlob("dict-9"))
        Assert.assertNull(dict.getArray("dict-9"))
        Assert.assertNull(dict.getDictionary("dict-9"))

        //#9 dict.setFloat(0.0F);
        if (fromJSON) {
            Assert.assertEquals(0.0, dict.getValue("dict-10"))
        } else {
            Assert.assertEquals(0.0f, dict.getValue("dict-10"))
        }
        Assert.assertFalse(dict.getBoolean("dict-10"))
        Assert.assertEquals(0, dict.getInt("dict-10"))
        Assert.assertEquals(0L, dict.getLong("dict-10"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-10"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-10"), 0.001)
        if (fromJSON) {
            Assert.assertEquals(0.0, dict.getValue("dict-10"))
        } else {
            Assert.assertEquals(0.0f, dict.getValue("dict-10"))
        }
        Assert.assertNull(dict.getString("dict-10"))
        Assert.assertNull(dict.getDate("dict-10"))
        Assert.assertNull(dict.getBlob("dict-10"))
        Assert.assertNull(dict.getArray("dict-10"))
        Assert.assertNull(dict.getDictionary("dict-10"))

        //#10 dict.setFloat(Float.MIN_VALUE);
        Assert.assertEquals(Float.MIN_VALUE, dict.getValue("dict-11")!!.demoteToFloat())
        Assert.assertFalse(dict.getBoolean("dict-11"))
        Assert.assertEquals(Float.MIN_VALUE.toInt(), dict.getInt("dict-11"))
        Assert.assertEquals(Float.MIN_VALUE.toLong(), dict.getLong("dict-11"))
        Assert.assertEquals(Float.MIN_VALUE, dict.getFloat("dict-11"), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), dict.getDouble("dict-11"), 0.001)
        Assert.assertEquals(Float.MIN_VALUE, dict.getNumber("dict-11")!!.demoteToFloat())
        Assert.assertNull(dict.getString("dict-11"))
        Assert.assertNull(dict.getDate("dict-11"))
        Assert.assertNull(dict.getBlob("dict-11"))
        Assert.assertNull(dict.getArray("dict-11"))
        Assert.assertNull(dict.getDictionary("dict-11"))

        //#11 dict.setFloat(Float.MAX_VALUE);
        Assert.assertEquals(Float.MAX_VALUE, dict.getValue("dict-12")!!.demoteToFloat())
        Assert.assertTrue(dict.getBoolean("dict-12"))
        // ??? Fails: Assert.assertEquals(Float.MAX_VALUE.toInt(), dict.getInt("dict-12"))
        // ??? Fails in Java: Assert.assertEquals(Float.MAX_VALUE.toLong(), dict.getLong("dict-12"))
        Assert.assertEquals(Float.MAX_VALUE, dict.getFloat("dict-12"), 1.0E32F)
        Assert.assertEquals(Float.MAX_VALUE.toDouble(), dict.getDouble("dict-12"), 1.0E32)
        Assert.assertEquals(Float.MAX_VALUE, dict.getNumber("dict-12")!!.demoteToFloat())
        Assert.assertNull(dict.getString("dict-12"))
        Assert.assertNull(dict.getDate("dict-12"))
        Assert.assertNull(dict.getBlob("dict-12"))
        Assert.assertNull(dict.getArray("dict-12"))
        Assert.assertNull(dict.getDictionary("dict-12"))

        //#12 dict.setDouble(0.0);
        Assert.assertEquals(0.0, dict.getValue("dict-13")!!.promoteToDouble(), 0.001)
        Assert.assertFalse(dict.getBoolean("dict-13"))
        Assert.assertEquals(0, dict.getInt("dict-13"))
        Assert.assertEquals(0L, dict.getLong("dict-13"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-13"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-13"), 0.001)
        Assert.assertEquals(0.0, dict.getNumber("dict-13")!!.promoteToDouble(), 0.001)
        Assert.assertNull(dict.getString("dict-13"))
        Assert.assertNull(dict.getDate("dict-13"))
        Assert.assertNull(dict.getBlob("dict-13"))
        Assert.assertNull(dict.getArray("dict-13"))
        Assert.assertNull(dict.getDictionary("dict-13"))

        //#13 dict.setDouble(Double.MIN_VALUE);
        Assert.assertEquals(Double.MIN_VALUE, dict.getValue("dict-14")!!.promoteToDouble(), 0.001)
        Assert.assertFalse(dict.getBoolean("dict-14"))
        Assert.assertEquals(Double.MIN_VALUE.toInt(), dict.getInt("dict-14"))
        Assert.assertEquals(Double.MIN_VALUE.toLong(), dict.getLong("dict-14"))
        Assert.assertEquals(Double.MIN_VALUE.toFloat(), dict.getFloat("dict-14"), 0.001f)
        Assert.assertEquals(Double.MIN_VALUE, dict.getDouble("dict-14"), 0.001)
        Assert.assertEquals(Double.MIN_VALUE, dict.getNumber("dict-14")!!.promoteToDouble(), 0.001)
        Assert.assertNull(dict.getString("dict-14"))
        Assert.assertNull(dict.getDate("dict-14"))
        Assert.assertNull(dict.getBlob("dict-14"))
        Assert.assertNull(dict.getArray("dict-14"))
        Assert.assertNull(dict.getDictionary("dict-14"))

        //#14 dict.setDouble(Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, dict.getValue("dict-15")!!.promoteToDouble(), 0.001)
        Assert.assertTrue(dict.getBoolean("dict-15"))
        // ??? Fails: Assert.assertEquals(Double.MAX_VALUE.toInt(), dict.getInt("dict-15"))
        // ??? Fails in Java: Assert.assertEquals(Double.MAX_VALUE.toLong(), dict.getLong("dict-15"))
        Assert.assertEquals(Double.MAX_VALUE.toFloat(), dict.getFloat("dict-15"), 100.0f)
        Assert.assertEquals(Double.MAX_VALUE, dict.getDouble("dict-15"), 100.0)
        Assert.assertEquals(Double.MAX_VALUE, dict.getNumber("dict-15")!!.promoteToDouble(), 0.001)
        Assert.assertNull(dict.getString("dict-15"))
        Assert.assertNull(dict.getDate("dict-15"))
        Assert.assertNull(dict.getBlob("dict-15"))
        Assert.assertNull(dict.getArray("dict-15"))
        Assert.assertNull(dict.getDictionary("dict-15"))

        //#15 dict.setNumber(null);
        Assert.assertNull(dict.getValue("dict-16"))
        Assert.assertFalse(dict.getBoolean("dict-16"))
        Assert.assertEquals(0, dict.getInt("dict-16"))
        Assert.assertEquals(0L, dict.getLong("dict-16"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-16"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-16"), 0.001)
        Assert.assertNull(dict.getNumber("dict-16"))
        Assert.assertNull(dict.getString("dict-16"))
        Assert.assertNull(dict.getDate("dict-16"))
        Assert.assertNull(dict.getBlob("dict-16"))
        Assert.assertNull(dict.getArray("dict-16"))
        Assert.assertNull(dict.getDictionary("dict-16"))

        //#16 dict.setNumber(0);
        Assert.assertEquals(0L, dict.getValue("dict-17"))
        Assert.assertFalse(dict.getBoolean("dict-17"))
        Assert.assertEquals(0, dict.getInt("dict-17").toLong())
        Assert.assertEquals(0L, dict.getLong("dict-17"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-17"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-17"), 0.001)
        Assert.assertEquals(0L, dict.getNumber("dict-17"))
        Assert.assertNull(dict.getString("dict-17"))
        Assert.assertNull(dict.getDate("dict-17"))
        Assert.assertNull(dict.getBlob("dict-17"))
        Assert.assertNull(dict.getArray("dict-17"))
        Assert.assertNull(dict.getDictionary("dict-17"))

        //#17 dict.setNumber(Float.MIN_VALUE);
        Assert.assertEquals(Float.MIN_VALUE, dict.getValue("dict-18")!!.demoteToFloat())
        Assert.assertFalse(dict.getBoolean("dict-18"))
        Assert.assertEquals(Float.MIN_VALUE.toInt(), dict.getInt("dict-18"))
        Assert.assertEquals(Float.MIN_VALUE.toLong(), dict.getLong("dict-18"))
        Assert.assertEquals(Float.MIN_VALUE, dict.getFloat("dict-18"), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), dict.getDouble("dict-18"), 0.001)
        Assert.assertEquals(Float.MIN_VALUE, dict.getNumber("dict-18")!!.demoteToFloat())
        Assert.assertNull(dict.getString("dict-18"))
        Assert.assertNull(dict.getDate("dict-18"))
        Assert.assertNull(dict.getBlob("dict-18"))
        Assert.assertNull(dict.getArray("dict-18"))
        Assert.assertNull(dict.getDictionary("dict-18"))

        //#18 dict.setNumber(Long.MIN_VALUE);
        Assert.assertEquals(Long.MIN_VALUE, dict.getValue("dict-19"))
        Assert.assertFalse(dict.getBoolean("dict-19"))
        Assert.assertEquals(Long.MIN_VALUE.toInt(), dict.getInt("dict-19"))
        Assert.assertEquals(Long.MIN_VALUE, dict.getLong("dict-19"))
        Assert.assertEquals(Long.MIN_VALUE.toFloat(), dict.getFloat("dict-19"), 0.001f)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), dict.getDouble("dict-19"), 0.001)
        Assert.assertEquals(Long.MIN_VALUE, dict.getNumber("dict-19"))
        Assert.assertNull(dict.getString("dict-19"))
        Assert.assertNull(dict.getDate("dict-19"))
        Assert.assertNull(dict.getBlob("dict-19"))
        Assert.assertNull(dict.getArray("dict-19"))
        Assert.assertNull(dict.getDictionary("dict-19"))

        //#19 dict.setString(null);
        Assert.assertNull(dict.getValue("dict-20"))
        Assert.assertFalse(dict.getBoolean("dict-20"))
        Assert.assertEquals(0, dict.getInt("dict-20"))
        Assert.assertEquals(0L, dict.getLong("dict-20"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-20"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-20"), 0.001)
        Assert.assertNull(dict.getNumber("dict-20"))
        Assert.assertNull(dict.getString("dict-20"))
        Assert.assertNull(dict.getDate("dict-20"))
        Assert.assertNull(dict.getBlob("dict-20"))
        Assert.assertNull(dict.getArray("dict-20"))
        Assert.assertNull(dict.getDictionary("dict-20"))

        //#20 dict.setString("Quatro");
        Assert.assertEquals("Quatro", dict.getValue("dict-21"))
        Assert.assertTrue(dict.getBoolean("dict-21"))
        Assert.assertEquals(0, dict.getInt("dict-21"))
        Assert.assertEquals(0, dict.getLong("dict-21"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-21"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-21"), 0.001)
        Assert.assertNull(dict.getNumber("dict-21"))
        Assert.assertEquals("Quatro", dict.getString("dict-21"))
        Assert.assertNull(dict.getDate("dict-21"))
        Assert.assertNull(dict.getBlob("dict-21"))
        Assert.assertNull(dict.getArray("dict-21"))
        Assert.assertNull(dict.getDictionary("dict-21"))

        //#21 dict.setDate(null);
        Assert.assertNull(dict.getValue("dict-22"))
        Assert.assertFalse(dict.getBoolean("dict-22"))
        Assert.assertEquals(0, dict.getInt("dict-22"))
        Assert.assertEquals(0L, dict.getLong("dict-22"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-22"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-22"), 0.001)
        Assert.assertNull(dict.getNumber("dict-22"))
        Assert.assertNull(dict.getString("dict-22"))
        Assert.assertNull(dict.getDate("dict-22"))
        Assert.assertNull(dict.getBlob("dict-22"))
        Assert.assertNull(dict.getArray("dict-22"))
        Assert.assertNull(dict.getDictionary("dict-22"))

        //#22 dict.setDate(JSONUtils.toDate(TEST_DATE));
        Assert.assertEquals(TEST_DATE, dict.getValue("dict-23"))
        Assert.assertTrue(dict.getBoolean("dict-23"))
        Assert.assertEquals(0, dict.getInt("dict-23"))
        Assert.assertEquals(0L, dict.getLong("dict-23"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-23"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-23"), 0.001)
        Assert.assertNull(dict.getNumber("dict-23"))
        Assert.assertEquals(TEST_DATE, dict.getString("dict-23"))
        Assert.assertEquals(JSONUtils.toDate(TEST_DATE), dict.getDate("dict-23"))
        Assert.assertNull(dict.getBlob("dict-23"))
        Assert.assertNull(dict.getArray("dict-23"))
        Assert.assertNull(dict.getDictionary("dict-23"))

        //#23 dict.setArray(null);
        Assert.assertNull(dict.getValue("dict-24"))
        Assert.assertFalse(dict.getBoolean("dict-24"))
        Assert.assertEquals(0, dict.getInt("dict-24"))
        Assert.assertEquals(0L, dict.getLong("dict-24"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-24"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-24"), 0.001)
        Assert.assertNull(dict.getNumber("dict-24"))
        Assert.assertNull(dict.getString("dict-24"))
        Assert.assertNull(dict.getDate("dict-24"))
        Assert.assertNull(dict.getBlob("dict-24"))
        Assert.assertNull(dict.getArray("dict-24"))
        Assert.assertNull(dict.getDictionary("dict-24"))

        //#24 dict.setArray(simpleArray);
        Assert.assertTrue(dict.getValue("dict-25") is Array)
        Assert.assertTrue(dict.getBoolean("dict-25"))
        Assert.assertEquals(0, dict.getInt("dict-25"))
        Assert.assertEquals(0L, dict.getLong("dict-25"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-25"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-25"), 0.001)
        Assert.assertNull(dict.getNumber("dict-25"))
        Assert.assertNull(dict.getString("dict-25"))
        Assert.assertNull(dict.getDate("dict-25"))
        Assert.assertNull(dict.getBlob("dict-25"))
        Assert.assertTrue(dict.getArray("dict-25") is Array)
        Assert.assertNull(dict.getDictionary("dict-25"))

        //#25 dict.setDictionary(null);
        Assert.assertNull(dict.getValue("dict-26"))
        Assert.assertFalse(dict.getBoolean("dict-26"))
        Assert.assertEquals(0, dict.getInt("dict-26"))
        Assert.assertEquals(0L, dict.getLong("dict-26"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-26"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-26"), 0.001)
        Assert.assertNull(dict.getNumber("dict-26"))
        Assert.assertNull(dict.getString("dict-26"))
        Assert.assertNull(dict.getDate("dict-26"))
        Assert.assertNull(dict.getBlob("dict-26"))
        Assert.assertNull(dict.getArray("dict-26"))
        Assert.assertNull(dict.getDictionary("dict-26"))

        //#26 dict.setDictionary(simpleDict);
        Assert.assertTrue(dict.getValue("dict-27") is Dictionary)
        Assert.assertTrue(dict.getBoolean("dict-27"))
        Assert.assertEquals(0, dict.getInt("dict-27"))
        Assert.assertEquals(0L, dict.getLong("dict-27"))
        Assert.assertEquals(0.0f, dict.getFloat("dict-27"), 0.001f)
        Assert.assertEquals(0.0, dict.getDouble("dict-27"), 0.001)
        Assert.assertNull(dict.getNumber("dict-27"))
        Assert.assertNull(dict.getString("dict-27"))
        Assert.assertNull(dict.getDate("dict-27"))
        Assert.assertNull(dict.getBlob("dict-27"))
        Assert.assertNull(dict.getArray("dict-27"))
        Assert.assertTrue(dict.getDictionary("dict-27") is Dictionary)
    }

    protected fun makeBlob(): Blob {
        return Blob("text/plain", BLOB_CONTENT.toByteArray(StandardCharsets.UTF_8))
    }

    protected fun verifyBlob(jBlob: JSONObject) {
        Assert.assertEquals(4, jBlob.length().toLong())
        Assert.assertEquals(Blob.TYPE_BLOB, jBlob[Blob.META_PROP_TYPE])
        Assert.assertEquals("sha1-C+QguVamTgLjyDQ9RzRtyCv6x60=", jBlob[Blob.PROP_DIGEST])
        Assert.assertEquals(59, jBlob.getLong(Blob.PROP_LENGTH))
        Assert.assertEquals("text/plain", jBlob[Blob.PROP_CONTENT_TYPE])
    }

    protected fun verifyBlob(blob: Blob?) {
        Assert.assertNotNull(blob)
        Assert.assertEquals("sha1-C+QguVamTgLjyDQ9RzRtyCv6x60=", blob!!.digest())
        Assert.assertEquals(59, blob.length())
        Assert.assertEquals("text/plain", blob.contentType)
        Assert.assertEquals(BLOB_CONTENT, String(blob.content!!))
    }

    protected fun makeDocument(): MutableDocument {
        // Dictionary:
        val mDoc = MutableDocument()
        mDoc.setValue("doc-1", null)
        mDoc.setBoolean("doc-2", true)
        mDoc.setBoolean("doc-3", false)
        mDoc.setInt("doc-4", 0)
        mDoc.setInt("doc-5", Int.MIN_VALUE)
        mDoc.setInt("doc-6", Int.MAX_VALUE)
        mDoc.setLong("doc-7", 0L)
        mDoc.setLong("doc-8", Long.MIN_VALUE)
        mDoc.setLong("doc-9", Long.MAX_VALUE)
        mDoc.setFloat("doc-10", 0.0f)
        mDoc.setFloat("doc-11", Float.MIN_VALUE)
        mDoc.setFloat("doc-12", Float.MAX_VALUE)
        mDoc.setDouble("doc-13", 0.0)
        mDoc.setDouble("doc-14", Double.MIN_VALUE)
        mDoc.setDouble("doc-15", Double.MAX_VALUE)
        mDoc.setNumber("doc-16", null)
        mDoc.setNumber("doc-17", 0)
        mDoc.setNumber("doc-18", Float.MIN_VALUE)
        mDoc.setNumber("doc-19", Long.MIN_VALUE)
        mDoc.setString("doc-20", null)
        mDoc.setString("doc-21", "Jett")
        mDoc.setDate("doc-22", null)
        mDoc.setDate("doc-23", JSONUtils.toDate(TEST_DATE))
        mDoc.setArray("doc-24", null)
        mDoc.setArray("doc-25", makeArray())
        mDoc.setDictionary("doc-26", null)
        mDoc.setDictionary("doc-27", makeDict())
        mDoc.setBlob("doc-28", null)
        mDoc.setBlob("doc-29", makeBlob())
        return mDoc
    }

    protected fun verifyDocument(jObj: JSONObject) {
        Assert.assertEquals(29, jObj.length().toLong())

        Assert.assertEquals(JSONObject.NULL, jObj["doc-1"])

        Assert.assertEquals(true, jObj["doc-2"])
        Assert.assertEquals(false, jObj["doc-3"])

        Assert.assertEquals(0, jObj["doc-4"])
        Assert.assertEquals(Int.MIN_VALUE, jObj["doc-5"])
        Assert.assertEquals(Int.MAX_VALUE, jObj["doc-6"])

        Assert.assertEquals(0, jObj["doc-7"])
        Assert.assertEquals(Long.MIN_VALUE, jObj["doc-8"])
        Assert.assertEquals(Long.MAX_VALUE, jObj["doc-9"])

        Assert.assertEquals(0.0f, jObj.getDouble("doc-10").toFloat(), 0.001F)
        Assert.assertEquals(Float.MIN_VALUE, jObj.getDouble("doc-11").toFloat(), 0.001F)
        Assert.assertEquals(Float.MAX_VALUE, jObj.getDouble("doc-12").toFloat(), 100.0F)

        Assert.assertEquals(0.0, jObj.getDouble("doc-13"), 0.001)
        Assert.assertEquals(Double.MIN_VALUE, jObj.getDouble("doc-14"), 0.001)
        Assert.assertEquals(Double.MAX_VALUE, jObj.getDouble("doc-15"), 1.0)

        Assert.assertEquals(JSONObject.NULL, jObj["doc-16"])
        Assert.assertEquals(0L, jObj.getLong("doc-17"))
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), jObj.getDouble("doc-18"), 0.001)
        Assert.assertEquals(Long.MIN_VALUE, jObj.getLong("doc-19"))

        Assert.assertEquals(JSONObject.NULL, jObj["doc-20"])
        Assert.assertEquals("Jett", jObj["doc-21"])

        Assert.assertEquals(JSONObject.NULL, jObj["doc-22"])
        Assert.assertEquals(TEST_DATE, jObj["doc-23"])

        Assert.assertEquals(JSONObject.NULL, jObj["doc-24"])
        verifyArray(jObj["doc-25"] as JSONArray)

        Assert.assertEquals(JSONObject.NULL, jObj["doc-26"])
        verifyDict(jObj["doc-27"] as JSONObject)

        Assert.assertEquals(JSONObject.NULL, jObj["doc-28"])
        verifyBlob(jObj.getJSONObject("doc-29"))
    }

    protected fun verifyDocument(doc: DictionaryInterface, fromJSON: Boolean = false) {
        Assert.assertEquals(29, doc.count().toLong())

        //#0 doc.setValue(null);
        Assert.assertNull(doc.getValue("doc-1"))
        Assert.assertFalse(doc.getBoolean("doc-1"))
        Assert.assertEquals(0, doc.getInt("doc-1").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-1"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-1"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-1"), 0.001)
        Assert.assertNull(doc.getNumber("doc-1"))
        Assert.assertNull(doc.getString("doc-1"))
        Assert.assertNull(doc.getDate("doc-1"))
        Assert.assertNull(doc.getBlob("doc-1"))
        Assert.assertNull(doc.getArray("doc-1"))
        Assert.assertNull(doc.getDictionary("doc-1"))

        //#1 doc.setBoolean(true);
        Assert.assertEquals(true, doc.getValue("doc-2"))
        Assert.assertTrue(doc.getBoolean("doc-2"))
        Assert.assertEquals(1, doc.getInt("doc-2").toLong())
        Assert.assertEquals(1L, doc.getLong("doc-2"))
        Assert.assertEquals(1.0f, doc.getFloat("doc-2"), 0.001f)
        Assert.assertEquals(1.0, doc.getDouble("doc-2"), 0.001)
        Assert.assertEquals(1, doc.getNumber("doc-2"))
        Assert.assertNull(doc.getString("doc-2"))
        Assert.assertNull(doc.getDate("doc-2"))
        Assert.assertNull(doc.getBlob("doc-2"))
        Assert.assertNull(doc.getArray("doc-2"))
        Assert.assertNull(doc.getDictionary("doc-2"))

        //#2 doc.setBoolean(false);
        Assert.assertEquals(false, doc.getValue("doc-3"))
        Assert.assertFalse(doc.getBoolean("doc-3"))
        Assert.assertEquals(0, doc.getInt("doc-3").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-3"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-3"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-3"), 0.001)
        Assert.assertEquals(0, doc.getNumber("doc-3"))
        Assert.assertNull(doc.getString("doc-3"))
        Assert.assertNull(doc.getDate("doc-3"))
        Assert.assertNull(doc.getBlob("doc-3"))
        Assert.assertNull(doc.getArray("doc-3"))
        Assert.assertNull(doc.getDictionary("doc-3"))

        //#3 doc.setInt(0);
        Assert.assertEquals(0L, doc.getValue("doc-4"))
        Assert.assertFalse(doc.getBoolean("doc-4"))
        Assert.assertEquals(0, doc.getInt("doc-4").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-4"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-4"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-4"), 0.001)
        Assert.assertEquals(0L, doc.getNumber("doc-4"))
        Assert.assertNull(doc.getString("doc-4"))
        Assert.assertNull(doc.getDate("doc-4"))
        Assert.assertNull(doc.getBlob("doc-4"))
        Assert.assertNull(doc.getArray("doc-4"))
        Assert.assertNull(doc.getDictionary("doc-4"))

        //#4 doc.setInt(Integer.MIN_VALUE);
        Assert.assertEquals(Int.MIN_VALUE.toLong(), doc.getValue("doc-5"))
        Assert.assertTrue(doc.getBoolean("doc-5"))
        Assert.assertEquals(Int.MIN_VALUE, doc.getInt("doc-5"))
        Assert.assertEquals(Int.MIN_VALUE.toLong(), doc.getLong("doc-5"))
        Assert.assertEquals(Int.MIN_VALUE.toFloat(), doc.getFloat("doc-5"), 0.001f)
        Assert.assertEquals(Int.MIN_VALUE.toDouble(), doc.getDouble("doc-5"), 0.001)
        Assert.assertEquals(Int.MIN_VALUE.toLong(), doc.getNumber("doc-5"))
        Assert.assertNull(doc.getString("doc-5"))
        Assert.assertNull(doc.getDate("doc-5"))
        Assert.assertNull(doc.getBlob("doc-5"))
        Assert.assertNull(doc.getArray("doc-5"))
        Assert.assertNull(doc.getDictionary("doc-5"))

        //#5 doc.setInt(Integer.MAX_VALUE);
        Assert.assertEquals(Int.MAX_VALUE.toLong(), doc.getValue("doc-6"))
        Assert.assertTrue(doc.getBoolean("doc-6"))
        Assert.assertEquals(Int.MAX_VALUE, doc.getInt("doc-6"))
        Assert.assertEquals(Int.MAX_VALUE.toLong(), doc.getLong("doc-6"))
        Assert.assertEquals(Int.MAX_VALUE.toFloat(), doc.getFloat("doc-6"), 100.0f)
        Assert.assertEquals(Int.MAX_VALUE.toDouble(), doc.getDouble("doc-6"), 100.0)
        Assert.assertEquals(Int.MAX_VALUE.toLong(), doc.getNumber("doc-6"))
        Assert.assertNull(doc.getString("doc-6"))
        Assert.assertNull(doc.getDate("doc-6"))
        Assert.assertNull(doc.getBlob("doc-6"))
        Assert.assertNull(doc.getArray("doc-6"))
        Assert.assertNull(doc.getDictionary("doc-6"))

        //#6 doc.setLong(0L);
        Assert.assertEquals(java.lang.Long.valueOf(0L), doc.getValue("doc-7"))
        Assert.assertFalse(doc.getBoolean("doc-7"))
        Assert.assertEquals(0, doc.getInt("doc-7").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-7"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-7"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-7"), 0.001)
        Assert.assertEquals(java.lang.Long.valueOf(0L), doc.getNumber("doc-7"))
        Assert.assertNull(doc.getString("doc-7"))
        Assert.assertNull(doc.getDate("doc-7"))
        Assert.assertNull(doc.getBlob("doc-7"))
        Assert.assertNull(doc.getArray("doc-7"))
        Assert.assertNull(doc.getDictionary("doc-7"))

        //#7 doc.setLong(Long.MIN_VALUE);
        Assert.assertEquals(java.lang.Long.valueOf(Long.MIN_VALUE), doc.getValue("doc-8"))
        // ??? Value differs for Documents and Results: Assert.assertTrue(doc.getBoolean("doc-8"));
        Assert.assertEquals(java.lang.Long.valueOf(Long.MIN_VALUE).toInt().toLong(), doc.getInt("doc-8").toLong())
        Assert.assertEquals(Long.MIN_VALUE, doc.getLong("doc-8"))
        Assert.assertEquals(Long.MIN_VALUE.toFloat(), doc.getFloat("doc-8"), 0.001f)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), doc.getDouble("doc-8"), 0.001)
        Assert.assertEquals(java.lang.Long.valueOf(Long.MIN_VALUE), doc.getNumber("doc-8"))
        Assert.assertNull(doc.getString("doc-8"))
        Assert.assertNull(doc.getDate("doc-8"))
        Assert.assertNull(doc.getBlob("doc-8"))
        Assert.assertNull(doc.getArray("doc-8"))
        Assert.assertNull(doc.getDictionary("doc-8"))

        //#8 doc.setLong(Long.MAX_VALUE);
        Assert.assertEquals(java.lang.Long.valueOf(Long.MAX_VALUE), doc.getValue("doc-9"))
        Assert.assertTrue(doc.getBoolean("doc-9"))
        Assert.assertEquals(0, doc.getInt("doc-9").toLong())
        Assert.assertEquals(Long.MAX_VALUE, doc.getLong("doc-9"))
        Assert.assertEquals(Long.MAX_VALUE.toFloat(), doc.getFloat("doc-9"), 100.0f)
        Assert.assertEquals(Long.MAX_VALUE.toDouble(), doc.getDouble("doc-9"), 100.0)
        Assert.assertEquals(java.lang.Long.valueOf(Long.MAX_VALUE), doc.getNumber("doc-9"))
        Assert.assertNull(doc.getString("doc-9"))
        Assert.assertNull(doc.getDate("doc-9"))
        Assert.assertNull(doc.getBlob("doc-9"))
        Assert.assertNull(doc.getArray("doc-9"))
        Assert.assertNull(doc.getDictionary("doc-9"))

        //#9 doc.setFloat(0.0F);
        Assert.assertEquals(java.lang.Double.valueOf(0.0), doc.getValue("doc-10"))
        Assert.assertFalse(doc.getBoolean("doc-10"))
        Assert.assertEquals(0, doc.getInt("doc-10").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-10"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-10"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-10"), 0.001)
        Assert.assertEquals(java.lang.Double.valueOf(0.0), doc.getNumber("doc-10"))
        Assert.assertNull(doc.getString("doc-10"))
        Assert.assertNull(doc.getDate("doc-10"))
        Assert.assertNull(doc.getBlob("doc-10"))
        Assert.assertNull(doc.getArray("doc-10"))
        Assert.assertNull(doc.getDictionary("doc-10"))

        //#10 doc.setFloat(Float.MIN_VALUE);
        Assert.assertEquals(java.lang.Float.valueOf(Float.MIN_VALUE), doc.getValue("doc-11")?.demoteToFloat())
        Assert.assertFalse(doc.getBoolean("doc-11"))
        Assert.assertEquals(
            java.lang.Float.valueOf(Float.MIN_VALUE).toInt().toFloat(),
            doc.getInt("doc-11").toFloat(),
            0.001f
        )
        Assert.assertEquals(
            java.lang.Float.valueOf(Float.MIN_VALUE).toLong().toFloat(),
            doc.getLong("doc-11").toFloat(),
            0.001f
        )
        Assert.assertEquals(Float.MIN_VALUE, doc.getFloat("doc-11"), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), doc.getDouble("doc-11"), 0.001)
        Assert.assertEquals(java.lang.Float.valueOf(Float.MIN_VALUE), doc.getValue("doc-11")?.demoteToFloat())
        Assert.assertNull(doc.getString("doc-11"))
        Assert.assertNull(doc.getDate("doc-11"))
        Assert.assertNull(doc.getBlob("doc-11"))
        Assert.assertNull(doc.getArray("doc-11"))
        Assert.assertNull(doc.getDictionary("doc-11"))

        //#11 doc.setFloat(Float.MAX_VALUE);
        Assert.assertEquals(java.lang.Float.valueOf(Float.MAX_VALUE), doc.getValue("doc-12")?.demoteToFloat())
        Assert.assertTrue(doc.getBoolean("doc-12"))
        // ??? Fails: Assert.assertEquals(java.lang.Float.valueOf(Float.MAX_VALUE).toInt(), doc.getInt("doc-12"));
        // ??? Fails in Java: Assert.assertEquals(java.lang.Float.valueOf(Float.MAX_VALUE).toLong(), doc.getLong("doc-12"))
        Assert.assertEquals(Float.MAX_VALUE, doc.getFloat("doc-12"), 1.0E32F)
        Assert.assertEquals(Float.MAX_VALUE.toDouble(), doc.getDouble("doc-12"), 1.0E32)
        Assert.assertEquals(java.lang.Float.valueOf(Float.MAX_VALUE), doc.getNumber("doc-12")?.demoteToFloat())
        Assert.assertNull(doc.getString("doc-12"))
        Assert.assertNull(doc.getDate("doc-12"))
        Assert.assertNull(doc.getBlob("doc-12"))
        Assert.assertNull(doc.getArray("doc-12"))
        Assert.assertNull(doc.getDictionary("doc-12"))

        //#12 doc.setDouble(0.0);
        Assert.assertEquals(java.lang.Double.valueOf(0.0), doc.getValue("doc-13"))
        Assert.assertFalse(doc.getBoolean("doc-13"))
        Assert.assertEquals(0, doc.getInt("doc-13").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-13"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-13"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-13"), 0.001)
        Assert.assertEquals(java.lang.Double.valueOf(0.0), doc.getNumber("doc-13"))
        Assert.assertNull(doc.getString("doc-13"))
        Assert.assertNull(doc.getDate("doc-13"))
        Assert.assertNull(doc.getBlob("doc-13"))
        Assert.assertNull(doc.getArray("doc-13"))
        Assert.assertNull(doc.getDictionary("doc-13"))

        //#13 doc.setDouble(Double.MIN_VALUE);
        Assert.assertEquals(java.lang.Double.valueOf(Double.MIN_VALUE), doc.getValue("doc-14"))
        Assert.assertFalse(doc.getBoolean("doc-14"))
        Assert.assertEquals(java.lang.Double.valueOf(Double.MIN_VALUE).toInt().toLong(), doc.getInt("doc-14").toLong())
        Assert.assertEquals(java.lang.Double.valueOf(Double.MIN_VALUE).toLong(), doc.getLong("doc-14"))
        Assert.assertEquals(java.lang.Double.valueOf(Double.MIN_VALUE).toFloat(), doc.getFloat("doc-14"), 0.001f)
        Assert.assertEquals(Double.MIN_VALUE, doc.getDouble("doc-14"), 0.001)
        Assert.assertEquals(java.lang.Double.valueOf(Double.MIN_VALUE), doc.getNumber("doc-14"))
        Assert.assertNull(doc.getString("doc-14"))
        Assert.assertNull(doc.getDate("doc-14"))
        Assert.assertNull(doc.getBlob("doc-14"))
        Assert.assertNull(doc.getArray("doc-14"))
        Assert.assertNull(doc.getDictionary("doc-14"))

        //#14 doc.setDouble(Double.MAX_VALUE);
        Assert.assertEquals(java.lang.Double.valueOf(Double.MAX_VALUE), doc.getValue("doc-15"))
        // ??? Fails: Assert.assertEquals(java.lang.Double.valueOf(Double.MAX_VALUE).toInt(), doc.getInt("doc-15"));
        // ??? Fails in Java: Assert.assertEquals(java.lang.Double.valueOf(Double.MAX_VALUE).toLong(), doc.getLong("doc-15"))
        Assert.assertEquals(java.lang.Double.valueOf(Double.MAX_VALUE).toFloat(), doc.getFloat("doc-15"), 100.0f)
        Assert.assertEquals(Double.MAX_VALUE, doc.getDouble("doc-15"), 100.0)
        Assert.assertEquals(java.lang.Double.valueOf(Double.MAX_VALUE), doc.getNumber("doc-15"))
        Assert.assertNull(doc.getString("doc-15"))
        Assert.assertNull(doc.getDate("doc-15"))
        Assert.assertNull(doc.getBlob("doc-15"))
        Assert.assertNull(doc.getArray("doc-15"))
        Assert.assertNull(doc.getDictionary("doc-15"))

        //#15 doc.setNumber(null);
        Assert.assertNull(doc.getValue("doc-16"))
        Assert.assertFalse(doc.getBoolean("doc-16"))
        Assert.assertEquals(0, doc.getInt("doc-16").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-16"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-16"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-16"), 0.001)
        Assert.assertNull(doc.getNumber("doc-16"))
        Assert.assertNull(doc.getString("doc-16"))
        Assert.assertNull(doc.getDate("doc-16"))
        Assert.assertNull(doc.getBlob("doc-16"))
        Assert.assertNull(doc.getArray("doc-16"))
        Assert.assertNull(doc.getDictionary("doc-16"))

        //#16 doc.setNumber(0);
        Assert.assertEquals(0L, doc.getValue("doc-17"))
        Assert.assertFalse(doc.getBoolean("doc-17"))
        Assert.assertEquals(0, doc.getInt("doc-17").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-17"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-17"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-17"), 0.001)
        Assert.assertEquals(0L, doc.getNumber("doc-17"))
        Assert.assertNull(doc.getString("doc-17"))
        Assert.assertNull(doc.getDate("doc-17"))
        Assert.assertNull(doc.getBlob("doc-17"))
        Assert.assertNull(doc.getArray("doc-17"))
        Assert.assertNull(doc.getDictionary("doc-17"))

        //#17 doc.setNumber(Float.MIN_VALUE);
        Assert.assertEquals(java.lang.Float.valueOf(Float.MIN_VALUE), doc.getValue("doc-18")?.demoteToFloat())
        Assert.assertFalse(doc.getBoolean("doc-18"))
        Assert.assertEquals(java.lang.Float.valueOf(Float.MIN_VALUE).toInt().toLong(), doc.getInt("doc-18").toLong())
        Assert.assertEquals(java.lang.Float.valueOf(Float.MIN_VALUE).toLong(), doc.getLong("doc-18"))
        Assert.assertEquals(Float.MIN_VALUE, doc.getFloat("doc-18"), 0.001f)
        Assert.assertEquals(Float.MIN_VALUE.toDouble(), doc.getDouble("doc-18"), 0.001)
        Assert.assertEquals(java.lang.Float.valueOf(Float.MIN_VALUE), doc.getNumber("doc-18")?.demoteToFloat())
        Assert.assertNull(doc.getString("doc-18"))
        Assert.assertNull(doc.getDate("doc-18"))
        Assert.assertNull(doc.getBlob("doc-18"))
        Assert.assertNull(doc.getArray("doc-18"))
        Assert.assertNull(doc.getDictionary("doc-18"))

        //#18 doc.setNumber(Long.MIN_VALUE);
        Assert.assertEquals(java.lang.Long.valueOf(Long.MIN_VALUE), doc.getValue("doc-19"))
        // ??? Value differs for Documents and Results: Assert.assertTrue(doc.getBoolean("doc-19"));
        Assert.assertEquals(java.lang.Long.valueOf(Long.MIN_VALUE).toInt().toLong(), doc.getInt("doc-19").toLong())
        Assert.assertEquals(Long.MIN_VALUE, doc.getLong("doc-19"))
        Assert.assertEquals(Long.MIN_VALUE.toFloat(), doc.getFloat("doc-19"), 0.001f)
        Assert.assertEquals(Long.MIN_VALUE.toDouble(), doc.getDouble("doc-19"), 0.001)
        Assert.assertEquals(java.lang.Long.valueOf(Long.MIN_VALUE), doc.getNumber("doc-19"))
        Assert.assertNull(doc.getString("doc-19"))
        Assert.assertNull(doc.getDate("doc-19"))
        Assert.assertNull(doc.getBlob("doc-19"))
        Assert.assertNull(doc.getArray("doc-19"))
        Assert.assertNull(doc.getDictionary("doc-19"))

        //#19 doc.setString(null);
        Assert.assertNull(doc.getValue("doc-20"))
        Assert.assertFalse(doc.getBoolean("doc-20"))
        Assert.assertEquals(0, doc.getInt("doc-20").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-20"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-20"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-20"), 0.001)
        Assert.assertNull(doc.getNumber("doc-20"))
        Assert.assertNull(doc.getString("doc-20"))
        Assert.assertNull(doc.getDate("doc-20"))
        Assert.assertNull(doc.getBlob("doc-20"))
        Assert.assertNull(doc.getArray("doc-20"))
        Assert.assertNull(doc.getDictionary("doc-20"))

        //#20 doc.setString("Quatro");
        Assert.assertEquals("Jett", doc.getValue("doc-21"))
        Assert.assertTrue(doc.getBoolean("doc-21"))
        Assert.assertEquals(0, doc.getInt("doc-21").toLong())
        Assert.assertEquals(0, doc.getLong("doc-21"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-21"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-21"), 0.001)
        Assert.assertNull(doc.getNumber("doc-21"))
        Assert.assertEquals("Jett", doc.getString("doc-21"))
        Assert.assertNull(doc.getDate("doc-21"))
        Assert.assertNull(doc.getBlob("doc-21"))
        Assert.assertNull(doc.getArray("doc-21"))
        Assert.assertNull(doc.getDictionary("doc-21"))

        //#21 doc.setDate(null);
        Assert.assertNull(doc.getValue("doc-22"))
        Assert.assertFalse(doc.getBoolean("doc-22"))
        Assert.assertEquals(0, doc.getInt("doc-22").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-22"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-22"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-22"), 0.001)
        Assert.assertNull(doc.getNumber("doc-22"))
        Assert.assertNull(doc.getString("doc-22"))
        Assert.assertNull(doc.getDate("doc-22"))
        Assert.assertNull(doc.getBlob("doc-22"))
        Assert.assertNull(doc.getArray("doc-22"))
        Assert.assertNull(doc.getDictionary("doc-22"))

        //#22 doc.setDate(JSONUtils.toDate(TEST_DATE));
        Assert.assertEquals(TEST_DATE, doc.getValue("doc-23"))
        Assert.assertTrue(doc.getBoolean("doc-23"))
        Assert.assertEquals(0, doc.getInt("doc-23").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-23"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-23"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-23"), 0.001)
        Assert.assertNull(doc.getNumber("doc-23"))
        Assert.assertEquals(TEST_DATE, doc.getString("doc-23"))
        Assert.assertEquals(JSONUtils.toDate(TEST_DATE), doc.getDate("doc-23"))
        Assert.assertNull(doc.getBlob("doc-23"))
        Assert.assertNull(doc.getArray("doc-23"))
        Assert.assertNull(doc.getDictionary("doc-23"))

        //#23 doc.setArray(null);
        Assert.assertNull(doc.getValue("doc-24"))
        Assert.assertFalse(doc.getBoolean("doc-24"))
        Assert.assertEquals(0, doc.getInt("doc-24").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-24"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-24"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-24"), 0.001)
        Assert.assertNull(doc.getNumber("doc-24"))
        Assert.assertNull(doc.getString("doc-24"))
        Assert.assertNull(doc.getDate("doc-24"))
        Assert.assertNull(doc.getBlob("doc-24"))
        Assert.assertNull(doc.getArray("doc-24"))
        Assert.assertNull(doc.getDictionary("doc-24"))

        //#24 doc.setDictionary(null);
        verifyArray(doc.getArray("doc-25"), fromJSON)

        //#25 doc.setDictionary(null);
        Assert.assertNull(doc.getValue("doc-26"))
        Assert.assertFalse(doc.getBoolean("doc-26"))
        Assert.assertEquals(0, doc.getInt("doc-26").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-26"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-26"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-26"), 0.001)
        Assert.assertNull(doc.getNumber("doc-26"))
        Assert.assertNull(doc.getString("doc-26"))
        Assert.assertNull(doc.getDate("doc-26"))
        Assert.assertNull(doc.getBlob("doc-26"))
        Assert.assertNull(doc.getArray("doc-26"))
        Assert.assertNull(doc.getDictionary("doc-26"))

        //#26 doc.setDictionary(simpleDict);
        verifyDict(doc.getDictionary("doc-27"), fromJSON)

        //#27 doc.setDictionary(null);
        Assert.assertNull(doc.getValue("doc-28"))
        Assert.assertFalse(doc.getBoolean("doc-28"))
        Assert.assertEquals(0, doc.getInt("doc-28").toLong())
        Assert.assertEquals(0L, doc.getLong("doc-28"))
        Assert.assertEquals(0.0f, doc.getFloat("doc-28"), 0.001f)
        Assert.assertEquals(0.0, doc.getDouble("doc-28"), 0.001)
        Assert.assertNull(doc.getNumber("doc-28"))
        Assert.assertNull(doc.getString("doc-28"))
        Assert.assertNull(doc.getDate("doc-28"))
        Assert.assertNull(doc.getBlob("doc-28"))
        Assert.assertNull(doc.getArray("doc-28"))
        Assert.assertNull(doc.getDictionary("doc-28"))
        verifyBlob(doc.getBlob("doc-29"))
    }

    // Some JSON encoding will promote a Float to a Double.
    private fun Any.demoteToFloat() = when (this) {
        is Float -> this
        is Double -> this.toFloat()
        else -> throw IllegalArgumentException("${this} cannot be converted to float")
    }

    private fun Any.promoteToDouble() = when (this) {
        is Float -> this.toDouble()
        is Double -> this
        else -> throw IllegalArgumentException("${this} cannot be converted to double")
    }

    // Some JSON encoding will promote a Float to a Double.
    private fun Any.demoteToInt() = when (this) {
        is Int -> this
        is Long -> this.toInt()
        else -> throw IllegalArgumentException("${this} cannot be converted to int")
    }

    private fun Any.promoteToLong() = when (this) {
        is Int -> this.toLong()
        is Long -> this
        else -> throw IllegalArgumentException("${this} cannot be converted to float")
    }
}
