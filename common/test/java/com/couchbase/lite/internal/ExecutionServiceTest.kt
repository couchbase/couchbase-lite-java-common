//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal

import com.couchbase.lite.BaseTest
import com.couchbase.lite.LogDomain
import com.couchbase.lite.internal.exec.CBLExecutor
import com.couchbase.lite.internal.exec.ExecutionService
import com.couchbase.lite.internal.support.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Stack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class ExecutionServiceTest : BaseTest() {
    private lateinit var cblService: ExecutionService

    @Before
    fun setUpExecutionServiceTest() {
        cblService = getExecutionService(CBLExecutor("test worker #"))
    }

    // Serial Executor tests

    // The serial executor executes in order.
    @Test
    fun testSerialExecutor() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val stack = Stack<String>()

        val executor = cblService.serialExecutor

        executor.execute {
            try {
                startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
                // second task is queued but should not pass us.
                Thread.sleep(1000)
            } catch (ignore: InterruptedException) {
            }

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        try {
            finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }

        synchronized(stack) {
            assertEquals("TWO", stack.pop())
            assertEquals("ONE", stack.pop())
        }
    }

    // A stopped serial executor throws on further attempts to schedule
    @Test(expected = RejectedExecutionException::class)
    fun testStoppedSerialExecutorRejects() {
        val executor = cblService.serialExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
    }

    // A stopped serial executor can finish currently queued tasks.
    @Test
    fun testStoppedSerialExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = cblService.serialExecutor

        executor.execute {
            try {
                startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        executor.execute {
            try {
                startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        } catch (expected: RejectedExecutionException) {
        }

        // allow the tasks to proceed.
        startLatch.countDown()

        try {
            assertTrue(finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        } catch (ignore: InterruptedException) {
        }

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }


    // Concurrent Executor tests

    // The concurrent executor can execute out of order.
    @Test
    fun testConcurrentExecutor() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val stack = Stack<String>()

        val executor = cblService.concurrentExecutor

        executor.execute {
            try {
                startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
                Thread.sleep(1000)
            } catch (ignore: InterruptedException) {
            }

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        try {
            finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }

        // tasks should finish in reverse start order
        synchronized(stack) {
            assertEquals("ONE", stack.pop())
            assertEquals("TWO", stack.pop())
        }
    }

    // A stopped concurrent executor finishes currently queued tasks.
    @Test
    fun testStoppedConcurrentExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = cblService.concurrentExecutor

        // enqueue two tasks
        executor.execute {
            try {
                startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        executor.execute {
            try {
                startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (ignore: InterruptedException) {
            }

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        } catch (expected: RejectedExecutionException) {
        }

        // allow the tasks to proceed.
        startLatch.countDown()

        try {
            assertTrue(finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        } catch (ignore: InterruptedException) {
        }

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }

    // A stopped concurrent executor throws on further attempts to schedule
    @Test(expected = RejectedExecutionException::class)
    fun testStoppedConcurrentExecutorRejects() {
        val executor = cblService.concurrentExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
    }


    // Implementation tests
    // These are tests of the platform specific implementations of the ExecutionService

    // The main executor always uses the same thread.
    @Test
    fun testDefaultThreadExecutor() {
        val latch = CountDownLatch(2)

        val threads = arrayOfNulls<Thread>(2)

        cblService.defaultExecutor.execute {
            threads[0] = Thread.currentThread()
            latch.countDown()
        }

        cblService.defaultExecutor.execute {
            threads[1] = Thread.currentThread()
            latch.countDown()
        }

        try {
            latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }

        assertEquals(threads[0], threads[1])
    }

    // The scheduler schedules on the passed queue, with the proper delay.
    @Test
    fun testEnqueueWithDelay() {
        val finishLatch = CountDownLatch(1)

        val threads = arrayOfNulls<Thread>(2)

        val executor = cblService.defaultExecutor

        // get the thread used by the executor
        // note that only the mainThreadExecutor guarantees execution on a single thread...
        executor.execute { threads[0] = Thread.currentThread() }

        var t = System.currentTimeMillis()
        val delay: Long = 777
        cblService.postDelayedOnExecutor(
            delay,
            executor,
            {
                t = System.currentTimeMillis() - t
                threads[1] = Thread.currentThread()
                finishLatch.countDown()
            })

        try {
            assertTrue(finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
        } catch (ignore: InterruptedException) {
        }

        // within 10% is good enough
        assertEquals(0L, (t - delay) / (delay / 10))
        assertEquals(threads[0], threads[1])
    }

    // A delayed task can be cancelled
    @Test
    fun testCancelDelayedTask() {
        val completed = BooleanArray(1)

        // schedule far enough in the future so that there is plenty of time to cancel it
        // but not so far that we have to wait a long time to be sure it didn't run.
        val task = cblService.postDelayedOnExecutor(
            100,
            cblService.concurrentExecutor,
            { completed[0] = true })

        cblService.cancelDelayedTask(task)

        try {
            Thread.sleep(200)
        } catch (ignore: InterruptedException) {
        }

        assertFalse(completed[0])
    }

    @Test(expected = RejectedExecutionException::class)
    fun testThrowAndDumpOnFail() {
        val latch = CountDownLatch(1)
        try {
            // queue len > 2 so that std deviation calculation kicks in.
            val exec = getExecutionService(CBLExecutor("test worker #", 1, 1, LinkedBlockingQueue(3)))
                .concurrentExecutor
            exec.execute { latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS) } // this one blocks the thread
            exec.execute { }                                                // this one stays on the queue
            exec.execute { }                                                // two on the queue
            exec.execute { }                                                // this one fills the queue
            exec.execute { }                                                // this one should fail
        } finally {
            latch.countDown()
        }
    }
}
