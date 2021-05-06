//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
import android.support.annotation.NonNull;

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

    protected final void logStart() {
        Log.v(LogDomain.NETWORK, String.format("Started %s network listener for %s: %s", name, getCblMgr(), this));
    }
}
