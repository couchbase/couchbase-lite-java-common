//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.internal.utils.Fn
import com.couchbase.lite.internal.utils.Report
import com.couchbase.lite.mock.TestReplicatorChangeListener
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.net.URI
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

val mockURLEndpoint: URLEndpoint
    get() = URLEndpoint(URI("ws://foo.couchbase.com/db"))

// Always use this to create a test replicator: it prevents
// the network connectivity manager from messing up the tests
fun testReplicator(config: ReplicatorConfiguration) = Replicator(null, config)

abstract class BaseReplicatorTest : BaseDbTest() {
    protected lateinit var targetDatabase: Database
    protected lateinit var targetCollection: Collection

    @Before
    @Throws(CouchbaseLiteException::class)
    fun setUpBaseReplicatorTest() {
        targetDatabase = createDb("target_db")
        targetCollection = targetDatabase.createSimilarCollection(testCollection)
    }

    @After
    fun tearDownBaseReplicatorTest() {
        eraseDb(targetDatabase)
    }

    protected fun makeReplicatorConfig(
        source: Map<Collection, CollectionConfiguration?> = mapOf(testCollection to null),
        target: Endpoint = mockURLEndpoint,
        type: ReplicatorType? = null,
        continuous: Boolean? = null,
        authenticator: Authenticator? = null,
        headers: Map<String, String>? = null,
        pinnedServerCert: Certificate? = null,
        maxAttempts: Int = 1,
        maxAttemptWaitTime: Int = 1,
        heartbeat: Int = AbstractReplicatorConfiguration.DISABLE_HEARTBEAT,
        autoPurge: Boolean = true
    ): ReplicatorConfiguration {
        val config = ReplicatorConfiguration(target)
        source.forEach { config.addCollection(it.key, it.value) }
        type?.let { config.type = it }
        continuous?.let { config.isContinuous = it }
        authenticator?.let { config.setAuthenticator(it) }
        headers?.let { config.headers = it }
        pinnedServerCert?.let { config.pinnedServerX509Certificate = it as X509Certificate }
        maxAttempts.let { config.maxAttempts = it }
        maxAttemptWaitTime.let { config.maxAttemptWaitTime = it }
        heartbeat.let { config.heartbeat = it }
        autoPurge.let { config.setAutoPurgeEnabled(it) }
        return config
    }

    protected fun run(
        config: ReplicatorConfiguration = makeReplicatorConfig(),
        code: Int = 0,
        domain: String? = null,
        reset: Boolean = false,
        onReady: Fn.Consumer<Replicator?>? = null
    ) = run(testReplicator(config), code, domain, reset, onReady)

    protected fun run(
        repl: Replicator,
        code: Int = 0,
        domain: String? = null,
        reset: Boolean = false,
        onReady: Fn.Consumer<Replicator?>? = null
    ): Replicator {
        val listener = TestReplicatorChangeListener()

        onReady?.accept(repl)

        var ok = false
        val token = repl.addChangeListener(testSerialExecutor, listener)
        try {
            Report.log("Test replicator starting: %s", repl.config)
            repl.start(reset)
            ok = listener.awaitCompletion(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } finally {
            Report.log(listener.error, "Test replicator finished ${if (ok) "" else "un"}successfully")
            token.remove()
            repl.stop()
        }

        val err = listener.error
        if ((code == 0) && (domain == null)) {
            if (err != null) {
                throw AssertionError("Replication failed with unexpected error", err)
            }
        } else {
            if (err !is CouchbaseLiteException) {
                throw AssertionError("Replication failed with unrecognized error", err)
            }

            if (code != 0) {
                Assert.assertEquals(code, err.code)
            }
            if (domain != null) {
                Assert.assertEquals(domain, err.domain)
            }
        }

        return repl
    }
}