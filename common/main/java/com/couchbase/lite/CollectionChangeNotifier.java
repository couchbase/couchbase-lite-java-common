//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.internal.core.C4CollectionObserver;
import com.couchbase.lite.internal.core.C4DocumentChange;
import com.couchbase.lite.internal.listener.ChangeNotifier;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


// Not thread safe...
final class CollectionChangeNotifier extends ChangeNotifier<CollectionChange> implements AutoCloseable {
    private static final int REQUESTED_CHANGES = 100;
    private static final int MAX_CHANGES = 1000;


    @NonNull
    private final Collection collection;

    @GuardedBy("collection.getDbLock()")
    @Nullable
    private C4CollectionObserver c4Observer;

    CollectionChangeNotifier(@NonNull Collection collection) {
        this.collection = Preconditions.assertNotNull(collection, "collection");
    }

    @Override
    public void close() {
        synchronized (collection.getDbLock()) {
            if (c4Observer != null) { c4Observer.close(); }
            c4Observer = null;
        }
    }

    void start(@NonNull Fn.Consumer<Runnable> onChange) throws CouchbaseLiteException {
        synchronized (collection.getDbLock()) {
            c4Observer = collection.createCollectionObserver(() -> onChange.accept(this::collectionChanged));
        }
    }

    private void collectionChanged() {
        synchronized (collection.getDbLock()) {
            final C4CollectionObserver observer = c4Observer;
            if (!collection.isOpen() || (observer == null)) { return; }

            boolean external = false;
            List<String> docIDs = new ArrayList<>();
            while (true) {
                // Read changes in batches of REQUESTED_CHANGES
                final List<C4DocumentChange> changes = Fn.filterToList(
                    Arrays.asList(observer.getChanges(REQUESTED_CHANGES)),
                    change -> change != null);

                // if core doesn't have anything more, we're done:
                // post anything that's cached and get outta here.
                if (changes.isEmpty()) {
                    postChanges(docIDs);
                    return;
                }

                final boolean newExternal = changes.get(0).isExternal();

                // if there are too many changes in the cache already
                // or if the next changes have a different external-ness
                // post and clear the cache
                if ((docIDs.size() > MAX_CHANGES) || ((external != newExternal))) {
                    postChanges(docIDs);
                    docIDs = new ArrayList<>();
                }

                // cache the new changes
                docIDs.addAll(Fn.mapToList(changes, C4DocumentChange::getDocID));
                external = newExternal;
            }
        }
    }

    // postChange will queue the notification:
    // the client code will not execute while holding the db lock.
    private void postChanges(List<String> docIds) {
        if (docIds.isEmpty()) { return; }
        postChange(new CollectionChange(collection, docIds));
    }
}
