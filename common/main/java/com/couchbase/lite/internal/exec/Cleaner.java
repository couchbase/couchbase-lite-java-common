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
package com.couchbase.lite.internal.exec;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;


/**
 * A cleaner that uses PhantomReferences to clean up resources.  It works like this:
 * <p>
 * Create a new Cleaner to manage some group of resources.
 * An object that needs finalization calls Cleaner.register with a reference to itself and a
 * Cleaner.Cleanable, presumably a lambda that releases the object's LiteCore native peer.
 * The code here packages the Cleaner.Cleanable and the cleanable object in a CleanableRef.
 * A cleanable ref is a  PhantomReference.  It is, itself, subject to garbage collection:
 * somebody has to hold a reference to it!  Since we are the ones that created it
 * I guess it has to be us.  That's the alive set.
 * <p>
 * So: the state after the call to Cleaner.register is:
 * <ul>
 * <li> The alive set contains a ref to a CleanableRef (making it reachable).
 * <li> The CleanableRef is a phantom ref to an object used by platform code that needs cleaning.
 *      The CleanableRef must NOT have a hard ref to the object!
 * <li> The CleanableRef also contains a ref to a method (probably a lambda) that does the clean up for the object.
 *      Again, be careful!  If the method is a lambda that contains a ref to the target object, the target object
 *      is hard reachable and will never be GCed!
 * </ul>
 * <p>
 * When the object becomes unreachable, the VM enqueues the CleanableRef on the zombies
 * queue.  The CleanerThread monitors the zombies queue, checking for enqueued Refs.
 * If it finds one, it removes it from the queue, clears it (apparently preferred
 * prior to Java 11), removes itself from the alive set, and calls the clean method on the
 * associated Cleaner.Cleanable.  Presumably, that releases the associated native resource
 * (see C4Peer). Cleaner.Cleanables support a finalizing flag so they can behave differently
 * in response to an explicit close than they do to being cleaned up by the Cleaner.
 * <p>
 * The timeout parameter is the maximum time the CleanerThread will wait for a zombie to appear.
 * It is useful because when a Cleaner is stopped, its cleaner threads will not notice until
 * they are awakened by a zombie or by the timeout.
 * <p>
 * Cleaners clean themselves: this is sole reason for the existence of CleanerImpl, separate from
 * Cleaner. A Cleaner will clean up its own resources when it becomes unreachable.  A Cleaner
 * registers itself with the CleanerImpl that actually does the cleaning.  When the Cleaner
 * becomes unreachable its CleanableRef will be enqueued on the zombies queue, its clean method
 * called, and that will, in turn, delegate to the stopCleaner method.
 * <p>
 * Failure modes:
 * <ul>
 * <li> If the Cleaner is not reachable, the alive set will become unreachable and the CleanableRef in it
 * will be unreachable.  They may be GC'ed before their associated objects and therefore unable to clean
 * up after those objects.
 * <li> If a Cleanable takes a long time (or even deadlocks), the CleanerThread will be hung and
 * not able to clean up other resources.
 * </ul>
 */
class CleanerImpl {
    private static final LogDomain LOG = LogDomain.DATABASE;

    private final class CleanerThread extends Thread {
        private final AtomicLong runtime = new AtomicLong();

        CleanerThread(@NonNull String name) {
            super(name);
            setPriority(Thread.MAX_PRIORITY - 2);
            setDaemon(true);
        }

        @Override
        @NonNull
        public UncaughtExceptionHandler getUncaughtExceptionHandler() {
            return (t, e) -> {
                Log.w(LOG, "Cleaner thread %s-%s crashed", e, cleanerName, t.getName());
                final UncaughtExceptionHandler hdlr = getDefaultUncaughtExceptionHandler();
                if (hdlr != null) { hdlr.uncaughtException(t, e); }
            };
        }

        @Override
        public void run() {
            Log.i(LOG, "Cleaner thread %s started", getName());

            Exception err = null;
            boolean stopping = shouldStop.get();
            try {
                while (!stopping) {
                    final Cleaner.Cleanable ref = getNextZombie();
                    stopping = shouldStop.get();
                    if (ref == null) { continue; }

                    final long t = System.nanoTime();
                    // Except in testing, "ref" is actually a CleanableRef: this is a call to CleanableRef.clean
                    ref.clean(true);
                    runtime.getAndAdd(System.nanoTime() - t);
                }
            }
            catch (Exception e) { err = e; }
            finally {
                synchronized (lock) {
                    if (this.equals(cleanerThread)) {
                        if (stopping) { cleanerThread = null; }
                        else { startCleaner(); }
                    }
                }
                Log.w(LOG, "Cleaner thread exiting: %s", err, getName());
            }
        }

        public long getRuntimeNanos() { return runtime.getAndSet(0); }
    }

    private final class CleanableRef extends PhantomReference<Object> implements Cleaner.Cleanable {
        @NonNull
        private final Cleaner.Cleanable cleanable;
        @NonNull
        private final String name;
        private final long ts;


        CleanableRef(
            @NonNull Object referent,
            @NonNull Cleaner.Cleanable cleanable) {
            super(referent, zombies);
            this.cleanable = cleanable;
            this.name = referent.getClass().getSimpleName() + ClassUtils.objId(referent);
            this.ts = System.currentTimeMillis();
        }

        @Override
        public void clean(boolean finalizing) {
            // Apparently preferred in Java 8. Not actually necessary,
            // because this ref is about to become unreachable, anyway.
            clear();

            final boolean removed;
            synchronized (alive) {
                removed = alive.remove(this);
                final int curSize = alive.size();
                if (curSize < minSize) { minSize = curSize; }
            }

            if (!removed) { Log.w(LOG, "%s was not alive at attempt to clean", this); }

            try { cleanable.clean(finalizing); }
            catch (Exception e) { Log.w(LOG, "Failed cleaning: %s%s", e, name, ((finalizing) ? "!" : "")); }
        }

        // Two CleanableRefs are equal if their cleanables are equal.

        @Override
        public int hashCode() { return cleanable.hashCode(); }

        @Override
        public boolean equals(@Nullable Object o) {
            return (this == o) || ((o instanceof CleanableRef) && ((CleanableRef) o).cleanable.equals(cleanable));
        }

        @Override
        @NonNull
        public String toString() { return "CleanableRef{" + name + ", " + cleanable + "}"; }
    }

    private final Object lock = new Object();

    @NonNull
    private final AtomicBoolean shouldStop = new AtomicBoolean();

    @GuardedBy("alive")
    @NonNull
    private final Set<CleanableRef> alive = new HashSet<>();

    @NonNull
    private final ReferenceQueue<Object> zombies = new ReferenceQueue<>();

    @GuardedBy("lock")
    @Nullable
    private CleanerThread cleanerThread;
    @GuardedBy("lock")
    private int threadId;

    private final int timeoutMs;

    @GuardedBy("alive")
    private int minSize;
    @GuardedBy("alive")
    private int maxSize;

    @NonNull
    private final String cleanerName;

    CleanerImpl(@NonNull String cleanerName, int timeoutMs) {
        this.cleanerName = cleanerName;
        this.timeoutMs = timeoutMs;
    }

    @NonNull
    final Cleaner.Cleanable register(@NonNull Object obj, @NonNull Cleaner.Cleanable cleanable) {
        if (shouldStop.get()) { throw new CouchbaseLiteError("Attempt to register with a closed cleaner"); }

        final CleanableRef ref = new CleanableRef(obj, cleanable);
        synchronized (alive) {
            if (!alive.add(ref)) { throw new CouchbaseLiteError("Attempt to register a duplicate CleanableRef"); }
            final int curSize = alive.size();
            if (curSize > maxSize) { maxSize = curSize; }
        }

        synchronized (lock) {
            if (cleanerThread == null) { startCleaner(); }
        }

        return ref;
    }

    @GuardedBy("lock")
    @VisibleForTesting
    final void startCleaner() {
        cleanerThread = new CleanerThread(cleanerName + "-thread-" + ++threadId);
        cleanerThread.start();
    }

    @VisibleForTesting
    @Nullable
    Cleaner.Cleanable getNextZombie() {
        try { return (Cleaner.Cleanable) zombies.remove(timeoutMs); }
        catch (InterruptedException ignore) { }
        return null;
    }

    @VisibleForTesting
    final void stopCleaner() { shouldStop.set(true); }

    final boolean isStopped() {
        synchronized (lock) { return shouldStop.get() && (cleanerThread == null); }
    }


    // Instrumentation
    @NonNull
    Cleaner.Stats getStats() {
        synchronized (alive) {
            final Cleaner.Stats stats = new Cleaner.Stats(
                (cleanerThread == null) ? 0 : cleanerThread.getRuntimeNanos(),
                minSize,
                maxSize,
                Fn.mapToList(alive, ref -> ref.ts)
            );

            final int curSize = alive.size();
            minSize = curSize;
            maxSize = curSize;

            return stats;
        }
    }
}

public final class Cleaner {
    @FunctionalInterface
    public interface Cleanable {
        void clean(boolean finalizing);
    }

    public static final class Stats {
        public final long timeIn;
        public final int minSize;
        public final int maxSize;
        public final List<Long> alive;

        public Stats(long timeIn, int minSize, int maxSize, @NonNull List<Long> alive) {
            this.timeIn = timeIn;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.alive = alive;
        }

        @Override
        @NonNull
        public String toString() {
            return "CleanerStats{" + timeIn + ", " + minSize + ", " + maxSize + ", " + alive + "}";
        }
    }

    @NonNull
    private final CleanerImpl impl;

    // A Cleaner told to close will be gone in 2 seconds.
    public Cleaner(@NonNull String name) { this(name, 2 * 1000); }

    @VisibleForTesting
    Cleaner(@NonNull String name, int timeoutMs) {
        // WARNING: Don't "fix" this to assign to this.impl!
        // If the lambda holds a reference to this.impl this Cleaner object will forever be reachable.
        final CleanerImpl impl = new CleanerImpl(name + "-clean", timeoutMs);
        impl.register(this, ignore -> impl.stopCleaner());

        this.impl = impl;
    }

    /**
     * WARNING:
     * Be very careful with implementations of Cleanable passed to this method!
     * Anything to which it holds a reference (even implicitly, as a lambda)
     * will be reachable and not eligible for garbage collection.  If the Cleanable
     * holds a reference to the obj, the obj will be leaked, permanently.
     */
    @NonNull
    public Cleanable register(@NonNull Object obj, @NonNull Cleanable cleaner) { return impl.register(obj, cleaner); }

    // This is quite expensive: don't use it in production.
    @NonNull
    public Stats getStats() { return impl.getStats(); }

    @VisibleForTesting
    void stop() { impl.stopCleaner(); }

    @VisibleForTesting
    boolean isStopped() { return impl.isStopped(); }
}
