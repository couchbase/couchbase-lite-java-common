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


/** Uses Kotlin Serialization to encode an arbitrary object or collection to Fleece.
 *  @param serializer  The SerializationStrategy to use.
 *  @param value  The object to encode.
 *  @param encoder  A Fleece [FLEncoder] to use; defaults to a fresh instance.
 *  @return  The encoded Fleece data. */
@ExperimentalSerializationApi
fun <T> serializeToFleece(serializer: SerializationStrategy<T>,
                          value: T,
                          encoder: FLEncoder? = null): ByteArray
{
    val encoder = FleeceRootEncoder(encoder)
    encoder.encodeSerializableValue(serializer, value)
    return encoder.container!!
}

/** Uses Kotlin Serialization to encode an arbitrary object or collection to Fleece.
 *  @param value  The object to encode.
 *  @param encoder  A Fleece [FLEncoder] to use; defaults to a fresh instance.
 *  @return  The encoded Fleece data. */
@ExperimentalSerializationApi
inline fun <reified T> serializeToFleece(value: T, encoder: FLEncoder? = null): ByteArray =
    serializeToFleece(serializer(), value, encoder)


class FleeceSerializationException(message: String): Exception(message)


/** Common interface of [FleeceRootEncoder] and [FleeceCollectionEncoder]. */
private interface FleeceParentEncoder {
    val flEncoder: FLEncoder

    fun childFinished()

    fun beginChild(descriptor: SerialDescriptor, collectionSize: Long = 0): CompositeEncoder {
        require(descriptor.kind is StructureKind)
        var isDict = false
        if (descriptor.kind == StructureKind.LIST) {
            flEncoder.beginArray(collectionSize)
        } else {
            if (descriptor.kind == StructureKind.MAP) {
                val keyDescriptor = descriptor.getElementDescriptor(0)
                if (keyDescriptor.kind != PrimitiveKind.STRING)
                    throw FleeceSerializationException("Map keys must be Strings, not ${keyDescriptor.serialName}")
            }
            isDict = true
            flEncoder.beginDict(collectionSize)
        }
        return FleeceCollectionEncoder(this, isDict = isDict)
    }
}


/** The root-level Encoder. It just expects a [beginCollection] or [beginStructure] call. */
private class FleeceRootEncoder(encoder: FLEncoder?) : AbstractEncoder(), FleeceParentEncoder {
    override val flEncoder = encoder ?: FLEncoder.getManagedEncoder()
    var container: ByteArray? = null

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeValue(value: Any) =
        throw FleeceSerializationException("Fleece cannot serialize primitive types, only classes and collections.")

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder =
        beginChild(descriptor, collectionSize.toLong())

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        beginChild(descriptor)

    override fun childFinished() {
        require(container == null)
        container = flEncoder.finish()
    }
}


// https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization.encoding/-composite-encoder/

/** Child Encoder that does the real work. */
private class FleeceCollectionEncoder (private val parent: FleeceParentEncoder,
                                       private val isDict: Boolean)
    : Encoder, CompositeEncoder, FleeceParentEncoder {

    override val flEncoder = parent.flEncoder

    override val serializersModule: SerializersModule = EmptySerializersModule()

    // True when the current value being encoded is actually a Dict key.
    private var valueIsKey: Boolean = false

    // Always called before adding a value to the Fleece encoder.
    private fun writeKey(descriptor: SerialDescriptor, index: Int, isString: Boolean = false) {
        require(!valueIsKey)
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                // If encoding a class instance, then the key is available from the descriptor.
                valueIsKey = true
                actuallyWriteKey(descriptor.getElementName(index))
            }
            StructureKind.MAP -> {
                // ...but if encoding a map, it's passed to us as alternating keys and values, so
                // we have to keep track of which are the keys by setting [valueIsKey] appropriately.
                valueIsKey = (index % 2 == 0)
                if (valueIsKey && !isString)
                    throw FleeceSerializationException("Map keys must be Strings to encode to Fleece")
            }
            else -> { }
        }
    }

    // Actually writes a key to the DictEncoder.
    private fun actuallyWriteKey(key: String) {
        assert(valueIsKey)
        flEncoder.writeKey(key)
        valueIsKey = false
    }

    override fun childFinished() { /*no-op*/ }

    //---- CompositeEncoder API:

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        writeKey(descriptor, index)
        encodeBoolean(value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) =
        encodeIntElement(descriptor, index, value.toInt())

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) =
        encodeIntElement(descriptor, index, value.toInt())

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) =
        encodeIntElement(descriptor, index, value.code)

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        writeKey(descriptor, index)
        encodeInt(value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        writeKey(descriptor, index)
        encodeLong(value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        writeKey(descriptor, index)
        encodeFloat(value)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        writeKey(descriptor, index)
        encodeDouble(value)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        writeKey(descriptor, index, isString = true)
        encodeString(value)
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder = this

    override fun <T> encodeSerializableElement(descriptor: SerialDescriptor,
                                               index: Int,
                                               serializer: SerializationStrategy<T>,
                                               value: T)
    {
        writeKey(descriptor, index, isString = true)
        serializer.serialize(this, value)
    }

    override fun <T : Any> encodeNullableSerializableElement(descriptor: SerialDescriptor,
                                                             index: Int,
                                                             serializer: SerializationStrategy<T>,
                                                             value: T?)
    {
        if (value != null) {
            encodeSerializableElement(descriptor, index, serializer, value)
        } else if (!descriptor.isElementOptional(index)) {
            writeKey(descriptor, index)
            encodeNull()
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (isDict) flEncoder.endDict() else flEncoder.endArray()
        parent.childFinished()
    }

    //---- Encoder API:

    override fun encodeNull()                   {flEncoder.writeNull()}
    override fun encodeBoolean(value: Boolean)  {flEncoder.writeValue(value)}
    override fun encodeByte(value: Byte)        {flEncoder.writeValue(value.toInt())}
    override fun encodeShort(value: Short)      {flEncoder.writeValue(value.toInt())}
    override fun encodeChar(value: Char)        {flEncoder.writeValue(value.code)}
    override fun encodeInt(value: Int)          {flEncoder.writeValue(value)}
    override fun encodeLong(value: Long)        {flEncoder.writeValue(value)}
    override fun encodeFloat(value: Float)      {flEncoder.writeValue(value)}
    override fun encodeDouble(value: Double)    {flEncoder.writeValue(value)}

    override fun encodeString(value: String) {
        if (valueIsKey)
            actuallyWriteKey(value)
        else
            flEncoder.writeString(value)
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        flEncoder.writeValue(index)
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int) =
        beginChild(descriptor, collectionSize.toLong())

    override fun beginStructure(descriptor: SerialDescriptor) =
        beginChild(descriptor)
}
