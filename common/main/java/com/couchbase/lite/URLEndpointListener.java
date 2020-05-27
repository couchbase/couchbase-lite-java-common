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
    private final URLEndpointListenerConfiguration config;

    @Nullable
    private int port;

    @Nullable
    private List<URL> urls;

    @Nullable
    private ConnectionStatus status;

    // Constructors
    public URLEndpointListener(@NonNull URLEndpointListenerConfiguration config) { this.config = config; }

    public URLEndpointListenerConfiguration getConfig() { return config; }

    public int getPort() { return port; }

    public void setPort(int port) { this.port = port; }

    @Nullable
    public List<URL> getUrls() { return urls; }

    public void setUrls(@Nullable List<URL> urls) { this.urls = urls; }

    @Nullable
    public ConnectionStatus getStatus() { return status; }

    public void setStatus(@Nullable ConnectionStatus status) { this.status = status; }

    // Methods
    public void start() { }

    public void stop() { }
}

