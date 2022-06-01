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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


// This is still assuming that we can cache the collections...
public class Scope {
    public static final String DEFAULT_NAME = "_default";

    @NonNull
    private final String name;
    @NonNull
    private final AbstractDatabase db;
    @NonNull
    private final Map<String, Collection> collections = new HashMap<>();

    Scope(@NonNull String name, @NonNull AbstractDatabase db) {
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
    public Set<Collection> getCollections() { return new HashSet<>(collections.values()); }

    /**
     * Get the named collection for the scope.
     *
     * @param name the name of the sought collection
     * @return the named collection or null
     */
    @Nullable
    public Collection getCollection(@NonNull String name) { return collections.get(name); }

    @NonNull
    @Override
    public String toString() { return db.getName() + "." + name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Scope)) { return false; }
        final Scope other = (Scope) o;
        return name.equals(other.name) && db.getName().equals(other.db.getName());
    }

    @Override
    public int hashCode() { return Objects.hash(name, db); }

    //---------------------------------------------
    // Package access
    //---------------------------------------------

    @NonNull
    Database getDatabase() { return (Database) db; }

    int getCollectionCount() { return collections.size(); }

    @NonNull
    Collection getOrAddCollection(@NonNull String collectionName) throws CouchbaseLiteException {
        final Collection collection = getCollection(collectionName);
        return (collection != null) ? collection : addCollection(collectionName);
    }

    @NonNull
    Collection addCollection(@NonNull String collectionName) throws CouchbaseLiteException {
        if (DEFAULT_NAME.equals(name)) { throw new IllegalArgumentException("Cannot create the default collection"); }
        final Collection collection = db.addCollection(this, collectionName);
        collections.put(name, collection);
        return collection;
    }

    void deleteCollection(@NonNull String name) throws CouchbaseLiteException {
        deleteCollection(collections.get(name));
    }

    void deleteCollection(@Nullable Collection collection) throws CouchbaseLiteException {
        if (collection == null) { return; }
        db.deleteCollection(collection);
        collections.remove(collection.getName());
    }

    @Nullable
    Collection getDefaultCollection() { return getCollection(Collection.DEFAULT_NAME); }

    void cacheCollection(@NonNull Collection collection) { collections.put(name, collection); }
}
