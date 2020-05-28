//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;


/**
 * Connection Status
 */
public class ConnectionStatus {
    private final int connectionCount;
    private final int activeConnectionCount;

    public ConnectionStatus(int connectionCount, int activeConnectionCount) {
        this.connectionCount = connectionCount;
        this.activeConnectionCount = activeConnectionCount;
    }

    public int getConnectionCount() { return connectionCount; }

    public ConnectionStatus withConnectionCount(int connectionCount) {
        return new ConnectionStatus(connectionCount, this.activeConnectionCount);
    }

    public int getActiveConnectionCount() { return activeConnectionCount; }

    public ConnectionStatus withActiveConnectionCount(int activeConnectionCount) {
        return new ConnectionStatus(this.connectionCount, activeConnectionCount);
    }
}
