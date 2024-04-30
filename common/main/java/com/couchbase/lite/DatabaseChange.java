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

import java.util.Collections;
import java.util.List;


/**
 * Provides details about a Database change.
 *
 * @deprecated Use CollectionChange
 */
@Deprecated
public class DatabaseChange {
    @NonNull
    private static Collection getDefaultCollection(@NonNull Database database) {
        try { return database.getDefaultCollection(); }
        catch (CouchbaseLiteException e) {
            throw new CouchbaseLiteError("Failed retrieving default collection for database: " + database.getName(), e);
        }
    }

    @NonNull
    private final List<String> documentIDs;
    @NonNull
    private final Collection collection;

    DatabaseChange(@NonNull Collection collection, @NonNull List<String> documentIDs) {
        this.collection = collection;
        this.documentIDs = Collections.unmodifiableList(documentIDs);
    }

    @Deprecated
    DatabaseChange(@NonNull Database database, @NonNull List<String> documentIDs) {
        this(getDefaultCollection(database), documentIDs);
    }

    /**
     * Returns the collection
     */
    @NonNull
    public Collection getCollection() { return collection; }

    /**
     * Returns the database instance
     */
    @NonNull
    public Database getDatabase() { return collection.getDatabase(); }

    /**
     * Returns the list of the changed document IDs
     *
     * @return a list of IDs for changed documents
     */
    @NonNull
    public List<String> getDocumentIDs() { return documentIDs; }

    @NonNull
    @Override
    public String toString() { return "DatabaseChange{" + getDatabase() + ", " + documentIDs + '}'; }
}
