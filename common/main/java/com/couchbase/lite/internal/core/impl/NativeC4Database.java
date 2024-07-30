package com.couchbase.lite.internal.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.fleece.FLSliceResult;


@SuppressWarnings("PMD.TooManyMethods")
public class NativeC4Database implements C4Database.NativeImpl {

    @Override
    public long nOpen(@NonNull String parentDir, @NonNull String name, int flags, int algorithm, byte[] encryptionKey)
        throws LiteCoreException {
        return open(parentDir, name, flags, algorithm, encryptionKey);
    }

    @Override
    public void nClose(long db) throws LiteCoreException { close(db); }

    @Override
    public void nFree(long db) { free(db); }

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
    public void nDeleteNamed(@NonNull String name, @NonNull String dir) throws LiteCoreException {
        deleteNamed(name, dir);
    }

    @NonNull
    @Override
    public byte[] nGetPublicUUID(long db) throws LiteCoreException { return getPublicUUID(db); }

    @NonNull
    @Override
    public byte[] nGetPrivateUUID(long db) throws LiteCoreException { return getPrivateUUID(db); }

    @Override
    public void nBeginTransaction(long db) throws LiteCoreException { beginTransaction(db); }

    @Override
    public void nEndTransaction(long db, boolean commit) throws LiteCoreException { endTransaction(db, commit); }

    @Override
    public boolean nMaintenance(long db, int type) throws LiteCoreException { return maintenance(db, type); }

    @Override
    public void nRekey(long db, int keyType, byte[] newKey) throws LiteCoreException { rekey(db, keyType, newKey); }

    @Override
    public void nSetCookie(long db, String url, String setCookieHeader, boolean acceptParentDomain)
        throws LiteCoreException {
        setCookie(db, url, setCookieHeader, acceptParentDomain);
    }

    @NonNull
    @Override
    public String nGetCookies(long db, @NonNull String url) throws LiteCoreException { return getCookies(db, url); }

    @Override
    public long nGetSharedFleeceEncoder(long db) { return getSharedFleeceEncoder(db); }

    @NonNull
    @Override
    public FLSliceResult nEncodeJSON(long db, @NonNull byte[] jsonData) throws LiteCoreException {
        return encodeJSON(db, jsonData);
    }

    @Override
    public long nGetFLSharedKeys(long db) { return getFLSharedKeys(db); }

    @NonNull
    @Override
    public Set<String> nGetScopeNames(long peer) throws LiteCoreException { return getScopeNames(peer); }

    @Override
    public boolean nHasScope(long peer, @NonNull String scope) { return hasScope(peer, scope); }

    @NonNull
    @Override
    public Set<String> nGetCollectionNames(long peer, @NonNull String scope) throws LiteCoreException {
        return getCollectionNames(peer, scope);
    }

    @Override
    public void nDeleteCollection(long peer, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        deleteCollection(peer, scope, collection);
    }

    @Override
    public int nGetFlags(long peer) throws LiteCoreException { return getFlags(peer); }


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

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static native void close(long db) throws LiteCoreException;

    static native void free(long db);

// - File System

    @Nullable
    private static native String getPath(long db);

    private static native void copy(
        String sourcePath,
        String parentDir,
        String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    private static native void delete(long db) throws LiteCoreException;

    private static native void deleteNamed(@NonNull String name, @NonNull String dir) throws LiteCoreException;

    // - UUID

    @NonNull
    private static native byte[] getPublicUUID(long db) throws LiteCoreException;

    @NonNull
    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    // - Transactions

    private static native void beginTransaction(long db) throws LiteCoreException;

    private static native void endTransaction(long db, boolean commit) throws LiteCoreException;

    // - Maintenance

    private static native void rekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

    private static native boolean maintenance(long db, int type) throws LiteCoreException;

    // - Cookie Store

    private static native void setCookie(long db, String url, String setCookieHeader, boolean acceptParentDomain)
        throws LiteCoreException;

    @NonNull
    private static native String getCookies(long db, @NonNull String url) throws LiteCoreException;

    // - Utilities

    private static native long getSharedFleeceEncoder(long db);

    private static native long getFLSharedKeys(long db);

    @NonNull
    private static native FLSliceResult encodeJSON(long db, @NonNull byte[] jsonData) throws LiteCoreException;

    private static native int getFlags(long db);

    // - Scopes and Collections

    // returns Set<String> of scope names
    @NonNull
    private static native Set<String> getScopeNames(long peer) throws LiteCoreException;

    // returns true if the db has a scope with the passed name
    private static native boolean hasScope(long peer, @NonNull String scope);

    // returns Set<String> of scope names
    @NonNull
    private static native Set<String> getCollectionNames(long peer, @NonNull String scope) throws LiteCoreException;

    // deletes the named collection
    private static native void deleteCollection(long peer, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException;
}
