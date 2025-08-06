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

        if (stopStates.contains(level)) {
            latch.countDown()
        }
    }

    fun awaitCompletion(maxWait: Long = BaseTest.LONG_TIMEOUT_SEC, units: TimeUnit = TimeUnit.SECONDS): Boolean =
        token.use { return latch.await(maxWait, units) }
}

internal class ReplicatorAwaiter(private val repl: Replicator, exec: Executor) : ReplicatorChangeListener {
    private val awaiter = ListenerAwaiter(repl.addChangeListener(exec, this))

    val error: Throwable?
        get() = awaiter.error

    override fun changed(change: ReplicatorChange) = awaiter.changed(change)

    fun awaitCompletion(maxWait: Long = BaseTest.LONG_TIMEOUT_SEC, units: TimeUnit = TimeUnit.SECONDS): Boolean {
        Report.log("Awaiting replicator ${repl}")
        val ok = awaiter.awaitCompletion(maxWait, units)
        Report.log("Replicator finished (${ok}, ${awaiter.error}): ${repl}")
        return ok
    }
}

// A filter can actually hang the replication
internal class DelayFilter(val name: String, private val barrier: CyclicBarrier) : ReplicationFilter {
    private val shouldWait = AtomicBoolean(true)

    override fun filtered(doc: Document, flags: EnumSet<DocumentFlag>): Boolean {
        if (shouldWait.getAndSet(false)) {
            Report.log("${name} in delay with doc: ${doc.id}")
            try {
                barrier.await(BaseTest.LONG_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Report.log("${name} delay interrupted", e)
            }
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
        targetCollection.close()

        val repls = replicators.toList()
        replicators.clear()
        repls.forEach { it.close() }

        eraseDb(targetDatabase)
    }

    protected fun makeCollectionConfig(
        collection: Collection = testCollection,
        channels: List<String>? = null,
        docIds: List<String>? = null,
        pullFilter: ReplicationFilter? = null,
        pushFilter: ReplicationFilter? = null,
        resolver: ConflictResolver? = null
    ): CollectionConfiguration {
        val config = CollectionConfiguration(collection)
        channels?.let { config.channels = it }
        docIds?.let { config.documentIDs = it }
        pullFilter?.let { config.pullFilter = it }
        pushFilter?.let { config.pushFilter = it }
        resolver.let { config.conflictResolver = it }
        return config
    }

    protected fun makeSimpleReplConfig(
        target: Endpoint = mockURLEndpoint,
        source: kotlin.collections.Collection<CollectionConfiguration> =
            CollectionConfiguration.fromCollections(setOf(testCollection)),
        type: ReplicatorType? = null,
        continuous: Boolean? = null,
        authenticator: Authenticator? = null,
        headers: Map<String, String>? = null,
        pinnedServerCert: Certificate? = null,
        maxAttempts: Int = 1,
        maxAttemptWaitTime: Int = 1,
        autoPurge: Boolean = true
    ): ReplicatorConfiguration {
        val config = ReplicatorConfiguration(source, target)
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

    protected fun ReplicatorConfiguration.run(code: Int = 0, reset: Boolean = false): Replicator {
        return this.testReplicator().run(
            reset,
            expectedErrs = if (code == 0) emptyArray() else arrayOf(
                CouchbaseLiteException("", CBLError.Domain.CBLITE, code)
            )
        )
    }

    protected fun Replicator.run(code: Int = 0): Replicator {
        return this.run(
            expectedErrs = if (code == 0) emptyArray()
            else arrayOf(CouchbaseLiteException("", CBLError.Domain.CBLITE, code))
        )
    }

    protected fun Replicator.run(
        reset: Boolean = false,
        timeoutSecs: Long = LONG_TIMEOUT_SEC,
        vararg expectedErrs: Exception
    ): Replicator {
        val awaiter = ReplicatorAwaiter(this, testSerialExecutor)

        Report.log("Test replicator starting: %s", this.config)
        try {
            this.start(reset)
            if (!awaiter.awaitCompletion(timeoutSecs, TimeUnit.SECONDS)) {
                throw AssertionError("Replicator timed out")
            }
        } finally {
            this.stop()
        }

        val err = awaiter.error
        if (err == null) {
            if (expectedErrs.isNotEmpty()) {
                throw AssertionError("Replication finished succesfully when expecting error in: ${expectedErrs}")
            }
        } else {
            if (!containsWithComparator(expectedErrs.toList(), err, BaseTest::compareExceptions)) {
                throw AssertionError("Expecting error in [${expectedErrs.joinToString(", ")}] but got:", err)
            }
        }

        return this
    }
}
