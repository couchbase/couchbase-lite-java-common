//
// Copyright (c) 2020, 2018 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.internal.CouchbaseLiteInternal;


class ChangeListenerToken<T> implements ListenerToken {
    @NonNull
    private final ChangeListener<T> listener;
    @Nullable
    private final Executor executor;

    @Nullable
    private Object key;

    ChangeListenerToken(@Nullable Executor executor, @NonNull ChangeListener<T> listener) {
        this.executor = executor;
        this.listener = listener;
    }

    @Nullable
    public Object getKey() { return key; }

    public void setKey(@Nullable Object key) { this.key = key; }

    @NonNull
    @Override
    public String toString() { return "ChangeListenerToken{" + key + ", " + listener + ", " + executor + "}"; }

    void postChange(@NonNull T change) {
        final Executor exec = (executor != null)
            ? executor
            : CouchbaseLiteInternal.getExecutionService().getDefaultExecutor();
        exec.execute(() -> listener.changed(change));
    }
}
