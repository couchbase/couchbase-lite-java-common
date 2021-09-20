package com.couchbase.lite.internal.replicator;

import androidx.annotation.NonNull;


public interface NetworkConnectivityManager {

    @FunctionalInterface
    interface Observer {
        void onConnectivityChanged(boolean connected);
    }

    boolean isConnected();

    void registerObserver(@NonNull Observer observer);

    void unregisterObserver(@NonNull Observer observer);
}
