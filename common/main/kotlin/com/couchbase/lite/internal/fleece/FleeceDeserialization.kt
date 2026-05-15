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

@file:OptIn(ExperimentalSerializationApi::class)

package com.couchbase.lite.internal.fleece

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*


/** Decodes an object from a Fleece [FLValue] using Kotlin Serialization. */
@ExperimentalSerializationApi
fun <T> deserializeFromFleece(root: FLValue, deserializer: DeserializationStrategy<T>): T =
    ValueDecoder(root).decodeSerializableValue(deserializer)

/** Decodes an object from a Fleece [FLValue] using Kotlin Serialization. */
@ExperimentalSerializationApi
inline fun <reified T> deserializeFromFleece(root: FLValue): T =
    deserializeFromFleece(root, serializer())

/** Decodes an object from a [Collection<FLValue>] using Kotlin Serialization. */
@ExperimentalSerializationApi
fun <T> deserializeFromFleece(root: Collection<FLValue>, deserializer: DeserializationStrategy<T>): T =
    CollectionRootDecoder(root).decodeSerializableValue(deserializer)


/** Decodes an object from a [Collection<FLValue>] using Kotlin Serialization. */
@ExperimentalSerializationApi
inline fun <reified T> deserializeFromFleece(root: Collection<FLValue>): T =
    deserializeFromFleece(root, serializer())


/** Decodes an object from a [Map] iterator using Kotlin Serialization. */
@ExperimentalSerializationApi
fun <T> deserializeFromFleece(iterator: Iterator<Map.Entry<String,FLValue>>,
                              size: Int,
                              deserializer: DeserializationStrategy<T>): T =
    MapRootDecoder(iterator, size).decodeSerializableValue(deserializer)

/** Decodes an object from a [Map] iterator using Kotlin Serialization. */
@ExperimentalSerializationApi
inline fun <reified T> deserializeFromFleece(iterator: Iterator<Map.Entry<String,FLValue>>,
                                             size: Int): T =
    deserializeFromFleece(iterator, size, serializer())


/** Root decoder for a `Collection<FLValue>` */
private class CollectionRootDecoder(val collection: Collection<FLValue>): AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        throw FleeceSerializationException("not a scalar value")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind) {
            StructureKind.LIST -> ArrayDecoder(collection)
            else -> throw FleeceSerializationException("unsupported SerialDescriptor kind ${descriptor.kind}")
        }
    }
}


/** Root decoder for a `Map<String,FLValue>` */
private class MapRootDecoder(val iter: Iterator<Map.Entry<String,FLValue>>,
                             val size: Int): AbstractDecoder()
{
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        throw FleeceSerializationException("not a scalar value")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind) {
            StructureKind.MAP -> {
                val keyDescriptor = descriptor.getElementDescriptor(0)
                if (keyDescriptor.kind != PrimitiveKind.STRING)
                    throw FleeceSerializationException("Map keys must be Strings, not ${keyDescriptor.serialName}")
                MapDecoder(iter, size)
            }
            StructureKind.CLASS -> {
                ClassDecoder(iter)
            }
            else -> throw FleeceSerializationException("unsupported SerialDescriptor kind ${descriptor.kind}")
        }
    }
}


/** Base class of ValueDecoder and ArrayDecoder. */
private abstract class AbstractValueDecoder(val size: Int): AbstractDecoder() {
    protected var index = -1

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = size

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (++index >= size) CompositeDecoder.DECODE_DONE else index

    abstract val value: FLValue

    override fun decodeBoolean(): Boolean   = value.asBool()
    override fun decodeByte(): Byte         = decodeLong().toByte()
    override fun decodeShort(): Short       = decodeLong().toShort()
    override fun decodeChar(): Char         = Char(decodeInt())
    override fun decodeInt(): Int           = decodeLong().toInt()
    override fun decodeLong(): Long         = value.asInt()
    override fun decodeFloat(): Float       = value.asFloat()
    override fun decodeDouble(): Double     = value.asDouble()
    override fun decodeString(): String     = value.asString()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = decodeInt()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        decoderForStructure(value, descriptor)
}


/** Root decoder for a [FLValue].
 *  Decodes scalars, and delegates collections to [ArrayDecoder] or [MapDecoder]. */
private class ValueDecoder(override val value: FLValue): AbstractValueDecoder(1)


/** Decodes a Kotlin [List] from a Collection of FLValues, usually an FLArray. */
private class ArrayDecoder(array: Collection<FLValue>): AbstractValueDecoder(array.size) {
    val iter = array.iterator()
    override val value get() = iter.next()
}


/** Decodes a Kotlin [Map] from a Map iterator. */
private class MapDecoder(val iter: Iterator<Map.Entry<String,FLValue>>,
                         val size: Int): AbstractDecoder()
{
    constructor(map: Map<String,FLValue>) :this(map.iterator(), map.size)

    private var index = 0
    private var curKey: String? = null
    private var curValue: FLValue? = null

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeSequentially() = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = size

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (index < size) index else CompositeDecoder.DECODE_DONE

    // Advances the iterator and returns the key.
    private fun nextKey(): String {
        require(curKey == null)
        val (key, value) = iter.next()
        curKey = key
        curValue = value
        index++
        return key
    }

    // Returns the current value without advancing.
    private fun nextValue(): FLValue {
        val value = curValue
        require(value != null) {"expected a map key to be decoded next"}
        curKey = null
        curValue = null
        index++
        return value
    }

    // A decoder for [StructureKind.MAP] is expected to provide alternating keys and values.
    // And keys are always strings (already preflighted.) So [decodeString] may be called for either
    // a key or a value. It checks if we've read the next entry, and if not advances the iterator
    // and returns the key. Otherwise it returns the current value.
    override fun decodeString(): String =
        if (curValue == null) nextKey() else nextValue().asString()

    // The other decode methods are only called for values.
    override fun decodeBoolean(): Boolean   = nextValue().asBool()
    override fun decodeByte(): Byte         = decodeLong().toByte()
    override fun decodeShort(): Short       = decodeLong().toShort()
    override fun decodeInt(): Int           = decodeLong().toInt()
    override fun decodeLong(): Long         = nextValue().asInt()
    override fun decodeFloat(): Float       = nextValue().asFloat()
    override fun decodeDouble(): Double     = nextValue().asDouble()
    override fun decodeChar(): Char         = Char(decodeInt())
}


/** Decodes a Kotlin class instance from a Map iterator. */
private class ClassDecoder(val iter: Iterator<Map.Entry<String,FLValue>>): CompositeDecoder {
    constructor(map: Map<String,FLValue>) :this(map.iterator())

    private var curKey: String? = null
    private var curValue: FLValue? = null
    private var elementIndex = -1

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        // "Decodes the index of the next element to be decoded. Index represents a position of the
        // current element in the serial descriptor element that can be found with
        // SerialDescriptor.getElementIndex." -- Kotlin API docs
        if (!iter.hasNext()) return CompositeDecoder.DECODE_DONE
        val (key, value) = iter.next()
        curKey = key
        curValue = value
        elementIndex = descriptor.getElementIndex(key)
        return elementIndex
    }

    private fun nextValue(index: Int): FLValue {
        require(index == elementIndex) {"unexpected element index"}
        elementIndex = -1
        return curValue!!
    }

    private fun decodeLong(index: Int) = nextValue(index).asInt()

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean   = nextValue(index).asBool()
    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte         = decodeLong(index).toByte()
    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short       = decodeLong(index).toShort()
    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int           = decodeLong(index).toInt()
    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long         = decodeLong(index)
    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float       = nextValue(index).asFloat()
    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double     = nextValue(index).asDouble()
    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char         = Char(decodeLong(index).toInt())
    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String     = nextValue(index).asString()

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder =
        ValueDecoder(nextValue(index))

    override fun <T> decodeSerializableElement(descriptor: SerialDescriptor,
                                               index: Int,
                                               deserializer: DeserializationStrategy<T>,
                                               previousValue: T?): T
    {
        return deserializer.deserialize(ValueDecoder(nextValue(index)))
    }

    override fun <T: Any> decodeNullableSerializableElement(descriptor: SerialDescriptor,
                                                            index: Int,
                                                            deserializer: DeserializationStrategy<T?>,
                                                            previousValue: T?): T?
    {
        val value = nextValue(index)
        if (value.type == FLValue.NULL)
            return null
        return deserializer.deserialize(ValueDecoder(value))
    }

    override fun endStructure(descriptor: SerialDescriptor) { /*no-op*/ }
}


// Creates an appropriate CompositeDecoder based on a SerialDescriptor.
@ExperimentalSerializationApi
private fun decoderForStructure(item: FLValue, descriptor: SerialDescriptor): CompositeDecoder {
    return when (descriptor.kind) {
        StructureKind.LIST -> {
            if (item.type != FLValue.ARRAY) {throw FleeceSerializationException("expected an array") }
            ArrayDecoder(item.asFLArray().asCollection)
        }
        StructureKind.MAP -> {
            val keyDescriptor = descriptor.getElementDescriptor(0)
            if (keyDescriptor.kind != PrimitiveKind.STRING)
                throw FleeceSerializationException("Map keys must be Strings, not ${keyDescriptor.serialName}")
            if (item.type != FLValue.DICT) {throw FleeceSerializationException("expected a dict") }
            MapDecoder(item.asFLDict().asMap)
        }
        StructureKind.CLASS -> {
            if (item.type != FLValue.DICT) {throw FleeceSerializationException("expected a dict") }
            ClassDecoder(item.asFLDict().asMap)
        }
        else -> {
            throw FleeceSerializationException("unsupported SerialDescriptor kind ${descriptor.kind}")
        }
    }
}
