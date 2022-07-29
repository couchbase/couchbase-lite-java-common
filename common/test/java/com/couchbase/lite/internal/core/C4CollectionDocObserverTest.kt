package com.couchbase.lite.internal.core

import com.couchbase.lite.Collection
import com.couchbase.lite.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test


class C4CollectionDocObserverTest : C4BaseTest() {
    private val mockCollectionDocObserver = object : C4DocumentObserver.NativeImpl {
        override fun nCreate(coll: Long, docId: String?): Long = 0xd8597341L
        override fun nFree(peer: Long) = Unit
    }

    // Test creating a doc observer with mock native implementation
    @Test
    fun testCreateC4CollectionDocObserver() {
        C4Collection.create(c4Database, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionDocObserver.newObserver(mockCollectionDocObserver, coll.peer, "test", {})
                .use { assertNotNull(it) }
        }
    }

    // Test mock callback
    @Test
    fun testDocumentChanged() {
        var i = 0
        createRev("A", "1-aa", fleeceBody)
        C4Collection.create(c4Database, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionDocObserver.newObserver(mockCollectionDocObserver, coll.peer, "A", { i++ }).use { obs ->
                assertEquals(0, i)

                C4CollectionDocObserver.callback(obs.peer, "A", 0L)
                C4CollectionDocObserver.callback(obs.peer, "A", 0L)
                C4CollectionDocObserver.callback(obs.peer, "A", 0L)

                assertEquals(3, i)
            }
        }
    }

    /**
     * Functional Test
     */

    @Test
    fun testCollObserverWithCoreCallback() {
        var i = 0
        var obs: C4CollectionDocObserver? = null
        createRev(c4Database, "A", "1-aa", fleeceBody)
        try {
            C4Collection.create(c4Database, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
                obs = C4CollectionDocObserver.newObserver(coll.peer, "A") { i++ }

                assertEquals(0, i)

                createRev(c4Database, "A", "2-bb", fleeceBody)
                createRev(c4Database, "B", "1-bb", fleeceBody)

                assertEquals(1, i)
            }
        } finally {
            obs?.close()
        }
    }
}
