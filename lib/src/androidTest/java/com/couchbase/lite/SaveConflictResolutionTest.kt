//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

const val docID = "doc1"

class SaveConflictResolutionTests : BaseTest() {

    //
    @Test
    fun testConflictHandler() {
        val doc = MutableDocument(docID)

        doc.setString("location", "Olympia")
        save(doc)

        assertEquals(1, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID).toMutable()
        val doc1b = db.getDocument(docID).toMutable()

        doc1a.setString("artist", "Sheep Jones")
        db.save(doc1a)

        assertEquals(2, db.getDocument(docID).generation())

        doc1b.setString("artist", "Holly Sears")

        var succeeded = db.save(doc1b) { cur: MutableDocument, old: Document? ->
            assertEquals(doc1b, cur)
            assertEquals(doc1a, old)
            assertEquals(2L, cur.generation())
            assertEquals(2L, old?.generation())
            true
        }
        assertTrue(succeeded)

        val newDoc = db.getDocument(docID)
        assertEquals(doc1b, newDoc)
        assertEquals(3L, newDoc.generation())

        val doc1c = db.getDocument(docID).toMutable()
        val doc1d = db.getDocument(docID).toMutable()

        doc1c.setString("artist", "Marjorie Morgan")
        db.save(doc1c)

        assertEquals(4L, db.getDocument(docID).generation())

        doc1d.setString("artist", "G. Charnelet-Vasselon")

        succeeded = db.save(doc1d) { cur: MutableDocument, old: Document? ->
            assertEquals(doc1d, cur)
            assertEquals(doc1c, old)
            assertEquals(4L, cur.generation())
            assertEquals(4L, old?.generation())
            cur.setString("artist", "Sheep Jones")
            true
        }
        assertTrue(succeeded)

        val curDoc = db.getDocument(docID)
        assertEquals("Olympia", curDoc.getString("location"))
        assertEquals("Sheep Jones", curDoc.getString("artist"))
        assertEquals(5L, curDoc.generation())
    }

    // keep the new doc, replacing the deleted doc
    @Test
    fun testConflictHandlerWithDeletedOldDoc1() {
        generateDocument(docID)

        assertEquals(1, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID)
        val doc1b = db.getDocument(docID).toMutable()

        db.delete(doc1a, ConcurrencyControl.LAST_WRITE_WINS)

        doc1b.setString("location", "Olympia")

        val succeeded = db.save(doc1b) { cur: MutableDocument, old: Document? ->
            assertNotNull(cur)
            assertNull(old)
            true
        }
        assertTrue(succeeded)

        assertEquals(doc1b, db.getDocument(docID))
    }

    // ignore the new doc, keeping the deletion.
    @Test
    fun testConflictHandlerWithDeletedOldDoc2() {
        generateDocument(docID)

        assertEquals(1, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID).toMutable()
        val doc1b = db.getDocument(docID).toMutable()

        db.delete(doc1a, ConcurrencyControl.LAST_WRITE_WINS)

        doc1b.setString("location", "Olympia")

        var succeeded = false
        try {
            succeeded = db.save(doc1b) { cur: MutableDocument, old: Document? ->
                assertNull(old)
                assertNotNull(cur)
                false
            }
            fail("save should not succeed!")
        } catch (err: CouchbaseLiteException) {
            assertEquals(CBLError.Code.CONFLICT, err.code)
        }
        assertFalse(succeeded)

        assertNull(db.getDocument(docID))

        val c4doc = db.c4Database.get(docID, false)
        assertNotNull(c4doc)
        assertTrue(c4doc.deleted())
    }

    // when the conflict handler returns false
    // failing the conflict handler causes no change
    @Test
    fun testCancelConflictHandler() {
        val doc = MutableDocument(docID)
        doc.setString("location", "Olympia")
        save(doc)

        assertEquals(1, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID).toMutable()
        val doc1b = db.getDocument(docID).toMutable()

        doc1a.setString("artist", "Sheep Jones")
        db.save(doc1a)

        assertEquals(2, db.getDocument(docID).generation())

        doc1b.setString("artist", "Holly Sears")

        var succeeded = false
        try {
            succeeded = db.save(doc1b) { cur: MutableDocument, old: Document? ->
                assertEquals(doc1b, cur)
                assertEquals(doc1a, old)
                false
            }
            fail("save should not succeed!")
        } catch (err: CouchbaseLiteException) {
            assertEquals(CBLError.Code.CONFLICT, err.code)
        }
        assertFalse(succeeded)

        val curDoc = db.getDocument(docID)
        assertEquals(curDoc, doc1a)

        // make sure no update to revision and generation
        assertEquals(doc1a.revID, curDoc.revID)
        assertEquals(2, curDoc.generation())

        val doc1c = db.getDocument(docID).toMutable()
        val doc1d = db.getDocument(docID).toMutable()

        doc1c.setString("artist", "Marjorie Morgan")
        db.save(doc1c)

        assertEquals(3, db.getDocument(docID).generation())

        doc1d.setString("artist", "G. Charnelet-Vasselon")

        try {
            succeeded = db.save(doc1d) { cur, _ ->
                cur.setString("artist", "Holly Sears")
                false
            }
            fail("save should not succeed!")
        } catch (err: CouchbaseLiteException) {
            assertEquals(CBLError.Code.CONFLICT, err.code)
        }
        assertFalse(succeeded)

        // make sure no update to revision and generation
        val newDoc = db.getDocument(docID)
        assertEquals(newDoc, doc1c)
        assertEquals(doc1c.revID, newDoc.revID)
        assertEquals(3, newDoc.generation())
    }

    @Test
    fun testCancelConflictHandlerCalledTwice() {
        val doc = MutableDocument(docID)
        doc.setString("location", "Olympia")
        save(doc)

        assertEquals(1, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID).toMutable()
        val doc1b = db.getDocument(docID).toMutable()

        doc1a.setString("artist", "Sheep Jones")
        db.save(doc1a)

        assertEquals(2, db.getDocument(docID).generation())

        doc1b.setString("artist", "Holly Sears")

        var count = 0
        var succeeded = db.save(doc1b) { cur: MutableDocument, old: Document? ->
                count++
                val doc1c = db.getDocument(docID).toMutable()
                if (!doc1c.getBoolean("second update")) {
                    assertEquals(2L, cur.generation())
                    assertEquals(2L, old?.generation())
                    doc1c.setBoolean("second update", true)
                    save(doc1c)
                    assertEquals(3L, db.getDocument(docID).generation())
                }
                val data = old?.toMap()?.toMutableMap() ?: mutableMapOf()
                for (key in cur.keys) { data[key] = cur.getValue(key) }
                cur.setData(data)
                cur.setString("edit", "local")
                true
            }
        assertTrue(succeeded)

        assertEquals(2, count)

        val newDoc = db.getDocument(docID)
        assertEquals(4, newDoc.generation()) // ??? is this right?
        assertEquals(newDoc.getString("location"), "Olympia")
        assertEquals(newDoc.getString("artist"), "Holly Sears")
        assertEquals(newDoc.getString("edit"), "local")
    }

    @Test
    fun testConflictHandlerThrowsException() {
        val doc = MutableDocument(docID)

        doc.setString("location", "Olympia")
        save(doc)

        assertEquals(1L, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID).toMutable()
        val doc1b = db.getDocument(docID).toMutable()

        doc1a.setString("artist", "Sheep Jones")
        db.save(doc1a)

        assertEquals(2L, db.getDocument(docID).generation())

        doc1b.setString("artist", "Holly Sears")

        var succeeded = false
        try {
            succeeded = db.save(doc1b) { _: MutableDocument, _: Document? -> throw IllegalStateException("freak out!") }
            fail("save should not succeed!")
        } catch (err: CouchbaseLiteException) {
            assertEquals(CBLError.Code.CONFLICT, err.code)
            assertEquals("freak out!", err.cause?.message)
        }
        assertFalse(succeeded)

        assertEquals(doc1a, db.getDocument(docID))
        assertEquals(2L, db.getDocument(docID).generation())
    }

    @Test
    fun testConflictHandlerWhenDocumentIsPurged() {
        val doc = MutableDocument(docID)

        doc.setString("location", "Olympia")
        save(doc)

        assertEquals(1L, db.getDocument(docID).generation())

        val doc1a = db.getDocument(docID).toMutable()

        db.purge(docID)

        doc1a.setString("artist", "Sheep Jones")

        var succeeded = false
        try {
            succeeded = db.save(doc1a) { _: MutableDocument, _: Document? -> true }
            fail("save should not succeed!")
        } catch (err: CouchbaseLiteException) {
            assertEquals(CBLError.Code.NOT_FOUND, err.code)
        }
        assertFalse(succeeded)
    }
}