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
import com.couchbase.lite.CouchbaseLiteError
import com.couchbase.lite.internal.core.C4Constants
import com.couchbase.lite.internal.utils.Fn.TaskThrows
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert
import org.junit.Test
import java.net.URI


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
    .body("{\"key\": \"val\"}".toResponseBody("application/json".toMediaType()))
    .build()

class OkHttpSocketTest : BaseTest() {

    // Can initialize the socket
    @Test
    fun testInit() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val mockCore = MockCore()
        ok.init(mockCore)
        Assert.assertEquals(mockCore, ok.core)
    }

    // Attempt to initialize a closed socket is ignored.
    @Test
    fun testInitAfterClose() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)

        ok.init(MockCore())
        Assert.assertNull(ok.core)
    }

    // Attempt to initialize a socket with the same core is ignored.
    @Test
    fun testReinitSameCore() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)
    }

    // Can't initialize a socket twice
    @Test
    fun testReinitDifferentCore() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        assertThrowsCBLSocketException(C4Constants.ErrorDomain.NETWORK, C4Constants.NetworkError.NETWORK_RESET) {
            ok.init(MockCore())
        }
    }

    // Core request to open a socket before it is initialized, fails
    @Test
    fun testOpenRemoteBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().openRemote(URI("https://foo.com"), null)
        }
    }

    // Core request to open the remote creates the socket
    @Test
    fun testOpenRemoteBeforeOpen() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
    }

    // Core request to reopen a socket is ignored
    @Test
    fun testOpenRemoteWhileOpen() {
        var callPermitted = true

        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) {
                if (callPermitted) {
                    return; }
                Assert.fail()
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        Assert.assertTrue(ok.openRemote(URI("https://foo.com"), null))
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        // attempt to re-open is ignored.
        callPermitted = false
        Assert.assertFalse(ok.openRemote(URI("https://foo.com"), null))
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
    }

    // Core request to open a closed socket is ignored
    @Test
    fun testOpenRemoteWhileClosed() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Core request to write to a socket before it is initialized, fails
    @Test
    fun testWriteToRemoteBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().writeToRemote(ByteArray(1))
        }
    }

    // Core request to write to an unopened socket is ignored
    @Test
    fun testWriteToRemoteBeforeOpen() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.writeToRemote(ByteArray(1))
    }

    // Core request to write to a half opened socket succeeds
    @Test
    fun testWriteToRemoteWhileHalfOpen() {
        var sentBytes = 0
        val ws = object : MockWS() {
            override fun send(bytes: ByteString): Boolean {
                sentBytes = bytes.size
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.writeToRemote(ByteArray(7))
        Assert.assertEquals(7, sentBytes)
    }

    // Core request to write to a fully opened socket succeeds
    @Test
    fun testWriteToRemoteWhileOpen() {
        var sentBytes = 0
        val ws = object : MockWS() {
            override fun send(bytes: ByteString): Boolean {
                sentBytes = bytes.size
                return true
            }
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(MockWS(), mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.writeToRemote(ByteArray(7))
        Assert.assertEquals(7, sentBytes)
    }

    // Core request to write to a closed socket is ignored
    @Test
    fun testWriteToRemoteAfterClosed() {
        val ws = object : MockWS() {
            override fun close(code: Int, reason: String?) = true
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.writeToRemote(ByteArray(7))
    }

    // Core request to close a socket before it is initialized, fails
    @Test
    fun testCloseRemoteBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().closeRemote(CloseStatus(1, ""))
        }
    }

    // Core request to close a socket it hasn't opened is ignored
    @Test
    fun testCloseRemoteBeforeOpen() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.closeRemote(CloseStatus(1, ""))
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)
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
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.closeRemote(CloseStatus(1, "silliness"))
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals(1, closeCode)
        Assert.assertEquals("silliness", closeReason)
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
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.closeRemote(CloseStatus(1, "silliness"))
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals(1, closeCode)
        Assert.assertEquals("silliness", closeReason)
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
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        closeStatus = null
        closeCode = null
        closeReason = null
        ok.closeRemote(CloseStatus(1, "silliness"))
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertNull(closeCode)
        Assert.assertNull(closeReason)
        Assert.assertNull(closeStatus)
    }

    // Core request to cancel an uninitialized socket closes it
    @Test
    fun testCancelRemoteBeforeInit() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)
        ok.cancelRemote()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Core request to cancel an unopened socket closes it
    @Test
    fun testCancelRemoteBeforeOpen() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.cancelRemote()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Core request to cancel a half-open socket closes it
    @Test
    fun testCancelRemoteWhileHalfOpen() {
        val ws = object : MockWS() {
            override fun cancel() = Unit
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.cancelRemote()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Core request to cancel an open socket closes it
    @Test
    fun testCancelRemoteWhileOpen() {
        val ws = object : MockWS() {
            override fun cancel() = Unit
        }
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.cancelRemote()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Core request to cancel socket that is already closed, is ignored
    @Test
    fun testCancelRemoteWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.cancelRemote()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote attempt to open an uninitialized socket fails
    @Test
    fun testOnOpenBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().onOpen(MockWS(), mockResponse)
        }
    }

    // Remote attempt to open a socket before core requests open, is ignored
    @Test
    fun testOnOpenBeforeOpen() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.onOpen(MockWS(), mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote attempt to open a socket after core requests open, succeeds
    @Test
    fun testOnOpen() {
        var respCode = 0
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) {
                respCode = code
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals(200, respCode)
    }

    // Remote attempt to reopen a socket that is already open, is ignored
    @Test
    fun testOnOpenWhileOpen() {
        var respCode: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) {
                respCode = code
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals(200, respCode)

        respCode = null
        ok.onOpen(MockWS(), mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertNull(respCode)
    }

    // Remote attempt to reopen a closed socket is ignored
    @Test
    fun testOnOpenWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote attempt to send to an uninitialized socket fails
    @Test
    fun testOnMessageBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().onMessage(MockWS(), "booya")
        }
    }


    // Remote attempt to send data on an unopened socket is ignored
    @Test
    fun testOnMessageBeforeOpen() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.onMessage(ws, "")
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote attempt to send data on a half open connection transfers data
    @Test
    fun testOnMessageWhileHalfOpen() {
        var sentBytes: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteWrites(data: ByteArray) {
                sentBytes = data.size
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onMessage(ws, "booya")
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals("booya".length, sentBytes)
    }

    // Remote attempt to send data on a fully open connection transfers data
    @Test
    fun testOnMessageWhileOpen() {
        var sentBytes: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteWrites(data: ByteArray) {
                sentBytes = data.size
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onMessage(ws, "booya")
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals("booya".length, sentBytes)
    }

    // Remote attempt to send data on a closed socket is a no-op
    @Test
    fun testOnMessageWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.onMessage(ws, "booya")
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote attempt to send data to the wrong socket is ignored
    @Test
    fun testOnMessageWithWrongSocket() {
        var sentBytes: Int? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteWrites(data: ByteArray) {
                sentBytes = data.size
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onMessage(MockWS(), "booya")
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertNull(sentBytes)
    }

    // Remote request to close an uninitialized socket fails
    @Test
    fun testOnClosingBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().onClosing(MockWS(), 47, "xyzzy")
        }
    }

    // Remote request to close an unopened socket is ignored
    @Test
    fun testOnClosingBeforeOpen() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = MockCore()
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote request to close a half-open socket is proxied to core
    @Test
    fun testOnClosingWhileHalfOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteRequestsClose(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals(CloseStatus(47, "xyzzy"), closeStatus)
    }

    // Remote request to close an open socket is proxied to core
    @Test
    fun testOnClosingWhileOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteRequestsClose(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)
        Assert.assertEquals(CloseStatus(47, "xyzzy"), closeStatus)
    }

    // Remote request to close a closed socket is ignored
    @Test
    fun testOnClosingWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.onClosing(ws, 47, "xyzzy")
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote confirm close on an uninitialized socket fails
    @Test
    fun testOnClosedBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().onClosed(MockWS(), 47, "xyzzy")
        }
    }

    // Remote confirmation of close on an unopened socket closed the socket
    @Test
    fun testOnClosedBeforeOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.onClosed(ws, 47, "xyzzy")
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, 47, "xyzzy"), closeStatus)
    }

    // Remote confirmation of close on a half open socket is proxied to core
    @Test
    fun testOnClosedWhileHalfOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onClosed(ws, 47, "xyzzy")
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, 47, "xyzzy"), closeStatus)
    }

    // Remote confirmation of close on an open socket is proxied to core
    @Test
    fun testOnClosedWhileOpen() {
        var closeStatus: CloseStatus? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onClosed(ws, 47, "xyzzy")
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, 47, "xyzzy"), closeStatus)
    }

    // Remote confirmation of close on an closed socket is ignored
    @Test
    fun testOnClosedWhileClosed() {
        var callPermitted = true
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                if (callPermitted) {
                    return; }
                Assert.fail()
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        callPermitted = false
        ok.onClosed(ws, 47, "xyzzy")
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Remote failure on an uninitialized socket fails
    @Test
    fun testOnFailureBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            OkHttpSocket().onFailure(MockWS(), Exception(), null)
        }
    }

    // Remote failure on an unopened socket closes it and proxies to core
    @Test
    fun testOnFailureBeforeOpen() {
        var failureErr: Throwable? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteFailed(err: Throwable) {
                failureErr = err
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        val failure = Exception()
        ok.onFailure(ws, failure, null)
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(failure, failureErr)
    }

    // Remote failure on an half-open socket closes it and proxies to core
    @Test
    fun testOnFailureWhileHalfOpen() {
        var failureErr: Throwable? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteFailed(err: Throwable) {
                failureErr = err
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        val failure = Exception()
        ok.onFailure(ws, failure, null)
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(failure, failureErr)
    }

    // Remote failure on an-open socket closes it and proxies to core
    @Test
    fun testOnFailureWhileOpen() {
        var failureErr: Throwable? = null
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteOpened(code: Int, headers: MutableMap<String, Any>?) = Unit
            override fun remoteFailed(err: Throwable) {
                failureErr = err
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ws, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        val failure = Exception()
        ok.onFailure(ws, failure, null)
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(failure, failureErr)
    }

    // Remote failure on a closed socket is ignored
    @Test
    fun testOnFailureWhileClosed() {
        val ws = MockWS()
        val ok = OkHttpSocket { _, _, _ -> ws }
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) = Unit
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        ok.onFailure(ws, Exception(), null)
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
    }

    // Closing an uninitialized socket closes the socket
    @Test
    fun testCloseBeforeInit() {
        val ok = OkHttpSocket()
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
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
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(
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
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(C4Constants.WebSocketError.GOING_AWAY, closeCode)
        Assert.assertEquals("Closed by client", closeReason)
        Assert.assertEquals(
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
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.openRemote(URI("https://foo.com"), null)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.onOpen(ok.remote!!, mockResponse)
        Assert.assertEquals(core, ok.core)
        Assert.assertEquals(ws, ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertEquals(C4Constants.WebSocketError.GOING_AWAY, closeCode)
        Assert.assertEquals("Closed by client", closeReason)
        Assert.assertEquals(
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
        Assert.assertEquals(SocketFromRemote.Constants.NULL, ok.core)
        Assert.assertNull(ok.remote)

        val core = object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        }
        ok.init(core)
        Assert.assertEquals(core, ok.core)
        Assert.assertNull(ok.remote)

        ok.close()
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)

        closeStatus = null
        ok.closeRemote(CloseStatus(1, "silliness"))
        Assert.assertNull(ok.core)
        Assert.assertNull(ok.remote)
        Assert.assertNull(closeStatus)
    }

    private fun assertThrowsCBLSocketException(domain: Int, code: Int, block: TaskThrows<java.lang.Exception?>) {
        try {
            block.run()
            Assert.fail("Expected CBL Socket exception ($domain, $code)")
        } catch (e: Exception) {
            assertIsCBLSocketException(e, domain, code)
        }
    }

    private fun assertIsCBLSocketException(err: Exception?, domain: Int, code: Int) {
        Assert.assertNotNull(err)
        if (err !is CBLSocketException) {
            throw AssertionError("Expected CBL Socket exception ($domain, $code) but got:", err)
        }
        if (domain > 0) {
            Assert.assertEquals(domain, err.domain)
        }
        if (code > 0) {
            Assert.assertEquals(code.toLong(), err.code.toLong())
        }
    }
}
