//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FlowTest : BaseReplicatorTest() {
    @Test
    fun testDatabaseChangeFlow() {
        val docIds = mutableListOf<String>()

        runBlocking {
            val latch = CountDownLatch(1)

            val collector = launch(Dispatchers.Default) {
                baseTestDb.databaseChangeFlow(testSerialExecutor)
                    .map {
                        Assert.assertEquals("change on wrong db", baseTestDb, it.database)
                        it.documentIDs
                    }
                    .onEach { ids ->
                        docIds.addAll(ids)
                        if (docIds.size >= 10) {
                            latch.countDown()
                        }
                    }
                    .catch {
                        latch.countDown()
                        throw it
                    }
                    .collect()
            }

            launch(Dispatchers.Default) {
                // Hate this: wait until the collector starts
                delay(20L)

                // make 10 db changes
                for (i in 0..9) {
                    val doc = MutableDocument("doc-${i}")
                    doc.setValue("type", "demo")
                    saveDocInBaseTestDb(doc)
                }
            }

            Assert.assertTrue("Timeout", latch.await(1, TimeUnit.SECONDS))
            collector.cancel()
        }

        Assert.assertEquals(10, docIds.size)
        for (i in 0..9) {
            val id = "doc-${i}"
            Assert.assertTrue("missing ${id}", docIds.contains(id))
        }
    }

    @Test
    fun testDocumentChangeFlowOnSave() {
        val changes = mutableListOf<DocumentChange>()

        val docA = MutableDocument("A")
        docA.setValue("theanswer", 18)
        val docB = MutableDocument("B")
        docB.setValue("thewronganswer", 18)

        runBlocking {
            val latch = CountDownLatch(1)

            val collector = launch(Dispatchers.Default) {
                baseTestDb.documentChangeFlow(docA.id, testSerialExecutor)
                    .onEach { change ->
                        changes.add(change)
                        latch.countDown()
                    }
                    .catch {
                        latch.countDown()
                        throw it
                    }
                    .collect()
            }

            launch(Dispatchers.Default) {
                // Hate this: wait until the collector starts
                delay(20L)

                saveDocInBaseTestDb(docB)
                saveDocInBaseTestDb(docA)
            }

            Assert.assertTrue("Timeout", latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
            collector.cancel()
        }

        Assert.assertEquals(1, changes.size)
        Assert.assertEquals("change on wrong db", baseTestDb, changes[0].database)
        Assert.assertEquals("change on wrong doc", docA.id, changes[0].documentID)
    }

    @Test
    fun testDocumentChangeFlowOnUpdate() {
        val changes = mutableListOf<DocumentChange>()

        var mDocA = MutableDocument("A")
        mDocA.setValue("theanswer", 18)
        val docA = saveDocInBaseTestDb(mDocA)
        var mDocB = MutableDocument("B")
        mDocB.setValue("thewronganswer", 18)
        val docB = saveDocInBaseTestDb(mDocB)

        runBlocking {
            val latch = CountDownLatch(1)

            val collector = launch(Dispatchers.Default) {
                baseTestDb.documentChangeFlow(docA.id, testSerialExecutor)
                    .onEach { change ->
                        changes.add(change)
                        latch.countDown()
                    }
                    .catch {
                        latch.countDown()
                        throw it
                    }
                    .collect()
            }

            launch(Dispatchers.Default) {
                // Hate this: wait until the collector starts
                delay(20L)

                mDocB = docB.toMutable()
                mDocB.setValue("thewronganswer", 42)
                saveDocInBaseTestDb(mDocB)

                mDocA = docA.toMutable()
                mDocA.setValue("thewronganswer", 18)
                saveDocInBaseTestDb(mDocA)
            }

            Assert.assertTrue("Timeout", latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
            collector.cancel()
        }

        Assert.assertEquals(1, changes.size)
        Assert.assertEquals("change on wrong db", baseTestDb, changes[0].database)
        Assert.assertEquals("change on wrong doc", docA.id, changes[0].documentID)
    }

    @Test
    fun testDocumentChangeFlowOnDelete() {
        val changes = mutableListOf<DocumentChange>()

        val mDocA = MutableDocument("A")
        mDocA.setValue("theanswer", 18)
        val docA = saveDocInBaseTestDb(mDocA)
        val mDocB = MutableDocument("B")
        mDocB.setValue("thewronganswer", 18)
        val docB = saveDocInBaseTestDb(mDocB)

        runBlocking {
            val latch = CountDownLatch(1)

            val collector = launch(Dispatchers.Default) {
                baseTestDb.documentChangeFlow(docA.id, testSerialExecutor)
                    .onEach { change ->
                        changes.add(change)
                        latch.countDown()
                    }
                    .catch {
                        latch.countDown()
                        throw it
                    }
                    .collect()
            }

            launch(Dispatchers.Default) {
                // Hate this: wait until the collector starts
                delay(20L)

                baseTestDb.delete(docB)
                baseTestDb.delete(docA)
            }

            Assert.assertTrue("Timeout", latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
            collector.cancel()
        }

        Assert.assertEquals(1, changes.size)
        Assert.assertEquals("change on wrong db", baseTestDb, changes[0].database)
        Assert.assertEquals("change on wrong doc", mDocA.id, changes[0].documentID)
    }

    @Test
    fun testQueryChangeFlow() {
        val allResults = mutableListOf<Any>()

        val mDoc = MutableDocument("doc-1")
        mDoc.setValue("theanswer", 18)

        runBlocking {
            val query = QueryBuilder.select(SelectResult.expression(Meta.id)).from(DataSource.database(baseTestDb))
            val latch = CountDownLatch(1)

            val collector = launch(Dispatchers.Default) {
                query.queryChangeFlow(testSerialExecutor)
                    .map { change ->
                        val err = change.error
                        if (err != null) {
                            throw err
                        }
                        change.results?.allResults()?.flatMap { it.toList() }
                    }
                    .onEach { v ->
                        if (v != null) {
                            allResults.addAll(v)
                        }
                        if (allResults.size > 0) {
                            latch.countDown()
                        }
                    }
                    .collect()
            }

            launch(Dispatchers.Default) {
                // Hate this: wait until the collector starts
                delay(20L)

                baseTestDb.save(mDoc)
            }

            Assert.assertTrue("Timeout", latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
            collector.cancel()
        }

        Assert.assertEquals(1, allResults.size)
        Assert.assertEquals(mDoc.id, allResults[0])
    }
}
