//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The BasicAuthenticator class is an authenticator that will authenticate using HTTP Basic
 * auth with the given username and password. This should only be used over an SSL/TLS connection,
 * as otherwise it's very easy for anyone sniffing network traffic to read the password.
 */
public final class BasicAuthenticator extends Authenticator {

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
     * @param username
     * @param password
     * @deprecated Use <code>BasicAuthenticator(String, char[])</code>
     */
    @Deprecated
    public BasicAuthenticator(@NonNull String username, @NonNull String password) {
        this(username, Preconditions.assertNotNull(password, "password").toCharArray());
    }

    /**
     * Create a Basic Authenticator.
     * The new instance contains a copy of the password char[] parameter:
     * the owner of the original retains the responsiblity for zeroing it before releasing it.
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
        Arrays.fill(password, (char) 0);
        super.finalize();
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------

    @Override
    void authenticate(@NonNull Map<String, Object> options) {
        final Map<String, Object> auth = new HashMap<>();
        auth.put(C4Replicator.REPLICATOR_AUTH_TYPE, C4Replicator.AUTH_TYPE_BASIC);
        auth.put(C4Replicator.REPLICATOR_AUTH_USER_NAME, username);
        // !!! Temporary hack until there is JNI/Core support for clearable passwords.
        auth.put(C4Replicator.REPLICATOR_AUTH_PASSWORD, new String(password));
        options.put(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION, auth);
    }
}
