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
package com.couchbase.lite.internal.replicator

import com.couchbase.lite.BaseTest
import com.couchbase.lite.internal.core.C4Constants
import com.couchbase.lite.internal.sockets.CloseStatus
import com.couchbase.lite.internal.sockets.SocketState
import com.couchbase.lite.internal.sockets.SocketToCore
import com.couchbase.lite.internal.sockets.SocketToRemote
import com.couchbase.lite.internal.utils.Fn
import org.junit.Assert
import org.junit.Test
import java.net.URI
import java.security.cert.Certificate

open class TestCBLWebSocket(
    toRemote: SocketToRemote,
    toCore: SocketToCore,
    uri: URI,
    opts: ByteArray?,
    cookieStore: CBLCookieStore,
    serverCertsListener: Fn.Consumer<MutableList<Certificate>>
) : AbstractCBLWebSocket(toRemote, toCore, uri, opts, cookieStore, serverCertsListener) {
    override fun handleClose(error: Throwable): CloseStatus? = TODO("Not yet implemented")
    override fun handleCloseCause(error: Throwable): Int = TODO("Not yet implemented")
}

class CBLWebSocketTest : BaseTest() {
    @Test
    fun testCoreRequestsOpenSucceeds() {
        var coreStatus: Int? = null
        lateinit var ws: AbstractCBLWebSocket
        ws = TestCBLWebSocket(
            object : MockRemote() {
                override fun openRemote(uri: URI, options: MutableMap<String, Any>?): Boolean {
                    ws.remoteOpened(200, null)
                    return true
                }
            },
            object : MockCore() {
                override fun ackOpenToCore(httpStatus: Int, responseHeadersFleece: ByteArray?) {
                    coreStatus = httpStatus
                }
            },
            URI("https://foo"),
            null,
            MockCookieStore(),
            { _ -> }
        )

        ws.coreRequestsOpen()
        Assert.assertEquals(200, coreStatus)
        Assert.assertEquals(SocketState.OPEN, ws.socketState)
    }

    @Test
    fun testCoreRequestsOpenFails() {
        var coreStatus: CloseStatus? = null
        lateinit var ws: AbstractCBLWebSocket
        ws = TestCBLWebSocket(
            object : MockRemote() {
                override fun openRemote(uri: URI, options: MutableMap<String, Any>?): Boolean {
                    ws.remoteClosed(
                        CloseStatus(
                            C4Constants.ErrorDomain.WEB_SOCKET,
                            C4Constants.WebSocketError.USER_PERMANENT,
                            "fail"
                        )
                    )
                    return false
                }
            },
            object : MockCore() {
                override fun closeCore(status: CloseStatus) {
                    coreStatus = status
                }
            },
            URI("https://foo"),
            null,
            MockCookieStore(),
            { _ -> }
        )

        ws.coreRequestsOpen()
        Assert.assertEquals(C4Constants.ErrorDomain.WEB_SOCKET, coreStatus?.domain)
        Assert.assertEquals(C4Constants.WebSocketError.USER_PERMANENT, coreStatus?.code)
        Assert.assertEquals("fail", coreStatus?.message)
        Assert.assertEquals(SocketState.CLOSED, ws.socketState)
    }
}