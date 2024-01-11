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

import java.util.Set;


abstract class PeerBinding<T> {
    /**
     * Bind an object to a key.
     *
     * @param key a unique long value
     * @param obj the object to be bound to the key.
     */
    public final synchronized void bind(long key, @NonNull T obj) {
        preBind(key, obj);

        final T currentBinding = get(key);
        if (currentBinding == obj) { return; }

        if (currentBinding == null) {
            set(key, obj);
            return;
        }

        throw new IllegalStateException("Attempt to rebind peer @x" + Long.toHexString(key));
    }

    /**
     * Get the object bound to the passed key.
     * Returns null if no object is bound to the key.
     *
     * @param key a unique long value
     * @return the bound object or null if none exists.
     */
    @Nullable
    public final synchronized T getBinding(long key) {
        preGetBinding(key);
        return get(key);
    }

    /**
     * Remove the binding for a key
     * Re-entrant.
     *
     * @param key the key to be unbound.
     */
    public final synchronized void unbind(long key) { remove(key); }

    @VisibleForTesting
    public abstract int size();

    @VisibleForTesting
    public abstract void clear();

    @VisibleForTesting
    @NonNull
    public abstract Set<Long> keySet();

    @GuardedBy("this")
    protected abstract void preBind(long key, @NonNull T obj);

    @GuardedBy("this")
    protected abstract void preGetBinding(long key);

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
