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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URI


class PreInitTest : BaseTest() {

    @Before
    fun setUpPreInitTest() = CouchbaseLiteInternal.reset()

    @Test
    fun testGetConsoleLoggerBeforeInit() {
        assertThrows(CouchbaseLiteError::class.java) { Log().console }
    }

    @Test
    fun testGetFileLoggerBeforeInit() {
        assertThrows(CouchbaseLiteError::class.java) { Log().file }
    }

    @Test
    fun testCreateDBConfigBeforeInit() {
        assertThrows(CouchbaseLiteError::class.java) { DatabaseConfiguration() }
    }

    @Test
    fun testCreateDatabaseBeforeInit() {
        assertThrows(CouchbaseLiteError::class.java) { Database("fail") }
    }

    @Test
    fun testCreateReplConfigBeforeInit() {
        assertThrows(CouchbaseLiteError::class.java) {
            ReplicatorConfiguration(URLEndpoint(URI("wss://foo.bar")))
        }
    }

    private fun assertThrows(ex: Class<out Exception>, test: () -> Unit) {
        var err: Throwable? = null
        try {
            test()
        } catch (e: Throwable) {
            if (ex == e::class.java) {
                return; }
            err = e
        }

        err?.printStackTrace()
        Assert.fail("Expected exception ${ex} but got ${err}")
    }
}
