//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.exec.RefQueueCleanerThread;


public class LockManager {
    private class LockRef extends WeakReference<Object> implements RefQueueCleanerThread.RefQueueCleaner {
        @NonNull
        final Long id;

        LockRef(long id, @NonNull Object lock) {
            super(lock, refQueue);
            this.id = id;
        }

        @Override
        public void clean() {
            clear();
            releaseLock(id);
        }
    }

    public static final LockManager INSTANCE = new LockManager();


    private final Map<Long, LockRef> locks = new HashMap<>();

    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
    @GuardedBy("locks")
    private RefQueueCleanerThread refQCleaner;


    /**
     * Get a lock object for the given reference.
     *
     * CAUTION: Calling this method for the first time for a given ref will create a new lock object for that ref.
     * You *MUST* hold a reference to that lock object until you are done with it or the next guy that asks for it
     * will get a *DIFFERENT* lock for the same ref!
     *
     * @param ref The reference to the lock object.
     * @return The lock object.
     */
    @NonNull
    public Object getLock(long ref) {
        synchronized (locks) {
            Object lock;

            final LockRef r = locks.get(ref);
            if (r != null) {
                lock = r.get();
                if (lock != null) { return lock; }
            }

            lock = new Object();
            locks.put(ref, new LockRef(ref, lock));

            if (refQCleaner == null) {
                final RefQueueCleanerThread t = new RefQueueCleanerThread("LockManager cleaner", refQueue);
                refQCleaner = t;
                t.start();
            }

            return lock;
        }
    }

    public void releaseLock(long ref) {
        synchronized (locks) {
            locks.remove(ref);
            if (locks.isEmpty()) {
                final RefQueueCleanerThread t = refQCleaner;
                refQCleaner = null;
                if (t != null) { t.quit(); }
            }
        }
    }
}
