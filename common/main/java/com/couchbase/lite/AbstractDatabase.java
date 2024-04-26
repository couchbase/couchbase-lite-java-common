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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ImmutableDatabaseConfiguration;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.exec.ClientTask;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.listener.ChangeListenerToken;
import com.couchbase.lite.internal.listener.Listenable;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.replicator.ConflictResolutionException;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Preconditions;


// It seems obvious that the Database should cache collections.
// Unfortunately collections can be created and deleted by other processes.
// An Architect-level decision asserted that an explicit synchronization call
// would embarrass the whole API.  As a result all collection interactions
// have to go through LiteCore.
// Actually, while deferring to LiteCore is necessary, it is not sufficient.
// In order to guarantee that a collection is valid while it is in use, client
// code must hold a database lock from the time it acquires a collection
// reference (or calls its isValid method) until it is done with it.

/**
 * AbstractDatabase is a base class of A Couchbase Lite Database.
 */
@SuppressWarnings({
    "PMD.ExcessivePublicCount",
    "PMD.CyclomaticComplexity",
    "PMD.TooManyMethods",
    "PMD.CouplingBetweenObjects"})
abstract class AbstractDatabase extends BaseDatabase
    implements Listenable<DatabaseChange, DatabaseChangeListener>, AutoCloseable {
    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final String ERROR_RESOLVER_FAILED = "Conflict resolution failed for document '%s': %s";
    private static final String WARN_WRONG_ID
        = "Conflict resolution for a document produced a new document with ID '%s', "
        + "which does not match the IDs of the conflicting document (%s)";
    private static final String WARN_WRONG_COLLECTION
        = "Conflict resolution for document '%s' produced a new document belonging to collection '%s', "
        + "not the collection into which it would be stored (%s)";

    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    private static final int DB_CLOSE_WAIT_SECS = 6; // > Core replicator timeout
    private static final int DB_CLOSE_MAX_RETRIES = 5; // random choice: wait for 5 replicators
    private static final int EXECUTOR_CLOSE_MAX_WAIT_SECS = 5;

    static class ActiveProcess<T> {
        @NonNull
        private final T process;

        ActiveProcess(@NonNull T process) { this.process = process; }

        public boolean isActive() { return true; }

        public void stop() { }

        @NonNull
        @Override
        public String toString() { return process.toString(); }

        @Override
        public int hashCode() { return process.hashCode(); }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) { return true; }
            if (!(o instanceof ActiveProcess)) { return false; }
            final ActiveProcess<?> other = (ActiveProcess<?>) o;
            return process.equals(other.process);
        }
    }

    // ---------------------------------------------
    // API - public static fields
    // ---------------------------------------------

    /**
     * Gets the logging controller for the Couchbase Lite library to configure the
     * logging settings and add custom logging.
     * <p>
     */
    // Public API.  Do not fix the name.
    @SuppressWarnings({"ConstantName", "PMD.FieldNamingConventions"})
    @NonNull
    public static final com.couchbase.lite.Log log = new com.couchbase.lite.Log();

    // ---------------------------------------------
    // API - public static methods
    // ---------------------------------------------

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name      the database's name
     * @param directory the directory containing the database: the database's parent directory.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(@NonNull String name, @Nullable File directory) throws CouchbaseLiteException {
        Preconditions.assertNotNull(name, "name");

        if (directory == null) { directory = CouchbaseLiteInternal.getDefaultDbDir(); }

        if (!exists(name, directory)) {
            throw new CouchbaseLiteException(
                "Database not found for delete",
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_FOUND);
        }

        Log.d(DOMAIN, "Delete database %s in %s", name, directory);
        try { C4Database.deleteNamedDb(directory.getCanonicalPath(), name); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        catch (IOException e) { throw new CouchbaseLiteException("No canonical path for " + directory, e); }
    }

    /**
     * Checks whether a database of the given name exists in the given directory or not.
     *
     * @param name      the database's name
     * @param directory the path where the database is located.
     * @return true if exists, false otherwise.
     */
    public static boolean exists(@NonNull String name, @Nullable File directory) {
        Preconditions.assertNotNull(name, "name");

        if (directory == null) { directory = CouchbaseLiteInternal.getDefaultDbDir(); }

        return C4Database.getDatabaseFile(directory, name).exists();
    }

    protected static void copy(
        @NonNull File path,
        @NonNull String name,
        @NonNull String dbDir,
        int algorithm,
        byte[] encryptionKey)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(path, "path");
        Preconditions.assertNotNull(name, "name");

        CouchbaseLiteException err;
        try {
            C4Database.copyDb(path.getCanonicalPath(), dbDir, name, algorithm, encryptionKey);
            return;
        }
        catch (LiteCoreException e) { err = CouchbaseLiteException.convertException(e); }
        catch (IOException e) { err = new CouchbaseLiteException("Failed creating canonical path for " + path, e); }

        FileUtils.eraseFileOrDir(C4Database.getDatabaseFile(new File(dbDir), name));

        throw err;
    }

    @Nullable
    static Database getDbForCollections(@Nullable Set<Collection> collections) {
        if ((collections == null) || collections.isEmpty()) { return null; }

        final Database db = collections.iterator().next().getDatabase();
        db.verifyCollections(collections);

        return db;
    }

    //---------------------------------------------
    // Member variables
    //---------------------------------------------

    @NonNull
    protected final ImmutableDatabaseConfiguration config;

    @NonNull
    private final String name;

    // Executor for purge and posting Database/Document changes.
    private final ExecutionService.CloseableExecutor postExecutor;
    // Executor for LiveQuery.
    private final ExecutionService.CloseableExecutor queryExecutor;

    private final FLSharedKeys sharedKeys;

    @GuardedBy("activeProcesses")
    private final Set<ActiveProcess<?>> activeProcesses;

    @GuardedBy("dbLock")
    private Collection defaultCollection;
    @GuardedBy("dbLock")
    private boolean noDefaultCollection;

    private volatile CountDownLatch closeLatch;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    protected AbstractDatabase(@NonNull String name) throws CouchbaseLiteException {
        this(name, new ImmutableDatabaseConfiguration(null));
    }

    protected AbstractDatabase(@NonNull String name, @NonNull DatabaseConfiguration config)
        throws CouchbaseLiteException {
        this(name, new ImmutableDatabaseConfiguration(config));
    }

    protected AbstractDatabase(@NonNull String name, @NonNull ImmutableDatabaseConfiguration config)
        throws CouchbaseLiteException {
        Preconditions.assertNotEmpty(name, "db name");
        Preconditions.assertNotNull(config, "config");

        CouchbaseLiteInternal.requireInit("Cannot create database");

        // Name:
        this.name = name;

        // Copy configuration
        this.config = config;

        this.postExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
        this.queryExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

        this.activeProcesses = new HashSet<>();

        fixHydrogenBug(config, name);

        // Can't open the DB until the file system is set up.
        final C4Database c4db = openC4Db();
        setC4DatabaseLocked(c4db);

        // Initialize a shared keys:
        this.sharedKeys = c4db.getFLSharedKeys();

        // warn if logging has not been turned on
        Log.warn();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the database name
     *
     * @return the database's name
     */
    @NonNull
    public String getName() { return name; }

    /**
     * The database's absolute path
     *
     * @return the database's path or null if the database is closed.
     */
    @Nullable
    public String getPath() {
        synchronized (getDbLock()) { return (!isOpenLocked()) ? null : getDbPath(); }
    }

    public boolean performMaintenance(MaintenanceType type) throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            try { return getOpenC4DbLocked().performMaintenance(type); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    /**
     * Closes a database.
     * Closing a database will stop all replicators, live queries and all listeners attached to it.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        Log.d(DOMAIN, "Closing %s at path %s", this, getDbPath());
        shutdown(false, C4Database::closeDb);
    }

    /**
     * Deletes a database.
     * Deleting a database will stop all replicators, live queries and all listeners attached to it.
     * Although attempting to close a closed database is not an error, attempting to delete a closed database is.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        Log.d(DOMAIN, "Deleting %s at path %s", this, getDbPath());
        shutdown(true, C4Database::deleteDb);
    }

    // - Scopes:

    /**
     * Get scope names that have at least one collection.
     * Note: the default scope is exceptional as it will always be listed even though there are no collections
     * under it.
     */
    @NonNull
    public final Set<Scope> getScopes() throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            final C4Database c4db = getC4DbOrThrowLocked();

            final Set<String> scopeNames;
            try { scopeNames = c4db.getScopeNames(); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }

            final Set<Scope> scopes = new HashSet<>(scopeNames.size());
            for (String scopeName: scopeNames) { scopes.add(new Scope(scopeName, getDatabase())); }

            return scopes;
        }
    }

    /**
     * Get a scope object by name. As the scope cannot exist by itself without having a collection,
     * the hull value will be returned if there are no collections under the given scope’s name.
     * Note: The default scope is exceptional, and it will always be returned.
     */
    @Nullable
    public final Scope getScope(@NonNull String name) throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            return (!getC4DbOrThrowLocked().hasScope(name)) ? null : new Scope(name, getDatabase());
        }
    }


    /**
     * Get the default scope.
     */
    @NonNull
    public final Scope getDefaultScope() throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            assertOpenChecked();
            return new Scope(getDatabase());
        }
    }

    // - Collections:

    /**
     * Create a named collection in the default scope.
     * If the collection already exists, the existing collection will be returned.
     *
     * @param name the scope in which to create the collection
     * @return the named collection in the default scope
     * @throws CouchbaseLiteException on failure
     */
    @NonNull
    public final Collection createCollection(@NonNull String name) throws CouchbaseLiteException {
        return createCollection(name, Scope.DEFAULT_NAME);
    }

    /**
     * Create a named collection in the specified scope.
     * If the collection already exists, the existing collection will be returned.
     *
     * @param collectionName the name of the new collection
     * @param scopeName      the scope in which to create the collection
     * @return the named collection in the default scope
     * @throws CouchbaseLiteException on failure
     */
    @NonNull
    public final Collection createCollection(@NonNull String collectionName, @Nullable String scopeName)
        throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }

        synchronized (getDbLock()) {
            assertOpenChecked();
            return Collection.createCollection(getDatabase(), scopeName, collectionName);
        }
    }

    /**
     * Get all collections in the default scope.
     */
    @NonNull
    public final Set<Collection> getCollections() throws CouchbaseLiteException {
        return getCollections(Scope.DEFAULT_NAME);
    }

    /**
     * Get all collections in the named scope.
     *
     * @param scopeName the scope name
     * @return the collections in the named scope
     */
    @NonNull
    public final Set<Collection> getCollections(@Nullable String scopeName) throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }

        final Set<String> collectionNames;
        synchronized (getDbLock()) {
            final C4Database c4db = getC4DbOrThrowLocked();
            try { collectionNames = c4db.getCollectionNames(scopeName); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }

        final Set<Collection> collections = new HashSet<>();
        for (String collectionName: collectionNames) { collections.add(getCollection(collectionName, scopeName)); }

        return collections;
    }

    /**
     * Get a collection in the default scope by name.
     * If the collection doesn't exist, the function will return null.
     *
     * @param name the collection to find
     * @return the named collection or null
     */
    @Nullable
    public final Collection getCollection(@NonNull String name) throws CouchbaseLiteException {
        return getCollection(name, Scope.DEFAULT_NAME);
    }

    /**
     * Get a collection in the specified scope by name.
     * If the collection doesn't exist, the function will return null.
     *
     * @param collectionName the collection to find
     * @param scopeName      the scope in which to create the collection
     * @return the named collection or null
     */
    @Nullable
    public final Collection getCollection(@NonNull String collectionName, @Nullable String scopeName)
        throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }

        synchronized (getDbLock()) {
            assertOpenChecked();
            return Collection.getCollection(getDatabase(), scopeName, collectionName);
        }
    }

    /**
     * Get the default collection.
     *
     * @return the default collection.
     */
    @NonNull
    public final Collection getDefaultCollection() throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            assertOpenChecked();
            return getDefaultCollectionLocked();
        }
    }

    /**
     * Delete a collection by name in the default scope. If the collection doesn't exist, the operation
     * will be no-ops. Note: the default collection cannot be deleted.
     *
     * @param name the collection to be deleted
     * @throws CouchbaseLiteException on failure
     */
    public final void deleteCollection(@NonNull String name) throws CouchbaseLiteException {
        deleteCollection(name, Scope.DEFAULT_NAME);
    }

    /**
     * Delete a collection by name in the specified scope. If the collection doesn't exist, the operation
     * will be no-ops. Note: the default collection cannot be deleted.
     *
     * @param collectionName the collection to be deleted
     * @param scopeName      the scope from which to delete the collection
     * @throws CouchbaseLiteException on failure
     */
    public final void deleteCollection(@NonNull String collectionName, @Nullable String scopeName)
        throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }
        synchronized (getDbLock()) {
            try { getC4DbOrThrowLocked().deleteCollection(scopeName, collectionName); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    // - Transactions:

    /**
     * Runs a group of database operations in a batch. Use this when performing bulk write operations
     * like multiple inserts/updates; it saves the overhead of multiple database commits, greatly
     * improving performance.
     *
     * @param work a unit of work that may terminate abruptly (with an exception)
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public <T extends Exception> void inBatch(@NonNull UnitOfWork<T> work) throws CouchbaseLiteException, T {
        Preconditions.assertNotNull(work, "work");

        boolean commit = false;
        synchronized (getDbLock()) {
            final C4Database db = getOpenC4DbLocked();

            try {
                db.beginTransaction();
                try {
                    work.run();
                    commit = true;
                }
                finally {
                    db.endTransaction(commit);
                }
            }
            catch (LiteCoreException e) {
                throw CouchbaseLiteException.convertException(e);
            }
        }
    }

    // - Queries:

    /**
     * Create a SQL++ query.
     *
     * @param query a valid SQL++ query
     * @return the Query object
     */
    @NonNull
    public Query createQuery(@NonNull String query) {
        synchronized (getDbLock()) {
            assertOpenUnchecked();
            return new N1qlQuery(this, query);
        }
    }

    // - Blobs:

    /**
     * (UNCOMMITTED) Use this API if you are developing Javascript language bindings.
     * If you are developing a native app, you must use the {@link Blob} API.
     *
     * @param blob a blob
     */
    @Internal("This method is not part of the public API")
    public void saveBlob(@NonNull Blob blob) {
        synchronized (getDbLock()) { assertOpenUnchecked(); }
        blob.installInDatabase((Database) this);
    }

    /**
     * (UNCOMMITTED) Use this API if you are developing Javascript language bindings.
     * If you are developing a native app, you must use the {@link Blob} API.
     *
     * @param props blob properties
     */
    @Internal("This method is not part of the public API")
    @Nullable
    public Blob getBlob(@NonNull Map<String, Object> props) {
        synchronized (getDbLock()) { assertOpenUnchecked(); }

        if (!Blob.isBlob(props)) { throw new IllegalArgumentException("getBlob arg does not specify a blob"); }

        final Blob blob = new Blob(this, props);

        return (blob.updateSize() < 0) ? null : blob;
    }

    // - Object methods:

    @NonNull
    @Override
    public String toString() { return "Database{@" + ClassUtils.objId(this) + ": '" + name + "'}"; }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof AbstractDatabase)) { return false; }
        final AbstractDatabase other = (AbstractDatabase) o;
        return Objects.equals(getPath(), other.getPath()) && name.equals(other.name);
    }

    //---------------------------------------------
    // Deprecated API methods
    //---------------------------------------------

    /**
     * The number of documents in the default collection.
     *
     * @return the number of documents in the database, 0 if database is closed.
     * @deprecated Use getDefaultCollection().getCount()
     */
    @Deprecated
    public long getCount() {
        final Collection defaultCollection;
        try {
            synchronized (getDbLock()) { defaultCollection = getDefaultCollectionLocked(); }
        }
        catch (CouchbaseLiteException e) {
            throw new CouchbaseLiteError("Failed getting default collection", e);
        }

        return (defaultCollection == null) ? 0 : defaultCollection.getCount();
    }

    /**
     * Returns a copy of the database configuration.
     *
     * @return the READONLY copied config object
     */
    @NonNull
    public DatabaseConfiguration getConfig() { return new DatabaseConfiguration(config); }

    /**
     * Gets an existing Document with the given ID from the default collection.
     * If the document with the given ID doesn't exist in the default collection,
     * the method will return null.  If the default collection does not exist or if
     * the database is closed, the method will throw an CouchbaseLiteError
     *
     * @param id the document ID
     * @return the Document object or null
     * @throws CouchbaseLiteError when the database is closed or the default collection has been deleted
     * @deprecated Use getDefaultCollection().getDocument()
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    @Deprecated
    @Nullable
    public Document getDocument(@NonNull String id) {
        synchronized (getDbLock()) {
            try { return getDefaultCollectionOrThrow().getDocument(id); }
            catch (CouchbaseLiteException e) { Log.i(LogDomain.DATABASE, "Failed retrieving document: %s", e, id); }
        }
        return null;
    }

    /**
     * Saves a document to the default collection. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this method is the same as calling save(MutableDocument, ConcurrencyControl.LAST_WRITE_WINS)
     *
     * @param document The document.
     * @throws CouchbaseLiteException on error
     * @deprecated Use getDefaultCollection().save
     */
    @Deprecated
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException {
        save(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

    /**
     * Saves a document to the default collection. When used with LAST_WRITE_WINS
     * concurrency control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, save will fail when there
     * is a conflict and the method will return false
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @return true if successful. false if the FAIL_ON_CONFLICT concurrency
     * @throws CouchbaseLiteException on error
     * @deprecated Use getDefaultCollection().save()
     */
    @Deprecated
    public boolean save(@NonNull MutableDocument document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        return getDefaultCollectionOrThrow().save(document, concurrencyControl);
    }

    /**
     * Saves a document to the default collection. Conflicts will be resolved by the passed ConflictHandler
     *
     * @param document        The document.
     * @param conflictHandler A conflict handler.
     * @return true if successful. false if the FAIL_ON_CONFLICT concurrency
     * @throws CouchbaseLiteException on error
     * @deprecated Use getDefaultCollection().save
     */
    @Deprecated
    public boolean save(@NonNull MutableDocument document, @NonNull ConflictHandler conflictHandler)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");
        Preconditions.assertNotNull(conflictHandler, "conflictHandler");
        try { return getDefaultCollectionOrThrow().save(document, conflictHandler); }
        catch (CouchbaseLiteException e) {
            if (!(CBLError.Domain.CBLITE.equals(e.getDomain()) && (CBLError.Code.NOT_OPEN == e.getCode()))) { throw e; }
            else { throw new CouchbaseLiteError(Log.lookupStandardMessage("DBClosedOrCollectionDeleted"), e); }
        }
    }

    /**
     * Deletes a document from the default collection. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this function is the same as calling delete(Document, ConcurrencyControl.LAST_WRITE_WINS)
     *
     * @param document The document.
     * @throws CouchbaseLiteException on error
     * @deprecated Use getDefaultCollection().delete
     */
    @Deprecated
    public void delete(@NonNull Document document) throws CouchbaseLiteException {
        delete(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

    /**
     * Deletes a document from the default collection. When used with lastWriteWins concurrency
     * control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, delete will fail and the method will return false.
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @throws CouchbaseLiteException on error
     * @deprecated Use getDefaultCollection().delete
     */
    @Deprecated
    public boolean delete(@NonNull Document document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        return getDefaultCollectionOrThrow().delete(document, concurrencyControl);
    }

    /**
     * Purges the passed document from the default collection. This is more drastic than delete(Document):
     * it removes all local traces of the document. Purges will NOT be replicated to other databases.
     *
     * @param document the document to be purged.
     * @deprecated Use getDefaultCollection().purge
     */
    @Deprecated
    public void purge(@NonNull Document document) throws CouchbaseLiteException {
        getDefaultCollectionOrThrow().purge(document);
    }

    /**
     * Purges the document with the passed id from default collection. This is more drastic than delete(Document),
     * it removes all local traces of the document. Purges will NOT be replicated to other databases.
     *
     * @param id the document ID
     * @deprecated Use getDefaultCollection().purge
     */
    @Deprecated
    public void purge(@NonNull String id) throws CouchbaseLiteException { getDefaultCollectionOrThrow().purge(id); }

    /**
     * Sets an expiration date for a document in the default collection. The document
     * will be purged from the database at the set time.
     *
     * @param id         The ID of the Document
     * @param expiration Nullable expiration timestamp as a Date, set timestamp to null
     *                   to remove expiration date time from doc.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     * @deprecated Use getDefaultCollection().setDocumentExpiration
     */
    @Deprecated
    public void setDocumentExpiration(@NonNull String id, @Nullable Date expiration) throws CouchbaseLiteException {
        getDefaultCollectionOrThrow().setDocumentExpiration(id, expiration);
    }

    /**
     * Returns the expiration time of the document. If the document has no expiration time set,
     * the method will return null.
     *
     * @param id The ID of the Document
     * @return Date a nullable expiration timestamp of the document or null if time not set.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     * @deprecated Use getDefaultCollection().getDocumentExpiration
     */
    @Deprecated
    @Nullable
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        return getDefaultCollectionOrThrow().getDocumentExpiration(id);
    }

    // - Change listeners:

    /**
     * Adds a change listener for the changes that occur in the database. The changes will be delivered on the UI
     * thread for the Android platform and on an arbitrary thread for the Java platform. When developing a Java
     * Desktop application using Swing or JavaFX that needs to update the UI after receiving the changes, make
     * sure to schedule the UI update on the UI thread by using SwingUtilities.invokeLater(Runnable) or
     * Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     * @deprecated Use getDefaultCollection().addChangeListener
     */
    @Deprecated
    @NonNull
    public ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener) {
        return addChangeListener(null, listener);
    }

    /**
     * Adds a change listener for the changes that occur in the database with an executor on which the changes will be
     * posted to the listener. If the executor is not specified, the changes will be delivered on the UI thread for
     * the Android platform and on an arbitrary thread for the Java platform.
     *
     * @param listener callback
     * @deprecated Use getDefaultCollection().addChangeListener
     */
    @Deprecated
    @NonNull
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull DatabaseChangeListener listener) {
        return getDefaultCollectionOrThrow().addChangeListener(executor, listener::changed);
    }

    /**
     * Adds a change listener for the changes that occur to the specified document, in the default collection.
     * On the Android platform changes will be delivered on the UI thread.  On other Java platforms changes
     * will be delivered on an arbitrary thread. When developing a Java Desktop application using Swing or JavaFX
     * for a UI that will be updated in response to a change, make sure to schedule the UI update
     * on the UI thread by using SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable)
     * respectively.
     *
     * @deprecated Use getDefaultCollection().addDocumentChangeListener
     */
    @Deprecated
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String docId, @NonNull DocumentChangeListener listener) {
        return addDocumentChangeListener(docId, null, listener);
    }

    /**
     * Adds a change listener for the changes that occur to the specified document, in the default collection.
     * Changes will be posted to the listener on the passed  executor on which.
     * If the executor is not specified, the changes will be delivered on the UI thread for
     * the Android platform and on an arbitrary thread for the Java platform.
     *
     * @deprecated Use getDefaultCollection().addDocumentChangeListener
     */
    @Deprecated
    @NonNull
    public ListenerToken addDocumentChangeListener(
        @NonNull String docId,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        return getDefaultCollectionOrThrow().addDocumentChangeListener(docId, executor, listener);
    }

    /**
     * Removes a change listener added to the default collection.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     * @deprecated Use ListenerToken.remove
     */
    @Deprecated
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        if (!(token instanceof ChangeListenerToken)) { return; }
        final Collection defaultCollection = getDefaultCollectionOrThrow();
        final String docId = ((ChangeListenerToken<?>) token).getKey();
        // This hackery depends on the fact that we only set the keys for DocumentChangeListeners
        if (docId == null) { defaultCollection.removeCollectionChangeListener(token); }
        else { defaultCollection.removeDocumentChangeListener(token); }
    }

    // - Indices:

    /**
     * Get a list of the names of indices on the default collection.
     *
     * @return the list of index names
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().getIndexes
     */
    @Deprecated
    @NonNull
    public List<String> getIndexes() throws CouchbaseLiteException {
        return new ArrayList<>(getDefaultCollectionOrThrow().getIndexes());
    }

    /**
     * Add an index to the default collection.
     *
     * @param name  index name
     * @param index index description
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().createIndex
     */
    @Deprecated
    public void createIndex(@NonNull String name, @NonNull Index index) throws CouchbaseLiteException {
        getDefaultCollectionOrThrow().createIndexInternal(name, index);
    }

    /**
     * Add an index to the default collection.
     *
     * @param name   index name
     * @param config index configuration
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().createIndex
     */
    @Deprecated
    public void createIndex(@NonNull String name, @NonNull IndexConfiguration config) throws CouchbaseLiteException {
        getDefaultCollectionOrThrow().createIndexInternal(name, config);
    }

    /**
     * Delete the named index from the default collection.
     *
     * @param name name of the index to delete
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().deleteIndex
     */
    @Deprecated
    public void deleteIndex(@NonNull String name) throws CouchbaseLiteException {
        getDefaultCollectionOrThrow().deleteIndex(name);
    }

    //---------------------------------------------
    // Protected access
    //---------------------------------------------

    @NonNull
    protected abstract Database getDatabase();

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try {
            // Closing these things might just speed things up a little
            shutdownActiveProcesses(activeProcesses);
            shutdownExecutors(postExecutor, queryExecutor, 0);
        }
        finally { super.finalize(); }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // - Databases:

    // Instead of clone()
    @NonNull
    Database copy() throws CouchbaseLiteException { return new Database(name, config); }

    @Nullable
    File getFilePath() {
        final String path = getPath();
        return (path == null) ? null : new File(path);
    }

    @Nullable
    File getDbFile() {
        final String path = getDbPath();
        return (path == null) ? null : new File(path);
    }

    @Nullable
    String getUuid() {
        byte[] uuid = null;
        LiteCoreException err = null;

        synchronized (getDbLock()) {
            if (!isOpenLocked()) { return null; }

            try { uuid = getOpenC4DbLocked().getPublicUUID(); }
            catch (LiteCoreException e) { err = e; }
        }

        if (err != null) { Log.w(DOMAIN, "Failed retrieving database UUID", err); }

        return (uuid == null) ? null : PlatformUtils.getEncoder().encodeToString(uuid);
    }

    @NonNull
    FLSharedKeys getSharedKeys() { return sharedKeys; }

    // - Collections:

    void verifyCollections(@NonNull Set<Collection> collections) {
        for (Collection collection: collections) {
            if (collection == null) {
                throw new IllegalArgumentException("Collection may not be null");
            }
            if (!equals(collection.getDatabase())) {
                throw new IllegalArgumentException(
                    "Collection " + collection + " does not belong to database " + getName());
            }
        }
    }

    @NonNull
    Set<Collection> getDefaultCollectionAsSet() {
        final Collection defaultCollection;
        try { defaultCollection = Collection.getDefaultCollection(this.getDatabase()); }
        catch (CouchbaseLiteException e) { throw new CouchbaseLiteError("Can't get default collection", e); }
        if (defaultCollection == null) { throw new IllegalArgumentException("Database " + getName() + "has no default collection"); }
        final HashSet<Collection> collections = new HashSet<>();
        collections.add(defaultCollection);
        return collections;
    }

    @NonNull
    C4Collection addC4Collection(@NonNull String scopeName, @NonNull String collectionName)
        throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().addCollection(scopeName, collectionName); }
    }

    @Nullable
    C4Collection getC4Collection(@NonNull String scopeName, @NonNull String collectionName)
        throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().getCollection(scopeName, collectionName); }
    }

    @NonNull
    C4Collection getDefaultC4Collection() throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().getDefaultCollection(); }
    }

    // - Documents:

    @GuardedBy("getDbLock()")
    void beginTransaction() throws CouchbaseLiteException {
        try { getOpenC4DbLocked().beginTransaction(); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @GuardedBy("getDbLock()")
    void endTransaction(boolean commit) throws CouchbaseLiteException {
        try { getOpenC4DbLocked().endTransaction(commit); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    // - Replicators:

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    C4Replicator createRemoteReplicator(
        @NonNull Map<Collection, CollectionConfiguration> collections,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDbName,
        @NonNull MessageFraming framing,
        @NonNull ReplicatorType type,
        boolean continuous,
        @Nullable Map<String, Object> options,
        @NonNull C4Replicator.StatusListener statusListener,
        @NonNull C4Replicator.DocEndsListener docEndsListener,
        @NonNull Replicator replicator,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (getDbLock()) {
            c4Repl = getOpenC4DbLocked().createRemoteReplicator(
                collections,
                scheme,
                host,
                port,
                path,
                remoteDbName,
                framing,
                type,
                continuous,
                options,
                statusListener,
                docEndsListener,
                replicator,
                socketFactory);
        }
        return c4Repl;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    C4Replicator createLocalReplicator(
        @NonNull Map<Collection, CollectionConfiguration> collections,
        @NonNull Database targetDb,
        @NonNull ReplicatorType type,
        boolean continuous,
        @Nullable Map<String, Object> options,
        @NonNull C4Replicator.StatusListener statusListener,
        @NonNull C4Replicator.DocEndsListener docEndsListener,
        @NonNull Replicator replicator)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (getDbLock()) {
            c4Repl = getOpenC4DbLocked().createLocalReplicator(
                collections,
                targetDb.getOpenC4DbLocked(),
                type,
                continuous,
                options,
                statusListener,
                docEndsListener,
                replicator);
        }
        return c4Repl;
    }

    @NonNull
    C4Replicator createMessageEndpointReplicator(
        @NonNull Set<Collection> collections,
        @NonNull C4Socket c4Socket,
        @Nullable Map<String, Object> options,
        @NonNull C4Replicator.StatusListener listener)
        throws LiteCoreException {
        synchronized (getDbLock()) {
            return getOpenC4DbLocked().createMessageEndpointReplicator(collections, c4Socket, options, listener);
        }
    }

    void addActiveReplicator(@NonNull AbstractReplicator replicator) {
        synchronized (getDbLock()) {
            assertOpenUnchecked();

            registerProcess(new ActiveProcess<AbstractReplicator>(replicator) {
                @Override
                public void stop() { replicator.stop(); }

                @Override
                public boolean isActive() {
                    return !ReplicatorActivityLevel.STOPPED.equals(replicator.getState());
                }
            });
        }
    }

    void removeActiveReplicator(@NonNull AbstractReplicator replicator) { unregisterProcess(replicator); }

    // - Replicator: Conflict resolution

    void resolveReplicationConflict(
        @Nullable ConflictResolver resolver,
        @NonNull ReplicatedDocument rDoc,
        @NonNull Fn.NullableConsumer<CouchbaseLiteException> callback) {
        int n = 0;
        CouchbaseLiteException err = null;
        try {
            while (true) {
                if (n++ > Collection.MAX_CONFLICT_RESOLUTION_RETRIES) {
                    err = new CouchbaseLiteException(
                        "Too many attempts to resolve a conflicted document(" + n + "): " + rDoc,
                        CBLError.Domain.CBLITE,
                        CBLError.Code.UNEXPECTED_ERROR);
                    break;
                }

                final String scope = rDoc.getScope();
                final String name = rDoc.getCollection();
                try {
                    final Collection collection = Collection.getCollection(this.getDatabase(), scope, name);
                    if (collection == null) {
                        err = new CouchbaseLiteException(
                            "Cannot find collection " + getName() + "." + scope + "." + name,
                            CBLError.Domain.CBLITE,
                            CBLError.Code.UNEXPECTED_ERROR);
                        break;
                    }

                    resolveConflictOnce(resolver, collection, rDoc.getID());
                    callback.accept(null);
                    return;
                }
                catch (CouchbaseLiteException e) {
                    if (!CouchbaseLiteException.isConflict(e)) {
                        err = e;
                        break;
                    }
                }
                catch (ConflictResolutionException e) {
                    // This error occurs when a resolver that starts after this one
                    // fixes the conflict before this one does.  When this one attempts
                    // to save, it gets a conflict error and retries.  During the retry,
                    // it cannot find a conflicting revision and throws this error.
                    // The other resolver did the right thing so there is no reason
                    // to report an error.
                    Log.w(DOMAIN, "Conflict already resolved: %s", e.getMessage());
                    break;
                }
            }
        }
        catch (RuntimeException e) {
            final String msg = e.getMessage();
            err = new CouchbaseLiteException(
                (msg != null) ? msg : "Conflict resolution failed",
                e,
                CBLError.Domain.CBLITE,
                CBLError.Code.UNEXPECTED_ERROR);
        }

        callback.accept(err);
    }

    // - Cookie Store:

    // We send the entire Set-Cookie string to Lite Core: e.g.,
    // session="asdf0p8ure"; Expires=Wed Dec 14 2022; Domain=couchbase.com
    // user="joe";  Expires=Wed Dec 14 2022; Domain=couchbase.com
    void setCookies(@NonNull URI uri, @NonNull List<String> cookies, boolean acceptParentDomain) {
        try {
            synchronized (getDbLock()) {
                final C4Database c4db = getOpenC4DbLocked();
                for (String cookie: cookies) { c4db.setCookie(uri, cookie, acceptParentDomain); }
            }
        }
        catch (LiteCoreException e) { Log.w(DOMAIN, "Cannot save cookies for " + uri, e); }
    }

    // Lite Core parses the strings we send it and returns *ONLY* the semi-colon separated
    // list of <cookie-pair>s: <cookie1-name>'='<cookie1-value>;<cookie1-name>'='<cookie1-value>,
    // e.g.: session=asdf0p8ure;user=joe
    // See: https://www.rfc-editor.org/rfc/rfc6265#section-4.1.1
    @Nullable
    String getCookies(@NonNull URI uri) {
        try {
            synchronized (getDbLock()) { return getOpenC4DbLocked().getCookies(uri); }
        }
        catch (LiteCoreException e) { Log.w(DOMAIN, "Cannot get cookies for " + uri, e); }
        return null;
    }

    // - Execution:

    void registerProcess(@NonNull ActiveProcess<?> process) {
        synchronized (activeProcesses) { activeProcesses.add(process); }
        Log.d(DOMAIN, "Added active process(%s): %s", getName(), process);
    }

    <T> void unregisterProcess(T process) {
        synchronized (activeProcesses) { activeProcesses.remove(new ActiveProcess<>(process)); }
        Log.d(DOMAIN, "Removed active process(%s): %s", getName(), process);
        verifyActiveProcesses();
    }

    // - Queries:

    @NonNull
    C4Query createJsonQuery(@NonNull String json) throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().createJsonQuery(json); }
    }

    @NonNull
    C4Query createN1qlQuery(@NonNull String n1ql) throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().createN1qlQuery(n1ql); }
    }

    // - Utility:

    @NonNull
    FLEncoder getSharedFleeceEncoder() {
        synchronized (getDbLock()) { return getOpenC4DbLocked().getSharedFleeceEncoder(); }
    }

    abstract int getEncryptionAlgorithm();

    @Nullable
    abstract byte[] getEncryptionKey();

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // - Collection:

    @NonNull
    private Collection getDefaultCollectionOrThrow() {
        Exception err = null;
        try {
            synchronized (getDbLock()) {
                assertOpenUnchecked();
                final Collection collection = getDefaultCollectionLocked();
                if (collection != null) { return collection; }
            }
        }
        catch (CouchbaseLiteException e) { err = e; }

        throw new CouchbaseLiteError(Log.lookupStandardMessage("DBClosedOrCollectionDeleted"), err);
    }

    @NonNull
    private Collection getDefaultCollectionLocked() throws CouchbaseLiteException {
        if (defaultCollection == null) { defaultCollection = Collection.getDefaultCollection(getDatabase()); }
        else if (!defaultCollection.isValid()) {
            throw new IllegalArgumentException("Database " + getName() + " default collection is invalid");
        }

        return defaultCollection;
    }

    // - Database:

    @NonNull
    private C4Database openC4Db() throws CouchbaseLiteException {
        final String parentDirPath = config.getDirectory();
        Log.d(DOMAIN, "Opening db %s at path %s", this, parentDirPath);
        try {
            return C4Database.getDatabase(
                parentDirPath,
                name,
                getEncryptionAlgorithm(),
                getEncryptionKey());
        }
        catch (LiteCoreException e) {
            if (e.code == CBLError.Code.NOT_A_DATABASE_FILE) {
                throw new CouchbaseLiteException(
                    "The provided encryption key was incorrect.",
                    e,
                    CBLError.Domain.CBLITE,
                    e.code);
            }

            if (e.code == CBLError.Code.CANT_OPEN_FILE) {
                throw new CouchbaseLiteException("CreateDBDirectoryFailed", e, CBLError.Domain.CBLITE, e.code);
            }

            throw CouchbaseLiteException.convertException(e);
        }
    }

    // - Replication: Conflict resolution

    private void resolveConflictOnce(
        @Nullable ConflictResolver resolver,
        @NonNull Collection collection,
        @NonNull String docID)
        throws CouchbaseLiteException, ConflictResolutionException {
        final Document localDoc;
        final Document remoteDoc;
        synchronized (getDbLock()) {
            localDoc = Document.getDocumentWithDeleted(collection, docID);
            remoteDoc = getConflictingRevision(collection, docID);
        }

        final Document resolvedDoc;
        // If both docs have been deleted, we're done here
        if (localDoc.isDeleted() && remoteDoc.isDeleted()) { resolvedDoc = remoteDoc; }
        else {
            // Resolve with conflict resolver:
            resolvedDoc = resolveConflict(
                (resolver != null) ? resolver : ConflictResolver.DEFAULT,
                docID,
                localDoc,
                remoteDoc);
        }

        synchronized (getDbLock()) {
            boolean commit = false;
            beginTransaction();
            try {
                saveResolvedDocument(resolvedDoc, localDoc, remoteDoc);
                commit = true;
            }
            finally { endTransaction(commit); }
        }
    }

    @NonNull
    private Document getConflictingRevision(@NonNull Collection collection, @NonNull String docID)
        throws CouchbaseLiteException, ConflictResolutionException {
        final Document remoteDoc = Document.getDocumentWithRevisions(collection, docID);
        try {
            if (!remoteDoc.selectConflictingRevision()) {
                throw new ConflictResolutionException(
                    "Unable to select conflicting revision for doc '" + docID + "'. Skipping.");
            }
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }

        return remoteDoc;
    }

    @Nullable
    private Document resolveConflict(
        @NonNull ConflictResolver resolver,
        @NonNull String docID,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc)
        throws CouchbaseLiteException {
        Log.d(
            DOMAIN,
            "Resolving doc '%s' (local=%s and remote=%s) with resolver %s",
            docID,
            localDoc.getRevisionID(),
            remoteDoc.getRevisionID(),
            resolver);

        final Collection localCollection = localDoc.getCollection();
        if (localCollection == null) {
            throw new CouchbaseLiteError("Local doc does not belong to any collection: " + docID);
        }

        final Document resolvedDoc = runClientResolver(resolver, docID, localDoc, remoteDoc);
        if (resolvedDoc == null) { return null; }

        Collection targetCollection = resolvedDoc.getCollection();
        if (targetCollection == null) {
            targetCollection = localCollection;
            resolvedDoc.setCollection(targetCollection);
        }

        if (!localCollection.equals(targetCollection)) {
            final String msg = String.format(WARN_WRONG_COLLECTION, docID, targetCollection, localCollection);
            Log.w(DOMAIN, msg);
            throw new CouchbaseLiteException(msg, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
        }

        if (!docID.equals(resolvedDoc.getId())) {
            Log.w(DOMAIN, WARN_WRONG_ID, resolvedDoc.getId(), docID);
            return new MutableDocument(docID, resolvedDoc);
        }

        return resolvedDoc;
    }

    @Nullable
    private Document runClientResolver(
        @NonNull ConflictResolver resolver,
        @NonNull String docID,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc) throws CouchbaseLiteException {
        final Conflict conflict
            = new Conflict(localDoc.isDeleted() ? null : localDoc, remoteDoc.isDeleted() ? null : remoteDoc);

        final ClientTask<Document> task = new ClientTask<>(() -> resolver.resolve(conflict));
        task.execute();

        final Exception err = task.getFailure();
        if (err != null) {
            final String msg = String.format(ERROR_RESOLVER_FAILED, docID, err.getLocalizedMessage());
            Log.w(DOMAIN, msg, err);
            throw new CouchbaseLiteException(msg, err, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
        }

        return task.getResult();
    }

    // Call in a transaction
    @GuardedBy("getDbLock()")
    private void saveResolvedDocument(
        @Nullable Document resolvedDoc,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc)
        throws CouchbaseLiteException {
        if (resolvedDoc == null) {
            if (remoteDoc.isDeleted()) { resolvedDoc = remoteDoc; }
            else if (localDoc.isDeleted()) { resolvedDoc = localDoc; }
        }

        int mergedFlags = 0x00;
        if (resolvedDoc != null) {
            final C4Document c4Doc = resolvedDoc.getC4doc();
            if (c4Doc != null) { mergedFlags = c4Doc.getSelectedFlags(); }
        }

        try { saveResolvedDocumentWithFlags(resolvedDoc, localDoc, remoteDoc, mergedFlags); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @GuardedBy("getDbLock()")
    private void saveResolvedDocumentWithFlags(
        @Nullable Document resolvedDoc,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc,
        int mergedFlags)
        throws LiteCoreException {
        byte[] mergedBodyBytes = null;

        // Unless the remote revision is being used as-is, we need a new revision:
        if (resolvedDoc != remoteDoc) {
            if ((resolvedDoc == null) || resolvedDoc.isDeleted()) {
                mergedFlags |= C4Constants.RevisionFlags.DELETED;
                try (FLEncoder enc = getSharedFleeceEncoder()) {
                    enc.writeValue(Collections.emptyMap());
                    final FLSliceResult mergedBody = enc.finish2();
                    mergedBodyBytes = mergedBody.getContent();
                }
            }
            else {
                final FLSliceResult mergedBody = resolvedDoc.encode();
                // if the resolved doc has attachments, be sure has the flag
                if (C4Document.dictContainsBlobs(mergedBody, sharedKeys)) {
                    mergedFlags |= C4Constants.RevisionFlags.HAS_ATTACHMENTS;
                }
                mergedBodyBytes = mergedBody.getContent();
            }
        }

        // Ask LiteCore to do the resolution:
        final C4Document rawDoc = Preconditions.assertNotNull(localDoc.getC4doc(), "raw doc is null");

        // The remote branch has to win so that the doc revision history matches the server's.
        rawDoc.resolveConflict(remoteDoc.getRevisionID(), localDoc.getRevisionID(), mergedBodyBytes, mergedFlags);
        rawDoc.save(0);

        Log.d(DOMAIN, "Conflict resolved as doc '%s' rev %s", localDoc.getId(), rawDoc.getRevID());
    }

    private void verifyActiveProcesses() {
        Set<ActiveProcess<?>> processes;
        synchronized (activeProcesses) { processes = new HashSet<>(activeProcesses); }

        final Set<ActiveProcess<?>> deadProcesses = new HashSet<>();
        for (ActiveProcess<?> process: processes) {
            if (!process.isActive()) {
                Log.i(DOMAIN, "Found dead db process: " + process);
                deadProcesses.add(process);
            }
        }

        if (!deadProcesses.isEmpty()) {
            synchronized (activeProcesses) { activeProcesses.removeAll(deadProcesses); }
        }

        if (closeLatch == null) { return; }

        synchronized (activeProcesses) { processes = new HashSet<>(activeProcesses); }

        final int n = processes.size();
        Log.d(DOMAIN, "Active processes %s: %d", getName(), n);
        if (n <= 0) {
            closeLatch.countDown();
            return;
        }

        for (ActiveProcess<?> process: processes) { Log.d(DOMAIN, " processes: %s", process); }
    }

    @SuppressWarnings("PMD.NPathComplexity")
    private void shutdown(boolean failIfClosed, Fn.ConsumerThrows<C4Database, LiteCoreException> onShut)
        throws CouchbaseLiteException {
        final C4Database c4Db;
        synchronized (getDbLock()) {
            final boolean open = isOpenLocked();
            Log.d(DOMAIN, "Shutdown (%b, %b)", failIfClosed, open);
            if (!(failIfClosed || open)) { return; }

            c4Db = getOpenC4DbLocked();
            setC4DatabaseLocked(null);
            // mustBeOpen will now fail, which should prevent any new processes from being registered.

            // ??? Need to shutdown observers?

            closeLatch = new CountDownLatch(1);
            Set<ActiveProcess<?>> liveProcesses = null;
            synchronized (activeProcesses) {
                if (!activeProcesses.isEmpty()) { liveProcesses = new HashSet<>(activeProcesses); }
            }

            shutdownActiveProcesses(liveProcesses);

            // the replicators won't be able to shut down until this lock is released
        }

        try {
            for (int i = 0; ; i++) {
                verifyActiveProcesses();
                if ((i >= DB_CLOSE_MAX_RETRIES) && (closeLatch.getCount() > 0)) {
                    throw new CouchbaseLiteException("Shutdown failed", CBLError.Domain.CBLITE, CBLError.Code.BUSY);
                }
                if (closeLatch.await(DB_CLOSE_WAIT_SECS, TimeUnit.SECONDS)) { break; }
            }
        }
        catch (InterruptedException ignore) { }

        synchronized (getDbLock()) {
            try { onShut.accept(c4Db); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }

        shutdownExecutors(postExecutor, queryExecutor, EXECUTOR_CLOSE_MAX_WAIT_SECS);
    }

    // called from the finalizer
    // be careful here:
    // The call to 'stop' may cause a synchronous call to another method that modifies
    // the passed collection!  Since this thread already holds the lock, the call will
    // execute immediately causing a concurrent modification exception.
    private void shutdownActiveProcesses(java.util.Collection<ActiveProcess<?>> processes) {
        if (processes == null) { return; }
        for (ActiveProcess<?> process: processes) { process.stop(); }
    }

    // called from the finalizer
    private void shutdownExecutors(
        ExecutionService.CloseableExecutor pExec,
        ExecutionService.CloseableExecutor qExec,
        int waitTime) {
        // shutdown executor service
        if (pExec != null) { pExec.stop(waitTime, TimeUnit.SECONDS); }
        if (qExec != null) { qExec.stop(waitTime, TimeUnit.SECONDS); }
    }

    // Fix the bug in 2.8.0 (CBL-1408) that caused databases created in the
    // default directory to be created in a *different* default directory.
    // The fix is to use the original "real" default dir (the one used by all pre 2.8.0 code)
    // and to copy a database from the "2.8" default directory into the "real" default
    // directory as long as it won't overwrite anything that is already there.
    private void fixHydrogenBug(@NonNull ImmutableDatabaseConfiguration config, @NonNull String dbName)
        throws CouchbaseLiteException {
        // This is the real default directory
        final String defaultDirPath = CouchbaseLiteInternal.getDefaultDbDir().getAbsolutePath();

        // Check to see if the rootDirPath refers to the default directory.  If not, none of this is relevant.
        // Both rootDir and defaultDir are canonical, so string comparison should work.
        if (!defaultDirPath.equals(config.getDirectory())) { return; }

        final File defaultDir = new File(defaultDirPath);

        // If this database doesn't exist in the 2.8 default dir, we're done here.
        final File twoDotEightDefaultDir = new File(defaultDir, ".couchbase");
        if (!exists(dbName, twoDotEightDefaultDir)) { return; }

        // If this database already exists in the real default directory,
        // we can't risk trashing it. We just use the database in the real default
        // directory and leave well enough alone.
        // It is *always* possible to use 2.8 database, by specifying
        // its directory explicitly.
        if (exists(dbName, defaultDir)) { return; }

        // This database is in the 2.8 default dir but not in the real
        // default dir.  Copy it to where it belongs.
        final File twoDotEightDb = C4Database.getDatabaseFile(twoDotEightDefaultDir, dbName);
        try { Database.copy(twoDotEightDb, dbName, config); }
        catch (CouchbaseLiteException e) {
            // Per review: If the copy fails, delete the partial DB
            // and throw an exception.  This is a poison pill.
            // The db can only be opened by explicitly specifying 2.8.0 directory.
            try { FileUtils.eraseFileOrDir(C4Database.getDatabaseFile(defaultDir, dbName)); }
            catch (Exception ignore) { }
            throw e;
        }
    }
}
