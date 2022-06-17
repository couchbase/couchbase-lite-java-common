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


final class DocumentChangeNotifier extends ChangeNotifier<DocumentChange> {
    @NonNull
    private final Database db;
    @NonNull
    private final String docID;

    @Nullable
    private C4DocumentObserver c4Observer;

    DocumentChangeNotifier(@NonNull final Database db, @NonNull final String docID) {
        this.db = db;
        this.docID = docID;
    }

    // We give the caller a runnable  and they give us back a
    // C4DocumentObserver that will call that function for every change.
    void start(@NonNull Fn.Function<Runnable, C4DocumentObserver> fn) { c4Observer = fn.apply(this::postChange); }

    @Override
    public void close() {
        closeObserver(c4Observer);
        c4Observer = null;
        db.removeDocumentObserver(docID);
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closeObserver(c4Observer); }
        finally { super.finalize(); }
    }

    private void postChange() { postChange(new DocumentChange(db, docID)); }

    private void closeObserver(C4DocumentObserver observer) {
        if (observer != null) { observer.close(); }
    }
}
