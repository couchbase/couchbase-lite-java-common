//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite;

import org.junit.Assert;
import org.junit.Test;


@SuppressWarnings("ConstantConditions")
public class AuthenticatorTest extends BaseTest {
    @Test
    public void testBasicAuthenticatorInstance() {
        String username = "someUsername";
        String password = "somePassword";
        BasicAuthenticator auth = new BasicAuthenticator(username, password.toCharArray());
        Assert.assertEquals(username, auth.getUsername());
        Assert.assertEquals(password, new String(auth.getPasswordChars()));
    }

    @Test
    public void testBasicAuthenticatorWithEmptyUsername() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BasicAuthenticator(null, "somePassword".toCharArray()));
    }


    @Test
    public void testBasicAuthenticatorWithEmptyPassword() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BasicAuthenticator("someUsername", null));
    }

    @Test
    public void testSessionAuthenticatorWithSessionID() {
        String sessionID = "someSessionID";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID);
        Assert.assertEquals(sessionID, auth.getSessionID());
        Assert.assertEquals("SyncGatewaySession", auth.getCookieName());
    }

    @Test
    public void testSessionAuthenticatorWithSessionIDAndCookie() {
        String sessionID = "someSessionID";
        String cookie = "someCookie";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID, cookie);
        Assert.assertEquals(sessionID, auth.getSessionID());
        Assert.assertEquals(cookie, auth.getCookieName());
    }

    @Test
    public void testSessionAuthenticatorEmptySessionID() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new SessionAuthenticator(null, null));
    }

    @Test
    public void testSessionAuthenticatorEmptyCookie() {
        String sessionID = "someSessionID";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID, null);
        Assert.assertEquals(sessionID, auth.getSessionID());
        Assert.assertEquals("SyncGatewaySession", auth.getCookieName());
    }
}
