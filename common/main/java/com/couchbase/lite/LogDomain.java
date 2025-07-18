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
package com.couchbase.lite;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;


/**
 * Log domain
 */
public enum LogDomain {
    DATABASE, QUERY, REPLICATOR, NETWORK, LISTENER, PEER_DISCOVERY, MULTIPEER;

    /**
     * All domains.
     */
    public static final Set<LogDomain> ALL;
    static { ALL = Collections.unmodifiableSet(EnumSet.allOf(LogDomain.class)); }

    /**
     * @deprecated Use LogDomain.ALL
     */
    @Deprecated
    public static final EnumSet<LogDomain> ALL_DOMAINS;
    static { ALL_DOMAINS = EnumSet.allOf(LogDomain.class); }
}

