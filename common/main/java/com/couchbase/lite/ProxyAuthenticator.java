//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.BaseAuthenticator;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.utils.Preconditions;


public final class ProxyAuthenticator extends BaseAuthenticator {
    @NonNull
    private final String username;
    @NonNull
    private final char[] password;

    public ProxyAuthenticator(@NonNull String username, @NonNull char[] password) {
        this.username = Preconditions.assertNotEmpty(username, "user name");
        final int n = (password == null) ? 0 : password.length;
        if (n <= 0) { throw new IllegalArgumentException("empty password"); }
        final char[] pwd = new char[n];
        System.arraycopy(password, 0, pwd, 0, n);
        this.password = pwd;
    }

    /**
     * Get the username.
     *
     * @return the username
     */
    @NonNull
    public String getUsername() { return username; }

    /**
     * Get the password.
     * The returned char[] is a copy: the owner is responsible for
     * zeroing it before releasing it.
     *
     * @return the password.
     */
    @NonNull
    public char[] getPassword() {
        final char[] pwd = new char[password.length];
        System.arraycopy(password, 0, pwd, 0, pwd.length);
        return pwd;
    }

    @Override
    public int hashCode() { return username.hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof ProxyAuthenticator)) { return false; }
        final ProxyAuthenticator other = (ProxyAuthenticator) o;
        return username.equals(other.username)
            && MessageDigest.isEqual(
            new String(password).getBytes(StandardCharsets.UTF_8),
            new String(other.password).getBytes(StandardCharsets.UTF_8));
    }


    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { Arrays.fill(password, (char) 0); }
        finally { super.finalize(); }
    }


    @SuppressWarnings("unchecked")
    @Override
    protected void authenticate(@NonNull Map<String, Object> options) {
        Map<String, Object> auth = (Map<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (auth == null) {
            auth = new HashMap<>();
            options.put(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION, auth);
        }
        auth.put(C4Replicator.REPLICATOR_OPTION_PROXY_USER, username);
        auth.put(C4Replicator.REPLICATOR_OPTION_PROXY_PASS, password);
    }
}
