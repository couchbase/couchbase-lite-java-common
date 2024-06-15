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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A Parameters object used for setting values to the query parameters defined in the query.
 */
public class Parameters {
    private static final class ImmutableParameters extends Parameters {
        private ImmutableParameters(@Nullable Parameters parameters) { super(parameters); }

        @NonNull
        @Override
        public Parameters setValue(@NonNull String name, @Nullable Object value) {
            throw new CouchbaseLiteError("Attempt to write read-only parameters.");
        }
    }

    @NonNull
    private final Map<String, Object> map;

    public Parameters() { this(null); }

    public Parameters(@Nullable Parameters parameters) {
        map = (parameters == null) ? new HashMap<>() : new HashMap<>(parameters.map);
    }

    /**
     * Gets a parameter's value.
     *
     * @param name The parameter name.
     * @return The parameter value.
     */
    @Nullable
    public final Object getValue(@NonNull String name) {
        Preconditions.assertNotNull(name, "name");
        return map.get(name);
    }

    /**
     * Gets a parameter's value.
     *
     * @param name the key.
     * @return The value in the dictionary at the key (or null).
     * @throws ClassCastException if the value is not of the passed class.
     */
    @Nullable
    public <T> T getValue(@NonNull Class<T> klass, @NonNull String name) {
        final Object val = getValue(name);
        return (val == null) ? null : klass.cast(val);
    }

    /**
     * Set an String value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The String value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setString(@NonNull String name, @Nullable String value) { return setValue(name, value); }

    /**
     * Set an Number value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Number value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setNumber(@NonNull String name, @Nullable Number value) { return setValue(name, value); }

    /**
     * Set an int value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The int value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setInt(@NonNull String name, int value) { return setValue(name, value); }

    /**
     * Set an long value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The long value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setLong(@NonNull String name, long value) { return setValue(name, value); }

    /**
     * Set a float value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The float value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setFloat(@NonNull String name, float value) { return setValue(name, value); }

    /**
     * Set a double value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The double value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setDouble(@NonNull String name, double value) { return setValue(name, value); }

    /**
     * Set a boolean value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The boolean value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setBoolean(@NonNull String name, boolean value) { return setValue(name, value); }

    /**
     * Set a date value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     * <p>
     * Note: The date value is stored as a string in the ISO 8601 format.
     * params.setDate("foo", new Date()).getValue("foo") returns a string in ISO 8601 format, not a Date.
     *
     * @param name  The parameter name.
     * @param value The date value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setDate(@NonNull String name, @Nullable Date value) { return setValue(name, value); }

    /**
     * Set the Blob value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Blob value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setBlob(@NonNull String name, @Nullable Blob value) { return setValue(name, value); }

    /**
     * Set the Array value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     * <p>
     * Note: The Array is not copied before insertion into the Parameters object: changes to the
     * Array after it is set in the Array but before the Parameters are attached to a Query,
     * will be reflected in the Query.  Changes to the Dictionary after the Parameters are attached
     * to a Query will not be reflected in the Query and will leave the Parameters in an inconsistent state.
     *
     * @param name  The parameter name.
     * @param value The Array value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setArray(@NonNull String name, @Nullable Array value) { return setValue(name, value); }

    /**
     * Set the Dictionary value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     * <p>
     * Note: The Dictionary is not copied before insertion into the Parameters object: changes to the
     * Dictionary after it is set in the Array but before the Parameters are attached to a Query,
     * will be reflected in the Query.  Changes to the Dictionary after the Parameters are attached
     * to a Query will not be reflected in the Query and will leave the Parameters in an inconsistent state.
     *
     * @param name  The parameter name.
     * @param value The Dictionary value.
     * @return The self object.
     */
    @NonNull
    public final Parameters setDictionary(@NonNull String name, @Nullable Dictionary value) {
        return setValue(name, value);
    }

    /**
     * Set a value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The value.
     * @return The self object.
     */
    @NonNull
    public Parameters setValue(@NonNull String name, @Nullable Object value) {
        Preconditions.assertNotNull(name, "name");
        map.put(name, Fleece.toCBLObject(value));
        return this;
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    @NonNull
    final Parameters readOnlyCopy() { return new ImmutableParameters(this); }

    // NOTE: the FLSliceResult returned by this method must be released by the caller
    @NonNull
    final FLSliceResult encode() throws LiteCoreException {
        try (FLEncoder encoder = FLEncoder.getManagedEncoder()) {
            encoder.setArg(Blob.ENCODER_ARG_QUERY_PARAM, true);
            encoder.write(map);
            return encoder.finish2();
        }
    }
}
