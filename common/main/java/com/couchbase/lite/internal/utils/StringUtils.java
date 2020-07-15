//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;


import android.support.annotation.NonNull;

import java.util.Locale;


public final class StringUtils {
    public static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String NUMERIC = "0123456789";
    public static final String ALPHANUMERIC = NUMERIC + ALPHA + ALPHA.toLowerCase(Locale.ROOT);
    private static final char[] CHARS = ALPHANUMERIC.toCharArray();

    public static String getUniqueName(@NonNull String prefix, int len) { return prefix + '-' + randomString(len); }

    @SuppressWarnings("ConstantConditions")
    public static String randomString(int len) {
        final char[] buf = new char[len];
        for (int idx = 0; idx < buf.length; ++idx) { buf[idx] = CHARS[MathUtils.RANDOM.get().nextInt(CHARS.length)]; }
        return new String(buf);
    }

    private StringUtils() { }

    public static boolean isEmpty(String str) { return (str == null) || str.isEmpty(); }

    @NonNull
    public static String getArrayString(String[] strs, int idx) {
        return (strs == null) || (idx < 0) || (idx >= strs.length) ? "" : strs[idx];
    }

    @NonNull
    public static String join(@NonNull CharSequence delimiter, @NonNull Iterable<?> tokens) {
        final StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token: tokens) {
            if (firstTime) { firstTime = false; }
            else { sb.append(delimiter); }
            sb.append(token);
        }
        return sb.toString();
    }
}
