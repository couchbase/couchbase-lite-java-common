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
package com.couchbase.lite.internal.core.peers;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public abstract class WeakPeerBinding<T> extends PeerBinding<T> {
    @GuardedBy("this")
    @NonNull
    private final Map<Long, WeakReference<T>> bindings = new HashMap<>();

    @GuardedBy("this")
    @Override
    @Nullable
    protected T get(long key) {
        final WeakReference<T> ref = bindings.get(key);
        if (ref == null) { return null; }

        final T obj = ref.get();
        if (obj != null) { return obj; }

        // clean up dead objects...
        bindings.remove(key);
        return null;
    }

    @GuardedBy("this")
    @Override
    protected void set(long key, @Nullable T obj) {
        bindings.put(key, (obj == null) ? null : new WeakReference<>(obj));
    }

    @GuardedBy("this")
    @Override
    protected void remove(long key) { bindings.remove(key); }

    @GuardedBy("this")
    @Override
    protected boolean exists(long key) { return bindings.containsKey(key); }

    @GuardedBy("this")
    @VisibleForTesting
    public final synchronized int size() { return bindings.size(); }

    @GuardedBy("this")
    @VisibleForTesting
    public final synchronized void clear() { bindings.clear(); }

    @GuardedBy("this")
    @NonNull
    @VisibleForTesting
    public final synchronized Set<Long> keySet() { return bindings.keySet(); }
}
