package com.couchbase.lite.internal.fleece

import com.couchbase.lite.BaseTest
import kotlinx.serialization.*
import org.junit.Assert
import org.junit.Test


@Serializable
data class Project(val name: String, val owner: User?, val votes: Int)

@Serializable
data class ProjectOpt(val name: String, val owner: User? = null, val votes: Int)

@Serializable
data class User(val name: String, val age: Age)

@Serializable @JvmInline
value class Age(val years: UInt)


private fun fleeceToJSON(container: FLSliceResult): String =
    FLValue.fromData(container)!!.toJSON()!!

private fun encode(fn: FLEncoder.()->Boolean): FLValue {
    val encoder = FLEncoder.getManagedEncoder()
    encoder.fn()
    return FLValue.fromData(encoder.finish())
}


@OptIn(ExperimentalSerializationApi::class)
class SerializationTests: BaseTest() {
    @Test
    fun encodeList() {
        serializeToFleece(listOf("hello", "there")).use { container ->
            Assert.assertEquals("[\"hello\",\"there\"]", fleeceToJSON(container))
        }
    }

    @Test
    fun encodeMap() {
        serializeToFleece(mapOf("key" to "value", "foo" to "bar")).use { container ->
            val json = fleeceToJSON(container)
            Assert.assertEquals("""{"foo":"bar","key":"value"}""", json)
        }
    }

    @Test
    fun encodeInvalidTypes() {
        Assert.assertThrows(FleeceSerializationException::class.java) {
            serializeToFleece("hello")  // Can't encode just a scalar
        }
        Assert.assertThrows(FleeceSerializationException::class.java) {
            serializeToFleece(Age(14u)) // ...even if it's wrapped in a value class
        }
        Assert.assertThrows(FleeceSerializationException::class.java) {
            serializeToFleece(mapOf(99 to "value", 30 to "bar"))    // Map keys must be Strings
        }
    }

    @Test
    fun encodeSimpleClass() {
        val data = User("kotlin", Age(17u))
        serializeToFleece(data).use { container ->
        val json = fleeceToJSON(container)
        Assert.assertEquals("""{"age":17,"name":"kotlin"}""", json)
            }
    }

    @Test
    fun encodeNestedClasses() {
        val data = Project("kotlinx.serialization", User("kotlin", Age(17u)), 9000)
        serializeToFleece(data).use { container ->
            val json = fleeceToJSON(container)
            Assert.assertEquals(
                """{"name":"kotlinx.serialization","owner":{"age":17,"name":"kotlin"},"votes":9000}""",
                json
            )
        }
    }

    @Test
    fun encodeNestedClassesWithNull() {
        val data = Project("kotlinx.serialization", null, 9000)
        serializeToFleece(data).use { container ->
            val json = fleeceToJSON(container)
            // "owner" appears with a null value because Project.owner doesn't default to null.
            Assert.assertEquals(
                """{"name":"kotlinx.serialization","owner":null,"votes":9000}""",
                json
            )
        }
    }

    @Test
    fun encodeNestedClassesWithOptionalNull() {
        val data = ProjectOpt("kotlinx.serialization", null, 9000)
        serializeToFleece(data).use { container ->
            val json = fleeceToJSON(container)
            // "owner" is omitted because ProjectOpt.owner has a default value of null.
            Assert.assertEquals("""{"name":"kotlinx.serialization","votes":9000}""", json)
        }
    }


    //---- DECODING:


    @Test fun decodeList() {
        val encoded = encode {
            beginArray(0)
            writeString("hi")
            writeString("there")
            endArray()
        }
        val list = deserializeFromFleece<List<String>>(encoded)
        Assert.assertEquals(listOf("hi", "there"), list)
    }

    @Test fun decodeMap() {
        val encoded = encode {
            beginDict(0)
            writeKey("hi")
            writeValue(19)
            writeKey("bye")
            writeValue(-1)
            endDict()
        }
        val map = deserializeFromFleece<Map<String,Int>>(encoded)
        Assert.assertEquals(mapOf("hi" to 19, "bye" to -1), map)
    }

    @Test fun decodeClass() {
        val encoded = encode {
            beginDict(0)
            writeKey("name")
            writeValue("pupshaw")
            writeKey("age")
            writeValue(29)
            endDict()
        }
        val user = deserializeFromFleece<User>(encoded)
        Assert.assertEquals(User("pupshaw", Age(29u)), user)
    }

    @Test fun decodeNestedClasses() {
        val encoded = encode {
            beginDict(0)
            writeKey("name")
            writeValue("Fleece")
            writeKey("votes")
            writeValue(9000)
            writeKey("owner")

            beginDict(0)
            writeKey("name")
            writeValue("pupshaw")
            writeKey("age")
            writeValue(29)
            endDict()

            endDict()
        }

        val user = deserializeFromFleece<Project>(encoded)
        Assert.assertEquals(Project("Fleece", User("pupshaw", Age(29u)), 9000), user)
    }

    @Test fun decodeNestedClassesWithNull() {
        val encoded = encode {
            beginDict(0)
            writeKey("name")
            writeValue("Fleece")
            writeKey("votes")
            writeValue(9000)
            writeKey("owner")
            writeNull()
            endDict()
        }

        val user = deserializeFromFleece<Project>(encoded)
        Assert.assertEquals(Project("Fleece", null, 9000), user)
    }

    @Test fun decodeNestedClassesWithOptionalNull() {
        val encoded = encode {
            beginDict(0)
            writeKey("name")
            writeValue("Fleece")
            writeKey("votes")
            writeValue(9000)
            endDict()
        }

        val user = deserializeFromFleece<ProjectOpt>(encoded)
        Assert.assertEquals(ProjectOpt("Fleece", null, 9000), user)
    }

    @Test fun roundTripNestedClassesWithNull() {
        val data = Project("Fleece", null, 9000)
        serializeToFleece(data).use { encoded ->
            print(fleeceToJSON(encoded))
            val user = deserializeFromFleece<Project>(FLValue.fromData(encoded)!!)
            Assert.assertEquals(Project("Fleece", null, 9000), user)
        }
    }
}
