//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//

package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.core.C4Collection;


/**
 *
 */
public final class Collection {
    public static final String DEFAULT_NAME = "_default";

    @NonNull
    static Collection createCollection(
        @NonNull Database db,
        @NonNull String scopeName,
        @NonNull String collectionName)
        throws CouchbaseLiteException {
        try { return new Collection(db, db.addC4Collection(scopeName, collectionName)); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @Nullable
    static Collection getCollection(
        @NonNull Database db,
        @NonNull String scopeName,
        @NonNull String collectionName)
        throws CouchbaseLiteException {
        try {
            final C4Collection c4Coll = db.getC4Collection(scopeName, collectionName);
            return (c4Coll == null) ? null : new Collection(db, c4Coll);
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @Nullable
    static Collection getDefaultCollection(@NonNull Database db) throws CouchbaseLiteException {
        try {
            final C4Collection c4Coll = db.getDefaultC4Collection();
            return (c4Coll == null) ? null : new Collection(db, c4Coll);
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @NonNull
    private final Database db;

    @SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField"})
    @NonNull
    private final C4Collection c4Collection;

    // Collections must be immutable
    Collection(@NonNull Database db, @NonNull C4Collection c4Collection) {
        this.db = db;
        this.c4Collection = c4Collection;
    }

    @NonNull
    @Override
    public String toString() { return c4Collection.getScope() + "." + c4Collection.getName(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Collection)) { return false; }
        final Collection other = (Collection) o;
        return c4Collection.getScope().equals(other.c4Collection.getScope())
            && c4Collection.getName().equals(other.c4Collection.getName());
    }

    @Override
    public int hashCode() { return Objects.hash(c4Collection.getScope(), c4Collection.getName()); }

    /**
     * Get scope
     */
    @NonNull
    public Scope getScope() { return new Scope(c4Collection.getScope(), db); }

    /**
     * Return the collection name
     */
    @NonNull
    public String getName() { return c4Collection.getName(); }

    /**
     * The number of documents in the collection.
     */
    public long getCount() { return c4Collection.getDocumentCount(); }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the collection, the value returned will be null.
     */
    @Nullable
    public Document getDocument(@NonNull String id) { return db.getDocument(id); }

    /**
     * Save a document into the collection. The default concurrency control, lastWriteWins,
     * will be used when there is conflict during  save.
     * <p>
     * When saving a document that already belongs to a collection, the collection instance of
     * the document and this collection instance must be the same, otherwise, the InvalidParameter
     * error will be thrown.
     */
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException { db.save(document); }

    /**
     * Save a document into the collection with a specified concurrency control. When specifying
     * the failOnConflict concurrency control, and conflict occurred, the save operation will fail with
     * 'false' value returned.
     * When saving a document that already belongs to a collection, the collection instance of the
     * document and this collection instance must be the same, otherwise, the InvalidParameter
     * error will be thrown.
     */
    public boolean save(@NonNull MutableDocument document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        return db.save(document, concurrencyControl);
    }

    /**
     * Save a document into the collection with a specified conflict handler. The specified conflict handler
     * will be called if there is conflict during save. If the conflict handler returns 'false', the save
     * operation
     * will be canceled with 'false' value returned.
     * <p>
     * When saving a document that already belongs to a collection, the collection instance of the
     * document and this collection instance must be the same, otherwise, the InvalidParameter error
     * will be thrown.
     */
    public boolean save(@NonNull MutableDocument document, @NonNull ConflictHandler conflictHandler)
        throws CouchbaseLiteException {
        return db.save(document, conflictHandler);
    }

    /**
     * Delete a document from the collection. The default concurrency control, lastWriteWins, will be used
     * when there is conflict during delete. If the document doesn't exist in the collection, the NotFound
     * error will be thrown.
     * <p>
     * When deleting a document that already belongs to a collection, the collection instance of
     * the document and this collection instance must be the same, otherwise, the InvalidParameter error
     * will be thrown.
     */
    public void delete(@NonNull Document document) throws CouchbaseLiteException { db.delete(document); }

    /**
     * Delete a document from the collection with a specified concurrency control. When specifying
     * the failOnConflict concurrency control, and conflict occurred, the delete operation will fail with
     * 'false' value returned.
     * <p>
     * When deleting a document, the collection instance of the document and this collection instance
     * must be the same, otherwise, the InvalidParameter error will be thrown.
     */
    public boolean delete(@NonNull Document document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        return db.delete(document, concurrencyControl);
    }

    /**
     * When purging a document, the collection instance of the document and this collection instance
     * must be the same, otherwise, the InvalidParameter error will be thrown.
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException { db.purge(document); }

    /**
     * Purge a document by id from the collection. If the document doesn't exist in the collection,
     * the NotFound error will be thrown.
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException { db.purge(id); }


    /**
     * Set an expiration date to the document of the given id. Setting a nil date will clear the expiration.
     */
    public void setDocumentExpiration(@NonNull String id, @Nullable Date expiration) throws CouchbaseLiteException {
        db.setDocumentExpiration(id, expiration);
    }

    /**
     * Get the expiration date set to the document of the given id.
     */
    @Nullable
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        return db.getDocumentExpiration(id);
    }

    /**
     * Add a change listener to listen to change events occurring to a document of the given document id.
     * To remove the listener, call remove() function on the returned listener token.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String id, @NonNull DocumentChangeListener listener) {
        return db.addDocumentChangeListener(id, listener);
    }

    /**
     * Adds a change listener for the changes that occur to the specified document with an executor on which
     * the changes will be posted to the listener.  If the executor is not specified, the changes will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(
        @NonNull String id,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        return db.addDocumentChangeListener(id, executor, listener);
    }

    @NonNull
    public Set<String> getIndexes() throws CouchbaseLiteException { return new HashSet<>(db.getIndexes()); }

    public void createIndex(String name, IndexConfiguration config) throws CouchbaseLiteException {
        db.createIndex(name, config);
    }

    public void deleteIndex(String name) throws CouchbaseLiteException { db.deleteIndex(name); }

    /**
     * Add a change listener to listen to change events occurring to any documents in the collection.
     * To remove the listener, call remove() function on the returned listener token.
     *
     * @param listener the observer
     * @return token used to cancel the listener
     * @throws IllegalStateException if the default collection doesn’t exist.
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener) {
        return db.addChangeListener(listener);
    }

    /**
     * Add a change listener to listen to change events occurring to any documents in the collection.
     * To remove the listener, call remove() function on the returned listener token.
     * This listener will be executed on the passed executor
     *
     * @param executor the executor on which to run the listener.
     * @param listener the observer
     * @return token used to cancel the listener
     * @throws IllegalStateException if the default collection doesn’t exist.
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull Executor executor, @NonNull DatabaseChangeListener listener) {
        return db.addChangeListener(executor, listener);
    }

    // ??? This probably shouldn't be in the public API.
    // It is used by BaseImmutableReplicatorConfiguration
    @NonNull
    public Database getDatabase() { return (Database) db; }
}
