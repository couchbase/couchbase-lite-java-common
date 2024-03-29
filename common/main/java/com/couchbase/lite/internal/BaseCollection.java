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

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.internal.fleece.FLDict;


public abstract class BaseCollection {
    @NonNull
    protected final Database db;

    protected BaseCollection(@NonNull Database db) { this.db = db; }

    @NonNull
    protected abstract Document createFilterDocument(
        @NonNull String docId,
        @NonNull String revId,
        @NonNull FLDict body);
}
