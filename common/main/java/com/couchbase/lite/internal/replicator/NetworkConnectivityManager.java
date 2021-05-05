package com.couchbase.lite.internal.replicator;

import android.support.annotation.NonNull;


public interface NetworkConnectivityManager {

    @FunctionalInterface
    interface Observer {
        void onConnectivityChanged(boolean connected);
    }

    boolean isConnected();

    void registerObserver(@NonNull Observer observer);

    void unregisterObserver(@NonNull Observer observer);
}
