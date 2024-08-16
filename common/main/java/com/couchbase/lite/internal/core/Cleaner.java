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

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;


/**
 * A cleaner that uses PhantomReferences to clean up resources.  It works like this:
 * <p>
 * Create a new Cleaner to manage some group of resources.  There are not strong reasons for
 * creating multiple Cleaners: a Cleaner with two threads is pretty much the same thing
 * as two Cleaners with one thread each.
 * <p>
 * An object that needs finalization calls Cleaner.register with a reference to itself and a
 * Cleaner.Cleanable, a lambda that, presumably, releases the object's LiteCore native peer.
 * The code here packages the Cleaner.Cleanable and the cleanable object in a CleanableRef.
 * A cleanable ref is a  PhantomReference.  It is, itself, subject to garbage collection:
 * somebody has to hold a reference to it!  Since we are the ones that created it
 * I guess it has to be us.  That's the alive set.
 * <p>
 * So: the state after the call to Cleaner.register is:
 * <ul>
 * <li> The alive set contains a ref to a CleanableRef (making it reachable).
 * <li> The CleanableRef is a phantom ref to an object used by platform code that needs cleaning.
 * <li> The CleanableRef also contains a ref to a method (probably a lambda) that does the clean up for the object.
 * </ul>
 * <p>
 * When the object becomes unreachable, the VM enqueues the CleanableRef on the zombies
 * queue.  Periodically, the CleanerThread checks the zombies queue for enqueued Refs.
 * If it finds one, it remove it from the zombies queue, clears it (apparently preferred
 * prior to Java 11), removes itself from the alive set and calls the clean method on the
 * associated Cleaner.Cleanable.  Presumably, that releases the associated native resource
 * (see C4Peer). Cleaner.Cleanables support a finalizing flag, so they can behave differently
 * in response to an explicit close, than they do to being cleaned up by the Cleaner.
 * <p>
 * The timeout parameter is the maximum time the CleanerThread will wait for a zombie to appear.
 * It is useful because when a Cleaner is stopped, its cleaner threads will not notice until
 * they awakened by a zombie or the timeout.
 * <p>
 * Cleaners clean themselves: this is sole reason for the existence of CleanerImpl, separate from
 * Cleaner. A Cleaner will clean up its own resources when it becomes unreachable.  A Cleaner
 * registers itself with the CleanerImpl that  actually does the cleaning.  When the Cleaner
 * becomes unreachable its CleanableRef will be enqueued on the zombies queue, its clean method
 * called, and that will, in turn delegate to the stopCleaner method.
 * p>
 * Potential failure modes:
 * <ul>
 * <li> If the Cleaner is not reachable, the alive set will become unreachable and the cleaners in it
 * will be unreachable.  They may be GC'ed before their associated objects and therefore unable to clean
 * up after those objects.
 * </ul>
 */
class CleanerImpl {
    private final class CleanableRef extends PhantomReference<Object> implements Cleaner.Cleanable {
        @NonNull
        private final Cleaner.Cleanable cleanable;
        @NonNull
        private final String name;

        CleanableRef(
            @NonNull Object referent,
            @NonNull ReferenceQueue<Object> q,
            @NonNull Cleaner.Cleanable cleanable) {
            super(referent, q);
            this.cleanable = cleanable;
            this.name = referent.getClass().getSimpleName();
        }

        @Override
        public void clean(boolean finalizing) {
            // Apparently preferred in Java 8. Not actually necessary,
            // because the ref is about to become unreachable, anyway.
            clear();

            alive.remove(this);

            try { cleanable.clean(finalizing); }
            catch (Exception e) {
                Log.w(LogDomain.DATABASE, "Cleanable error" + ((finalizing) ? "!" : "") + " in " + name, e);
            }
        }

        @Override
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
                        // Except in tests, "ref" is, in fact, a CleanableRef
                        // ... so this is a call to CleanableRef.clean
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

    @NonNull
    private final Set<CleanableRef> alive = Collections.synchronizedSet(new HashSet<>());
    @NonNull
    private final ReferenceQueue<Object> zombies = new ReferenceQueue<>();

    @NonNull
    private final AtomicBoolean shouldStop = new AtomicBoolean();

    @NonNull
    private final AtomicLong threadId = new AtomicLong();
    @NonNull
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

    @NonNull
    final Cleaner.Cleanable register(@NonNull Object obj, @NonNull Cleaner.Cleanable cleanable) {
        if (shouldStop.get()) { throw new CouchbaseLiteError("Attempt to register with a closed cleaner"); }

        final CleanableRef ref = new CleanableRef(obj, zombies, cleanable);
        alive.add(ref);

        startCleaner();

        return ref;
    }

    @VisibleForTesting
    final int runningThreads() { return threads.size(); }

    @VisibleForTesting
    final void stopCleaner() { shouldStop.set(true); }

    @VisibleForTesting
    final void startCleaner() {
        while (threads.size() < nThreads) {
            final Thread thread = new CleanerThread(threadId.getAndIncrement());
            threads.add(thread);
            thread.start();
        }
    }

    @VisibleForTesting
    @Nullable
    Cleaner.Cleanable getNextZombie() throws InterruptedException {
        return (Cleaner.Cleanable) zombies.remove(timeoutMs);
    }
}

public final class Cleaner {
    @FunctionalInterface
    public interface Cleanable {
        void clean(boolean finalizing);
    }

    @NonNull
    private final CleanerImpl impl;

    public Cleaner(@NonNull String name, int nThreads) { this(name, nThreads, 60 * 1000); }

    @VisibleForTesting
    public Cleaner(@NonNull String name, int nThreads, int timeoutMs) {
        // Don't "fix" this!  If the lambda holds a reference to `this.impl`
        // the Cleaner object will forever be reachable.
        final CleanerImpl cleaner = new CleanerImpl(name, nThreads, timeoutMs);
        cleaner.register(this, ignore -> cleaner.stopCleaner());

        impl = cleaner;
    }

    /**
     * Be very careful with implementations of Cleanable passed to this method!
     * Anything to which it holds a reference (even implicitly, as a lambda)
     * will be reachable and not eligible for garbage collection.  If the Cleanable
     * holds a reference to the the obj, the obj will be leaked, permanently.
     * LOL: looking at the Java implementation of Cleanable, I find pretty much
     * this same warning.
     */
    @NonNull
    public Cleanable register(@NonNull Object obj, @NonNull Cleanable cleaner) {
        return impl.register(obj, cleaner);
    }

    @VisibleForTesting
    public int runningThreads() { return impl.runningThreads(); }

    @VisibleForTesting
    public void stop() { impl.stopCleaner(); }
}
