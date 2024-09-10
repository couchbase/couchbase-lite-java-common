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
package com.couchbase.lite.internal.exec

import com.couchbase.lite.BaseTest
import com.couchbase.lite.CouchbaseLiteError
import com.couchbase.lite.internal.core.C4Peer
import com.couchbase.lite.internal.core.C4Peer.PeerCleaner
import com.couchbase.lite.internal.exec.Cleaner.Cleanable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


class C4PeerTest : BaseTest() {
    // Verify that registering a cleanable with a cleaner
    // adds a CleanableRef to the alive set and that cleaning it removes it.
    @Test
    fun testCleanerRefs() {
        val cleaner = Cleaner("refsTest", 1000)
        assertEquals(1, cleaner.liveCount)
        cleaner.register(Object()) { _ -> }
        assertEquals(2, cleaner.liveCount)
        // ??? Difficult to verify the removal of the ref, because it happens only after a GC.
    }

    // Verify that a ref added to the CleanerImpl's ref queue is cleaned
    // ??? There is no way to add things to a ReferenceQueue, explicitly, though.

    // Verify that attempting to register with a stopped cleaner throws
    @Test
    fun testClosedCleaner() {
        val cleaner = Cleaner("refsTest", 1000)
        cleaner.stop()
        assertThrows(CouchbaseLiteError::class.java) { cleaner.register(Object()) { _ -> } }
    }

    // Verify that closing a peer ref explicitly gets its dispose method called.
    @Test
    fun testClosePeer() {
        val visited = CountDownLatch(1)
        val peered = object : C4Peer(8954L, PeerCleaner { visited.countDown() }) {}

        peered.close()

        assertTrue(visited.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
    }

    // Verify that closing a peer ref explicitly, multiple time, gets its dispose method called exactly once.
    @Test
    fun testClosePeerLots() {
        val closes = AtomicInteger()
        val peered = object : C4Peer(8954L, PeerCleaner { closes.incrementAndGet() }) {}

        for (i in 0 until 100) {
            peered.close()
        }
        assertEquals(1, closes.get())
    }

    // Verify that closing a C4Peer before it is cleaned does not cause multiple
    // calls to the dispose method.
    // ??? Difficult to verify because this happens only after a GC

    // Verify that finalizing a peer ref gets its dispose method called.
    // This test will throw an OOM on failure!
    @Test
    fun testFinalizePeer() {
        val cleaner = Cleaner("finalizerTest", 1000)
        val visited = AtomicBoolean()
        try {
            while (!visited.get()) {
                cleaner.register(Any()) { visited.set(true) }
            }
        } catch (e: OutOfMemoryError) {
            // not at all clear that this will save our biscuit
            throw AssertionError("Cleaner did not run")
        }
    }

    // Verify that stopping a cleaner kills its threads within the specified timeout,
    // even if there are no queued cleanables
    @Test
    fun testStopCleaner() {
        val peerCleaner = Cleaner("stopTest", 200)
        Thread.sleep(300)
        assertFalse(peerCleaner.isStopped)

        peerCleaner.stop()
        Thread.sleep(300)
        assertTrue(peerCleaner.isStopped)
    }

    // Verify that an exception in a cleanable does not reduce the number of running threads
    @Test
    fun testCleanableException() {
        val thread = AtomicReference<Thread>()
        val ok = AtomicBoolean()
        val latch = CountDownLatch(1)
        val peerCleaner = object : CleanerImpl("testCleanerError", 500) {
            override fun getNextZombie(): Cleanable? {
                // if thread.get is null, this is the first time through
                // return a Cleanable that throws an exception
                if (null == thread.get()) {
                    thread.set(Thread.currentThread())
                    return Cleanable { throw Exception("ch ch ch ch ch ch ch ch cherry bomb") }
                }

                // if we get here the thread on which the exception was thrown should be gone
                // and we should be on a new thread.
                ok.set(!Thread.currentThread().equals(thread.get()))
                latch.countDown()

                return null
            }
        }

        // start the cleaner and verify that it has a thread
        peerCleaner.startCleaner()
        try {
            assertTrue(latch.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            assertTrue(ok.get())
        } finally {
            peerCleaner.stopCleaner()
        }
    }

    // Verify that an exception in the cleaner itself does not reduce the number of running threads
    @Test
    fun testCleanerError() {
        val thread = AtomicReference<Thread>()
        val ok = AtomicBoolean()
        val latch = CountDownLatch(1)
        val peerCleaner = object : CleanerImpl("testCleanerError", 500) {
            override fun getNextZombie(): Cleanable? {
                // if thread.get is null, this is the first time through
                // throw an exception
                if (null == thread.get()) {
                    thread.set(Thread.currentThread())
                    throw Exception("ch ch ch ch ch ch ch ch cherry bomb")
                }

                // if we get here the thread on which the exception was thrown should be gone
                // and we should be on a new thread.
                ok.set(!Thread.currentThread().equals(thread.get()))
                latch.countDown()

                return null
            }
        }

        // start the cleaner and verify that it has a thread
        peerCleaner.startCleaner()
        try {
            assertTrue(latch.await(STD_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            assertTrue(ok.get())
        } finally {
            peerCleaner.stopCleaner()
        }
    }
}