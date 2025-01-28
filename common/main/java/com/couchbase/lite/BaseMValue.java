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
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This class exists solely to provide access to package visible symbols, to MValue
 */
@Internal("This class is not part of the public API")
public abstract class BaseMValue {
    protected static final String META_PROP_TYPE = Blob.META_PROP_TYPE;
    protected static final String TYPE_BLOB = Blob.TYPE_BLOB;

    protected static final String PROP_DIGEST = Blob.PROP_DIGEST;
    protected static final String PROP_LENGTH = Blob.PROP_LENGTH;
    protected static final String PROP_STUB = Blob.PROP_STUB;
    protected static final String PROP_REVPOS = Blob.PROP_REVPOS;


    protected BaseMValue() { }

    @NonNull
    protected Blob getBlob(@NonNull DbContext context, @NonNull FLDict flDict) {
        return new Blob(
            Preconditions.assertNotNull(context.getDatabase(), "database"),
            flDict.asMap(String.class, Object.class));
    }

    @NonNull
    protected Array getArray(@NonNull MValue mVal, @Nullable MCollection parent) {
        return ((parent == null) || !parent.hasMutableChildren())
            ? new Array(mVal, parent)
            : new MutableArray(mVal, parent);
    }

    @NonNull
    protected Dictionary getDictionary(@NonNull MValue mVal, @Nullable MCollection parent) {
        return ((parent == null) || !parent.hasMutableChildren())
            ? new Dictionary(mVal, parent)
            : new MutableDictionary(mVal, parent);
    }
}
