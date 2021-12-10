package com.couchbase.lite.internal.core


import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding
import org.junit.Assert
import org.junit.Test
import java.lang.IllegalStateException

class PeerBindingTest {

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
        }
        finally { binding.clear() }
    }

    // Trying to bind an object to a key that's not reserved leads to exception
    @Test(expected = IllegalStateException::class)
    fun testBindWithoutKey() {
        val binding = TaggedWeakPeerBinding<Any>()
        val keyNotReserved: Long = 1
        try {
            binding.bind(keyNotReserved, object {})
        }
        finally { binding.clear() }
    }

    // Rebinding an existing mapping with a different object results in exception
    @Test(expected = IllegalStateException::class)
    fun testRebindPeerWithDifferentObject() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            val keyReserve = binding.reserveKey()
            val object1 = object {}
            val object2 = object {}

            binding.bind(keyReserve, object1)
            binding.bind(keyReserve, object2)
        }
        finally { binding.clear() }
    }

    // Getting a binding that doesn't exist returns null
    @Test
    fun testGetNonExistingBinding() {
        val binding = TaggedWeakPeerBinding<Any>()
        try {
            val randomNum: Long = 2
            Assert.assertEquals(null, binding.getBinding(randomNum))
        }
        finally { binding.clear() }
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
            Assert.assertEquals(null, binding.getBinding(key1))
        }
        finally { binding.clear() }
    }
}


