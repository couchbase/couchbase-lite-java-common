package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;


public final class Collection implements Indexable, DatabaseChangeObservable {
    public static final String DEFAULT_COLLECTION_NAME = "_default";
    private final Scope scope;
    private final Database database;

    private final String defaultCollectionName;

    public Collection(@NonNull Database database) {
        this.database = database;
        defaultCollectionName = DEFAULT_COLLECTION_NAME;
        scope = new Scope(Scope.DEFAULT_SCOPE_NAME);
    }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the collection, the value returned will be null.
     */
    @Nullable
    public Document getDocument(@NonNull String id) { return database.getDocument(id); }

    /**
     * Save a document into the collection. The default concurrency control, lastWriteWins,
     * will be used when there is conflict during  save.
     * <p>
     * When saving a document that already belongs to a collection, the collection instance of
     * the document and this collection instance must be the same, otherwise, the InvalidParameter
     * error will be thrown.
     */
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException { database.save(document); }

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
        return database.save(document, concurrencyControl);
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
        return database.save(document, conflictHandler);
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
    public void delete(@NonNull Document document) throws CouchbaseLiteException { database.delete(document); }

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
        return database.delete(document, concurrencyControl);
    }

    /**
     * When purging a document, the collection instance of the document and this collection instance
     * must be the same, otherwise, the InvalidParameter error will be thrown.
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException { database.purge(document); }

    /**
     * Purge a document by id from the collection. If the document doesn't exist in the collection,
     * the NotFound error will be thrown.
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException { database.purge(id); }


    /**
     * Set an expiration date to the document of the given id. Setting a nil date will clear the expiration.
     */
    public void setDocumentExpiration(@NonNull String id, @Nullable Date expiration) throws CouchbaseLiteException {
        database.setDocumentExpiration(id, expiration);
    }

    /**
     * Get the expiration date set to the document of the given id.
     */
    @Nullable
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        return database.getDocumentExpiration(id);
    }

    /**
     * Add a change listener to listen to change events occurring to a document of the given document id.
     * To remove the listener, call remove() function on the returned listener token.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String id, @NonNull DocumentChangeListener listener) {
        return database.addDocumentChangeListener(id, listener);
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
        return database.addDocumentChangeListener(id, executor, listener);
    }

    /**
     * Return the collection name
     */
    @NonNull
    public String getName() { return defaultCollectionName; }

    /**
     * The number of documents in the collection.
     */
    public long getCount() { return database.getCount(); }

    /**
     * Get scope
     */
    @NonNull
    public Scope getScope() { return scope; }

    @Override
    @NonNull
    public List<String> indexes() throws CouchbaseLiteException { return database.getIndexes(); }

    @Override
    public void createIndex(String name, IndexConfiguration config) throws CouchbaseLiteException {
        database.createIndex(name, config);
    }

    @Override
    public void deleteIndex(String name) throws CouchbaseLiteException { database.deleteIndex(name); }

    @Override
    @NonNull
    public ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener) {
        return database.addChangeListener(listener);
    }

    @Override
    @NonNull
    public ListenerToken addChangeListener(@NonNull Executor executor, @NonNull DatabaseChangeListener listener) {
        return database.addChangeListener(executor, listener);
    }
}
