//
// Copyright (c) 2026 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.internal.fleece.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer


/** Uses Kotlin Serialization to create an object of type [T] from a query result.
 *  If the [key] parameter is non-null, it uses the result's value for that key as the source,
 *  instead of the entire result.
 *
 *  For example, if your query is `SELECT name, age, shoeSize FROM people` and you have a
 *  serializable `Person` class with properties `name`, `age` and `shoeSize`, you can call:
 *  `val person = result.data<Person>()`
 *
 *  Or you could use the query `SELECT * as person FROM people` and create the `Person` with
 *  `val person = result.data<Person>("person")`.
 *  */
@ExperimentalSerializationApi
inline fun <reified T> Result.data(key: String? = null): T =
    data(serializer(), key)

/** Uses Kotlin Serialization to create a [DocumentModel] instance of type [T] from the query result.
 *  This is a specialization of the one-parameter [data] method that adds a [metaKey] parameter.
 *
 *  The [metaKey] parameter is the name of the result property whose value comes from the N1QL
 *  `meta()` function; this is used to set the [DocumentModel.documentMeta] property of the result.
 *
 *  For example, if your query is `SELECT * as person, meta() as meta FROM people`, you would call
 *  `result.data<Person>("person", "meta")`.
 */
@ExperimentalSerializationApi
inline fun <reified T: DocumentModel> Result.data(key: String? = null,
                                                  metaKey: String? = null): T =
    data(serializer(), key, metaKey)

@ExperimentalSerializationApi
fun <T> Result.data(deserializer: DeserializationStrategy<T>,
                    key: String? = null,
                    metaKey: String? = null): T
{
    val columns = flValues
    val result = if (key == null) {
        if (deserializer.descriptor as? StructureKind == StructureKind.LIST) {
            // Deserializer wants a List, so pass the column values directly:
            deserializeFromFleece(columns, deserializer)
        } else {
            // Deserializer wants a Map, so smush the column names and values together:
            val size = columns.size
            val names = columnNames
            val iter = object: Iterator<Map.Entry<String,FLValue>> {
                var i = 0
                override fun hasNext() = i < size
                override fun next(): Map.Entry<String,FLValue> {
                    val entry = Entry(names[i], columns[i])
                    i++
                    return entry
                }
            }
            deserializeFromFleece(iter, size, deserializer)
        }
    } else {
        // Deserializing a single value from the result:
        val i = getIndexForKey(key)
        if (i < 0) throw CouchbaseLiteError("Query result has no property '$key'")
        deserializeFromFleece(columns[i], deserializer)
    }
    if (result is DocumentModel && metaKey != null)
        result.documentMeta = getDocumentMeta(metaKey)
    return result
}


/** Returns the query rows transformed by Kotlin Serialization into instances of [T]. */
@ExperimentalSerializationApi
inline fun <reified T> ResultSet.data(key: String? = null): Sequence<T> =
    data(serializer(), key)

@ExperimentalSerializationApi
inline fun <reified T: DocumentModel> ResultSet.data(key: String? = null,
                                                     metaKey: String? = null): Sequence<T> =
    data(serializer(), key, metaKey)

/** Returns the query rows transformed by Kotlin Serialization into instances of [T]. */
@ExperimentalSerializationApi
fun <T> ResultSet.data(deserializer: DeserializationStrategy<T>,
                       key: String? = null,
                       metaKey: String? = null): Sequence<T> =
    sequence {
        while (true) {
            val result = next() ?: break
            yield(result.data(deserializer, key, metaKey))
        }
    }


// A trivial implementation of [Map.Entry].
private class Entry(override val key: String, override val value: FLValue) : Map.Entry<String,FLValue>


// Creates a [DocumentMeta] from the "meta" column of a Result. (Note: It can't set the `collection`.)
private fun Result.getDocumentMeta(key: String): DocumentMeta? {
    val i = getIndexForKey(key)
    if (i < 0) throw CouchbaseLiteError("Query result has no property '$key'")
    val col = flValues[i]
    if (col.type != FLValue.DICT) return null
    val meta = col.asFLDict()
    val id = meta["id"]?.asString() ?: return null
    val revID = meta["revisionID"]?.asString() ?: return null
    return DocumentMeta(null, id, revID)
}