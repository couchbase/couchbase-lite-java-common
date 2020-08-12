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
package com.couchbase.lite.internal.utils;

import android.support.annotation.NonNull;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


public final class CookieUtils {
    private CookieUtils() { }

    /**
     * Parse request header "Cookie" in the format of "name=value;name=value..."
     * into OKHTTP Cookie used by AbstractCBLWebSocket.
     */
    @NonNull
    public static List<Cookie> parseCookies(@NonNull HttpUrl url, @NonNull String cookies) {
        final List<Cookie> cookieList = new ArrayList<>();
        final StringTokenizer st = new StringTokenizer(cookies, ";");
        while (st.hasMoreTokens()) {
            final Cookie cookie = Cookie.parse(url, st.nextToken().trim());
            if (cookie != null) { cookieList.add(cookie); }
        }
        return cookieList;
    }

    /**
     * Parse request header "Cookie" in the format of "name=value;name=value..."
     * into HttpCookie for used by ReplicatorCookieTest.
     */
    @NonNull
    public static List<HttpCookie> parseHttpCookies(String cookies)  {
        final List<HttpCookie> cookieList = new ArrayList<>();
        final StringTokenizer st = new StringTokenizer(cookies, ";");
        while (st.hasMoreTokens()) {
            try { cookieList.addAll(HttpCookie.parse(st.nextToken().trim())); }
            catch (IllegalArgumentException e) {
                Log.w(LogDomain.NETWORK, "Invalid Cookies");
            }
        }
        return cookieList;
    }
}
