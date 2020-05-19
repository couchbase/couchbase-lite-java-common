package com.couchbase.lite.internal.replicator;

public interface NetworkConnectivityManager {
    interface Observer {
        void onConnectivityChanged(boolean connected);
    }

    boolean isConnected();

    void registerObserver(Observer observer);

    void unregisterObserver(Observer observer);
}
