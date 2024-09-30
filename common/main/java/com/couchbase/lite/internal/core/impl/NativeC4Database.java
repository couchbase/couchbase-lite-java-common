package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Database;


@SuppressWarnings("PMD.TooManyMethods")
public final class NativeC4Database implements C4Database.NativeImpl {

    @Override
    public long nOpen(@NonNull String parentDir, @NonNull String name, int flags, int algorithm, byte[] encryptionKey)
        throws LiteCoreException {
        return open(parentDir, name, flags, algorithm, encryptionKey);
    }

    @GuardedBy("dbLock")
    @Override
    public void nClose(long db) throws LiteCoreException { close(db); }

    @Override
    public void nFree(long db) { free(db); }

    // - File System

    @Override
    @Nullable
    public String nGetPath(long db) { return getPath(db); }

    @Override
    public void nCopy(String sourcePath, String parentDir, String name, int flags, int algorithm, byte[] encryptionKey)
        throws LiteCoreException {
        copy(sourcePath, parentDir, name, flags, algorithm, encryptionKey);
    }

    @Override
    public void nDelete(long db) throws LiteCoreException { delete(db); }

    @Override
    public void nDeleteNamed(@NonNull String parentDir, @NonNull String name) throws LiteCoreException {
        deleteNamed(parentDir, name);
    }

    // - UUID

    @GuardedBy("dbLock")
    @NonNull
    @Override
    public byte[] nGetPublicUUID(long db) throws LiteCoreException { return getPublicUUID(db); }

    // - Transactions

    @GuardedBy("dbLock")
    @Override
    public void nBeginTransaction(long db) throws LiteCoreException { beginTransaction(db); }

    @GuardedBy("dbLock")
    @Override
    public void nEndTransaction(long db, boolean commit) throws LiteCoreException { endTransaction(db, commit); }

    // - Maintenance

    @Override
    public void nRekey(long db, int keyType, byte[] newKey) throws LiteCoreException { rekey(db, keyType, newKey); }

    @GuardedBy("dbLock")
    @Override
    public boolean nMaintenance(long db, int type) throws LiteCoreException { return maintenance(db, type); }

    // - Cookie Store

    @GuardedBy("dbLock")
    @Override
    public void nSetCookie(long db, String url, String setCookieHeader, boolean acceptParentDomain)
        throws LiteCoreException {
        setCookie(db, url, setCookieHeader, acceptParentDomain);
    }

    @GuardedBy("dbLock")
    @Nullable
    @Override
    public String nGetCookies(long db, @NonNull String url) throws LiteCoreException { return getCookies(db, url); }

    // - Utilities

    @GuardedBy("dbLock")
    @Override
    public long nGetSharedFleeceEncoder(long db) { return getSharedFleeceEncoder(db); }

    @GuardedBy("dbLock")
    @Override
    public long nGetFLSharedKeys(long db) { return getFLSharedKeys(db); }

    @GuardedBy("dbLock")
    @Override
    public boolean nDocContainsBlobs(long dictPtr, long dictSize, long sharedKeys) {
        return docContainsBlobs(dictPtr, dictSize, sharedKeys);
    }

    // - Scopes and Collections

    @GuardedBy("dbLock")
    @NonNull
    @Override
    public Set<String> nGetScopeNames(long peer) throws LiteCoreException { return getScopeNames(peer); }

    @GuardedBy("dbLock")
    @Override
    public boolean nHasScope(long peer, @NonNull String scope) { return hasScope(peer, scope); }

    @GuardedBy("dbLock")
    @NonNull
    @Override
    public Set<String> nGetCollectionNames(long peer, @NonNull String scope) throws LiteCoreException {
        return getCollectionNames(peer, scope);
    }

    @GuardedBy("dbLock")
    @Override
    public void nDeleteCollection(long peer, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        deleteCollection(peer, scope, collection);
    }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    //-------------------------------------------------------------------------

    // - Lifecycle

    static native long open(
        @NonNull String parentDir,
        @NonNull String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @GuardedBy("dbLock")
    private static native void close(long peer) throws LiteCoreException;

    static native void free(long peer);

// - File System

    @Nullable
    private static native String getPath(long peer);

    private static native void copy(
        String sourcePath,
        String parentDir,
        String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    private static native void delete(long peer) throws LiteCoreException;

    private static native void deleteNamed(@NonNull String parentDir, @NonNull String name) throws LiteCoreException;

    // - UUID

    @GuardedBy("dbLock")
    @NonNull
    private static native byte[] getPublicUUID(long peer) throws LiteCoreException;

    // - Transactions

    @GuardedBy("dbLock")
    private static native void beginTransaction(long peer) throws LiteCoreException;

    @GuardedBy("dbLock")
    private static native void endTransaction(long peer, boolean commit) throws LiteCoreException;

    // - Maintenance

    private static native void rekey(long peer, int keyType, byte[] newKey) throws LiteCoreException;

    @GuardedBy("dbLock")
    private static native boolean maintenance(long peer, int type) throws LiteCoreException;

    // - Cookie Store

    @GuardedBy("dbLock")
    private static native void setCookie(long peer, String url, String setCookieHeader, boolean acceptParentDomain)
        throws LiteCoreException;

    @GuardedBy("dbLock")
    @Nullable
    private static native String getCookies(long peer, @NonNull String url) throws LiteCoreException;

    // - Utilities

    @GuardedBy("dbLock")
    private static native long getSharedFleeceEncoder(long peer);

    @GuardedBy("dbLock")
    private static native long getFLSharedKeys(long peer);

    @GuardedBy("dbLock")
    private static native boolean docContainsBlobs(long peer, long dictSize, long sharedKeys);

    // - Scopes and Collections

    @GuardedBy("dbLock")
    @NonNull
    private static native Set<String> getScopeNames(long peer) throws LiteCoreException;

    @GuardedBy("dbLock")
    private static native boolean hasScope(long peer, @NonNull String scope);

    @GuardedBy("dbLock")
    @NonNull
    private static native Set<String> getCollectionNames(long peer, @NonNull String scope) throws LiteCoreException;

    @GuardedBy("dbLock")
    private static native void deleteCollection(long peer, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException;
}
