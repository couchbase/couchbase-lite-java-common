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

import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Configuration for indexing property values within nested arrays
 * in documents, intended for use with the UNNEST query.
 */
public final class ArrayIndexConfiguration extends IndexConfiguration {
    private final String path;

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
     * @param expressions An optional list of strings, where each string
     *                    represents an expression defining the values within the array
     *                    to be indexed. If the array specified by the path contains
     *                    scalar values, this parameter can be null.
     */
    public ArrayIndexConfiguration(@NonNull String path, @NonNull String... expressions) {
        this(path, Arrays.asList(expressions));
    }

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
    public ArrayIndexConfiguration(@NonNull String path) { this(path, Arrays.asList("")); }

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
     * @param expressions An optional list of strings, where each string
     *                    represents an expression defining the values within the array
     *                    to be indexed. If the array specified by the path contains
     *                    scalar values, this parameter can be null.
     */
    public ArrayIndexConfiguration(@NonNull String path, @NonNull List<String> expressions) {
        super(expressions);
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
        c4Collection.createArrayIndex(name, path, getIndexSpec());
    }
}
