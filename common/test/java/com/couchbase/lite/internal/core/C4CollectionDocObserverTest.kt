package com.couchbase.lite.internal.core

import com.couchbase.lite.Collection
import com.couchbase.lite.LiteCoreException
import com.couchbase.lite.Scope
import com.couchbase.lite.internal.utils.StringUtils
import org.junit.Assert
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
        override fun nCreate(token: Long, coll: Long, docId: String?): Long = 0xd8597341L
        override fun nFree(peer: Long) = Unit
    }

    // Test creating a doc observer with mock native implementation
    @Test
    fun testCreateC4CollectionDocObserver() {
        C4Collection.create(c4Database, collName, scopeName).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionDocObserver.newObserver(mockCollectionDocObserver, peer, "test", {}).use {
                    Assert.assertNotNull(it)
                }
            }
        }
    }

    // Test mock callback
    @Test
    fun testDocumentChanged() {
        var i = 0
        createRev("A", getTestRevId("aa", 1), fleeceBody)
        C4Collection.create(c4Database, collName, scopeName).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionDocObserver.newObserver(mockCollectionDocObserver, peer, "A", { i++ }).use { obs ->
                    Assert.assertEquals(0, i)

                    C4CollectionDocObserver.callback(obs.token, 43L, "A")
                    C4CollectionDocObserver.callback(obs.token, 43L, "A")
                    C4CollectionDocObserver.callback(obs.token, 43L, "A")

                    Assert.assertEquals(3, i)
                }
            }
        }
    }

    @Test
    fun testCollObserverWithCoreCallback() {
        val collection = C4Collection.create(c4Database, collName, scopeName)
        createRev(collection, "A", getTestRevId("aa", 1), fleeceBody)

        var i = 0
        C4Collection.create(c4Database, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionDocObserver.newObserver(peer, "A", { i++ }).use {
                    Assert.assertEquals(0, i)

                    createRev(coll, "A", getTestRevId("bb", 2), fleeceBody)
                    createRev(coll, "B", getTestRevId("bb", 1), fleeceBody)

                    Assert.assertEquals(1, i)
                }
            }
        }
    }
}
