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


/**
 * POJO used to populate LiteCore struct of the same name
 */
class C4ReplicationCollection {
    final long token;

    @NonNull
    final String scope;
    @NonNull
    final String name;

    final int push;
    final boolean hasPushFilter;
    final int pull;
    final boolean hasPullFilter;

    @NonNull
    final byte[] options;

    C4ReplicationCollection(
        long token,
        @NonNull String scope,
        @NonNull String name,
        int push,
        boolean hasPushFilter,
        int pull,
        boolean hasPullFilter,
        @NonNull byte[] options) {
        this.token = token;
        this.scope = scope;
        this.name = name;
        this.push = push;
        this.hasPushFilter = hasPushFilter;
        this.pull = pull;
        this.hasPullFilter = hasPullFilter;
        this.options = new byte[options.length];
        System.arraycopy(options, 0, this.options, 0, this.options.length);
    }
}
