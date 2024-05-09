//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.core

import com.couchbase.lite.internal.QueryLanguage
import com.couchbase.lite.internal.core.impl.NativeC4QueryObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit


const val JSON_QUERY =
    "['AND', ['=', ['array_count()', ['.', 'contact', 'phone']], 2], ['=', ['.', 'gender'], " + "'male']]"

val mockMockQueryEnumerator = object : C4QueryEnumerator.NativeImpl {
    override fun nNext(peer: Long) = false
    override fun nFree(peer: Long) = Unit
    override fun nGetColumns(peer: Long) = 0L
    override fun nGetMissingColumns(peer: Long) = 0L
}

val mockNativeQueryObserver = object : C4QueryObserver.NativeImpl {
    override fun nCreate(token: Long, c4Query: Long): Long = 0xdeadbea7L
    override fun nEnable(peer: Long) = Unit
    override fun nFree(peer: Long) = Unit
}

class C4QueryObserverTest : C4BaseTest() {
    private val query: String = json5(JSON_QUERY)
    private val mockQEnum = C4QueryEnumerator(mockMockQueryEnumerator, 0x0a1fa1faL)

    @Test
    fun testCreateC4QueryObserver() {
        val c4Query = C4Query.create(c4Database, QueryLanguage.JSON, query)
        assertNotNull(
            C4QueryObserver.create(
                mockNativeQueryObserver,
                { mockQEnum },
                c4Query,
                { _, _ -> })
        )
    }

    @Test
    fun testQueryChanged() {
        var i = 0

        val c4Query = C4Query.create(c4Database, QueryLanguage.JSON, query)
        val obs = C4QueryObserver.create(
            mockNativeQueryObserver,
            { mockQEnum },
            c4Query,
            { result, err ->
                i++
                assertSame(mockQEnum, result)
                assertNull(err)
                result?.close()
            })


        val token = obs.token
        C4QueryObserver.onQueryChanged(token, 5L, 0, 0, null)
        C4QueryObserver.onQueryChanged(token, 5L, 0, 0, null)
        C4QueryObserver.onQueryChanged(token, 5L, 0, 0, null)

        assertEquals(3, i)
    }

    // Check that concurrent create and close don't cause obvious crashes.
    @Test
    fun testConcurrentCreateAndRelease() {
        val dbLock = Any()
        val nativeImpl = NativeC4QueryObserver()
        val latch = CountDownLatch(10)
        val barrier = CyclicBarrier(10)
        (0..9).map {
            Thread {
                val q = C4Query.create(c4Database, QueryLanguage.JSON, query)

                barrier.await()
                val obs = C4QueryObserver.create(nativeImpl, { mockQEnum }, q, { _, _ -> })
                // C4QueryObserver.enable is not thread safe: mock the dbLock
                synchronized(dbLock) { obs.enable(); }

                barrier.await()
                obs.close()

                latch.countDown()
            }
        }.forEach { it.start() }

        assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS))
    }
}
