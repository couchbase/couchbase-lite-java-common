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
import androidx.annotation.VisibleForTesting;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.BaseCollection;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.core.C4CollectionObserver;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.core.C4DocumentObserver;
import com.couchbase.lite.internal.core.C4QueryIndex;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.listener.ChangeListenerToken;
import com.couchbase.lite.internal.listener.Listenable;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A <code>Collection</code> is a container for documents similar to aa table in a relational
 * database. Collections are grouped into namespaces called `scope`s and have a
 * unique name within that scope.
 *
 * <p>Every database contains a default collection named "_default"
 * (the constant <code>Collection.DEFAULT_NAME</code>) in the default scope
 * (also named "_default", the constant <code>Scope.DEFAULT_NAME</code>)
 *
 * <p>While creating a new collection requires the name of the collection
 * and the name of its scope, there are convenience methods that take only
 * the name of the collection and create the collection in the default scope.
 *
 * <p>The naming rules for collections and scopes are as follows:
 * <ul>
 * <li> Must be between 1 and 251 characters in length.
 * <li> Can only contain the characters A-Z, a-z, 0-9, and the symbols _, -, and %.
 * <li> Cannot start with _ or %.
 * <li> Both scope and collection names are case sensitive.
 * </ul>
 *
 * <code>Collection</code> objects are only valid during the time the database that
 * contains them is open.  An attempt to use a collection that belongs to a closed
 * database will throw an <code>CouchbaseLiteError</code>.
 * An application can hold references to multiple instances of a single database.
 * Under these circumstances, it is possible that a collection will be deleted in
 * one instance before an attempt to use it in another.  Such an attempt will
 * also cause an <code>CouchbaseLiteError</code> to be thrown.
 * <p><code>Collection</code>s are <code>AutoCloseable</code>.  While garbage
 * collection will manage them correctly, developers are strongly encouraged to
 * to use them in try-with-resources blocks or to close them explicitly, after use.
 */
// A considerable amount of code depends on this class being immutable!
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public final class Collection extends BaseCollection
    implements Listenable<CollectionChange, CollectionChangeListener>, AutoCloseable {
    public static final String DEFAULT_NAME = "_default";

    // A random but absurdly large number.
    static final int MAX_CONFLICT_RESOLUTION_RETRIES = 13;

    private static final String INDEX_KEY_NAME = "name";


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

    @NonNull
    static Collection getDefaultCollection(@NonNull Database db) throws CouchbaseLiteException {
        try { return new Collection(db, db.getDefaultC4Collection()); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final C4Collection c4Collection;


    // These notifiers will be retained until they are either explicitly released,
    // or this Collection or until this Collection goes out of scope.
    // While the database does not keep a reference to its collection,
    // a replicator does.  Notifiers should be safe for the life of the replicator.
    @GuardedBy("getDbLock()")
    private CollectionChangeNotifier collectionChangeNotifier;
    // A map of doc ids to the groups of listeners listening for changes to that doc
    @GuardedBy("getDbLock()")
    private final Map<String, DocumentChangeNotifier> docChangeNotifiers = new HashMap<>();

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
     * Get scope
     */
    @NonNull
    public Scope getScope() { return new Scope(c4Collection.getScope(), db); }

    /**
     * Return the collection name
     */
    @NonNull
    public String getName() { return c4Collection.getName(); }

    @NonNull
    public String getFullName() { return c4Collection.getScope() + "." + c4Collection.getName(); }

    @NonNull
    public Database getDatabase() { return db; }

    // - Documents

    /**
     * The number of documents in the collection.
     */
    public long getCount() {
        return Preconditions.assertNotNull(
            withLock(() -> (!db.isOpenLocked()) ? 0L : c4Collection.getDocumentCount()),
            "token");
    }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the collection, the value returned will be null.
     * <p>
     *
     * @param id the document id
     * @return the Document object or null
     * @throws CouchbaseLiteException if the database is closed, the collection has been deleted, etc.
     */
    @Nullable
    public Document getDocument(@NonNull String id) throws CouchbaseLiteException {
        return withLockAndOpenDb(() -> Document.getDocumentOrNull(this, id));
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
            prepareDocument(document);
            withLockAndOpenDb(() -> {
                saveLocked(document, null, false, concurrencyControl);
                return null;
            });
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        // saveLocked will never throw a conflict exception if concurrency control is LAST_WRITE_WINS
        // we get here only if concurrency control is FAIL_ON_CONFLICT and there is a conflict
        return false;
    }

    /**
     * Save a document into the collection with a specified conflict handler. The specified conflict handler
     * will be called if there is conflict during save. If the conflict handler returns 'false', the save
     * operation will be canceled with 'false' value returned.
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
        return saveWithConflictHandler(document, conflictHandler);
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
            prepareDocument(document);
            withLockAndOpenDb(() -> {
                saveLocked(document, null, true, concurrencyControl);
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
        return withLockAndOpenDb(() -> {
            try {
                final long timestamp = c4Collection.getDocumentExpiration(id);
                return (timestamp == 0) ? null : new Date(timestamp);
            }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        });
    }

    // - Collection Change Notification

    /**
     * Add a change listener to listen to change events occurring to any documents in the collection.
     * To remove the listener, call remove() function on the returned listener token.
     *
     * @param listener the observer
     * @return token used to cancel the listener
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull CollectionChangeListener listener) {
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
     */
    @NonNull
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull CollectionChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return Preconditions.assertNotNull(
            // this call cannot, in fact, return null
            withLock(() -> addCollectionChangeListenerLocked(executor, listener)),
            "token");
    }

    // - Document Change Notification

    /**
     * Add a change listener to listen to change events occurring to a document of the given document id.
     * To remove the listener, call remove() function on the returned listener token.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String id, @NonNull DocumentChangeListener listener) {
        return addDocumentChangeListener(id, null, listener);
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
        Preconditions.assertNotNull(id, "docId");
        Preconditions.assertNotNull(listener, "listener");
        return Preconditions.assertNotNull(
            withLock(() -> addDocumentChangeListenerLocked(id, executor, listener)),
            "token");
    }

    // - Indexes

    /**
     * Get a list of the names of indices in the collection.
     *
     * @return the set of index names
     * @throws CouchbaseLiteException on failure
     */
    @NonNull
    public Set<String> getIndexes() throws CouchbaseLiteException {
        final FLValue flIndexInfo;
        synchronized (getDbLock()) {
            try { flIndexInfo = c4Collection.getIndexesInfo(); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }

        final Set<String> indexNames = new HashSet<>();

        final Object indexesInfo = flIndexInfo.asObject();
        if (!(indexesInfo instanceof List<?>)) { return indexNames; }

        for (Object idxInfo: (List<?>) indexesInfo) {
            if (!(idxInfo instanceof Map<?, ?>)) { continue; }
            final Object idxName = ((Map<?, ?>) idxInfo).get(INDEX_KEY_NAME);
            if (idxName instanceof String) { indexNames.add((String) idxName); }
        }

        return indexNames;
    }

    /**
     * Get the named index from the collection.
     *
     * @param name index name
     * @return the QueryIndex
     */
    @Nullable
    public QueryIndex getIndex(@NonNull String name) throws CouchbaseLiteException {
        // ??? Use error kC4ErrorMissingIndex instead?
        if (!getIndexes().contains(name)) { return null; }
        final C4QueryIndex idx = c4Collection.getIndex(getDbLock(), name);
        return new QueryIndex(this, name, idx);
    }

    /**
     * Add an index to the collection.
     *
     * @param name   index name
     * @param config index configuration
     * @throws CouchbaseLiteException on failure
     */
    public void createIndex(@NonNull String name, @NonNull IndexConfiguration config) throws CouchbaseLiteException {
        createIndexInternal(name, config);
    }

    /**
     * Add an index to the collection.
     *
     * @param name  index name
     * @param index index configuration
     * @throws CouchbaseLiteException on failure
     */
    public void createIndex(@NonNull String name, @NonNull Index index) throws CouchbaseLiteException {
        createIndexInternal(name, index);
    }

    /**
     * Delete the named index from the collection.
     *
     * @param name name of the index to delete
     * @throws CouchbaseLiteException on failure
     */
    public void deleteIndex(@NonNull String name) throws CouchbaseLiteException {
        try { c4Collection.deleteIndex(name); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    // - AutoCloseable

    @Override
    public void close() {
        synchronized (getDbLock()) {
            closeCollectionChangeNotifierLocked();
            for (DocumentChangeNotifier notifier: docChangeNotifiers.values()) { notifier.close(); }
            docChangeNotifiers.clear();
            c4Collection.close();
        }
    }

    // - Object Methods

    @NonNull
    @Override
    public String toString() { return c4Collection.getDb() + "." + getFullName(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Collection)) { return false; }
        final Collection other = (Collection) o;
        // don't use .equals here!  The database must be the exact same instance.
        return db == other.db
            && c4Collection.getScope().equals(other.c4Collection.getScope())
            && c4Collection.getName().equals(other.c4Collection.getName());
    }

    @Override
    public int hashCode() { return Objects.hash(c4Collection.getScope(), c4Collection.getName()); }

    //-------------------------------------------------------------------------
    // Package visibility
    //-------------------------------------------------------------------------

    @NonNull
    C4CollectionObserver createCollectionObserver(@NonNull Runnable listener)
        throws CouchbaseLiteException {
        try { return c4Collection.createCollectionObserver(listener); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(
                e,
                "Invalid collection: it has either been deleted or its database closed");
        }
    }

    @NonNull
    C4DocumentObserver createDocumentObserver(@NonNull String docID, @NonNull Runnable listener)
        throws CouchbaseLiteException {
        try { return c4Collection.createDocumentObserver(docID, listener); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(
                e,
                "Invalid collection: it has either been deleted or its database closed");
        }
    }

    boolean isValid() { return c4Collection.isValid(); }

    boolean isDefault() {
        return Scope.DEFAULT_NAME.equals(getScope().getName()) && Collection.DEFAULT_NAME.equals(getName());
    }

    @NonNull
    Object getDbLock() { return db.getDbLock(); }

    @NonNull
    C4Collection getOpenC4Collection() throws CouchbaseLiteException {
        synchronized (getDbLock()) { return getOpenC4CollectionLocked(); }
    }

    @GuardedBy("getDbLock()")
    boolean isOpenLocked() { return db.isOpenLocked(); }

    // - Documents:

    @NonNull
    C4Document createC4Document(@NonNull String docId, @Nullable FLSliceResult body, int flags)
        throws CouchbaseLiteException {
        try {
            synchronized (getDbLock()) { return c4Collection.createDocument(docId, body, flags); }
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Could not create document: " + docId);
        }
    }

    @Nullable
    C4Document getC4Document(@NonNull String docId) throws CouchbaseLiteException {
        try {
            synchronized (getDbLock()) { return c4Collection.getDocument(docId); }
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed retrieving document: " + docId);
        }
    }

    @Nullable
    C4Document getC4DocumentWithRevs(@NonNull String docId) throws CouchbaseLiteException {
        try {
            synchronized (getDbLock()) { return c4Collection.getDocumentWithRevs(docId); }
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed retrieving document: " + docId);
        }
    }

    boolean saveWithConflictHandler(@NonNull MutableDocument document, @NonNull ConflictHandler handler)
        throws CouchbaseLiteException {
        Document oldDoc = null;
        int n = 0;
        while (true) {
            if (n++ > MAX_CONFLICT_RESOLUTION_RETRIES) {
                throw new CouchbaseLiteException(
                    "Too many attempts to resolve a conflicted document: " + n,
                    CBLError.Domain.CBLITE,
                    CBLError.Code.UNEXPECTED_ERROR);
            }

            synchronized (getDbLock()) {
                assertOpen();
                try {
                    saveLocked(document, oldDoc, false, ConcurrencyControl.FAIL_ON_CONFLICT);
                    return true;
                }
                catch (CouchbaseLiteException e) {
                    if (!CouchbaseLiteException.isConflict(e)) { throw e; }
                }

                // Conflict
                oldDoc = Document.getDocumentWithDeleted(this, document.getId());
            }

            try {
                if (!handler.handle(document, (oldDoc.isDeleted()) ? null : oldDoc)) { return false; }
            }
            catch (Exception e) {
                throw new CouchbaseLiteException(
                    "Conflict handler threw an exception",
                    e,
                    CBLError.Domain.CBLITE,
                    CBLError.Code.CONFLICT
                );
            }
        }
    }

    // The main save method.
    // Contract:
    //     Called holding the db lock,
    //     Database must be open
    //     document must have a valid collection (see: prepareDocument)
    @GuardedBy("getDbLock()")
    void saveLocked(
        @NonNull Document document,
        @Nullable Document baseDoc,
        boolean deleting,
        @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");
        Preconditions.assertNotNull(concurrencyControl, "concurrencyControl");

        if (deleting && (!document.exists())) {
            throw new CouchbaseLiteException(
                "DeleteDocFailedNotSaved",
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_FOUND);
        }

        boolean commit = false;
        db.beginTransaction();
        try {
            try {
                saveInTransaction(document, (baseDoc == null) ? null : baseDoc.getC4doc(), deleting);
                commit = true;
                return;
            }
            catch (CouchbaseLiteException e) {
                if (!CouchbaseLiteException.isConflict(e)) { throw e; }
            }

            // Conflict
            switch (concurrencyControl) {
                case FAIL_ON_CONFLICT:
                    throw new CouchbaseLiteException("Conflict", CBLError.Domain.CBLITE, CBLError.Code.CONFLICT);

                case LAST_WRITE_WINS:
                    commit = saveConflicted(document, deleting);
                    break;

                default:
                    throw new CouchbaseLiteException("Unrecognized concurrency control: " + concurrencyControl);
            }
        }
        finally {
            db.endTransaction(commit);
        }
    }

    @GuardedBy("getDbLock()")
    @NonNull
    ListenerToken addCollectionChangeListenerLocked(
        @Nullable Executor executor,
        @NonNull CollectionChangeListener listener) {
        if (collectionChangeNotifier == null) {
            collectionChangeNotifier = new CollectionChangeNotifier(this);
            if (isOpenLocked()) {
                try { collectionChangeNotifier.start(this::scheduleImmediateOnPostExecutor); }
                // ??? Revisit this: there is no programatic way for client code
                // to know that the listener has failed.
                catch (CouchbaseLiteException e) {
                    Log.d(LogDomain.LISTENER, "Listener failed", e);
                    return ChangeListenerToken.DUMMY;
                }
            }
        }
        return collectionChangeNotifier.addChangeListener(executor, listener, this::removeCollectionChangeListener);
    }

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    void removeCollectionChangeListener(@NonNull ListenerToken token) {
        if (!(token instanceof ChangeListenerToken)) {
            Log.d(LogDomain.DATABASE, "Attempt to remove unrecognized db change listener: " + token);
            return;
        }
        synchronized (getDbLock()) {
            if (collectionChangeNotifier != null) {
                if (collectionChangeNotifier.removeChangeListener(token)) { closeCollectionChangeNotifierLocked(); }
            }
        }
    }

    @GuardedBy("getDbLock()")
    @NonNull
    ListenerToken addDocumentChangeListenerLocked(
        @NonNull String docID,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        DocumentChangeNotifier docNotifier = docChangeNotifiers.get(docID);
        if (docNotifier == null) {
            docNotifier = new DocumentChangeNotifier(this, docID);
            docChangeNotifiers.put(docID, docNotifier);
            if (isOpenLocked()) {
                try { docNotifier.start(this::scheduleImmediateOnPostExecutor); }
                // ??? Revisit this: there is no programatic way for client code
                // to know that the listener has failed.
                catch (CouchbaseLiteException e) {
                    Log.d(LogDomain.LISTENER, "Listener failed", e);
                    return ChangeListenerToken.DUMMY;
                }
            }
        }
        final ChangeListenerToken<?> token
            = docNotifier.addChangeListener(executor, listener, this::removeDocumentChangeListener);
        token.setKey(docID);
        return token;
    }

    void removeDocumentChangeListener(@NonNull ListenerToken token) {
        if (!(token instanceof ChangeListenerToken)) {
            Log.d(LogDomain.DATABASE, "Attempt to remove unrecognized doc change listener: " + token);
            return;
        }

        final String docId = ((ChangeListenerToken<?>) token).getKey();
        synchronized (getDbLock()) {
            final DocumentChangeNotifier notifier = docChangeNotifiers.get(docId);
            if (notifier == null) { return; }

            if (notifier.removeChangeListener(token)) {
                notifier.close();
                docChangeNotifiers.remove(docId);
            }
        }
    }

    // - Indices:

    void createIndexInternal(@NonNull String name, @NonNull AbstractIndex index)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(name, "name");
        Preconditions.assertNotNull(index, "index");

        synchronized (getDbLock()) {
            try { index.createIndex(name, c4Collection); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    @VisibleForTesting
    int getCollectionListenerCount() {
        synchronized (getDbLock()) {
            return (collectionChangeNotifier == null) ? 0 : collectionChangeNotifier.getListenerCount();
        }
    }

    //-------------------------------------------------------------------------
    // Private (class only)
    //-------------------------------------------------------------------------

    @Nullable
    private <T> T withLock(@NonNull Fn.Provider<T> task) {
        synchronized (getDbLock()) { return task.get(); }
    }

    @GuardedBy("dbLock")
    @NonNull
    private C4Collection getOpenC4CollectionLocked() throws CouchbaseLiteException {
        assertOpenChecked();
        return Preconditions.assertNotNull(c4Collection, "c4collection");
    }

    @GuardedBy("dbLock")
    private void assertOpenChecked() throws CouchbaseLiteException {
        if (!db.isOpenLocked()) {
            throw new CouchbaseLiteException(
                Log.lookupStandardMessage("DBClosedOrCollectionDeleted"),
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_OPEN);
        }
    }

    @GuardedBy("dbLock")
    private void assertOpen() throws CouchbaseLiteException {
        if (!db.isOpenLocked()) {
            throw new CouchbaseLiteException(
                Log.lookupStandardMessage("DBClosedOrCollectionDeleted"),
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_OPEN);
        }
    }

    @Nullable
    private <T> T withLockAndOpenDb(@NonNull Fn.ProviderThrows<T, CouchbaseLiteException> task)
        throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            assertOpen();
            return task.get();
        }
    }

    // - Documents:

    @GuardedBy("getDbLock()")
    private void prepareDocument(@NonNull Document document) throws CouchbaseLiteException {
        final Collection docCollection = document.getCollection();
        if (docCollection == null) { document.setCollection(this); }
        else if (!this.equals(docCollection)) {
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
    private boolean saveConflicted(@NonNull Document document, boolean deleting) throws CouchbaseLiteException {
        final C4Document curDoc;

        curDoc = getC4Document(document.getId());
        if (curDoc == null) { return false; }

        // here if deleting and the curDoc has already been deleted
        if (deleting && curDoc.isDocDeleted()) {
            document.replaceC4Document(curDoc);
            return false;
        }

        // Save changes on the current branch:
        saveInTransaction(document, curDoc, deleting);

        return true;
    }

    // Low-level save method
    @GuardedBy("getDbLock()")
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private void saveInTransaction(@NonNull Document document, @Nullable C4Document base, boolean deleting)
        throws CouchbaseLiteException {
        FLSliceResult body = null;
        try {
            int revFlags = 0;
            if (deleting) { revFlags = C4Constants.RevisionFlags.DELETED; }
            else if (!document.isEmpty()) {
                // Encode properties to Fleece data:
                body = document.encode();
                if (C4Document.dictContainsBlobs(body, db.getSharedKeys())) {
                    revFlags |= C4Constants.RevisionFlags.HAS_ATTACHMENTS;
                }
            }

            // Save to database:
            C4Document c4Doc = (base != null) ? base : document.getC4doc();

            if (c4Doc != null) { c4Doc = c4Doc.update(body, revFlags); }
            else {
                final Collection collection = document.getCollection();
                if (collection == null) {
                    throw new CouchbaseLiteError("Attempt to save document in null collection");
                }
                c4Doc = collection.createC4Document(document.getId(), body, revFlags);
            }

            document.replaceC4Document(c4Doc);
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
    }

    private void closeCollectionChangeNotifierLocked() {
        final CollectionChangeNotifier notifier = collectionChangeNotifier;
        collectionChangeNotifier = null;
        if (notifier != null) { notifier.close(); }
    }

    private void scheduleImmediateOnPostExecutor(@NonNull Runnable task) {
        CouchbaseLiteInternal.getExecutionService().postDelayedOnExecutor(0L, postExecutor, task);
    }

    @NonNull
    @Override
    protected Document createFilterDocument(@NonNull String docId, @NonNull String revId, @NonNull FLDict body) {
        return new Document(this, docId, revId, body);
    }
}
