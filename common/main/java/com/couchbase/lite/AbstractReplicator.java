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
package com.couchbase.lite;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.URI;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorMode;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.replicator.BaseReplicator;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
import com.couchbase.lite.internal.replicator.ReplicatorListener;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * A replicator for replicating document changes between a local database and a target database.
 * The replicator can be bidirectional or either push or pull. The replicator can also be one-shot
 * or continuous. The replicator runs asynchronously, so observe the status to
 * be notified of progress.
 */
@SuppressWarnings({"PMD.TooManyFields", "PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public abstract class AbstractReplicator extends BaseReplicator {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;

    static class ReplicatorCookieStore implements CBLCookieStore {
        @NonNull
        private final Database db;

        ReplicatorCookieStore(@NonNull Database db) { this.db = db; }

        @Override
        public void setCookie(@NonNull URI uri, @NonNull String header) { db.setCookie(uri, header); }

        @Nullable
        @Override
        public String getCookies(@NonNull URI uri) {
            synchronized (db.getDbLock()) { return (!db.isOpen()) ? null : db.getCookies(uri); }
        }
    }

    static boolean isStopped(@NonNull C4ReplicatorStatus c4Status) {
        return c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.STOPPED;
    }

    static boolean isOffline(@NonNull C4ReplicatorStatus c4Status) {
        return c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.OFFLINE;
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    private final ImmutableReplicatorConfiguration config;

    private final Executor dispatcher = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    @GuardedBy("getReplicatorLock()")
    private final Set<ReplicatorChangeListenerToken> changeListeners = new HashSet<>();
    @GuardedBy("getReplicatorLock()")
    private final Set<DocumentReplicationListenerToken> docEndedListeners = new HashSet<>();

    @NonNull
    private final Set<Fn.NullableConsumer<CouchbaseLiteException>> pendingResolutions = new HashSet<>();
    @NonNull
    private final Deque<C4ReplicatorStatus> pendingStatusNotifications = new LinkedList<>();
    @NonNull
    private final ReplicatorListener c4ReplListener;
    @NonNull
    private final SocketFactory socketFactory;

    // Server certificates received from the server during the TLS handshake
    private final AtomicReference<List<Certificate>> serverCertificates = new AtomicReference<>();

    @NonNull
    @GuardedBy("getReplicatorLock()")
    private ReplicatorStatus status
        = new ReplicatorStatus(ReplicatorActivityLevel.STOPPED, new ReplicatorProgress(0, 0), null);

    @GuardedBy("getReplicatorLock()")
    private C4ReplicationFilter c4ReplPushFilter;
    @GuardedBy("getReplicatorLock()")
    private C4ReplicationFilter c4ReplPullFilter;

    @GuardedBy("getReplicatorLock()")
    @Nullable
    private CouchbaseLiteException lastError;

    private volatile String desc;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config replicator configuration
     */
    protected AbstractReplicator(@NonNull ReplicatorConfiguration config) {
        Preconditions.assertNotNull(config, "config");
        Preconditions.assertNotNull(config.getDatabase(), "Configuration must include at least one collection");
        this.config = new ImmutableReplicatorConfiguration(config);
        this.socketFactory = new SocketFactory(
            config,
            new ReplicatorCookieStore(getDatabase()),
            this::setServerCertificates);
        this.c4ReplListener = new ReplicatorReplicatorListener(dispatcher);
    }

    //---------------------------------------------
    // Public Methods
    //---------------------------------------------

    /**
     * Start the replicator.
     */
    public void start() { start(false); }

    /**
     * Start the replicator.
     * This method does not wait for the replicator to start.
     * The replicator runs asynchronously and reports its progress
     * through replicator change notifications.
     */
    public void start(boolean resetCheckpoint) {
        Log.i(DOMAIN, "Replicator is starting");

        getDatabase().addActiveReplicator(this);

        final C4Replicator repl = getOrCreateC4Replicator();
        synchronized (getReplicatorLock()) {
            repl.start(resetCheckpoint);

            C4ReplicatorStatus status = repl.getStatus();
            if (status == null) {
                status = new C4ReplicatorStatus(
                    C4ReplicatorStatus.ActivityLevel.STOPPED,
                    C4Constants.ErrorDomain.LITE_CORE,
                    C4Constants.LiteCoreError.UNEXPECTED_ERROR);
            }

            status = updateStatus(status);

            c4ReplListener.statusChanged(repl, status);
        }
    }

    /**
     * Stop a running replicator.
     * This method does not wait for the replicator to stop.
     * When the replicator actually stops, it will a broadcast a new state, STOPPED,
     * to change listeners.
     */
    public void stop() {
        final C4Replicator c4repl = getC4Replicator();
        Log.i(DOMAIN, "%s: Replicator is stopping (%s)", this, c4repl);
        if (c4repl == null) { return; }
        c4repl.stop();
    }

    /**
     * The replicator's configuration.
     *
     * @return this replicator's configuration
     */
    @NonNull
    public ReplicatorConfiguration getConfig() { return new ReplicatorConfiguration(config); }

    /**
     * The replicator's current status: its activity level and progress.
     *
     * @return this replicator's status
     */
    @NonNull
    public ReplicatorStatus getStatus() {
        synchronized (getReplicatorLock()) { return new ReplicatorStatus(status); }
    }

    /**
     * The server certificates received from the server during the TLS handshake.
     *
     * @return this replicator's server certificates.
     */
    @Nullable
    public List<Certificate> getServerCertificates() {
        final List<Certificate> serverCerts = serverCertificates.get();
        return ((serverCerts == null) || serverCerts.isEmpty())
            ? null
            : new ArrayList<>(serverCerts);
    }

    /**
     * Get a best effort list of documents still pending replication.
     *
     * @return a set of ids for documents still awaiting replication.
     */
    @NonNull
    public Set<String> getPendingDocumentIds() throws CouchbaseLiteException {
        if (config.getType().equals(ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        final Set<String> pending;
        try { pending = getOrCreateC4Replicator().getPendingDocIDs(); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed fetching pending documentIds");
        }

        return Collections.unmodifiableSet(pending);
    }

    /**
     * Best effort check to see if the document whose ID is passed is still pending replication.
     *
     * @param docId Document id
     * @return true if the document is pending
     */
    public boolean isDocumentPending(@NonNull String docId) throws CouchbaseLiteException {
        Preconditions.assertNotNull(docId, "document ID");

        if (config.getType().equals(ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        try { return getOrCreateC4Replicator().isDocumentPending(docId); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed getting document pending status");
        }
    }

    /**
     * Adds a change listener for the changes in the replication status and progress.
     * <p>
     * The changes will be delivered on the UI thread for the Android platform
     * On other Java platforms, the callback will occur on the single threaded default executor.
     * <p>
     * When developing a Java Desktop application using Swing or JavaFX that needs to update the UI after
     * receiving the changes, make sure to schedule the UI update on the UI thread by using
     * SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return addChangeListener(null, listener);
    }

    /**
     * Adds a change listener for the changes in the replication status and progress with an executor on which
     * the changes will be posted to the listener. If the executor is not specified, the changes will be delivered
     * on the UI thread on Android platform and on the single threaded default executor on other Java platforms
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@Nullable Executor executor, @NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (getReplicatorLock()) {
            final ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(
                executor,
                listener,
                this::removeReplicationListener);
            changeListeners.add(token);
            setProgressLevel();
            return token;
        }
    }

    /**
     * Adds a listener for receiving the replication status of the specified document.
     * The status will be delivered on the UI thread for the Android platform
     * and on the single threaded default executor on other platforms.
     * <p>
     * When developing a Java Desktop application using Swing or JavaFX that needs to update the UI after
     * receiving the status, make sure to schedule the UI update on the UI thread by using
     * SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     * @return A ListenerToken that can be used to remove the handler in the future.
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(@NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return addDocumentReplicationListener(null, listener);
    }

    /**
     * Adds a listener for receiving the replication status of the specified document with an executor on which
     * the status will be posted to the listener. If the executor is not specified, the status will be delivered
     * on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(
        @Nullable Executor executor,
        @NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (getReplicatorLock()) {
            final DocumentReplicationListenerToken token = new DocumentReplicationListenerToken(
                executor,
                listener,
                this::removeDocumentReplicationListener);
            docEndedListeners.add(token);
            setProgressLevel();
            return token;
        }
    }

    /**
     * Remove the given ReplicatorChangeListener or DocumentReplicationListener from the this replicator.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     * @deprecated use ListenerToken.remove
     */
    @Deprecated
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        synchronized (getReplicatorLock()) {
            if (token instanceof ReplicatorChangeListenerToken) { removeReplicationListener(token); }
            else if (token instanceof DocumentReplicationListenerToken) { removeDocumentReplicationListener(token); }
            else { throw new IllegalArgumentException("unexpected token: " + token); }
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (desc == null) { desc = description(); }
        return getC4Replicator() + "$" + desc;
    }

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @GuardedBy("getDbLock()")
    @NonNull
    protected abstract C4Replicator createReplicatorForTarget(@NonNull Endpoint target) throws LiteCoreException;

    protected abstract void handleOffline(@NonNull ReplicatorActivityLevel prevState, boolean nowOnline);

    /**
     * Create and return a c4Replicator targeting the passed URI
     *
     * @param remoteUri a URI for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("getDbLock()")
    @NonNull
    protected final C4Replicator getRemoteC4Replicator(@NonNull URI remoteUri) throws LiteCoreException {
        // Set up the port: core uses 0 for not set
        final int p = remoteUri.getPort();
        final int port = Math.max(0, p);

        // get db name and path
        final Deque<String> splitPath = splitPath(remoteUri.getPath());
        final String dbName = (splitPath.size() <= 0) ? "" : splitPath.removeLast();
        final String path = "/" + StringUtils.join("/", splitPath);

        final boolean continuous = config.isContinuous();

        return getDatabase().createRemoteReplicator(
            remoteUri.getScheme(),
            remoteUri.getHost(),
            port,
            path,
            dbName,
            makeMode(config.isPush(), continuous),
            makeMode(config.isPull(), continuous),
            MessageFraming.NO_FRAMING,
            getFleeceOptions(),
            c4ReplListener,
            (Replicator) this,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory
        );
    }

    /**
     * Create and return a c4Replicator targeting the passed Database
     *
     * @param targetDb a local database for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("getDbLock()")
    @NonNull
    protected final C4Replicator getLocalC4Replicator(@NonNull Database targetDb) throws LiteCoreException {
        final boolean continuous = config.isContinuous();
        return getDatabase().createLocalReplicator(
            targetDb,
            makeMode(config.isPush(), continuous),
            makeMode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            (Replicator) this,
            c4ReplPushFilter,
            c4ReplPullFilter);
    }

    /**
     * Create and return a c4Replicator.
     * The socket factory is responsible for setting up the target
     *
     * @param framing the message framing
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("getDbLock()")
    @NonNull
    protected final C4Replicator getMessageC4Replicator(@NonNull MessageFraming framing) throws LiteCoreException {
        final boolean continuous = config.isContinuous();
        return getDatabase().createRemoteReplicator(
            C4Replicator.MESSAGE_SCHEME,
            null,
            0,
            null,
            null,
            makeMode(config.isPush(), continuous),
            makeMode(config.isPull(), continuous),
            framing,
            getFleeceOptions(),
            c4ReplListener,
            (Replicator) this,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory
        );
    }

    //---------------------------------------------
    // Package visible methods
    //
    // Some of these are package protected only to avoid a synthetic accessor
    //---------------------------------------------

    @Nullable
    @VisibleForTesting
    CouchbaseLiteException getLastError() { return lastError; }

    @NonNull
    ReplicatorActivityLevel getState() {
        synchronized (getReplicatorLock()) { return status.getActivityLevel(); }
    }

    void c4StatusChanged(@NonNull C4ReplicatorStatus c4Status) {
        final ReplicatorChange change;
        final List<ReplicatorChangeListenerToken> tokens;
        synchronized (getReplicatorLock()) {
            Log.i(
                DOMAIN,
                "status changed: (%d, %d) @%s for %s",
                pendingResolutions.size(), pendingStatusNotifications.size(), c4Status, this);

            if (config.isContinuous()) { handleOffline(status.getActivityLevel(), !isOffline(c4Status)); }

            if (!pendingResolutions.isEmpty()) { pendingStatusNotifications.add(c4Status); }
            if (!pendingStatusNotifications.isEmpty()) { return; }

            // Update my properties:
            updateStatus(c4Status);

            // Post notification
            // Replicator.getStatus() creates a copy of Status.
            change = new ReplicatorChange((Replicator) this, this.getStatus());
            tokens = new ArrayList<>(changeListeners);
        }

        // this will probably make this instance eligible for garbage collection...
        if (isStopped(c4Status)) { getDatabase().removeActiveReplicator(this); }

        for (ReplicatorChangeListenerToken token: tokens) { token.notify(change); }
    }

    void documentEnded(boolean pushing, @NonNull C4DocumentEnded... docEnds) {
        final List<ReplicatedDocument> unconflictedDocs = new ArrayList<>();

        for (C4DocumentEnded docEnd: docEnds) {
            final String docId = docEnd.getDocID();
            final C4Error c4Error = docEnd.getC4Error();

            CouchbaseLiteException error = null;

            if ((c4Error != null) && (c4Error.getCode() != 0)) {
                if (!pushing && docEnd.isConflicted()) {
                    queueConflictResolution(docId, docEnd.getFlags());
                    continue;
                }

                error = CouchbaseLiteException.convertC4Error(c4Error);
            }

            // !!! temporary hack
            final Collection coll = getDefaultCollection();

            unconflictedDocs.add(new ReplicatedDocument(
                coll.getScope().getName(),
                coll.getName(),
                docId,
                docEnd.getFlags(),
                error));
        }

        if (!unconflictedDocs.isEmpty()) { notifyDocumentEnded(pushing, unconflictedDocs); }
    }

    // callback from queueConflictResolution
    void onConflictResolved(
        Fn.NullableConsumer<CouchbaseLiteException> task,
        String docId,
        int flags,
        CouchbaseLiteException err) {
        Log.i(DOMAIN, "Conflict resolved: %s", err, docId);
        List<C4ReplicatorStatus> pendingNotifications = null;
        synchronized (getReplicatorLock()) {
            pendingResolutions.remove(task);
            // if no more resolutions, deliver any outstanding status notifications
            if (pendingResolutions.isEmpty()) {
                pendingNotifications = new ArrayList<>(pendingStatusNotifications);
                pendingStatusNotifications.clear();
            }
        }

        // !!! temporary hack
        final Collection coll = getDefaultCollection();

        notifyDocumentEnded(
            false,
            Arrays.asList(new ReplicatedDocument(coll.getScope().getName(), coll.getName(), docId, flags, err)));

        if ((pendingNotifications != null) && (!pendingNotifications.isEmpty())) {
            for (C4ReplicatorStatus status: pendingNotifications) { dispatcher.execute(() -> c4StatusChanged(status)); }
        }
    }

    void notifyDocumentEnded(boolean pushing, List<ReplicatedDocument> docs) {
        final DocumentReplication update = new DocumentReplication((Replicator) this, pushing, docs);
        final List<DocumentReplicationListenerToken> tokens;
        synchronized (getReplicatorLock()) { tokens = new ArrayList<>(docEndedListeners); }
        for (DocumentReplicationListenerToken token: tokens) { token.notify(update); }
        Log.i(DOMAIN, "notifyDocumentEnded: %s" + update);
    }

    @NonNull
    @VisibleForTesting
    SocketFactory getSocketFactory() { return socketFactory; }

    @VisibleForTesting
    int getListenerCount() {
        synchronized (getReplicatorLock()) { return changeListeners.size() + docEndedListeners.size(); }
    }

    @VisibleForTesting
    int getDocEndListenerCount() {
        synchronized (getReplicatorLock()) { return docEndedListeners.size(); }
    }

    @VisibleForTesting
    int getReplicatorListenerCount() {
        synchronized (getReplicatorLock()) { return changeListeners.size(); }
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @NonNull
    private C4Replicator getOrCreateC4Replicator() {
        // createReplicatorForTarget is going to seize this lock anyway: force in-order seizure
        synchronized (getDatabase().getDbLock()) {
            C4Replicator c4Repl = getC4Replicator();

            if (c4Repl != null) {
                c4Repl.setOptions(getFleeceOptions());
                // !!! This is probably a bug.  SetOptions should not clear the progress level
                synchronized (getReplicatorLock()) { setProgressLevel(); }
                return c4Repl;
            }

            setupFilters();
            try {
                c4Repl = createReplicatorForTarget(config.getTarget());
                synchronized (getReplicatorLock()) {
                    setC4Replicator(c4Repl);
                    setProgressLevel();
                }
                return c4Repl;
            }
            catch (LiteCoreException e) {
                throw new IllegalStateException(
                    "Could not create replicator",
                    CouchbaseLiteException.convertException(e));
            }
        }
    }

    @NonNull
    @GuardedBy("getReplicatorLock()")
    private C4ReplicatorStatus updateStatus(@NonNull C4ReplicatorStatus c4Status) {
        final ReplicatorStatus oldStatus = status;
        status = new ReplicatorStatus(c4Status);

        final CouchbaseLiteException err = status.getError();
        if (c4Status.getErrorCode() != 0) { lastError = err; }

        Log.i(DOMAIN, "State changed %s => %s(%d/%d): %s for %s",
            oldStatus.getActivityLevel(),
            status.getActivityLevel(),
            c4Status.getProgressUnitsCompleted(),
            c4Status.getProgressUnitsTotal(),
            err,
            this);

        return c4Status.copy();
    }

    private void queueConflictResolution(@NonNull String docId, int flags) {
        Log.i(DOMAIN, "%s: pulled conflicting version of '%s'", this, docId);

        final ExecutionService.CloseableExecutor executor
            = CouchbaseLiteInternal.getExecutionService().getConcurrentExecutor();
        final Database db = getDatabase();
        final ConflictResolver resolver = config.getConflictResolver();
        final Fn.NullableConsumer<CouchbaseLiteException> task = new Fn.NullableConsumer<CouchbaseLiteException>() {
            public void accept(CouchbaseLiteException err) { onConflictResolved(this, docId, flags, err); }
        };

        synchronized (getReplicatorLock()) {
            executor.execute(() -> db.resolveReplicationConflict(resolver, docId, task));
            pendingResolutions.add(task);
        }
    }

    @Nullable
    private byte[] getFleeceOptions() {
        final Map<String, Object> options = config.getConnectionOptions();

        byte[] optionsFleece = null;
        if (!options.isEmpty()) {
            try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
                enc.write(options);
                optionsFleece = enc.finish();
            }
            catch (LiteCoreException e) { Log.w(DOMAIN, "Failed encoding replicator options", e); }
        }

        return optionsFleece;
    }

    private void setupFilters() {
        synchronized (getReplicatorLock()) {
            if (config.getPushFilter() != null) {
                c4ReplPushFilter = (docID, revId, flags, dict, isPush, repl) ->
                    repl.filterDocument(docID, revId, getDocumentFlags(flags), dict, isPush);
            }

            if (config.getPullFilter() != null) {
                c4ReplPullFilter = (docID, revId, flags, dict, isPush, repl) ->
                    repl.filterDocument(docID, revId, getDocumentFlags(flags), dict, isPush);
            }
        }
    }

    private int makeMode(boolean active, boolean continuous) {
        final C4ReplicatorMode mode = (!active)
            ? C4ReplicatorMode.C4_DISABLED
            : ((continuous) ? C4ReplicatorMode.C4_CONTINUOUS : C4ReplicatorMode.C4_ONE_SHOT);
        return mode.getVal();
    }

    @NonNull
    private EnumSet<DocumentFlag> getDocumentFlags(int flags) {
        final EnumSet<DocumentFlag> documentFlags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.RevisionFlags.DELETED) == C4Constants.RevisionFlags.DELETED) {
            documentFlags.add(DocumentFlag.DELETED);
        }
        if ((flags & C4Constants.RevisionFlags.PURGED) == C4Constants.RevisionFlags.PURGED) {
            documentFlags.add(DocumentFlag.ACCESS_REMOVED);
        }
        return documentFlags;
    }

    private boolean filterDocument(
        @NonNull String docId,
        String revId,
        @NonNull EnumSet<DocumentFlag> flags,
        long dict,
        boolean isPush) {
        final ReplicationFilter filter = (isPush) ? config.getPushFilter() : config.getPullFilter();
        return (filter != null) && filter.filtered(
            new Document(getDatabase(), docId, revId, FLDict.create(dict)),
            flags);
    }

    private void removeDocumentReplicationListener(@NonNull ListenerToken token) {
        synchronized (getReplicatorLock()) {
            docEndedListeners.remove(token);
            setProgressLevel();
        }
    }

    private void removeReplicationListener(@NonNull ListenerToken token) {
        synchronized (getReplicatorLock()) { changeListeners.remove(token); }
    }

    @GuardedBy("getReplicatorLock()")
    private void setProgressLevel() {
        final C4Replicator c4Repl = getC4Replicator();
        if (c4Repl == null) { return; }

        try {
            c4Repl.setProgressLevel(
                docEndedListeners.isEmpty()
                    ? C4Replicator.PROGRESS_OVERALL
                    : C4Replicator.PROGRESS_PER_DOC);
        }
        catch (LiteCoreException e) {
            Log.w(LogDomain.REPLICATOR, "failed setting progress level");
        }
    }

    @NonNull
    private Database getDatabase() {
        final Database db = config.getDatabase();
        if (db == null) { throw new IllegalStateException("No database in Replicator"); }
        return db;
    }

    // Consumer callback to set the server certificates received during the TLS Handshake
    private void setServerCertificates(List<Certificate> certificates) { serverCertificates.set(certificates); }

    @NonNull
    private String description() { return baseDesc() + "," + getDatabase() + " => " + config.getTarget() + "}"; }

    @NonNull
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String simpleDesc() { return baseDesc() + "}"; }

    @NonNull
    private String baseDesc() {
        return "Replicator{" + ClassUtils.objId(this) + "("
            + (config.isPull() ? "<" : "")
            + (config.isContinuous() ? "*" : "-")
            + (config.isPush() ? ">" : "")
            + ")";
    }

    // Decompose a path into its elements.
    @NonNull
    private Deque<String> splitPath(@NonNull String fullPath) {
        final Deque<String> path = new ArrayDeque<>();
        for (String element: fullPath.split("/")) {
            if (element.length() > 0) { path.addLast(element); }
        }
        return path;
    }

    @NonNull
    private Collection getDefaultCollection() {
        try {
            final Collection collection = getDatabase().getDefaultCollection();
            if (collection != null) { return collection; }
            throw new IllegalStateException("Cannot find collection for replicator");
        }
        catch (CouchbaseLiteException e) { throw new IllegalStateException("Database is not open?", e); }
    }
}
