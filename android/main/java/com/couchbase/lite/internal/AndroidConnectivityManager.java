//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.replicator.NetworkConnectivityManager;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.Fn;


public class AndroidConnectivityManager implements NetworkConnectivityManager {
    private static final class ConnectivityListener extends BroadcastReceiver {
        private final WeakReference<AndroidConnectivityManager> mgr;

        ConnectivityListener(AndroidConnectivityManager mgr) { this.mgr = new WeakReference<>(mgr); }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                final AndroidConnectivityManager connectivityManager = mgr.get();
                if (connectivityManager != null) { connectivityManager.connectivityChanged(); }
            }
        }
    }

    static AndroidConnectivityManager newInstance() {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        return new AndroidConnectivityManager(mainHandler::post);
    }

    @NonNull
    @GuardedBy("observers")
    private final WeakHashMap<Observer, WeakReference<Observer>> observers = new WeakHashMap<>();

    @NonNull
    private final AtomicReference<ConnectivityListener> listener = new AtomicReference<>(null);

    @NonNull
    private final Fn.Runner runner;

    @VisibleForTesting
    public AndroidConnectivityManager(Fn.Runner runner) {
        this.runner = runner;
    }

    @Override
    public boolean isConnected() {
        final Context ctxt = CouchbaseLiteInternal.getContext();

        final ConnectivityManager svc = ((ConnectivityManager) ctxt.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (svc == null) { return true; }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            final NetworkInfo networkInfo = svc.getActiveNetworkInfo();
            return (networkInfo == null) || networkInfo.isConnected();
        }

        final Network nw = svc.getActiveNetwork();
        if (nw == null) { return true; }

        final NetworkCapabilities activeNetwork = svc.getNetworkCapabilities(nw);
        if (activeNetwork == null) { return true; }

        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH);
    }

    @Override
    public void registerObserver(@NonNull Observer observer) {
        synchronized (observers) { observers.put(observer, new WeakReference<>(observer)); }
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

    public void connectivityChanged() {
        final Set<Observer> obs = new HashSet<>();
        synchronized (observers) {
            for (WeakReference<Observer> ref: observers.values()) {
                final Observer observer = ref.get();
                if (observer != null) { obs.add(observer); }
            }
        }

        if (obs.isEmpty()) {
            stop();
            return;
        }

        final boolean connected = isConnected();
        for (Observer observer: obs) {
            runner.run(() -> observer.onConnectivityChanged(connected));
        }
    }

    @VisibleForTesting
    public boolean isRunning() { return listener.get() != null; }

    private void start() {
        Log.v(LogDomain.NETWORK, "Registering network listener: " + this);

        final ConnectivityListener newListener = new ConnectivityListener(this);
        if (!listener.compareAndSet(null, newListener)) { return; }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        CouchbaseLiteInternal.getContext().registerReceiver(newListener, filter);
    }

    private void stop() {
        try {
            final ConnectivityListener oldListener = listener.getAndSet(null);
            if (oldListener == null) { return; }

            CouchbaseLiteInternal.getContext().unregisterReceiver(oldListener);

            Log.v(LogDomain.NETWORK, "Unregistered network listener: " + this);
        }
        catch (RuntimeException e) {
            Log.e(LogDomain.NETWORK, "Failed unregistering network listener: " + this, e);
        }
    }
}
