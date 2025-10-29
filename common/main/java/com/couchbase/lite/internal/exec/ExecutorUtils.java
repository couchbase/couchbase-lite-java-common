package com.couchbase.lite.internal.exec;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;

public final class ExecutorUtils {
    private ExecutorUtils() {}

    public static void shutdownAndAwaitTermination(
            @NonNull ExecutorService pool,
            int timeoutSeconds,
            @NonNull LogDomain logDomain) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                Log.w(logDomain, "Executor did not terminate within timeout");
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            Log.w(logDomain, "Executor shutdown interrupted", ie);
        }
    }
}
