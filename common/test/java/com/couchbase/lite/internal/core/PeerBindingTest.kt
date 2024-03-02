//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.core


import com.couchbase.lite.BaseTest
import com.couchbase.lite.CouchbaseLiteError
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding
import org.junit.Assert
import org.junit.Test

class PeerBindingTest {
    /**
     * Tests for TaggedWeakPeerBinding
     */

    // Binding valid key value pair should create a binding in map
    @Test
    fun testCreateBasicPeerBinding() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            val key = binding.reserveKey()
            val mockObject = object {}

            // reserve a key should create a new entry in the map
            Assert.assertEquals(1, binding.size())
            Assert.assertEquals(null, binding.getBinding(key))

            binding.bind(key, mockObject)
            Assert.assertEquals(1, binding.size())
            Assert.assertEquals(mockObject, binding.getBinding(key))

            // it should be fine to bind the same object and key again
            binding.bind(key, mockObject)
            Assert.assertEquals(1, binding.size())
            Assert.assertEquals(mockObject, binding.getBinding(key))
        } finally {
            binding.clear()
        }
    }

    // Trying to bind an object to a key that's not reserved leads to exception
    @Test
    fun testBindWithoutKey() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            BaseTest.assertThrows(CouchbaseLiteError::class.java) { binding.bind(4345, object {}) }
        } finally {
            binding.clear()
        }
    }

    // Rebinding an existing mapping with a different object results in exception
    @Test
    fun testRebindPeerWithDifferentObject() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            val keyReserve = binding.reserveKey()
            val object1 = object {}
            val object2 = object {}

            binding.bind(keyReserve, object1)
            BaseTest.assertThrows(CouchbaseLiteError::class.java) { binding.bind(keyReserve, object2) }
        } finally {
            binding.clear()
        }
    }

    // Getting a binding that doesn't exist returns null
    @Test
    fun testGetNonExistingBinding() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            Assert.assertNull(binding.getBinding(234))
        } finally {
            binding.clear()
        }
    }

    // Getting an out of bound key for lookup should throw an exception right away
    @Test
    fun testGetOutOfBoundKey() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            BaseTest.assertThrows(IllegalArgumentException::class.java) { binding.getBinding(-1) }
        } finally {
            binding.clear()
        }
    }

    // Unbinding a key should remove the mapping of that key
    @Test
    fun testUnbindPeer() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            val key1 = binding.reserveKey()
            val key2 = binding.reserveKey()
            val key3 = binding.reserveKey()

            val object1 = object {}
            val object2 = object {}
            val object3 = object {}

            binding.bind(key1, object1)
            binding.bind(key2, object2)
            binding.bind(key3, object3)

            Assert.assertEquals(3, binding.size())
            binding.unbind(key1)
            Assert.assertEquals(2, binding.size())
            Assert.assertNull(binding.getBinding(key1))
        } finally {
            binding.clear()
        }
    }
}


