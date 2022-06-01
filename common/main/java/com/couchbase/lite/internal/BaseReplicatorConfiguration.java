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
package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.Collection;
import com.couchbase.lite.CollectionConfiguration;


public class BaseReplicatorConfiguration {
    @NonNull
    private final Map<Collection, CollectionConfiguration> internalCollectionConfigurations = new HashMap<>();

    // subclasses can read the collection directly but not write it.
    @NonNull
    protected final Map<Collection, CollectionConfiguration> collectionConfigurations
        = Collections.unmodifiableMap(internalCollectionConfigurations);

    protected BaseReplicatorConfiguration() { }

    protected BaseReplicatorConfiguration(@Nullable Map<Collection, CollectionConfiguration> collections) {
        if (collections != null) { internalCollectionConfigurations.putAll(collections); }
    }

    protected void addCollectionInternal(@Nullable Collection coll, @Nullable CollectionConfiguration config) {
        if (coll != null) { internalCollectionConfigurations.put(coll, config); }
    }

    protected void removeCollectionInternal(@Nullable Collection coll) {
        if (coll != null) { internalCollectionConfigurations.remove(coll); }
    }

    @NonNull
    Map<Collection, CollectionConfiguration> getCollectionConfigurations() { return collectionConfigurations; }
}
