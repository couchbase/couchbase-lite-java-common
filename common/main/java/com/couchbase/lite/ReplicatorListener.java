//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.Executor;

import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.support.Log;


// just queue everything up for in-order processing.
final class ReplicatorListener implements C4ReplicatorListener {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;
    private final Executor dispatcher;

    ReplicatorListener(@NonNull Executor dispatcher) { this.dispatcher = dispatcher; }

    @Override
    public void statusChanged(
        @Nullable C4Replicator c4Repl,
        @Nullable C4ReplicatorStatus status,
        @Nullable Object repl) {
        Log.i(DOMAIN, "ReplicatorListener.statusChanged, repl: %s, status: %s", repl, status);

        final AbstractReplicator replicator = verifyReplicator(c4Repl, repl);
        if (replicator == null) { return; }

        if (status == null) {
            Log.w(DOMAIN, "C4ReplicatorListener.statusChanged, status is null");
            return;
        }

        dispatcher.execute(() -> replicator.c4StatusChanged(status));
    }

    @Override
    public void documentEnded(
        @NonNull C4Replicator c4Repl,
        boolean pushing,
        @Nullable C4DocumentEnded[] documents,
        @Nullable Object repl) {
        Log.i(DOMAIN, "C4ReplicatorListener.documentEnded, repl: %s, pushing: %s", repl, pushing);

        if (!(repl instanceof AbstractReplicator)) {
            Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, repl is null");
            return;
        }

        final AbstractReplicator replicator = verifyReplicator(c4Repl, repl);
        if (replicator == null) { return; }

        if (documents == null) {
            Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, documents is null");
            return;
        }

        dispatcher.execute(() -> replicator.documentEnded(pushing, documents));
    }

    @Nullable
    private AbstractReplicator verifyReplicator(@Nullable C4Replicator c4Repl, @Nullable Object repl) {
        final AbstractReplicator replicator
            = (!(repl instanceof AbstractReplicator)) ? null : (AbstractReplicator) repl;

        if ((replicator != null) && (c4Repl == replicator.getC4Replicator())) { return replicator; }

        Log.w(DOMAIN, "C4ReplicatorListener: c4replicator and replicator don't match: %s :: %s", c4Repl, repl);

        return null;
    }
}
