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
package com.couchbase.lite.internal.replicator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.cert.X509Certificate;


/**
 * Just delegate everything to the base class
 */
public class CBLTrustManager extends AbstractCBLTrustManager {
    public CBLTrustManager(
        @Nullable X509Certificate pinnedServerCert,
        boolean acceptAllCertificates, // ignore this and hardwire "false"
        boolean acceptOnlySelfSignedServerCertificate,
        @NonNull ServerCertsListener serverCertsListener) {
        super(pinnedServerCert, acceptOnlySelfSignedServerCertificate, false, serverCertsListener);
    }
}
