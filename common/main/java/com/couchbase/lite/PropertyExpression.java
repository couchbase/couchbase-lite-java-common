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


/**
 * Property expression
 */
public final class PropertyExpression extends Expression {
    static final String PROPS_ALL = "";

    @NonNull
    static PropertyExpression allFrom(@Nullable String from) {
        // Use data source alias name as the column name if specified:
        return new PropertyExpression(PROPS_ALL, from, (from != null ? from : PROPS_ALL));
    }

    @NonNull
    private final String keyPath;
    @Nullable
    private final String fromAlias; // Data Source Alias
    @Nullable
    private String columnName;

    PropertyExpression(@NonNull String keyPath) { this(keyPath, null); }

    private PropertyExpression(@NonNull String keyPath, @Nullable String from) { this(keyPath, from, null); }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    private PropertyExpression(@NonNull String keyPath, @Nullable String from, @Nullable String columnName) {
        this.keyPath = keyPath;
        this.fromAlias = from;
        this.columnName = columnName;
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    /**
     * Specifies an alias name of the data source to query the data from.
     *
     * @param alias The alias name of the data source.
     * @return The property Expression with the given data source alias name.
     */
    @NonNull
    public Expression from(@Nullable String alias) {
        return new PropertyExpression(keyPath, alias);
    }

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
        synchronized (this) {
            if (columnName == null) {
                final String[] paths = keyPath.split("\\.");
                columnName = paths[paths.length - 1];
            }
            return columnName;
        }
    }
}
