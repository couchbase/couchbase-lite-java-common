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

import java.util.List;

import org.json.JSONException;

import com.couchbase.lite.internal.utils.JSONUtils;


/**
 * Index represents an index: either a value index for regular queries or
 * full-text index for full-text queries (using the match operator).
 */
public abstract class Index extends AbstractIndex {
    Index() { }

    @NonNull
    abstract List<Object> getJson();

    @NonNull
    String getIndexSpec() throws CouchbaseLiteException {
        try { return JSONUtils.toJSON(getJson()).toString(); }
        catch (JSONException e) { throw new CouchbaseLiteException("Error encoding JSON", e); }
    }
}
