//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.utils.Internal;


/**
 * This is an internal interface and not part of the public API.
 */
@Internal("This interface is not part of the public API")
public interface Container {
    @Nullable
    static Object toPureJava(@Nullable Object value) {
        if (value == null) { return null; }
        else if (value instanceof Dictionary) { return ((Dictionary) value).toMap(); }
        else if (value instanceof Array) { return ((Array) value).toList(); }
        else { return value; }
    }

    int count();

    @NonNull
    String toJSON() throws CouchbaseLiteException;
}
