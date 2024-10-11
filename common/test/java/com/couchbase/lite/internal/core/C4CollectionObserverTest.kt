package com.couchbase.lite.internal.core

import com.couchbase.lite.Collection
import com.couchbase.lite.LiteCoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class C4CollectionObserverTest : C4BaseTest() {
    private val mockCollectionObserver = object : C4CollectionObserver.NativeImpl {
        override fun nCreate(token: Long, coll: Long): Long = 0xac6b2ed7L
        override fun nGetChanges(peer: Long, maxChanges: Int): Array<C4DocumentChange> {
            return arrayOf()
        }

        override fun nFree(peer: Long) = Unit
    }

    @Test
    //test collection observer
    fun testCreateCollectionObserver() {
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionObserver.newObserver(mockCollectionObserver, peer, {}).use { obs -> assertNotNull(obs) }
            }
        }
    }

    //test observer collection callback
    @Test
    fun testCollectionCallBack() {
        var i = 0
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionObserver.newObserver(mockCollectionObserver, peer, { i++ }).use { obs ->
                    C4CollectionObserver.callback(obs.token)
                    C4CollectionObserver.callback(obs.token)
                    C4CollectionObserver.callback(obs.token)
                }
            }
        }

        assertEquals(3, i)
    }

    /**
     * Functional tests
     */

    // Test simple collection observer
    // This test will fail on LiteCore using Version Vectors
    // C4DocumentChange.revId under VV contains not the latest version,
    // but a semicolon separated list of versions
    @Test
    fun testCollObserver() {
        var i = 0
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionObserver.newObserver(peer, { i++ }).use { obs ->
                    assertEquals(0, i)
                    val revId1 = getTestRevId("aa", 1)
                    createRev(coll, "A", revId1, fleeceBody)
                    assertEquals(1, i)
                    val revId2 = getTestRevId("bb", 1)
                    createRev(coll, "B", revId2, fleeceBody)
                    assertEquals(1, i)

                    checkChanges(obs, arrayListOf("A", "B"), arrayListOf(revId1, revId2), false)

                    val revId3 = getTestRevId("bbbb", 2)
                    createRev(coll, "B", revId3, fleeceBody)
                    assertEquals(2, i)
                    val revId4 = getTestRevId("cc", 1)
                    createRev(coll, "C", revId4, fleeceBody)
                    assertEquals(2, i)

                    checkChanges(obs, arrayListOf("B", "C"), arrayListOf(revId3, revId4), false)
                }

                // no call back if observer is closed
                createRev(coll, "A", getTestRevId("aaaa", 2), fleeceBody)
                assertEquals(2, i)
            }
        }
    }

    //Test observer on multiple _default collection instances
    @Test
    fun testObserverOnMultiCollectionInstances() {
        var i = 0
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionObserver.newObserver(peer, { i++ }).use { obs ->
                    val otherColl = C4Database.getDatabase(dbParentDirPath, dbName)
                        .getCollection(coll.name, coll.scope)
                    assertNotNull(otherColl)

                    val revId1 = getTestRevId("cc", 1)
                    createRev(otherColl, "c", revId1, fleeceBody)
                    val revId2 = getTestRevId("dd", 1)
                    createRev(otherColl, "d", revId2, fleeceBody)
                    val revId3 = getTestRevId("ee", 1)
                    createRev(otherColl, "e", revId3, fleeceBody)

                    assertEquals(1, i)
                    checkChanges(obs, arrayListOf("c", "d", "e"), arrayListOf(revId1, revId2, revId3), true)
                }
            }
        }
    }

    // Test multiple observers on the same collection
    // This test will fail on LiteCore using Version Vectors
    // C4DocumentChange.revId under VV contains not the latest version,
    // but a semicolon separated list of versions
    @Test
    fun testMultipleObserversOnTheSameColl() {
        var i = 0
        var j = 0

        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            coll.voidWithPeerOrThrow<LiteCoreException> { peer ->
                C4CollectionObserver.newObserver(peer, { i++ }).use { obs1 ->
                    C4CollectionObserver.newObserver(peer, { j++ }).use { obs2 ->
                        val revId1 = getTestRevId("aa", 1)
                        createRev(coll, "A", revId1, fleeceBody)
                        assertEquals(1, i)
                        assertEquals(1, j)

                        checkChanges(obs1, arrayListOf("A"), arrayListOf(revId1), false)
                        checkChanges(obs2, arrayListOf("A"), arrayListOf(revId1), false)

                        val revId2 = getTestRevId("aaaa", 2)
                        createRev(coll, "A", revId2, fleeceBody)
                        assertEquals(2, i)
                        assertEquals(2, j)

                        checkChanges(obs1, arrayListOf("A"), arrayListOf(revId2), false)
                        checkChanges(obs2, arrayListOf("A"), arrayListOf(revId2), false)
                    }
                }
            }
        }
    }
}
