//
// AbstractReplicator.java
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
package com.couchbase.lite;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.CBLStatus;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.C4ReplicatorMode;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.core.InternalReplicator;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;
import com.couchbase.lite.utils.Fn;


/**
 * A replicator for replicating document changes between a local database and a target database.
 * The replicator can be bidirectional or either push or pull. The replicator can also be one-short
 * or continuous. The replicator runs asynchronously, so observe the status property to
 * be notified of progress.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyFields"})
public abstract class AbstractReplicator extends InternalReplicator {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;

    private static final Map<Integer, ActivityLevel> ACTIVITY_LEVEL_FROM_C4;

    static {
        final Map<Integer, ActivityLevel> m = new HashMap<>();
        m.put(C4ReplicatorStatus.ActivityLevel.STOPPED, ActivityLevel.STOPPED);
        m.put(C4ReplicatorStatus.ActivityLevel.OFFLINE, ActivityLevel.OFFLINE);
        m.put(C4ReplicatorStatus.ActivityLevel.CONNECTING, ActivityLevel.CONNECTING);
        m.put(C4ReplicatorStatus.ActivityLevel.IDLE, ActivityLevel.IDLE);
        m.put(C4ReplicatorStatus.ActivityLevel.BUSY, ActivityLevel.BUSY);
        ACTIVITY_LEVEL_FROM_C4 = Collections.unmodifiableMap(m);
    }


    /**
     * Activity level of a replicator.
     */
    public enum ActivityLevel {
        /**
         * The replication is finished or hit a fatal error.
         */
        STOPPED,
        /**
         * The replicator is offline because the remote host is unreachable.
         */
        OFFLINE,
        /**
         * The replicator is connecting to the remote host.
         */
        CONNECTING,
        /**
         * The replication is inactive; either waiting for changes or offline
         * as the remote host is unreachable.
         */
        IDLE,
        /**
         * The replication is actively transferring data.
         */
        BUSY
    }


    /**
     * Progress of a replicator. If `total` is zero, the progress is indeterminate; otherwise,
     * dividing the two will produce a fraction that can be used to draw a progress bar.
     */
    public static final class Progress {
        //---------------------------------------------
        // member variables
        //---------------------------------------------

        // The number of completed changes processed.
        private final long completed;

        // The total number of changes to be processed.
        private final long total;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        private Progress(long completed, long total) {
            this.completed = completed;
            this.total = total;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The number of completed changes processed.
         */
        public long getCompleted() { return completed; }

        /**
         * The total number of changes to be processed.
         */
        public long getTotal() { return total; }

        @NonNull
        @Override
        public String toString() { return "Progress{" + "completed=" + completed + ", total=" + total + '}'; }

        Progress copy() { return new Progress(completed, total); }
    }


    /**
     * Combined activity level and progress of a replicator.
     */
    public static final class Status {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private final ActivityLevel activityLevel;
        private final Progress progress;
        private final CouchbaseLiteException error;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        // Note: c4Status.level is current matched with CBLReplicatorActivityLevel:
        public Status(C4ReplicatorStatus c4Status) {
            this(
                ACTIVITY_LEVEL_FROM_C4.get(c4Status.getActivityLevel()),
                new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal()),
                c4Status.getErrorCode() != 0 ? CBLStatus.convertError(c4Status.getC4Error()) : null);
        }

        private Status(ActivityLevel activityLevel, Progress progress, CouchbaseLiteException error) {
            this.activityLevel = activityLevel;
            this.progress = progress;
            this.error = error;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The current activity level.
         */
        @NonNull
        public ActivityLevel getActivityLevel() { return activityLevel; }

        /**
         * The current progress of the replicator.
         */
        @NonNull
        public Replicator.Progress getProgress() { return progress; }

        public CouchbaseLiteException getError() { return error; }

        @NonNull
        @Override
        public String toString() {
            return "Status{" + "activityLevel=" + activityLevel + ", progress=" + progress + ", error=" + error + '}';
        }

        Status copy() { return new Status(activityLevel, progress.copy(), error); }
    }


    /**
     * An enum representing level of opt in on progress of replication
     * OVERALL: No additional replication progress callback
     * PER_DOCUMENT: >=1 Every document replication ended callback
     * PER_ATTACHMENT: >=2 Every blob replication progress callback
     */
    enum ReplicatorProgressLevel {
        OVERALL(0),
        PER_DOCUMENT(1),
        PER_ATTACHMENT(2);

        private final int value;

        ReplicatorProgressLevel(int value) { this.value = value; }
    }


    // just queue everything up for in-order processing.
    final class ReplicatorListener implements C4ReplicatorListener {
        @Override
        public void statusChanged(
            @Nullable C4Replicator repl,
            @NonNull C4ReplicatorStatus status,
            @Nullable Object context) {
            Log.i(DOMAIN, "C4ReplicatorListener.statusChanged, context: %s, status: %s", context, status);

            if (context == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.statusChanged, context is null!");
                return;
            }

            final AbstractReplicator replicator = (AbstractReplicator) context;
            if (!replicator.isSameReplicator(repl)) { return; } // this handles repl == null

            dispatcher.execute(() -> replicator.c4StatusChanged(status));
        }

        @Override
        public void documentEnded(
            @NonNull C4Replicator repl,
            boolean pushing,
            @Nullable C4DocumentEnded[] documents,
            @Nullable Object context) {
            Log.i(DOMAIN, "C4ReplicatorListener.documentEnded, context: %s, pushing: %s", context, pushing);

            if (context == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, context is null!");
                return;
            }

            final AbstractReplicator replicator = (AbstractReplicator) context;
            if (!replicator.isSameReplicator(repl)) { return; } // this handles repl == null

            if (documents == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, documents is null!");
                return;
            }

            dispatcher.execute(() -> replicator.documentEnded(pushing, documents));
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////  R E P L I C A T O R   ////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    final ReplicatorConfiguration config;

    private final Object lock = new Object();

    private final Executor dispatcher = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    @GuardedBy("lock")
    private final Set<ReplicatorChangeListenerToken> changeListenerTokens = new HashSet<>();
    @GuardedBy("lock")
    private final Set<DocumentReplicationListenerToken> docEndedListenerTokens = new HashSet<>();

    @NonNull
    private final Set<Fn.Consumer> pendingResolutions = new HashSet<>();
    @NonNull
    private final Deque<C4ReplicatorStatus> pendingStatusNotifications = new LinkedList<>();
    @NonNull
    private final C4ReplicatorListener c4ReplListener = new ReplicatorListener();
    @NonNull
    private final SocketFactory socketFactory;

    @GuardedBy("lock")
    private Status status = new Status(ActivityLevel.IDLE, new Progress(0, 0), null);
    @GuardedBy("lock")
    private ReplicatorProgressLevel progressLevel = ReplicatorProgressLevel.OVERALL;

    @GuardedBy("lock")
    private C4ReplicationFilter c4ReplPushFilter;
    @GuardedBy("lock")
    private C4ReplicationFilter c4ReplPullFilter;

    // Reset the replicator checkpoint.
    @GuardedBy("lock")
    private boolean shouldResetCheckpoint;

    // Do something with these (for auth)
    @GuardedBy("lock")
    private Map<String, Object> responseHeaders;

    @GuardedBy("lock")
    private CouchbaseLiteException lastError;

    private volatile String desc;

    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config replicator configuration
     */
    protected AbstractReplicator(@NonNull ReplicatorConfiguration config) {
        Preconditions.assertNotNull(config, "config");
        this.config = config.readonlyCopy();
        this.socketFactory = new SocketFactory(config);
    }

    /**
     * Starts the replicator. This method returns immediately; the replicator runs asynchronously
     * and will report its progress through the replicator change notification.
     */
    public void start() {
        Log.i(DOMAIN, "Replicator is starting .....");
        final C4Replicator repl = lazyCreateC4Replicator();

        repl.start();

        C4ReplicatorStatus status = repl.getStatus();
        if (status == null) {
            status = new C4ReplicatorStatus(
                C4ReplicatorStatus.ActivityLevel.STOPPED,
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.UNEXPECTED_ERROR);
        }

        synchronized (lock) { status = updateStateProperties(status); }

        c4ReplListener.statusChanged(repl, status, this);
    }

    /**
     * Stops a running replicator. This method returns immediately; when the replicator actually
     * stops, the replicator will change its status's activity level to `kCBLStopped`
     * and the replicator change notification will be notified accordingly.
     */
    public void stop() {
        Log.i(DOMAIN, "%s: Replicator is stopping ...", this);
        lazyCreateC4Replicator().stop();
    }

    /**
     * The replicator's configuration.
     *
     * @return this replicator's configuration
     */
    @NonNull
    public ReplicatorConfiguration getConfig() { return config.readonlyCopy(); }

    /**
     * The replicator's current status: its activity level and progress. Observable.
     *
     * @return this replicator's status
     */
    @NonNull
    public Status getStatus() {
        synchronized (lock) { return status.copy(); }
    }

    /**
     * Get a best effort list of documents still pending replication.
     *
     * @return a set of ids for documents still awaiting replication.
     */
    @NonNull
    public Set<String> getPendingDocumentIds() throws CouchbaseLiteException {
        if (config.getReplicatorType().equals(ReplicatorConfiguration.ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        final Set<String> pending;
        try { pending = lazyCreateC4Replicator().getPendingDocIDs(); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e, "Failed fetching pending documentIds"); }

        if (pending == null) { throw new IllegalStateException("Pending doc ids is unexpectedly null"); }

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

        if (config.getReplicatorType().equals(ReplicatorConfiguration.ReplicatorType.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        try { return lazyCreateC4Replicator().isDocumentPending(docId); }
        catch (LiteCoreException e) { throw CBLStatus.convertException(e, "Failed getting document pending status"); }
    }

    /**
     * Adds a change listener for the changes in the replication status and progress.
     * <p>
     * The changes will be delivered on the UI thread for the Android platform
     * On other Java platforms, the callback will occur on an arbitrary thread.
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
     * on the UI thread on Android platform and on an arbitrary thread on other Java platform.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(Executor executor, @NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (lock) {
            final ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
            changeListenerTokens.add(token);
            return token;
        }
    }

    /**
     * Remove the given ReplicatorChangeListener or DocumentReplicationListener from the this replicator.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     */
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");

        synchronized (lock) {
            if (token instanceof ReplicatorChangeListenerToken) {
                changeListenerTokens.remove(token);
                return;
            }

            if (token instanceof DocumentReplicationListenerToken) {
                docEndedListenerTokens.remove(token);
                if (docEndedListenerTokens.isEmpty()) { progressLevel = ReplicatorProgressLevel.OVERALL; }
                return;
            }

            throw new IllegalArgumentException("unexpected token: " + token);
        }
    }

    /**
     * Adds a listener for receiving the replication status of the specified document. The status will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
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
        synchronized (lock) {
            progressLevel = ReplicatorProgressLevel.PER_DOCUMENT;
            final DocumentReplicationListenerToken token = new DocumentReplicationListenerToken(executor, listener);
            docEndedListenerTokens.add(token);
            return token;
        }
    }

    /**
     * Reset the replicator's local checkpoint.
     * This method can only be called when the replicator is in a stopped state.
     * If this replicator is started after this flag is set, it will read all of the changes since
     * the beginning of time, from the remote database.
     * The flag affects only the first run of the replicator after it is set.  If it is started yet again,
     * after it stops, it will not, again, read all of the changes.
     */
    public void resetCheckpoint() {
        synchronized (lock) {
            if (getC4Replicator() != null) { // !!! This is broken: CBL-787
                throw new IllegalStateException(Log.lookupStandardMessage("ReplicatorNotStopped"));
            }
            shouldResetCheckpoint = true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (desc == null) { desc = description(); }
        return desc;
    }

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @GuardedBy("lock")
    protected abstract C4Replicator createReplicatorForTarget(Endpoint target) throws LiteCoreException;

    /**
     * Create and return a c4Replicator targeting the passed URI
     *
     * @param remoteUri a URI for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
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

        return config.getDatabase().createRemoteReplicator(
            (Replicator) this,
            remoteUri.getScheme(),
            remoteUri.getHost(),
            port,
            path,
            dbName,
            mkmode(config.isPush(), continuous),
            mkmode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory,
            C4Socket.NO_FRAMING);
    }

    /**
     * Create and return a c4Replicator targeting the passed Database
     *
     * @param otherDb a local database for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getLocalC4Replicator(@NonNull Database otherDb) throws LiteCoreException {
        final boolean continuous = config.isContinuous();
        return config.getDatabase().createLocalReplicator(
            (Replicator) this,
            otherDb.getC4Database(),
            mkmode(config.isPush(), continuous),
            mkmode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter);
    }

    /**
     * Create and return a c4Replicator.
     * The socket factory is responsible for setting up the target
     *
     * @param framing the framing mode (C4Socket.XXX_FRAMING)
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getMessageC4Replicator(int framing) throws LiteCoreException {
        final boolean continuous = config.isContinuous();
        return config.getDatabase().createRemoteReplicator(
            (Replicator) this,
            C4Socket.MESSAGE_SCHEME,
            null,
            0,
            null,
            null,
            mkmode(config.isPush(), continuous),
            mkmode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory,
            framing);
    }

    //---------------------------------------------
    // Package visible methods
    //
    // Some of these are package protected only to avoid a synthetic accessor
    //---------------------------------------------

    CouchbaseLiteException getLastError() { return lastError; }

    void c4StatusChanged(@NonNull C4ReplicatorStatus c4Status) {
        final ReplicatorChange change;
        final List<ReplicatorChangeListenerToken> tokens;

        synchronized (lock) {
            Log.i(
                DOMAIN,
                "%s: status changed: (%d, %d) @%s",
                this, pendingResolutions.size(), pendingStatusNotifications.size(), c4Status);

            if (!pendingResolutions.isEmpty()) { pendingStatusNotifications.add(c4Status); }
            if (!pendingStatusNotifications.isEmpty()) { return; }

            if (responseHeaders == null) {
                final byte[] h = lazyCreateC4Replicator().getResponseHeaders();
                if (h != null) { responseHeaders = FLValue.fromData(h).asDict(); }
            }

            // Update my properties:
            updateStateProperties(c4Status);

            // Post notification
            // Replicator.getStatus() creates a copy of Status.
            change = new ReplicatorChange((Replicator) this, this.getStatus());
            tokens = new ArrayList<>(changeListenerTokens);
        }

        if (c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.STOPPED) {
            config.getDatabase().removeActiveReplicator((Replicator) this); // this is likely to dealloc me
        }

        for (ReplicatorChangeListenerToken token: tokens) { token.notify(change); }
    }

    void documentEnded(boolean pushing, C4DocumentEnded... docEnds) {
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

                error = CBLStatus.convertError(c4Error);
            }

            unconflictedDocs.add(new ReplicatedDocument(docId, docEnd.getFlags(), error, docEnd.errorIsTransient()));
        }

        if (!unconflictedDocs.isEmpty()) { notifyDocumentEnded(pushing, unconflictedDocs); }
    }

    // callback from queueConflictResolution
    void onConflictResolved(Fn.Consumer task, String docId, int flags, CouchbaseLiteException err) {
        Log.i(DOMAIN, "Conflict resolved: %s", docId);
        List<C4ReplicatorStatus> pendingNotifications = null;
        synchronized (lock) {
            pendingResolutions.remove(task);
            // if no more resolutions, deliver any outstanding status notifications
            if (pendingResolutions.isEmpty()) {
                pendingNotifications = new ArrayList<>(pendingStatusNotifications);
                pendingStatusNotifications.clear();
            }
        }

        notifyDocumentEnded(false, Arrays.asList(new ReplicatedDocument(docId, flags, err, false)));

        if ((pendingNotifications != null) && (!pendingNotifications.isEmpty())) {
            for (C4ReplicatorStatus status: pendingNotifications) {
                dispatcher.execute(() -> c4StatusChanged(status));
            }
        }
    }

    void notifyDocumentEnded(boolean pushing, List<ReplicatedDocument> docs) {
        final DocumentReplication update = new DocumentReplication((Replicator) this, pushing, docs);
        final List<DocumentReplicationListenerToken> tokens;
        synchronized (lock) { tokens = new ArrayList<>(docEndedListenerTokens); }
        for (DocumentReplicationListenerToken token: tokens) { token.notify(update); }
        Log.i(DOMAIN, "notifyDocumentEnded: %s" + update);
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @NonNull
    private C4Replicator lazyCreateC4Replicator() {
        synchronized (lock) {
            C4Replicator c4Repl = getC4Replicator();
            if (c4Repl == null) {
                setupFilters();
                try {
                    c4Repl = createReplicatorForTarget(config.getTarget());
                    setC4Replicator(c4Repl);
                }
                catch (LiteCoreException e) {
                    throw new IllegalStateException("Could not create replicator", CBLStatus.convertException(e));
                }
            }
            return c4Repl;
        }
    }

    private boolean isSameReplicator(C4Replicator repl) {
        synchronized (lock) { return repl == getC4Replicator(); }
    }

    @GuardedBy("lock")
    private C4ReplicatorStatus updateStateProperties(@NonNull C4ReplicatorStatus c4Status) {
        final C4ReplicatorStatus c4ReplStatus = c4Status.copy();

        CouchbaseLiteException error = null;
        if (c4Status.getErrorCode() != 0) {
            error = CBLStatus.convertException(
                c4Status.getErrorDomain(),
                c4Status.getErrorCode(),
                c4Status.getErrorInternalInfo());
            lastError = error;
        }

        final ActivityLevel level = ACTIVITY_LEVEL_FROM_C4.get(c4Status.getActivityLevel());

        status = new Status(
            level,
            new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal()), error);

        Log.i(DOMAIN, "%s is %s, progress %d/%d, error: %s",
            this,
            (level == null) ? "unknown" : level.toString(),
            c4Status.getProgressUnitsCompleted(),
            c4Status.getProgressUnitsTotal(),
            error);

        return c4ReplStatus;
    }

    private void queueConflictResolution(@NonNull String docId, int flags) {
        Log.i(DOMAIN, "%s: pulled conflicting version of '%s'", this, docId);

        final ExecutionService.CloseableExecutor executor
            = CouchbaseLiteInternal.getExecutionService().getConcurrentExecutor();
        final Database db = config.getDatabase();
        final ConflictResolver resolver = config.getConflictResolver();
        final Fn.Consumer<CouchbaseLiteException> task = new Fn.Consumer<CouchbaseLiteException>() {
            public void accept(CouchbaseLiteException err) { onConflictResolved(this, docId, flags, err); }
        };

        synchronized (lock) {
            executor.execute(() -> db.resolveReplicationConflict(resolver, docId, task));
            pendingResolutions.add(task);
        }
    }

    private byte[] getFleeceOptions() {
        // Encode the options:
        final Map<String, Object> options = config.effectiveOptions();

        synchronized (lock) {
            options.put(AbstractReplicatorConfiguration.REPLICATOR_OPTION_PROGRESS_LEVEL, progressLevel.value);

            // Update shouldResetCheckpoint flag if needed:
            if (shouldResetCheckpoint) {
                options.put(AbstractReplicatorConfiguration.REPLICATOR_RESET_CHECKPOINT, true);
                // reset the flag: if this replicator is restarted, it will *not* reset the checkpoint
                shouldResetCheckpoint = false;
            }
        }

        byte[] optionsFleece = null;
        if (!options.isEmpty()) {
            final FLEncoder enc = new FLEncoder();
            try {
                enc.write(options);
                optionsFleece = enc.finish();
            }
            catch (LiteCoreException e) { Log.e(DOMAIN, "Failed to encode", e); }
            finally { enc.free(); }
        }

        return optionsFleece;
    }

    @GuardedBy("lock")
    private void setupFilters() {
        if (config.getPushFilter() != null) {
            c4ReplPushFilter = (docID, revId, flags, dict, isPush, context) ->
                ((AbstractReplicator) context).filterDocument(docID, revId, documentFlags(flags), dict, isPush);
        }

        if (config.getPullFilter() != null) {
            c4ReplPullFilter = (docID, revId, flags, dict, isPush, context) ->
                ((AbstractReplicator) context).filterDocument(docID, revId, documentFlags(flags), dict, isPush);
        }
    }

    private int mkmode(boolean active, boolean continuous) {
        final C4ReplicatorMode mode = (!active)
            ? C4ReplicatorMode.C4_DISABLED
            : ((continuous) ? C4ReplicatorMode.C4_CONTINUOUS : C4ReplicatorMode.C4_ONE_SHOT);
        return mode.getVal();
    }

    private EnumSet<DocumentFlag> documentFlags(int flags) {
        final EnumSet<DocumentFlag> documentFlags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.RevisionFlags.DELETED) == C4Constants.RevisionFlags.DELETED) {
            documentFlags.add(DocumentFlag.DocumentFlagsDeleted);
        }
        if ((flags & C4Constants.RevisionFlags.PURGED) == C4Constants.RevisionFlags.PURGED) {
            documentFlags.add(DocumentFlag.DocumentFlagsAccessRemoved);
        }
        return documentFlags;
    }

    private boolean filterDocument(
        String docId,
        String revId,
        EnumSet<DocumentFlag> flags,
        long dict,
        boolean isPush) {
        final ReplicationFilter filter = (isPush) ? config.getPushFilter() : config.getPullFilter();
        return filter.filtered(new Document(config.getDatabase(), docId, revId, new FLDict(dict)), flags);
    }

    // Decompose a path into its elements.
    private Deque<String> splitPath(String fullPath) {
        final Deque<String> path = new ArrayDeque<>();
        for (String element: fullPath.split("/")) {
            if (element.length() > 0) { path.addLast(element); }
        }
        return path;
    }

    private String description() { return baseDesc() + "," + config.getDatabase() + "," + config.getTarget() + "]"; }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String simpleDesc() { return baseDesc() + "}"; }

    private String baseDesc() {
        return "Replicator{@"
            + Integer.toHexString(hashCode()) + ","
            + (config.isPull() ? "<" : "")
            + (config.isContinuous() ? "*" : "-")
            + (config.isPush() ? ">" : "");
    }
}
