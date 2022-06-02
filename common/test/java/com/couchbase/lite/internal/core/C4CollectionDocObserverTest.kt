package com.couchbase.lite.internal.core

import com.couchbase.lite.AbstractIndex
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class C4CollectionDocObserverTest : C4BaseTest() {
    private val mockCollectionDocObserver = object : C4CollectionDocObserver.NativeImpl {
        override fun nCreate(coll: Long, docId: String?): Long = 0xd8597341L
        override fun nFree(peer: Long) = Unit
    }

    @Test
    fun testCreateC4CollectionDocObserver() {
        val impl = C4CollectionDocObserver.nativeImpl
        var obs: C4CollectionDocObserver? = null
        try {
            C4CollectionDocObserver.nativeImpl = mockCollectionDocObserver
            val coll = C4Collection.create(c4Database, "_default", "_default").peer
            obs = C4CollectionDocObserver.newObserver(coll, "test") {}
            assertNotNull(obs)
        } finally {
            obs?.close()
            C4CollectionDocObserver.nativeImpl = impl
        }
    }

    // Test mock callback
    @Test
    fun testDocumentChanged() {
        var i = 0;
        var obs: C4CollectionDocObserver? = null
        createRev("A", "1-aa", fleeceBody);
        val impl = C4CollectionDocObserver.nativeImpl
        try {
            C4CollectionDocObserver.nativeImpl = mockCollectionDocObserver
            val coll = C4Collection.create(c4Database, "default", "_default").peer
            obs = C4CollectionDocObserver.newObserver(coll, "A") { i++ }
            assertEquals(0, i)

            obs.docChanged()
            obs.docChanged()
            obs.docChanged()

            assertEquals(3, i)

        } finally {
            obs?.close()
            C4CollectionDocObserver.nativeImpl = impl
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