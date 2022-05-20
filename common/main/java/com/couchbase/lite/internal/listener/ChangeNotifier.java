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
import com.couchbase.lite.internal.utils.Preconditions;


public class ChangeNotifier<T> {
    @NonNull
    private final Object lock = new Object();
    @NonNull
    private final Set<ChangeListenerToken<T>> listenerTokens = new HashSet<>();

    @NonNull
    public ChangeListenerToken<T> addChangeListener(@Nullable Executor executor, @NonNull ChangeListener<T> listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (lock) {
            final ChangeListenerToken<T> token = new ChangeListenerToken<>(
                executor,
                listener,
                this::removeChangeListener);
            listenerTokens.add(token);
            return token;
        }
    }

    /**
     * Remove a change listener.
     *
     * @param token the listener token
     * @return the number of remaining listeners.
     * @deprecated use ListenerToken.remove
     */
    @Deprecated
    public int removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        synchronized (lock) {
            listenerTokens.remove(token);
            return listenerTokens.size();
        }
    }

    public void postChange(@NonNull T change) {
        Preconditions.assertNotNull(change, "change");
        synchronized (lock) {
            for (ChangeListenerToken<T> token: listenerTokens) { token.postChange(change); }
        }
    }

    @VisibleForTesting
    public int getListenerCount() {
        synchronized (lock) { return listenerTokens.size(); }
    }
}
