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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.BaseCollection;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 *
 */
public final class Collection extends BaseCollection {
    public static final String DEFAULT_NAME = "_default";


    //-------------------------------------------------------------------------
    // Factory methods
    //-------------------------------------------------------------------------

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


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final C4Collection c4Collection;

    @GuardedBy("getDbLock()")
    private CollectionChangeNotifier collectionChangeNotifier;

    // Executor for changes.
    private final ExecutionService.CloseableExecutor postExecutor;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    // Collections must be immutable
    Collection(@NonNull Database db, @NonNull C4Collection c4Collection) {
        super(db);
        this.c4Collection = c4Collection;
        this.postExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
    }

    //-------------------------------------------------------------------------
    // Public API
    //-------------------------------------------------------------------------

    // - Properties

    /**
     * Return the collection name
     */
    @NonNull
    public String getName() { return c4Collection.getName(); }

    /**
     * Get scope
     */
    @NonNull
    public Scope getScope() { return new Scope(c4Collection.getScope(), db); }

    /**
     * The number of documents in the collection.
     */

    // - Documents
    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    long getCount() throws CouchbaseLiteException {
        return withLock(() -> {
            // there is no way this can return null...
            return (!db.isOpen()) ? 0L : c4Collection.getDocumentCount();
        });
    }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the collection, the value returned will be null.
     */
    @Nullable
    public Document getDocument(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotEmpty(id, "id");
        return withLockAndOpenDb(() -> {
            try { return Document.getDocument(this, id, false); }
            catch (CouchbaseLiteException e) { Log.i(LogDomain.DATABASE, "Failed retrieving document: %s", id); }
            return null;
        });
    }

    /**
     * Save a document into the collection. The default concurrency control, lastWriteWins,
     * will be used when there is conflict during  save.
     * <p>
     * When saving a document that already belongs to a collection, the collection instance of
     * the document and this collection instance must be the same, otherwise, the InvalidParameter
     * error will be thrown.
     */
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException {
        save(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

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
        try {
            withLockAndOpenDb(() -> {
                prepareDocument(document);
                db.saveInternal(document, null, false, concurrencyControl);
                return null;
            });
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        return false;
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
        Preconditions.assertNotNull(document, "document");
        Preconditions.assertNotNull(conflictHandler, "conflictHandler");
        prepareDocument(document);
        db.saveWithConflictHandler(document, conflictHandler);
        return true;
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
    public void delete(@NonNull Document document) throws CouchbaseLiteException {
        delete(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

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
        try {
            withLockAndOpenDb(() -> {
                prepareDocument(document);
                db.saveInternal(document, null, true, concurrencyControl);
                return null;
            });
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        return false;
    }

    /**
     * When purging a document, the collection instance of the document and this collection instance
     * must be the same, otherwise, the InvalidParameter error will be thrown.
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");

        if (document.isNewDocument()) {
            throw new CouchbaseLiteException("DocumentNotFound", CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
        }

        withLockAndOpenDb(() -> {
            prepareDocument(document);

            try { purgeLocked(document.getId()); }
            catch (CouchbaseLiteException e) {
                // Ignore not found (already deleted)
                if (e.getCode() != CBLError.Code.NOT_FOUND) { throw e; }
            }

            document.replaceC4Document(null); // Reset c4doc:
            return null;
        });
    }

    /**
     * Purge a document by id from the collection. If the document doesn't exist in the collection,
     * the NotFound error will be thrown.
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        withLockAndOpenDb(() -> {
            purgeLocked(id);
            return null;
        });
    }

    // - Documents Expiry

    /**
     * Set an expiration date to the document of the given id. Setting a nil date will clear the expiration.
     */
    public void setDocumentExpiration(@NonNull String id, @Nullable Date expiration) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        withLockAndOpenDb(() -> {
            try { c4Collection.setDocumentExpiration(id, (expiration == null) ? 0 : expiration.getTime()); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
            return null;
        });
    }

    /**
     * Get the expiration date set to the document of the given id.
     */
    @Nullable
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        withLockAndOpenDb(() -> {
            try {
                final long timestamp = c4Collection.getDocumentExpiration(id);
                return (timestamp == 0) ? null : new Date(timestamp);
            }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        });
        throw new IllegalStateException("Unreachable");
    }

    // - Collection Change Notification

    /**
     * Add a change listener to listen to change events occurring to any documents in the collection.
     * To remove the listener, call remove() function on the returned listener token.
     *
     * @param listener the observer
     * @return token used to cancel the listener
     * @throws IllegalStateException if the default collection doesn’t exist.
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull CollectionChangeListener listener) throws CouchbaseLiteException {
        return addChangeListener(null, listener);
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
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @NonNull
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull CollectionChangeListener listener)
        throws CouchbaseLiteException {
        return withLock(() ->
            // this call cannot, in fact, return null
            addChangeListenerLocked(executor, Preconditions.assertNotNull(listener, "listener")));
    }

    // - Document Change Notification

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

    // - Indexes

    @NonNull
    public Set<String> getIndexes() throws CouchbaseLiteException { return new HashSet<>(db.getIndexes()); }

    public void createIndex(String name, IndexConfiguration config) throws CouchbaseLiteException {
        db.createIndex(name, config);
    }

    public void deleteIndex(String name) throws CouchbaseLiteException { db.deleteIndex(name); }

    // - Object Methods

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

    //-------------------------------------------------------------------------
    // Package Protected
    //-------------------------------------------------------------------------

    @NonNull
    Database getDatabase() { return db; }

    boolean isOpen() { return db.isOpen(); }

    @NonNull
    C4Document createC4Document(@NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        synchronized (getDbLock()) { return c4Collection.createDocument(docID, body, flags); }
    }

    @NonNull
    C4Document getC4Document(@NonNull String id) throws LiteCoreException {
        synchronized (getDbLock()) { return c4Collection.getDocument(id); }
    }

    @NonNull
    Object getDbLock() { return db.getDbLock(); }

    @GuardedBy("dbLock")
    private void assertOpen() throws CouchbaseLiteException {
        if (!db.isOpen()) { throw new CouchbaseLiteException(Log.lookupStandardMessage("DBClosed")); }
    }

    @Nullable
    private <T> T withLock(@NonNull Fn.Provider<T> task) throws CouchbaseLiteException {
        synchronized (getDbLock()) { return task.get(); }
    }

    @Nullable
    private <T> T withLockAndOpenDb(@NonNull Fn.ProviderThrows<T, CouchbaseLiteException> task)
        throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            assertOpen();
            return task.get();
        }
    }

    @GuardedBy("getDbLock()")
    private void prepareDocument(@NonNull Document document) throws CouchbaseLiteException {
        final Collection docCollection = document.getCollection();
        if (docCollection == null) { document.setCollection(this); }
        else if (docCollection != this) {
            throw new CouchbaseLiteException(
                "DocumentAnotherDatabase",
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER);
        }
    }

    @GuardedBy("getDbLock()")
    private void purgeLocked(@NonNull String id) throws CouchbaseLiteException {
        try { c4Collection.purgeDocument(id); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e, "Purge failed"); }
    }


    @GuardedBy("getDbLock()")
    @NonNull
    private ListenerToken addChangeListenerLocked(
        @Nullable Executor executor,
        @NonNull CollectionChangeListener listener) {
        if (collectionChangeNotifier == null) {
            collectionChangeNotifier = new CollectionChangeNotifier(this);
            if (isOpen()) {
                final ExecutionService exSvc = CouchbaseLiteInternal.getExecutionService();
                collectionChangeNotifier.start((onChange) ->
                    c4Collection.createCollectionObserver(() ->
                        exSvc.postDelayedOnExecutor(0L, postExecutor, onChange)));
            }
        }
        return collectionChangeNotifier.addChangeListener(executor, listener);
    }

    @GuardedBy("getDbLock()")
    void removeObserver() { collectionChangeNotifier = null; }
}
