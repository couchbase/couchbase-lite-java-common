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

import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * Base class for ConnectivityManager.NetworkCallback based Watchers.
 */
abstract class CallbackConnectivityWatcher extends ConnectivityWatcher {
    @NonNull
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
        try { connectivityMgr.unregisterNetworkCallback(connectivityCallback); }
        catch (RuntimeException e) {
            Log.w(LogDomain.NETWORK, "Failed stopping " + msg, e);
            return;
        }

        Log.d(LogDomain.NETWORK, "Stopped: %s", msg);
    }
}
