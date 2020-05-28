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
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
    private final byte[] password;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    /**
     * @param username
     * @param password
     * @deprecated Use BasicAuthenticator(String, byte[])
     */
    @Deprecated
    public BasicAuthenticator(@NonNull String username, @NonNull String password) {
        this(username, Preconditions.assertNotNull(password, "password").getBytes(Charset.defaultCharset()));
    }

    /**
     * Create a Basic Authenticator.
     * The new instance contains a copy of the password byte array parameter:
     * the owner of the original retains the responsiblity for zeroing it before releasing it.
     *
     * @return
     */
    public BasicAuthenticator(@NonNull String username, @NonNull byte[] password) {
        this.username = Preconditions.assertNotNull(username, "username");
        Preconditions.assertNotNull(password, "password");
        this.password = new byte[password.length];
        System.arraycopy(password, 0, this.password, 0, this.password.length);
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    @NonNull
    public String getUsername() { return username; }

    /**
     * @deprecated Use getPasswordBytes(byte[])
     */
    @Deprecated
    @NonNull
    public String getPassword() { return new String(password, Charset.defaultCharset()); }

    /**
     * Get the password.
     * The returned byte array is a copy: the owner is responsible for zeroing it before releasing it.
     *
     * @return the password, as a byte array.
     */
    @NonNull
    public byte[] getPasswordBytes() {
        final byte[] pwd = new byte[password.length];
        System.arraycopy(this.password, 0, pwd, 0, pwd.length);
        return pwd;
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        Arrays.fill(password, (byte) 0);
        super.finalize();
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------

    @Override
    void authenticate(@NonNull Map<String, Object> options) {
        final Map<String, Object> auth = new HashMap<>();
        auth.put(AbstractReplicatorConfiguration.REPLICATOR_AUTH_TYPE, AbstractReplicatorConfiguration.AUTH_TYPE_BASIC);
        auth.put(AbstractReplicatorConfiguration.REPLICATOR_AUTH_USER_NAME, username);
        auth.put(AbstractReplicatorConfiguration.REPLICATOR_AUTH_PASSWORD, password);
        options.put(AbstractReplicatorConfiguration.REPLICATOR_AUTH_OPTION, auth);
    }
}
