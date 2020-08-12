//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.replicator;

import android.support.annotation.NonNull;

import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.utils.Fn;


public class CBLWebSocket extends AbstractCBLWebSocket {
    protected CBLWebSocket(
        long handle,
        String scheme,
        String hostname,
        int port,
        String path,
        Map<String, Object> options,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener)
        throws GeneralSecurityException, URISyntaxException {
        super(handle, scheme, hostname, port, path, options, cookieStore, serverCertsListener);
    }
}
