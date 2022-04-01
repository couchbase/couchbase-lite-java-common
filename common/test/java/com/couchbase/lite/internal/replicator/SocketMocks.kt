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

import com.couchbase.lite.internal.sockets.*
import java.net.URI


open class MockCore : SocketToCore {
    val mutex = Object()
    override fun getLock() = mutex
    override fun init(listener: SocketFromCore): Unit = TODO("Not yet implemented")
    override fun close(): Unit = TODO("Not yet implemented")
    override fun ackWriteToCore(byteCount: Long): Unit = TODO("Not yet implemented")
    override fun writeToCore(data: ByteArray): Unit = TODO("Not yet implemented")
    override fun requestCoreClose(status: CloseStatus): Unit = TODO("Not yet implemented")
    override fun closeCore(status: CloseStatus): Unit = TODO("Not yet implemented")
    override fun ackOpenToCore(httpStatus: Int, responseHeadersFleece: ByteArray?): Unit =
        TODO("Not yet implemented")
}

open class MockRemote : SocketToRemote {
    override fun close(): Unit = TODO("Not yet implemented")
    override fun init(listener: SocketFromRemote): Unit = TODO("Not yet implemented")
    override fun writeToRemote(data: ByteArray): Boolean = TODO("Not yet implemented")
    override fun closeRemote(status: CloseStatus): Boolean = TODO("Not yet implemented")
    override fun cancelRemote(): Unit = TODO("Not yet implemented")
    override fun openRemote(uri: URI, options: MutableMap<String, Any>?): Boolean =
        TODO("Not yet implemented")
}

open class MockCookieStore : CBLCookieStore {
    override fun setCookie(uri: URI, setCookieHeader: String): Unit = TODO("Not yet implemented")
    override fun getCookies(uri: URI): String? = TODO("Not yet implemented")
}
