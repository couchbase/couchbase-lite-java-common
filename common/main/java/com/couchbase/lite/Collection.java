package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;


public final class Collection implements Indexable, DatabaseChangeObservable {
    public static final String DEFAULT_NAME = "_default";

    @NonNull
    static Collection getDefault(@NonNull Scope scope) { return new Collection(scope, DEFAULT_NAME); }


    @NonNull
    private final String name;
    @NonNull
    private final Scope scope;

    public Collection(@NonNull Scope scope, @NonNull String name) {
        this.scope = scope;
        this.name = name;
    }

    /**
     * Return the collection name
     */
    @NonNull
    public String getName() { return name; }

    /**
     * Get scope
     */
    @NonNull
    public Scope getScope() { return scope; }

    /**
     * The number of documents in the collection.
     */
    public long getCount() { return getDatabase().getCount(); }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the collection, the value returned will be null.
     */
    @Nullable
    public Document getDocument(@NonNull String id) { return getDatabase().getDocument(id); }

    /**
     * Save a document into the collection. The default concurrency control, lastWriteWins,
     * will be used when there is conflict during  save.
     * <p>
     * When saving a document that already belongs to a collection, the collection instance of
     * the document and this collection instance must be the same, otherwise, the InvalidParameter
     * error will be thrown.
     */
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException { getDatabase().save(document); }

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
        return getDatabase().save(document, concurrencyControl);
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
        return getDatabase().save(document, conflictHandler);
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
    public void delete(@NonNull Document document) throws CouchbaseLiteException { getDatabase().delete(document); }

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
        return getDatabase().delete(document, concurrencyControl);
    }

    /**
     * When purging a document, the collection instance of the document and this collection instance
     * must be the same, otherwise, the InvalidParameter error will be thrown.
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException { getDatabase().purge(document); }

    /**
     * Purge a document by id from the collection. If the document doesn't exist in the collection,
     * the NotFound error will be thrown.
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException { getDatabase().purge(id); }


    /**
     * Set an expiration date to the document of the given id. Setting a nil date will clear the expiration.
     */
    public void setDocumentExpiration(@NonNull String id, @Nullable Date expiration) throws CouchbaseLiteException {
        getDatabase().setDocumentExpiration(id, expiration);
    }

    /**
     * Get the expiration date set to the document of the given id.
     */
    @Nullable
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        return getDatabase().getDocumentExpiration(id);
    }

    /**
     * Add a change listener to listen to change events occurring to a document of the given document id.
     * To remove the listener, call remove() function on the returned listener token.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String id, @NonNull DocumentChangeListener listener) {
        return getDatabase().addDocumentChangeListener(id, listener);
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
        return getDatabase().addDocumentChangeListener(id, executor, listener);
    }

    @Override
    @NonNull
    public Set<String> getIndexes() throws CouchbaseLiteException { return new HashSet<>(getDatabase().getIndexes()); }

    @Override
    public void createIndex(String name, IndexConfiguration config) throws CouchbaseLiteException {
        getDatabase().createIndex(name, config);
    }

    @Override
    public void deleteIndex(String name) throws CouchbaseLiteException { getDatabase().deleteIndex(name); }

    /**
     * Add a change listener to listen to change events occurring to any documents in the collection.
     * To remove the listener, call remove() function on the returned listener token.
     *
     * @param listener the observer
     * @return token used to cancel the listener
     * @throws IllegalStateException if the default collection doesn’t exist.
     */
    @Override
    @NonNull
    public ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener) {
        return getDatabase().addChangeListener(listener);
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
    @Override
    @NonNull
    public ListenerToken addChangeListener(@NonNull Executor executor, @NonNull DatabaseChangeListener listener) {
        return getDatabase().addChangeListener(executor, listener);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final Collection that = (Collection) o;
        return name.equals(that.name) && scope.getName().equals(that.scope.getName());
    }

    @Override
    public int hashCode() { return Objects.hash(name, scope); }

    // ??? This probably shouldn't be in the public API.
    // It is used by BaseImmutableReplicatorConfiguration
    @NonNull
    public Database getDatabase() { return scope.getDatabase(); }
}
