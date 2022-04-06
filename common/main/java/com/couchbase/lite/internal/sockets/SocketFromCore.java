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
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;


/**
 * +------+                                                                      +--------+
 * |      | ==> SocketFromCore ==> AbstractCBLWebSocket ==>   SocketToCore   ==> |        |
 * | core |                                                                      | remote |
 * |      | <==  SocketToCore  <== AbstractCBLWebSocket <== SocketFromRemote <== |        |
 * +------+                                                                      +--------+
 * <p>
 * This is, actually two different types depending on Framing.
 */
public interface SocketFromCore {
    void coreRequestsOpen();
    void coreWrites(@NonNull byte[] allocatedData);
    void coreAcksWrite(long byteCount);
    // called only when NO_FRAMING
    void coreRequestsClose(@NonNull CloseStatus status);
    // called only when CLIENT_FRAMING
    void coreClosed();
}
