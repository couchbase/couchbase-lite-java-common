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

// Used for Document and Collection (and MessageEndpoint) change notification.
// When used for a collection, we register a single callback with Core.
// That single callback is delegated to each of the listeners registered
// with this notifier.
// Document notifiers are similar: They are created, one per document and
// registered with Core.  A single callback is delegated to all observers
// observing the document.
// The token created here, and returned to the client code, dominates very
// little memory.  Notifiers, however, hold references to Core companion
// objects.  When the last listener is removed (removeListener returns true)
// the notifier should be closed and the companion objects freed.
// This moots the concerning scenario in which client code simply drops
// the token on the floor.  If it is never released, it and the notifier
// that contains it will not be released: the Core objects will be retained.
// Having the tokens free themselves in their finalizers is not a good approach.
// If you do that, as soon as the token is GCed, the associated listener will
// stop listening, surely surprising to the client code.
// On the other hand, once the replicator is freed, all of its notifiers are
// freed.  If we simply assure that Java objects with Core companions correctly
// free their companions in their finalizers, releasing the replicator will
// correctly free all this stuff... eventually.
public abstract class ChangeNotifier<T> {
    @NonNull
    private final Object lock = new Object();
    @NonNull
    private final Set<ChangeListenerToken<T>> listeners = new HashSet<>();

    @SuppressWarnings("CheckFunctionalParameters")
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
