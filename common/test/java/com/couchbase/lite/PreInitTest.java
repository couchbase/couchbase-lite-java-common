//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


public class PreInitTest extends PlatformBaseTest {

    @BeforeClass
    public static void setUpPreInitTestClass() { CouchbaseLiteInternal.reset(false); }

    @AfterClass
    public static void tearDownPreInitTest() {  CouchbaseLiteInternal.reset(true); }

    @Test(expected = IllegalStateException.class)
    public void testCreateDatabaseBeforeInit() throws CouchbaseLiteException {
        new Database("fail", new DatabaseConfiguration());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetConsoleBeforeInit() { new Log().getConsole(); }

    @Test(expected = IllegalStateException.class)
    public void testGetFileBeforeInit() { new Log().getFile(); }

    @Test(expected = IllegalStateException.class)
    public void testCreateDBConfigBeforeInit() { new DatabaseConfiguration(); }
}
