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

import com.couchbase.lite.internal.core.C4DocumentObserver;
import com.couchbase.lite.internal.listener.ChangeNotifier;
import com.couchbase.lite.internal.utils.Fn;


final class DocumentChangeNotifier extends ChangeNotifier<DocumentChange> implements AutoCloseable {
    @NonNull
    private final Collection collection;
    @NonNull
    private final String docID;

    @Nullable
    private C4DocumentObserver c4Observer;

    DocumentChangeNotifier(@NonNull final Collection collection, @NonNull final String docID) {
        this.collection = collection;
        this.docID = docID;
    }

    @Override
    public void close() {
        synchronized (collection.getDbLock()) {
            closeObserver(c4Observer);
            c4Observer = null;
        }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closeObserver(c4Observer); }
        finally { super.finalize(); }
    }

    void start(@NonNull Fn.Consumer<Runnable> onChange) {
        synchronized (collection.getDbLock()) {
            c4Observer = collection.getC4Collection().createDocumentObserver(
                docID,
                () -> onChange.accept(this::documentChanged));
        }
    }

    private void documentChanged() { postChange(new DocumentChange(collection, docID)); }

    private void closeObserver(C4DocumentObserver observer) {
        if (observer != null) { observer.close(); }
    }
}
