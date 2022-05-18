package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.util.List;

public interface Indexable {
    @NonNull
    List<String> indexes() throws CouchbaseLiteException;
    void createIndex(String name, IndexConfiguration config) throws CouchbaseLiteException;
    void deleteIndex(String name) throws CouchbaseLiteException;
}
