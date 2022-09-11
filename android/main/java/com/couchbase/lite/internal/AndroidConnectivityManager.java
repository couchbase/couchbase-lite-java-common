//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.replicator.NetworkConnectivityManager;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;


public class AndroidConnectivityManager implements NetworkConnectivityManager {
    /**
     * Base class for ConnectivityWatchers
     * A ConnectivityWatcher can:
     * <li>
     *     <ul>provide current connection status</ul>
     *     <ul>between the time it is started and the time it is stopped, notify of connection status changes</ul>
     *     <ul>stop itself if nobody cares about the info it is providing</ul>
     * </li>
     */
    private abstract static class ConnectivityWatcher {
        protected final String name;
        private final WeakReference<AndroidConnectivityManager> mgr;

        ConnectivityWatcher(@NonNull String name, @NonNull AndroidConnectivityManager mgr) {
            this.name = name;
            this.mgr = new WeakReference<>(mgr);
        }

        public abstract void start();
        public abstract boolean isConnected();
        public abstract void stop();

        protected final AndroidConnectivityManager getCblMgr() { return mgr.get(); }

        protected final ConnectivityManager getSysMgr() {
            return (ConnectivityManager)
                CouchbaseLiteInternal.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        protected final void onConnectivityChange(boolean networkState) {
            final AndroidConnectivityManager cblConnectivityMgr = getCblMgr();
            if (cblConnectivityMgr == null) {
                stop();
                return;
            }

            Log.v(LogDomain.NETWORK, "Connectivity changed (" + name + ") for " + getCblMgr() + ": " + this);
            cblConnectivityMgr.connectivityChanged(networkState);
        }

        protected final String getLogMessage(@NonNull String prefix) {
            return String.format("%s %s network listener for %s: %s", prefix, name, getCblMgr(), this);
        }
    }

    /**
     * Listener for API <= 20: use a BroadcastReceiver for updates and ActiveNetworkInfo for current status
     */
    private static final class ConnectivityListenerPre21 extends ConnectivityWatcher {
        private final AtomicBoolean fallbackNetInfo = new AtomicBoolean(true);
        private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) { return; }

                @SuppressWarnings("deprecation")
                final NetworkInfo netInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                fallbackNetInfo.set((netInfo != null) && netInfo.isConnected());

                onConnectivityChange(isConnected());
            }
        };

        ConnectivityListenerPre21(AndroidConnectivityManager mgr) { super("<=20", mgr); }

        @Override
        public void start() {
            try {
                CouchbaseLiteInternal.getContext().registerReceiver(
                    connectivityReceiver,
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                Log.v(LogDomain.NETWORK, getLogMessage("Started"));
            }
            catch (RuntimeException e) {
                Log.w(LogDomain.NETWORK, "Start failed", e);
            }
        }

        @Override
        public void stop() {
            final String msg = name + " network listener for " + getCblMgr() + ": " + this;
            try {
                CouchbaseLiteInternal.getContext().unregisterReceiver(connectivityReceiver);
                Log.v(LogDomain.NETWORK, "Stopped " + msg);
            }
            catch (RuntimeException e) {
                Log.w(LogDomain.NETWORK, "Failed stopping " + msg, e);
            }
        }

        @Override
        public boolean isConnected() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return true; }

            final NetworkInfo networkInfo = connectivityMgr.getActiveNetworkInfo();

            if ((networkInfo != null) && networkInfo.isConnected()) { return true; }

            // on some devices ConnectivityManager.getActiveNetworkInfo()
            // does not provide the correct network state
            // https://issuetracker.google.com/issues/37137911
            return fallbackNetInfo.get();
        }
    }

    /**
     * Base class for ConnectivityManager.NetworkCallback based Watchers.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private abstract static class CallbackConnectivityWatcher extends ConnectivityWatcher {
        protected final ConnectivityManager.NetworkCallback connectivityCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network ignore) { onConnectivityChange(isConnected()); }

                @Override
                public void onLost(@NonNull Network ignore) { onConnectivityChange(isConnected()); }
            };

        CallbackConnectivityWatcher(@NonNull String name, @NonNull AndroidConnectivityManager mgr) { super(name, mgr); }

        @Override
        public final void stop() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return; }

            final String msg = name + " network listener for " + getCblMgr() + ": " + this;
            try {
                connectivityMgr.unregisterNetworkCallback(connectivityCallback);
                Log.v(LogDomain.NETWORK, "Stopped " + msg);
            }
            catch (RuntimeException e) {
                Log.w(LogDomain.NETWORK, "Failed stopping " + msg, e);
            }
        }
    }

    /**
     * Listener for API 21 - 23: use a ConnectivityCallback for updates and ActiveNetworkInfo for status
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static final class ConnectivityListener21to23 extends CallbackConnectivityWatcher {
        ConnectivityListener21to23(AndroidConnectivityManager mgr) { super("21-23", mgr); }

        @Override
        public void start() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return; }

            try {
                connectivityMgr.registerNetworkCallback(new NetworkRequest.Builder().build(), connectivityCallback);
                Log.v(LogDomain.NETWORK, getLogMessage("Started"));
            }
            catch (RuntimeException e) {
                Log.w(LogDomain.NETWORK, "Start failed", e);
            }
        }

        @Override
        public boolean isConnected() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return true; }

            final NetworkInfo networkInfo = connectivityMgr.getActiveNetworkInfo();
            return (networkInfo != null) && networkInfo.isConnected();
        }
    }

    /**
     * Listener for API 24 - 28: use a ConnectivityCallback registered to the Default Network for updates
     * and ActiveNetworkInfo for current status
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static final class ConnectivityListener24to28 extends CallbackConnectivityWatcher {
        ConnectivityListener24to28(AndroidConnectivityManager mgr) { super("24-28", mgr); }

        @Override
        public void start() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return; }

            try {
                connectivityMgr.registerDefaultNetworkCallback(connectivityCallback);
                Log.v(LogDomain.NETWORK, getLogMessage("Started"));
            }
            catch (RuntimeException e) {
                Log.w(LogDomain.NETWORK, "Start failed", e);
            }
        }

        @Override
        public boolean isConnected() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return true; }

            final NetworkInfo networkInfo = connectivityMgr.getActiveNetworkInfo();
            return (networkInfo != null) && networkInfo.isConnected();
        }
    }

    /**
     * Listener for API >= 29: use a ConnectivityCallback registered to the Default Network for updates
     * and getNetworkCapabilites for current status
     * <p>
     * This actually might work as far back as API 23.  It has not been tested on API 29.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static final class ConnectivityListenerPost28 extends CallbackConnectivityWatcher {
        ConnectivityListenerPost28(AndroidConnectivityManager mgr) { super(">=29", mgr); }

        @Override
        public void start() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return; }

            try {
                connectivityMgr.registerDefaultNetworkCallback(connectivityCallback);
                Log.v(LogDomain.NETWORK, getLogMessage("Started"));
            }
            catch (RuntimeException e) {
                Log.w(LogDomain.NETWORK, "Start failed", e);
            }
        }

        @Override
        public boolean isConnected() {
            final ConnectivityManager connectivityMgr = getSysMgr();
            if (connectivityMgr == null) { return true; }

            final NetworkCapabilities activeNetworkCapabilities
                = connectivityMgr.getNetworkCapabilities(connectivityMgr.getActiveNetwork());

            return (activeNetworkCapabilities != null)
                && activeNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
    }

    static AndroidConnectivityManager newInstance() {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        return new AndroidConnectivityManager(mainHandler::post);
    }

    // This is just a weak Set
    @NonNull
    @GuardedBy("observers")
    private final WeakHashMap<Observer, Boolean> observers = new WeakHashMap<>();

    @NonNull
    private final AtomicReference<ConnectivityWatcher> listener = new AtomicReference<>(null);

    @NonNull
    private final Fn.Runner runner;
    private final int androidVersion;

    // Distinct API codepaths are: 19 22 26 29
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
        final boolean shouldStart;
        synchronized (observers) {
            shouldStart = observers.isEmpty();
            observers.put(observer, Boolean.TRUE);
        }
        if (shouldStart) { start(); }
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

        if (androidVersion < Build.VERSION_CODES.LOLLIPOP) {
            connectivityListener = new ConnectivityListenerPre21(this);
        }
        else if (androidVersion < Build.VERSION_CODES.N) {
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
