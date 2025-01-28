//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
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

import java.util.Objects;

import com.couchbase.lite.internal.BaseJFleeceCollection;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.FleeceEncodable;
import com.couchbase.lite.internal.fleece.JSONEncodable;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MContext;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.Internal;


/**
 * AbstractJFleeceCollection is the implementation of JFleeceCollectionInterface common to Array and Dictionary.
 *
 * @param <T> the type of the backing collection
 */
@Internal("This class in not part of the public API")
public abstract class AbstractJFleeceCollection<T extends MCollection>
    extends BaseJFleeceCollection
    implements JFleeceCollectionInterface, JSONEncodable, FleeceEncodable {
    @NonNull
    protected final Object lock;

    @NonNull
    protected final T contents;

    protected AbstractJFleeceCollection(@NonNull T collection) {
        contents = collection;
        final MContext context = collection.getContext();
        final BaseDatabase db = (context == null) ? null : context.getDatabase();
        lock = (db == null) ? new Object() : db.getDbLock();
    }

    @NonNull
    public abstract AbstractJFleeceCollection<T> toMutable();

    public int count() {
        synchronized (contents) { return contents.count(); }
    }

    public boolean isEmpty() { return count() == 0; }

    @Override
    @Nullable
    protected Object toJFleece(@Nullable Object value) {
        return (value == this) ? toMutable() : super.toJFleece(value);
    }

    // !!! Should be synchronized??
    public void encodeTo(@NonNull FLEncoder enc) { contents.encodeTo(enc); }

    /**
     * Encode an Array as a JSON string
     *
     * @return JSON encoded representation of the Array
     * @throws CouchbaseLiteException on encoder failure.
     */
    // !!! Should be synchronized??
    @NonNull
    public String toJSON() throws CouchbaseLiteException {
        try (FLEncoder.JSONEncoder encoder = FLEncoder.getJSONEncoder()) {
            contents.encodeTo(encoder);
            return encoder.finishJSON();
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Cannot encode array: " + this);
        }
    }

    // Assume that array and dict values are always different to avoid expensive comparisons.
    protected boolean willMutate(Object newValue, @NonNull MValue oldValue, MCollection container) {
        final FLValue val = oldValue.getFLValue();
        final int oldType = (val != null) ? val.getType() : FLValue.UNDEFINED;
        return ((oldType == FLValue.UNDEFINED)
            || (oldType == FLValue.DICT)
            || (newValue instanceof Dictionary)
            || (oldType == FLValue.ARRAY)
            || (newValue instanceof Array)
            || !Objects.equals(newValue, oldValue.toJFleece(container)));
    }
}
