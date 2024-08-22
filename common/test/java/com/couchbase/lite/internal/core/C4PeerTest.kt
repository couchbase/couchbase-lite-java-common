//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core

import com.couchbase.lite.BaseTest
import com.couchbase.lite.CouchbaseLiteError
import com.couchbase.lite.internal.core.C4Peer.PeerCleaner
import com.couchbase.lite.internal.core.Cleaner.Cleanable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class C4PeerTest : BaseTest() {
    // Verify that a newly created cleaner has the expected number of threads running
    @Test
    fun testNewCleaner() {
        assertEquals(2, Cleaner("newTest", 2, 1000).runningThreads())
    }

    // Verify that registering a cleanable with a cleaner
    // adds a CleanableRef to the alive set and that cleaning it removes it.
    @Test
    fun testCleanerRefs() {
        val cleaner = Cleaner("refsTest", 2, 1000)
        assertEquals(0, cleaner.capacity())
        cleaner.register(Object()) { _ -> }
        assertEquals(1, cleaner.capacity())
        // It is hard to test for the removal of the ref, because it happens only after a GC.
    }

    // Verify that closing a C4Peer before it is cleaned does not cause multiple
    // calls to the dispose method.
    /// This is hard to test, because it has to verify something that happens only after a GC

    // Verify that attempting to register with a stopped cleaner throws
    @Test
    fun testClosedCleaner() {
        val cleaner = Cleaner("refsTest", 2, 1000)
        cleaner.stop()
        assertThrows(CouchbaseLiteError::class.java) { cleaner.register(Object()) { _ -> } }
    }

    // Verify that closing a peer ref explicitly gets its dispose method called.
    @Test
    fun testClosePeer() {
        val visited = AtomicBoolean()
        val peered = object : C4Peer(8954L, PeerCleaner { visited.set(true) }) {}

        peered.close()
        assertTrue(visited.get())
    }

    // Verify that finalizing a peer ref gets its dispose method called.
    // This test will throw an OOM on failure1
    @Test
    fun testFinalizePeer() {
        val cleaner = Cleaner("finalizerTest", 1, 1000)

        val visited = AtomicBoolean()
        while (!visited.get()) {
            cleaner.register(Any()) { visited.set(true) }
        }
    }

    // Verify that stopping a cleaner kills its threads within the specified timeout,
    // even if there are no queued cleanables
    @Test
    fun testStopCleaner() {
        val peerCleaner = Cleaner("stopTest", 3, 200)
        assertEquals(3, peerCleaner.runningThreads())

        peerCleaner.stop()
        Thread.sleep(300)
        assertEquals(0, peerCleaner.runningThreads())
    }

    // Verify that an exception in a cleanable does not reduce the number of running threads
    @Test
    fun testCleanableException() {
        val latch = CountDownLatch(1)
        val peerCleaner = object : CleanerImpl("testCleanerError", 1, 500) {
            override fun getNextZombie(): Cleanable? {
                if (latch.count == 0L) return null
                latch.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                throw Exception("ch ch ch ch ch ch ch ch cherry bomb")
            }
        }

        // start the cleaner and verify that it has a thread
        peerCleaner.startCleaner()
        waitUntil(STD_TIMEOUT_MS) { peerCleaner.runningThreads() == 1 }
        val theThread = peerCleaner.threads.first()

        // let the Cleanable throw its exception
        latch.countDown()

        // verify that the thread dies and is removed from the list of threads
        waitUntil(STD_TIMEOUT_MS) { !peerCleaner.threads.contains(theThread) }

        // verify that it is replaced by another thread
        waitUntil(STD_TIMEOUT_MS) { peerCleaner.runningThreads() == 1 }

        peerCleaner.stopCleaner()
    }

    // Verify that an exception in the cleaner itself does not reduce the number of running threads
    @Test
    fun testCleanerError() {
        val latch = CountDownLatch(1)
        val peerCleaner = object : CleanerImpl("testCleanerError", 1, 500) {
            override fun getNextZombie(): Cleanable? {
                if (latch.count == 0L) return null
                latch.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                throw Exception("ch ch ch ch ch ch ch ch cherry bomb")
            }
        }

        // start the cleaner and verify that it has a thread
        peerCleaner.startCleaner()
        waitUntil(STD_TIMEOUT_MS) { peerCleaner.runningThreads() == 1 }
        val theThread = peerCleaner.threads.first()

        // let getNextZombie throw its exception
        latch.countDown()

        // verify that the thread dies and is removed from the list of threads
        waitUntil(STD_TIMEOUT_MS) { !peerCleaner.threads.contains(theThread) }

        // verify that it is replaced by another thread
        waitUntil(STD_TIMEOUT_MS) { peerCleaner.runningThreads() == 1 }

        peerCleaner.stopCleaner()
    }
}