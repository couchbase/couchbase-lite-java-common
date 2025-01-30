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

import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.core.C4Collection;


/**
 * Configuration for a standard database index.
 */
public final class ValueIndexConfiguration extends IndexConfiguration {
    @Nullable
    private String where;

    public ValueIndexConfiguration(@NonNull String... expressions) { this(Arrays.asList(expressions)); }

    public ValueIndexConfiguration(@NonNull List<String> expressions) { super(expressions); }

    @Nullable
    public String getWhere() { return where; }

    @NonNull
    public ValueIndexConfiguration setWhere(@Nullable String where) {
        this.where = where;
        return this;
    }

    @Override
    void createIndex(@NonNull String name, @NonNull C4Collection c4Collection) throws LiteCoreException {
        c4Collection.createValueIndex(name, QueryLanguage.N1QL.getCode(), getIndexSpec(), where);
    }
}
