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
package com.couchbase.lite.mock

import com.couchbase.lite.internal.ReplicationCollection
import com.couchbase.lite.internal.core.C4BaseTest
import com.couchbase.lite.internal.core.C4Replicator
import com.couchbase.lite.internal.core.C4ReplicatorStatus
import com.couchbase.lite.internal.core.C4Socket
import com.couchbase.lite.internal.fleece.FLSliceResult

open class MockNativeSocket : C4Socket.NativeImpl {
    override fun nFromNative(
        token: Long,
        schema: String?,
        host: String?,
        port: Int,
        path: String?,
        framing: Int
    ) = C4BaseTest.MOCK_PEER

    override fun nOpened(peer: Long) = Unit
    override fun nGotHTTPResponse(peer: Long, httpStatus: Int, responseHeadersFleece: ByteArray?) = Unit
    override fun nCompletedWrite(peer: Long, byteCount: Long) = Unit
    override fun nReceived(peer: Long, data: ByteArray?) = Unit
    override fun nCloseRequested(peer: Long, status: Int, message: String?) = Unit
    override fun nClosed(peer: Long, errorDomain: Int, errorCode: Int, message: String?) = Unit
    override fun setPeer(peer: Long) = Unit
}

open class MockNativeReplicator : C4Replicator.NativeImpl {
    override fun nCreate(
        id: String,
        collections: Array<out ReplicationCollection>,
        db: Long,
        scheme: String?,
        host: String?,
        port: Int,
        path: String?,
        remoteDbName: String?,
        framing: Int,
        push: Boolean,
        pull: Boolean,
        continuous: Boolean,
        options: ByteArray?,
        replicatorToken: Long,
        socketFactoryToken: Long
    ) = C4BaseTest.MOCK_PEER

    override fun nCreateLocal(
        id: String,
        collections: Array<out ReplicationCollection>,
        db: Long,
        targetDb: Long,
        push: Boolean,
        pull: Boolean,
        continuous: Boolean,
        options: ByteArray?,
        replicatorToken: Long
    ) = C4BaseTest.MOCK_PEER

    override fun nCreateWithSocket(
        id: String,
        collections: Array<out ReplicationCollection>,
        db: Long,
        openSocket: Long,
        options: ByteArray?,
        replicatorToken: Long
    ) = C4BaseTest.MOCK_PEER

    override fun nGetStatus(peer: Long) = C4ReplicatorStatus(0, 0L, 0L, 0L, 0, 0, 0)
    override fun nStart(peer: Long, restart: Boolean) = Unit
    override fun nStop(peer: Long) = Unit
    override fun nSetOptions(peer: Long, options: ByteArray?) = Unit
    override fun nGetPendingDocIds(peer: Long, scope: String, collection: String) = FLSliceResult.createTestSlice()
    override fun nIsDocumentPending(peer: Long, id: String, scope: String, collection: String) = true
    override fun nSetProgressLevel(peer: Long, progressLevel: Int) = Unit
    override fun nSetHostReachable(peer: Long, reachable: Boolean) = Unit
    override fun nFree(peer: Long) = Unit
}
