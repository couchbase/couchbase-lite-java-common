//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;

import com.couchbase.lite.DatabaseConfiguration;


@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class BaseImmutableDatabaseConfiguration {
    @NonNull
    private final String dbDir;

    protected BaseImmutableDatabaseConfiguration(@NonNull DatabaseConfiguration config) {
        this.dbDir = config.getDirectory();
    }

    @NonNull
    public final String getDirectory() { return dbDir; }
}
