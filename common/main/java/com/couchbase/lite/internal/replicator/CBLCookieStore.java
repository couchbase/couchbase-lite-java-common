//
// Copyright (c) 2020 Couchbase, Inc.  All rights reserved.
//
// Licensed under the Couchbase License Agreement (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.replicator;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import okhttp3.Cookie;
import okhttp3.HttpUrl;


/**
 * Interface for CookieStore
 */
public interface CBLCookieStore {
    /**
     * Parse request header "Cookie" in the format of "name=value;name=value..."
     * into OKHTTP Cookie used by AbstractCBLWebSocket.
     */
    @NonNull
    static List<Cookie> parseCookies(@NonNull HttpUrl url, @NonNull String cookies) {
        final List<Cookie> cookieList = new ArrayList<>();
        final StringTokenizer st = new StringTokenizer(cookies, ";");
        while (st.hasMoreTokens()) {
            final Cookie cookie = Cookie.parse(url, st.nextToken().trim());
            if (cookie != null) { cookieList.add(cookie); }
        }
        return cookieList;
    }

    void setCookies(@NonNull URI uri, @NonNull List<String> cookies, boolean acceptParentDomain);

    @Nullable
    String getCookies(@NonNull URI uri);
}
