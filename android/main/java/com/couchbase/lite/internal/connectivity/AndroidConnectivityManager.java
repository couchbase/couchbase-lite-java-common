//
// Copyright (c) 2020 Couchbase, Inc.
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
package com.couchbase.lite.internal.connectivity;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.replicator.NetworkConnectivityManager;
import com.couchbase.lite.internal.utils.Fn;


public class AndroidConnectivityManager implements NetworkConnectivityManager {
    @NonNull
    public static AndroidConnectivityManager newInstance() {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        return new AndroidConnectivityManager(mainHandler::post);
    }

    // This is just a weak Set
    @GuardedBy("observers")
    @NonNull
    private final WeakHashMap<Observer, Boolean> observers = new WeakHashMap<>();

    @NonNull
    private final AtomicReference<ConnectivityWatcher> listener = new AtomicReference<>(null);

    @NonNull
    private final Fn.Runner runner;
    private final int androidVersion;

    // Distinct API code paths are: (obsolete: 19), 22, 26, 29
    AndroidConnectivityManager(@NonNull Fn.Runner runner) { this(Build.VERSION.SDK_INT, runner); }

    @VisibleForTesting
    public AndroidConnectivityManager(int androidVersion, @NonNull Fn.Runner runner) {
        this.runner = runner;
        this.androidVersion = androidVersion;
    }

    // If this method cannot figure out what's going on,
    // it should return true in order to avoid making things even worse.
    @Override
    public boolean isConnected() {
        final ConnectivityWatcher connectivityListener = listener.get();
        return (connectivityListener != null) && connectivityListener.isConnected();
    }

    @Override
    public void registerObserver(@NonNull Observer observer) {
        synchronized (observers) {
            if (Boolean.TRUE.equals(observers.put(observer, Boolean.TRUE))) { return; }
        }
        start();
    }

    @Override
    public void unregisterObserver(@NonNull Observer observer) {
        final boolean shouldStop;
        synchronized (observers) {
            observers.remove(observer);
            shouldStop = observers.isEmpty();
        }
        if (shouldStop) { stop(); }
    }

    public void connectivityChanged(boolean connected) {
        final Set<Observer> obs;
        synchronized (observers) { obs = new HashSet<>(observers.keySet()); }

        if (obs.isEmpty()) {
            stop();
            return;
        }

        for (Observer observer: obs) { runner.run(() -> observer.onConnectivityChanged(connected)); }
    }

    @VisibleForTesting
    public boolean isRunning() { return listener.get() != null; }

    @SuppressLint("NewApi")
    private void start() {
        ConnectivityWatcher connectivityListener = listener.get();
        if (connectivityListener != null) { return; }

        if (androidVersion < Build.VERSION_CODES.N) {
            connectivityListener = new ConnectivityListener21to23(this);
        }
        else if (androidVersion < Build.VERSION_CODES.Q) {
            connectivityListener = new ConnectivityListener24to28(this);
        }
        else {
            connectivityListener = new ConnectivityListenerPost28(this);
        }

        if (listener.compareAndSet(null, connectivityListener)) { connectivityListener.start(); }
    }

    private void stop() {
        final ConnectivityWatcher connectivityListener = listener.getAndSet(null);
        if (connectivityListener != null) { connectivityListener.stop(); }
    }
}
