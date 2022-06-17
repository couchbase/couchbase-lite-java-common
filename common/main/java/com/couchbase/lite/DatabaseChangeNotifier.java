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

import java.util.ArrayList;
import java.util.List;

import com.couchbase.lite.internal.core.C4DatabaseObserver;
import com.couchbase.lite.internal.core.C4DocumentChange;
import com.couchbase.lite.internal.listener.ChangeNotifier;
import com.couchbase.lite.internal.utils.Fn;


final class DatabaseChangeNotifier extends ChangeNotifier<DatabaseChange> {
    private static final int REQUESTED_CHANGES = 100;
    private static final int MAX_CHANGES = 1000;

    @NonNull
    private final Collection collection;

    @Nullable
    private C4DatabaseObserver c4Observer;

    DatabaseChangeNotifier(@NonNull final Collection collection) { this.collection = collection; }

    // We give the caller a runnable  and they give us back a
    // C4DocumentObserver that will call that function for every change.
    void start(@NonNull Fn.Function<Runnable, C4DatabaseObserver> fn) { c4Observer = fn.apply(this::databaseChanged); }

    @Override
    public void close() {
        closeObserver(c4Observer);
        c4Observer = null;
        collection.getDatabase().removeDatabaseObserver();
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closeObserver(c4Observer); }
        finally { super.finalize(); }
    }

    @SuppressWarnings("PMD.NPathComplexity")
    void databaseChanged() {
        synchronized (collection.getDbLock()) {
            final C4DatabaseObserver observer = c4Observer;
            if ((observer == null) || !collection.isOpen()) { return; }

            boolean external = false;
            int nChanges;
            List<String> docIDs = new ArrayList<>();
            do {
                // Read changes in batches of MAX_CHANGES
                final C4DocumentChange[] c4DocChanges = observer.getChanges(REQUESTED_CHANGES);

                int i = 0;
                nChanges = (c4DocChanges == null) ? 0 : c4DocChanges.length;
                if (nChanges > 0) {
                    while (c4DocChanges[i] == null) {
                        i++;
                        nChanges--;
                    }
                }
                final boolean newExternal = (nChanges > 0) && c4DocChanges[i].isExternal();

                if ((!docIDs.isEmpty())
                    && ((nChanges <= 0) || (external != newExternal) || (docIDs.size() > MAX_CHANGES))) {
                    postChange(new CollectionChange(collection, docIDs));
                    docIDs = new ArrayList<>();
                }

                external = newExternal;

                for (int j = i; j < nChanges; j++) {
                    final C4DocumentChange change = c4DocChanges[j];
                    if (change != null) { docIDs.add(change.getDocID()); }
                }
            }
            while (nChanges > 0);
        }
    }

    private void closeObserver(C4DatabaseObserver observer) {
        if (observer != null) { observer.close(); }
    }
}
