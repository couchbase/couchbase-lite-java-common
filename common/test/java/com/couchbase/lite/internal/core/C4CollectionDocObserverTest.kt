package com.couchbase.lite.internal.core


import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test

class C4CollectionDocObserverTest : C4BaseTest() {
    private val mockCollectionDocObserver = object : C4CollectionDocObserver.NativeImpl {
        override fun nCreate(coll: Long, docId: String?): Long = 0xd8597341L
        override fun nFree(peer: Long) = Unit
    }

    // Test creating a doc observer with mock native implementation
    @Test
    fun testCreateC4CollectionDocObserver() {
        val coll = C4Collection.create(c4Database, "_default", "_default").peer
        C4CollectionDocObserver.newObserver(mockCollectionDocObserver, coll, "test") {}.use { obs ->
            assertNotNull(obs)
            obs.close()
        }
    }

    // Test mock callback
    @Test
    fun testDocumentChanged() {
        var i = 0
        var obs: C4CollectionDocObserver? = null
        createRev("A", "1-aa", fleeceBody)
        try {
            val coll = C4Collection.create(c4Database, "default", "_default").peer
            obs = C4CollectionDocObserver.newObserver(mockCollectionDocObserver, coll, "A") { i++ }
            assertEquals(0, i)

            C4CollectionDocObserver.callback(obs.peer, "A", 0L)
            C4CollectionDocObserver.callback(obs.peer, "A", 0L)
            C4CollectionDocObserver.callback(obs.peer, "A", 0L)

            assertEquals(3, i)

        } finally {
            obs?.close()
        }
    }

    /**
     * Functional Tests
     */

    // This tests that a revision to a document in a database will be listened as a document revision in _default collection
    @Test
    @Ignore("This test should pass when we wire db methods to call collection methods")
    fun testDbObserverWithCoreCallback() {
        var i = 0
        var obs: C4CollectionDocObserver? = null

        createRev("A", "1-aa", fleeceBody)

        try {
            val coll = C4Collection.create(c4Database, "_default", "_default").peer
            obs = C4CollectionDocObserver.newObserver(coll, "A") { i++ }

            assertEquals(0, i)

            createRev("A", "2-bb", fleeceBody)
            createRev("B", "1-bb", fleeceBody)
            assertEquals(1, i)
        } finally {
            obs?.close()
        }
    }

    @Test
    @Ignore("Can't create a C4Document at the moment, getFromCollection always return 0L")
    fun testCollObserverWithCoreCallback() {
        var i = 0
        var obs: C4CollectionDocObserver? = null

        try {
            val coll = C4Collection.create(c4Database, "_default", "_default")
            obs = C4CollectionDocObserver.newObserver(coll.peer, "A") { i++ }

            createRevInCollection(coll, "A", "1-aa", fleeceBody, obs)

            assertEquals(0, i)

            createRevInCollection(coll, "A", "2-bb", fleeceBody, obs)
            createRevInCollection(coll, "B", "1-bb", fleeceBody, obs)
            assertEquals(1, i)
        } finally {
            obs?.close()
        }
    }
}
