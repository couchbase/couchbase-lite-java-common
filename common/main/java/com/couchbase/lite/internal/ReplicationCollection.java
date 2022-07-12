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
package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.couchbase.lite.Collection;
import com.couchbase.lite.CollectionConfiguration;
import com.couchbase.lite.ConflictResolver;
import com.couchbase.lite.DocumentFlag;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.ReplicationFilter;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.exec.ClientTask;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.support.Log;


public final class ReplicationCollection implements AutoCloseable {

    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    private static final LogDomain LOG_DOMAIN = LogDomain.REPLICATOR;

    //-------------------------------------------------------------------------
    // Types
    //-------------------------------------------------------------------------
    @FunctionalInterface
    interface C4Filter {
        boolean test(@NonNull String docId, @NonNull String revId, long body, int flags);
    }

    private static final class CollectionFilter implements C4Filter {
        @NonNull
        private final BaseCollection collection;
        @NonNull
        private final ReplicationFilter filter;

        CollectionFilter(@NonNull Collection collection, @NonNull ReplicationFilter filter) {
            this.collection = collection;
            this.filter = filter;
        }

        @Override
        public boolean test(@NonNull String docId, @NonNull String revId, long body, int flags) {
            return filter.filtered(
                collection.createFilterDocument(docId, revId, FLDict.create(body)),
                getDocumentFlags(flags));
        }

        @NonNull
        private EnumSet<DocumentFlag> getDocumentFlags(int flags) {
            final EnumSet<DocumentFlag> fs = EnumSet.noneOf(DocumentFlag.class);
            if (C4Constants.hasFlags(flags, C4Constants.RevisionFlags.DELETED)) { fs.add(DocumentFlag.DELETED); }
            if (C4Constants.hasFlags(flags, C4Constants.RevisionFlags.PURGED)) { fs.add(DocumentFlag.ACCESS_REMOVED); }
            return fs;
        }
    }

    //-------------------------------------------------------------------------
    // Static fields
    //-------------------------------------------------------------------------
    // Lookup table: maps a random token (context) to its companion Java C4ReplicationCollection
    @NonNull
    @VisibleForTesting
    static final TaggedWeakPeerBinding<ReplicationCollection> BOUND_COLLECTIONS = new TaggedWeakPeerBinding<>();

    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    // It is called from a native thread that Java has never even heard of...
    static boolean filterCallback(
        long collToken,
        @Nullable String scope,
        @Nullable String name,
        @Nullable String docID,
        @Nullable String revID,
        int flags,
        long body,
        boolean isPush) {
        final ReplicationCollection coll = BOUND_COLLECTIONS.getBinding(collToken);
        Log.d(
            LOG_DOMAIN,
            "Running %s filter for doc %s@%s, %s@%s",
            (isPush ? "push" : "pull"),
            docID,
            revID,
            collToken,
            coll);
        if (coll == null) {
            Log.w(LOG_DOMAIN, "Request to filter unrecognized collection: " + scope + "." + name);
            return true;
        }

        final C4Filter filter = (isPush) ? coll.getPushFilter() : coll.getPullFilter();
        if (filter == null) { return true; }

        // This shouldn't happen.
        // If it does, we have no idea what is going on and shouldn't get in the way.
        if ((docID == null) || (revID == null)) {
            Log.w(LOG_DOMAIN, "Ignoring filter request for null %s/%s", docID, revID);
            return true;
        }

        final ClientTask<Boolean> task = new ClientTask<>(() -> filter.test(docID, revID, body, flags));
        task.execute();

        final Exception err = task.getFailure();
        if (err != null) {
            Log.w(LOG_DOMAIN, "Replication filter failed", err);
            return false;
        }

        final Boolean accepted = task.getResult();
        return (accepted != null) && accepted;
    }

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static ReplicationCollection[] createAll(
        @NonNull Set<Collection> collections,
        @Nullable Map<String, Object> options) {
        final ReplicationCollection[] replColls = new ReplicationCollection[collections.size()];
        int i = 0;
        for (Collection coll: collections) { replColls[i++] = create(coll, options, null, null, null); }
        return replColls;
    }

    @NonNull
    public static ReplicationCollection[] createAll(
        @NonNull Map<Collection, CollectionConfiguration> collections,
        @Nullable Map<String, Object> options) {
        final ReplicationCollection[] replColls = new ReplicationCollection[collections.size()];
        int i = 0;
        for (Map.Entry<Collection, CollectionConfiguration> entry: collections.entrySet()) {
            replColls[i++] = create(entry.getKey(), entry.getValue(), options);
        }
        return replColls;
    }

    @NonNull
    public static ReplicationCollection create(
        @NonNull Collection coll,
        @NonNull CollectionConfiguration config,
        @Nullable Map<String, Object> opts) {
        final Map<String, Object> options = (opts == null) ? new HashMap<>() : new HashMap<>(opts);

        final List<String> documentIDs = config.getDocumentIDs();
        if ((documentIDs != null) && (!documentIDs.isEmpty())) {
            options.put(C4Replicator.REPLICATOR_OPTION_DOC_IDS, documentIDs);
        }

        final List<String> channels = config.getChannels();
        if ((channels != null) && (!channels.isEmpty())) {
            options.put(C4Replicator.REPLICATOR_OPTION_CHANNELS, channels);
        }

        return create(coll, options, config.getPushFilter(), config.getPullFilter(), config.getConflictResolver());
    }

    @SuppressWarnings("CheckFunctionalParameters")
    @NonNull
    public static ReplicationCollection create(
        @NonNull Collection coll,
        @Nullable Map<String, Object> options,
        @Nullable ReplicationFilter pushFilter,
        @Nullable ReplicationFilter pullFilter,
        @Nullable ConflictResolver resolver) {
        final long token = BOUND_COLLECTIONS.reserveKey();
        final ReplicationCollection replColl = new ReplicationCollection(
            token,
            coll.getScope().getName(),
            coll.getName(),
            FLEncoder.encodeMap(options),
            (pushFilter == null) ? null : new CollectionFilter(coll, pushFilter),
            (pullFilter == null) ? null : new CollectionFilter(coll, pullFilter),
            resolver);
        BOUND_COLLECTIONS.bind(token, replColl);
        return replColl;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    // These fields are accessed by reflection.  Don't change them.

    @VisibleForTesting
    final long token;

    @VisibleForTesting
    @NonNull
    final String scope;
    @VisibleForTesting
    @NonNull
    final String name;

    @VisibleForTesting
    @Nullable
    final byte[] options;

    @VisibleForTesting
    @Nullable
    final C4Filter c4PushFilter;
    @VisibleForTesting
    @Nullable
    final C4Filter c4PullFilter;
    @VisibleForTesting
    @Nullable
    final ConflictResolver resolver;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private ReplicationCollection(
        long token,
        @NonNull String scope,
        @NonNull String name,
        @Nullable byte[] options,
        @Nullable C4Filter pushFilter,
        @Nullable C4Filter pullFilter,
        @Nullable ConflictResolver resolver) {
        this.token = token;
        this.scope = scope;
        this.name = name;
        this.options = options;
        this.c4PushFilter = pushFilter;
        this.c4PullFilter = pullFilter;
        this.resolver = resolver;
    }

    //-------------------------------------------------------------------------
    // Instance Methods
    //-------------------------------------------------------------------------

    @Nullable
    public ConflictResolver getConflictResolver() { return resolver; }

    @Nullable
    public C4Filter getPushFilter() { return c4PushFilter; }

    @Nullable
    public C4Filter getPullFilter() { return c4PullFilter; }

    @Override
    public void close() { BOUND_COLLECTIONS.unbind(token); }

    @NonNull
    @Override
    public String toString() { return "ReplicationCollection {" + scope + "." + name + "}"; }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { close(); }
        finally { super.finalize(); }
    }
}
