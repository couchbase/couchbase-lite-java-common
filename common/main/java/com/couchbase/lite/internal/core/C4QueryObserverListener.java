package com.couchbase.lite.internal.core;

public interface C4QueryObserverListener {
    void callback(C4QueryObserver observer, Object context);
}
