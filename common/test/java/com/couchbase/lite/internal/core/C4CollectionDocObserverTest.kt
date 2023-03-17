package com.couchbase.lite.internal.core

import com.couchbase.lite.Collection
import com.couchbase.lite.Scope
import com.couchbase.lite.internal.utils.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test


class C4CollectionDocObserverTest : C4BaseTest() {
    private lateinit var scopeName: String
    private lateinit var collName: String

    @Before
    fun setupC4CollectionDocObserverTest() {
        scopeName = StringUtils.getUniqueName("C4CollectionDocObserverTest_SCOPE", 4)
        collName = StringUtils.getUniqueName("C4CollectionDocObserverTest_SCOPE", 4)
    }


    private val mockCollectionDocObserver = object : C4DocumentObserver.NativeImpl {
        override fun nCreate(coll: Long, docId: String?): Long = 0xd8597341L
        override fun nFree(peer: Long) = Unit
    }

    // Test creating a doc observer with mock native implementation
    @Test
    fun testCreateC4CollectionDocObserver() {
        C4Collection.create(c4Database, collName, scopeName).use { coll ->
            C4CollectionDocObserver.newObserver(mockCollectionDocObserver, coll.peer, "test", {})
                .use { assertNotNull(it) }
        }
    }

    // Test mock callback
    @Test
    fun testDocumentChanged() {
        var i = 0
        createRev("A", "1-aa", fleeceBody)
        C4Collection.create(c4Database, collName, scopeName).use { coll ->
            C4CollectionDocObserver.newObserver(mockCollectionDocObserver, coll.peer, "A", { i++ }).use { obs ->
                assertEquals(0, i)

                C4CollectionDocObserver.callback(obs.peer, "A")
                C4CollectionDocObserver.callback(obs.peer, "A")
                C4CollectionDocObserver.callback(obs.peer, "A")

                assertEquals(3, i)
            }
        }
    }

    @Test
    fun testCollObserverWithCoreCallback() {
        var i = 0

        val collection = C4Collection.create(c4Database, collName, scopeName)
        createRev(collection, "A", "1-aa", fleeceBody)

        var obs: C4CollectionDocObserver? = null
        C4Collection.create(c4Database, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            obs = C4CollectionDocObserver.newObserver(coll.peer, "A") { i++ }
            assertEquals(0, i)

            createRev(coll, "A", "2-bb", fleeceBody)
            createRev(coll, "B", "1-bb", fleeceBody)

            assertEquals(1, i)
        }
    }
}

