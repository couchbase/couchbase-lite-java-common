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
package com.couchbase.lite.internal.core

import com.couchbase.lite.BaseTest
import com.couchbase.lite.internal.BaseSocketFactory
import com.couchbase.lite.internal.sockets.CloseStatus
import com.couchbase.lite.internal.sockets.SocketFromCore
import com.couchbase.lite.internal.sockets.SocketToCore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


open class MockSocketFromCore : SocketFromCore {
    val latch = CountDownLatch(1)
    var threadId: Long? = null
    override fun coreRequestsOpen() = Unit
    override fun coreWrites(data: ByteArray) = Unit
    override fun coreAcksWrite(nBytes: Long) = Unit
    override fun coreRequestsClose(status: CloseStatus) = Unit
    override fun coreClosed() = Unit
    fun called() {
        threadId = Thread.currentThread().id
        latch.countDown()
    }
}

open class MockSocketFactory : BaseSocketFactory {
    override fun createSocket(
        toCore: SocketToCore,
        scheme: String,
        host: String,
        port: Int,
        path: String,
        opts: ByteArray
    ) = throw IllegalStateException("BOOM!")
}

// https://youtrack.jetbrains.com/issue/KT-5870
// fingers crossed...
open class MockImpl : C4Socket.NativeImpl {
    private var peer = 0L
    var totalCalls = 0

    override fun nFromNative(
        token: Long,
        schema: String?,
        host: String?,
        port: Int,
        path: String?,
        framing: Int
    ) = C4BaseTest.MOCK_PEER

    override fun nRetain(peer: Long) {
        assertEquals(0L, this.peer)
        this.peer = peer
        verifyPeer(peer)
    }

    override fun nOpened(peer: Long) = verifyPeer(peer)
    override fun nGotHTTPResponse(peer: Long, httpStatus: Int, responseHeaders: ByteArray?) =
        verifyPeer(peer)

    override fun nCompletedWrite(peer: Long, nBytes: Long) = verifyPeer(peer)
    override fun nReceived(peer: Long, data: ByteArray?) = verifyPeer(peer)
    override fun nCloseRequested(peer: Long, status: Int, message: String?) = verifyPeer(peer)
    override fun nClosed(peer: Long, domain: Int, code: Int, message: String?) {
        verifyPeer(peer)
        this.peer = -1L
    }

    private fun verifyPeer(peer: Long) {
        assertEquals(peer, this.peer)
        totalCalls++
    }
}

class C4SocketTest : BaseTest() {
    @After
    fun tearDownC4SocketTest() {
        C4Socket.BOUND_SOCKETS.clear()
    }


    ////////////////  S T A T I C   U T I L I T I E S   ////////////////

    @Test
    fun testCreateSocket() {
        assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)
        verifySocket(C4Socket.createSocket(MockImpl(), C4BaseTest.MOCK_PEER))
    }


    ////////////////  C O R E   C A L L B A C K S   ////////////////
    // These methods pretty much just proxy the callback to
    // a client, the bound socket's `fromCore`, on the socket's thread.

    @Test
    fun testOpen() {
        assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)

        val fromCore = object : MockSocketFromCore() {
            override fun coreRequestsOpen() = called()
        }

        createSocket(fromCore)

        C4Socket.open(C4BaseTest.MOCK_PEER, 0L, null, null, 0, null, null)
        awaitCall(fromCore)
    }

    @Test
    fun testOpenThrows() {
        assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)

        val mockImpl = object : MockImpl() {
            var domain: Int? = null
            var code: Int? = null
            var message: String? = null
            override fun nClosed(peer: Long, domain: Int, code: Int, message: String?) {
                super.nClosed(peer, domain, code, message)
                this.domain = domain
                this.code = code
                this.message = message
            }
        }

        val token = BaseSocketFactory.bindSocketFactory(MockSocketFactory())
        try {
            assertFalse(C4Socket.openSocket(mockImpl, C4BaseTest.MOCK_PEER, token, "ws:", "Oprah", 86, "/fail", ByteArray(0)))
            assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)
            assertEquals(2, mockImpl.totalCalls)
            assertEquals(C4Constants.ErrorDomain.NETWORK, mockImpl.domain)
            assertEquals(C4Constants.NetworkError.INVALID_URL, mockImpl.code)
            assertEquals("BOOM!", mockImpl.message)
        } finally {
            BaseSocketFactory.unbindSocketFactory(token)
        }
    }

    @Test
    fun testWrite() {
        assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)

        val fromCore = object : MockSocketFromCore() {
            var realData: ByteArray? = null
            override fun coreWrites(data: ByteArray) {
                realData = data
                called()
            }
        }

        createSocket(fromCore)

        val data = byteArrayOf(0x2E, 0x38)
        C4Socket.write(C4BaseTest.MOCK_PEER, data)
        awaitCall(fromCore)

        assertArrayEquals(data, fromCore.realData)
    }

    @Test
    fun testCompletedReceive() {
        val fromCore = object : MockSocketFromCore() {
            var realNBytes: Long? = null
            override fun coreAcksWrite(nBytes: Long) {
                realNBytes = nBytes
                called()
            }
        }

        createSocket(fromCore)

        C4Socket.completedReceive(C4BaseTest.MOCK_PEER, 15L)
        awaitCall(fromCore)

        assertEquals(15L, fromCore.realNBytes)
    }

    @Test
    fun testRequestClose() {
        val fromCore = object : MockSocketFromCore() {
            var realMessage: String? = null
            override fun coreRequestsClose(status: CloseStatus) {
                realMessage = status.message
                called()
            }
        }

        createSocket(fromCore)

        C4Socket.requestClose(C4BaseTest.MOCK_PEER, 19, "Upstate NY")
        awaitCall(fromCore)

        assertEquals("Upstate NY", fromCore.realMessage)
    }

    @Test
    fun testClose() {
        val fromCore = object : MockSocketFromCore() {
            override fun coreClosed() = called()
        }

        createSocket(fromCore)

        C4Socket.close(C4BaseTest.MOCK_PEER)
        awaitCall(fromCore)
    }


    ////////////////  O B J E C T   M E T H O D S   ////////////////

    @Test
    fun testSocketInit() {
        val socket = createSocket(MockImpl())

        val fromCore = MockSocketFromCore()
        socket.init(fromCore)
        assertEquals(fromCore, socket.fromCore)

        socket.init(MockSocketFromCore())
        assertEquals(fromCore, socket.fromCore)
    }

    @Test
    fun testSocketAckOpenToCore() {
        val impl = object : MockImpl() {
            var peer: Long? = null
            var status: Int? = null
            var headers: ByteArray? = null
            override fun nOpened(peer: Long) {
                super.nOpened(peer)
                this.peer = peer
            }

            override fun nGotHTTPResponse(
                peer: Long,
                httpStatus: Int,
                responseHeaders: ByteArray?
            ) {
                super.nGotHTTPResponse(peer, httpStatus, responseHeaders)
                this.peer = peer
                this.status = httpStatus
                this.headers = responseHeaders
            }
        }
        val socket = createSocket(impl)
        val headers = ByteArray(4)
        socket.ackOpenToCore(302, headers)
        assertEquals(C4BaseTest.MOCK_PEER, impl.peer)
        assertEquals(302, impl.status)
        assertEquals(headers, impl.headers)
        assertEquals(3, impl.totalCalls)
    }

    @Test
    fun testSocketAckWriteToCore() {
        val impl = object : MockImpl() {
            var peer: Long? = null
            var nBytes: Long? = null
            override fun nCompletedWrite(peer: Long, nBytes: Long) {
                super.nCompletedWrite(peer, nBytes)
                this.peer = peer
                this.nBytes = nBytes
            }
        }
        val socket = createSocket(impl)
        socket.ackWriteToCore(23L)
        assertEquals(C4BaseTest.MOCK_PEER, impl.peer)
        assertEquals(23L, impl.nBytes)
        assertEquals(2, impl.totalCalls)
    }

    @Test
    fun testSocketWriteToCore() {
        val impl = object : MockImpl() {
            var peer: Long? = null
            var data: ByteArray? = null
            override fun nReceived(peer: Long, data: ByteArray?) {
                super.nReceived(peer, data)
                this.peer = peer
                this.data = data
            }
        }
        val socket = createSocket(impl)
        val data = byteArrayOf(0x2E, 0x38)
        socket.writeToCore(data)
        assertEquals(C4BaseTest.MOCK_PEER, impl.peer)
        assertEquals(data, impl.data)
        assertEquals(2, impl.totalCalls)
    }

    @Test
    fun testSocketRequestCoreClose() {
        val impl = object : MockImpl() {
            var peer: Long? = null
            var code: Int? = null
            var msg: String? = null
            override fun nCloseRequested(peer: Long, status: Int, message: String?) {
                super.nCloseRequested(peer, status, message)
                this.peer = peer
                this.code = status
                this.msg = message
            }
        }
        val socket = createSocket(impl)
        socket.requestCoreClose(CloseStatus(47, "truly..."))
        assertEquals(C4BaseTest.MOCK_PEER, impl.peer)
        assertEquals(47, impl.code)
        assertEquals("truly...", impl.msg)
        assertEquals(2, impl.totalCalls)
    }

    @Test
    fun testSocketClose() {
        val impl = MockImpl()
        val socket = createSocket(impl)
        socket.close()
        assertEquals(2, impl.totalCalls)
        assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)
    }

    ////////////////  U T I L I T I E S   ////////////////

    private fun createSocket(impl: MockImpl): C4Socket {
        val socket = C4Socket(impl, C4BaseTest.MOCK_PEER)
        assertEquals(1, impl.totalCalls)
        return socket
    }

    private fun createSocket(fromCore: SocketFromCore): C4Socket {
        assertEquals(0, C4Socket.BOUND_SOCKETS.keySet().size)
        val socket = C4Socket.createSocket(MockImpl(), C4BaseTest.MOCK_PEER)
        socket.init(fromCore)
        verifySocket(socket)
        return socket
    }

    private fun awaitCall(fromCore: MockSocketFromCore) {
        assertTrue(fromCore.latch.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertNotEquals(Thread.currentThread().id, fromCore.threadId)
    }

    private fun verifySocket(socket: C4Socket) {
        assertEquals(1, C4Socket.BOUND_SOCKETS.keySet().size)
        assertEquals(socket, C4Socket.BOUND_SOCKETS.getBinding(C4BaseTest.MOCK_PEER))
    }
}
