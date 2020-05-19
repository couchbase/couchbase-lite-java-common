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
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.WeakHashMap;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.replicator.NetworkConnectivityManager;
import com.couchbase.lite.internal.support.Log;


class AndroidConnectivityManager implements NetworkConnectivityManager {
    private final BroadcastReceiver connectivityListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                AndroidConnectivityManager.this.connectivityChanged(context);
            }
        }
    };

    private final WeakHashMap<Observer, WeakReference<Observer>> observers = new WeakHashMap<>();

    private boolean listening;


    @Override
    public boolean isConnected() { return isConnected(CouchbaseLiteInternal.getContext()); }

    @Override
    public void registerObserver(Observer observer) {
        final boolean shouldStart;
        synchronized (observers) {
            observers.put(observer, new WeakReference<>(observer));
            shouldStart = !listening;
        }
        if (shouldStart) { start(); }
    }

    @Override
    public void unregisterObserver(Observer observer) {
        final boolean shouldStop;
        synchronized (observers) {
            observers.remove(observer);
            shouldStop = (listening && observers.isEmpty());
        }
        if (shouldStop) { stop(); }
    }

    void connectivityChanged(Context ctxt) {
        if (ctxt == null) { return; }

        final boolean connected = isConnected(ctxt);

        final Collection<WeakReference<Observer>> obs;
        synchronized (observers) { obs = observers.values(); }

        int n = 0;
        for (WeakReference<Observer> obRef: obs) {
            final Observer ob = obRef.get();
            if (ob == null) { continue; }
            ob.onConnectivityChanged(connected);
            n++;
        }

        if (listening && (n <= 0)) { stop(); }
    }

    private boolean isConnected(@NonNull Context ctxt) {
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

    private void start() {
        Log.v(LogDomain.NETWORK, "Registering network listener: " + this);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        CouchbaseLiteInternal.getContext().registerReceiver(connectivityListener, filter);
    }

    private void stop() {
        try {
            CouchbaseLiteInternal.getContext().unregisterReceiver(connectivityListener);
            Log.v(LogDomain.NETWORK, "Unregistered network listener: " + this);
        }
        catch (Exception e) {
            Log.e(LogDomain.NETWORK, "Failed unregistering network listener: " + this, e);
        }
    }
}
