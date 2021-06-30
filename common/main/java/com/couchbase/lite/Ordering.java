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
 * An Ordering represents a single ordering component in the query ORDER BY clause.
 */
public abstract class Ordering {
    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------

    /**
     * SortOrder represents a single ORDER BY entity. You can specify either ascending or
     * descending order. The default order is ascending.
     */
    public static class SortOrder extends Ordering {
        private final Expression expression;
        private boolean isAscending;

        SortOrder(Expression expression) {
            this.expression = expression;
            this.isAscending = true;
        }

        /**
         * Set the order as ascending order.
         *
         * @return the OrderBy object.
         */
        @NonNull
        public Ordering ascending() {
            this.isAscending = true;
            return this;
        }

        /**
         * Set the order as descending order.
         *
         * @return the OrderBy object.
         */
        @NonNull
        public Ordering descending() {
            this.isAscending = false;
            return this;
        }

        @Nullable
        Object asJSON() {
            if (isAscending) { return expression.asJSON(); }

            final List<Object> json = new ArrayList<>();
            json.add("DESC");
            json.add(expression.asJSON());
            return json;
        }
    }

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    /**
     * Create a SortOrder, inherited from the OrderBy class, object by the given
     * property name.
     *
     * @param property the property name
     * @return the SortOrder object.
     */
    @NonNull
    public static SortOrder property(@NonNull String property) {
        Preconditions.assertNotNull(property, "property");
        return expression(Expression.property(property));
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    /**
     * Create a SortOrder, inherited from the OrderBy class, object by the given expression.
     *
     * @param expression the expression object.
     * @return the SortOrder object.
     */
    @NonNull
    public static SortOrder expression(@NonNull Expression expression) {
        Preconditions.assertNotNull(expression, "expression");
        return new SortOrder(expression);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @Nullable
    abstract Object asJSON();
}
