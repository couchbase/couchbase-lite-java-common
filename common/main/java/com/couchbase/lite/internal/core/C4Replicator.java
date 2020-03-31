//
// C4Replicator.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * There are two things that need protection in the class:
 * <ol>
 * <li/> Messages sent to it from native code:  This object proxies those messages out to
 * various listeners.  Until a replicator object is removed from the REVERSE_LOOKUP_TABLE
 * forwarding such a message must work.
 * <li/> Calls to the native object:  These should work as long as the `handle` is non-zero.
 * This object must be careful never to forward a call to a native object once that object has been freed.
 * </ol>
 * <p>
 * Instances of this class are created using static factory methods
 * <p>
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
@SuppressWarnings("PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal")
public class C4Replicator extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    public static final String C4_REPLICATOR_SCHEME_2 = "blip";
    public static final String C4_REPLICATOR_TLS_SCHEME_2 = "blips";

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // This lock protects both of the maps below and the corresponding vector in the JNI code
    private static final Object CLASS_LOCK = new Object();

    // Long: handle of C4Replicator native address
    // C4Replicator: Java class holds handle
    @NonNull
    @GuardedBy("CLASS_LOCK")
    private static final Map<Long, C4Replicator> REVERSE_LOOKUP_TABLE = new HashMap<>();

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createRemoteReplicator(
        long db,
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
        final C4Replicator replicator;
        synchronized (CLASS_LOCK) {
            replicator = new C4Replicator(
                db,
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
            bind(replicator);
        }

        return replicator;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createLocalReplicator(
        long db,
        @Nullable C4Database otherLocalDB,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext)
        throws LiteCoreException {
        final C4Replicator replicator;
        synchronized (CLASS_LOCK) {
            replicator = new C4Replicator(
                db,
                otherLocalDB,
                push,
                pull,
                options,
                listener,
                pushFilter,
                pullFilter,
                replicatorContext);
            bind(replicator);
        }

        return replicator;
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    static C4Replicator createTargetReplicator(
        long db,
        @NonNull C4Socket openSocket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {
        final C4Replicator replicator;
        synchronized (CLASS_LOCK) {
            replicator = new C4Replicator(
                db,
                openSocket,
                push,
                pull,
                options,
                listener,
                replicatorContext);
            bind(replicator);
        }

        return replicator;
    }

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("unused")
    static void statusChangedCallback(long handle, @Nullable C4ReplicatorStatus status) {
        final C4Replicator repl = getReplicatorForHandle(handle);
        Log.d(LogDomain.REPLICATOR, "statusChangedCallback() handle: " + handle + ", status: " + status);
        if (repl == null) { return; }

        final C4ReplicatorListener listener = repl.listener;
        if (listener != null) { listener.statusChanged(repl, status, repl.replicatorContext); }
    }

    @SuppressWarnings("unused")
    static void documentEndedCallback(long handle, boolean pushing, @Nullable C4DocumentEnded... documentsEnded) {
        Log.d(LogDomain.REPLICATOR, "documentEndedCallback() handle: " + handle + ", pushing: " + pushing);

        final C4Replicator repl = getReplicatorForHandle(handle);
        if (repl == null) { return; }

        final C4ReplicatorListener listener = repl.listener;
        if (listener != null) { listener.documentEnded(repl, pushing, documentsEnded, repl.replicatorContext); }
    }

    // Supported only for Replicators
    static boolean validationFunction(String docID, String revID, int flags, long dict, boolean isPush, Object ctxt) {
        if (!(ctxt instanceof AbstractReplicator)) {
            Log.w(LogDomain.DATABASE, "Validation function called with unrecognized context: " + ctxt);
            return true;
        }
        final AbstractReplicator repl = (AbstractReplicator) ctxt;

        final C4Replicator c4Repl = repl.getC4Replicator(); // Try that, Kotlin!!
        if (c4Repl == null) { return true; }

        final C4ReplicationFilter filter = (isPush) ? c4Repl.pushFilter : c4Repl.pullFilter;
        return (filter == null) || filter.validationFunction(docID, revID, flags, dict, isPush, repl);
    }

    //-------------------------------------------------------------------------
    // Private static methods
    //-------------------------------------------------------------------------

    @Nullable
    private static C4Replicator getReplicatorForHandle(long handle) {
        synchronized (CLASS_LOCK) { return REVERSE_LOOKUP_TABLE.get(handle); }
    }

    private static void bind(@NonNull C4Replicator repl) {
        Preconditions.assertNotNull(repl, "repl");
        REVERSE_LOOKUP_TABLE.put(repl.getPeer(), repl);
    }

    private static void release(long handle) {
        if (handle != 0) { REVERSE_LOOKUP_TABLE.remove(handle); }
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final Object replicatorContext;
    @SuppressFBWarnings("SE_BAD_FIELD")
    @Nullable
    private final SocketFactory socketFactoryContext;

    @Nullable
    private final C4ReplicatorListener listener;

    @Nullable
    private final C4ReplicationFilter pushFilter;
    @Nullable
    private final C4ReplicationFilter pullFilter;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    // Remote
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private C4Replicator(
        long db,
        @Nullable String schema,
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
        super(create(
            db,
            schema,
            host,
            port,
            path,
            remoteDatabaseName,
            push,
            pull,
            socketFactoryContext,
            framing,
            replicatorContext,
            pushFilter,
            pullFilter,
            options));

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.socketFactoryContext = socketFactoryContext;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;
    }

    // Local
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private C4Replicator(
        long db,
        @Nullable C4Database otherLocalDB,
        int push,
        int pull,
        @NonNull byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext)
        throws LiteCoreException {
        super(createLocal(
            db,
            (otherLocalDB == null) ? 0 : otherLocalDB.getHandle(),
            push,
            pull,
            C4Socket.NO_FRAMING,
            replicatorContext,
            pushFilter,
            pullFilter,
            options));

        this.socketFactoryContext = null;

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;
    }

    // Target
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private C4Replicator(
        long db,
        @NonNull C4Socket openSocket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {
        super(createWithSocket(db, openSocket.getHandle(), push, pull, replicatorContext, options));

        this.socketFactoryContext = null;

        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.pushFilter = null;
        this.pullFilter = null;
    }

    public void start() { start(getPeer()); }

    public void stop() { stop(getPeer()); }

    @Nullable
    public C4ReplicatorStatus getStatus() { return getStatus(getPeer()); }

    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    @Nullable
    public byte[] getResponseHeaders() { return getResponseHeaders(getPeer()); }

    // Null return value indicates that this replicator is dead
    public boolean isDocumentPending(String docId) throws LiteCoreException {
        return isDocumentPending(getPeer(), docId);
    }

    // Null return value indicates that this replicator is dead
    @Nullable
    public Set<String> getPendingDocIDs() throws LiteCoreException {
        final FLSliceResult result = new FLSliceResult(getPendingDocIds(getPeer()));
        try {
            final FLValue slice = FLValue.fromData(result);
            return (slice == null) ? Collections.emptySet() : new HashSet<>(slice.asTypedArray());
        }
        finally { result.free(); }
    }

    // Several bugs have been reported, near here:
    // Usually: JNI DETECTED ERROR IN APPLICATION: use of deleted global reference
    // https://issues.couchbase.com/browse/CBL-34
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        final long handle = getPeerAndClear();

        // !!! the two arguments may already be gone
        if (handle != 0) { free(handle, replicatorContext, socketFactoryContext); }

        release(handle);

        super.finalize();
    }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Creates a new replicator.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long create(
        long db,
        String schema,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        int push,
        int pull,
        Object socketFactoryContext,
        int framing,
        Object replicatorContext,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        byte[] options)
        throws LiteCoreException;

    /**
     * Creates a new local replicator.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long createLocal(
        long db,
        long targetDb,
        int push,
        int pull,
        int framing,
        Object replicatorContext,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        byte[] options)
        throws LiteCoreException;

    /**
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections.  Wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     *
     * @param db                The local database.
     * @param openSocket        An already-created C4Socket.
     * @param push              boolean: push replication
     * @param pull              boolean: pull replication
     * @param replicatorContext context object
     * @param options           flags
     * @return The pointer of the newly created replicator
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long createWithSocket(
        long db,
        long openSocket,
        int push,
        int pull,
        Object replicatorContext,
        byte[] options)
        throws LiteCoreException;

    /**
     * Frees a replicator reference. If the replicator is running it will stop.
     */
    private static native void free(long replicator, Object replicatorContext, Object socketFactoryContext);

    /**
     * Tells a replicator to start.
     */
    private static native void start(long replicator);

    /**
     * Tells a replicator to stop.
     */
    private static native void stop(long replicator);

    /**
     * Returns the current state of a replicator.
     */
    private static native C4ReplicatorStatus getStatus(long replicator);

    /**
     * Returns the HTTP response headers as a Fleece-encoded dictionary.
     */
    private static native byte[] getResponseHeaders(long replicator);

    /**
     * Returns a list of string ids for pending documents.
     */
    private static native long getPendingDocIds(long handle) throws LiteCoreException;

    /**
     * Returns true if there are documents that have not been resolved.
     */
    private static native boolean isDocumentPending(long handle, String id) throws LiteCoreException;
}

