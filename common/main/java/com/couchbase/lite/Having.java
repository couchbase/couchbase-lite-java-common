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

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Having represents a HAVING clause of the query statement used for filtering the aggregated values
 * from the the GROUP BY clause.
 */
public final class Having extends BuilderQuery implements OrderByRouter, LimitRouter {
    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private final Expression expression;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    Having(@NonNull BuilderQuery query, @NonNull Expression expression) {
        copy(query);
        this.expression = expression;
        setHaving(this);
    }

    //---------------------------------------------
    // implementation of OrderByRouter
    //---------------------------------------------

    /**
     * Create and chain an ORDER BY component for specifying the orderings of the query result.
     *
     * @param orderings an array of the ORDER BY expressions.
     * @return the ORDER BY component.
     */
    @NonNull
    @Override
    public OrderBy orderBy(@NonNull Ordering... orderings) {
        Preconditions.assertNotNull(orderings, "orderings");
        return new OrderBy(this, Arrays.asList(orderings));
    }

    //---------------------------------------------
    // implementation of FromRouter
    //---------------------------------------------

    /**
     * Creates and chains a Limit object to limit the number query results.
     *
     * @param limit The limit expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @NonNull
    @Override
    public Limit limit(@NonNull Expression limit) { return limit(limit, null); }

    /**
     * Creates and chains a Limit object to skip the returned results for the given offset
     * position and to limit the number of results to not more than the given limit value.
     *
     * @param limit  The limit expression.
     * @param offset The offset expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @NonNull
    @Override
    public Limit limit(@NonNull Expression limit, @Nullable Expression offset) {
        Preconditions.assertNotNull(limit, "limit");
        return new Limit(this, limit, offset);
    }

    //---------------------------------------------
    // package level
    //---------------------------------------------

    @Nullable
    Object asJSON() { return expression != null ? expression.asJSON() : null; }
}
