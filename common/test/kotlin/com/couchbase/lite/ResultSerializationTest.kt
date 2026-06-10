//
// Copyright (c) 2026 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http:// www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.couchbase.lite

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.*
import org.junit.Test

/** Tests serialization-based Result and ResultSet accessors implemented in QueryExtensions.kt. */
class ResultSerializationTest : BaseQueryTest() {
    @Test @OptIn(ExperimentalSerializationApi::class)
    fun testResultToModel() {
        // Note: For simplicity this uses a class that implements DocumentModel, but it isn't necessary.
        val doc = MutableDocument("nigel")
        doc.setString("name", "Nigel")
        doc.setInt("age", 12)
        testCollection.save(doc)

        val query = testDatabase.createQuery("SELECT name, age FROM " + testCollection.fullName)
        val rows = query.execute().data<TestModel>()
        val iter = rows.iterator()

        assertTrue(iter.hasNext())
        val nigel: TestModel = iter.next()
        assertEquals("Nigel", nigel.name)
        assertEquals(12, nigel.age)
        assertNull(nigel.favorites)
        assertNull(nigel.documentMeta)  // query doesn't return `meta`

        assertFalse(iter.hasNext())
    }

    @Test @OptIn(ExperimentalSerializationApi::class)
    fun testResultToModelWithMeta() {
        val doc = MutableDocument("nigel")
        doc.setString("name", "Nigel")
        doc.setInt("age", 12)
        testCollection.save(doc)

        val query = testDatabase.createQuery("SELECT * as doc, meta() as meta FROM " + testCollection.fullName)
        val resultSet = query.execute()
        val rows = resultSet.data<TestModel>("doc", "meta")
        val iter = rows.iterator()

        assertTrue(iter.hasNext())
        val nigel: TestModel = iter.next()
        assertEquals("Nigel", nigel.name)
        assertEquals(12, nigel.age)
        assertNull(nigel.favorites)
        // Verify the documentMeta got set:
        assertEquals(doc.id, nigel.documentMeta?.id)
        assertEquals(doc.revisionID, nigel.documentMeta?.revisionID)

        assertFalse(iter.hasNext())
    }
}
