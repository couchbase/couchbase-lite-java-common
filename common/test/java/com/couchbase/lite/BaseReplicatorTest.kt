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

import com.couchbase.lite.internal.replicator.ReplicationStatusChange
import com.couchbase.lite.internal.utils.Report
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.net.URI
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


internal class ListenerAwaiter(
    private val token: ListenerToken,
    private val stopStates: kotlin.collections.Collection<ReplicatorActivityLevel> = STOP_STATES
) {
    companion object {
        private val STOP_STATES = setOf(
            ReplicatorActivityLevel.STOPPED,
            ReplicatorActivityLevel.OFFLINE,
            ReplicatorActivityLevel.IDLE
        )
    }

    private val err = AtomicReference<Throwable?>(null)
    private val latch = CountDownLatch(1)

    val error: Throwable?
        get() = err.get()

    fun changed(change: ReplicationStatusChange) {
        val status = change.status
        status.error?.let { err.compareAndSet(null, it) }
        val level = status.activityLevel
        val e = status.error
        Report.log(e, "Test replicator state change: $level")

        if (e != null) err.compareAndSet(null, e)

        if (stopStates.contains(level)) latch.countDown()
    }

    fun awaitCompletion(maxWait: Long = BaseTest.LONG_TIMEOUT_SEC, units: TimeUnit = TimeUnit.SECONDS): Boolean =
        token.use {
            val ok = latch.await(maxWait, units)
            if (!ok) err.compareAndSet(null, IllegalStateException("timeout"))
            return ok
        }
}

internal class ReplicatorAwaiter(repl: Replicator, exec: Executor) : ReplicatorChangeListener {
    private val awaiter = ListenerAwaiter(repl.addChangeListener(exec, this))

    val error: Throwable?
        get() = awaiter.error

    override fun changed(change: ReplicatorChange) = awaiter.changed(change)

    fun awaitCompletion(maxWait: Long = BaseTest.LONG_TIMEOUT_SEC, units: TimeUnit = TimeUnit.SECONDS) =
        awaiter.awaitCompletion(maxWait, units)
}

// A filter can actually hang the replication
internal class DelayFilter(val name: String, val barrier: CyclicBarrier) : ReplicationFilter {
    val shouldWait = AtomicBoolean(true)

    override fun filtered(doc: Document, flags: EnumSet<DocumentFlag>): Boolean {
        if (shouldWait.getAndSet(false)) {
            Report.log("${name} waiting with doc: ${doc.id}")
            barrier.await(BaseTest.STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        }

        Report.log("${name} filtered doc: ${doc.id}")
        return true
    }
}


abstract class BaseReplicatorTest : BaseDbTest() {
    protected val mockURLEndpoint = URLEndpoint(URI("ws://foo.couchbase.com/db"))

    protected val nullResolver = ConflictResolver { null }
    protected val localResolver = ConflictResolver { conflict -> conflict.localDocument }
    protected val remoteResolver = ConflictResolver { conflict -> conflict.remoteDocument }

    protected lateinit var targetDatabase: Database
        private set
    protected lateinit var targetCollection: Collection
        private set

    private val replicators = mutableListOf<Replicator>()

    @Before
    @Throws(CouchbaseLiteException::class)
    fun setUpBaseReplicatorTest() {
        targetDatabase = createDb("target_db")
        targetCollection = targetDatabase.createSimilarCollection(testCollection)
    }

    @After
    fun tearDownBaseReplicatorTest() {
        replicators.forEach { it.close() }
        eraseDb(targetDatabase)
    }

    protected fun makeCollectionConfig(
        channels: List<String>? = null,
        docIds: List<String>? = null,
        pullFilter: ReplicationFilter? = null,
        pushFilter: ReplicationFilter? = null,
        resolver: ConflictResolver? = null
    ): CollectionConfiguration {
        val config = CollectionConfiguration()
        channels?.let { config.channels = it }
        docIds?.let { config.documentIDs = it }
        pullFilter?.let { config.pullFilter = it }
        pushFilter?.let { config.pushFilter = it }
        resolver.let { config.conflictResolver = it }
        return config
    }

    protected fun makeSimpleReplConfig(
        target: Endpoint = mockURLEndpoint,
        source: kotlin.collections.Collection<Collection> = setOf(testCollection),
        srcConfig: CollectionConfiguration? = null,
        type: ReplicatorType? = null,
        continuous: Boolean? = null,
        authenticator: Authenticator? = null,
        headers: Map<String, String>? = null,
        pinnedServerCert: Certificate? = null,
        maxAttempts: Int = 1,
        maxAttemptWaitTime: Int = 1,
        autoPurge: Boolean = true
    ) = makeReplConfig(
        target,
        mapOf(source to srcConfig),
        type,
        continuous,
        authenticator,
        headers,
        pinnedServerCert,
        maxAttempts,
        maxAttemptWaitTime,
        autoPurge
    )

    protected fun makeReplConfig(
        target: Endpoint = mockURLEndpoint,
        source: Map<out kotlin.collections.Collection<Collection>, CollectionConfiguration?> =
            mapOf(setOf(testCollection) to null),
        type: ReplicatorType? = null,
        continuous: Boolean? = null,
        authenticator: Authenticator? = null,
        headers: Map<String, String>? = null,
        pinnedServerCert: Certificate? = null,
        maxAttempts: Int = 1,
        maxAttemptWaitTime: Int = 1,
        autoPurge: Boolean = true
    ): ReplicatorConfiguration {
        val config = ReplicatorConfiguration(target)

        source.forEach { config.addCollections(it.key, it.value) }
        type?.let { config.type = it }
        continuous?.let { config.isContinuous = it }
        authenticator?.let { config.setAuthenticator(it) }
        headers?.let { config.headers = it }
        pinnedServerCert?.let { config.pinnedServerX509Certificate = it as X509Certificate }
        maxAttempts.let { config.maxAttempts = it }
        maxAttemptWaitTime.let { config.maxAttemptWaitTime = it }
        autoPurge.let { config.setAutoPurgeEnabled(it) }

        // The mocks used in the loopback tests are
        // not prepared to handle heartbeats
        config.heartbeat = AbstractReplicatorConfiguration.DISABLE_HEARTBEAT

        return config
    }

    // Prefer this method to any other, for creating new replicators
    // It prevents the NetworkConnectivityManager from confusing these tests
    protected fun ReplicatorConfiguration.testReplicator(): Replicator {
        val repl = Replicator(null, this)
        replicators.add(repl)
        return repl
    }

    protected fun ReplicatorConfiguration.run(reset: Boolean = false, errDomain: String? = null, errCode: Int = 0) =
        replicatorConfiguration.testReplicator().run(reset, errDomain, errCode)

    protected fun Replicator.run(reset: Boolean = false, errDomain: String? = null, errCode: Int = 0): Replicator {
        val awaiter = ReplicatorAwaiter(this, testSerialExecutor)

        Report.log("Test replicator starting: %s", this.config)
        var ok = false
        try {
            this.start(reset)
            ok = awaiter.awaitCompletion(STD_TIMEOUT_SEC, TimeUnit.SECONDS)
        } finally {
            this.stop()
            Report.log(awaiter.error, "Test replicator ${if (ok) "finished" else "timed out"}")
        }

        val err = awaiter.error
        if ((errCode == 0) && (errDomain == null)) {
            if (err != null) throw AssertionError("Replication failed with unexpected error", err)
        } else {
            if (err !is CouchbaseLiteException) {
                if (err != null) throw AssertionError("Replication failed with unexpected error", err)
                throw AssertionError("Expected CBLError (${errDomain}, ${errCode}) but no error occurred")
            }

            if (errCode != 0) {
                Assert.assertEquals(errCode, err.code)
            }

            if (errDomain != null) {
                Assert.assertEquals(errDomain, err.domain)
            }
        }

        return this
    }
}
