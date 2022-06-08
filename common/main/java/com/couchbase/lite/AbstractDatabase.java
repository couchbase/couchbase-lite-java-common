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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ImmutableDatabaseConfiguration;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Collection;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4DatabaseObserver;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.core.C4DocumentChange;
import com.couchbase.lite.internal.core.C4DocumentObserver;
import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.exec.ClientTask;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.listener.ChangeListenerToken;
import com.couchbase.lite.internal.listener.ChangeNotifier;
import com.couchbase.lite.internal.replicator.ConflictResolutionException;
import com.couchbase.lite.internal.replicator.ReplicatorListener;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * AbstractDatabase is a base class of A Couchbase Lite Database.
 */
@SuppressWarnings({
    "PMD.CyclomaticComplexity",
    "PMD.TooManyMethods",
    "PMD.ExcessiveImports",
    "PMD.ExcessivePublicCount"})
abstract class AbstractDatabase extends BaseDatabase {

    /**
     * Gets the logging controller for the Couchbase Lite library to configure the
     * logging settings and add custom logging.
     * <p>
     */
    // Public API.  Do not fix the name.
    @SuppressWarnings({"ConstantName", "PMD.FieldNamingConventions"})
    @NonNull
    public static final com.couchbase.lite.Log log = new com.couchbase.lite.Log();

    //---------------------------------------------
    // Constants
    //---------------------------------------------
    private static final String ERROR_RESOLVER_FAILED = "Conflict resolution failed for document '%s': %s";
    private static final String WARN_WRONG_DATABASE = "The database to which the document produced by"
        + " conflict resolution for document '%s' belongs, '%s', is not the one in which it will be stored (%s)";
    private static final String WARN_WRONG_ID = "The ID of the document produced by conflict resolution"
        + " for document (%s) does not match the IDs of the conflicting documents (%s)";

    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    private static final int MAX_CHANGES = 100;

    private static final int DB_CLOSE_WAIT_SECS = 6; // > Core replicator timeout
    private static final int DB_CLOSE_MAX_RETRIES = 5; // random choice: wait for 5 replicators
    private static final int EXECUTOR_CLOSE_MAX_WAIT_SECS = 5;

    private static final String INDEX_KEY_NAME = "name";

    // A random but absurdly large number.
    private static final int MAX_CONFLICT_RESOLUTION_RETRIES = 13;

    private static final int DEFAULT_DATABASE_FLAGS
        = C4Constants.DatabaseFlags.CREATE
        | C4Constants.DatabaseFlags.AUTO_COMPACT
        | C4Constants.DatabaseFlags.SHARED_KEYS;

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
    public static boolean exists(@NonNull String name, @NonNull File directory) {
        Preconditions.assertNotNull(name, "name");
        Preconditions.assertNotNull(directory, "directory");
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
            C4Database.copyDb(path.getCanonicalPath(), dbDir, name, DEFAULT_DATABASE_FLAGS, algorithm, encryptionKey);
            return;
        }
        catch (LiteCoreException e) { err = CouchbaseLiteException.convertException(e); }
        catch (IOException e) { err = new CouchbaseLiteException("Failed creating canonical path for " + path, e); }

        FileUtils.eraseFileOrDir(C4Database.getDatabaseFile(new File(dbDir), name));

        throw err;
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

    @GuardedBy("getDbLock()")
    private final Map<String, Scope> scopes = new HashMap<>();

    @GuardedBy("activeProcesses")
    private final Set<ActiveProcess<?>> activeProcesses;

    // A map of doc ids to the groups of listeners listening for changes to that doc
    @GuardedBy("getDbLock()")
    private final Map<String, DocumentChangeNotifier> docChangeNotifiers;

    @GuardedBy("getDbLock()")
    private ChangeNotifier<DatabaseChange> dbChangeNotifier;

    @GuardedBy("getDbLock()")
    private C4DatabaseObserver c4DbObserver;

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
        this.docChangeNotifiers = new HashMap<>();

        fixHydrogenBug(config, name);

        // Can't open the DB until the file system is set up.
        final C4Database c4db = openC4Db();
        setC4DatabaseLocked(c4db);

        // Initialize a shared keys:
        this.sharedKeys = c4db.getFLSharedKeys();

        // Scope
        loacScopesAndCollections(c4db);

        // warn if logging has not been turned on
        Log.warn();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    // GET EXISTING DOCUMENT

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
        synchronized (getDbLock()) { return (!isOpen()) ? null : getDbPath(); }
    }

    /**
     * The number of documents in the database.
     *
     * @return the number of documents in the database, 0 if database is closed.
     * @deprecated Use getDefaultCollection().getCount()
     */
    @Deprecated
    public long getCount() {
        synchronized (getDbLock()) { return (!isOpen()) ? 0L : getOpenC4DbLocked().getDocumentCount(); }
    }

    /**
     * Returns a copy of the database configuration.
     *
     * @return the READONLY copied config object
     */
    @NonNull
    public DatabaseConfiguration getConfig() { return new DatabaseConfiguration(config); }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the database, the value returned will be null.
     *
     * @param id the document ID
     * @return the Document object
     * @deprecated Use getDefaultCollection().getCount()
     */
    @Deprecated
    @Nullable
    public Document getDocument(@NonNull String id) {
        Preconditions.assertNotEmpty(id, "id");

        synchronized (getDbLock()) {
            mustBeOpen();
            try { return Document.getDocument((Database) this, id, false); }
            catch (CouchbaseLiteException e) { Log.i(LogDomain.DATABASE, "Failed retrieving document: %s", e, id); }
        }
        return null;
    }

    /**
     * Saves a document to the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this method is the same as calling the ave(MutableDocument, ConcurrencyControl)
     * method with LAST_WRITE_WINS concurrency control.
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
     * Saves a document to the database. When used with LAST_WRITE_WINS
     * concurrency control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, save will fail with false value
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
        try {
            saveInternal(document, null, false, concurrencyControl);
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        return false;
    }

    /**
     * Saves a document to the database. Conflicts will be resolved by the passed ConflictHandler
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
        saveWithConflictHandler(document, conflictHandler);
        return true;
    }

    /**
     * Deletes a document from the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this function is the same as calling delete(Document, ConcurrencyControl)
     * function with LAST_WRITE_WINS concurrency control.
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
     * Deletes a document from the database. When used with lastWriteWins concurrency
     * control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, delete will fail with
     * 'false' value returned.
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @throws CouchbaseLiteException on error
     * @deprecated Use getDefaultCollection().delete
     */
    @Deprecated
    public boolean delete(@NonNull Document document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        // NOTE: synchronized in save(Document, boolean, ConcurrencyControl, ConflictHandler) method
        try {
            saveInternal(document, null, true, concurrencyControl);
            return true;
        }
        catch (CouchbaseLiteException e) {
            if (!CouchbaseLiteException.isConflict(e)) { throw e; }
        }
        return false;
    }

    // Batch operations:

    /**
     * Purges the given document from the database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param document the document to be purged.
     * @deprecated Use getDefaultCollection().purge
     */
    @Deprecated
    public void purge(@NonNull Document document) throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");

        if (document.isNewDocument()) {
            throw new CouchbaseLiteException("DocumentNotFound", CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
        }

        synchronized (getDbLock()) {
            prepareDocument(document);

            try { purge(document.getId()); }
            catch (CouchbaseLiteException e) {
                // Ignore not found (already deleted)
                if (e.getCode() != CBLError.Code.NOT_FOUND) { throw e; }
            }

            document.replaceC4Document(null); // Reset c4doc:
        }
    }

    /**
     * Purges the given document id for the document in database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param id the document ID
     * @deprecated Use getDefaultCollection().purge
     */
    @Deprecated
    public void purge(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        synchronized (getDbLock()) { purgeLocked(id); }
    }

    // Database changes:

    /**
     * Sets an expiration date on a document. After this time, the document
     * will be purged from the database.
     *
     * @param id         The ID of the Document
     * @param expiration Nullable expiration timestamp as a Date, set timestamp to null
     *                   to remove expiration date time from doc.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     * @deprecated Use getDefaultCollection().setDocumentExpiration
     */
    @Deprecated
    public void setDocumentExpiration(@NonNull String id, @Nullable Date expiration) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        synchronized (getDbLock()) {
            try { getOpenC4DbLocked().setDocumentExpiration(id, (expiration == null) ? 0 : expiration.getTime()); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    /**
     * Returns the expiration time of the document. null will be returned if there is
     * no expiration time set
     *
     * @param id The ID of the Document
     * @return Date a nullable expiration timestamp of the document or null if time not set.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     * @deprecated Use getDefaultCollection().getDocumentExpiration
     */
    @Deprecated
    @Nullable
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");

        synchronized (getDbLock()) {
            try {
                final long timestamp = getOpenC4DbLocked().getDocumentExpiration(id);
                return (timestamp == 0) ? null : new Date(timestamp);
            }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

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

        synchronized (getDbLock()) {
            final C4Database db = getOpenC4DbLocked();
            boolean commit = false;
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

        postDatabaseChanged();
    }

    // Document changes:

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

    // Others:

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
        Preconditions.assertNotNull(listener, "listener");
        synchronized (getDbLock()) {
            mustBeOpen();
            return addDatabaseChangeListenerLocked(executor, listener);
        }
    }

    /**
     * Removes the change listener added to the database.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     * @deprecated Use getDefaultCollection().removeChangeListener
     */
    @Deprecated
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");

        synchronized (getDbLock()) {
            if (token instanceof ChangeListenerToken) {
                final ChangeListenerToken<?> changeListenerToken = (ChangeListenerToken<?>) token;
                if (changeListenerToken.getKey() != null) {
                    removeDocumentChangeListenerLocked(changeListenerToken);
                    return;
                }
            }

            removeDatabaseChangeListenerLocked(token);
        }
    }

    /**
     * Adds a change listener for the changes that occur to the specified document.
     * The changes will be delivered on the UI thread for the Android platform and on an arbitrary
     * thread for the Java platform. When developing a Java Desktop application using Swing or JavaFX
     * that needs to update the UI after receiving the changes, make sure to schedule the UI update
     * on the UI thread by using SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable)
     * respectively.
     *
     * @deprecated Use getDefaultCollection().
     */
    @Deprecated
    @NonNull
    public ListenerToken addDocumentChangeListener(@NonNull String docId, @NonNull DocumentChangeListener listener) {
        return addDocumentChangeListener(docId, null, listener);
    }

    /**
     * Adds a change listener for the changes that occur to the specified document with an executor on which
     * the changes will be posted to the listener.  If the executor is not specified, the changes will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     *
     * @deprecated Use getDefaultCollection().
     */
    @Deprecated
    @NonNull
    public ListenerToken addDocumentChangeListener(
        @NonNull String docId,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        Preconditions.assertNotNull(docId, "docId");
        Preconditions.assertNotNull(listener, "listener");

        synchronized (getDbLock()) {
            mustBeOpen();
            return addDocumentChangeListenerLocked(docId, executor, listener);
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

    // Queries:

    /**
     * Create a SQL++ query.
     *
     * @param query a valid SQL++ query
     * @return the Query object
     */
    @NonNull
    public Query createQuery(@NonNull String query) {
        synchronized (getDbLock()) {
            mustBeOpen();
            return new N1qlQuery(this, query);
        }
    }

    /**
     * Get a list of the names of database indices.
     *
     * @return the list of index names
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().getIndexes
     */
    @Deprecated
    @NonNull
    public List<String> getIndexes() throws CouchbaseLiteException {
        final FLValue flIndexInfo;
        synchronized (getDbLock()) {
            try { flIndexInfo = getOpenC4DbLocked().getIndexesInfo(); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }

        final List<String> indexNames = new ArrayList<>();

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
     * Add an index to the database
     *
     * @param name  index name
     * @param index index description
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().createIndex
     */
    @Deprecated
    public void createIndex(@NonNull String name, @NonNull Index index) throws CouchbaseLiteException {
        createIndexInternal(name, index);
    }

    /**
     * Add an index to the database
     *
     * @param name   index name
     * @param config index configuration
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().createIndex
     */
    @Deprecated
    public void createIndex(@NonNull String name, @NonNull IndexConfiguration config) throws CouchbaseLiteException {
        createIndexInternal(name, config);
    }

    /**
     * Delete the named index.
     *
     * @param name name of the index to delete
     * @throws CouchbaseLiteException on failure
     * @deprecated Use getDefaultCollection().deleteIndex
     */
    @Deprecated
    public void deleteIndex(@NonNull String name) throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            try { getOpenC4DbLocked().deleteIndex(name); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    public boolean performMaintenance(MaintenanceType type) throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            try { return getOpenC4DbLocked().performMaintenance(type); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    /**
     * (UNCOMMITTED) Use this API if you are developing Javascript language bindings.
     * If you are developing a native app, you must use the {@link Blob} API.
     *
     * @param blob a blob
     */
    @Internal("This method is not part of the public API: it is for internal use only")
    public void saveBlob(@NonNull Blob blob) {
        synchronized (getDbLock()) { mustBeOpen(); }
        blob.installInDatabase((Database) this);
    }

    /**
     * (UNCOMMITTED) Use this API if you are developing Javascript language bindings.
     * If you are developing a native app, you must use the {@link Blob} API.
     *
     * @param props blob properties
     */
    @Internal("This method is not part of the public API: it is for internal use only")
    @Nullable
    public Blob getBlob(@NonNull Map<String, Object> props) {
        synchronized (getDbLock()) { mustBeOpen(); }

        if (!Blob.isBlob(props)) { throw new IllegalArgumentException("getBlob arg does not specify a blob"); }

        final Blob blob = new Blob(this, props);

        return (blob.updateSize() < 0) ? null : blob;
    }

    @NonNull
    @Override
    public String toString() { return "Database{" + ClassUtils.objId(this) + ", name='" + name + "'}"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof AbstractDatabase)) { return false; }
        final AbstractDatabase other = (AbstractDatabase) o;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    //---------------------------------------------
    // Scopes
    //---------------------------------------------

    /**
     * Get scope names that have at least one collection.
     * Note: the default scope is exceptional as it will always be listed even though there are no collections
     * under it.
     */
    @NonNull
    public final Set<Scope> getScopes() throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            assertOpen();
            return Fn.filterToSet(
                scopes.values(),
                scope -> Scope.DEFAULT_NAME.equals(scope.getName()) || (scope.getCollectionCount() > 0));
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
            assertOpen();
            final Scope scope = scopes.get(name);
            return ((scope == null) || (Scope.DEFAULT_NAME.equals(name)) || ((scope.getCollectionCount() > 0)))
                ? scope
                : null;
        }
    }

    /// Get the default scope.
    @NonNull
    public final Scope getDefaultScope() throws CouchbaseLiteException {
        return Preconditions.assertNotNull(getScope(Scope.DEFAULT_NAME), "default scope");
    }

    //---------------------------------------------
    // Collections
    //---------------------------------------------

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
        final Scope scope;
        synchronized (getDbLock()) {
            assertOpen();
            scope = scopes.get(StringUtils.isEmpty(scopeName) ? Scope.DEFAULT_NAME : scopeName);
        }

        return (scope == null) ? new HashSet<>() : scope.getCollections();
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
     * @param name      the collection to find
     * @param scopeName the scope in which to create the collection
     * @return the named collection or null
     */
    @Nullable
    public final Collection getCollection(@NonNull String name, @Nullable String scopeName)
        throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }
        final Scope scope;
        synchronized (getDbLock()) {
            assertOpen();
            scope = scopes.get(scopeName);
        }
        return (scope == null) ? null : scope.getCollection(name);
    }

    /**
     * Get the default collection. If the default collection is deleted, null will be returned.
     *
     * @return the default collection or null if it does not exist.
     */
    @Nullable
    public final Collection getDefaultCollection() throws CouchbaseLiteException {
        return getCollection(Collection.DEFAULT_NAME);
    }

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
     * @param name      the name of the new collection
     * @param scopeName the scope in which to create the collection
     * @return the named collection in the default scope
     * @throws CouchbaseLiteException on failure
     */
    @NonNull
    public final Collection createCollection(@NonNull String name, @Nullable String scopeName)
        throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }

        Scope scope;
        synchronized (getDbLock()) {
            assertOpen();

            scope = scopes.get(scopeName);
            if (scope == null) {
                scope = new Scope(scopeName, this);
                scopes.put(scopeName, scope);
            }

            return scope.getOrAddCollection(name);
        }
    }

    /**
     * Delete a collection by name  in the default scope. If the collection doesn't exist, the operation
     * will be no-ops. Note: the default collection can be deleted but cannot be recreated.
     *
     * @param name the collection to be deleted
     * @throws CouchbaseLiteException on failure
     */
    public final void deleteCollection(@NonNull String name) throws CouchbaseLiteException {
        deleteCollection(name, Scope.DEFAULT_NAME);
    }

    /**
     * Delete a collection by name  in the specified scope. If the collection doesn't exist, the operation
     * will be no-ops. Note: the default collection can be deleted but cannot be recreated.
     *
     * @param name      the collection to be deleted
     * @param scopeName the scope from which to delete the collection
     * @throws CouchbaseLiteException on failure
     */
    public final void deleteCollection(@NonNull String name, @Nullable String scopeName) throws CouchbaseLiteException {
        if (scopeName == null) { scopeName = Scope.DEFAULT_NAME; }
        final Scope scope;
        synchronized (getDbLock()) { scope = scopes.get(scopeName); }
        if (scope == null) { return; }
        scope.deleteCollection(name);
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try {
            // This is the only thing that is really essential.
            final C4DatabaseObserver observer = c4DbObserver;
            if (observer != null) { observer.close(); }

            // This stuff might just speed things up a little
            shutdownActiveProcesses(activeProcesses);
            shutdownExecutors(postExecutor, queryExecutor, 0);
        }
        finally { super.finalize(); }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    boolean equalsWithPath(@Nullable Database other) {
        return (other != null) && Objects.equals(getFilePath(), other.getFilePath());
    }

    // Instead of clone()
    @NonNull
    Database copy() throws CouchbaseLiteException { return new Database(name, config); }

    //////// DATABASES:

    @Nullable
    String getUuid() {
        byte[] uuid = null;
        LiteCoreException err = null;

        synchronized (getDbLock()) {
            if (!isOpen()) { return null; }

            try { uuid = getOpenC4DbLocked().getPublicUUID(); }
            catch (LiteCoreException e) { err = e; }
        }

        if (err != null) { Log.w(DOMAIN, "Failed retrieving database UUID", err); }

        return (uuid == null) ? null : PlatformUtils.getEncoder().encodeToString(uuid);
    }

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

    //////// SCOPES AND COLLECTIONS:

    @NonNull
    Collection addCollection(@NonNull Scope scope, @NonNull String collectionName) throws CouchbaseLiteException {
        synchronized (getDbLock()) { return Collection.create(getOpenC4DbLocked(), scope, collectionName); }
    }

    void deleteCollection(@NonNull Collection collection) throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            getOpenC4DbLocked().deleteCollection(collection.getScope().getName(), collection.getName());
        }
    }

    //////// DOCUMENTS:

    // This method is not thread safe

    @NonNull
    C4Query createJsonQuery(@NonNull String json) throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().createJsonQuery(json); }
    }

    @NonNull
    C4Query createN1qlQuery(@NonNull String n1ql) throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().createN1qlQuery(n1ql); }
    }

    @NonNull
    C4Document getC4Document(@NonNull String id) throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().getDocument(id); }
    }

    @NonNull
    FLEncoder getSharedFleeceEncoder() {
        synchronized (getDbLock()) { return getOpenC4DbLocked().getSharedFleeceEncoder(); }
    }

    @NonNull
    C4DocumentObserver createDocumentObserver(@NonNull String docID, @NonNull Runnable listener) {
        synchronized (getDbLock()) { return getOpenC4DbLocked().createDocumentObserver(docID, listener); }
    }

    //////// REPLICATORS:

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    C4Replicator createRemoteReplicator(
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDatabaseName,
        int push,
        int pull,
        @NonNull MessageFraming framing,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener,
        @NonNull Replicator replicator,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (getDbLock()) {
            c4Repl = getOpenC4DbLocked().createRemoteReplicator(
                scheme,
                host,
                port,
                path,
                remoteDatabaseName,
                push,
                pull,
                framing,
                options,
                listener,
                replicator,
                pushFilter,
                pullFilter,
                socketFactory
            );
        }
        return c4Repl;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    C4Replicator createLocalReplicator(
        @NonNull Database targetDb,
        int push,
        int pull,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener,
        @NonNull Replicator replicator,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (getDbLock()) {
            c4Repl = getOpenC4DbLocked().createLocalReplicator(
                targetDb.getOpenC4DbLocked(),
                push,
                pull,
                options,
                listener,
                replicator,
                pushFilter,
                pullFilter
            );
        }
        return c4Repl;
    }

    // !!! This method is *NOT* thread safe.
    // Used wo/synchronization, there is a race on the open db
    void addActiveReplicator(AbstractReplicator replicator) {
        synchronized (getDbLock()) { mustBeOpen(); }

        registerProcess(new ActiveProcess<AbstractReplicator>(replicator) {
            @Override
            public void stop() { replicator.stop(); }

            @Override
            public boolean isActive() {
                return !ReplicatorActivityLevel.STOPPED.equals(replicator.getState());
            }
        });
    }

    void removeActiveReplicator(AbstractReplicator replicator) { unregisterProcess(replicator); }

    //////// RESOLVING REPLICATED CONFLICTS:

    void resolveReplicationConflict(
        @Nullable ConflictResolver resolver,
        @NonNull String docId,
        @NonNull Fn.NullableConsumer<CouchbaseLiteException> callback) {
        int n = 0;
        CouchbaseLiteException err = null;
        try {
            while (true) {
                if (n++ > MAX_CONFLICT_RESOLUTION_RETRIES) {
                    err = new CouchbaseLiteException(
                        "Too many attempts to resolve a conflicted document: " + n,
                        CBLError.Domain.CBLITE,
                        CBLError.Code.UNEXPECTED_ERROR);
                    break;
                }

                try {
                    resolveConflictOnce(resolver, docId);
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
                    // The other resolver did the right thing, so there is no reason
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

    //////// Cookie Store:

    void setCookie(@NonNull URI uri, @NonNull String setCookieHeader) {
        try {
            synchronized (getDbLock()) { getOpenC4DbLocked().setCookie(uri, setCookieHeader); }
        }
        catch (LiteCoreException e) { Log.w(DOMAIN, "Cannot save cookie for " + uri, e); }
    }

    @Nullable
    String getCookies(@NonNull URI uri) {
        try {
            synchronized (getDbLock()) { return getOpenC4DbLocked().getCookies(uri); }
        }
        catch (LiteCoreException e) { Log.w(DOMAIN, "Cannot get cookies for " + uri, e); }
        return null;
    }

    //////// Execution:

    void scheduleOnPostNotificationExecutor(@NonNull Runnable task, long delayMs) {
        CouchbaseLiteInternal.getExecutionService().postDelayedOnExecutor(delayMs, postExecutor, task);
    }

    void scheduleOnQueryExecutor(@NonNull Runnable task, long delayMs) {
        CouchbaseLiteInternal.getExecutionService().postDelayedOnExecutor(delayMs, queryExecutor, task);
    }

    void registerProcess(ActiveProcess<?> process) {
        synchronized (activeProcesses) { activeProcesses.add(process); }
        Log.d(DOMAIN, "Added active process(%s): %s", getName(), process);
    }

    <T> void unregisterProcess(T process) {
        synchronized (activeProcesses) { activeProcesses.remove(new ActiveProcess<>(process)); }
        Log.d(DOMAIN, "Removed active process(%s): %s", getName(), process);
        verifyActiveProcesses();
    }

    abstract int getEncryptionAlgorithm();

    @Nullable
    abstract byte[] getEncryptionKey();


    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:

    @GuardedBy("getDbLock()")
    private void beginTransaction() throws CouchbaseLiteException {
        try { getOpenC4DbLocked().beginTransaction(); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @GuardedBy("getDbLock()")
    private void endTransaction(boolean commit) throws CouchbaseLiteException {
        try { getOpenC4DbLocked().endTransaction(commit); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @NonNull
    private C4Database openC4Db() throws CouchbaseLiteException {
        final String parentDirPath = config.getDirectory();
        Log.d(DOMAIN, "Opening db %s at path %s", this, parentDirPath);
        try {
            return C4Database.getDatabase(
                parentDirPath,
                name,
                DEFAULT_DATABASE_FLAGS,
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

    //////// COLLECTIONS:

    private void loacScopesAndCollections(@NonNull C4Database c4db) {
        for (String scopeName: Preconditions.assertNotNull(c4db.getScopeNames(), "scopes")) {
            scopes.put(scopeName, new Scope(scopeName, this));
        }

        // ??? Is this necessary or can we count on LiteCore to do it correctly?
        Scope defaultScope = scopes.get(Scope.DEFAULT_NAME);
        if (defaultScope == null) {
            defaultScope = new Scope(Scope.DEFAULT_NAME, this);
            scopes.put(Scope.DEFAULT_NAME, defaultScope);
        }

        final C4Collection c4Coll = c4db.getDefaultCollection();
        if ((c4Coll != null) && (defaultScope.getCollection(Scope.DEFAULT_NAME) == null)) {
            defaultScope.cacheCollection(Collection.create(c4Coll, defaultScope, Collection.DEFAULT_NAME));
        }

        for (Scope scope: scopes.values()) {
            if (Scope.DEFAULT_NAME.equals(scope.getName())) { continue; }
            scope.loadCollections(c4db);
        }
    }


    //////// INDICES:

    private void createIndexInternal(@NonNull String name, @NonNull AbstractIndex config)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(name, "name");
        Preconditions.assertNotNull(config, "config");

        synchronized (getDbLock()) {
            final C4Database c4Db = getOpenC4DbLocked();
            try {
                c4Db.createIndex(
                    name,
                    config.getIndexSpec(),
                    config.getQueryLanguage(),
                    config.getIndexType(),
                    config.getLanguage(),
                    config.isIgnoringDiacritics());
            }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    //////// DOCUMENTS:

    // --- Database changes:

    @GuardedBy("getDbLock()")
    @NonNull
    private ListenerToken addDatabaseChangeListenerLocked(
        @Nullable Executor executor,
        @NonNull DatabaseChangeListener listener) {
        if (dbChangeNotifier == null) {
            dbChangeNotifier = new ChangeNotifier<>();
            registerC4DbObserver();
        }
        return dbChangeNotifier.addChangeListener(executor, listener);
    }

    // --- Notification: - C4DatabaseObserver/C4DocumentObserver

    @GuardedBy("getDbLock()")
    private void removeDatabaseChangeListenerLocked(@NonNull ListenerToken token) {
        if (dbChangeNotifier.removeChangeListener(token) == 0) {
            freeC4DbObserver();
            dbChangeNotifier = null;
        }
    }

    // --- Document changes:

    @GuardedBy("getDbLock()")
    @NonNull
    private ListenerToken addDocumentChangeListenerLocked(
        @NonNull String docID,
        @Nullable Executor executor,
        @NonNull DocumentChangeListener listener) {
        DocumentChangeNotifier docNotifier = docChangeNotifiers.get(docID);
        if (docNotifier == null) {
            docNotifier = new DocumentChangeNotifier((Database) this, docID);
            docChangeNotifiers.put(docID, docNotifier);
        }
        final ChangeListenerToken<?> token = docNotifier.addChangeListener(executor, listener);
        token.setKey(docID);
        return token;
    }

    @GuardedBy("getDbLock()")
    private void removeDocumentChangeListenerLocked(@NonNull ChangeListenerToken<?> token) {
        final String docID = (String) token.getKey();
        if (docChangeNotifiers.containsKey(docID)) {
            final DocumentChangeNotifier notifier = docChangeNotifiers.get(docID);
            if ((notifier != null) && (notifier.removeChangeListener(token) == 0)) {
                docChangeNotifiers.remove(docID);
                notifier.close();
            }
        }
    }

    @GuardedBy("getDbLock()")
    private void registerC4DbObserver() {
        if (!isOpen()) { return; }
        c4DbObserver = getOpenC4DbLocked().createDatabaseObserver(
            () -> scheduleOnPostNotificationExecutor(this::postDatabaseChanged, 0));
    }

    // ??? Refactor this to get rid of the warning
    @SuppressWarnings("PMD.NPathComplexity")
    private void postDatabaseChanged() {
        synchronized (getDbLock()) {
            if (!isOpen() || (c4DbObserver == null)) { return; }

            boolean external = false;
            int nChanges;
            List<String> docIDs = new ArrayList<>();
            do {
                // Read changes in batches of MAX_CHANGES
                final C4DocumentChange[] c4DocChanges = c4DbObserver.getChanges(MAX_CHANGES);

                int i = 0;
                nChanges = (c4DocChanges == null) ? 0 : c4DocChanges.length;
                if (nChanges > 0) {
                    while (c4DocChanges[i] == null) {
                        i++;
                        nChanges--;
                    }
                }
                final boolean newExternal = (nChanges > 0) && c4DocChanges[i].isExternal();

                if ((!docIDs.isEmpty()) && ((nChanges <= 0) || (external != newExternal) || (docIDs.size() > 1000))) {
                    // !!! This is going to have to find the collection that owns this change
                    dbChangeNotifier.postChange(new DatabaseChange((Database) this, docIDs));
                    docIDs = new ArrayList<>();
                }

                external = newExternal;

                for (int j = i; j < nChanges; j++) {
                    final C4DocumentChange change = c4DocChanges[j];
                    if (change != null) { docIDs.add(change.getDocID()); }
                }
            }
            while (nChanges > 0);
        }
    }

    @GuardedBy("getDbLock()")
    private void prepareDocument(Document document) throws CouchbaseLiteException {
        mustBeOpen();

        final Database db = document.getDatabase();
        if (db == null) { document.setDatabase((Database) this); }
        else if (db != this) {
            throw new CouchbaseLiteException(
                "DocumentAnotherDatabase",
                CBLError.Domain.CBLITE,
                CBLError.Code.INVALID_PARAMETER);
        }
    }

    //////// RESOLVE REPLICATED CONFLICTS:
    private void resolveConflictOnce(@Nullable ConflictResolver resolver, @NonNull String docID)
        throws CouchbaseLiteException, ConflictResolutionException {
        final Document localDoc;
        final Document remoteDoc;
        synchronized (getDbLock()) {
            localDoc = Document.getDocument((Database) this, docID);
            remoteDoc = getConflictingRevision(docID);
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
    private Document getConflictingRevision(@NonNull String docID)
        throws CouchbaseLiteException, ConflictResolutionException {
        final Document remoteDoc = Document.getDocument((Database) this, docID);
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
        final Conflict conflict
            = new Conflict(localDoc.isDeleted() ? null : localDoc, remoteDoc.isDeleted() ? null : remoteDoc);

        Log.d(
            DOMAIN,
            "Resolving doc '%s' (local=%s and remote=%s) with resolver %s",
            docID,
            localDoc.getRevisionID(),
            remoteDoc.getRevisionID(),
            resolver);

        final ClientTask<Document> task = new ClientTask<>(() -> resolver.resolve(conflict));
        task.execute();

        final Exception err = task.getFailure();
        if (err != null) {
            final String msg = String.format(ERROR_RESOLVER_FAILED, docID, err.getLocalizedMessage());
            Log.w(DOMAIN, msg, err);
            throw new CouchbaseLiteException(msg, err, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
        }

        final Document doc = task.getResult();
        if (doc == null) { return null; }

        final Database target = doc.getDatabase();
        if (!this.equals(target)) {
            if (target == null) { doc.setDatabase((Database) this); }
            else {
                final String msg = String.format(WARN_WRONG_DATABASE, docID, target.getName(), getName());
                Log.w(DOMAIN, msg);
                throw new CouchbaseLiteException(msg, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
            }
        }

        if (!docID.equals(doc.getId())) {
            Log.w(DOMAIN, WARN_WRONG_ID, doc.getId(), docID);
            return new MutableDocument(docID, doc);
        }

        return doc;
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
            if (resolvedDoc != localDoc) { resolvedDoc.setDatabase((Database) this); }

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
                    try (FLSliceResult mergedBody = enc.finish2()) { mergedBodyBytes = mergedBody.getBuf(); }
                }
            }
            else {
                try (FLSliceResult mergedBody = resolvedDoc.encode()) {
                    // if the resolved doc has attachments, be sure has the flag
                    if (C4Document.dictContainsBlobs(mergedBody, sharedKeys)) {
                        mergedFlags |= C4Constants.RevisionFlags.HAS_ATTACHMENTS;
                    }
                    mergedBodyBytes = mergedBody.getBuf();
                }
            }
        }

        // Ask LiteCore to do the resolution:
        final C4Document rawDoc = Preconditions.assertNotNull(localDoc.getC4doc(), "raw doc is null");

        // The remote branch has to win so that the doc revision history matches the server's.
        rawDoc.resolveConflict(remoteDoc.getRevisionID(), localDoc.getRevisionID(), mergedBodyBytes, mergedFlags);
        rawDoc.save(0);

        Log.d(DOMAIN, "Conflict resolved as doc '%s' rev %s", localDoc.getId(), rawDoc.getRevID());
    }

    private void saveWithConflictHandler(@NonNull MutableDocument document, @NonNull ConflictHandler handler)
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

            try {
                saveInternal(document, oldDoc, false, ConcurrencyControl.FAIL_ON_CONFLICT);
                return;
            }
            catch (CouchbaseLiteException e) {
                if (!CouchbaseLiteException.isConflict(e)) { throw e; }
            }

            // Conflict
            synchronized (getDbLock()) { oldDoc = Document.getDocument((Database) this, document.getId()); }

            try {
                if (!handler.handle(document, (oldDoc.isDeleted()) ? null : oldDoc)) {
                    throw new CouchbaseLiteException(
                        "Conflict handler returned false",
                        CBLError.Domain.CBLITE,
                        CBLError.Code.CONFLICT
                    );
                }
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
    private void saveInternal(
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

        synchronized (getDbLock()) {
            prepareDocument(document);

            boolean commit = false;
            beginTransaction();
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

                // return false if FAIL_ON_CONFLICT
                if (concurrencyControl.equals(ConcurrencyControl.FAIL_ON_CONFLICT)) {
                    throw new CouchbaseLiteException("Conflict", CBLError.Domain.CBLITE, CBLError.Code.CONFLICT);
                }

                commit = saveConflicted(document, deleting);
            }
            finally {
                endTransaction(commit);
            }
        }
    }

    @GuardedBy("getDbLock()")
    private boolean saveConflicted(@NonNull Document document, boolean deleting)
        throws CouchbaseLiteException {
        final C4Document curDoc;

        try { curDoc = getC4Document(document.getId()); }
        catch (LiteCoreException e) {
            // here if deleting and the curDoc doesn't exist.
            if (deleting
                && (e.domain == C4Constants.ErrorDomain.LITE_CORE)
                && (e.code == C4Constants.LiteCoreError.NOT_FOUND)) {
                return false;
            }

            // here if the save failed.
            throw CouchbaseLiteException.convertException(e);
        }

        // here if deleting and the curDoc has already been deleted
        if (deleting && curDoc.deleted()) {
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
                if (C4Document.dictContainsBlobs(body, sharedKeys)) {
                    revFlags |= C4Constants.RevisionFlags.HAS_ATTACHMENTS;
                }
            }

            // Save to database:
            C4Document c4Doc = (base != null) ? base : document.getC4doc();

            c4Doc = (c4Doc != null)
                ? c4Doc.update(body, revFlags)
                : getOpenC4DbLocked().createDocument(document.getId(), body, revFlags);

            document.replaceC4Document(c4Doc);
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
        finally {
            if (body != null) { body.close(); }
        }
    }

    @GuardedBy("getDbLock()")
    private void purgeLocked(@NonNull String id) throws CouchbaseLiteException {
        boolean commit = false;
        beginTransaction();
        try {
            getOpenC4DbLocked().purgeDoc(id);
            commit = true;
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
        finally {
            endTransaction(commit);
        }
    }

    private void verifyActiveProcesses() {
        final Set<ActiveProcess<?>> processes;
        final Set<ActiveProcess<?>> deadProcesses = new HashSet<>();
        synchronized (activeProcesses) { processes = new HashSet<>(activeProcesses); }
        for (ActiveProcess<?> process: processes) {
            if (!process.isActive()) {
                Log.w(DOMAIN, "Found dead process: " + process);
                deadProcesses.add(process);
            }
        }

        if (!deadProcesses.isEmpty()) {
            synchronized (activeProcesses) { activeProcesses.removeAll(deadProcesses); }
        }

        if (closeLatch == null) { return; }

        final int activeProcessCount;
        synchronized (activeProcesses) { activeProcessCount = activeProcesses.size(); }
        Log.d(DOMAIN, "Active processes(%s): %d", getName(), activeProcessCount);
        if (activeProcessCount <= 0) { closeLatch.countDown(); }
    }

    private void shutdown(boolean failIfClosed, Fn.ConsumerThrows<C4Database, LiteCoreException> onShut)
        throws CouchbaseLiteException {
        final C4Database c4Db;
        synchronized (getDbLock()) {
            final boolean open = isOpen();
            Log.d(DOMAIN, "Shutdown (%b, %b)", failIfClosed, open);
            if (!(failIfClosed || open)) { return; }

            c4Db = getOpenC4DbLocked();
            setC4DatabaseLocked(null);
            // mustBeOpen will now fail, which should prevent any new processes from being registered.

            freeC4DbObserver();
            docChangeNotifiers.clear();

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

    @GuardedBy("getDbLock()")
    private void freeC4DbObserver() {
        final C4DatabaseObserver observer = c4DbObserver;
        c4DbObserver = null;
        if (observer == null) { return; }

        observer.close();
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
