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

import java.util.ArrayList;
import java.util.List;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Property expression
 */
public final class PropertyExpression extends Expression {
    static final String PROPS_ALL = "";

    @NonNull
    static PropertyExpression allFrom(@Nullable String from) { return new PropertyExpression(PROPS_ALL, from); }

    @NonNull
    private final String keyPath;
    @Nullable
    private final String fromAlias; // Data Source Alias

    PropertyExpression(@NonNull String keyPath) { this(keyPath, null); }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    private PropertyExpression(@NonNull String keyPath, @Nullable String fromAlias) {
        this.keyPath = keyPath;
        this.fromAlias = fromAlias;
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    /**
     * Specifies an alias name of the data source to query the data from.
     *
     * @param fromAlias The alias name of the data source.
     * @return The property Expression with the given data source alias name.
     */
    @NonNull
    public Expression from(@NonNull String fromAlias) {
        Preconditions.assertNotNull(fromAlias, "fromAlias");
        return new PropertyExpression(keyPath, fromAlias);
    }

    @NonNull
    @Override
    Object asJSON() {
        final List<Object> json = new ArrayList<>();
        json.add("." + ((fromAlias == null) ? "" : fromAlias + ".") + keyPath);
        return json;
    }
}
