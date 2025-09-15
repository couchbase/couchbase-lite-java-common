//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal

import com.couchbase.lite.Authenticator
import com.couchbase.lite.Collection
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.ReplicatorConfiguration
import com.couchbase.lite.ReplicatorType
import java.security.cert.X509Certificate

abstract class AbstractWorkManagerReplicatorConfiguration(protected val replConfig: ReplicatorConfiguration) {
    var type: ReplicatorType by replConfig::type
    var authenticator: Authenticator? by replConfig::authenticator
    var headers: Map<String, String>? by replConfig::headers
    var pinnedServerCertificate: X509Certificate? by replConfig::pinnedServerX509Certificate
    var enableAutoPurge
        get() = replConfig.isAutoPurgeEnabled
        set(value) {
            replConfig.isAutoPurgeEnabled = value
        }

    fun getConfig() = ReplicatorConfiguration(replConfig)
}

