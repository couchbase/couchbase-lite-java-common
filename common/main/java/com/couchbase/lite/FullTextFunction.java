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

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Full-text functions.
 */
public final class FullTextFunction {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextFunction() { }

    /**
     * Creates a full-text rank function with the given full-text index expression.
     * The rank function indicates how well the current query result matches
     * the full-text query when performing the match comparison.
     *
     * @param index The full-text index expression.
     * @return The full-text rank function.
     */
    @NonNull
    public static Expression rank(@NonNull IndexExpression index) {
        return new Expression.IdxExpression("RANK()", Preconditions.assertNotNull(index, "index"));
    }

    /**
     * Creates a full-text match() function  with the given full-text index expression and the query text
     *
     * @param index  The full-text index expression.
     * @param query The query string.
     * @return The full-text match() function expression.
     */
    @NonNull
    public static Expression match(@NonNull IndexExpression index, @NonNull String query) {
        return new Expression.IdxExpression(
            "MATCH()",
            Preconditions.assertNotNull(index, "index"),
            Expression.string(query));
    }

    /**
     * Creates a full-text rank function with the given full-text index name.
     * The rank function indicates how well the current query result matches
     * the full-text query when performing the match comparison.
     *
     * @param indexName The index name.
     * @return The full-text rank function.
     * @deprecated Use: FullTextFunction.rank(IndexExpression)
     */
    @Deprecated
    @NonNull
    public static Expression rank(@NonNull String indexName) {
        return new Expression.FunctionExpression(
            "RANK()",
            Expression.string(Preconditions.assertNotNull(indexName, "indexName")));
    }
}
