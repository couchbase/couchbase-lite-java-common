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

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;


/**
 * Listener for API 21 - 23: use a ConnectivityCallback for updates and ActiveNetworkInfo for status
 */
final class ConnectivityListener21to23 extends CallbackConnectivityWatcher {
    ConnectivityListener21to23(@NonNull AndroidConnectivityManager mgr) { super("21-23", mgr); }

    @Override
    public void start() {
        final ConnectivityManager connectivityMgr = getSysMgr();
        if (connectivityMgr == null) { return; }

        connectivityMgr.registerNetworkCallback(new NetworkRequest.Builder().build(), connectivityCallback);
        logStart();
    }

    @Override
    public boolean isConnected() {
        final ConnectivityManager connectivityMgr = getSysMgr();
        if (connectivityMgr == null) { return true; }

        final NetworkInfo networkInfo = connectivityMgr.getActiveNetworkInfo();
        return (networkInfo != null) && networkInfo.isConnected();
    }
}
