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

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;


public class URLEndpointTest extends BaseTest {
    @Test
    public void testEmbeddedUserForbidden() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new URLEndpoint(new URI("ws://user@couchbase.com/sg")));
    }

    @Test
    public void testEmbeddedPasswordNotAllowed() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new URLEndpoint(new URI("ws://user:pass@couchbase.com/sg")));
    }

    @Test
    public void testEmbeddedPasswordWithSpecialCharsNotAllowed() throws URISyntaxException {
        final String[] urls = {
            "ws://user:p@ss@couchbase.com/sg",   // Password containing '@': java.net.URI can't parse the user info
            "ws://user:p%40ss@couchbase.com/sg", // Password containing an escaped '@'
            "ws://user:1:2:3@couchbase.com/sg",  // Password containing ':'
            "ws://user:@couchbase.com/sg",       // Empty password
            "ws://:pass@couchbase.com/sg"        // Empty user
        };

        for (String url: urls) {
            final URI uri = new URI(url);
            Assert.assertThrows(url, IllegalArgumentException.class, () -> new URLEndpoint(uri));
        }
    }

    @Test
    public void testBadScheme() {
        String uri = "http://4.4.4.4:4444";
        Exception err = null;
        try { new URLEndpoint(new URI(uri)); }
        catch (Exception e) { err = e; }
        Assert.assertNotNull(err);
        Assert.assertTrue(err.getMessage().contains("http"));
        Assert.assertFalse(err.getMessage().contains(uri));
    }

    // The url may contain credentials: the error message must never include it
    @Test
    public void testBadSchemeDoesNotLeakCredentials() {
        Exception err = null;
        try { new URLEndpoint(new URI("http://user:secret@couchbase.com/sg")); }
        catch (Exception e) { err = e; }
        Assert.assertTrue(err instanceof IllegalArgumentException);
        Assert.assertFalse(err.getMessage().contains("secret"));
    }

    // java.net.URI doesn't parse a host name containing '_': make sure these are still accepted.
    @Test
    public void testValidURLs() throws URISyntaxException {
        final String[] urls = {"ws://couchbase.com/sg", "wss://couchbase.com:4984/sg", "ws://sync_gateway:4984/sg"};
        for (String url: urls) { Assert.assertEquals(new URI(url), new URLEndpoint(new URI(url)).getURL()); }
    }
}
