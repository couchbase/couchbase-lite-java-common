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

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;


/**
 * Base class for ConnectivityWatchers
 * A ConnectivityWatcher can:
 * <li>
 *     <ul>provide current connection status</ul>
 *     <ul>between the time it is started and the time it is stopped, notify of connection status changes</ul>
 *     <ul>stop itself if nobody cares about the info it is providing</ul>
 * </li>
 */
abstract class ConnectivityWatcher {
    @NonNull
    protected final String name;
    @NonNull
    private final WeakReference<AndroidConnectivityManager> mgr;

    ConnectivityWatcher(@NonNull String name, @NonNull AndroidConnectivityManager mgr) {
        this.name = name;
        this.mgr = new WeakReference<>(mgr);
    }

    public abstract void start();
    public abstract boolean isConnected();
    public abstract void stop();

    @Nullable
    protected final AndroidConnectivityManager getCblMgr() { return mgr.get(); }

    @Nullable
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

        Log.d(LogDomain.NETWORK, "Changed %s connectivity for %s: %s", name, getCblMgr(), this);
        cblConnectivityMgr.connectivityChanged(networkState);
    }

    protected final void logStart() {
        Log.d(LogDomain.NETWORK, "Started %s network listener for %s: %s", name, getCblMgr(), this);
    }

    protected final void startFailed(RuntimeException err) {
        Log.w(LogDomain.NETWORK, "Failed starting %s network listener for %s: %s", err, name, getCblMgr(), this);
    }
}
