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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.core.C4Collection;


/**
 * A Standard query index.
 */
public final class ValueIndex extends Index {
    @NonNull
    private final List<ValueIndexItem> indexItems;

    ValueIndex(@NonNull ValueIndexItem... indexItems) { this.indexItems = Arrays.asList(indexItems); }

    @NonNull
    @Override
    List<Object> getJson() {
        final List<Object> items = new ArrayList<>();
        for (ValueIndexItem item: indexItems) { items.add(item.viExpression.asJSON()); }
        return items;
    }

    @Override
    void createIndex(@NonNull String name, @NonNull C4Collection c4Collection)
        throws LiteCoreException, CouchbaseLiteException {
        c4Collection.createValueIndex(name, QueryLanguage.JSON.getCode(), getIndexSpec(), null);
    }
}
