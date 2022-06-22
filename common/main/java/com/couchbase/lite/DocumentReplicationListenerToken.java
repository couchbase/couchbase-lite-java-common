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

import java.util.concurrent.Executor;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


final class DocumentReplicationListenerToken extends ListenerToken {
    @NonNull
    private final DocumentReplicationListener listener;

    DocumentReplicationListenerToken(
        @Nullable Executor executor,
        @NonNull DocumentReplicationListener listener,
        @NonNull Fn.Consumer<ListenerToken> onRemove) {
        super(executor, onRemove);
        this.listener = Preconditions.assertNotNull(listener, "listener");
    }

    @NonNull
    @Override
    public String toString() { return "DocumentReplicationListenerToken{" + listener + super.toString() + "}"; }

    void postChange(@NonNull DocumentReplication change) { send(() -> listener.replication(change)); }
}
