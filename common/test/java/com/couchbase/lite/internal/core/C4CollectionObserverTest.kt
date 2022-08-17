package com.couchbase.lite.internal.core

import com.couchbase.lite.Collection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class C4CollectionObserverTest : C4BaseTest() {
    private val mockCollectionObserver = object : C4CollectionObserver.NativeImpl {
        override fun nCreate(coll: Long): Long = 0xac6b2ed7L
        override fun nGetChanges(peer: Long, maxChanges: Int): Array<C4DocumentChange> {
            return arrayOf()
        }

        override fun nFree(peer: Long) = Unit
    }

    @Test
    //test collection observer
    fun testCreateCollectionObserver() {
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionObserver.newObserver(mockCollectionObserver, coll.peer, {}).use { obs -> assertNotNull(obs) }
        }
    }

    //test observer collection callback
    @Test
    fun testCollectionCallBack() {
        var i = 0
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionObserver.newObserver(mockCollectionObserver, coll.peer, { i++ }).use { obs ->
                C4CollectionObserver.callback(obs.peer)
                C4CollectionObserver.callback(obs.peer)
                C4CollectionObserver.callback(obs.peer)
            }
        }

        assertEquals(3, i)
    }

    /**
     * Functional tests
     */

    //Test simple collection observer
    @Test
    fun testCollObserver() {
        var i = 0
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionObserver.newObserver(coll.peer, { i++ }).use { obs ->
                assertEquals(0, i)
                createRev(c4Database, "A", "1-aa", fleeceBody)
                assertEquals(1, i)
                createRev(c4Database, "B", "1-bb", fleeceBody)
                assertEquals(1, i)

                checkChanges(obs, arrayListOf("A", "B"), arrayListOf("1-aa", "1-bb"), false)

                createRev(c4Database, "B", "2-bbbb", fleeceBody)
                assertEquals(2, i)
                createRev(c4Database, "C", "1-cc", fleeceBody)
                assertEquals(2, i)

                checkChanges(obs, arrayListOf("B", "C"), arrayListOf("2-bbbb", "1-cc"), false)
            }

            //no call back if observer is closed
            createRev(c4Database, "A", "2-aaaa", fleeceBody)
            assertEquals(2, i)
        }
    }

    //Test observer on multiple _default collection instances
    @Test
    fun testObserverOnMultiCollectionInstances() {
        var i = 0
        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionObserver.newObserver(coll.peer, { i++ }).use { obs ->

                val otherDb =
                    C4Database.getDatabase(dbParentDirPath, dbName, flags, C4Constants.EncryptionAlgorithm.NONE, null)

                createRev(otherDb, "c", "1-cc", fleeceBody)
                createRev(otherDb, "d", "1-dd", fleeceBody)
                createRev(otherDb, "e", "1-ee", fleeceBody)

                assertEquals(1, i)
                checkChanges(obs, arrayListOf("c", "d", "e"), arrayListOf("1-cc", "1-dd", "1-ee"), true)
            }
        }
    }

    //Test multiple observers on the same collection
    @Test
    fun testMultipleObserversOnTheSameColl() {
        var i = 0
        var j = 0

        C4Collection.create(c4Database, Collection.DEFAULT_NAME, Collection.DEFAULT_NAME).use { coll ->
            C4CollectionObserver.newObserver(coll.peer, { i++ }).use { obs1 ->
                C4CollectionObserver.newObserver(coll.peer, { j++ }).use { obs2 ->
                    createRev(c4Database, "A", "1-aa", fleeceBody)
                    assertEquals(1, i)
                    assertEquals(1, j)

                    checkChanges(obs1, arrayListOf("A"), arrayListOf("1-aa"), false)
                    checkChanges(obs2, arrayListOf("A"), arrayListOf("1-aa"), false)

                    createRev(c4Database, "A", "2-aaaa", fleeceBody)
                    assertEquals(2, i)
                    assertEquals(2, j)

                    checkChanges(obs1, arrayListOf("A"), arrayListOf("2-aaaa"), false)
                    checkChanges(obs2, arrayListOf("A"), arrayListOf("2-aaaa"), false)
                }
            }
        }
    }

    //helper method
    private fun checkChanges(
        observer: C4CollectionObserver,
        expectedDocIds: List<String>,
        expectedRevIds: List<String>,
        external: Boolean,
    ) {
        val changes = observer.getChanges(100)
        assertNotNull(changes)
        assertEquals(expectedDocIds.size, changes.size)
        for (i in changes.indices) {
            assertEquals(expectedDocIds[i], changes[i].docID)
            assertEquals(expectedRevIds[i], changes[i].revID)
            assertEquals(external, changes[i].isExternal)
        }
    }
}
