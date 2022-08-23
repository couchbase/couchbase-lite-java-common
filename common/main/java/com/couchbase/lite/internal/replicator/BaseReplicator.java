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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


public abstract class BaseReplicator implements AutoCloseable {
    private final Object lock = new Object();
    protected final Executor dispatcher = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    @Nullable
    private C4Replicator c4Replicator;

    protected BaseReplicator() { }

    @Override
    public void close() {
        final C4Replicator c4Repl;
        synchronized (getReplicatorLock()) {
            c4Repl = c4Replicator;
            c4Replicator = null;
        }
        if (c4Repl != null) { c4Repl.close(); }
    }

    @Nullable
    public C4Replicator getC4Replicator() {
        synchronized (getReplicatorLock()) { return c4Replicator; }
    }

    protected void setC4Replicator(@NonNull C4Replicator c4Repl) {
        Log.d(
            LogDomain.REPLICATOR,
            "Setting c4 replicator %s for replicator %s", ClassUtils.objId(c4Repl), ClassUtils.objId(this));

        synchronized (getReplicatorLock()) { c4Replicator = c4Repl; }
    }

    @NonNull
    protected Object getReplicatorLock() { return lock; }
}
