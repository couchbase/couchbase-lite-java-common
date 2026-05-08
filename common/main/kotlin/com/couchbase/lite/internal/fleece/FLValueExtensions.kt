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

package com.couchbase.lite.internal.fleece


/** Returns a []Collection<FLValue>] with the same contents as this FLArray. */
val FLArray.asCollection: Collection<FLValue> get() = FLArrayAsCollection(this)


private class FLArrayAsCollection(val array: FLArray): Collection<FLValue> {
    override val size: Int = array.count().toInt()

    override fun isEmpty(): Boolean = (size == 0)

    override fun contains(element: FLValue): Boolean {
        for (i in 0L ..< size.toLong())
            if (array[i] == element) return true
        return false
    }

    override fun containsAll(elements: Collection<FLValue>): Boolean =
        elements.all { this.contains(it) }

    override fun iterator(): Iterator<FLValue> {
        return object: Iterator<FLValue> {
            var iter = array.iterator()

            override fun hasNext(): Boolean = iter.value != null

            override fun next(): FLValue {
                val value = iter.value ?: throw IndexOutOfBoundsException()
                iter.next()
                return value
            }
        }
    }
}


/** Returns a [Map<String,FLValue>] with the same contents as this FLDict. */
val FLDict.asMap: Map<String,FLValue> get() = FLDictAsMap(this)

private class FLDictAsMap(val dict: FLDict): Map<String,FLValue> {
    override val size: Int = dict.count().toInt()

    override fun isEmpty(): Boolean = (dict.count() == 0L)

    override fun get(key: String): FLValue? = dict.get(key)

    override fun containsKey(key: String): Boolean = (dict.get(key) != null)

    override fun containsValue(value: FLValue): Boolean {
        val iter = dict.iterator()
        while (iter.key != null) {
            if (iter.value == value) return true
            iter.next()
        }
        return false
    }

    class Entry(override val key: String, override val value: FLValue) : Map.Entry<String,FLValue>

    override val entries: Set<Map.Entry<String, FLValue>> get() {
        val iter = dict.iterator()
        return buildSet {
            while (true) {
                val key = iter.key ?: break
                add(Entry(key, iter.value))
                iter.next()
            }
        }
    }

    override val keys: Set<String> get() {
        val iter = dict.iterator()
        return buildSet {
            while (true) {
                add(iter.key ?: break)
                iter.next()
            }
        }
    }

    override val values: Collection<FLValue> get() {
        val iter = dict.iterator()
        return buildList {
            while (iter.key != null) {
                add(iter.value)
                iter.next()
            }
        }
    }
}
