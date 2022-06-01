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

import androidx.annotation.NonNull;


/**
 * The listener interface for receiving Database change events.
 */
@FunctionalInterface
public interface CollectionChangeListener extends ChangeListener<CollectionChange> {
    /**
     * Callback from Lite Core when the collection changes
     *
     * @param change change information
     */
    @Override
    void changed(@NonNull CollectionChange change);
}
