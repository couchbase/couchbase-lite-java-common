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

import androidx.annotation.NonNull;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4ReplicationCollection;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;


public final class NativeC4Replicator implements C4Replicator.NativeImpl {
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Override
    public long nCreate(
        C4ReplicationCollection[] collections,
        long db,
        String scheme,
        String host,
        int port,
        String path,
        String remoteDbName,
        int framing,
        boolean continuous,
        byte[] options,
        long replicatorToken,
        long socketFactoryToken)
        throws LiteCoreException {
        return create(
            collections,
            db,
            scheme,
            host,
            port,
            path,
            remoteDbName,
            framing,
            continuous,
            options,
            replicatorToken,
            socketFactoryToken
        );
    }

    @Override
    public long nCreateLocal(
        C4ReplicationCollection[] collections,
        long db,
        long targetDb,
        boolean continuous,
        byte[] options,
        long replicatorToken)
        throws LiteCoreException {
        return createLocal(
            collections,
            db,
            targetDb,
            continuous,
            options,
            replicatorToken);
    }

    @Override
    public long nCreateWithSocket(
        C4ReplicationCollection[] collections,
        long db,
        long openSocket,
        boolean continuous,
        byte[] options,
        long replicatorToken)
        throws LiteCoreException {
        return createWithSocket(collections, db, openSocket, continuous, options, replicatorToken);
    }

    @Override
    public void nFree(long peer) { free(peer); }

    @Override
    public void nStart(long peer, boolean restart) { start(peer, restart); }

    @Override
    public void nStop(long peer) { stop(peer); }

    @Override
    public void nSetOptions(long peer, byte[] options) { setOptions(peer, options); }

    @NonNull
    @Override
    public C4ReplicatorStatus nGetStatus(long peer) { return getStatus(peer); }

    @Override
    public long nGetPendingDocIds(long peer) throws LiteCoreException { return getPendingDocIds(peer); }

    @Override
    public boolean nIsDocumentPending(long peer, String id) throws LiteCoreException {
        return isDocumentPending(peer, id);
    }

    @Override
    public void nSetProgressLevel(long peer, int progressLevel) throws LiteCoreException {
        setProgressLevel(peer, progressLevel);
    }

    @Override
    public void nSetHostReachable(long peer, boolean reachable) { setHostReachable(peer, reachable); }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Override
    public long nCreateDeprecated(
        long db,
        String scheme,
        String host,
        int port,
        String path,
        String remoteDbName,
        int push,
        int pull,
        int framing,
        byte[] options,
        boolean hasPushFilter,
        boolean hasPullFilter,
        long replicatorToken,
        long socketFactoryToken)
        throws LiteCoreException {
        return createDeprecated(
            db,
            scheme,
            host,
            port,
            path,
            remoteDbName,
            push,
            pull,
            framing,
            options,
            hasPushFilter,
            hasPullFilter,
            replicatorToken,
            socketFactoryToken
        );
    }

    @Override
    public long nCreateLocalDeprecated(
        long db,
        long targetDb,
        int push,
        int pull,
        byte[] options,
        boolean hasPushFilter,
        boolean hasPullFilter,
        long replicatorToken)
        throws LiteCoreException {
        return createLocalDeprecated(
            db,
            targetDb,
            push,
            pull,
            options,
            hasPushFilter,
            hasPullFilter,
            replicatorToken);
    }

    @Override
    public long nCreateWithSocketDeprecated(
        long db,
        long openSocket,
        int push,
        int pull,
        byte[] options,
        long replicatorToken)
        throws LiteCoreException {
        return createWithSocketDeprecated(db, openSocket, push, pull, options, replicatorToken);
    }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Creates a new replicator.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long create(
        C4ReplicationCollection[] collections,
        long db,
        String scheme,
        String host,
        int port,
        String path,
        String remoteDbName,
        int framing,
        boolean continuous,
        byte[] options,
        long replicatorToken,
        long socketFactoryToken)
        throws LiteCoreException;

    /**
     * Creates a new local replicator.
     */
    private static native long createLocal(
        C4ReplicationCollection[] collections,
        long db,
        long targetDb,
        boolean continuous,
        byte[] options,
        long replicatorToken)
        throws LiteCoreException;

    /**
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections.  Wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     *
     * @param db              The local database.
     * @param openSocket      An already-created C4Socket.
     * @param options         flags
     * @param replicatorToken replicatorToken
     * @return The pointer of the newly created replicator
     */
    private static native long createWithSocket(
        C4ReplicationCollection[] collections,
        long db,
        long openSocket,
        boolean continuous,
        byte[] options,
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
    private static native void setOptions(long peer, byte[] options);

    /**
     * Returns the current state of a replicator.
     */
    @NonNull
    private static native C4ReplicatorStatus getStatus(long peer);

    /**
     * Returns a list of string ids for pending documents.
     */
    private static native long getPendingDocIds(long peer) throws LiteCoreException;

    /**
     * Returns true if there are documents that have not been resolved.
     */
    private static native boolean isDocumentPending(long peer, String id) throws LiteCoreException;

    /**
     * Set the core progress callback level.
     */
    private static native void setProgressLevel(long peer, int progressLevel) throws LiteCoreException;

    /**
     * Hint to core about the reachability of the target of this replicator.
     */
    private static native void setHostReachable(long peer, boolean reachable);

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long createDeprecated(
        long db,
        String scheme,
        String host,
        int port,
        String path,
        String remoteDbName,
        int push,
        int pull,
        int framing,
        byte[] options,
        boolean hasPushFilter,
        boolean hasPullFilter,
        long replicatorToken,
        long socketFactoryToken)
        throws LiteCoreException;

    private static native long createLocalDeprecated(
        long db,
        long targetDb,
        int push,
        int pull,
        byte[] options,
        boolean hasPushFilter,
        boolean hasPullFilter,
        long replicatorToken)
        throws LiteCoreException;

    private static native long createWithSocketDeprecated(
        long db,
        long openSocket,
        int push,
        int pull,
        byte[] options,
        long replicatorToken)
        throws LiteCoreException;
}
