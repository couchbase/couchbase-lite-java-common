//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Configuration for indexing property values within nested arrays
 * in documents, intended for use with the UNNEST query.
 */
public final class ArrayIndexConfiguration extends IndexConfiguration {
    @NonNull
    private static List<String> aggregateExpressions(@NonNull String expression, @Nullable String[] expressions) {
        final List<String> list = new ArrayList<>();
        list.add(expression);
        if (expressions != null) { list.addAll(Arrays.asList(expressions)); }
        return list;
    }

    @NonNull
    private static List<String> checkExpressions(@Nullable List<String> expressions) {
        if (expressions == null) { return Collections.singletonList(""); }
        if (expressions.size() == 1) {
            final String expr = expressions.get(0);
            if ((expr != null) && (expr.isEmpty())) {
                throw new IllegalArgumentException(
                    "An expression list should not contain a single empty string. Use null to specify no expressions.");
            }
        }
        return expressions;
    }

    private final String path;

    /**
     * Initializes the configuration with paths to the nested array with
     * no expressions constraining the values within the arrays to be indexed.
     *
     * @param path Path to the array, which can be nested to be indexed.
     *             Use "[]" to represent a property that is an array of each
     *             nested array level. For a single array or the last level
     *             array, the "[]" is optional. For instance, use
     *             "contacts[].phones" to specify an array of phones within each
     *             contact.
     */
    public ArrayIndexConfiguration(@NonNull String path) { this(path, null); }

    /**
     * Initializes the configuration with paths to the nested array
     * and the expressions for the values within the arrays to be indexed.
     * A null expression will cause a runtime error.
     *
     * @param path        Path to the array, which can be nested to be indexed.
     *                    Use "[]" to represent a property that is an array of each
     *                    nested array level. For a single array or the last level
     *                    array, the "[]" is optional. For instance, use
     *                    "contacts[].phones" to specify an array of phones within each
     *                    contact.
     * @param expressions A list of strings, where each string represents an expression
     *                    defining the values within the array to be indexed. Expressions
     *                    may not be null.
     */
    public ArrayIndexConfiguration(@NonNull String path, @NonNull String expression, @Nullable String... expressions) {
        this(path, aggregateExpressions(expression, expressions));
    }

    /**
     * Initializes the configuration with paths to the nested array
     * and the expressions for the values within the arrays to be indexed.
     *
     * @param path        Path to the array, which can be nested to be indexed.
     *                    Use "[]" to represent a property that is an array of each
     *                    nested array level. For a single array or the last level
     *                    array, the "[]" is optional. For instance, use
     *                    "contacts[].phones" to specify an array of phones within each
     *                    contact.
     * @param expressions An optional list of strings, where each string represents an expression
     *                    defining the values within the array to be indexed. If the array specified
     *                    by the path contains scalar values, this parameter can be null:
     *                    see <code>ArrayIndexConfiguration(String)</code>
     */
    public ArrayIndexConfiguration(@NonNull String path, @Nullable List<String> expressions) {
        super(checkExpressions(expressions));
        this.path = Preconditions.assertNotNull(path, "path");
    }

    /**
     * Path to the array, which can be nested.
     */
    @NonNull
    public String getPath() { return path; }

    @Override
    void createIndex(@NonNull String name, @NonNull C4Collection c4Collection)
        throws LiteCoreException, CouchbaseLiteException {
        final String indexSpec = getIndexSpec();
        c4Collection.createArrayIndex(name, path, indexSpec);
    }
}
