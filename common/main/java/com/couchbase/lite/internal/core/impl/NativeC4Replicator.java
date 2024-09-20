//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core.impl;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.ReplicationCollection;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public final class NativeC4Replicator implements C4Replicator.NativeImpl {
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Override
    public long nCreate(
        @NonNull String id,
        @NonNull ReplicationCollection[] collections,
        long db,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDbName,
        int framing,
        boolean push,
        boolean pull,
        boolean continuous,
        @Nullable byte[] options,
        long replicatorToken,
        long socketFactoryToken)
        throws LiteCoreException {
        return create(
            id,
            collections,
            db,
            scheme,
            host,
            port,
            path,
            remoteDbName,
            framing,
            push,
            pull,
            continuous,
            options,
            replicatorToken,
            socketFactoryToken
        );
    }

    @Override
    public long nCreateLocal(
        @NonNull String id,
        @NonNull ReplicationCollection[] collections,
        long db,
        long targetDb,
        boolean push,
        boolean pull,
        boolean continuous,
        @Nullable byte[] options,
        long replicatorToken)
        throws LiteCoreException {
        return createLocal(
            id,
            collections,
            db,
            targetDb,
            push,
            pull,
            continuous,
            options,
            replicatorToken);
    }

    @Override
    public long nCreateWithSocket(
        @NonNull String id,
        @NonNull ReplicationCollection[] collections,
        long db,
        long openSocket,
        @Nullable byte[] options,
        long replicatorToken)
        throws LiteCoreException {
        return createWithSocket(id, collections, db, openSocket, options, replicatorToken);
    }

    @Override
    public void nFree(long peer) { free(peer); }

    @Override
    public void nStart(long peer, boolean restart) { start(peer, restart); }

    @Override
    public void nStop(long peer) { stop(peer); }

    @Override
    public void nSetOptions(long peer, @Nullable byte[] options) { setOptions(peer, options); }

    @NonNull
    @Override
    public C4ReplicatorStatus nGetStatus(long peer) { return getStatus(peer); }

    @NonNull
    @Override
    public FLSliceResult nGetPendingDocIds(long peer, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        return getPendingDocIds(peer, scope, collection);
    }

    @Override
    public boolean nIsDocumentPending(long peer, @NonNull String id, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException {
        return isDocumentPending(peer, id, scope, collection);
    }

    @Override
    public void nSetProgressLevel(long peer, int progressLevel) throws LiteCoreException {
        setProgressLevel(peer, progressLevel);
    }

    @Override
    public void nSetHostReachable(long peer, boolean reachable) { setHostReachable(peer, reachable); }


    //-------------------------------------------------------------------------
    // Native methods
    //
    // Methods that take a peer as an argument assume that the peer is valid until the method returns
    // Methods without a @GuardedBy annotation are otherwise thread-safe
    //-------------------------------------------------------------------------

    /*
     * Creates a new replicator.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @GuardedBy("dbLock")
    private static native long create(
        @NonNull String id,
        @NonNull ReplicationCollection[] collections,
        long db,
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDbName,
        int framing,
        boolean push,
        boolean pull,
        boolean continuous,
        @Nullable byte[] options,
        long replicatorToken,
        long socketFactoryToken)
        throws LiteCoreException;

    /*
     * Creates a new local replicator.
     */
    @GuardedBy("dbLock")
    private static native long createLocal(
        @NonNull String id,
        @NonNull ReplicationCollection[] collections,
        long db,
        long targetDb,
        boolean push,
        boolean pull,
        boolean continuous,
        @Nullable byte[] options,
        long replicatorToken)
        throws LiteCoreException;

    /*
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections.  Wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     */
    @GuardedBy("dbLock")
    private static native long createWithSocket(
        @NonNull String id,
        @NonNull ReplicationCollection[] collections,
        long db,
        long openSocket,
        @Nullable byte[] options,
        long replicatorToken)
        throws LiteCoreException;

    /**
     * Frees a replicator reference. If the replicator is running it will stop.
     */
    private static native void free(long peer);

    /**
     * Tells a replicator to start.
     */
    private static native void start(long peer, boolean restart);

    /**
     * Tells a replicator to stop.
     */
    private static native void stop(long peer);

    /**
     * Set the replicator options.
     */
    private static native void setOptions(long peer, @Nullable byte[] options);

    /**
     * Returns the current state of a replicator.
     */
    @NonNull
    private static native C4ReplicatorStatus getStatus(long peer);

    /**
     * Returns a list of string ids for pending documents.
     */
    @NonNull
    private static native FLSliceResult getPendingDocIds(long peer, @NonNull String scope, @NonNull String collection)
        throws LiteCoreException;

    /**
     * Returns true if there are documents that have not been resolved.
     */
    private static native boolean isDocumentPending(
        long peer,
        @NonNull String id,
        @NonNull String scope,
        @NonNull String collection)
        throws LiteCoreException;

    /**
     * Set the core progress callback level.
     */
    @GuardedBy("replLock")
    private static native void setProgressLevel(long peer, int progressLevel) throws LiteCoreException;

    /**
     * Hint to core about the reachability of the target of this replicator.
     */
    private static native void setHostReachable(long peer, boolean reachable);
}
