package com.couchbase.lite.internal.utils;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SerialExecutor implements Executor {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
         final Thread t = new Thread(r, "serial-executor");
         t.setDaemon(true);
         return t;
    });

    private final Deque<BlockingTask> blockingTasks = new ArrayDeque<>();

    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private Runnable active;

    public synchronized void execute(Runnable r) {
        executeInner(r);
    }

    public synchronized void execute(BlockingTask t) {
        blockingTasks.addLast(t);
        executeInner(t);
    }

    public synchronized void taskComplete() {
        final BlockingTask t = blockingTasks.pollFirst();
        if (t != null) {
            t.done();
        }
    }

    public synchronized void cancelPendingTasks() {
        for (BlockingTask task : blockingTasks) {
            task.done();
        }

        blockingTasks.clear();
    }

    private synchronized void executeInner(Runnable r) {
        tasks.offer(() -> {
            try {
                r.run();
            } finally {
                scheduleNext();
            }
        });

        if (active == null) {
            scheduleNext();
        }
    }

    private synchronized void scheduleNext() {
        active = tasks.poll();
        if (active != null) {
            if (active instanceof BlockingTask) {
                try {
                    POOL.execute(active);
                } catch (RuntimeException e) {
                    blockingTasks.removeLastOccurrence(active);
                    throw e;
                }
            } else {
                POOL.execute(active);
            }
        }
    }

    // Queue one BLE operation at a time and don't let the next one start
    // until the async callback for the current one signals completion.
    public static final class BlockingTask implements Runnable {
        private static final int STATE_PENDING = 0;
        private static final int STATE_RUNNING = 1;
        private static final int STATE_COMPLETED = 2;

        @NonNull
        private final Runnable block;
        @NonNull
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        @NonNull
        private final AtomicInteger state = new AtomicInteger(STATE_PENDING);

        public BlockingTask(@NonNull Runnable block) {
            this.block = block;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(STATE_PENDING, STATE_RUNNING)) {
                return;
            }

            try {
                block.run();
            }
            catch (RuntimeException e) {
                done();
                throw e;
            }

            if (state.get() == STATE_COMPLETED) {
                return;
            }

            try {
                completionLatch.await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                done();
            }
        }

        void done() {
            if (state.getAndSet(STATE_COMPLETED) == STATE_COMPLETED) {
                return;
            }
            completionLatch.countDown();
        }
    }
}
