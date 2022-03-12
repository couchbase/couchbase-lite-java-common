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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertEquals
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

class OkHttpSocketTest : BaseTest() {

    @Test
    fun testCloseBeforeInit() = OkHttpSocket().close()

    @Test
    fun testCloseWithCore() {
        var closeStatus: CloseStatus? = null
        val ok = OkHttpSocket()
        ok.init(object : MockCore() {
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        })
        ok.close()
        assertEquals(
            CloseStatus(
                C4Constants.ErrorDomain.WEB_SOCKET,
                C4Constants.WebSocketError.GOING_AWAY,
                "Closed by client"
            ),
            closeStatus
        )
    }

    @Test
    fun testCloseWithCoreAndRemote() {
        var closeStatus: CloseStatus? = null
        val ok = OkHttpSocket()
        ok.init(object : MockCore() {
            override fun setupRemoteSocketFactory(builder: OkHttpClient.Builder) = Unit
            override fun remoteClosed(status: CloseStatus) {
                closeStatus = status
            }
        })
        ok.openRemote(URI("https://foo.com"), null)
        ok.close()
        assertEquals(
            CloseStatus(
                C4Constants.ErrorDomain.WEB_SOCKET,
                C4Constants.WebSocketError.GOING_AWAY,
                "Closed by client"
            ),
            closeStatus
        )
    }

    @Test
    fun testInit() {
        val ok = OkHttpSocket()
        val mockCore = MockCore()
        ok.init(mockCore)
        assertEquals(mockCore, ok.core)
    }

    @Test(expected = IllegalStateException::class)
    fun testRepopenWithInit() {
        val ok = OkHttpSocket()
        ok.close()
        ok.init(MockCore())
    }

    @Test(expected = IllegalStateException::class)
    fun testRepopenWhileOpen() {
        val ok = OkHttpSocket()
        ok.init(MockCore())
        ok.init(MockCore())
    }


    @Test(expected = IllegalStateException::class)
    fun testOpenRemoteBeforeInit() = OkHttpSocket().openRemote(URI("https://foo.com"), null).unit()

    @Test(expected = IllegalStateException::class)
    fun testReopenWithOpenRemote() {
        val ok = OkHttpSocket()
        ok.close()
        ok.openRemote(URI("https://foo.com"), null)
    }


    @Test(expected = IllegalStateException::class)
    fun testOnOpenBeforeInit() = OkHttpSocket().onOpen(MockWS(), Response.Builder().build()).unit()

    @Test(expected = IllegalStateException::class)
    fun testRepopenWithOnOpen() {
        val ok = OkHttpSocket()
        ok.close()
        ok.onOpen(MockWS(), Response.Builder().build())
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