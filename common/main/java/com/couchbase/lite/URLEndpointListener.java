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

import java.net.URL;
import java.util.List;


public class URLEndpointListener {
    @NonNull
    private final URLEndpointListenerConfiguration config;

    private final int port;

    @Nullable
    private final List<URL> urls;

    @Nullable
    private final ConnectionStatus status;

    // Constructors
    public URLEndpointListener(@NonNull URLEndpointListenerConfiguration config) {
        this.config = config;
        this.port = 0;
        this.urls = null;
        this.status = null;
    }

    @NonNull
    public URLEndpointListenerConfiguration getConfig() { return config; }

    public int getPort() { return port; }

    @Nullable
    public List<URL> getUrls() { return urls; }

    @Nullable
    public ConnectionStatus getStatus() { return status; }

    // Methods
    public void start() throws CouchbaseLiteException { }

    public void stop() throws CouchbaseLiteException { }
}

