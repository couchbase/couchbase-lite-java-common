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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.Collection;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.MaintenanceType;
import com.couchbase.lite.Scope;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLArray;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.replicator.ReplicatorListener;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


@SuppressWarnings({
     "PMD.UnusedPrivateMethod",
    "PMD.TooManyMethods",
    "PMD.ExcessivePublicCount",
    "PMD.ExcessiveParameterList",
    "PMD.CyclomaticComplexity"})
public abstract class C4Database extends C4NativePeer {

    @VisibleForTesting
    public static final String DB_EXTENSION = ".cblite2";

    // unmanaged: the native code will free it
    static final class UnmanagedC4Database extends C4Database {
        UnmanagedC4Database(long peer) { super(peer); }

        @Override
        public void close() { releasePeer(); }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedC4Database extends C4Database {
        ManagedC4Database(long peer) { super(peer); }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4Database::free); }
    }

    // These enum values must match the ones in DataFile::MaintenanceType
    @NonNull
    private static final Map<MaintenanceType, Integer> MAINTENANCE_TYPE_MAP;
    static {
        final Map<MaintenanceType, Integer> m = new HashMap<>();
        m.put(MaintenanceType.COMPACT, 0);
        m.put(MaintenanceType.REINDEX, 1);
        m.put(MaintenanceType.INTEGRITY_CHECK, 2);
        m.put(MaintenanceType.OPTIMIZE, 3);
        m.put(MaintenanceType.FULL_OPTIMIZE, 4);
        MAINTENANCE_TYPE_MAP = Collections.unmodifiableMap(m);
    }
    public static void copyDb(
        @NonNull String sourcePath,
        @NonNull String parentDir,
        @NonNull String name,
        int flags,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        if (sourcePath.charAt(sourcePath.length() - 1) != File.separatorChar) { sourcePath += File.separator; }

        if (parentDir.charAt(parentDir.length() - 1) != File.separatorChar) { parentDir += File.separator; }

        copy(sourcePath, parentDir, name, flags, algorithm, encryptionKey);
    }

    // This will throw domain = 0, code = 0 if called for a non-existent name/dir pair
    public static void deleteNamedDb(@NonNull String directory, @NonNull String name) throws LiteCoreException {
        deleteNamed(name, directory);
    }

    @NonNull
    public static File getDatabaseFile(@NonNull File directory, @NonNull String name) {
        return new File(directory, name + DB_EXTENSION);
    }


    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    // unmanaged: someone else owns it
    @NonNull
    public static C4Database getUnmanagedDatabase(long peer) { return new UnmanagedC4Database(peer); }

    // managed: Java code is responsible for freeing it
    @NonNull
    public static C4Database getDatabase(
        @NonNull String parentDirPath,
        @NonNull String name,
        int flags,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {

        // Stupid LiteCore will throw a total hissy fit if we pass
        // it something that it decides isn't a directory.
        boolean pathOk = false;
        try {
            final File parentDir = new File(parentDirPath);
            parentDirPath = parentDir.getCanonicalPath();
            pathOk = (parentDir.exists()) && (parentDir.isDirectory());
        }
        catch (IOException ignore) { }

        if (!pathOk) {
            throw new LiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.WRONG_FORMAT,
                "Parent directory does not exist or is not a directory: " + parentDirPath);
        }

        return new ManagedC4Database(open(
            parentDirPath,
            name,
            flags,
            algorithm,
            encryptionKey));
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    final AtomicReference<File> dbFile = new AtomicReference<>();

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected C4Database(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "C4Database" + super.toString(); }

    // - Lifecycle

    // The meaning of "close" changes at this level.
    // C4Database is AutoCloseable: this call frees it.
    // Database is not AutoCloseable.  In it, "close" means close the database.
    // Close behavior is delegated to one of the two subtypes
    @Override
    public abstract void close();

    // This is subtle
    // The call to close() will fail horribly if the db is currently in a transaction.
    // On the other hand, the call to close(peer) will throw an exception if the db is in a transaction.
    // That means that close() will never be called and the failure will be reported normally.
    // The finalizer will backstop this rare case, so that the Database doesn't leak.
    public void closeDb() throws LiteCoreException {
        close(getPeer());
        close();
    }

    // This is subtle: see above.
    public void deleteDb() throws LiteCoreException {
        delete(getPeer());
        close();
    }

    // - File System

    // this is the full name of the database directory, e.g., /foo/bar.cblite
    @Nullable
    public String getDbPath() {
        final File file = getDbFile();
        return (file == null) ? null : file.getPath() + File.separator;
    }

    @Nullable
    public String getDbDirectory() {
        final File file = getDbFile();
        return (file == null) ? null : file.getParent();
    }

    @Nullable
    public String getDbFileName() {
        final File file = getDbFile();
        return (file == null) ? null : file.getName();
    }

    @Nullable
    public String getDbName() {
        String dbFileName = getDbFileName();
        if (dbFileName == null) { return null; }

        if (dbFileName.endsWith(DB_EXTENSION)) {
            dbFileName = dbFileName.substring(0, dbFileName.length() - DB_EXTENSION.length());
        }

        return dbFileName;
    }

    // - UUID

    @NonNull
    public byte[] getPublicUUID() throws LiteCoreException { return getPublicUUID(getPeer()); }

    // - Blobs

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return C4BlobStore.getUnmanagedBlobStore(getPeer()); }

    // - Transactions

    public void beginTransaction() throws LiteCoreException { beginTransaction(getPeer()); }

    public void endTransaction(boolean commit) throws LiteCoreException { endTransaction(getPeer(), commit); }

    // - Fleece

    // This must be called holding both the document and the database locks!
    @NonNull
    public FLEncoder getSharedFleeceEncoder() {
        return FLEncoder.getUnmanagedEncoder(getSharedFleeceEncoder(getPeer()));
    }

    // - Maintenance

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException { rekey(getPeer(), keyType, newKey); }

    public boolean performMaintenance(MaintenanceType type) throws LiteCoreException {
        return maintenance(
            getPeer(),
            Preconditions.assertNotNull(MAINTENANCE_TYPE_MAP.get(type), "Unrecognized maintenance type: " + type));
    }

    // - Cookies

    public void setCookie(@NonNull URI uri, @NonNull String setCookieHeader) throws LiteCoreException {
        setCookie(getPeer(), uri.toString(), setCookieHeader);
    }

    @Nullable
    public String getCookies(@NonNull URI uri) throws LiteCoreException {
        return getCookies(getPeer(), uri.toString());
    }

    @NonNull
    public FLSharedKeys getFLSharedKeys() { return new FLSharedKeys(getFLSharedKeys(getPeer())); }

    // - Scopes and Collections

    @NonNull
    public Set<String> getScopes() {
        final Set<String> scopes = new HashSet<>();
        final long arrayRef = getScopeNames(getPeer());
        if (arrayRef == 0) {
            // !!! delete this once the native call works.
            scopes.add(Scope.DEFAULT_NAME);
            return scopes;
        }
        final FLArray flScopes = FLArray.create(arrayRef);
        final long n = flScopes.count();
        if (n <= 0) { return scopes; }
        for (int i = 0; i < n; i++) { scopes.add(flScopes.get(i).toStr()); }

        return scopes;
    }

    public boolean hasScope(@NonNull String scope) { return hasScope(getPeer(), scope); }

    @NonNull
    public Set<String> getCollections(@NonNull String scope) {
        final Set<String> collections = new HashSet<>();
        final long arrayRef = getCollectionNames(getPeer(), scope);
        if (arrayRef == 0) {
            // !!! delete this once the native call works.
            collections.add(Collection.DEFAULT_NAME);
            return collections;
        }
        final FLArray flCollections = FLArray.create(arrayRef);
        final long n = flCollections.count();
        if (n <= 0) { return collections; }
        for (int i = 0; i < n; i++) { collections.add(flCollections.get(i).toStr()); }

        return collections;
    }

    @Nullable
    public final C4Collection getDefaultCollection() { return C4Collection.getDefault(this); }

    @NonNull
    public C4Collection getCollection(@NonNull String scopeName, @NonNull String collectionName) {
        return C4Collection.get(this, scopeName, collectionName);
    }

    @NonNull
    public C4Collection addCollection(@NonNull String scopeName, @NonNull String collectionName)
        throws LiteCoreException {
        return C4Collection.create(this, scopeName, collectionName);
    }

    public void deleteCollection(@NonNull String scopeName, @NonNull String collectionName) {
        deleteCollection(getPeer(), scopeName, collectionName);
    }

    // - Replicators

    @SuppressWarnings("CheckFunctionalParameters")
    @NonNull
    public C4Replicator createRemoteReplicator(
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
        @NonNull AbstractReplicator replicator,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        return C4Replicator.createRemoteReplicator(
            getPeer(),
            scheme,
            host,
            port,
            path,
            remoteDatabaseName,
            push,
            pull,
            framing, options,
            listener,
            replicator,
            pushFilter,
            pullFilter,
            socketFactory
        );
    }

    @SuppressWarnings("CheckFunctionalParameters")
    @NonNull
    public C4Replicator createLocalReplicator(
        @NonNull C4Database targetDb,
        int push,
        int pull,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener,
        @NonNull AbstractReplicator replicator,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter)
        throws LiteCoreException {
        return C4Replicator.createLocalReplicator(
            getPeer(),
            targetDb,
            push,
            pull,
            options,
            listener,
            replicator,
            pushFilter,
            pullFilter
        );
    }

    @NonNull
    public C4Replicator createTargetReplicator(
        @NonNull C4Socket c4Socket,
        int push,
        int pull,
        @Nullable byte[] options,
        @NonNull ReplicatorListener listener)
        throws LiteCoreException {
        return C4Replicator.createMessageEndpointReplicator(
            getPeer(),
            c4Socket,
            push,
            pull,
            options,
            listener);
    }

    // !!! DEPRECATED
    // Delete these methods when the corresponding Java methods proxy to the default collection

    @VisibleForTesting
    @NonNull
    public C4Document getDocument(@NonNull String docID, boolean mustExist) throws LiteCoreException {
        return C4Document.create(this, docID, mustExist);
    }

    // - Documents

    public long getDocumentCount() { return getDocumentCount(getPeer()); }

    @NonNull
    public C4Document createDocument(@NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return C4Document.create(this, docID, body, flags);
    }

    @NonNull
    public C4Document getDocument(@NonNull String docID) throws LiteCoreException {
        return C4Document.create(this, docID, true);
    }

    public void setDocumentExpiration(@NonNull String docID, long timestamp) throws LiteCoreException {
        setDocumentExpiration(getPeer(), docID, timestamp);
    }

    public long getDocumentExpiration(@NonNull String docID) throws LiteCoreException {
        return getDocumentExpiration(getPeer(), docID);
    }

    public void purgeDoc(String docID) throws LiteCoreException { purgeDoc(getPeer(), docID); }

    // - Queries

    @NonNull
    public C4Query createJsonQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, AbstractIndex.QueryLanguage.JSON, expression);
    }

    @NonNull
    public C4Query createN1qlQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, AbstractIndex.QueryLanguage.N1QL, expression);
    }

    // - Observers

    @NonNull
    public C4DatabaseObserver createDatabaseObserver(@NonNull Runnable listener) {
        return C4DatabaseObserver.newObserver(getPeer(), listener);
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(@NonNull String docID, @NonNull Runnable listener) {
        return C4DocumentObserver.newObserver(getPeer(), docID, listener);
    }

    // - Indexes

    @NonNull
    public FLValue getIndexesInfo() throws LiteCoreException {
        final FLValue info = withPeerOrNull((peer) -> new FLValue(getIndexesInfo(peer)));
        return Preconditions.assertNotNull(info, "index info");
    }

    public void createIndex(
        @NonNull String name,
        @NonNull String queryExpression,
        @NonNull AbstractIndex.QueryLanguage queryLanguage,
        @NonNull AbstractIndex.IndexType indexType,
        @Nullable String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        Log.d(LogDomain.QUERY, "creating index: %s", queryExpression);
        withPeerThrows(
            (peer) -> createIndex(
                peer,
                name,
                queryExpression,
                queryLanguage.getValue(),
                indexType.getValue(),
                language,
                ignoreDiacritics));
    }

    public void deleteIndex(String name) throws LiteCoreException {
        withPeerThrows((peer) -> deleteIndex(peer, name));
    }

    // !!! end deprecation


    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    long getHandle() { return getPeer(); }

    @VisibleForTesting
    long getLastSequence() { return getLastSequence(getPeer()); }

    @NonNull
    @VisibleForTesting
    byte[] getPrivateUUID() throws LiteCoreException { return getPrivateUUID(getPeer()); }

    @VisibleForTesting
    @NonNull
    FLSliceResult encodeJSON(@NonNull String data) throws LiteCoreException {
        return FLSliceResult.getManagedSliceResult(encodeJSON(getPeer(), data.getBytes(StandardCharsets.UTF_8)));
    }

    @VisibleForTesting
    @NonNull
    C4Document getDocumentBySequence(long sequence) throws LiteCoreException {
        return C4Document.create(this, sequence);
    }

    @VisibleForTesting
    void rawPut(String storeName, String key, String meta, byte[] body) throws LiteCoreException {
        rawPut(getPeer(), storeName, key, meta, body);
    }

    @VisibleForTesting
    @NonNull
    C4RawDocument rawGet(@NonNull String storeName, @NonNull String docID) throws LiteCoreException {
        return new C4RawDocument(rawGet(getPeer(), storeName, docID));
    }

    @VisibleForTesting
    @NonNull
    public C4Document putDocument(
        @NonNull byte[] body,
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return C4Document.create(
            this,
            body,
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID);
    }

    @NonNull
    @VisibleForTesting
    C4Document putDocument(
        @NonNull FLSliceResult body, // C4Slice*
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return C4Document.create(
            this,
            body,
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID);
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @Nullable
    private File getDbFile() {
        final File file = dbFile.get();
        if (file != null) { return file; }

        final String path = getPath(getPeer());
        if (path == null) { return null; }

        try { dbFile.compareAndSet(null, new File(path).getCanonicalFile()); }
        catch (IOException ignore) { }

        return dbFile.get();
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    // - Lifecycle

    static native long open(
        @NonNull String parentDir,
        @NonNull String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    private static native void copy(
        String sourcePath,
        String parentDir,
        String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static native void close(long db) throws LiteCoreException;

    private static native void delete(long db) throws LiteCoreException;

    private static native void deleteNamed(@NonNull String name, @NonNull String dir) throws LiteCoreException;

    static native void free(long db);

    // - UUID

    @NonNull
    private static native byte[] getPublicUUID(long db) throws LiteCoreException;

    @NonNull
    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    // - File System

    @Nullable
    private static native String getPath(long db);

    private static native long getLastSequence(long db);

    // - Transactions

    private static native void beginTransaction(long db) throws LiteCoreException;

    private static native void endTransaction(long db, boolean commit) throws LiteCoreException;

    // - Fleece-related

    private static native long getSharedFleeceEncoder(long db);

    private static native long encodeJSON(long db, byte[] jsonData) throws LiteCoreException;

    // - Maintenance

    private static native boolean maintenance(long db, int type) throws LiteCoreException;

    private static native void rekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

    // - Cookie Store

    private static native void setCookie(long db, String url, String setCookieHeader) throws LiteCoreException;

    @NonNull
    private static native String getCookies(long db, @NonNull String url) throws LiteCoreException;

    private static native long getFLSharedKeys(long db);

    // - Scopes and Collections

    // returns FLArray of scope names
    private static native long getScopeNames(long peer);

    // returns FLArray of scope names
    private static native boolean hasScope(long peer, @NonNull String scope);

    // returns FLArray of scope names
    private static native long getCollectionNames(long peer, @NonNull String scope);

    // returns true if db has collection
    private static native boolean hasCollection(long peer, @NonNull String scope, @NonNull String collection);

    // returns true if db has collection
    private static native boolean deleteCollection(long peer, @NonNull String scope, @NonNull String collection);

    // - Raw Documents (i.e. info or _local)

    private static native long rawGet(long db, String storeName, String docID) throws LiteCoreException;

    private static native void rawPut(
        long db,
        String storeName,
        String key,
        String meta,
        byte[] body)
        throws LiteCoreException;

    // !!! DEPRECATED:
    //  Delete these methods when the corresponding Java methods proxy to the default collection

    private static native long getDocumentCount(long db);

    private static native void setDocumentExpiration(long db, String docID, long timestamp) throws LiteCoreException;

    private static native long getDocumentExpiration(long db, String docID) throws LiteCoreException;

    private static native void purgeDoc(long db, String id) throws LiteCoreException;

    private static native long getIndexesInfo(long db) throws LiteCoreException;

    private static native void createIndex(
        long db,
        @NonNull String name,
        @NonNull String queryExpressions,
        int queryLanguage,
        int indexType,
        @Nullable String language,
        boolean ignoreDiacritics)
        throws LiteCoreException;

    private static native void deleteIndex(long db, @NonNull String name) throws LiteCoreException;

    // !!! end deprecation
}
