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
package com.couchbase.lite.internal.core.peers;

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Set;


abstract class PeerBinding<T> {
    /**
     * Bind an object to a key.
     *
     * @param key a unique long value
     * @param obj the object to be bound to the key.
     */
    @CallSuper
    public synchronized void bind(long key, @NonNull T obj) {
        if (get(key) != null) { throw new IllegalStateException("attempt to rebind key: " + key); }
        set(key, obj);
    }

    /**
     * Get the object bound to the passed key.
     * Returns null if no object is bound to the key.
     *
     * @param key a unique long value
     * @return the bound object or null if none exists.
     */
    @CallSuper
    @Nullable
    public synchronized T getBinding(long key) { return get(key); }

    /**
     * Remove the binding for a key
     * Re-entrant.
     *
     * @param key the key to be unbound.
     */
    @CallSuper
    public synchronized void unbind(long key) { remove(key); }

    @VisibleForTesting
    public abstract int size();

    @VisibleForTesting
    public abstract void clear();

    @NonNull
    @VisibleForTesting
    public abstract Set<Long> keySet();

    @GuardedBy("this")
    @Nullable
    protected abstract T get(long key);
    @GuardedBy("this")
    protected abstract boolean exists(long key);
    @GuardedBy("this")
    protected abstract void set(long key, @Nullable T obj);
    @GuardedBy("this")
    protected abstract void remove(long key);
}
