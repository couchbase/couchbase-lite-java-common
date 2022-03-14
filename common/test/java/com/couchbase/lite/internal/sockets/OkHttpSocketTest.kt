//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.sockets

import com.couchbase.lite.BaseTest
import com.couchbase.lite.internal.core.C4Constants
import okhttp3.*
import okhttp3.MediaType.parse
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import java.net.URI


fun Any?.unit() = Unit


open class MockWS : WebSocket {
    override fun request(): Request {
        TODO("Not yet implemented")
    }

    override fun queueSize(): Long {
        TODO("Not yet implemented")
    }

    override fun send(text: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun send(bytes: ByteString): Boolean {
        TODO("Not yet implemented")
    }

    override fun close(code: Int, reason: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun cancel() {
        TODO("Not yet implemented")
    }
}

open class MockCore : SocketFromRemote {
    override fun getLock(): Any {
        TODO("Not yet implemented")
    }

    override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) {
        TODO("Not yet implemented")
    }

    override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) {
        TODO("Not yet implemented")
    }

    override fun remoteWrites(data: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun remoteRequestsClose(status: CloseStatus) {
        TODO("Not yet implemented")
    }

    override fun remoteClosed(status: CloseStatus) {
        TODO("Not yet implemented")
    }

    override fun remoteFailed(err: Throwable) {
        TODO("Not yet implemented")
    }
}

val mockResponse = Response.Builder()
    .request(Request.Builder().url("http://url.com").build())
    .protocol(Protocol.HTTP_1_1)
    .code(200)
    .message("")
    .body(ResponseBody.create(parse("application/json"), "{\"key\": \"val\"}"))
    .build()

class OkHttpSocketTest : BaseTest() {
    // Can set up a core connection
    @Test
    fun testInit() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)
        val mockCore = MockCore()
        ok.init(mockCore)
        assertEquals(mockCore, ok.core)
        assertFalse(ok.isClosed)
    }

    // Can't set up a core connection after close
    @Test(expected = IllegalStateException::class)
    fun testRepopenWithInit() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)
        ok.close()
        ok.init(MockCore())
    }

    // Can't set up a core connection twice
    @Test(expected = IllegalStateException::class)
    fun testRepopenWithInitWhileOpen() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.init(MockCore())
        assertNotNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.init(MockCore())
    }

    // Remote can't open the connection until there is a core connection
    @Test(expected = IllegalStateException::class)
    fun testOnOpenBeforeInit() = OkHttpSocket().onOpen(MockWS(), Response.Builder().build()).unit()

    // Remote can't open the connection after it has been closed
    @Test(expected = IllegalStateException::class)
    fun testRepopenWithOnOpen() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertTrue(ok.isClosed)

        ok.onOpen(MockWS(), Response.Builder().build())
    }

    // Remote can't open the connection twice
    @Test(expected = IllegalStateException::class)
    fun testRepopenWithOnOpenWhileOpen() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.onOpen(MockWS(), Response.Builder().build())
        assertNull(ok.core)
        assertNotNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.onOpen(MockWS(), Response.Builder().build())
    }

    // Core can't open the connection without a core connection
    @Test(expected = IllegalStateException::class)
    fun testOpenRemoteBeforeInit() = OkHttpSocket().openRemote(URI("https://foo.com"), null).unit()

    // Core can't reopen the remote connection after close
    @Test(expected = IllegalStateException::class)
    fun testReopenWithOpenRemote() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertTrue(ok.isClosed)

        ok.openRemote(URI("https://foo.com"), null)
    }

    // Core can't reopen the remote connection after close
    @Test(expected = IllegalStateException::class)
    fun testReopenWithOpenRemoteWhileOpen() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.openRemote(URI("https://foo.com"), null)
        assertNull(ok.core)
        assertNotNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.openRemote(URI("https://foo.com"), null)
    }

    // Close before open is fine
    @Test
    fun testCloseBeforeInit() {
        val ok = OkHttpSocket()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertTrue(ok.isClosed)
    }

    // Close with only the core connection
    // closes the core connection
    @Test
    fun testCloseWithCore() {
        var closeStatus: CloseStatus? = null
        val ok = OkHttpSocket()
        ok.init(object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        })
        assertNotNull(ok.core)
        assertNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertTrue(ok.isClosed)
        assertEquals(
            CloseStatus(
                C4Constants.ErrorDomain.WEB_SOCKET,
                C4Constants.WebSocketError.GOING_AWAY,
                "Closed by client"
            ),
            closeStatus
        )
    }

    // Close with both core and remote connections
    // closes both
    //
    @Test
    fun testCloseWithCoreAndRemote() {
        var closeStatus: CloseStatus? = null
        var closeCode: Int? = null
        var closeReason: String? = null

        val ok = OkHttpSocket()
        ok.init(object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        })
        ok.onOpen(
            object : MockWS() {
                override fun close(code: Int, reason: String?): Boolean {
                    closeCode = code
                    closeReason = reason
                    return true
                }
            },
            mockResponse
        )
        assertNotNull(ok.core)
        assertNotNull(ok.remote)
        assertFalse(ok.isClosed)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertTrue(ok.isClosed)
        assertEquals(C4Constants.WebSocketError.GOING_AWAY, closeCode)
        assertEquals("Closed by client", closeReason)
        assertEquals(
            CloseStatus(
                C4Constants.ErrorDomain.WEB_SOCKET,
                C4Constants.WebSocketError.GOING_AWAY,
                "Closed by client"
            ),
            closeStatus
        )
    }


//    @Test(expected = IllegalStateException::class)
//    fun testOpenRemoteBeforeInit() = OkHttpSocket().openRemote(URI("foo:"), mapOf()).unit()
//
//    @Test(expected = IllegalStateException::class)
//    fun testWriteToRemoteBeforeInit() = OkHttpSocket().writeToRemote(ByteArray(0)).unit()
//
//    @Test(expected = IllegalStateException::class)
//    fun testCloseRemoteBeforeInit() = OkHttpSocket().closeRemote(CloseStatus(0, null)).unit()
//
//    @Test(expected = IllegalStateException::class)
//    fun testCancelRemoteBeforeInit() = OkHttpSocket().cancelRemote()
//
//    @Test(expected = IllegalStateException::class)
//    fun testOnOpenBeforeInit() = OkHttpSocket().onOpen(mockWS, Response.Builder().build())
//
//    @Test(expected = IllegalStateException::class)
//    fun testOnMessageBytesBeforeInit() = OkHttpSocket().onMessage(mockWS, ByteString.EMPTY)
//
//    @Test(expected = IllegalStateException::class)
//    fun testOnMessageStringBeforeInit() = OkHttpSocket().onMessage(mockWS, "")
//
//    @Test(expected = IllegalStateException::class)
//    fun testOnClosingBeforeInit() = OkHttpSocket().onClosing(mockWS, 4, "")
//
//    @Test(expected = IllegalStateException::class)
//    fun testOnClosedBeforeInit() = OkHttpSocket().onClosed(mockWS, 4, "")
//
//    @Test(expected = IllegalStateException::class)
//    fun testOnFailureBeforeInit() = OkHttpSocket().onFailure(mockWS, Exception(), null)
}
