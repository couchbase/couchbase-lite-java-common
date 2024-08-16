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
import com.couchbase.lite.internal.core.C4Peer.PeerCleaner
import com.couchbase.lite.internal.core.Cleaner.Cleanable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class C4PeerTest : BaseTest() {
    // Verify that a newly created cleaner has no threads running

    // Verify that registering a cleanable with a cleaner causes it to start
    // all of the threads specified at its creation

    // Verify that registering a cleanable with a cleaner
    // adds a CleanableRef to the alive set and that cleaning it removes it.

    // Verify that closing a C4Peer before it is cleaned does not cause multiple
    // calls to the dispose method.

    // Verify that attempting to register with a closed cleaner throws

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
        val cleaner = Cleaner("test-cleaner", 1, 1000)

        val visited = AtomicBoolean()
        while (!visited.get()) {
            cleaner.register(Any()) { visited.set(true) }
        }
    }

    // Verify that stopping a cleaner kills its threads within the specified timeout,
    // even if there are no queued cleanables
    @Test
    fun testStopCleaner() {
        val peerCleaner = Cleaner("testThreads", 3, 200)
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
            override fun getNextZombie(): Cleanable {
                if (latch.count <= 0) {
                    Thread.sleep(1000)
                }
                val bomb = Cleanable {
                    latch.countDown()
                    throw Exception()
                }
                return bomb
            }
        }

        peerCleaner.startCleaner()
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(1, peerCleaner.runningThreads())
    }

    // Verify that an exception in the cleaner itself does not reduce the number of running threads
    @Test
    fun testCleanerError() {
        val latch = CountDownLatch(1)
        val peerCleaner = object : CleanerImpl("testCleanerError", 1, 500) {
            override fun getNextZombie(): Cleanable? {
                latch.countDown()
                throw Exception()
            }
        }

        peerCleaner.startCleaner()
        latch.await(5, TimeUnit.SECONDS)
        assertEquals(1, peerCleaner.runningThreads())

        peerCleaner.stopCleaner()
    }
}