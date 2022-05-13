//
// Copyright (c) 2020 Couchbase, Inc.
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


private open class MockWS : WebSocket {
    override fun request(): Request = TODO("Not yet implemented")
    override fun queueSize(): Long = TODO("Not yet implemented")
    override fun send(text: String): Boolean = TODO("Not yet implemented")
    override fun send(bytes: ByteString): Boolean = TODO("Not yet implemented")
    override fun close(code: Int, reason: String?): Boolean = TODO("Not yet implemented")
    override fun cancel(): Unit = TODO("Not yet implemented")
}

private open class MockCore : SocketFromRemote {
    override fun getLock(): Any = TODO("Not yet implemented")
    override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder): Unit =
        TODO("Not yet implemented")

    override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?): Unit =
        TODO("Not yet implemented")

    override fun remoteWrites(data: ByteArray): Unit = TODO("Not yet implemented")
    override fun remoteRequestsClose(status: CloseStatus): Unit = TODO("Not yet implemented")
    override fun remoteClosed(status: CloseStatus): Unit = TODO("Not yet implemented")
    override fun remoteFailed(err: Throwable): Unit = TODO("Not yet implemented")
}

val mockResponse: Response = Response.Builder()
    .request(Request.Builder().url("http://url.com").build())
    .protocol(Protocol.HTTP_1_1)
    .code(200)
    .message("")
    .body(ResponseBody.create(parse("application/json"), "{\"key\": \"val\"}"))
    .build()

class OkHttpSocketTest : BaseTest() {

    // Can initialize the socket
    @Test
    fun testInit() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val mockCore = MockCore()
        ok.init(mockCore)
        assertEquals(mockCore, ok.core)
    }

    // Attempt to initialize a closed socket is ignored.
    @Test
    fun testInitAfterClose() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)

        ok.init(MockCore())
        assertNull(ok.core)
    }

    // Attempt to initialize a socket with the same core is ignored.
    @Test
    fun testReinitSameCore() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)
    }

    // Can't initialize a socket twice
    @Test(expected = CBLSocketException::class)
    fun testReinitDifferentCore() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.init(MockCore())
    }

    // Core request to open a socket before it is initialized, fails
    @Test(expected = IllegalStateException::class)
    fun testOpenRemoteBeforeInit() = OkHttpSocket().openRemote(URI("https://foo.com"), null).unit()

    // Core request to open the remote creates the socket
    @Test
    fun testOpenRemoteBeforeOpen() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
    }

    // Core request to reopen a socket is ignored
    @Test
    fun testOpenRemoteWhileOpen() {
        var callPermitted = true

        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) {
                if (callPermitted) {
                    return; }
                fail()
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        assertTrue(ok.openRemote(URI("https://foo.com"), null))
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        // attempt to re-open is ignored.
        callPermitted = false
        assertFalse(ok.openRemote(URI("https://foo.com"), null))
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
    }

    // Core request to open a closed socket is ignored
    @Test
    fun testOpenRemoteWhileClosed() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Core request to write to a socket before it is initialized, fails
    @Test(expected = IllegalStateException::class)
    fun testWriteToRemoteBeforeInit() = OkHttpSocket().writeToRemote(ByteArray(1)).unit()

    // Core request to write to an unopened socket is ignored
    @Test
    fun testWriteToRemoteBeforeOpen() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.writeToRemote(ByteArray(1))
    }

    // Core request to write to a half opened socket succeeds
    @Test
    fun testWriteToRemoteWhileHalfOpen() {
        var sentBytes = 0
        val ws = object : MockWS() {
            override fun send(bytes: ByteString): Boolean {
                sentBytes = bytes.size()
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.writeToRemote(ByteArray(7))
        assertEquals(7, sentBytes)
    }

    // Core request to write to a fully opened socket succeeds
    @Test
    fun testWriteToRemoteWhileOpen() {
        var sentBytes = 0
        val ws = object : MockWS() {
            override fun send(bytes: ByteString): Boolean {
                sentBytes = bytes.size()
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(MockWS(), mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.writeToRemote(ByteArray(7))
        assertEquals(7, sentBytes)
    }

    // Core request to write to a closed socket is ignored
    @Test
    fun testWriteToRemoteAfterClosed() {
        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?) = true
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.writeToRemote(ByteArray(7))
    }

    // Core request to close a socket before it is initialized, fails
    @Test(expected = IllegalStateException::class)
    fun testCloseRemoteBeforeInit() = OkHttpSocket().closeRemote(CloseStatus(1, "")).unit()

    // Core request to close a socket it hasn't opened is ignored
    @Test
    fun testCloseRemoteBeforeOpen() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.closeRemote(CloseStatus(1, ""))
        assertEquals(core, ok.core)
        assertNull(ok.remote)
    }

    // Core request to close a socket it has requested open proxies the request to the remote
    @Test
    fun testCloseRemoteWhileHalfOpen() {
        var closeCode: Int? = null
        var closeReason: String? = null

        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?): Boolean {
                closeCode = code
                closeReason = reason
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.closeRemote(CloseStatus(1, "silliness"))
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals(1, closeCode)
        assertEquals("silliness", closeReason)
    }

    // Core request to close a socket that is open proxies the request to the remote
    @Test
    fun testCloseRemoteWhileOpen() {
        var closeCode: Int? = null
        var closeReason: String? = null

        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?): Boolean {
                closeCode = code
                closeReason = reason
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.closeRemote(CloseStatus(1, "silliness"))
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals(1, closeCode)
        assertEquals("silliness", closeReason)
    }

    // Core request to close a closed socket is ignored
    @Test
    fun testCloseRemoteWhileClosed() {
        var closeStatus: CloseStatus?
        var closeCode: Int?
        var closeReason: String?

        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?): Boolean {
                closeCode = code
                closeReason = reason
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        closeStatus = null
        closeCode = null
        closeReason = null
        ok.closeRemote(CloseStatus(1, "silliness"))
        assertNull(ok.core)
        assertNull(ok.remote)
        assertNull(closeCode)
        assertNull(closeReason)
        assertNull(closeStatus)
    }

    // Core request to cancel an uninitialized socket closes it
    @Test
    fun testCancelRemoteBeforeInit() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)
        ok.cancelRemote()
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Core request to cancel an unopened socket closes it
    @Test
    fun testCancelRemoteBeforeOpen() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.cancelRemote()
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Core request to cancel a half-open socket closes it
    @Test
    fun testCancelRemoteWhileHalfOpen() {
        val ws = object : MockWS() {
            override fun cancel() = Unit
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.cancelRemote()
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Core request to cancel an open socket closes it
    @Test
    fun testCancelRemoteWhileOpen() {
        val ws = object : MockWS() {
            override fun cancel() = Unit
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.cancelRemote()
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Core request to cancel socket that is already closed, is ignored
    @Test
    fun testCancelRemoteWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.cancelRemote()
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Remote attempt to open an uninitialized socket fails
    @Test(expected = IllegalStateException::class)
    fun testOnOpenBeforeInit() = OkHttpSocket().onOpen(MockWS(), mockResponse)

    // Remote attempt to open a socket before core requests open, is ignored
    @Test
    fun testOnOpenBeforeOpen() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.onOpen(MockWS(), mockResponse)
        assertEquals(core, ok.core)
        assertNull(ok.remote)
    }

    // Remote attempt to open a socket after core requests open, succeeds
    @Test
    fun testOnOpen() {
        var respCode = 0
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) {
                respCode = code
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals(200, respCode)
    }

    // Remote attempt to reopen a socket that is already open, is ignored
    @Test
    fun testOnOpenWhileOpen() {
        var respCode: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) {
                respCode = code
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals(200, respCode)

        respCode = null
        ok.onOpen(MockWS(), mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertNull(respCode)
    }

    // Remote attempt to reopen a closed socket is ignored
    @Test
    fun testOnOpenWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.onOpen(ws, mockResponse)
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Remote attempt to send to an uninitialized socket fails
    @Test(expected = IllegalStateException::class)
    fun testOnMessageBeforeInit() = OkHttpSocket().onMessage(MockWS(), "booya")

    // Remote attempt to send data on an unopened socket is ignored
    @Test
    fun testOnMessageBeforeOpen() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.onMessage(ws, "")
        assertEquals(core, ok.core)
        assertNull(ok.remote)
    }

    // Remote attempt to send data on a half open connection transfers data
    @Test
    fun testOnMessageWhileHalfOpen() {
        var sentBytes: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteWrites(data: ByteArray) {
                sentBytes = data.size
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onMessage(ws, "booya")
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals("booya".length, sentBytes)
    }

    // Remote attempt to send data on a fully open connection transfers data
    @Test
    fun testOnMessageWhileOpen() {
        var sentBytes: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteWrites(data: ByteArray) {
                sentBytes = data.size
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onMessage(ws, "booya")
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals("booya".length, sentBytes)
    }

    // Remote attempt to send data on a closed socket is a no-op
    @Test
    fun testOnMessageWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.onMessage(ws, "booya")
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Remote attempt to send data to the wrong socket is ignored
    @Test
    fun testOnMessageWithWrongSocket() {
        var sentBytes: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteWrites(data: ByteArray) {
                sentBytes = data.size
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onMessage(MockWS(), "booya")
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertNull(sentBytes)
    }

    // Remote request to close an uninitialized socket fails
    @Test(expected = IllegalStateException::class)
    fun testOnClosingBeforeInit() = OkHttpSocket().onClosing(MockWS(), 47, "xyzzy")

    // Remote request to close an unopened socket is ignored
    @Test
    fun testOnClosingBeforeOpen() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        assertEquals(core, ok.core)
        assertNull(ok.remote)
    }

    // Remote request to close a half-open socket is proxied to core
    @Test
    fun testOnClosingWhileHalfOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteRequestsClose(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals(CloseStatus(47, "xyzzy"), closeStatus)
    }

    // Remote request to close an open socket is proxied to core
    @Test
    fun testOnClosingWhileOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteRequestsClose(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)
        assertEquals(CloseStatus(47, "xyzzy"), closeStatus)
    }

    // Remote request to close a closed socket is ignored
    @Test
    fun testOnClosingWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Remote confirm close on an uninitialized socket fails
    @Test(expected = IllegalStateException::class)
    fun testOnClosedBeforeInit() = OkHttpSocket().onClosed(MockWS(), 47, "xyzzy")

    // Remote confirmation of close on an unopened socket closed the socket
    @Test
    fun testOnClosedBeforeOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.onClosed(ws, 47, "xyzzy")
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, 47, "xyzzy"), closeStatus)
    }

    // Remote confirmation of close on a half open socket is proxied to core
    @Test
    fun testOnClosedWhileHalfOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onClosed(ws, 47, "xyzzy")
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, 47, "xyzzy"), closeStatus)
    }

    // Remote confirmation of close on an open socket is proxied to core
    @Test
    fun testOnClosedWhileOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onClosed(ws, 47, "xyzzy")
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, 47, "xyzzy"), closeStatus)
    }

    // Remote confirmation of close on an closed socket is ignored
    @Test
    fun testOnClosedWhileClosed() {
        var callPermitted = true
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                if (callPermitted) {
                    return; }
                fail()
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        callPermitted = false
        ok.onClosed(ws, 47, "xyzzy")
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Remote failure on an uninitialized socket fails
    @Test(expected = IllegalStateException::class)
    fun testOnFailureBeforeInit() = OkHttpSocket().onFailure(MockWS(), Exception(), null)

    // Remote failure on an unopened socket closes it and proxies to core
    @Test
    fun testOnFailureBeforeOpen() {
        var failureErr: Throwable? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteFailed(err: Throwable) {
                failureErr = err
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        val failure = Exception()
        ok.onFailure(ws, failure, null)
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(failure, failureErr)
    }

    // Remote failure on an half-open socket closes it and proxies to core
    @Test
    fun testOnFailureWhileHalfOpen() {
        var failureErr: Throwable? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteFailed(err: Throwable) {
                failureErr = err
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        val failure = Exception()
        ok.onFailure(ws, failure, null)
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(failure, failureErr)
    }

    // Remote failure on an-open socket closes it and proxies to core
    @Test
    fun testOnFailureWhileOpen() {
        var failureErr: Throwable? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteFailed(err: Throwable) {
                failureErr = err
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        val failure = Exception()
        ok.onFailure(ws, failure, null)
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(failure, failureErr)
    }

    // Remote failure on a closed socket is ignored
    @Test
    fun testOnFailureWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        ok.onFailure(ws, Exception(), null)
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Closing an uninitialized socket closes the socket
    @Test
    fun testCloseBeforeInit() {
        val ok = OkHttpSocket()
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
    }

    // Closing an unopened socket closes the socket and tells core
    @Test
    fun testCloseBeforeOpen() {
        var closeStatus: CloseStatus? = null
        val ok = OkHttpSocket()
        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
        assertEquals(
            CloseStatus(
                C4Constants.ErrorDomain.WEB_SOCKET,
                C4Constants.WebSocketError.GOING_AWAY,
                "Closed by client"
            ),
            closeStatus
        )
    }

    // Closing a half-open socket closes the socket and tells core and the remote
    @Test
    fun testCloseWhileHalfOpen() {
        var closeStatus: CloseStatus? = null
        var closeCode: Int? = null
        var closeReason: String? = null

        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?): Boolean {
                closeCode = code
                closeReason = reason
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
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

    // Closing an open socket closes the socket and tells core and the remote
    @Test
    fun testCloseWhileOpen() {
        var closeStatus: CloseStatus? = null
        var closeCode: Int? = null
        var closeReason: String? = null

        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?): Boolean {
                closeCode = code
                closeReason = reason
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.onOpen(ok.remote!!, mockResponse)
        assertEquals(core, ok.core)
        assertEquals(ws, ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)
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

    // Closing a close socket closes is ignored
    @Test
    fun testRemoteCloseWhileClosed() {
        var closeStatus: CloseStatus?

        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        assertEquals(core, ok.core)
        assertNull(ok.remote)

        ok.close()
        assertNull(ok.core)
        assertNull(ok.remote)

        closeStatus = null
        ok.closeRemote(CloseStatus(1, "silliness"))
        assertNull(ok.core)
        assertNull(ok.remote)
        assertNull(closeStatus)
    }
}
