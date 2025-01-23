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

import com.couchbase.lite.internal.fleece.Encodable;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MContext;


public abstract class AbstractCollection<T extends MCollection> implements CollectionInterface, Encodable {
    @NonNull
    protected final Object lock;

    @NonNull
    protected final T contents;

    protected AbstractCollection(@NonNull T collection) {
        contents = collection;
        final MContext context = collection.getContext();
        final BaseDatabase db = (context == null) ? null : context.getDatabase();
        lock = (db == null) ? new Object() : db.getDbLock();
    }

    public int count() {
        synchronized (contents) { return contents.count(); }
    }

    public boolean isEmpty() { return count() == 0; }

    /**
     * Encode an Array as a JSON string
     *
     * @return JSON encoded representation of the Array
     * @throws CouchbaseLiteException on encoder failure.
     */
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

    public void encodeTo(@NonNull FLEncoder enc) { contents.encodeTo(enc); }
}
