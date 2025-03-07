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
package com.couchbase.lite.internal

import com.couchbase.lite.BaseTest
import com.couchbase.lite.CouchbaseLiteError
import com.couchbase.lite.LogDomain
import com.couchbase.lite.internal.exec.CBLExecutor
import com.couchbase.lite.internal.exec.ClientTask
import com.couchbase.lite.internal.exec.ExecutionService
import com.couchbase.lite.internal.exec.InstrumentedTask
import com.couchbase.lite.internal.logging.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Stack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


class ExecutionServiceTest : BaseTest() {
    private lateinit var cblService: ExecutionService

    @Before
    fun setUpExecutionServiceTest() {
        cblService = getExecutionService(CBLExecutor("Test worker"))
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
            startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            // second task is queued but should not pass us.
            Thread.sleep(1000)

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)

        synchronized(stack) {
            assertEquals("TWO", stack.pop())
            assertEquals("ONE", stack.pop())
        }
    }

    // A stopped serial executor throws on further attempts to schedule
    @Test
    fun testStoppedSerialExecutorRejects() {
        val executor = cblService.serialExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        assertThrows(ExecutionService.CloseableExecutor.ExecutorClosedException::class.java) {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
        }
    }

    // A stopped serial executor can finish currently queued tasks.
    @Test
    fun testStoppedSerialExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = cblService.serialExecutor

        executor.execute {
            startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            finishLatch.countDown()
        }

        executor.execute {
            startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        } catch (ignore: RejectedExecutionException) {
        }

        // allow the tasks to proceed.
        startLatch.countDown()

        assertTrue(finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
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
            startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
            Thread.sleep(1000)

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)

        // tasks should finish in reverse start order
        synchronized(stack) {
            assertEquals("ONE", stack.pop())
            assertEquals("TWO", stack.pop())
        }
    }

    // A stopped concurrent executor throws on further attempts to schedule
    @Test
    fun testStoppedConcurrentExecutorRejects() {
        val executor = cblService.concurrentExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        assertThrows(ExecutionService.CloseableExecutor.ExecutorClosedException::class.java) {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
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
            startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)

            finishLatch.countDown()
        }

        executor.execute {
            startLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.DATABASE, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        } catch (ignore: RejectedExecutionException) {
        }

        // allow the tasks to proceed.
        startLatch.countDown()

        assertTrue(finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }

    // The Concurrent Executor throws on fail
    @Test
    fun testConcurrentExecutorThrowAndDumpOnFail() {
        val latch = CountDownLatch(1)
        try {
            // queue len > 2 so that std deviation calculation kicks in.
            val exec = getExecutionService(CBLExecutor("Tiny test worker", 1, 1, LinkedBlockingQueue(3)))
                .concurrentExecutor
            exec.execute { latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS) } // this one blocks the thread
            exec.execute { }                                                // this one stays on the queue
            exec.execute { }                                                // two on the queue
            exec.execute { }                                                // this one fills the queue
            assertThrows(RejectedExecutionException::class.java) {
                exec.execute { }
            }                                                               // this one should fail
        } finally {
            latch.countDown()
        }
    }

    // Client Task tests.

    @Test
    fun testClientTaskReturnsValue() {
        val task = ClientTask { 42 }

        task.execute(1, TimeUnit.SECONDS)
        Thread.sleep(5)
        assertEquals(42, task.result)
    }

    @Test
    fun testClientTaskFail() {
        val err = RuntimeException("bang")
        val task = ClientTask<Int> { throw err }

        task.execute(1, TimeUnit.SECONDS)
        Thread.sleep(5)
        assertEquals(err, task.failure)
    }

    @Test
    fun testClientTaskTimeout() {
        val latch = CountDownLatch(1)
        val task = ClientTask {
            latch.await(10, TimeUnit.SECONDS)
            1
        }

        val startTime = System.currentTimeMillis()
        task.execute(1, TimeUnit.SECONDS)
        assertTrue(System.currentTimeMillis() - startTime < 2000)

        latch.countDown()
    }

    // Other tests

    // The main executor uses a single thread.... at least a few times
    @Test
    fun testDefaultThreadExecutor() {
        val n = 10
        val latch = CountDownLatch(n)
        val threads = MutableList(n) { AtomicReference<Thread>() }

        for (i in 0 until n) {
            cblService.defaultExecutor.execute {
                threads[i].set(Thread.currentThread())
                latch.countDown()
            }
        }
        latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)

        for (i in 1 until n) { assertEquals(threads[0].get(), threads[i].get()) }
    }

    // The scheduler schedules on the passed queue, with the proper delay.
    @Test
    fun testEnqueueWithDelay() {
        val finishLatch = CountDownLatch(1)

        val executor = cblService.defaultExecutor

        val elapsedTime = AtomicLong(System.currentTimeMillis())
        val delay: Long = 777
        cblService.postDelayedOnExecutor(delay, executor) {
            elapsedTime.set(System.currentTimeMillis() - elapsedTime.get())
            finishLatch.countDown()
        }

        assertTrue(finishLatch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))

        // within 10% is good enough
        assertEquals(0L, (elapsedTime.get() - delay) / (delay / 10))
    }

    // A delayed task can be cancelled
    @Test
    fun testCancelDelayedTask() {
        val completed = BooleanArray(1)

        // schedule the task far enough in the future so that there is plenty of time to cancel
        // it but not so far that we have to wait a long time to be sure it didn't run.
        val task = cblService.postDelayedOnExecutor(100, cblService.concurrentExecutor) {
            completed[0] = true
        }

        cblService.cancelDelayedTask(task)

        Thread.sleep(200)

        assertFalse(completed[0])
    }

    @Test
    fun testCantRunInstrumentedTaskTwice() {
        val barrier = CyclicBarrier(2)
        val exec = CBLExecutor("simple test worker", 1, 1, LinkedBlockingQueue(3))
        val task = InstrumentedTask({ }, null)
        var fail: Exception? = null
        val runnable = Runnable {
            try {
                task.run()
            } catch (e: Exception) {
                fail = e
            } finally {
                barrier.await()
            }
        }
        exec.execute(runnable)
        barrier.await()
        assertNull(fail)

        barrier.reset()
        exec.execute(runnable)
        barrier.await()
        assertTrue(fail is CouchbaseLiteError)
    }

    // If this test fails, it may bring down the entire test process
    @Test
    fun testExceptionDoesNotCauseCrashSerial() {
        val executor = CouchbaseLiteInternal.getExecutionService().serialExecutor
        val latch1 = CountDownLatch(1)
        executor.execute {
            try {
                throw RuntimeException("BANG")
            } finally {
                latch1.countDown()
            }
        }
        assertTrue(latch1.await(2, TimeUnit.SECONDS))

        val latch2 = CountDownLatch(1)
        executor.execute { latch2.countDown() }
        assertTrue(latch2.await(2, TimeUnit.SECONDS))
    }

    // If this test fails, it may bring down the entire test process
    @Test
    fun testExceptionDoesNotCauseCrashConcurrent() {
        val executor = CouchbaseLiteInternal.getExecutionService().concurrentExecutor
        val latch1 = CountDownLatch(1)
        executor.execute {
            try {
                throw RuntimeException("BANG")
            } finally {
                latch1.countDown()
            }
        }
        assertTrue(latch1.await(2, TimeUnit.SECONDS))

        val latch2 = CountDownLatch(1)
        executor.execute { latch2.countDown() }
        assertTrue(latch2.await(2, TimeUnit.SECONDS))
    }
}
