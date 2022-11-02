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

import org.junit.Test;

import static org.junit.Assert.assertEquals;


@SuppressWarnings("ConstantConditions")
public class AuthenticatorTest extends BaseTest {
    @Test
    public void testBasicAuthenticatorInstance() {
        String username = "someUsername";
        String password = "somePassword";
        BasicAuthenticator auth = new BasicAuthenticator(username, password.toCharArray());
        assertEquals(username, auth.getUsername());
        assertEquals(password, new String(auth.getPasswordChars()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBasicAuthenticatorWithEmptyUsername() {
        new BasicAuthenticator(null, "somePassword".toCharArray());
    }


    @Test(expected = IllegalArgumentException.class)
    public void testBasicAuthenticatorWithEmptyPassword() { new BasicAuthenticator("someUsername", null); }

    @Test
    public void testSessionAuthenticatorWithSessionID() {
        String sessionID = "someSessionID";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID);
        assertEquals(sessionID, auth.getSessionID());
        assertEquals("SyncGatewaySession", auth.getCookieName());
    }

    @Test
    public void testSessionAuthenticatorWithSessionIDAndCookie() {
        String sessionID = "someSessionID";
        String cookie = "someCookie";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID, cookie);
        assertEquals(sessionID, auth.getSessionID());
        assertEquals(cookie, auth.getCookieName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSessionAuthenticatorEmptySessionID() { new SessionAuthenticator(null, null); }

    @Test
    public void testSessionAuthenticatorEmptyCookie() {
        String sessionID = "someSessionID";
        SessionAuthenticator auth = new SessionAuthenticator(sessionID, null);
        assertEquals(sessionID, auth.getSessionID());
        assertEquals("SyncGatewaySession", auth.getCookieName());
    }
}
