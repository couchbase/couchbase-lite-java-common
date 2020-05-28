//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.security.KeyStore;
import java.util.Date;
import java.util.Map;

import com.couchbase.lite.internal.AbstractTLSIdentity;


public final class TLSIdentity extends AbstractTLSIdentity {

    @Nullable
    public static TLSIdentity getIdentity(
        @NonNull KeyStore keyStore,
        @NonNull String alias,
        @Nullable byte[] keyPassword)
        throws CouchbaseLiteException {
        return null;
    }

    @NonNull
    public static TLSIdentity createIdentity(
        boolean isServer,
        @NonNull Map<String, String> attributes,
        @Nullable Date expiration,
        @NonNull KeyStore keyStore,
        @NonNull String alias,
        @Nullable byte[] keyPassword)
        throws CouchbaseLiteException {
        return new TLSIdentity();
    }

    private TLSIdentity() { super(null, null); }
}


