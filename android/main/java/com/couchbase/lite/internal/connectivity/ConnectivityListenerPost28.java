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
import android.net.NetworkCapabilities;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;


/**
 * Listener for API >= 29: use a ConnectivityCallback registered to the Default Network for updates
 * and getNetworkCapabilities for current status
 * <p>
 * This actually might work as far back as API 23.  It has not been tested on API 29.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
final class ConnectivityListenerPost28 extends CallbackConnectivityWatcher {
    ConnectivityListenerPost28(@NonNull AndroidConnectivityManager mgr) { super(">=29", mgr); }

    @Override
    public void start() {
        final ConnectivityManager connectivityMgr = getSysMgr();
        if (connectivityMgr == null) { return; }

        connectivityMgr.registerDefaultNetworkCallback(connectivityCallback);
        logStart();
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
