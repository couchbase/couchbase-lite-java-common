package com.couchbase.lite;

import java.nio.charset.Charset;

import org.junit.Test;

import static com.couchbase.lite.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;


public class AuthenticatorTest extends BaseTest {

    @Test
    public void testBasicAuthenticatorInstance() {
        String username = "someUsername";
        String password = "somePassword";
        BasicAuthenticator auth = new BasicAuthenticator(username, password.toCharArray());
        assertEquals(username, auth.getUsername());
        assertEquals(password, new String(auth.getPasswordChars()));
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBasicAuthenticatorWithEmptyUsername() {
        new BasicAuthenticator(null, "somePassword".toCharArray());
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void testBasicAuthenticatorWithEmptyPassword() { new BasicAuthenticator("someUsername", (char[]) null); }

    @SuppressWarnings("deprecation")
    @Test
    public void testLegacyBasicAuthenticatorInstance() {
        String username = "someUsername";
        String password = "somePassword";
        BasicAuthenticator auth = new BasicAuthenticator(username, password);
        assertEquals(username, auth.getUsername());
        assertEquals(password, auth.getPassword());
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    @Test
    public void testLegacyBasicAuthenticatorWithEmptyArgs() {
        String username = "someUsername";
        String password = "somePassword";
        assertThrows(IllegalArgumentException.class, () -> new BasicAuthenticator(null, password));
        assertThrows(IllegalArgumentException.class, () -> new BasicAuthenticator(username, (String) null));
    }

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

    @SuppressWarnings("ConstantConditions")
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
