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

import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Base class for a removable subscription to an observable.
 */
public class ListenerToken extends AtomicBoolean implements AutoCloseable {
    private final Fn.Consumer<ListenerToken> onRemove;

    protected ListenerToken(@NonNull Fn.Consumer<ListenerToken> onRemove) {
        super(true);
        this.onRemove = Preconditions.assertNotNull(onRemove, "onRemove task");
    }

    @Override
    public void close() throws Exception { remove(); }

    public void remove() {
        if (getAndSet(false)) { onRemove.accept(this); }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { remove(); }
        finally { super.finalize(); }
    }
}
