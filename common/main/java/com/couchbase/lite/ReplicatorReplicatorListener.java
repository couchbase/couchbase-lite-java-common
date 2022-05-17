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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.replicator.ReplicatorListener;
import com.couchbase.lite.internal.support.Log;


// just queue everything up for in-order processing.
final class ReplicatorReplicatorListener implements ReplicatorListener {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;
    private final Executor dispatcher;

    ReplicatorReplicatorListener(@NonNull Executor dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public void statusChanged(@Nullable C4Replicator repl, @Nullable C4ReplicatorStatus status) {
        Log.i(DOMAIN, "ReplicatorListener.statusChanged, repl: %s, status: %s", repl, status);

        final AbstractReplicator replicator = (repl == null) ? null : repl.getReplicator();
        if (replicator == null) {
            Log.w(DOMAIN, "ReplicatorListener.statusChanged, replicator is null");
            return;
        }

        if (status == null) {
            Log.w(DOMAIN, "ReplicatorListener.statusChanged, status is null");
            return;
        }

        dispatcher.execute(() -> replicator.c4StatusChanged(status));
    }

    @Override
    public void documentEnded(@Nullable C4Replicator repl, boolean pushing, @Nullable C4DocumentEnded[] documents) {
        Log.i(DOMAIN, "C4ReplicatorListener.documentEnded, repl: %s, pushing: %s", repl, pushing);

        final AbstractReplicator replicator = (repl == null) ? null : repl.getReplicator();
        if (replicator == null) {
            Log.w(DOMAIN, "ReplicatorListener.documentEnded, replicator is null");
            return;
        }

        if (documents == null) {
            Log.w(DOMAIN, "ReplicatorListener.documentEnded, documents is null");
            return;
        }

        dispatcher.execute(() -> replicator.documentEnded(pushing, documents));
    }
}
