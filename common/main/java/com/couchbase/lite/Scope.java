//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
import java.util.Set;


//
public class Scope {
    public static final String DEFAULT_NAME = "_default";

    @NonNull
    private final String name;
    @NonNull
    private final Database db;

    Scope(@NonNull Database db) { this(DEFAULT_NAME, db); }

    Scope(@NonNull String name, @NonNull Database db) {
        this.name = name;
        this.db = db;
    }

    /**
     * Get the scope name.
     *
     * @return Scope name
     */
    @NonNull
    public String getName() { return name; }

    /**
     * Get all collections in the scope.
     *
     * @return a set of all collections in the scope
     */
    @NonNull
    public Set<Collection> getCollections() throws CouchbaseLiteException { return db.getCollections(name); }

    /**
     * Get the named collection for the scope.
     *
     * @param collectionName the name of the sought collection
     * @return the named collection or null
     */
    @Nullable
    public Collection getCollection(@NonNull String collectionName) throws CouchbaseLiteException {
        return db.getCollection(collectionName, name);
    }

    @NonNull
    public Database getDatabase() { return db; }

    @NonNull
    @Override
    public String toString() { return db.getName() + "." + name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Scope)) { return false; }
        final Scope other = (Scope) o;
        return (db == other.db) && name.equals(other.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name, db); }
}
