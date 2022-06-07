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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.MaintenanceType;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.impl.NativeC4Database;
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
    public interface NativeImpl {
        long nOpen(
            @NonNull String parentDir,
            @NonNull String name,
            int flags,
            int algorithm,
            byte[] encryptionKey)
            throws LiteCoreException;


        void nClose(long db) throws LiteCoreException;

        void nFree(long db);

        // - File System

        @Nullable
        String nGetPath(long db);

        void nCopy(
            String sourcePath,
            String parentDir,
            String name,
            int flags,
            int algorithm,
            byte[] encryptionKey)
            throws LiteCoreException;

        void nDelete(long db) throws LiteCoreException;

        void nDeleteNamed(@NonNull String name, @NonNull String dir) throws LiteCoreException;

        // - UUID

        @NonNull
        byte[] nGetPublicUUID(long db) throws LiteCoreException;

        @NonNull
        byte[] nGetPrivateUUID(long db) throws LiteCoreException;

        // - Transactions

        void nBeginTransaction(long db) throws LiteCoreException;

        void nEndTransaction(long db, boolean commit) throws LiteCoreException;

        // - Maintenance

        boolean nMaintenance(long db, int type) throws LiteCoreException;

        void nRekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

        // - Cookie Store

        void nSetCookie(long db, String url, String setCookieHeader) throws LiteCoreException;

        @NonNull
        String nGetCookies(long db, @NonNull String url) throws LiteCoreException;

        // - Utilities

        long nGetSharedFleeceEncoder(long db);

        long nEncodeJSON(long db, byte[] jsonData) throws LiteCoreException;

        long nGetFLSharedKeys(long db);

        // - Scopes and Collections

        // returns Set<String> of scope names
        @Nullable
        Set<String> nGetScopeNames(long peer);

        // returns true if the db has a scope with the passed name
        boolean nHasScope(long peer, @NonNull String scope);

        // returns Set<String> of collection names
        @Nullable
        Set<String> nGetCollectionNames(long peer, @NonNull String scope);

        // deletes the named collection
        void nDeleteCollection(long peer, @NonNull String scope, @NonNull String collection)
            throws LiteCoreException;

        // - Documents

        // !!! DEPRECATED:
        //  Delete these methods when the corresponding Database methods proxy to the default collection

        long nGetDocumentCount(long db);

        void nSetDocumentExpiration(long db, String docID, long timestamp)
            throws LiteCoreException;

        long nGetDocumentExpiration(long db, String docID) throws LiteCoreException;

        void nPurgeDoc(long db, String id) throws LiteCoreException;

        long nGetIndexesInfo(long db) throws LiteCoreException;

        void nCreateIndex(
            long db,
            @NonNull String name,
            @NonNull String queryExpressions,
            int queryLanguage,
            int indexType,
            @Nullable String language,
            boolean ignoreDiacritics)
            throws LiteCoreException;

        void nDeleteIndex(long db, @NonNull String name) throws LiteCoreException;

        // !!! end deprecation

        // - Testing

        long nGetLastSequence(long db);
    }

    @VisibleForTesting
    public static final String DB_EXTENSION = ".cblite2";

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Database();

    // unmanaged: the native code will free it
    static final class UnmanagedC4Database extends C4Database {
        UnmanagedC4Database(@NonNull NativeImpl impl, long peer) { super(impl, peer); }

        @Override
        public void close() { releasePeer(); }
    }


    // managed: Java code is responsible for freeing it
    static final class ManagedC4Database extends C4Database {
        @NonNull
        private final NativeImpl impl;

        ManagedC4Database(@NonNull NativeImpl impl, long peer) {
            super(impl, peer);
            this.impl = impl;
        }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, impl::nFree); }
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

        NATIVE_IMPL.nCopy(sourcePath, parentDir, name, flags, algorithm, encryptionKey);
    }

    // This will throw domain = 0, code = 0 if called for a non-existent name/dir pair
    public static void deleteNamedDb(@NonNull String directory, @NonNull String name) throws LiteCoreException {
        NATIVE_IMPL.nDeleteNamed(name, directory);
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
    public static C4Database getUnmanagedDatabase(long peer) { return new UnmanagedC4Database(NATIVE_IMPL, peer); }

    // managed: Java code is responsible for freeing it
    @NonNull
    public static C4Database getDatabase(
        @NonNull String parentDirPath,
        @NonNull String name,
        int flags,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        return getDatabase(NATIVE_IMPL, parentDirPath, name, flags, algorithm, encryptionKey);
    }

    @VisibleForTesting
    @NonNull
    static C4Database getDatabase(
        @NonNull NativeImpl impl,
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

        return new ManagedC4Database(
            impl,
            impl.nOpen(
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
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    protected C4Database(@NonNull NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
    }

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
        impl.nClose(getPeer());
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

    // This is subtle: see closeDb above.
    public void deleteDb() throws LiteCoreException {
        impl.nDelete(getPeer());
        close();
    }

    // - UUID

    @NonNull
    public byte[] getPublicUUID() throws LiteCoreException { return impl.nGetPublicUUID(getPeer()); }

    // - Blobs

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return C4BlobStore.getUnmanagedBlobStore(getPeer()); }

    // - Transactions

    public void beginTransaction() throws LiteCoreException { impl.nBeginTransaction(getPeer()); }

    public void endTransaction(boolean commit) throws LiteCoreException { impl.nEndTransaction(getPeer(), commit); }

    // - Maintenance

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException { impl.nRekey(getPeer(), keyType, newKey); }

    public boolean performMaintenance(MaintenanceType type) throws LiteCoreException {
        return impl.nMaintenance(
            getPeer(),
            Preconditions.assertNotNull(MAINTENANCE_TYPE_MAP.get(type), "Unrecognized maintenance type: " + type));
    }

    // - Cookies

    public void setCookie(@NonNull URI uri, @NonNull String setCookieHeader) throws LiteCoreException {
        impl.nSetCookie(getPeer(), uri.toString(), setCookieHeader);
    }

    @Nullable
    public String getCookies(@NonNull URI uri) throws LiteCoreException {
        return impl.nGetCookies(getPeer(), uri.toString());
    }

    // - Utilities

    // This must be called holding both the document and the database locks!
    @NonNull
    public FLEncoder getSharedFleeceEncoder() {
        return FLEncoder.getUnmanagedEncoder(impl.nGetSharedFleeceEncoder(getPeer()));
    }

    @NonNull
    public FLSharedKeys getFLSharedKeys() { return new FLSharedKeys(impl.nGetFLSharedKeys(getPeer())); }

    // - Scopes and Collections

    @Nullable
    public Set<String> getScopeNames() { return impl.nGetScopeNames(getPeer()); }

    public boolean hasScope(@NonNull String scope) { return impl.nHasScope(getPeer(), scope); }

    @Nullable
    public Set<String> getCollectionNames(@NonNull String scope) { return impl.nGetCollectionNames(getPeer(), scope); }

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

    public void deleteCollection(@NonNull String scopeName, @NonNull String collectionName)
        throws CouchbaseLiteException {
        try { impl.nDeleteCollection(getPeer(), scopeName, collectionName); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
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
    // Delete these methods when the corresponding Database methods proxy to the default collection

    // - Documents

    public long getDocumentCount() { return impl.nGetDocumentCount(getPeer()); }

    @NonNull
    public C4Document getDocument(@NonNull String docID) throws LiteCoreException {
        return C4Document.create(this, docID, true);
    }

    @VisibleForTesting
    @NonNull
    public C4Document getDocument(@NonNull String docID, boolean mustExist) throws LiteCoreException {
        return C4Document.create(this, docID, mustExist);
    }

    @NonNull
    public C4Document createDocument(@NonNull String docID, @Nullable FLSliceResult body, int flags)
        throws LiteCoreException {
        return C4Document.create(this, docID, body, flags);
    }

    public void setDocumentExpiration(@NonNull String docID, long timestamp) throws LiteCoreException {
        impl.nSetDocumentExpiration(getPeer(), docID, timestamp);
    }

    public long getDocumentExpiration(@NonNull String docID) throws LiteCoreException {
        return impl.nGetDocumentExpiration(getPeer(), docID);
    }

    public void purgeDoc(String docID) throws LiteCoreException { impl.nPurgeDoc(getPeer(), docID); }

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
            (peer) -> impl.nCreateIndex(
                peer,
                name,
                queryExpression,
                queryLanguage.getValue(),
                indexType.getValue(),
                language,
                ignoreDiacritics));
    }

    @NonNull
    public FLValue getIndexesInfo() throws LiteCoreException {
        final FLValue info = withPeerOrNull((peer) -> new FLValue(impl.nGetIndexesInfo(peer)));
        return Preconditions.assertNotNull(info, "index info");
    }

    public void deleteIndex(String name) throws LiteCoreException {
        withPeerThrows((peer) -> impl.nDeleteIndex(peer, name));
    }

    // !!! end deprecation


    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    long getHandle() { return getPeer(); }

    @VisibleForTesting
    long getLastSequence() { return impl.nGetLastSequence(getPeer()); }

    @NonNull
    @VisibleForTesting
    byte[] getPrivateUUID() throws LiteCoreException { return impl.nGetPrivateUUID(getPeer()); }

    @VisibleForTesting
    @NonNull
    FLSliceResult encodeJSON(@NonNull String data) throws LiteCoreException {
        return FLSliceResult.getManagedSliceResult(impl.nEncodeJSON(getPeer(), data.getBytes(StandardCharsets.UTF_8)));
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @Nullable
    private File getDbFile() {
        final File file = dbFile.get();
        if (file != null) { return file; }

        final String path = impl.nGetPath(getPeer());
        if (path == null) { return null; }

        try { dbFile.compareAndSet(null, new File(path).getCanonicalFile()); }
        catch (IOException ignore) { }

        return dbFile.get();
    }
}
