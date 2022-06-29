//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import com.couchbase.lite.ReplicatorType;
import com.couchbase.lite.internal.fleece.FLEncoder;


/**
 * POJO used to populate LiteCore struct of the same name
 */
public class C4ReplicationCollection {
    @NonNull
    final String scope;
    @NonNull
    final String name;

    final boolean push;
    final boolean pull;

    @Nullable
    final byte[] options;

    final boolean hasPushFilter;
    final boolean hasPullFilter;

    final long token;

    C4ReplicationCollection(
        @NonNull String scope,
        @NonNull String name,
        @NonNull ReplicatorType type,
        boolean hasPushFilter,
        boolean hasPullFilter,
        @NonNull Map<String, Object> options,
        long token) {
        this.scope = scope;
        this.name = name;
        this.push = (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PUSH);
        this.pull = (type == ReplicatorType.PUSH_AND_PULL) || (type == ReplicatorType.PULL);
        this.options = FLEncoder.encodeMap(options);
        this.hasPushFilter = hasPushFilter;
        this.hasPullFilter = hasPullFilter;
        this.token = token;
    }
}
