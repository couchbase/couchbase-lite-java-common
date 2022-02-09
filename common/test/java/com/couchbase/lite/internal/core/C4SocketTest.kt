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
package com.couchbase.lite.internal.core

import org.junit.After
import org.junit.Assert.assertEquals

private const val MOCK_PEER = 500005L

private val MOCK_CORE = object : C4Socket.NativeImpl {
    var peer = 0L
    var totalCalls = 0

    override fun nRetain(peer: Long) {
        assertEquals(0L, this.peer)
        this.peer = peer
        totalCalls++
    }

    override fun nFromNative(
        token: Long,
        schema: String?,
        host: String?,
        port: Int,
        path: String?,
        framing: Int
    ): Long {
        assertEquals(0L, this.peer)
        return MOCK_PEER
    }

    override fun nOpened(peer: Long) {
        checkPeer(peer)
    }

    override fun nGotHTTPResponse(peer: Long, httpStatus: Int, responseHeadersFleece: ByteArray?) {
        checkPeer(peer)
    }

    override fun nCompletedWrite(peer: Long, byteCount: Long) {
        checkPeer(peer)
    }

    override fun nReceived(peer: Long, data: ByteArray?) {
        checkPeer(peer)
    }

    override fun nCloseRequested(peer: Long, status: Int, message: String?) {
        checkPeer(peer)
    }

    override fun nClosed(peer: Long, errorDomain: Int, errorCode: Int, message: String?) {
        checkPeer(peer)
    }

    override fun nRelease(peer: Long) {
        checkPeer(peer)
        this.peer = -1L
    }

    private fun checkPeer(peer: Long) {
        assertEquals(peer, this.peer)
        totalCalls++
    }
}

class C4SocketTest : C4BaseTest() {
    @After
    fun tearDownC4SocketTest() = C4Socket.BOUND_SOCKETS.clear()
}
