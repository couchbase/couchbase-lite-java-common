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
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * SelectResult represents the result of a query.
 */
public class SelectResult {

    /**
     * SelectResult.From is a SelectResult for which you can specify a data source alias.
     */
    public static final class From extends SelectResult {
        private From(@NonNull Expression expression) { super(expression); }

        /**
         * Specifies the data source alias for the SelectResult object.
         *
         * @param alias The data source alias name.
         * @return The SelectResult object with the data source alias name specified.
         */
        @NonNull
        public SelectResult from(@NonNull String alias) {
            Preconditions.assertNotNull(alias, "alias");
            setExpression(PropertyExpression.allFrom(alias));
            return this;
        }
    }

    /**
     * SelectResult.As is a SelectResult with an alias.
     * The alias can be used as the key for accessing the result value from the query Result.
     */
    public static final class As extends SelectResult {
        @Nullable
        private String alias;

        private As(@NonNull Expression expression) { super(expression); }

        /**
         * Specifies the alias for the SelectResult object.
         *
         * @param alias The alias name.
         * @return The SelectResult object with the alias name specified.
         */
        @NonNull
        public As as(@NonNull String alias) {
            Preconditions.assertNotNull(alias, "alias");
            this.alias = alias;
            return this;
        }

        @Nullable
        Object asJSON() {
            final Object prop = super.asJSON();
            if (alias == null) { return prop; }

            final List<Object> json = new ArrayList<>();
            json.add("AS");
            json.add(prop);
            json.add(alias);
            return json;
        }
    }

    /**
     * Creates a SelectResult with the given property name.
     *
     * @param property The property name.
     * @return a SelectResult.From that can be used to alias the property.
     */
    @NonNull
    public static SelectResult.As property(@NonNull String property) {
        Preconditions.assertNotNull(property, "property");
        return new SelectResult.As(PropertyExpression.property(property));
    }

    /**
     * Creates a SelectResult object with the given expression.
     *
     * @param expression The expression.
     * @return a SelectResult.From that can be used to alias the property.
     */
    @NonNull
    public static SelectResult.As expression(@NonNull Expression expression) {
        Preconditions.assertNotNull(expression, "expression");
        return new SelectResult.As(expression);
    }

    /**
     * Creates a SelectResult that contains values for all properties matching the query.
     * The result is a single CBLMutableDictionary whose key is the name of the data source.
     *
     * @return a SelectResult.From that can be used to alias the property.
     */
    @NonNull
    public static SelectResult.From all() { return new SelectResult.From(PropertyExpression.allFrom(null)); }


    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private Expression selectExpression;

    protected SelectResult(@NonNull Expression expression) { this.selectExpression = expression; }

    protected final void setExpression(@NonNull Expression expression) { this.selectExpression = expression; }

    @Nullable
    Object asJSON() { return selectExpression.asJSON(); }
}
