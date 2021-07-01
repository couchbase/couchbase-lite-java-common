//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A meta property expression.
 */
public class MetaExpression extends Expression {
    @NonNull
    private final String keyPath;
    @Nullable
    private final String fromAlias; // Data Source Alias
    @Nullable
    private String columnName;

    MetaExpression(@NonNull String keyPath, @Nullable String columnName, @Nullable String from) {
        this.keyPath = keyPath;
        this.columnName = columnName;
        this.fromAlias = from;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    /**
     * Specifies an alias name of the data source to query the data from.
     *
     * @param alias The data source alias name.
     * @return The Meta expression with the given alias name specified.
     */
    @NonNull
    public Expression from(@NonNull String alias) {
        Preconditions.assertNotNull(alias, "alias");
        return new MetaExpression(keyPath, null, alias);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    @NonNull
    @Override
    Object asJSON() {
        final List<Object> json = new ArrayList<>();
        if (fromAlias != null) { json.add("." + fromAlias + "." + keyPath); }
        else { json.add("." + keyPath); }
        return json;
    }

    @NonNull
    String getColumnName() {
        if (columnName == null) {
            final String[] paths = keyPath.split("\\.");
            columnName = paths[paths.length - 1];
        }
        return columnName;
    }
}
