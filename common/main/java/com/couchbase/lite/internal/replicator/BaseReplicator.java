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
package com.couchbase.lite.internal.replicator;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


public abstract class BaseReplicator implements AutoCloseable {
    private final Object lock = new Object();
    protected final Executor dispatcher = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    @GuardedBy("lock")
    @Nullable
    private C4Replicator c4Replicator;

    @GuardedBy("lock")
    @NonNull
    private String id;

    protected BaseReplicator() { id = getReplId(null) + ClassUtils.objId(this); }

    protected final void setC4Replicator(@NonNull C4Replicator newC4Repl) {
        setC4ReplicatorInternal(newC4Repl);
    }

    @NonNull
    protected final String getId() {
        synchronized (getReplicatorLock()) { return id; }
    }

    @Nullable
    protected final C4Replicator getC4Replicator() {
        synchronized (getReplicatorLock()) { return c4Replicator; }
    }

    protected final void closeC4Replicator() { setC4ReplicatorInternal(null); }

    @NonNull
    protected final Object getReplicatorLock() { return lock; }

    private void setC4ReplicatorInternal(@Nullable C4Replicator newC4Repl) {
        final String oldReplId;
        final String newReplId;
        final C4Replicator oldC4Repl;
        synchronized (getReplicatorLock()) {
            oldReplId = id;
            oldC4Repl = c4Replicator;

            newReplId = getReplId(newC4Repl);
            id = newReplId + ClassUtils.objId(this);

            c4Replicator = newC4Repl;
        }
        Log.d(LogDomain.REPLICATOR, "%s: new c4Repl: %s", oldReplId, newReplId, this);

        if (oldC4Repl != null) { oldC4Repl.close(); }
    }

    @NonNull
    private String getReplId(@Nullable C4Replicator repl) {
        return (repl == null) ? "unattached" : repl.getReplId();
    }
}
