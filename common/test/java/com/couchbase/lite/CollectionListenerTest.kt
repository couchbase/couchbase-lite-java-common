//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License")
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit


class CollectionListenerTest : BaseCollectionTest() {

    // Test that change listeners can be added to a collection and that they receive changes correctly.
    @Test
    fun testCollectionChangeListener() {
        testCollectionChangeListener(null)
    }

    // Test that change listeners can be added to a collection with a custom executor
    //   and that they receive changes correctly.
    @Test
    fun testCollectionChangeListenerWithExecutor() {
        testCollectionChangeListener(testSerialExecutor)
    }

    // Test that document change listeners can be added to a collection and that they receive changes correctly.
    @Test
    fun testCollectionDocumentChangeListener() {
        testCollectionDocumentChangeListener(null)
    }

    // Test that document change listeners can be added to a collection with a custom executor
    //   and that they receive changes correctly.
    @Test
    fun testCollectionDocumentChangeListenerWithExecutor() {
        testCollectionChangeListener(testSerialExecutor)
    }

    // These tests tests are incredibly finicky.

    // Create two collections, A and B.
    // Add two change listeners to collection A.
    // Create documents, update documents, and delete Documents to/from both collections.
    // Ensure that the two listeners received only the changes from the collection A.
    // Remove the two change listeners by using token.remove() API.
    // Update the documents on the collection A.
    // Ensure that the two listeners don’t receive any changes.
    private fun testCollectionChangeListener(exec: Executor?) {
        val collectionA = baseTestDb.createCollection("colA", "scopeA")
        val collectionB = baseTestDb.createCollection("colB", "scopeA")

        var latch: CountDownLatch? = null
        var changes1: MutableList<String>? = null
        var changes2: MutableList<String>? = null
        var thread1: Thread? = null
        var thread2: Thread? = null

        val doc1Id = "doc_1"
        val doc2Id = "doc_2"
        val doc3Id = "doc_3"

        var t = 0L

        val token1 = collectionA.addChangeListener(exec) { c ->
            changes1?.addAll(c.documentIDs)
            thread1 = Thread.currentThread()
            if ((changes1?.size ?: 0) >= 2) {
                latch?.countDown()
            }
        }

        val token2 = collectionA.addChangeListener(exec) { c ->
            changes2?.addAll(c.documentIDs)
            thread2 = Thread.currentThread()
            if ((changes2?.size ?: 0) >= 2) {
                latch?.countDown()
            }
        }

        // Create documents
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        t -= System.currentTimeMillis()
        collectionB.save(MutableDocument(doc3Id))
        collectionA.save(MutableDocument(doc2Id))
        collectionA.save(MutableDocument(doc1Id))

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        t += System.currentTimeMillis()

        assertEquals(2, changes1.size)
        assertTrue(changes1.contains(doc1Id))
        assertTrue(changes1.contains(doc2Id))
        assertEquals(2, changes2.size)
        assertTrue(changes1.contains(doc1Id))
        assertTrue(changes1.contains(doc2Id))
        if (exec != null) {
            assertNotEquals(Thread.currentThread(), thread1)
            assertNotEquals(Thread.currentThread(), thread2)
        }

        // Update documents
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        t -= System.currentTimeMillis()
        collectionB.save(
            collectionB.getDocument(doc3Id)?.toMutable()?.setString("Lucky", "Radiohead")!!
        )
        collectionA.save(
            collectionA.getDocument(doc2Id)?.toMutable()?.setString("Dazzle", "Siouxsie & the Banshees")!!
        )
        collectionA.save(
            collectionA.getDocument(doc1Id)?.toMutable()?.setString("Baroud", "Cheb Khaled & Safy Boutella")!!
        )

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        t += System.currentTimeMillis()

        assertEquals(2, changes1.size)
        assertTrue(changes1.contains(doc1Id))
        assertTrue(changes1.contains(doc2Id))
        assertEquals(2, changes2.size)
        assertTrue(changes1.contains(doc1Id))
        assertTrue(changes1.contains(doc2Id))
        if (exec != null) {
            assertNotEquals(Thread.currentThread(), thread1)
            assertNotEquals(Thread.currentThread(), thread2)
        }

        // Delete documents
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        t -= System.currentTimeMillis()
        collectionB.delete(collectionB.getDocument(doc3Id)!!)
        collectionA.delete(collectionA.getDocument(doc1Id)!!)
        collectionA.delete(collectionA.getDocument(doc2Id)!!)

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        t += System.currentTimeMillis()

        assertEquals(2, changes1.size)
        assertTrue(changes1.contains(doc1Id))
        assertTrue(changes1.contains(doc2Id))
        assertEquals(2, changes2.size)
        assertTrue(changes1.contains(doc1Id))
        assertTrue(changes1.contains(doc2Id))
        if (exec != null) {
            assertNotEquals(Thread.currentThread(), thread1)
            assertNotEquals(Thread.currentThread(), thread2)
        }

        // Remove the change listeners
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        token1.remove()
        token2.remove()

        collectionB.save(MutableDocument(doc3Id))
        collectionA.save(MutableDocument(doc2Id))
        collectionA.save(MutableDocument(doc1Id))

        // wait twice the average time to notify
        assertFalse(latch.await((t * 2) / 3, TimeUnit.MILLISECONDS))
        assertTrue(changes1.isEmpty())
        assertTrue(changes2.isEmpty())
    }

    // Create two collections, A and B.
    // Add two document change listeners to collection A.
    // Create documents, update documents, and delete Documents to/from both collections.
    // Ensure that the two listeners received only the changes from the collection A.
    // Remove the two change listeners by using token.remove() API.
    // Update the documents on the collection A.
    // Ensure that the two listeners don’t receive any changes.
    private fun testCollectionDocumentChangeListener(exec: Executor?) {
        val collectionA = baseTestDb.createCollection("colA", "scopeA")
        val collectionB = baseTestDb.createCollection("colB", "scopeA")

        var latch: CountDownLatch? = null
        var changes1: MutableList<String>? = null
        var changes2: MutableList<String>? = null
        var thread1: Thread? = null
        var thread2: Thread? = null

        val doc1Id = "doc_1"
        val doc2Id = "doc_2"
        val doc3Id = "doc_3"

        var t = 0L

        val token1 = collectionA.addDocumentChangeListener(doc1Id, exec) { c ->
            changes1?.add(c.documentID)
            thread1 = Thread.currentThread()
            latch?.countDown()
        }

        val token2 = collectionA.addDocumentChangeListener(doc2Id, exec) { c ->
            changes2?.add(c.documentID)
            thread2 = Thread.currentThread()
            latch?.countDown()
        }

        // Create documents
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        t -= System.currentTimeMillis()
        collectionB.save(MutableDocument(doc3Id))
        collectionA.save(MutableDocument(doc2Id))
        collectionA.save(MutableDocument(doc1Id))

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        t += System.currentTimeMillis()

        assertEquals(1, changes1.size)
        assertTrue(changes1.contains(doc1Id))
        assertEquals(1, changes2.size)
        assertTrue(changes2.contains(doc2Id))
        if (exec != null) {
            assertNotEquals(Thread.currentThread(), thread1)
            assertNotEquals(Thread.currentThread(), thread2)
        }

        // Update documents
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        t -= System.currentTimeMillis()
        collectionB.save(
            collectionB.getDocument(doc3Id)?.toMutable()?.setString("Lucky", "Radiohead")!!
        )
        collectionA.save(
            collectionA.getDocument(doc2Id)?.toMutable()?.setString("Dazzle", "Siouxsie & the Banshees")!!
        )
        collectionA.save(
            collectionA.getDocument(doc1Id)?.toMutable()?.setString("Baroud", "Cheb Khaled & Safy Boutella")!!
        )

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        t += System.currentTimeMillis()

        assertEquals(1, changes1.size)
        assertTrue(changes1.contains(doc1Id))
        assertEquals(1, changes2.size)
        assertTrue(changes2.contains(doc2Id))
        if (exec != null) {
            assertNotEquals(Thread.currentThread(), thread1)
            assertNotEquals(Thread.currentThread(), thread2)
        }

        // Delete documents
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        t -= System.currentTimeMillis()
        collectionB.delete(collectionB.getDocument(doc3Id)!!)
        collectionA.delete(collectionA.getDocument(doc2Id)!!)
        collectionA.delete(collectionA.getDocument(doc1Id)!!)

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        t += System.currentTimeMillis()

        assertEquals(1, changes1.size)
        assertTrue(changes1.contains(doc1Id))
        assertEquals(1, changes2.size)
        assertTrue(changes2.contains(doc2Id))
        if (exec != null) {
            assertNotEquals(Thread.currentThread(), thread1)
            assertNotEquals(Thread.currentThread(), thread2)
        }

        // Remove the change listeners
        latch = CountDownLatch(2)
        changes1 = mutableListOf()
        changes2 = mutableListOf()

        token1.remove()
        token2.remove()

        collectionB.save(MutableDocument(doc3Id))
        collectionA.save(MutableDocument(doc2Id))
        collectionA.save(MutableDocument(doc1Id))

        // wait twice the average time to notify
        assertFalse(latch.await((t * 2) / 3, TimeUnit.MILLISECONDS))
        assertTrue(changes1.isEmpty())
        assertTrue(changes2.isEmpty())
    }
}
