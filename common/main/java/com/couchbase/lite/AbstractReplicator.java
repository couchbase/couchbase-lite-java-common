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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.ReplicationCollection;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.listener.Listenable;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.replicator.BaseReplicator;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
import com.couchbase.lite.internal.sockets.MessageFraming;
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
public abstract class AbstractReplicator extends BaseReplicator
    implements Listenable<ReplicatorChange, ReplicatorChangeListener> {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;

    static class ReplicatorCookieStore implements CBLCookieStore {
        @NonNull
        private final Database db;

        ReplicatorCookieStore(@NonNull Database db) { this.db = db; }

        @Override
        public void setCookies(@NonNull URI uri, @NonNull List<String> cookies, boolean acceptParentDomain) {
            db.setCookies(uri, cookies, acceptParentDomain);
        }

        @Nullable
        @Override
        public String getCookies(@NonNull URI uri) {
            synchronized (db.getDbLock()) { return (!db.isOpenLocked()) ? null : db.getCookies(uri); }
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

    @NonNull
    private final SocketFactory socketFactory;

    // Server certificates received from the server during the TLS handshake
    private final AtomicReference<List<Certificate>> serverCertificates = new AtomicReference<>();

    // Listeners are reachable until they are closed or this replicator is freed.
    @NonNull
    @GuardedBy("getReplicatorLock()")
    private final Set<ReplicatorChangeListenerToken> changeListeners = new HashSet<>();
    @NonNull
    @GuardedBy("getReplicatorLock()")
    private final Set<DocumentReplicationListenerToken> docEndedListeners = new HashSet<>();

    @NonNull
    @GuardedBy("getReplicatorLock()")
    private ReplicatorStatus status = ReplicatorStatus.INIT;

    // This is pretty arbitrary.  There's actually no reason that one couldn't restert a closed replicator
    @GuardedBy("getReplicatorLock()")
    private boolean closed;

    @GuardedBy("getReplicatorLock()")
    @Nullable
    private CouchbaseLiteException lastError;

    @GuardedBy("getReplicatorLock()")
    @NonNull
    private final Set<Fn.NullableConsumer<CouchbaseLiteException>> pendingResolutions = new HashSet<>();
    @GuardedBy("getReplicatorLock()")
    @NonNull
    private final Deque<C4ReplicatorStatus> pendingStatusNotifications = new LinkedList<>();

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
        this.config = new ImmutableReplicatorConfiguration(config);

        this.socketFactory = new SocketFactory(
            config,
            new ReplicatorCookieStore(getDatabase()),
            this::setServerCertificates);
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

        final C4Replicator c4Repl = getOrCreateC4Replicator();
        synchronized (getReplicatorLock()) {
            c4Repl.start(resetCheckpoint);

            C4ReplicatorStatus status = c4Repl.getStatus();
            if (status == null) {
                status = new C4ReplicatorStatus(
                    C4ReplicatorStatus.ActivityLevel.STOPPED,
                    C4Constants.ErrorDomain.LITE_CORE,
                    C4Constants.LiteCoreError.UNEXPECTED_ERROR);
            }

            status = updateStatus(status);

            dispatchStatusChange(c4Repl, status);
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
     * Get a best effort list of documents in the default collection, that are still pending replication.
     *
     * @return a set of ids for documents in the default collection still awaiting replication.
     * @deprecated Use getPendingDocumentIds(Collection)
     */
    @Deprecated
    @NonNull
    public Set<String> getPendingDocumentIds() throws CouchbaseLiteException {
        return getPendingDocIds(Scope.DEFAULT_NAME, Collection.DEFAULT_NAME);
    }

    /**
     * Get a best effort list of documents in the passed collection that are still pending replication.
     *
     * @return a set of ids for documents in the passed collection still awaiting replication.
     */
    @NonNull
    public Set<String> getPendingDocumentIds(@NonNull Collection collection) throws CouchbaseLiteException {
        return getPendingDocIds(collection.getScope().getName(), collection.getName());
    }

    /**
     * Best effort check to see if the document whose ID is passed is still pending replication.
     *
     * @param docId Document id
     * @return true if the document is pending
     * @deprecated Use isDocumentPending(String, Collection)
     */
    @Deprecated
    public boolean isDocumentPending(@NonNull String docId)
        throws CouchbaseLiteException {
        return isDocPending(docId, Scope.DEFAULT_NAME, Collection.DEFAULT_NAME);
    }

    /**
     * Best effort check to see if the document whose ID is passed is still pending replication.
     *
     * @param docId Document id
     * @return true if the document is pending
     */
    public boolean isDocumentPending(@NonNull String docId, @NonNull Collection collection)
        throws CouchbaseLiteException {
        return isDocPending(docId, collection.getScope().getName(), collection.getName());
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
        final ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(
            executor,
            listener,
            this::removeReplicationListener);
        synchronized (getReplicatorLock()) { changeListeners.add(token); }
        return token;
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
        final DocumentReplicationListenerToken token = new DocumentReplicationListenerToken(
            executor,
            listener,
            this::removeDocumentReplicationListener);
        synchronized (getReplicatorLock()) {
            docEndedListeners.add(token);
            setProgressLevel();
        }
        return token;
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
            if (token instanceof ReplicatorChangeListenerToken) {
                removeReplicationListener(token);
                return;
            }

            if (token instanceof DocumentReplicationListenerToken) {
                removeDocumentReplicationListener(token);
                return;
            }

            throw new IllegalArgumentException("unexpected token: " + token);
        }
    }

    // I've thought a lot about how to implement this.  The problem is that you cannot, fundamentally,
    // close a replicator(discard its resources before this method returns). If it is not stopped,
    // it will have to be stopped.  I came up with several possibilities.
    // 1) close it if it is stopped; stop it if it is running.
    // 2) throw an exception on the attempt to close a running replicator.
    // 3) tell it to stop and forget it, without verifying that it actually goes away.
    // 4) Make replicators not Autoclosable.
    // #4 is very tempting.  Replicators will never be used in a try-with-resources block.
    // Replicators *do*, however, dominate a LiteCore object that must be freed explicitly.
    // Making them, transitively, Autoclosable seems correct.
    // #2 seems to put an excessive burden on client code: it is a bomb that explodes unless you
    // wait around and pay very close attention to the state of every replicator.
    // I dislike #1 because it puts the replicator into an odd state in which it is still kinda
    // half alive.  The client closed it but it isn't quite gone yet.
    // I have chosen #3.  My rational is that, if you create and start a replicator and then null out
    // all references to it, this is exactly what will happen: the replicator will be forgotten and
    // eventually, GCed, told to stop, and freed.  This method does precisely that: best effort to
    // release the resources and then forget about them.

    /**
     * Immediatly close the replicator and free its resources.
     * We recommend the use of this method on Replicators that are in the STOPPED state.  If the
     * replicator is not stopped, this method will make a best effort attempt to stop it but
     * will not wait to confirm that it was stopped cleanly.
     * Any attempt to restart a closed replicator will result in an IllegalStateException.
     * This includes calls to getPendingDocIds and isDocPending.
     */
    public void close() {
        HashSet<ReplicatorChangeListenerToken> listeners = null;
        ReplicatorStatus newStatus = null;

        synchronized (getReplicatorLock()) {
            if (closed) { return; }
            closed = true;

            if (status.getActivityLevel() != ReplicatorActivityLevel.STOPPED) {
                listeners = new HashSet<>(changeListeners);
                newStatus = new ReplicatorStatus(ReplicatorActivityLevel.STOPPED, status.getProgress(), null);
                status = newStatus;
            }
        }

        // there is the potential for a race here...
        closeC4Replicator();

        if ((listeners == null) || listeners.isEmpty()) { return; }

        postChange(true, new ReplicatorChange((Replicator) this, newStatus), listeners);
    }

    /**
     * Determine whether this replicator has been closed.
     *
     * @return true iff the replicator is closed.
     */
    public boolean isClosed() {
        synchronized (getReplicatorLock()) { return closed; }
    }

    @NonNull
    @Override
    public String toString() {
        if (desc == null) { desc = description(); }
        return desc + " $" + getC4Replicator();
    }

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @GuardedBy("getDbLock()")
    @NonNull
    protected abstract C4Replicator createReplicatorForTarget(@NonNull Endpoint target) throws LiteCoreException;

    protected abstract void handleOffline(@NonNull ReplicatorActivityLevel prevState, boolean nowOnline);

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
        return getDatabase().createLocalReplicator(
            config.getCollectionConfigs(),
            targetDb,
            config.getType(),
            config.isContinuous(),
            config.getConnectionOptions(),
            this::dispatchStatusChange,
            this::dispatchDocumentsEnded,
            (Replicator) this);
    }

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

        return getDatabase().createRemoteReplicator(
            config.getCollectionConfigs(),
            remoteUri.getScheme(),
            remoteUri.getHost(),
            port,
            path,
            dbName,
            MessageFraming.NO_FRAMING,
            config.getType(),
            config.isContinuous(),
            config.getConnectionOptions(),
            this::dispatchStatusChange,
            this::dispatchDocumentsEnded,
            (Replicator) this,
            socketFactory
        );
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
        return getDatabase().createRemoteReplicator(
            config.getCollectionConfigs(),
            C4Replicator.MESSAGE_SCHEME,
            null,
            0,
            null,
            null,
            framing,
            config.getType(),
            config.isContinuous(),
            config.getConnectionOptions(),
            this::dispatchStatusChange,
            this::dispatchDocumentsEnded,
            (Replicator) this,
            socketFactory);
    }

    //---------------------------------------------
    // Package visible methods
    //
    // Some of these are package protected only to avoid a synthetic accessor
    //---------------------------------------------

    void dispatchStatusChange(@NonNull C4Replicator ignored, @NonNull C4ReplicatorStatus status) {
        dispatcher.execute(() -> statusChanged(status));
    }

    void dispatchDocumentsEnded(@NonNull List<C4DocumentEnded> docEnds, boolean pushing) {
        dispatcher.execute(() -> documentsEnded(docEnds, pushing));
    }

    @Nullable
    @VisibleForTesting
    CouchbaseLiteException getLastError() { return lastError; }

    @NonNull
    ReplicatorActivityLevel getState() {
        synchronized (getReplicatorLock()) { return status.getActivityLevel(); }
    }

    // callback from queueConflictResolution
    void onConflictResolved(Fn.NullableConsumer<CouchbaseLiteException> task, @NonNull ReplicatedDocument rDoc) {
        Log.i(DOMAIN, "Conflict resolved: %s", rDoc.getError(), rDoc.getID());
        List<C4ReplicatorStatus> pendingNotifications = null;
        synchronized (getReplicatorLock()) {
            pendingResolutions.remove(task);
            // if no more resolutions, deliver any outstanding status notifications
            if (pendingResolutions.isEmpty()) {
                pendingNotifications = new ArrayList<>(pendingStatusNotifications);
                pendingStatusNotifications.clear();
            }
        }

        notifyDocumentEnded(false, Arrays.asList(rDoc));

        if ((pendingNotifications != null) && (!pendingNotifications.isEmpty())) {
            for (C4ReplicatorStatus status: pendingNotifications) { dispatcher.execute(() -> statusChanged(status)); }
        }
    }

    void notifyDocumentEnded(boolean pushing, List<ReplicatedDocument> docs) {
        final DocumentReplication update = new DocumentReplication((Replicator) this, pushing, docs);
        Log.i(DOMAIN, "notifyDocumentEnded: %s", update);
        final Set<DocumentReplicationListenerToken> listenerTokens;
        synchronized (getReplicatorLock()) { listenerTokens = new HashSet<>(docEndedListeners); }
        for (DocumentReplicationListenerToken token: listenerTokens) { token.postChange(update); }
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

    @VisibleForTesting
    void runTaskConcurrently(@NonNull Runnable task) {
        CouchbaseLiteInternal.getExecutionService().getConcurrentExecutor().execute(task);
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @NonNull
    private Set<String> getPendingDocIds(@NonNull String scope, @NonNull String coll) throws CouchbaseLiteException {
        if (config.getType().equals(ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        verifyCollection(scope, coll);

        final Set<String> pending;
        try { pending = getOrCreateC4Replicator().getPendingDocIDs(scope, coll); }
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
    private boolean isDocPending(@NonNull String docId, @NonNull String scope, @NonNull String coll)
        throws CouchbaseLiteException {
        Preconditions.assertNotNull(docId, "document ID");

        if (config.getType().equals(ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        verifyCollection(scope, coll);

        try { return getOrCreateC4Replicator().isDocumentPending(docId, scope, coll); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed getting document pending status");
        }
    }

    private void verifyCollection(String scope, String name) {
        if (null == Fn.firstOrNull(
            config.getCollectionConfigs().keySet(),
            (coll) -> scope.equals(coll.getScope().getName()) && name.equals((coll.getName()))
        )) {
            throw new IllegalArgumentException(
                "This replicator is not replicating the collection " + scope + "." + name);
        }
    }

    @NonNull
    private C4Replicator getOrCreateC4Replicator() {
        // createReplicatorForTarget is going to seize this lock anyway: force in-order seizure
        synchronized (getDatabase().getDbLock()) {
            if (closed) { throw new IllegalStateException("Attempt to operate on a closed replicator"); }

            C4Replicator c4Repl = getC4Replicator();

            final Map<String, Object> options = config.getConnectionOptions();
            Log.d(LogDomain.REPLICATOR, "Replication options: " + options);

            if (c4Repl != null) {
                c4Repl.setOptions(FLEncoder.encodeMap(options));
                synchronized (getReplicatorLock()) { setProgressLevel(); }
                return c4Repl;
            }

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

    private void statusChanged(@NonNull C4ReplicatorStatus c4Status) {
        final ReplicatorChange change;
        final Set<ReplicatorChangeListenerToken> listenerTokens;
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

            listenerTokens = new HashSet<>(changeListeners);
        }

        postChange(isStopped(c4Status), change, listenerTokens);
    }

    // this will probably make this instance eligible for garbage collection...
    private void postChange(boolean isStopped, ReplicatorChange change, Set<ReplicatorChangeListenerToken> listeners) {
        if (isStopped) { getDatabase().removeActiveReplicator(this); }
        Fn.forAll(listeners, token -> token.postChange(change));
    }

    private void documentsEnded(@NonNull List<C4DocumentEnded> docEnds, boolean pushing) {
        Log.d(LogDomain.REPLICATOR, "AbstractReplicator.documentsEnded: " + docEnds.size());

        final List<ReplicatedDocument> unconflictedDocs = new ArrayList<>();

        for (C4DocumentEnded docEnd: docEnds) {
            if (docEnd.docId == null) {
                Log.w(LogDomain.REPLICATOR, "DocId is null in document end: " + docEnd);
                continue;
            }

            final ReplicationCollection coll = ReplicationCollection.getBinding(docEnd.token);
            if (coll == null) {
                Log.w(LogDomain.REPLICATOR, "No collection for document end: " + docEnd);
                continue;
            }

            final int errCode = docEnd.getErrorCode();
            final CouchbaseLiteException err = (errCode == 0)
                ? null
                : CouchbaseLiteException.toCouchbaseLiteException(
                    docEnd.getErrorDomain(),
                    errCode,
                    docEnd.getErrorInfo());

            final ReplicatedDocument rDoc
                = new ReplicatedDocument(coll.scope, coll.name, docEnd.docId, docEnd.flags, err);

            if (pushing || !CouchbaseLiteException.isConflict(err)) {
                unconflictedDocs.add(rDoc);
                continue;
            }

            queueConflictResolution(rDoc, coll.getConflictResolver());
        }

        if (!unconflictedDocs.isEmpty()) { notifyDocumentEnded(pushing, unconflictedDocs); }
    }

    // ??? Don't like logging with a lock.
    // Perhaps refactor so that only the assignment to status
    // happens with the lock?
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

        return new C4ReplicatorStatus(c4Status);
    }

    private void queueConflictResolution(@NonNull ReplicatedDocument rDoc, @Nullable ConflictResolver resolver) {
        final Database db = getDatabase();
        Log.i(
            DOMAIN,
            "%s: pulled conflicting version of '%s.%s.%s#%s'",
            this, db.getName(), rDoc.getScope(), rDoc.getCollection(), rDoc.getID());

        final Fn.NullableConsumer<CouchbaseLiteException> continuation
            = new Fn.NullableConsumer<CouchbaseLiteException>() {
            public void accept(CouchbaseLiteException err) {
                rDoc.setError(err);
                onConflictResolved(this, rDoc);
            }
        };

        final Runnable task = () -> db.resolveReplicationConflict(resolver, rDoc, continuation);

        synchronized (getReplicatorLock()) {
            runTaskConcurrently(task);
            pendingResolutions.add(continuation);
        }
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
    private String description() { return baseDesc() + ", " + getDatabase() + " => " + config.getTarget() + "}"; }

    // keep this around
    @NonNull
    @SuppressWarnings({"PMD.UnusedPrivateMethod", "unused"})
    private String simpleDesc() { return baseDesc() + "}"; }

    @NonNull
    private String baseDesc() {
        return "Replicator{" + ClassUtils.objId(this) + "("
            + (config.isPull() ? "<" : "")
            + (config.isContinuous() ? "*" : "o")
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
}
