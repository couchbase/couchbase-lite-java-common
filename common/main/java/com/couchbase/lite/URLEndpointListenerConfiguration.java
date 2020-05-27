//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


public class URLEndpointListenerConfiguration {
    @NonNull
    private final Database database;

    @Nullable
    private int port;

    @Nullable
    private String networkInterface;

    private boolean disableTls;

    @Nullable
    private TLSIdentity tlsIdentity;

    @Nullable
    private ListenerAuthenticator authenticator;

    private boolean enableDeltaSync;

    // Constructors
    public URLEndpointListenerConfiguration(@NonNull Database database) { this.database = database; }

    public URLEndpointListenerConfiguration(@NonNull URLEndpointListenerConfiguration config) {
        database = config.database;
        port = config.port;
        networkInterface = config.networkInterface;
        disableTls = config.disableTls;
        tlsIdentity = config.tlsIdentity;
        authenticator = config.authenticator;
        enableDeltaSync = config.enableDeltaSync;
    }

    @NonNull
    public Database getDatabase() { return database; }

    public int getPort() { return port; }

    @Nullable
    public String getNetworkInterface() { return networkInterface; }

    public boolean isTlsDisabled() { return disableTls; }

    @Nullable
    public TLSIdentity getTlsIdentity() { return tlsIdentity; }


    @Nullable
    public ListenerAuthenticator getAuthenticator() { return authenticator; }

    public boolean isEnableDeltaSync() { return enableDeltaSync; }

    public URLEndpointListenerConfiguration setPort(int port) {
        this.port = port;
        return this;
    }

    public URLEndpointListenerConfiguration setNetworkInterface(@Nullable String networkInterface) {
        this.networkInterface = networkInterface;
        return this;
    }

    public URLEndpointListenerConfiguration setTlsDisabled(boolean disableTls) {
        this.disableTls = disableTls;
        return this;
    }

    public URLEndpointListenerConfiguration setTlsIdentity(@Nullable TLSIdentity tlsIdentity) {
        this.tlsIdentity = tlsIdentity;
        return this;
    }

    public URLEndpointListenerConfiguration setAuthenticator(@Nullable ListenerAuthenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    public URLEndpointListenerConfiguration setEnableDeltaSync(boolean enableDeltaSync) {
        this.enableDeltaSync = enableDeltaSync;
        return this;
    }
}
