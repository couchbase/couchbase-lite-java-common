package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.util.Set;


public interface Indexable {
    /**
     * Returns all index names.
     *
     * @return all index names
     * @throws CouchbaseLiteException on failure
     */
    @NonNull
    Set<String> getIndexes() throws CouchbaseLiteException;

    /**
     * Create an index with the index name and config.
     *
     * @param name   index name
     * @param config index configuration.
     * @throws CouchbaseLiteException on failure
     */
    void createIndex(String name, IndexConfiguration config) throws CouchbaseLiteException;

    /**
     * Delete an index by name.
     *
     * @param name name of the index to delete.
     * @throws CouchbaseLiteException on failure
     */
    void deleteIndex(String name) throws CouchbaseLiteException;
}
