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

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class URLEndpointTest extends BaseTest {
    @Test(expected = IllegalArgumentException.class)
    public void testEmbeddedUserForbidden() throws URISyntaxException {
        new URLEndpoint(new URI("ws://user@couchbase.com/sg"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmbeddedPasswordNotAllowed() throws URISyntaxException {
        new URLEndpoint(new URI("ws://user:pass@couchbase.com/sg"));
    }

    @Test
    public void testBadScheme() {
        String uri = "http://4.4.4.4:4444";
        Exception err = null;
        try { new URLEndpoint(new URI(uri)); }
        catch (Exception e) { err = e; }
        assertNotNull(err);
        assertTrue(err.getMessage().contains(uri));
    }
}
