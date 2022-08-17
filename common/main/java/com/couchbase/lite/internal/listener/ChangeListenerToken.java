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

import java.util.concurrent.Executor;

import com.couchbase.lite.ChangeListener;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


public class ChangeListenerToken<T> extends ListenerToken {
    public static final ChangeListenerToken<Void> DUMMY = new ChangeListenerToken<Void>(change -> {}, null, t -> {}) {
        @NonNull
        @Override
        public String toString() { return "Dummy Token!!"; }
        @Override
        public void postChange(@NonNull Void change) { }
    };

    @NonNull
    private final ChangeListener<T> listener;
    @Nullable
    private String key;

    public ChangeListenerToken(
        @NonNull ChangeListener<T> listener,
        @Nullable Executor executor,
        @NonNull Fn.Consumer<ListenerToken> onRemove) {
        super(executor, onRemove);
        this.listener = Preconditions.assertNotNull(listener, "listener");
    }

    @NonNull
    @Override
    public String toString() { return "ChangeListenerToken{@" + key + ": " + listener + super.toString() + "}"; }

    @Nullable
    public String getKey() { return key; }

    public void setKey(@Nullable String key) { this.key = key; }

    public void postChange(@NonNull T change) { send(() -> listener.changed(change)); }
}
