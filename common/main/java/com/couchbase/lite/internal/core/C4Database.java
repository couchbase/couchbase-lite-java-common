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
package com.couchbase.lite.internal.core;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.MaintenanceType;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.Preconditions;


@SuppressWarnings({
    "PMD.GodClass",
    "PMD.ExcessivePublicCount",
    "PMD.TooManyMethods",
    "PMD.ExcessiveParameterList",
    "PMD.CyclomaticComplexity"})
public abstract class C4Database extends C4NativePeer {

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
    private static final Map<MaintenanceType, Integer> MAINTENANCE_TYPE_MAP;
    static {
        final Map<MaintenanceType, Integer> m = new HashMap<>();
        m.put(MaintenanceType.COMPACT, 0);
        m.put(MaintenanceType.REINDEX, 1);
        m.put(MaintenanceType.INTEGRITY_CHECK, 2);
        MAINTENANCE_TYPE_MAP = Collections.unmodifiableMap(m);
    }
    public static void copyDb(
        String sourcePath,
        String destinationPath,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException {
        copy(sourcePath, destinationPath, flags, storageEngine, versioning, algorithm, encryptionKey);
    }

    public static void deleteDbAtPath(String path) throws LiteCoreException { deleteAtPath(path); }

    static void rawFreeDocument(long rawDoc) throws LiteCoreException { rawFree(rawDoc); }


    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    // unmanaged: someone else owns it
    public static C4Database getUnmanagedDatabase(long peer) { return new UnmanagedC4Database(peer); }

    // managed: Java code is responsible for freeing it
    public static C4Database getDatabase(
        String path,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException {
        return new ManagedC4Database(open(path, flags, storageEngine, versioning, algorithm, encryptionKey));
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected C4Database(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // - Lifecycle

    // The meaning of "close" changes at this level.
    // C4Database is AutoCloseable: this call frees it.
    // Database is not AutoCloseable.  In it, "close" means close the database.
    @Override
    public abstract void close();

    // This is subtle
    // The call to close() will fail horribly if the db is currently in a transaction.
    // On the other hand, the call to close(peer) with throw an exception if the db is in a transaction.
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

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException { rekey(getPeer(), keyType, newKey); }

    // - Accessors

    @Nullable
    public String getPath() { return getPath(getPeer()); }

    public long getDocumentCount() { return getDocumentCount(getPeer()); }

    public long nextDocExpiration() { return nextDocExpiration(getPeer()); }

    public long purgeExpiredDocs() { return purgeExpiredDocs(getPeer()); }

    public void purgeDoc(String docID) throws LiteCoreException { purgeDoc(getPeer(), docID); }

    public byte[] getPublicUUID() throws LiteCoreException { return getPublicUUID(getPeer()); }

    // - Transactions

    public void beginTransaction() throws LiteCoreException { beginTransaction(getPeer()); }

    public void endTransaction(boolean commit) throws LiteCoreException { endTransaction(getPeer(), commit); }

    // c4Document+Fleece.h

    // - Fleece-related
    // This must be called holding both the document and the database locks!
    public FLEncoder getSharedFleeceEncoder() {
        return FLEncoder.getUnmanagedEncoder(getSharedFleeceEncoder(getPeer()));
    }

    ////////////////////////////////
    // C4Document
    ////////////////////////////////

    public C4Document get(String docID) throws LiteCoreException { return new C4Document(getPeer(), docID, true); }

    // - Purging and Expiration

    public void setExpiration(String docID, long timestamp) throws LiteCoreException {
        C4Document.setExpiration(getPeer(), docID, timestamp);
    }

    public long getExpiration(String docID) throws LiteCoreException {
        return C4Document.getExpiration(getPeer(), docID);
    }

    @NonNull
    public C4Document create(String docID, FLSliceResult body, int flags) throws LiteCoreException {
        return new C4Document(C4Document.create2(getPeer(), docID, body != null ? body.getHandle() : 0, flags));
    }

    ////////////////////////////////////////////////////////////////
    // C4DatabaseObserver/C4DocumentObserver
    ////////////////////////////////////////////////////////////////

    @NonNull
    public C4DatabaseObserver createDatabaseObserver(C4DatabaseObserverListener listener, Object context) {
        return C4DatabaseObserver.newObserver(getPeer(), listener, context);
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(
        String docID,
        C4DocumentObserverListener listener,
        Object context) {
        return C4DocumentObserver.newObserver(getPeer(), docID, listener, context);
    }

    ////////////////////////////////
    // C4BlobStore
    ////////////////////////////////

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return C4BlobStore.getUnmanagedBlobStore(getPeer()); }

    ////////////////////////////////
    // C4Query
    ////////////////////////////////

    public C4Query createQuery(String expression) throws LiteCoreException {
        return new C4Query(getPeer(), expression);
    }

    public void createIndex(
        String name,
        String expressionsJSON,
        int indexType,
        String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        C4Query.createIndex(getPeer(), name, expressionsJSON, indexType, language, ignoreDiacritics);
    }

    public void deleteIndex(String name) throws LiteCoreException { C4Query.deleteIndex(getPeer(), name); }

    public FLValue getIndexes() throws LiteCoreException { return new FLValue(C4Query.getIndexes(getPeer())); }

    public boolean performMaintenance(MaintenanceType type) throws LiteCoreException {
        return maintenance(
            getPeer(),
            Preconditions.assertNotNull(MAINTENANCE_TYPE_MAP.get(type), "Unrecognized maintenance type: " + type));
    }

    ////////////////////////////////
    // C4Replicator
    ////////////////////////////////

    @NonNull
    public C4Replicator createRemoteReplicator(
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
        @NonNull AbstractReplicator replicatorContext,
        @Nullable SocketFactory socketFactoryContext,
        int framing)
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
            options,
            listener,
            pushFilter,
            pullFilter,
            replicatorContext,
            socketFactoryContext,
            framing);
    }

    @NonNull
    public C4Replicator createLocalReplicator(
        @NonNull C4Database otherLocalDB,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext)
        throws LiteCoreException {
        return C4Replicator.createLocalReplicator(
            getPeer(),
            otherLocalDB,
            push,
            pull,
            options,
            listener,
            pushFilter,
            pullFilter,
            replicatorContext);
    }

    @NonNull
    public C4Replicator createTargetReplicator(
        @NonNull C4Socket openSocket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {
        return C4Replicator.createTargetReplicator(
            getPeer(),
            openSocket,
            push,
            pull,
            options,
            listener,
            replicatorContext);
    }

    ////////////////////////////////
    // Cookie Store
    ////////////////////////////////

    public void setCookie(@NonNull URI uri, @NonNull String setCookieHeader) throws LiteCoreException {
        setCookie(getPeer(), uri.toString(), setCookieHeader);
    }

    @Nullable
    public String getCookies(@NonNull URI uri) throws LiteCoreException {
        return getCookies(getPeer(), uri.toString());
    }

    @VisibleForTesting
    public C4Document get(String docID, boolean mustExist) throws LiteCoreException {
        return new C4Document(getPeer(), docID, mustExist);
    }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    long getHandle() { return getPeer(); }

    @VisibleForTesting
    C4Document create(String docID, byte[] body, int revisionFlags) throws LiteCoreException {
        return new C4Document(C4Document.create(getPeer(), docID, body, revisionFlags));
    }

    @VisibleForTesting
    void compact() throws LiteCoreException { compact(getPeer()); }

    @VisibleForTesting
    long getLastSequence() { return getLastSequence(getPeer()); }

    @VisibleForTesting
    int getMaxRevTreeDepth() { return getMaxRevTreeDepth(getPeer()); }

    @VisibleForTesting
    void setMaxRevTreeDepth(int maxRevTreeDepth) { setMaxRevTreeDepth(getPeer(), maxRevTreeDepth); }

    @VisibleForTesting
    byte[] getPrivateUUID() throws LiteCoreException { return getPrivateUUID(getPeer()); }

    @VisibleForTesting
    FLSharedKeys getFLSharedKeys() { return new FLSharedKeys(getFLSharedKeys(getPeer())); }

    @VisibleForTesting
    FLSliceResult encodeJSON(String data) throws LiteCoreException {
        return FLSliceResult.getManagedSliceResult(encodeJSON(getPeer(), data.getBytes(StandardCharsets.UTF_8)));
    }

    @VisibleForTesting
    C4Document getBySequence(long sequence) throws LiteCoreException { return new C4Document(getPeer(), sequence); }

    @VisibleForTesting
    public C4Document put(
        byte[] body,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(C4Document.put(
            getPeer(),
            body,
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @VisibleForTesting
    C4Document put(
        FLSliceResult body, // C4Slice*
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(C4Document.put2(
            getPeer(),
            body.getHandle(),
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @VisibleForTesting
    void rawPut(String storeName, String key, String meta, byte[] body) throws LiteCoreException {
        rawPut(getPeer(), storeName, key, meta, body);
    }

    @VisibleForTesting
    C4RawDocument rawGet(String storeName, String docID) throws LiteCoreException {
        return new C4RawDocument(rawGet(getPeer(), storeName, docID));
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    // - Lifecycle
    static native long open(
        String path,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    static native void free(long db);

    private static native long rawGet(long db, String storeName, String docID) throws LiteCoreException;

    private static native void rawPut(
        long db,
        String storeName,
        String key,
        String meta,
        byte[] body)
        throws LiteCoreException;

    private static native void copy(
        String sourcePath,
        String destinationPath,
        int flags,
        String storageEngine,
        int versioning,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static native void close(long db) throws LiteCoreException;

    private static native void delete(long db) throws LiteCoreException;

    private static native void deleteAtPath(String path) throws LiteCoreException;

    private static native void rekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

    // - Accessors


    @Nullable
    private static native String getPath(long db);

    private static native long getDocumentCount(long db);

    private static native long getLastSequence(long db);

    private static native long nextDocExpiration(long db);

    private static native long purgeExpiredDocs(long db);

    private static native void purgeDoc(long db, String id) throws LiteCoreException;

    private static native int getMaxRevTreeDepth(long db);

    private static native void setMaxRevTreeDepth(long db, int maxRevTreeDepth);

    private static native byte[] getPublicUUID(long db) throws LiteCoreException;

    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    // - Compaction

    private static native void compact(long db) throws LiteCoreException;

    // - Transactions

    private static native void beginTransaction(long db) throws LiteCoreException;

    private static native void endTransaction(long db, boolean commit) throws LiteCoreException;

    // - Raw Documents (i.e. info or _local)

    private static native void rawFree(long rawDoc) throws LiteCoreException;

    // - Cookie Store

    private static native void setCookie(long db, String url, String setCookieHeader) throws LiteCoreException;

    private static native String getCookies(long db, String url) throws LiteCoreException;

    ////////////////////////////////
    // c4Document+Fleece.h
    ////////////////////////////////

    // - Fleece-related

    private static native long getSharedFleeceEncoder(long db);

    private static native long encodeJSON(long db, byte[] jsonData) throws LiteCoreException;

    private static native long getFLSharedKeys(long db);

    private static native boolean maintenance(long db, int type) throws LiteCoreException;
}
