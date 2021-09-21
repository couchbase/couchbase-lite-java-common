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


import androidx.annotation.NonNull;

import java.util.Arrays;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Full-text function.
 */
public final class FullTextFunction {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextFunction() { }

    /**
     * Creates a full-text rank function with the given full-text index name.
     * The rank function indicates how well the current query result matches
     * the full-text query when performing the match comparison.
     *
     * @param indexName The index name.
     * @return The full-text rank function.
     */
    @NonNull
    public static Expression rank(@NonNull String indexName) {
        Preconditions.assertNotNull(indexName, "indexName");
        return new Expression.FunctionExpression("RANK()", Arrays.asList(Expression.string(indexName)));
    }
}
