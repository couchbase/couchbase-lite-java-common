package com.couchbase.lite.internal.core

import com.couchbase.lite.AbstractIndex
import com.couchbase.lite.ChangeListener
import com.couchbase.lite.LiteCoreException
import com.couchbase.lite.QueryChange
import com.couchbase.lite.internal.listener.ChangeListenerToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Test


class C4QueryObserverTest : C4BaseTest() {
    private val c4QueryObserverMock = object : C4QueryObserver.NativeImpl {
        override fun nCreate(token: Long, c4Query: Long): Long = 0xdadL
        override fun nSetEnabled(peer: Long, enabled: Boolean) = Unit
        override fun nFree(peer: Long) = Unit
        override fun nGetEnumerator(peer: Long, forget: Boolean): Long = 0xdadL
    }

    @Before
    fun setUpC4QueryObserverTest() {
        C4QueryObserver.nativeImpl = c4QueryObserverMock
        C4QueryObserver.QUERY_OBSERVER_CONTEXT.clear()
    }

    @After
    fun tearDownC4QueryObserverTest() {
        C4QueryObserver.nativeImpl = NativeC4QueryObserver()
        C4QueryObserver.QUERY_OBSERVER_CONTEXT.clear()
    }

    @Test
    fun testCreateC4QueryObserver() {
        val c4Query = C4Query(
            c4Database.handle,
            AbstractIndex.QueryLanguage.JSON,
            json5(json5("['=', ['.', 'contact', 'address', 'state'], 'CA']"))
        )
        val token = ChangeListenerToken<QueryChange>(null, { })
        assertEquals(0, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())
        val observer = C4QueryObserver.create(c4Query, token) { _, _, _ -> }
        assertNotNull(observer)
        assertEquals(1, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())

        //create a second observer should increase size
        val token2 = ChangeListenerToken<QueryChange>(null, { })
        val observer2 = C4QueryObserver.create(c4Query, token2){_,_,_ ->}
        assertNotNull(observer2)
        assertEquals(2, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())
    }

    @Test
    fun testCloseC4QueryObserver() {
        val c4Query2 = C4Query(
            c4Database.handle, AbstractIndex.QueryLanguage.JSON, json5(
                "['AND', " +
                        "['=', ['array_count()', ['.', 'contact', 'phone']], 2]," +
                        "['=', ['.', 'gender'], " + "'male']]"
            )
        )
        val token = ChangeListenerToken<QueryChange>(null, { })
        val observerClose = C4QueryObserver.create(c4Query2, token){_,_,_->}
        assertEquals(1, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())
        observerClose.close()
        assertEquals(0, C4QueryObserver.QUERY_OBSERVER_CONTEXT.size())
    }

    @Test
    fun testQueryChanged() {
        var callCount = 0;
        val c4Query = C4Query(
            c4Database.handle, AbstractIndex.QueryLanguage.JSON, json5(
                "['AND', ['=', ['array_count()', ['.', 'contact', 'phone']], 2],['=', ['.', 'gender'], "
                        + "'male']]"
            )
        )
        val token = ChangeListenerToken<QueryChange>(null, { })
        val c4QueryObserver =
            C4QueryObserver(0xdacL, c4QueryObserverMock, c4Query, token)
            { _: ChangeListenerToken<QueryChange>, _: C4QueryEnumerator?, _: LiteCoreException? ->
                callCount++;
            }
        c4QueryObserver.queryChanged();
        assertEquals(1, callCount)
    }

}