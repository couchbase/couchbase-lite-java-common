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
package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.BaseAuthenticator;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The BasicAuthenticator class is an authenticator that will authenticate using HTTP Basic
 * auth with the given username and password. This should only be used over an SSL/TLS connection,
 * as otherwise it's very easy for anyone sniffing network traffic to read the password.
 */
public final class BasicAuthenticator extends BaseAuthenticator implements Authenticator {

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final String username;
    @NonNull
    private final char[] password;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    /**
     * Create a Basic Authenticator.
     * The new instance contains a copy of the password char[] parameter:
     * the owner of the original retains the responsibility for zeroing it before releasing it.
     */
    public BasicAuthenticator(@NonNull String username, @NonNull char[] password) {
        this.username = Preconditions.assertNotNull(username, "username");
        Preconditions.assertNotNull(password, "password");
        this.password = new char[password.length];
        System.arraycopy(password, 0, this.password, 0, this.password.length);
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    @NonNull
    public String getUsername() { return username; }

    /**
     * @deprecated Use <code>getPasswordChars(char[])</code>
     */
    @Deprecated
    @NonNull
    public String getPassword() { return new String(password); }

    /**
     * Get the password.
     * The returned char[] is a copy: the owner is responsible for zeroing it before releasing it.
     *
     * @return the password, as a char[].
     */
    @NonNull
    public char[] getPasswordChars() {
        final char[] pwd = new char[password.length];
        System.arraycopy(this.password, 0, pwd, 0, pwd.length);
        return pwd;
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try {
            final char[] pwd = password;
            if (pwd != null) { Arrays.fill(pwd, (char) 0); }
        }
        finally { super.finalize(); }
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------

    @SuppressWarnings("unchecked")
    @Override
    protected void authenticate(@NonNull Map<String, Object> options) {
        Map<String, Object> auth = (Map<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (auth == null) {
            auth = new HashMap<>();
            options.put(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION, auth);
        }
        auth.put(C4Replicator.REPLICATOR_AUTH_TYPE, C4Replicator.AUTH_TYPE_BASIC);
        auth.put(C4Replicator.REPLICATOR_AUTH_USER_NAME, username);
        auth.put(C4Replicator.REPLICATOR_AUTH_PASSWORD, password);
    }
}
