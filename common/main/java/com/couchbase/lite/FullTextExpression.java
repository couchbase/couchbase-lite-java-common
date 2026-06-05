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

import java.util.Arrays;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Full-text expression
 *
 * @deprecated Use FullTextFunction.match()
 */
@Deprecated
public final class FullTextExpression {
    static final class FullTextMatchExpression extends Expression {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        @NonNull
        private final String indexName;
        @NonNull
        private final String text;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        FullTextMatchExpression(@NonNull String indexName, @NonNull String text) {
            this.indexName = indexName;
            this.text = text;
        }

        //---------------------------------------------
        // package level access
        //---------------------------------------------

        @NonNull
        @Override
        Object asJSON() { return Arrays.asList("MATCH()", indexName, text); }
    }

    /**
     * Creates a full-text expression with the given full-text index name.
     *
     * @param name The full-text index name.
     * @return The full-text expression.
     * @deprecated Use FullTextFunction.match()
     */
    @Deprecated
    @NonNull
    public static FullTextExpression index(@NonNull String name) {
        Preconditions.assertNotNull(name, "name");
        return new FullTextExpression(name);
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final String name;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextExpression(@NonNull String name) { this.name = name; }

    /**
     * Creates a full-text match expression with the given search text.
     *
     * @param query The search text
     * @return The full-text match expression
     * @deprecated Use FullTextFunction.match()
     */
    @Deprecated
    @NonNull
    public Expression match(@NonNull String query) {
        Preconditions.assertNotNull(query, "query");
        return new FullTextMatchExpression(this.name, query);
    }
}
