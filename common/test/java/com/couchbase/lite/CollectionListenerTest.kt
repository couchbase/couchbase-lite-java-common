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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit


class CollectionListenerTest : BaseCollectionTest() {

    // Test that change listeners can be added to a collection and that they receive changes correctly.
    @Test
    fun testCollectionChangeListener() = testCollectionChangeListener(null)

    // Test that change listeners can be added to a collection with a custom executor
    //   and that they receive changes correctly.
    @Test
    fun testCollectionChangeListenerWithExecutor() = testCollectionChangeListener(testSerialExecutor)

    // Test that document change listeners can be added to a collection and that they receive changes correctly.
    @Test
    fun testCollectionDocumentChangeListener() = testCollectionDocumentChangeListener(null)

    // Test that document change listeners can be added to a collection with a custom executor
    //   and that they receive changes correctly.
    @Test
    fun testCollectionDocumentChangeListenerWithExecutor() = testCollectionDocumentChangeListener(testSerialExecutor)

    // Test that adding a change listener to a deleted collection doesn't throw exception
    @Test
    fun testAddChangeListenerToDeletedCollection() {
        baseTestDb.deleteCollection(testCollection.name, testCollection.scope.name)
        testCollection.addChangeListener(testSerialExecutor) {}
    }


    // Test that addChangeListener to a collection deleted from a different db instance doesn't throw exception
    @Test
    fun testAddChangeListenerToCollectionDeletedInDifferentDBInstance() {
        duplicateBaseTestDb().use {
            it.deleteCollection(testCollection.name, testCollection.scope.name)
            testCollection.addChangeListener(testSerialExecutor) {}
        }
    }

    // Test that adding a document change listener to a deleted collection
    // doesn't throw exception
    @Test
    fun testAddDocumentChangeListenerToDeletedCollection() {
        baseTestDb.deleteCollection(testCollection.name, testCollection.scope.name)
        testCollection.addDocumentChangeListener("doc_id", testSerialExecutor) {}
    }

    // Test that adding a document change listener to a deleted collection gets warning message
    // and doesn't throw exception
    @Test
    fun testAddDocumentChangeListenerToCollectionDeletedInADifferentDBInstance() {
        duplicateBaseTestDb().use {
            it.deleteCollection(testCollection.name, testCollection.scope.name)
            testCollection.addDocumentChangeListener("doc_id", testSerialExecutor) {}
        }
    }

    // Test that removing a listener from a deleted collection doesn't throw exception
    @Ignore("CBL-3602")
    @Test
    fun testRemoveChangeListenerFromDeletedCollection() {
        val token = testCollection.addChangeListener { }
        try {
            baseTestDb.deleteCollection(testCollection.name, testCollection.scope.name)
            assertNull(baseTestDb.getCollection(testCollection.name, testCollection.scope.name))
        } finally {
            token.remove()
        }
    }

    // Test that removing a listener from a collection deleted in a different db doesn't throw exception
    @Ignore("CBL-3602")
    @Test
    fun testRemoveChangeListenerFromCollectionDeletedInADifferentDBInstance() {
        val otherDb = duplicateBaseTestDb()
        val token = testCollection.addChangeListener { }

        try {
            otherDb.deleteCollection(testCollection.name, testCollection.scope.name)
            assertNull(baseTestDb.getCollection(testCollection.name, testCollection.scope.name))
        } finally {
            token.remove()

            deleteDb(otherDb)
        }
    }

    // Test that removing a listener from a deleted collection doesn't throw exception
    @Ignore("CBL-3602")
    @Test
    fun testRemoveDocChangeListenerFromDeletedCollection() {
        val docId = "doc_1"
        testCollection.save(MutableDocument(docId))

        val token = testCollection.addDocumentChangeListener(docId) { }
        try {
            baseTestDb.deleteCollection(testCollection.name, testCollection.scope.name)
            assertNull(baseTestDb.getCollection(testCollection.name, testCollection.scope.name))

        } finally {
            token.remove()
        }
    }

    // Test that removing a listener from a collection deleted in a different db doesn't throw exception
    @Ignore("CBL-3602")
    @Test
    fun testRemoveDocChangeListenerFromCollectionDeletedInADifferentDBInstance() {
        val otherDb = duplicateBaseTestDb()
        val token = testCollection.addChangeListener { }

        try {
            otherDb.deleteCollection(testCollection.name, testCollection.scope.name)
            assertNull(baseTestDb.getCollection(testCollection.name, testCollection.scope.name))
        } finally {
            token.remove()

            deleteDb(otherDb)
        }
    }

    // Test that adding a change listener to a collection in a closed database doesn't throw an exception
    @Test
    fun testAddChangeListenerToCollectionInClosedDatabase() {
        closeDb(baseTestDb)
        testCollection.addChangeListener(null) {}
    }

    // Test that adding a document change listener  to a collection in a closed database doesn't throw an exception
    @Test
    fun testAddDocumentChangeListenerToCollectionInClosedDatabase() {
        val docID = "testDoc"
        testCollection.save(MutableDocument(docID))

        closeDb(baseTestDb)

        testCollection.addDocumentChangeListener(docID, null) {}
    }

    // Test that removing a listener from a collection in a closed database doesn't throw exception
    @Test
    fun testRemoveChangeListenerFromCollectionInClosedDatabase() {
        val token = testCollection.addChangeListener {}
        try {
            closeDb(baseTestDb)
        } finally {
            token.remove()
        }
    }

    // Test that addChangeListener to a collection in a deleted database doesn't throw an exception
    @Test
    fun testAddChangeListenerToCollectionInDeletedDatabase() {
        deleteDb(baseTestDb)
        testCollection.addChangeListener(null) {}
    }

    // Test that addDocumentChangeListener to a collection in a deleted database doesn't throw an exception
    @Test
    fun testAddDocumentChangeListenerToCollectionInDeletedDatabase() {
        deleteDb(baseTestDb)
        testCollection.addDocumentChangeListener("doc_id", null) {}
    }

    // Test that removeChangeListener from a collection in a deleted database doesn't throw exception
    @Test
    fun testRemoveChangeListenerFromCollectionInDeletedDatabase() {
        val token = testCollection.addChangeListener { }
        try {
            deleteDb(baseTestDb)
        } finally {
            token.remove()
        }
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
        val doc1Id = "doc_1"
        val doc2Id = "doc_2"
        val doc3Id = "doc_3"

        val collectionA = baseTestDb.createCollection("colA", "scopeA")
        val collectionB = baseTestDb.createCollection("colB", "scopeA")

        var latch = CountDownLatch(2)
        var changes1 = mutableListOf<String>()
        var changes2 = mutableListOf<String>()
        var thread1: Thread? = null
        var thread2: Thread? = null

        var t = 0L

        val token1 = collectionA.addChangeListener(exec) { c ->
            changes1.addAll(c.documentIDs)
            thread1 = Thread.currentThread()
            if (changes1.size >= 2) {
                latch.countDown()
            }
        }

        val token2 = collectionA.addChangeListener(exec) { c ->
            changes2.addAll(c.documentIDs)
            thread2 = Thread.currentThread()
            if (changes2.size >= 2) {
                latch.countDown()
            }
        }

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
        collectionB.save(collectionB.getDocument(doc3Id)?.toMutable()?.setString("Lucky", "Radiohead")!!)
        collectionA.save(collectionA.getDocument(doc2Id)?.toMutable()?.setString("Dazzle", "Siouxsie & the Banshees")!!)
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

        try {
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

        } finally {
            token1.remove()
            token2.remove()
        }

        collectionB.save(MutableDocument(doc3Id))
        collectionA.save(MutableDocument(doc2Id))
        collectionA.save(MutableDocument(doc1Id))

        // wait twice the average time to notify
        assertFalse(latch?.await((t * 2) / 3, TimeUnit.MILLISECONDS) ?: false)
        assertTrue(changes1?.isEmpty() ?: false)
        assertTrue(changes2?.isEmpty() ?: false)
    }
}
