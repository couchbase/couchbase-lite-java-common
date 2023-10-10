//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;


@VisibleForTesting
class CleanerImpl {
    private final class CleanableRef extends PhantomReference<Object> implements Cleaner.Cleanable {
        @NonNull
        private final Cleaner.Cleanable cleanable;
        @Nullable
        private final String name;

        CleanableRef(
            @NonNull Object referent,
            @NonNull ReferenceQueue<Object> q,
            @NonNull Cleaner.Cleanable cleanable) {
            super(referent, q);
            this.cleanable = cleanable;
            this.name = referent.getClass().getSimpleName();
        }

        public void clean(boolean finalizing) {
            alive.remove(this);
            try { cleanable.clean(finalizing); }
            catch (Exception e) {
                Log.w(LogDomain.DATABASE, "Cleanable error" + ((finalizing) ? "!" : "") + " in " + name, e);
            }
        }

        @NonNull
        public String toString() { return "CleanableRef{" + name + "}"; }
    }

    private final class CleanerThread extends Thread {
        CleanerThread(long id) {
            super("cleaner-" + id);
            setPriority(Thread.MAX_PRIORITY - 2);
            setDaemon(true);
        }

        @Override
        @NonNull
        public UncaughtExceptionHandler getUncaughtExceptionHandler() {
            return (t, e) -> {
                Log.w(LogDomain.DATABASE, "Cleaner thread " + cleanerName + "-" + t.getName() + ": " + "crashed", e);
                final UncaughtExceptionHandler hdlr = getDefaultUncaughtExceptionHandler();
                if (hdlr != null) { hdlr.uncaughtException(t, e); }
            };
        }

        @Override
        public void run() {
            Exception err = null;
            try {
                Log.i(LogDomain.DATABASE, "Cleaner thread " + cleanerName + "-" + getName() + " started");
                while (!shouldStop.get()) {
                    try {
                        final Cleaner.Cleanable ref = getNextZombie();
                        if (ref != null) { ref.clean(true); }
                    }
                    catch (InterruptedException ignore) { }
                }
            }
            catch (Exception e) { err = e; }
            finally {
                Log.w(LogDomain.DATABASE, "Cleaner thread " + cleanerName + "-" + getName() + ": exiting", err);
                threads.remove(this);
                if (!shouldStop.get()) { startCleaner(); }
            }
        }
    }

    private final Set<CleanableRef> alive = Collections.synchronizedSet(new HashSet<>());
    private final ReferenceQueue<Object> zombies = new ReferenceQueue<>();

    private final AtomicBoolean shouldStop = new AtomicBoolean();

    private final AtomicLong threadId = new AtomicLong();
    private final Set<Thread> threads = Collections.synchronizedSet(new HashSet<>());

    @NonNull
    private final String cleanerName;
    private final int nThreads;
    private final int timeoutMs;

    CleanerImpl(@NonNull String cleanerName, int nThreads, int timeoutMs) {
        this.cleanerName = cleanerName;
        this.nThreads = nThreads;
        this.timeoutMs = timeoutMs;
    }

    @VisibleForTesting
    @NonNull
    final Cleaner.Cleanable register(@NonNull Object obj, @NonNull Cleaner.Cleanable cleanable) {
        if (shouldStop.get()) { throw new IllegalStateException("Attempt to register with a closed cleaner"); }

        final CleanableRef ref = new CleanableRef(obj, zombies, cleanable);
        alive.add(ref);

        startCleaner();

        return ref;
    }

    @VisibleForTesting
    final void stopCleaner() { shouldStop.set(true); }

    @VisibleForTesting
    @Nullable
    Cleaner.Cleanable getNextZombie() throws InterruptedException {
        return (Cleaner.Cleanable) zombies.remove(timeoutMs);
    }

    @VisibleForTesting
    final int runningThreads() { return threads.size(); }

    @VisibleForTesting
    final void startCleaner() {
        while (threads.size() < nThreads) {
            final Thread thread = new CleanerThread(threadId.getAndIncrement());
            threads.add(thread);
            thread.start();
        }
    }
}

public final class Cleaner {
    @FunctionalInterface
    public interface Cleanable {
        void clean(boolean finalizing);
    }

    private final CleanerImpl impl;

    public Cleaner(@NonNull String name, int nThreads) { this(name, nThreads, 60 * 1000); }

    @VisibleForTesting
    public Cleaner(@NonNull String name, int nThreads, int timeoutMs) {
        // Don't "fix" this!  If the lambda has a reference to `impl`
        // the Cleaner object will forever be reachable.
        final CleanerImpl cleaner = new CleanerImpl(name, nThreads, timeoutMs);
        cleaner.register(this, ignore -> cleaner.stopCleaner());

        impl = cleaner;
    }

    /**
     * Be very careful with implementations of of Cleanable passed to this method!
     * Anything to which it holds a reference (even implicitly, as a lambda)
     * will be visible and not eligible for garbage collection.  If the Cleanable
     * holds a reference to the the obj, the obj will be leaked, permanently.
     * LOL: looking at the Java implementation of Cleanable, I find pretty much this
     * same warning.
     */
    @NonNull
    public Cleanable register(@NonNull Object obj, @NonNull Cleanable cleaner) {
        return impl.register(
            obj,
            cleaner);
    }

    @VisibleForTesting
    public int runningThreads() { return impl.runningThreads(); }

    @VisibleForTesting
    public void stop() { impl.stopCleaner(); }
}
