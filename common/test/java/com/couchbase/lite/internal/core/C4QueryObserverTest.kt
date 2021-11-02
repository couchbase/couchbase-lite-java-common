package com.couchbase.lite.internal.core

import com.couchbase.lite.AbstractIndex
import org.junit.Assert.*
import org.junit.Test


const val JSON_QUERY =
    "['AND', ['=', ['array_count()', ['.', 'contact', 'phone']], 2], ['=', ['.', 'gender'], " + "'male']]"

class C4QueryObserverTest : C4BaseTest() {
    private val mockQueryEnumerator = object : C4QueryEnumerator.NativeImpl {
        override fun nNext(peer: Long) = false
        override fun nFree(peer: Long) = Unit
        override fun nGetColumns(peer: Long) = 0L
        override fun nGetMissingColumns(peer: Long) = 0L
        override fun nGetFullTextMatchCount(peer: Long) = 0L
        override fun nGetFullTextMatch(peer: Long, idx: Int) = 0L
    }

    private val mockQueryObserver = object : C4QueryObserver.NativeImpl {
        override fun nCreate(token: Long, c4Query: Long): Long = 0xdeadbea7L
        override fun nSetEnabled(peer: Long, enabled: Boolean) = Unit
        override fun nFree(peer: Long) = Unit
        override fun nGetEnumerator(peer: Long, forget: Boolean): Long = 0x0L
    }

    private val query: String = json5(JSON_QUERY)

    @Test
    fun testCreateC4QueryObserver() {
        val impl = C4QueryObserver.nativeImpl
        try {
            C4QueryObserver.nativeImpl = mockQueryObserver
            val ctxtSize = C4QueryObserver.QUERY_OBSERVER_CONTEXT.size()

            C4Query(c4Database.handle, AbstractIndex.QueryLanguage.JSON, query).use { c4Query ->
                C4QueryObserver.create(c4Query, { _, _ -> }).use { obs1 ->
                    assertNotNull(obs1)
                    assertEquals(ctxtSize + 1, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())

                    // create a second observer should increase size
                    C4QueryObserver.create(c4Query, { _, _ -> }).use { obs2 ->
                        assertNotNull(obs2)
                        assertEquals(ctxtSize + 2, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())
                    }
                }
            }
            assertEquals(ctxtSize, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())
        } finally {
            C4QueryObserver.nativeImpl = impl
        }
    }

    @Test
    fun testQueryChanged() {
        var i = 0
        var expected: C4QueryEnumerator? = null

        C4Query(c4Database.handle, AbstractIndex.QueryLanguage.JSON, query).use { c4Query ->
            val obs = C4QueryObserver(
                mockQueryObserver,
                {
                    expected = C4QueryEnumerator(mockQueryEnumerator, 0xdeadbea7L)
                    expected
                },
                0xba5eba11L,
                c4Query,
                { result, err ->
                    i++
                    assertEquals(expected, result)
                    assertNull(err)
                    result?.close()
                })

            obs.queryChanged()
            obs.queryChanged()
            obs.queryChanged()

            assertEquals(3, i)
        }
    }
}