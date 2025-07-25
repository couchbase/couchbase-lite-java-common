//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite

import com.couchbase.lite.internal.CouchbaseLiteInternal
import com.couchbase.lite.logging.LogSinks
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URI


class PreInitTest : BaseTest() {

    @Before
    fun setUpPreInitTest() = CouchbaseLiteInternal.reset()

    @Test
    fun testGetConsoleLoggerBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) { LogSinks.get().console }
    }

    @Test
    fun testGetFileLoggerBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) { LogSinks.get().file }
    }

    @Test
    fun testCreateDBConfigBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) { DatabaseConfiguration() }
    }

    @Test
    fun testCreateDatabaseBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) { Database("fail") }
    }

    @Test
    fun testCreateReplConfigBeforeInit() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            ReplicatorConfiguration(URLEndpoint(URI("wss://foo.bar")))
        }
    }
}
