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
package com.couchbase.lite.internal.listener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import com.couchbase.lite.ChangeListener;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.internal.utils.Fn;


public abstract class ChangeNotifier<T> {
    @NonNull
    private final Object lock = new Object();
    @NonNull
    private final Set<ChangeListenerToken<T>> listeners = new HashSet<>();

    @NonNull
    public final ChangeListenerToken<T> addChangeListener(
        @Nullable Executor executor,
        @NonNull ChangeListener<T> listener,
        @NonNull Fn.Consumer<ListenerToken> onRemove) {
        synchronized (lock) {
            final ChangeListenerToken<T> token = new ChangeListenerToken<>(listener, executor, onRemove);
            listeners.add(token);
            return token;
        }
    }

    public final void postChange(@NonNull T change) {
        final Set<ChangeListenerToken<T>> localListeners;
        synchronized (lock) { localListeners = new HashSet<>(listeners); }
        for (ChangeListenerToken<T> token: localListeners) { token.postChange(change); }
    }

    public final boolean removeChangeListener(@NonNull ListenerToken token) {
        synchronized (lock) {
            listeners.remove(token);
            return listeners.isEmpty();
        }
    }

    @VisibleForTesting
    public final int getListenerCount() {
        synchronized (lock) { return listeners.size(); }
    }
}
