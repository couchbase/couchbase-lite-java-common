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
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Base class for a removable subscription to an observable.
 */
public abstract class ListenerToken implements AutoCloseable {
    @Nullable
    private final Executor executor;
    private final Fn.Consumer<ListenerToken> onRemove;
    private final AtomicBoolean active = new AtomicBoolean(true);

    protected ListenerToken(@Nullable Executor executor, @NonNull Fn.Consumer<ListenerToken> onRemove) {
        this.executor = executor;
        this.onRemove = Preconditions.assertNotNull(onRemove, "onRemove task");
    }

    @NonNull
    @Override
    public String toString() { return " on " + executor + " then " + onRemove; }

    @Override
    public void close() { remove(); }

    public void remove() {
        if (active.getAndSet(false)) { onRemove.accept(this); }
    }

    protected void send(@NonNull Runnable notification) { getExecutor().execute(notification); }

    @VisibleForTesting
    boolean isActive() { return active.get(); }

    @VisibleForTesting
    @NonNull
    Executor getExecutor() {
        return (executor != null) ? executor : CouchbaseLiteInternal.getExecutionService().getDefaultExecutor();
    }
}
