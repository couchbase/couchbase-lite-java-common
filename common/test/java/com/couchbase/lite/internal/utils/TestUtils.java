//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;

import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.internal.KeyStoreManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public final class TestUtils {
    private TestUtils() {}

    public static <T extends Exception> void assertThrows(Class<T> ex, Fn.TaskThrows<Exception> test) {
        try {
            test.run();
            fail("Expecting exception: " + ex);
        }
        catch (Throwable e) {
            try { ex.cast(e); }
            catch (ClassCastException e1) { fail("Expecting exception: " + ex + " but got " + e); }
        }
    }

    public static void assertThrowsCBL(String domain, int code, Fn.TaskThrows<CouchbaseLiteException> task) {
        try {
            task.run();
            fail("Expected a CouchbaseLiteException");
        }
        catch (CouchbaseLiteException e) {
            assertEquals(code, e.getCode());
            assertEquals(domain, e.getDomain());
        }
    }

    public static Map<String, String> get509Attributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(KeyStoreManager.CERT_ATTRIBUTE_ORGANIZATION, "Couchbase");
        attributes.put(KeyStoreManager.CERT_ATTRIBUTE_ORGANIZATION_UNIT, "Mobile");
        attributes.put(KeyStoreManager.CERT_ATTRIBUTE_EMAIL_ADDRESS, "lite@couchbase.com");
        return attributes;
    }
}

