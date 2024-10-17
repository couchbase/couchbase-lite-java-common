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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.Collection;
import com.couchbase.lite.CollectionConfiguration;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.MaintenanceType;
import com.couchbase.lite.ReplicatorType;
import com.couchbase.lite.internal.QueryLanguage;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.impl.NativeC4Database;
import com.couchbase.lite.internal.core.peers.LockManager;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.utils.Preconditions;


@SuppressWarnings("PMD.ExcessivePublicCount")
public abstract class C4Database extends C4Peer {
    @VisibleForTesting
    public static final String DB_EXTENSION = ".cblite2";

    public interface NativeImpl {
        long nOpen(
            @NonNull String parentDir,
            @NonNull String name,
            long flags,
            int algorithm,
            byte[] encryptionKey)
            throws LiteCoreException;


        @GuardedBy("dbLock")
        void nClose(long db) throws LiteCoreException;

        void nFree(long db);

        // - File System

        @Nullable
        String nGetPath(long db);

        void nCopy(
            String sourcePath,
            String parentDir,
            String name,
            long flags,
            int algorithm,
            byte[] encryptionKey)
            throws LiteCoreException;

        void nDelete(long db) throws LiteCoreException;

        void nDeleteNamed(@NonNull String parentDir, @NonNull String name) throws LiteCoreException;

        // - UUID

        @GuardedBy("dbLock")
        @NonNull
        byte[] nGetPublicUUID(long db) throws LiteCoreException;

        // - Transactions

        @GuardedBy("dbLock")
        void nBeginTransaction(long db) throws LiteCoreException;

        @GuardedBy("dbLock")
        void nEndTransaction(long db, boolean commit) throws LiteCoreException;

        // - Maintenance

        @GuardedBy("dbLock")
        void nRekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

        @GuardedBy("dbLock")
        boolean nMaintenance(long db, int type) throws LiteCoreException;

        // - Cookie Store

        @GuardedBy("dbLock")
        void nSetCookie(long db, String url, String setCookieHeader, boolean acceptParentDomain)
            throws LiteCoreException;

        @GuardedBy("dbLock")
        @Nullable
        String nGetCookies(long db, @NonNull String url) throws LiteCoreException;

        // - Utilities

        @GuardedBy("dbLock")
        long nGetSharedFleeceEncoder(long db);

        @GuardedBy("dbLock")
        long nGetFLSharedKeys(long db);

        @GuardedBy("dbLock")
        boolean nDocContainsBlobs(long dictPtr, long dictSize, long sharedKeys);

        // - Scopes and Collections

        @GuardedBy("dbLock")
        @NonNull
        Set<String> nGetScopeNames(long peer) throws LiteCoreException;

        @GuardedBy("dbLock")
        boolean nHasScope(long peer, @NonNull String scope);

        @GuardedBy("dbLock")
        @NonNull
        Set<String> nGetCollectionNames(long peer, @NonNull String scope) throws LiteCoreException;

        @GuardedBy("dbLock")
        void nDeleteCollection(long peer, @NonNull String scope, @NonNull String collection)
            throws LiteCoreException;
    }

    // unmanaged: the native code will free it
    static final class UnmanagedC4Database extends C4Database {
        UnmanagedC4Database(@NonNull NativeImpl impl, long peer) {
            super(impl, peer, LockManager.INSTANCE.getLock(peer), "shell", null);
        }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedC4Database extends C4Database {
        ManagedC4Database(@NonNull NativeImpl impl, long peer, @NonNull Object lock, @NonNull String name) {
            super(
                impl,
                peer,
                lock,
                name,
                releasePeer -> {
                    synchronized (lock) { impl.nFree(peer); }
                });
        }
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4Database();

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

    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    // unmanaged: someone else owns it
    @NonNull
    public static C4Database getUnmanagedDatabase(long peer) {
        return new UnmanagedC4Database(NATIVE_IMPL, peer);
    }

    // managed: Java code is responsible for freeing it
    @NonNull
    public static C4Database getDatabase(
        @NonNull String parentDirPath,
        @NonNull String name,
        boolean isFullSync,
        boolean isMMapEnabled,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        long flags = C4Constants.DatabaseFlags.CREATE;
        if (isFullSync) { flags |= C4Constants.DatabaseFlags.DISC_FULL_SYNC; }
        if (!isMMapEnabled) { flags |= C4Constants.DatabaseFlags.DISABLE_MMAP; }
        return getDatabase(NATIVE_IMPL, parentDirPath, name, flags, algorithm, encryptionKey);
    }

    @VisibleForTesting
    @NonNull
    static C4Database getDatabase(@NonNull String parentDirPath, @NonNull String name)
        throws LiteCoreException {
        return getDatabase(parentDirPath, name, C4Constants.DatabaseFlags.CREATE);
    }

    @VisibleForTesting
    @NonNull
    static C4Database getDatabase(@NonNull String parentDirPath, @NonNull String name, long flags)
        throws LiteCoreException {
        return getDatabase(NATIVE_IMPL, parentDirPath, name, flags);
    }

    @VisibleForTesting
    @NonNull
    static C4Database getDatabase(
        @NonNull NativeImpl impl,
        @NonNull String parentDirPath,
        @NonNull String name,
        long flags)
        throws LiteCoreException {
        return getDatabase(impl, parentDirPath, name, flags, C4Constants.EncryptionAlgorithm.NONE, null);
    }

    @VisibleForTesting
    @NonNull
    static C4Database getDatabase(
        @NonNull NativeImpl impl,
        @NonNull String parentDirPath,
        @NonNull String name,
        long flags,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        Preconditions.assertUInt32(flags, "flags");

        // LiteCore will throw a total hissy fit if we pass it something that it decides isn't a directory.
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

        final long peer = impl.nOpen(
            parentDirPath,
            name,
            flags | C4Constants.DatabaseFlags.VERSION_VECTORS,
            algorithm,
            encryptionKey);

        return new ManagedC4Database(impl, peer, LockManager.INSTANCE.getLock(peer), name);
    }

    //-------------------------------------------------------------------------
    // Utility Methods
    //-------------------------------------------------------------------------

    public static void copyDb(
        @NonNull String sourcePath,
        @NonNull String parentDir,
        @NonNull String name,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        copyDb(NATIVE_IMPL, sourcePath, parentDir, name, algorithm, encryptionKey);
    }

    @VisibleForTesting
    static void copyDb(
        @NonNull String sourcePath,
        @NonNull String parentDir,
        @NonNull String name)
        throws LiteCoreException {
        copyDb(NATIVE_IMPL, sourcePath, parentDir, name, C4Constants.EncryptionAlgorithm.NONE, null);
    }

    @VisibleForTesting
    static void copyDb(
        @NonNull NativeImpl impl,
        @NonNull String sourcePath,
        @NonNull String parentDir,
        @NonNull String name,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        if (sourcePath.charAt(sourcePath.length() - 1) != File.separatorChar) { sourcePath += File.separator; }

        if (parentDir.charAt(parentDir.length() - 1) != File.separatorChar) { parentDir += File.separator; }

        impl.nCopy(
            sourcePath,
            parentDir,
            name,
            C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.VERSION_VECTORS,
            algorithm,
            encryptionKey);
    }

    // This will throw domain = 0, code = 0 if called for a non-existent name/dir pair
    public static void deleteNamedDb(@NonNull String parentDir, @NonNull String name) throws LiteCoreException {
        NATIVE_IMPL.nDeleteNamed(parentDir, name);
    }

    @NonNull
    public static File getDatabaseFile(@NonNull File directory, @NonNull String name) {
        return new File(directory, name + DB_EXTENSION);
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;
    @NonNull
    private final String name;

    // We need to hold a reference to this for this objects lifetime,
    // to be sure that everyone else gets the same lock.
    @SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField"})
    @NonNull
    private final Object lock;

    @NonNull
    private final AtomicReference<File> dbFile = new AtomicReference<>();

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    protected C4Database(
        @NonNull NativeImpl impl,
        long peer,
        @NonNull Object lock,
        @NonNull String name,
        @Nullable PeerCleaner cleaner) {
        super(peer, cleaner);
        this.name = name;
        this.impl = impl;
        this.lock = lock;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    public Object getLock() { return lock; }

    @NonNull
    @Override
    public String toString() { return name + "@" + super.toString(); }

    // - Lifecycle

    // The meaning of "close" changes at this level.
    // Database is not AutoCloseable.  In it, "close" means close the database.
    // C4Database is AutoCloseable (inherited from C4Peer). Here, close means free the native peer
    // and clean up as necessary.
    // This method is only here to hold this comment.
    @SuppressWarnings("PMD.UselessOverridingMethod")
    @Override
    public void close() { super.close(); }

    // This is subtle
    // The call to close() will fail horribly if the db is currently in a transaction.
    // On the other hand, the call to close(peer) will throw an exception if the db is in a transaction.
    // That means that close() will never be called and the failure will be reported normally.
    // Even if the close failes, the cleaner will backstop this rare case so that the Database doesn't leak.
    public void closeDb() throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nClose(peer); }
        });
        close();
    }

    // - File System

    // this is the conical name of the database directory, e.g., /foo/bar.cblite/
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
        voidWithPeerOrThrow(impl::nDelete);
        close();
    }

    // - UUID

    @NonNull
    public byte[] getPublicUUID() throws LiteCoreException {
        return withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nGetPublicUUID(peer); }
        });
    }

    // - Blobs

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return withPeerOrThrow(C4BlobStore::create); }

    public boolean docContainsBlobs(FLSliceResult body, FLSharedKeys keys) {
        synchronized (lock) { return impl.nDocContainsBlobs(body.getBase(), body.getSize(), keys.getPeer()); }
    }

    // - Transactions

    public void beginTransaction() throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nBeginTransaction(peer); }
        });
    }

    public void endTransaction(boolean commit) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nEndTransaction(peer, commit); }
        });
    }

    // - Maintenance

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nRekey(peer, keyType, newKey); }
        });
    }

    public boolean performMaintenance(MaintenanceType type) throws LiteCoreException {
        final Integer mTyp = MAINTENANCE_TYPE_MAP.get(type);
        if (mTyp == null) { throw new IllegalArgumentException("Unknown maintenance type: " + type); }
        return withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nMaintenance(peer, mTyp); }
        });
    }

    // - Cookies

    public void setCookie(@NonNull URI uri, @NonNull String setCookieHeader, boolean acceptParentDomain)
        throws LiteCoreException {
        final String uriStr = uri.toString();
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nSetCookie(peer, uriStr, setCookieHeader, acceptParentDomain); }
        });
    }


    @Nullable
    public String getCookies(@NonNull URI uri) throws LiteCoreException {
        final String uriStr = uri.toString();
        return withPeerOrNull(peer -> {
            synchronized (lock) { return impl.nGetCookies(peer, uriStr); }
        });
    }

    // - Utilities

    @NonNull
    public FLEncoder getSharedFleeceEncoder() {
        return FLEncoder.getSharedEncoder(withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nGetSharedFleeceEncoder(peer); }
        }));
    }

    @NonNull
    public FLSharedKeys getFLSharedKeys() {
        return new FLSharedKeys(withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nGetFLSharedKeys(peer); }
        }));
    }

    // - Scopes and Collections

    @NonNull
    public Set<String> getScopeNames() throws LiteCoreException {
        return withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nGetScopeNames(peer); }
        });
    }

    public boolean hasScope(@NonNull String scope) {
        return withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nHasScope(peer, scope); }
        });
    }

    @NonNull
    public Set<String> getCollectionNames(@NonNull String scope) throws LiteCoreException {
        return withPeerOrThrow(peer -> {
            synchronized (lock) { return impl.nGetCollectionNames(peer, scope); }
        });
    }

    public void deleteCollection(@NonNull String scopeName, @NonNull String collectionName) throws LiteCoreException {
        voidWithPeerOrThrow(peer -> {
            synchronized (lock) { impl.nDeleteCollection(peer, scopeName, collectionName); }
        });
    }

    @NonNull
    public C4Collection addCollection(@NonNull String scopeName, @NonNull String collectionName)
        throws LiteCoreException {
        return C4Collection.create(this, scopeName, collectionName);
    }

    @Nullable
    public C4Collection getCollection(@NonNull String scopeName, @NonNull String collectionName)
        throws LiteCoreException {
        return C4Collection.get(this, scopeName, collectionName);
    }

    @NonNull
    public final C4Collection getDefaultCollection() throws LiteCoreException { return C4Collection.getDefault(this); }

    // - Replicators

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    public C4Replicator createRemoteReplicator(
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
        @NonNull AbstractReplicator replicator,
        @Nullable SocketFactory socketFactory)
        throws LiteCoreException {
        return withPeerOrThrow(peer ->
            C4Replicator.createRemoteReplicator(
                collections,
                peer,
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
                socketFactory));
    }

    @NonNull
    public C4Replicator createLocalReplicator(
        @NonNull Map<Collection, CollectionConfiguration> collections,
        @NonNull C4Database targetDb,
        @NonNull ReplicatorType type,
        boolean continuous,
        @Nullable Map<String, Object> options,
        @NonNull C4Replicator.StatusListener statusListener,
        @NonNull C4Replicator.DocEndsListener docEndsListener,
        @NonNull AbstractReplicator replicator)
        throws LiteCoreException {
        return withPeerOrThrow(peer ->
            C4Replicator.createLocalReplicator(
                collections,
                peer,
                targetDb,
                type,
                continuous,
                options,
                statusListener,
                docEndsListener,
                replicator));
    }

    @NonNull
    public C4Replicator createMessageEndpointReplicator(
        @NonNull Set<Collection> collections,
        @NonNull C4Socket c4Socket,
        @Nullable Map<String, Object> options,
        @NonNull C4Replicator.StatusListener statusListener)
        throws LiteCoreException {
        return withPeerOrThrow(peer ->
            C4Replicator.createMessageEndpointReplicator(
                collections,
                peer,
                c4Socket,
                options,
                statusListener));
    }

// - Queries

    @NonNull
    public C4Query createJsonQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, QueryLanguage.JSON, expression);
    }

    @NonNull
    public C4Query createN1qlQuery(@NonNull String expression) throws LiteCoreException {
        return C4Query.create(this, QueryLanguage.N1QL, expression);
    }

//-------------------------------------------------------------------------
// Private methods
//-------------------------------------------------------------------------

    @Nullable
    private File getDbFile() {
        final File file = dbFile.get();
        if (file != null) { return file; }

        final String path = withPeerOrNull(impl::nGetPath);
        if (path == null) { return null; }

        try { dbFile.compareAndSet(null, new File(path).getCanonicalFile()); }
        catch (IOException ignore) { }

        return dbFile.get();
    }
}
