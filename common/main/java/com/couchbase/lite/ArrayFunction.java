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
 * Function provides array functions.
 */
public final class ArrayFunction {
    private ArrayFunction() { }

    /**
     * Creates an ARRAY_CONTAINS(expr, value) function that checks whether the given array
     * expression contains the given value or not.
     *
     * @param expression The expression that evaluate to an array.
     * @param value      The value to search for in the given array expression.
     * @return The ARRAY_CONTAINS(expr, value) function.
     */
    @NonNull
    public static Expression contains(@NonNull Expression expression, @NonNull Expression value) {
        return new Expression.FunctionExpression(
            "ARRAY_CONTAINS()",
            Preconditions.assertNotNull(expression, "expression"),
            Preconditions.assertNotNull(value, "value"));
    }

    /**
     * Creates an ARRAY_LENGTH(expr) function that returns the length of the given array
     * expression.
     *
     * @param expression The expression that evaluates to an array.
     * @return The ARRAY_LENGTH(expr) function.
     */
    @NonNull
    public static Expression length(@NonNull Expression expression) {
        return new Expression.FunctionExpression(
            "ARRAY_LENGTH()",
            Preconditions.assertNotNull(expression, "expression"));
    }
}
