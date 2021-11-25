//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.utils.Preconditions;


public enum MessageFraming {
    CLIENT_FRAMING, NO_FRAMING, SERVER_FRAMING;

    //-------------------------------------------------------------------------
    // C4SocketFraming (from: c4SocketTypes.h)
    //-------------------------------------------------------------------------

    // kC4WebSocketClientFraming: Frame as WebSocket client messages (masked)
    public static final int C4_WEB_SOCKET_CLIENT_FRAMING = 0;
    // kC4NoFraming: No framing; use messages as-is
    public static final int C4_NO_FRAMING = 1;
    // kC4WebSocketServerFraming: Frame as WebSocket server messages (not masked)
    public static final int C4_WEB_SOCKET_SERVER_FRAMING = 2;

    private static final Map<MessageFraming, Integer> FRAMINGS;
    static {
        final Map<MessageFraming, Integer> m = new HashMap<>();
        m.put(CLIENT_FRAMING, C4_WEB_SOCKET_CLIENT_FRAMING);
        m.put(NO_FRAMING, C4_NO_FRAMING);
        m.put(SERVER_FRAMING, C4_WEB_SOCKET_SERVER_FRAMING);
        FRAMINGS = Collections.unmodifiableMap(m);
    }
    public static int getC4Framing(@NonNull MessageFraming framing) {
        return Preconditions.assertNotNull(FRAMINGS.get(framing), "framing");
    }
}
