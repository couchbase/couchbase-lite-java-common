//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.json.JSONException;

import com.couchbase.lite.internal.CBLInternalException;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4DatabaseChange;
import com.couchbase.lite.internal.core.C4DatabaseObserver;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.core.C4DocumentObserver;
import com.couchbase.lite.internal.core.C4DocumentObserverListener;
import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.SharedKeys;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * AbstractDatabase is a base class of A Couchbase Lite Database.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.TooManyMethods"})
abstract class AbstractDatabase {

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

    @VisibleForTesting
    static final String DB_EXTENSION = ".cblite2";

    private static final int MAX_CHANGES = 100;

    private static final int DB_CLOSE_WAIT_SECS = 6; // > Core replicator timeout
    private static final int DB_CLOSE_MAX_RETRIES = 5; // random choice: wait for 5 replicators
    private static final int EXECUTOR_CLOSE_MAX_WAIT_SECS = 5;

    // A random but absurdly large number.
    private static final int MAX_CONFLICT_RESOLUTION_RETRIES = 13;

    // How long to wait after a database opens before expiring docs
    private static final long INITIAL_PURGE_DELAY_MS = 3;
    private static final long STANDARD_PURGE_INTERVAL_MS = 1000;

    private static final int DEFAULT_DATABASE_FLAGS
        = C4Constants.DatabaseFlags.CREATE
        | C4Constants.DatabaseFlags.AUTO_COMPACT
        | C4Constants.DatabaseFlags.SHARED_KEYS;

    static class ActiveProcess<T> {
        @NonNull
        private final T process;

        ActiveProcess(@NonNull T process) { this.process = process; }

        public boolean isActive() { return true; }

        public void stop() {}

        @NonNull
        @Override
        public String toString() { return process.toString(); }

        @Override
        public int hashCode() { return process.hashCode(); }

        @Override
        public boolean equals(Object o) {
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

        if (directory == null) { directory = CouchbaseLiteInternal.getRootDir(); }

        if (!exists(name, directory)) {
            throw new CouchbaseLiteException(
                "Database not found for delete",
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_FOUND);
        }

        final File path = getDatabaseFile(directory, name);
        try {
            Log.v(DOMAIN, "Delete database %s at %s", name, path.toString());
            C4Database.deleteDbAtPath(path.getPath());
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
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
        return getDatabaseFile(directory, name).exists();
    }

    protected static void copy(
        @NonNull File path,
        @NonNull String name,
        @NonNull DatabaseConfiguration config,
        int algorithm,
        byte[] encryptionKey)
        throws CouchbaseLiteException {

        String fromPath = path.getPath();
        if (fromPath.charAt(fromPath.length() - 1) != File.separatorChar) { fromPath += File.separator; }
        String toPath = getDatabaseFile(new File(config.getDirectory()), name).getPath();
        if (toPath.charAt(toPath.length() - 1) != File.separatorChar) { toPath += File.separator; }

        try {
            C4Database.copyDb(
                fromPath,
                toPath,
                DEFAULT_DATABASE_FLAGS,
                null,
                C4Constants.DocumentVersioning.REVISION_TREES,
                algorithm,
                encryptionKey);
        }
        catch (LiteCoreException e) {
            FileUtils.eraseFileOrDir(toPath);
            throw CouchbaseLiteException.convertException(e);
        }
    }

    @VisibleForTesting
    static File getDatabaseFile(File dir, String name) {
        return new File(dir, name.replaceAll("/", ":") + DB_EXTENSION);
    }


    //---------------------------------------------
    // Member variables
    //---------------------------------------------

    @NonNull
    final DatabaseConfiguration config;

    // Main database lock object for thread-safety
    @NonNull
    private final Object dbLock = new Object();

    private final String name;

    private final String path;

    // Executor for purge and posting Database/Document changes.
    private final ExecutionService.CloseableExecutor postExecutor;
    // Executor for LiveQuery.
    private final ExecutionService.CloseableExecutor queryExecutor;

    private final SharedKeys sharedKeys;

    private final DocumentExpirationStrategy purgeStrategy;

    @GuardedBy("activeProcesses")
    private final Set<ActiveProcess<?>> activeProcesses;

    @GuardedBy("dbLock")
    private final Map<String, DocumentChangeNotifier> docChangeNotifiers;

    @GuardedBy("dbLock")
    private C4Database c4Database;

    @GuardedBy("dbLock")
    private ChangeNotifier<DatabaseChange> dbChangeNotifier;

    @GuardedBy("dbLock")
    private C4DatabaseObserver c4DbObserver;

    private volatile CountDownLatch closeLatch;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a  AbstractDatabase with a given name and database config.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name   The name of the database. May NOT contain capital letters!
     * @param config The database config, Note: null config parameter is not allowed with Android platform
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    protected AbstractDatabase(@NonNull String name, @NonNull DatabaseConfiguration config)
        throws CouchbaseLiteException {
        Preconditions.assertNotEmpty(name, "db name");
        Preconditions.assertNotNull(config, "config");

        CouchbaseLiteInternal.requireInit("Cannot create database");

        // Name:
        this.name = name;

        // Copy configuration
        this.config = new DatabaseConfiguration(config, true);

        this.postExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();
        this.queryExecutor = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

        this.activeProcesses = new HashSet<>();
        this.docChangeNotifiers = new HashMap<>();

        fixHydrogenBug(config, name);

        // Can't open the DB until the file system is set up.
        this.c4Database = openC4Db();
        this.path = c4Database.getPath();

        // Initialize a shared keys:
        this.sharedKeys = new SharedKeys(c4Database);

        this.purgeStrategy = new DocumentExpirationStrategy(this, STANDARD_PURGE_INTERVAL_MS, postExecutor);
        this.purgeStrategy.schedulePurge(INITIAL_PURGE_DELAY_MS);

        // warn if logging has not been turned on
        Log.warn();
    }

    /**
     * Initialize Database with a give C4Database object in the shell mode. The life of the
     * C4Database object will be managed by the caller. This is currently used for creating a
     * Dictionary as an input of the predict() method of the PredictiveModel.
     */
    // !!! This should be a separate class...
    protected AbstractDatabase(long c4dbHandle) {
        CouchbaseLiteInternal.requireInit("Cannot create database");

        this.c4Database = C4Database.getUnmanagedDatabase(c4dbHandle);
        this.path = c4Database.getPath();

        this.name = null;

        this.config = new DatabaseConfiguration();

        this.postExecutor = null;
        this.queryExecutor = null;

        this.activeProcesses = null;
        this.docChangeNotifiers = null;

        this.sharedKeys = null;

        this.purgeStrategy = null;
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
    public String getName() { return this.name; }

    /**
     * Return the database's path. If the database is closed or deleted, null value will be returned.
     *
     * @return the database's path.
     */
    public String getPath() {
        synchronized (getLock()) { return (!isOpen()) ? null : path; }
    }

    /**
     * The number of documents in the database.
     *
     * @return the number of documents in the database, 0 if database is closed.
     */
    public long getCount() {
        synchronized (getLock()) { return (!isOpen()) ? 0L : c4Database.getDocumentCount(); }
    }

    /**
     * Returns a READONLY config object which will throw a runtime exception
     * when any setter methods are called.
     *
     * @return the READONLY copied config object
     */
    @NonNull
    public DatabaseConfiguration getConfig() { return new DatabaseConfiguration(config, true); }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the database, the value returned will be null.
     *
     * @param id the document ID
     * @return the Document object
     */
    public Document getDocument(@NonNull String id) {
        Preconditions.assertNotNull(id, "id");

        synchronized (getLock()) {
            mustBeOpen();
            try { return Document.getDocument((Database) this, id, false); }
            // only 404 - Not Found error throws CouchbaseLiteException
            catch (CouchbaseLiteException ex) { return null; }
        }
    }

    /**
     * Saves a document to the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this method is the same as calling the ave(MutableDocument, ConcurrencyControl)
     * method with LAST_WRITE_WINS concurrency control.
     *
     * @param document The document.
     * @throws CouchbaseLiteException on error
     */
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
     */
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
     */
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
     * Calling this function is the same as calling the delete(Document, ConcurrencyControl)
     * function with LAST_WRITE_WINS concurrency control.
     *
     * @param document The document.
     * @throws CouchbaseLiteException on error
     */
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
     */
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
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException {
        Preconditions.assertNotNull(document, "document");

        if (document.isNewDocument()) {
            throw new CouchbaseLiteException("DocumentNotFound", CBLError.Domain.CBLITE, CBLError.Code.NOT_FOUND);
        }

        synchronized (getLock()) {
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
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");
        synchronized (getLock()) { purgeLocked(id); }
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
     */
    public void setDocumentExpiration(@NonNull String id, Date expiration) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");

        if (purgeStrategy == null) {
            Log.w(DOMAIN, "Attempt to set document expiration without a purge strategy");
            return;
        }

        synchronized (getLock()) {
            try {
                getC4DatabaseLocked().setExpiration(id, (expiration == null) ? 0 : expiration.getTime());
                purgeStrategy.schedulePurge(0);
            }
            catch (LiteCoreException e) {
                throw CouchbaseLiteException.convertException(e);
            }
        }
    }

    /**
     * Returns the expiration time of the document. null will be returned if there is
     * no expiration time set
     *
     * @param id The ID of the Document
     * @return Date a nullable expiration timestamp of the document or null if time not set.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        Preconditions.assertNotNull(id, "id");

        synchronized (getLock()) {
            try {
                if (getC4Document(id) == null) {
                    throw new CouchbaseLiteException(
                        "DocumentNotFound",
                        CBLError.Domain.CBLITE,
                        CBLError.Code.NOT_FOUND);
                }
                final long timestamp = getC4DatabaseLocked().getExpiration(id);
                return (timestamp == 0) ? null : new Date(timestamp);
            }
            catch (LiteCoreException e) {
                throw CouchbaseLiteException.convertException(e);
            }
        }
    }

    /**
     * Runs a group of database operations in a batch. Use this when performing bulk write operations
     * like multiple inserts/updates; it saves the overhead of multiple database commits, greatly
     * improving performance.
     *
     * @param task a task that may terminate abruptly (with an exception)
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public <T extends Exception> void inBatch(@NonNull Fn.TaskThrows<T> task) throws CouchbaseLiteException, T {
        Preconditions.assertNotNull(task, "task");

        synchronized (getLock()) {
            final C4Database db = getC4DatabaseLocked();
            boolean commit = false;
            try {
                db.beginTransaction();
                try {
                    task.run();
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
     */
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
     */
    @NonNull
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull DatabaseChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (getLock()) {
            mustBeOpen();
            return addDatabaseChangeListenerLocked(executor, listener);
        }
    }

    /**
     * Removes the change listener added to the database.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     */
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");

        synchronized (getLock()) {
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
        Preconditions.assertNotNull(id, "id");
        Preconditions.assertNotNull(listener, "listener");

        synchronized (getLock()) {
            mustBeOpen();
            return addDocumentChangeListenerLocked(id, executor, listener);
        }
    }

    /**
     * Closes a database.
     * Closing a database will stop all replicators, live queries and all listeners attached to it.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        Log.v(DOMAIN, "Closing %s at path %s", this, path);
        if (!isOpen()) { return; }
        shutdown(C4Database::closeDb);
    }

    /**
     * Deletes a database.
     * Deleting a database will stop all replicators, live queries and all listeners attached to it.
     * Although attempting to close a closed database is not an error, attempting to delete a closed database is.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        Log.v(DOMAIN, "Deleting %s at path %s", this, path);
        shutdown(C4Database::deleteDb);
    }


    @SuppressWarnings("unchecked")
    @NonNull
    public List<String> getIndexes() throws CouchbaseLiteException {
        synchronized (getLock()) {
            try { return (List<String>) getC4DatabaseLocked().getIndexes().asObject(); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    public void createIndex(@NonNull String name, @NonNull Index idx) throws CouchbaseLiteException {
        Preconditions.assertNotNull(name, "name");
        final AbstractIndex index = (AbstractIndex) Preconditions.assertNotNull(idx, "index");

        synchronized (getLock()) {
            final C4Database c4Db = getC4DatabaseLocked();
            try {
                final String json = JSONUtils.toJSON(index.items()).toString();
                c4Db.createIndex(
                    name,
                    json,
                    index.type().getValue(),
                    index.language(),
                    index.ignoreAccents());
            }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
            catch (JSONException e) { throw new CouchbaseLiteException("Error encoding JSON", e); }
        }
    }

    public void deleteIndex(@NonNull String name) throws CouchbaseLiteException {
        synchronized (getLock()) {
            try { getC4DatabaseLocked().deleteIndex(name); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    public boolean performMaintenance(MaintenanceType type) throws CouchbaseLiteException {
        synchronized (getLock()) {
            try { return getC4DatabaseLocked().performMaintenance(type); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }
    }

    // !!!JSON: NOT YET IMPLEMENTED
    public void saveBlob(@NonNull Blob blob) throws CouchbaseLiteException {
        mustBeOpen();
    }

    // !!!JSON: NOT YET IMPLEMENTED
    @NonNull
    public Blob getBlob(@NonNull Map<String, ?> props) throws CouchbaseLiteException {
        mustBeOpen();
        return new Blob("foo", new byte[] {});
    }

    @NonNull
    @Override
    public String toString() { return "Database{" + ClassUtils.objId(this) + ", name='" + name + "'}"; }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    // This method may return a closed database
    @NonNull
    protected C4Database getC4Database() {
        synchronized (getLock()) { return getC4DatabaseLocked(); }
    }

    @GuardedBy("dbLock")
    @NonNull
    protected C4Database getC4DatabaseLocked() {
        mustBeOpen();
        return c4Database;
    }

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

    // When seizing multiple locks, always seize this lock first.
    @NonNull
    Object getLock() { return dbLock; }

    boolean equalsWithPath(Database other) {
        if (other == null) { return false; }

        final File path = getFilePath();
        final File otherPath = other.getFilePath();

        if ((path == null) && (otherPath == null)) { return true; }

        return (path != null) && path.equals(otherPath);
    }

    @NonNull
    C4BlobStore getBlobStore() throws LiteCoreException {
        synchronized (getLock()) { return getC4DatabaseLocked().getBlobStore(); }
    }

    // Instead of clone()
    Database copy() throws CouchbaseLiteException { return new Database(this.name, this.config); }

    //////// DATABASES:

    // WARNING: this is the state at the time of the call!
    // You must hold dbLock to keep the state from changing.
    boolean isOpen() {
        synchronized (getLock()) { return c4Database != null; }
    }

    // WARNING: guarantees state at the time of the call!
    // You must hold dbLock to keep the state from changing.
    void mustBeOpen() {
        synchronized (getLock()) {
            if (!isOpen()) { throw new IllegalStateException(Log.lookupStandardMessage("DBClosed")); }
        }
    }

    @Nullable
    String getUuid() {
        byte[] uuid = null;
        LiteCoreException err = null;

        synchronized (getLock()) {
            if (!isOpen()) { return null; }

            try { uuid = c4Database.getPublicUUID(); }
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
    File getDbFile() { return (path == null) ? null : new File(path); }

    //////// DOCUMENTS:

    // This method is *NOT* thread safe.
    // If used wo/synchronization, there is a race on the open db
    ListenerToken addActiveLiveQuery(@NonNull LiveQuery query) {
        mustBeOpen();
        registerProcess(new ActiveProcess<LiveQuery>(query) {
            @Override
            public void stop() { query.stop(); }

            @Override
            public boolean isActive() { return !LiveQuery.State.STOPPED.equals(query.getState()); }
        });

        return addChangeListener(query);
    }

    // This method is not thread safe
    void removeActiveLiveQuery(@NonNull LiveQuery query, @NonNull ListenerToken token) {
        removeChangeListener(token);
        unregisterProcess(query);
    }

    C4Query createQuery(@NonNull String json) throws LiteCoreException {
        synchronized (getLock()) { return getC4DatabaseLocked().createQuery(json); }
    }

    C4Document getC4Document(@NonNull String id) throws LiteCoreException {
        synchronized (getLock()) { return getC4DatabaseLocked().get(id); }
    }

    @NonNull
    FLEncoder getSharedFleeceEncoder() {
        synchronized (getLock()) { return getC4DatabaseLocked().getSharedFleeceEncoder(); }
    }

    long getNextDocumentExpiration() {
        synchronized (getLock()) { return getC4DatabaseLocked().nextDocExpiration(); }
    }

    long purgeExpiredDocs() {
        synchronized (getLock()) { return getC4DatabaseLocked().purgeExpiredDocs(); }
    }

    @NonNull
    C4DocumentObserver createDocumentObserver(
        @NonNull ChangeNotifier<?> context,
        @NonNull String docID,
        @NonNull C4DocumentObserverListener listener) {
        synchronized (getLock()) { return getC4DatabaseLocked().createDocumentObserver(docID, listener, context); }
    }

    //////// REPLICATORS:

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    C4Replicator createRemoteReplicator(
        @NonNull Replicator replicator,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDatabaseName,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @Nullable SocketFactory socketFactoryContext,
        int framing)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (getLock()) {
            c4Repl = getC4DatabaseLocked().createRemoteReplicator(
                scheme,
                host,
                port,
                path,
                remoteDatabaseName,
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicator,
                socketFactoryContext,
                framing);
        }
        return c4Repl;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    C4Replicator createLocalReplicator(
        @NonNull Replicator replicator,
        @NonNull Database otherLocalDb,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter)
        throws LiteCoreException {
        final C4Replicator c4Repl;
        synchronized (getLock()) {
            c4Repl = getC4DatabaseLocked().createLocalReplicator(
                otherLocalDb.getC4Database(),
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicator);
        }
        return c4Repl;
    }

    // This method is *NOT* thread safe.
    // If used wo/synchronization, there is a race on the open db
    void addActiveReplicator(AbstractReplicator replicator) {
        mustBeOpen();
        registerProcess(new ActiveProcess<AbstractReplicator>(replicator) {
            @Override
            public void stop() { replicator.stop(); }

            @Override
            public boolean isActive() {
                return !AbstractReplicator.ActivityLevel.STOPPED.equals(replicator.getState());
            }
        });
    }

    void removeActiveReplicator(AbstractReplicator replicator) { unregisterProcess(replicator); }

    //////// RESOLVING REPLICATED CONFLICTS:

    void resolveReplicationConflict(
        @Nullable ConflictResolver resolver,
        @NonNull String docId,
        @NonNull Fn.Consumer<CouchbaseLiteException> callback) {
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
                catch (CBLInternalException e) {
                    // This error occurs when a resolver that starts after this one
                    // fixes the conflict before this one does.  When this one attempts
                    // to save, it gets a conflict error and retries.  During the retry,
                    // it cannot find a conflicting revision and throws this error.
                    // The other resolver did the right thing, so there is no reason
                    // to report an error.
                    if (e.getCode() != CBLInternalException.FAILED_SELECTING_CONFLICTING_REVISION) {
                        err = new CouchbaseLiteException("Conflict resolution failed", e);
                    }
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
            synchronized (getLock()) { getC4DatabaseLocked().setCookie(uri, setCookieHeader); }
        }
        catch (LiteCoreException e) { Log.w(DOMAIN, "Cannot save cookie for " + uri, e); }
    }

    @Nullable
    String getCookies(@NonNull URI uri) {
        try {
            synchronized (getLock()) { return getC4DatabaseLocked().getCookies(uri); }
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

    abstract byte[] getEncryptionKey();

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:

    @GuardedBy("dbLock")
    private void beginTransaction() throws CouchbaseLiteException {
        try { getC4DatabaseLocked().beginTransaction(); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @GuardedBy("dbLock")
    private void endTransaction(boolean commit) throws CouchbaseLiteException {
        try { getC4DatabaseLocked().endTransaction(commit); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    private C4Database openC4Db() throws CouchbaseLiteException {
        final File dbFile = getDatabaseFile(new File(config.getDirectory()), this.name);
        Log.v(DOMAIN, "Opening %s at path %s", this, dbFile.getPath());

        try {
            return C4Database.getDatabase(
                dbFile.getPath(),
                getDatabaseFlags(),
                null,
                C4Constants.DocumentVersioning.REVISION_TREES,
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

    private int getDatabaseFlags() { return DEFAULT_DATABASE_FLAGS; }

    //////// DOCUMENTS:

    // --- Database changes:

    @GuardedBy("dbLock")
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

    @GuardedBy("dbLock")
    private void removeDatabaseChangeListenerLocked(@NonNull ListenerToken token) {
        if (dbChangeNotifier.removeChangeListener(token) == 0) {
            freeC4DbObserver();
            dbChangeNotifier = null;
        }
    }

    // --- Document changes:

    @GuardedBy("dbLock")
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

    @GuardedBy("dbLock")
    private void removeDocumentChangeListenerLocked(@NonNull ChangeListenerToken<?> token) {
        final String docID = (String) token.getKey();
        if (docChangeNotifiers.containsKey(docID)) {
            final DocumentChangeNotifier notifier = docChangeNotifiers.get(docID);
            if ((notifier != null) && (notifier.removeChangeListener(token) == 0)) { docChangeNotifiers.remove(docID); }
        }
    }

    @GuardedBy("dbLock")
    private void registerC4DbObserver() {
        if (!isOpen()) { return; }
        c4DbObserver = c4Database.createDatabaseObserver(
            (observer, context) -> scheduleOnPostNotificationExecutor(this::postDatabaseChanged, 0),
            this);
    }

    private void postDatabaseChanged() {
        synchronized (getLock()) {
            if (!isOpen() || (c4DbObserver == null)) { return; }

            boolean external = false;
            int nChanges;
            List<String> docIDs = new ArrayList<>();
            do {
                // Read changes in batches of kMaxChanges:
                final C4DatabaseChange[] c4DbChanges = c4DbObserver.getChanges(MAX_CHANGES);
                nChanges = (c4DbChanges == null) ? 0 : c4DbChanges.length;
                final boolean newExternal = (nChanges > 0) && c4DbChanges[0].isExternal();
                if ((!docIDs.isEmpty()) && ((nChanges <= 0) || (external != newExternal) || (docIDs.size() > 1000))) {
                    dbChangeNotifier.postChange(new DatabaseChange((Database) this, docIDs));
                    docIDs = new ArrayList<>();
                }

                external = newExternal;
                for (int i = 0; i < nChanges; i++) { docIDs.add(c4DbChanges[i].getDocID()); }
            }
            while (nChanges > 0);
        }
    }

    @GuardedBy("dbLock")
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
        throws CouchbaseLiteException, CBLInternalException {
        final Document localDoc;
        final Document remoteDoc;
        synchronized (getLock()) {
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

        synchronized (getLock()) {
            boolean commit = false;
            beginTransaction();
            try {
                saveResolvedDocument(resolvedDoc, localDoc, remoteDoc);
                commit = true;
            }
            finally { endTransaction(commit); }
        }
    }

    private Document getConflictingRevision(@NonNull String docID)
        throws CouchbaseLiteException, CBLInternalException {
        final Document remoteDoc = Document.getDocument((Database) this, docID);
        try {
            if (!remoteDoc.selectConflictingRevision()) {
                final String msg = "Unable to select conflicting revision for doc '" + docID + "'. Skipping.";
                Log.w(DOMAIN, msg);
                throw new CBLInternalException(CBLInternalException.FAILED_SELECTING_CONFLICTING_REVISION, msg);
            }
        }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }

        return remoteDoc;
    }

    private Document resolveConflict(
        @NonNull ConflictResolver resolver,
        @NonNull String docID,
        @NonNull Document localDoc,
        @NonNull Document remoteDoc)
        throws CouchbaseLiteException {
        final Conflict conflict
            = new Conflict(localDoc.isDeleted() ? null : localDoc, remoteDoc.isDeleted() ? null : remoteDoc);

        Log.v(
            DOMAIN,
            "Resolving doc '%s' (local=%s and remote=%s) with resolver %s",
            docID,
            localDoc.getRevisionID(),
            remoteDoc.getRevisionID(),
            resolver);

        final Document doc;
        try { doc = resolver.resolve(conflict); }
        catch (Exception err) {
            final String msg = String.format(ERROR_RESOLVER_FAILED, docID, err.getLocalizedMessage());
            Log.w(DOMAIN, msg, err);
            throw new CouchbaseLiteException(msg, err, CBLError.Domain.CBLITE, CBLError.Code.UNEXPECTED_ERROR);
        }

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
    @GuardedBy("dbLock")
    @SuppressWarnings("PMD.NPathComplexity")
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

        byte[] mergedBodyBytes = null;
        try {
            // Unless the remote revision is being used as-is, we need a new revision:
            if (resolvedDoc != remoteDoc) {
                if ((resolvedDoc != null) && !resolvedDoc.isDeleted()) {
                    try (FLSliceResult mergedBody = resolvedDoc.encode()) { mergedBodyBytes = mergedBody.getBuf(); }
                }
                else {
                    mergedFlags |= C4Constants.RevisionFlags.DELETED;
                    try (FLEncoder enc = getSharedFleeceEncoder()) {
                        enc.writeValue(new HashMap<>());
                        try (FLSliceResult mergedBody = enc.finish2()) { mergedBodyBytes = mergedBody.getBuf(); }
                    }
                }
            }

            // Ask LiteCore to do the resolution:
            final C4Document rawDoc = Preconditions.assertNotNull(localDoc.getC4doc(), "raw doc is null");
            // The remote branch has to win so that the doc revision history matches the server's.
            rawDoc.resolveConflict(remoteDoc.getRevisionID(), localDoc.getRevisionID(), mergedBodyBytes, mergedFlags);
            rawDoc.save(0);

            Log.v(DOMAIN, "Conflict resolved as doc '%s' rev %s", rawDoc.getDocID(), rawDoc.getRevID());
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
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
            synchronized (getLock()) { oldDoc = Document.getDocument((Database) this, document.getId()); }

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

        synchronized (getLock()) {
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

    @GuardedBy("dbLock")
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
    @GuardedBy("dbLock")
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
                if (C4Document.dictContainsBlobs(body, sharedKeys.getFLSharedKeys())) {
                    revFlags |= C4Constants.RevisionFlags.HAS_ATTACHMENTS;
                }
            }

            // Save to database:
            C4Document c4Doc = (base != null) ? base : document.getC4doc();

            c4Doc = (c4Doc != null)
                ? c4Doc.update(body, revFlags)
                : getC4DatabaseLocked().create(document.getId(), body, revFlags);

            document.replaceC4Document(c4Doc);
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
        finally {
            if (body != null) { body.close(); }
        }
    }

    @GuardedBy("dbLock")
    private void purgeLocked(@NonNull String id) throws CouchbaseLiteException {
        boolean commit = false;
        beginTransaction();
        try {
            getC4DatabaseLocked().purgeDoc(id);
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
        Log.v(DOMAIN, "Active processes(%s): %d", getName(), activeProcessCount);
        if (activeProcessCount <= 0) { closeLatch.countDown(); }
    }

    private void shutdown(Fn.ConsumerThrows<C4Database, LiteCoreException> onShut) throws CouchbaseLiteException {
        final C4Database c4Db;
        synchronized (getLock()) {
            c4Db = getC4DatabaseLocked();
            c4Database = null;

            // don't do any of this stuff in shell mode
            if (name == null) { return; }

            purgeStrategy.cancelPurges();

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

        synchronized (getLock()) {
            try { onShut.accept(c4Db); }
            catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
        }

        shutdownExecutors(postExecutor, queryExecutor, EXECUTOR_CLOSE_MAX_WAIT_SECS);
    }

    @GuardedBy("dbLock")
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
    private void shutdownActiveProcesses(Collection<ActiveProcess<?>> processes) {
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
    private void fixHydrogenBug(@NonNull DatabaseConfiguration config, @NonNull String dbName)
        throws CouchbaseLiteException {
        // This is the real default directory
        final String defaultDirPath = CouchbaseLiteInternal.getRootDir().getAbsolutePath();

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
        final File twoDotEightDb = getDatabaseFile(twoDotEightDefaultDir, dbName);
        try { Database.copy(twoDotEightDb, dbName, config); }
        catch (CouchbaseLiteException e) {
            // Per review: If the copy fails, delete the partial DB
            // and throw an exception.  This is a poison pill.
            // The db can only be opened by explicitly specifying 2.8.0 directory.
            try { FileUtils.eraseFileOrDir(getDatabaseFile(defaultDir, dbName)); }
            catch (Exception ignore) { }
            throw e;
        }
    }
}
